package io.github.lightsongbook.core

import java.io.File

/**
 * Keeps the library in the tool's private files directory:
 *
 *  songbook/backup.txt   – dataFile.txt from the latest Songbook Pro backup
 *  songbook/extra/…      – individually imported ChordPro files
 *  songbook/audio/…      – practice recordings (mp3 etc.)
 */
class LibraryStore(filesDir: File) {

    private val root = File(filesDir, "songbook")
    private val backupFile = File(root, "backup.txt")
    private val extraDir = File(root, "extra")
    private val audioDir = File(root, "audio")
    val userData = UserData(root)

    data class ImportSummary(
        val songs: Int,
        val sets: Int,
        val replacedLibrary: Boolean,
        val audioFile: String? = null,
    ) {
        val message: String
            get() = if (audioFile != null) "Audio gespeichert: $audioFile" else buildString {
                append(if (songs == 1) "1 Song" else "$songs Songs")
                if (replacedLibrary) append(if (sets == 1) ", 1 Setlist" else ", $sets Setlists")
                append(" importiert")
            }
    }

    private var cachedBackup: Pair<Long, Library>? = null

    /** Parsed backup, cached until the file changes (editing setlists reloads often). */
    private fun backupLibrary(): Library {
        if (!backupFile.isFile) return Library.EMPTY
        val stamp = backupFile.lastModified() xor backupFile.length()
        cachedBackup?.let { (s, lib) -> if (s == stamp) return lib }
        val lib = try {
            Importer.parseBackupData(backupFile.readText())
        } catch (e: ImportException) {
            Library.EMPTY
        }
        cachedBackup = stamp to lib
        return lib
    }

    fun load(): Library {
        val backup = backupLibrary()
        val extras = extraDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name }
            ?.map { Importer.songFromChordPro(it.name, it.readText()) }
            .orEmpty()
        val all = backup.songs + extras
        val hidden = userData.loadHidden()
        val visible = all.filter { it.id !in hidden }
        val visibleIds = visible.mapTo(HashSet()) { it.id }
        val backupSets = backup.sets.map { set -> set.copy(entries = set.entries.filter { it.songId in visibleIds }) }
        val ownSets = userData.loadSets().map { u ->
            SetList(
                id = u.id,
                name = u.name,
                date = u.date,
                entries = u.songIds.filter { it in visibleIds }.map { SetEntry(it, 0, 0) },
                editable = true,
            )
        }
        return Library(visible, ownSets + backupSets, all.filter { it.id in hidden })
    }

    /** Parses first and only writes when parsing succeeded, so a bad file never wipes the library. */
    fun import(bytes: ByteArray, fileName: String): ImportSummary {
        return when (val payload = Importer.detect(bytes, fileName)) {
            is Importer.Payload.Backup -> {
                if (payload.library.songs.isEmpty()) throw ImportException("Das Backup enthält keine Songs.")
                root.mkdirs()
                val tmp = File(root, "backup.tmp")
                tmp.writeText(payload.dataFileText)
                cachedBackup = null
                if (!tmp.renameTo(backupFile)) {
                    backupFile.delete()
                    tmp.renameTo(backupFile)
                }
                ImportSummary(payload.library.songs.size, payload.library.sets.size, replacedLibrary = true)
            }
            is Importer.Payload.AudioFile -> {
                audioDir.mkdirs()
                val name = safeFileName(payload.fileName)
                val tmp = File(audioDir, ".$name.tmp")
                tmp.writeBytes(payload.bytes)
                val target = File(audioDir, name)
                target.delete()
                tmp.renameTo(target)
                ImportSummary(0, 0, replacedLibrary = false, audioFile = name)
            }
            is Importer.Payload.ChordProFiles -> {
                extraDir.mkdirs()
                for ((name, text) in payload.files) {
                    File(extraDir, safeFileName(name)).writeText(text)
                }
                ImportSummary(payload.files.size, 0, replacedLibrary = false)
            }
        }
    }

    /** Names of the stored recordings, sorted. */
    fun audioFiles(): List<String> =
        audioDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.map { it.name }?.sorted().orEmpty()

    fun audioFile(name: String): File? = File(audioDir, safeFileName(name)).takeIf { it.isFile }

    fun deleteAudio(name: String) {
        File(audioDir, safeFileName(name)).delete()
    }

    fun clear() {
        root.deleteRecursively()
    }

    private fun safeFileName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9ÄÖÜäöüß ._()-]"), "_")
            .trim('.', ' ')
            .take(120)
        return base.ifEmpty { "song.cho" }
    }
}
