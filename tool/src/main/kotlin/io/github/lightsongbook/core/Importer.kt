package io.github.lightsongbook.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ImportException(message: String) : Exception(message)

/**
 * Reads Songbook Pro backups (.sbpbackup) and ChordPro files.
 *
 * A .sbpbackup is a zip archive containing `dataFile.txt`: a version line ("1.0")
 * followed by JSON with `songs`, `sets` and `folders`. App settings in the archive
 * (`settings.hive`) are ignored.
 */
object Importer {

    const val MAX_UPLOAD_BYTES = 80 * 1024 * 1024
    private const val MAX_UNZIPPED_BYTES = 100L * 1024 * 1024
    private val CHORDPRO_EXTENSIONS = setOf("cho", "crd", "chopro", "chordpro", "chord", "pro", "txt")

    sealed class Payload {
        /** Complete Songbook Pro library; replaces the previous backup. */
        data class Backup(val dataFileText: String, val library: Library) : Payload()

        /** Single songs to add (file name to file text). */
        data class ChordProFiles(val files: List<Pair<String, String>>) : Payload()

        /** A practice recording. */
        class AudioFile(val fileName: String, val bytes: ByteArray) : Payload()
    }

    fun detect(bytes: ByteArray, fileName: String): Payload {
        if (bytes.isEmpty()) throw ImportException("Die Datei ist leer.")
        if (Audio.isAudioFile(fileName)) return Payload.AudioFile(fileName, bytes)
        if (isZip(bytes)) {
            val entries = unzip(bytes)
            entries["dataFile.txt"]?.let { data ->
                val text = data.toString(Charsets.UTF_8)
                return Payload.Backup(text, parseBackupData(text))
            }
            val songs = entries
                .filterKeys { name -> name.substringAfterLast('.').lowercase() in CHORDPRO_EXTENSIONS }
                .filterKeys { name -> !name.substringAfterLast('/').startsWith(".") && !name.startsWith("__MACOSX") }
                .map { (name, data) -> name.substringAfterLast('/') to data.toString(Charsets.UTF_8) }
            if (songs.isEmpty()) throw ImportException("Im Archiv wurden keine Songs gefunden.")
            return Payload.ChordProFiles(songs)
        }
        val text = bytes.toString(Charsets.UTF_8)
        val trimmed = text.trimStart('﻿', ' ', '\n', '\r')
        if (looksLikeBackupData(trimmed)) return Payload.Backup(trimmed, parseBackupData(trimmed))
        if (fileName.substringAfterLast('.').lowercase() == "sbp") {
            throw ImportException("Einzelne .sbp-Songs werden nicht unterstützt. Bitte ein Backup oder ChordPro exportieren.")
        }
        return Payload.ChordProFiles(listOf(fileName to text))
    }

    private fun isZip(bytes: ByteArray) =
        bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    private fun looksLikeBackupData(text: String): Boolean {
        val firstLine = text.substringBefore('\n').trim()
        val json = if (firstLine.startsWith("{")) text else text.substringAfter('\n')
        return json.trimStart().startsWith("{") && json.contains("\"songs\"")
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val out = ByteArrayOutputStream()
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_UNZIPPED_BYTES) throw ImportException("Das Archiv ist zu groß.")
                    out.write(buffer, 0, n)
                }
                result[entry.name] = out.toByteArray()
            }
        }
        return result
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Parses the contents of `dataFile.txt`. */
    fun parseBackupData(text: String): Library {
        val firstLine = text.substringBefore('\n').trim()
        val body = if (firstLine.startsWith("{")) text else text.substringAfter('\n', "")
        val root = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: throw ImportException("Das Backup konnte nicht gelesen werden.")

        val songs = root.array("songs").mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            if (o.bool("Deleted")) return@mapNotNull null
            if (o.int("type", 1) != 1) return@mapNotNull null // PDFs and other non-text songs
            val sbpId = o.int("Id", -1)
            Song(
                id = "sbp:$sbpId",
                title = o.str("name").ifBlank { "Ohne Titel" },
                artist = o.str("author"),
                keyPitch = o["key"]?.let { (it as? JsonPrimitive)?.intOrNull }
                    ?.takeIf { it in 0..11 }
                    ?.let(Chords::pitchFromSongbookKey),
                keyShift = o.int("KeyShift", 0),
                capo = o.int("Capo", 0),
                content = o.str("content"),
                tempo = o.int("TempoInt", 0),
                durationSeconds = o.int("Duration", 0),
                notes = o.str("NotesText"),
                source = Song.Source.Backup,
            )
        }
        val songIds = songs.mapTo(HashSet()) { it.id }

        val sets = root.array("sets").mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val details = o["details"] as? JsonObject ?: return@mapNotNull null
            if (details.bool("Deleted")) return@mapNotNull null
            val entries = o.array("contents")
                .mapNotNull { it as? JsonObject }
                .filter { !it.bool("Deleted") && it.int("ItemType", 1) == 1 }
                .sortedBy { it.int("Order", 0) }
                .map { SetEntry("sbp:${it.int("SongId", -1)}", it.int("keyOfset", 0), it.int("Capo", 0)) }
                .filter { it.songId in songIds }
            SetList(
                id = "sbp:${details.int("Id", -1)}",
                name = details.str("name").ifBlank { "Setlist" },
                date = details.str("date").take(10),
                entries = entries,
            )
        }
        return Library(songs, sets)
    }

    /** Converts a ChordPro file into a song. */
    fun songFromChordPro(fileName: String, text: String): Song {
        val meta = ChordPro.readFile(text)
        val fallbackTitle = fileName.substringAfterLast('/').substringBeforeLast('.')
        return Song(
            id = "cho:$fileName",
            title = meta.title?.ifBlank { null } ?: fallbackTitle,
            artist = meta.artist ?: "",
            keyPitch = meta.keyPitch,
            keyShift = 0,
            capo = meta.capo,
            content = meta.body,
            tempo = meta.tempo,
            source = Song.Source.ChordPro,
        )
    }

    private fun JsonObject.array(name: String): List<JsonElement> = (this[name] as? JsonArray) ?: emptyList()

    private fun JsonObject.str(name: String): String {
        val p = this[name] as? JsonPrimitive ?: return ""
        return if (p.isString) p.content else ""
    }

    private fun JsonObject.int(name: String, default: Int): Int =
        (this[name] as? JsonPrimitive)?.let { it.intOrNull ?: it.content.toDoubleOrNull()?.toInt() } ?: default

    private fun JsonObject.bool(name: String): Boolean {
        val p = this[name] as? JsonPrimitive ?: return false
        return p.booleanOrNull ?: (p.intOrNull?.let { it != 0 } ?: false)
    }
}
