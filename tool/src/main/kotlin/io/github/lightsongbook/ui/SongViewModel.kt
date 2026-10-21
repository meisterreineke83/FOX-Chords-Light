package io.github.lightsongbook.ui

import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import io.github.lightsongbook.core.ChordPro
import io.github.lightsongbook.core.Library
import io.github.lightsongbook.core.SetList
import io.github.lightsongbook.core.Song
import io.github.lightsongbook.core.SongRendering
import io.github.lightsongbook.core.SongbookRepository
import io.github.lightsongbook.core.Transposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which songs the song screen can step through, and where to start. */
data class SongRequest(
    val items: List<Item>,
    val startIndex: Int,
    val setId: String? = null,
    val setName: String? = null,
) {
    data class Item(val songId: String, val keyOffset: Int = 0, val capo: Int = 0)

    companion object {
        fun fromList(songIds: List<String>, index: Int) =
            SongRequest(songIds.map { Item(it) }, index.coerceIn(0, (songIds.size - 1).coerceAtLeast(0)))

        fun fromSet(set: SetList, index: Int) = SongRequest(
            items = set.entries.map { Item(it.songId, it.keyOffset, it.capo) },
            startIndex = index,
            setId = set.id,
            setName = set.name,
        )
    }
}

class SongViewModel(
    private val request: SongRequest,
    private val prefs: Prefs,
) : LightViewModel<Unit>() {

    data class Ui(
        val song: Song,
        val blocks: List<SongRendering.Block>,
        /** e.g. "C" – sounding key, or the shift if the key is unknown */
        val keyLabel: String,
        /** e.g. "Capo 2 (A) · +2 · 3/12" */
        val info: String,
        val hasPrev: Boolean,
        val hasNext: Boolean,
        val nextTitle: String?,
        val isList: Boolean,
    )

    private val index = MutableStateFlow(request.startIndex)

    /** Total shift of the current song; null until loaded from preferences. */
    private val shift = MutableStateFlow<Int?>(null)

    val playing = MutableStateFlow(false)

    val fontStep: StateFlow<Int> =
        prefs.fontStep.stateIn(viewModelScope, SharingStarted.Eagerly, Prefs.DEFAULT_FONT_STEP)

    val scrollSpeed: StateFlow<Int> =
        prefs.scrollSpeed.stateIn(viewModelScope, SharingStarted.Eagerly, Prefs.DEFAULT_SCROLL_SPEED)

    val ui: StateFlow<Ui?> = combine(SongbookRepository.library, index, shift) { library, i, total ->
        build(library, i, total)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        loadShift()
    }

    private fun item(i: Int = index.value): SongRequest.Item? = request.items.getOrNull(i)

    private fun baseShift(song: Song, item: SongRequest.Item) = song.keyShift + item.keyOffset

    private fun prefKey(item: SongRequest.Item) =
        request.setId?.let { "set:$it:${item.songId}" } ?: "song:${item.songId}"

    private fun capoFor(song: Song, item: SongRequest.Item) = if (item.capo > 0) item.capo else song.capo

    private fun build(library: Library, i: Int, total: Int?): Ui? {
        val item = item(i) ?: return null
        val song = library.song(item.songId) ?: return null
        val base = baseShift(song, item)
        val shiftTotal = total ?: base
        val capo = capoFor(song, item)
        val t = Transposition.of(song, shiftTotal, capo)
        val lines = SongRendering.transpose(ChordPro.parse(song.content), t)

        val delta = shiftTotal - base
        val info = buildList {
            if (capo > 0) add(if (t.shapeKey != null) "Capo $capo (${t.shapeKey})" else "Capo $capo")
            if (song.tempo > 0) add("${song.tempo} BPM")
            if (delta != 0) add(if (delta > 0) "+$delta" else "$delta")
            if (request.items.size > 1) add("${i + 1}/${request.items.size}")
        }.joinToString(" · ")

        val keyLabel = t.soundingKey ?: when {
            shiftTotal > 0 -> "+$shiftTotal"
            shiftTotal < 0 -> "$shiftTotal"
            else -> "±0"
        }
        val nextTitle = item(i + 1)?.let { library.song(it.songId)?.title }

        return Ui(
            song = song,
            blocks = SongRendering.blocks(lines),
            keyLabel = keyLabel,
            info = info,
            hasPrev = i > 0,
            hasNext = i < request.items.size - 1,
            nextTitle = nextTitle,
            isList = request.items.size > 1,
        )
    }

    private fun loadShift() {
        val item = item() ?: return
        viewModelScope.launch {
            val library = SongbookRepository.library.value
            val song = library.song(item.songId) ?: return@launch
            shift.value = prefs.transposition(prefKey(item), baseShift(song, item))
        }
    }

    fun transpose(delta: Int) {
        val item = item() ?: return
        val song = SongbookRepository.library.value.song(item.songId) ?: return
        val base = baseShift(song, item)
        var relative = (shift.value ?: base) + delta - base
        if (relative > 11) relative -= 12
        if (relative < -11) relative += 12
        val total = base + relative
        shift.value = total
        viewModelScope.launch { prefs.setTransposition(prefKey(item), base, total) }
    }

    fun resetTranspose() {
        val item = item() ?: return
        val song = SongbookRepository.library.value.song(item.songId) ?: return
        val base = baseShift(song, item)
        shift.value = base
        viewModelScope.launch { prefs.setTransposition(prefKey(item), base, base) }
    }

    fun changeFont(delta: Int) {
        viewModelScope.launch { prefs.setFontStep(fontStep.value + delta) }
    }

    fun changeSpeed(delta: Int) {
        viewModelScope.launch { prefs.setScrollSpeed(scrollSpeed.value + delta) }
    }

    fun togglePlaying() {
        playing.value = !playing.value
    }

    fun stopPlaying() {
        playing.value = false
    }

    fun next() = goTo(index.value + 1)

    fun previous() = goTo(index.value - 1)

    private fun goTo(i: Int) {
        if (i !in request.items.indices) return
        playing.value = false
        shift.value = null
        index.value = i
        loadShift()
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) {
        super.onScreenHide(screen)
        playing.value = false
    }
}
