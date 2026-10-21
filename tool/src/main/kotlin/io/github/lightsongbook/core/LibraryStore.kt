package io.github.lightsongbook.core

import java.io.File

/**
 * Keeps the library in the tool's private files directory:
 *
 *  songbook/backup.txt   – dataFile.txt from the latest Songbook Pro backup
 *  songbook/extra/…      – individually imported ChordPro files
 */
class LibraryStore(filesDir: File) {

    private val root = File(filesDir, "songbook")
    private val backupFile = File(root, "backup.txt")
    private val extraDir = File(root, "extra")

    data class ImportSummary(val songs: Int, val sets: Int, val replacedLibrary: Boolean) {
        val message: String
            get() = buildString {
                append(if (songs == 1) "1 Song" else "$songs Songs")
                if (replacedLibrary) append(if (sets == 1) ", 1 Setlist" else ", $sets Setlists")
                append(" importiert")
            }
    }

    fun load(): Library {
        val backup = if (backupFile.isFile) {
            try {
                Importer.parseBackupData(backupFile.readText())
            } catch (e: ImportException) {
                Library.EMPTY
            }
        } else {
            Library.EMPTY
        }
        val extras = extraDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name }
            ?.map { Importer.songFromChordPro(it.name, it.readText()) }
            .orEmpty()
        return Library(backup.songs + extras, backup.sets)
    }

    /** Parses first and only writes when parsing succeeded, so a bad file never wipes the library. */
    fun import(bytes: ByteArray, fileName: String): ImportSummary {
        return when (val payload = Importer.detect(bytes, fileName)) {
            is Importer.Payload.Backup -> {
                if (payload.library.songs.isEmpty()) throw ImportException("Das Backup enthält keine Songs.")
                root.mkdirs()
                val tmp = File(root, "backup.tmp")
                tmp.writeText(payload.dataFileText)
                if (!tmp.renameTo(backupFile)) {
                    backupFile.delete()
                    tmp.renameTo(backupFile)
                }
                ImportSummary(payload.library.songs.size, payload.library.sets.size, replacedLibrary = true)
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
