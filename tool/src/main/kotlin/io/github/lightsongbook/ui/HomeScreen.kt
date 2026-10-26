package io.github.lightsongbook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightThemeColors
import com.thelightphone.sdk.ui.LightThemeController
import io.github.lightsongbook.core.CrashLog
import io.github.lightsongbook.core.SongbookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class HomeViewModel(
    private val filesDir: File,
    dataStore: DataStore<Preferences>,
) : LightViewModel<Unit>() {

    val prefs = Prefs(dataStore)
    val library = SongbookRepository.library
    val loaded = SongbookRepository.loaded

    val lastCrash = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    init {
        CrashLog.install(filesDir)
        viewModelScope.launch(Dispatchers.IO) {
            lastCrash.value = CrashLog.read()
            prefs.applyTheme()
            SongbookRepository.ensureLoaded(filesDir)
        }
    }

    fun clearCrash() {
        CrashLog.clear()
        lastCrash.value = null
    }

    fun toggleTheme() {
        viewModelScope.launch { prefs.toggleTheme() }
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HomeViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeViewModel>
        get() = HomeViewModel::class.java

    override fun createViewModel() = HomeViewModel(lightContext.filesDir, lightContext.dataStore)

    @Composable
    override fun Content() {
        val library by viewModel.library.collectAsState()
        val loaded by viewModel.loaded.collectAsState()
        val colors by LightThemeController.colors.collectAsState()
        val isDark = colors == LightThemeColors.Dark
        val lastCrash by viewModel.lastCrash.collectAsState()
        val audioFiles by SongbookRepository.audioFiles.collectAsState()

        ToolScaffold(title = "Chords", onBack = null) {
            MenuItem("SONGS", value = library.songs.size.toString()) {
                navigateTo(::SongListScreen)
            }
            MenuItem("SETLISTS", value = library.sets.size.toString()) {
                navigateTo(::SetListsScreen)
            }
            MenuItem("AUFNAHMEN", value = audioFiles.size.toString()) {
                navigateTo(::AudioListScreen)
            }
            if (library.hiddenSongs.isNotEmpty()) {
                MenuItem("ENTFERNT", value = library.hiddenSongs.size.toString()) {
                    navigateTo(::HiddenSongsScreen)
                }
            }
            MenuItem("STIMMGERÄT") {
                navigateTo(::TunerScreen)
            }
            MenuItem("IMPORT") {
                navigateTo(::ImportScreen)
            }
            MenuItem("ANZEIGE", value = if (isDark) "DUNKEL" else "HELL") {
                viewModel.toggleTheme()
            }
            lastCrash?.let { report ->
                MenuItem("LETZTER FEHLER") {
                    navigateTo({ CrashScreen(it, report) }) { viewModel.clearCrash() }
                }
            }
            if (loaded && library.songs.isEmpty()) {
                HintText(
                    "Noch keine Songs. Tippe auf IMPORT, um dein Songbook-Pro-Backup vom iPad zu übertragen."
                )
            }
        }
    }
}
