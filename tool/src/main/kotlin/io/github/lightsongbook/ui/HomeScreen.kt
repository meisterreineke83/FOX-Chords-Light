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

    init {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.applyTheme()
            SongbookRepository.ensureLoaded(filesDir)
        }
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

        ToolScaffold(title = "Chords", onBack = null) {
            MenuItem("SONGS", value = library.songs.size.toString()) {
                navigateTo(::SongListScreen)
            }
            MenuItem("SETLISTS", value = library.sets.size.toString()) {
                navigateTo(::SetListsScreen)
            }
            MenuItem("IMPORT") {
                navigateTo(::ImportScreen)
            }
            MenuItem("ANZEIGE", value = if (isDark) "DUNKEL" else "HELL") {
                viewModel.toggleTheme()
            }
            if (loaded && library.songs.isEmpty()) {
                HintText(
                    "Noch keine Songs. Tippe auf IMPORT, um dein Songbook-Pro-Backup vom iPad zu übertragen."
                )
            }
        }
    }
}
