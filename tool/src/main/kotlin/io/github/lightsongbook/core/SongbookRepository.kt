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

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    @Synchronized
    fun ensureLoaded(filesDir: File) {
        if (store != null) return
        val s = LibraryStore(filesDir)
        store = s
        _library.value = s.load()
        _loaded.value = true
    }

    /** Imports an uploaded file and reloads the library. Returns a short summary for display. */
    @Synchronized
    fun import(filesDir: File, bytes: ByteArray, fileName: String): String {
        val s = store ?: LibraryStore(filesDir).also { store = it }
        val summary = s.import(bytes, fileName)
        _library.value = s.load()
        _loaded.value = true
        return summary.message
    }
}
