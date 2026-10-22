package io.github.lightsongbook.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightLazyScrollView
import io.github.lightsongbook.core.SongbookRepository

/** Result of the recording picker. */
data class AudioChoice(
    /** null = automatic by file name, "" = no recording, otherwise the file name */
    val fileName: String?,
)

/** Lets the user pick which recording belongs to a song. */
class AudioPickerScreen(
    sealedActivity: SealedLightActivity,
    private val songTitle: String,
    private val current: String?,
) : SimpleLightScreen<AudioChoice>(sealedActivity) {

    @Composable
    override fun Content() {
        val files by SongbookRepository.audioFiles.collectAsState()

        ToolScaffold(title = "Aufnahme", subtitle = songTitle, onBack = { goBack() }) {
            LightLazyScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                uniformItemHeightGridUnits = LIST_ROW_UNITS,
            ) {
                item {
                    TwoLineRow(
                        title = "AUTOMATISCH",
                        detail = "nach Dateiname",
                        onClick = { goBack(AudioChoice(null)) },
                    )
                }
                item {
                    TwoLineRow(
                        title = "KEINE",
                        detail = null,
                        onClick = { goBack(AudioChoice("")) },
                    )
                }
                items(files, key = { it }) { name ->
                    TwoLineRow(
                        title = name,
                        detail = if (name == current) "aktuell" else null,
                        onClick = { goBack(AudioChoice(name)) },
                    )
                }
            }
        }
    }
}

class AudioListViewModel : LightViewModel<Unit>() {
    val files = SongbookRepository.audioFiles

    fun delete(name: String) {
        SongbookRepository.deleteAudio(name)
    }
}

/** All stored recordings; tapping twice deletes one. */
class AudioListScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, AudioListViewModel>(sealedActivity) {

    override val viewModelClass: Class<AudioListViewModel>
        get() = AudioListViewModel::class.java

    override fun createViewModel() = AudioListViewModel()

    @Composable
    override fun Content() {
        val files by viewModel.files.collectAsState()
        var armed by remember { mutableStateOf<String?>(null) }

        ToolScaffold(title = "Aufnahmen", onBack = { goBack() }) {
            if (files.isEmpty()) {
                HintText("Noch keine Aufnahmen. Lade MP3s über IMPORT hoch, genau wie das Backup.")
                return@ToolScaffold
            }
            LightLazyScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                uniformItemHeightGridUnits = LIST_ROW_UNITS,
            ) {
                items(files, key = { it }) { name ->
                    TwoLineRow(
                        title = name,
                        detail = if (armed == name) "Nochmal tippen zum Löschen" else null,
                        onClick = {
                            if (armed == name) {
                                viewModel.delete(name)
                                armed = null
                            } else {
                                armed = name
                            }
                        },
                    )
                }
            }
        }
    }
}
