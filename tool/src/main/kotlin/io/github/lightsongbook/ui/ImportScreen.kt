package io.github.lightsongbook.ui

import com.thelightphone.sdk.ui.lightClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import io.github.lightsongbook.core.SongbookRepository
import io.github.lightsongbook.core.UploadServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class ImportViewModel(private val filesDir: File) : LightViewModel<Unit>() {

    sealed class State {
        object Starting : State()
        object NoNetwork : State()
        data class Ready(val url: String) : State()
        data class Failed(val message: String) : State()
    }

    val state = MutableStateFlow<State>(State.Starting)

    /** Results of uploads, newest first. */
    val log = MutableStateFlow<List<String>>(emptyList())

    @Volatile
    private var server: UploadServer? = null

    @Volatile
    private var active = false

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        start()
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) {
        super.onScreenHide(screen)
        stop()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private var startJob: Job? = null

    fun start() {
        active = true
        if (server != null || startJob?.isActive == true) return
        state.value = State.Starting
        startJob = viewModelScope.launch(Dispatchers.IO) {
            val ip = UploadServer.localIpAddress()
            if (ip == null) {
                state.value = State.NoNetwork
                return@launch
            }
            val s = UploadServer { bytes, name ->
                val message = try {
                    SongbookRepository.import(filesDir, bytes, name)
                } catch (e: Exception) {
                    log.update { listOf("$name: ${e.message ?: "Fehler"}") + it }
                    throw e
                }
                log.update { listOf(message) + it }
                message
            }
            try {
                s.start()
                if (!active) {
                    s.stop()
                    return@launch
                }
                server = s
                state.value = State.Ready("http://$ip:${s.port}/${s.code}")
            } catch (e: Exception) {
                state.value = State.Failed("Der Import konnte nicht gestartet werden.")
            }
        }
    }

    fun stop() {
        active = false
        server?.stop()
        server = null
    }
}

class ImportScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, ImportViewModel>(sealedActivity) {

    override val viewModelClass: Class<ImportViewModel>
        get() = ImportViewModel::class.java

    override fun createViewModel() = ImportViewModel(lightContext.filesDir)

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        val log by viewModel.log.collectAsState()

        ToolScaffold(title = "Import", onBack = { goBack() }) {
            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                when (val s = state) {
                    is ImportViewModel.State.Starting -> Paragraph("Starte …")

                    is ImportViewModel.State.NoNetwork -> {
                        Paragraph("Kein WLAN gefunden. Verbinde das Light Phone mit dem WLAN, in dem auch dein iPad ist.")
                        LightText(
                            text = "ERNEUT VERSUCHEN",
                            variant = LightTextVariant.Copy,
                            modifier = Modifier
                                .padding(vertical = 1f.gridUnitsAsDp())
                                .lightClickable { viewModel.start() },
                        )
                    }

                    is ImportViewModel.State.Failed -> Paragraph(s.message)

                    is ImportViewModel.State.Ready -> {
                        Paragraph("1. In Songbook Pro auf dem iPad ein Backup erstellen und in „Dateien“ sichern.")
                        Paragraph("2. Auf dem iPad (gleiches WLAN) in Safari öffnen:")
                        LightText(
                            text = s.url,
                            variant = LightTextVariant.Subheading,
                            modifier = Modifier.padding(vertical = 1f.gridUnitsAsDp()),
                        )
                        Paragraph("3. Datei auswählen. Fertig.")
                        Paragraph("Übe-Aufnahmen (MP3 …) gehen genauso. Heißt die Datei wie der Song, z. B. „Africa.mp3“, wird sie automatisch zugeordnet.")
                        LightText(
                            text = "Ein neues Backup ersetzt Songs und Setlists. Die Seite funktioniert nur, solange dieser Bildschirm offen ist.",
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                        )
                    }
                }

                for (entry in log) {
                    LightText(
                        text = entry,
                        variant = LightTextVariant.Copy,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                    )
                }
            }
        }
    }

    @Composable
    private fun Paragraph(text: String) {
        LightText(
            text = text,
            variant = LightTextVariant.Paragraph,
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )
    }
}
