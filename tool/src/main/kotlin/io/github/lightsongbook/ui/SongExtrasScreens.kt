package io.github.lightsongbook.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightLazyScrollView
import io.github.lightsongbook.core.SongbookRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** Chooses one of the own setlists. Returns its id, or "" for "new setlist". */
class SetPickerScreen(sealedActivity: SealedLightActivity) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val library by SongbookRepository.library.collectAsState()
        val own = library.sets.filter { it.editable }

        ToolScaffold(title = "Zu Setlist", onBack = { goBack() }) {
            LightLazyScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                uniformItemHeightGridUnits = LIST_ROW_UNITS,
            ) {
                item {
                    TwoLineRow(title = "NEUE SETLIST", detail = null, onClick = { goBack("") })
                }
                items(own, key = { it.id }) { set ->
                    val count = set.entries.size
                    TwoLineRow(
                        title = set.name,
                        detail = if (count == 1) "1 Song" else "$count Songs",
                        onClick = { goBack(set.id) },
                    )
                }
            }
        }
    }
}

class HiddenSongsViewModel : LightViewModel<Unit>() {
    val library = SongbookRepository.library
    val armed = MutableStateFlow<String?>(null)
}

/** Songs removed on the phone; tapping twice brings one back. */
class HiddenSongsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HiddenSongsViewModel>(sealedActivity) {

    override val viewModelClass: Class<HiddenSongsViewModel>
        get() = HiddenSongsViewModel::class.java

    override fun createViewModel() = HiddenSongsViewModel()

    @Composable
    override fun Content() {
        val library by viewModel.library.collectAsState()
        val armed by viewModel.armed.collectAsState()

        ToolScaffold(title = "Entfernte Songs", onBack = { goBack() }) {
            if (library.hiddenSongs.isEmpty()) {
                HintText("Keine entfernten Songs.")
                return@ToolScaffold
            }
            LightLazyScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                uniformItemHeightGridUnits = LIST_ROW_UNITS,
            ) {
                items(library.hiddenSongs, key = { it.id }) { song ->
                    TwoLineRow(
                        title = song.title,
                        detail = if (armed == song.id) "Nochmal tippen zum Wiederherstellen" else song.artist,
                        onClick = {
                            if (armed == song.id) {
                                SongbookRepository.restoreSong(song.id)
                                viewModel.armed.value = null
                            } else {
                                viewModel.armed.value = song.id
                            }
                        },
                    )
                }
            }
        }
    }
}
