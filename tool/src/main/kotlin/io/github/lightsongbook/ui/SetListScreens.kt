package io.github.lightsongbook.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIconConfiguration
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import io.github.lightsongbook.core.SetList
import io.github.lightsongbook.core.SongbookRepository
import androidx.lifecycle.viewModelScope
import io.github.lightsongbook.core.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class LibraryViewModel : LightViewModel<Unit>() {
    val library = SongbookRepository.library
}

private fun setDetail(set: SetList): String {
    val count = set.entries.size
    return listOf(
        if (set.editable) "eigene" else "",
        if (count == 1) "1 Song" else "$count Songs",
        set.date,
    ).filter { it.isNotBlank() }.joinToString(" · ")
}

class SetListsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, LibraryViewModel>(sealedActivity) {

    override val viewModelClass: Class<LibraryViewModel>
        get() = LibraryViewModel::class.java

    override fun createViewModel() = LibraryViewModel()

    @Composable
    override fun Content() {
        val library by viewModel.library.collectAsState()

        ToolScaffold(
            title = "Setlists",
            onBack = { goBack() },
            rightButton = LightBarButton.LightIcon(icon = LightIcons.ADD, onClick = { createSet() }),
        ) {
            if (library.sets.isEmpty()) {
                HintText("Noch keine Setlists. Mit + legst du eine eigene an; Setlists aus Songbook Pro kommen mit dem Backup.")
            } else {
                LightLazyScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    uniformItemHeightGridUnits = LIST_ROW_UNITS,
                ) {
                    items(library.sets, key = { it.id }) { set ->
                        TwoLineRow(
                            title = set.name,
                            detail = setDetail(set),
                            onClick = { navigateTo({ SetDetailScreen(it, set.id) }) },
                        )
                    }
                }
            }
        }
    }

    private fun createSet() {
        navigateTo(
            screenFactory = { TextEditorScreen(it, "", title = "Neue Setlist", submitLabel = "ANLEGEN", isSearch = false) },
            resultCallback = { name ->
                if (name.isNotBlank()) {
                    val id = SongbookRepository.createSet(name)
                    navigateTo({ SetDetailScreen(it, id, startEditing = true) })
                }
            },
        )
    }
}

class SetDetailViewModel(startEditing: Boolean) : LightViewModel<Unit>() {
    val library = SongbookRepository.library
    val editing = MutableStateFlow(startEditing)

    /** Name of the entry that will be deleted on the next tap ("" = the whole setlist). */
    val armedDelete = MutableStateFlow<String?>(null)
}

class SetDetailScreen(
    sealedActivity: SealedLightActivity,
    private val setId: String,
    private val startEditing: Boolean = false,
) : LightScreen<Unit, SetDetailViewModel>(sealedActivity) {

    override val viewModelClass: Class<SetDetailViewModel>
        get() = SetDetailViewModel::class.java

    override fun createViewModel() = SetDetailViewModel(startEditing)

    @Composable
    override fun Content() {
        val library by viewModel.library.collectAsState()
        val editing by viewModel.editing.collectAsState()
        val armed by viewModel.armedDelete.collectAsState()
        val set = library.set(setId)

        if (set == null) {
            ToolScaffold(title = "Setlist", onBack = { goBack() }) {
                HintText("Diese Setlist gibt es nicht mehr.")
            }
            return
        }

        val isEditing = editing && set.editable
        ToolScaffold(
            title = set.name,
            onBack = { goBack() },
            rightButton = if (set.editable) {
                LightBarButton.LightIcon(
                    icon = if (isEditing) LightIcons.ACCEPT else LightIcons.PENCIL,
                    onClick = {
                        viewModel.editing.value = !isEditing
                        viewModel.armedDelete.value = null
                    },
                )
            } else {
                null
            },
            bottomBar = if (isEditing) {
                listOf(
                    LightBarButton.Text(text = "+ SONG", onClick = { addSong() }),
                    LightBarButton.Text(text = "NAME", onClick = { rename(set.name) }),
                    LightBarButton.Text(
                        text = if (armed == "") "SICHER?" else "LÖSCHEN",
                        onClick = {
                            if (armed == "") {
                                SongbookRepository.deleteSet(setId)
                                goBack()
                            } else {
                                viewModel.armedDelete.value = ""
                            }
                        },
                    ),
                )
            } else {
                null
            },
        ) {
            if (set.entries.isEmpty()) {
                HintText(
                    if (set.editable) "Noch leer. Mit „+ SONG“ fügst du Songs hinzu – oder im Song unten „ZU SETLIST HINZUFÜGEN“."
                    else "Diese Setlist ist leer."
                )
                return@ToolScaffold
            }
            LightLazyScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                uniformItemHeightGridUnits = LIST_ROW_UNITS,
            ) {
                itemsIndexed(set.entries) { index, entry ->
                    val song = library.song(entry.songId)
                    val title = song?.title ?: "Unbekannter Song"
                    if (isEditing) {
                        EditRow(
                            number = index + 1,
                            title = title,
                            armed = armed == "$index",
                            canMoveUp = index > 0,
                            canMoveDown = index < set.entries.size - 1,
                            onUp = { SongbookRepository.moveInSet(setId, index, -1) },
                            onDown = { SongbookRepository.moveInSet(setId, index, 1) },
                            onRemove = {
                                if (armed == "$index") {
                                    SongbookRepository.removeFromSet(setId, index)
                                    viewModel.armedDelete.value = null
                                } else {
                                    viewModel.armedDelete.value = "$index"
                                }
                            },
                        )
                    } else {
                        TwoLineRow(
                            leading = "${index + 1}",
                            title = title,
                            detail = song?.artist,
                            onClick = { navigateTo({ SongScreen(it, SongRequest.fromSet(set, index)) }) },
                        )
                    }
                }
            }
        }
    }

    private fun addSong() {
        navigateTo(
            screenFactory = { SongPickerScreen(it) },
            resultCallback = { songId -> SongbookRepository.addToSet(setId, songId) },
        )
    }

    private fun rename(current: String) {
        navigateTo(
            screenFactory = { TextEditorScreen(it, current, title = "Name der Setlist", submitLabel = "OK", isSearch = false) },
            resultCallback = { name -> SongbookRepository.renameSet(setId, name) },
        )
    }

    @Composable
    private fun EditRow(
        number: Int,
        title: String,
        armed: Boolean,
        canMoveUp: Boolean,
        canMoveDown: Boolean,
        onUp: () -> Unit,
        onDown: () -> Unit,
        onRemove: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(LIST_ROW_UNITS.gridUnitsAsDp())
                .padding(horizontal = 1f.gridUnitsAsDp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText(
                text = "$number",
                variant = LightTextVariant.Copy,
                lighten = true,
                modifier = Modifier.width(2.5f.gridUnitsAsDp()),
            )
            Column(modifier = Modifier.weight(1f)) {
                LightText(
                    text = title,
                    variant = LightTextVariant.Copy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (armed) {
                    LightText(text = "Nochmal ✕ zum Entfernen", variant = LightTextVariant.Detail, lighten = true, maxLines = 1)
                }
            }
            RowIcon(LightIcons.UP, enabled = canMoveUp, onClick = onUp)
            RowIcon(LightIcons.DOWN, enabled = canMoveDown, onClick = onDown)
            RowIcon(LightIcons.CLOSE, enabled = true, onClick = onRemove)
        }
    }

    @Composable
    private fun RowIcon(icon: LightIconConfiguration, enabled: Boolean, onClick: () -> Unit) {
        LightIcon(
            icon = if (enabled) icon else LightIcons.SPACER,
            size = 1.6f,
            modifier = Modifier
                .padding(start = 0.6f.gridUnitsAsDp())
                .lightClickable(enabled = enabled, onClick = onClick),
        )
    }
}

/** Picks a song for a setlist; same search as the song list. Returns the song id. */
class SongPickerScreen(sealedActivity: SealedLightActivity) :
    LightScreen<String, SongPickerViewModel>(sealedActivity) {

    override val viewModelClass: Class<SongPickerViewModel>
        get() = SongPickerViewModel::class.java

    override fun createViewModel() = SongPickerViewModel()

    @Composable
    override fun Content() {
        val results by viewModel.results.collectAsState()
        val query by viewModel.query.collectAsState()

        ToolScaffold(
            title = "Song hinzufügen",
            onBack = { goBack() },
            rightButton = LightBarButton.LightIcon(
                icon = LightIcons.SEARCH,
                onClick = {
                    navigateTo(
                        screenFactory = { TextEditorScreen(it, query) },
                        resultCallback = { viewModel.query.value = it.trim() },
                    )
                },
            ),
        ) {
            if (query.isNotEmpty()) {
                LightText(
                    text = "„$query“  ✕",
                    variant = LightTextVariant.Detail,
                    modifier = Modifier
                        .lightClickable { viewModel.query.value = "" }
                        .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
                )
            }
            LightLazyScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                uniformItemHeightGridUnits = LIST_ROW_UNITS,
            ) {
                items(results, key = { it.id }) { song ->
                    TwoLineRow(title = song.title, detail = song.artist, onClick = { goBack(song.id) })
                }
            }
        }
    }
}

class SongPickerViewModel : LightViewModel<String>() {
    val query = MutableStateFlow("")

    val results: StateFlow<List<Song>> = combine(SongbookRepository.library, query) { library, q ->
        library.search(q)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SongbookRepository.library.value.sortedSongs)
}
