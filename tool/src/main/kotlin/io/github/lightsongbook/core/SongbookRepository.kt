package io.github.lightsongbook.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Process-wide access to the song library. Call [ensureLoaded] off the main thread. */
object SongbookRepository {

    private var store: LibraryStore? = null
    private val _library = MutableStateFlow(Library.EMPTY)
    val library: StateFlow<Library> = _library.asStateFlow()

    private val _audioFiles = MutableStateFlow<List<String>>(emptyList())
    /** Names of the stored practice recordings. */
    val audioFiles: StateFlow<List<String>> = _audioFiles.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    @Synchronized
    fun ensureLoaded(filesDir: File) {
        if (store != null) return
        val s = LibraryStore(filesDir)
        store = s
        _library.value = s.load()
        _audioFiles.value = s.audioFiles()
        _loaded.value = true
    }

    /** Imports an uploaded file and reloads the library. Returns a short summary for display. */
    @Synchronized
    fun import(filesDir: File, bytes: ByteArray, fileName: String): String {
        val s = store ?: LibraryStore(filesDir).also { store = it }
        val summary = s.import(bytes, fileName)
        if (summary.audioFile != null) {
            _audioFiles.value = s.audioFiles()
            val file = summary.audioFile
            val song = _library.value.songs
                .filter { Audio.match(it, listOf(file)) != null }
                .maxByOrNull { it.title.length }
            return summary.message + (song?.let { " → ${it.title}" } ?: "")
        }
        _library.value = s.load()
        _loaded.value = true
        return summary.message
    }

    fun audioFile(name: String): File? = store?.audioFile(name)

    // ---- own setlists and removed songs ----

    private fun editSets(change: (List<UserSet>) -> List<UserSet>) {
        val s = store ?: return
        s.userData.saveSets(change(s.userData.loadSets()))
        _library.value = s.load()
    }

    private fun editSet(setId: String, change: (UserSet) -> UserSet) =
        editSets { sets -> sets.map { if (it.id == setId) change(it) else it } }

    /** Creates an empty setlist and returns its id. */
    @Synchronized
    fun createSet(name: String, firstSongId: String? = null): String {
        val set = UserData.newSet(name).let { if (firstSongId != null) it.copy(songIds = listOf(firstSongId)) else it }
        editSets { listOf(set) + it }
        return set.id
    }

    @Synchronized
    fun renameSet(setId: String, name: String) =
        editSet(setId) { it.copy(name = name.trim().ifEmpty { it.name }) }

    @Synchronized
    fun deleteSet(setId: String) = editSets { sets -> sets.filter { it.id != setId } }

    @Synchronized
    fun addToSet(setId: String, songId: String) =
        editSet(setId) { it.copy(songIds = it.songIds + songId) }

    /** Index refers to the visible entries of the set (removed songs are skipped). */
    @Synchronized
    fun removeFromSet(setId: String, index: Int) = editSet(setId) { set ->
        val visible = visibleIds(set)
        val songId = visible.getOrNull(index) ?: return@editSet set
        val occurrence = visible.take(index).count { it == songId }
        var seen = 0
        val position = set.songIds.indexOfFirst { it == songId && seen++ == occurrence }
        if (position < 0) set else set.copy(songIds = set.songIds.toMutableList().also { it.removeAt(position) })
    }

    @Synchronized
    fun moveInSet(setId: String, index: Int, delta: Int) = editSet(setId) { set ->
        with(UserData) {
            // reorder within the visible songs, keep hidden ones at the end
            val visible = visibleIds(set)
            val hidden = set.songIds.filter { it !in _library.value.songIds }
            set.copy(songIds = visible.moved(index, delta) + hidden)
        }
    }

    private fun visibleIds(set: UserSet): List<String> {
        val ids = _library.value.songIds
        return set.songIds.filter { it in ids }
    }

    @Synchronized
    fun hideSong(songId: String) {
        val s = store ?: return
        s.userData.saveHidden(s.userData.loadHidden() + songId)
        _library.value = s.load()
    }

    @Synchronized
    fun restoreSong(songId: String) {
        val s = store ?: return
        s.userData.saveHidden(s.userData.loadHidden() - songId)
        _library.value = s.load()
    }

    @Synchronized
    fun deleteAudio(name: String) {
        val s = store ?: return
        s.deleteAudio(name)
        _audioFiles.value = s.audioFiles()
    }
}
