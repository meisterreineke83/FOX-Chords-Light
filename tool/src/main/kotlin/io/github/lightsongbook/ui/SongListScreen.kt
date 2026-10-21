package io.github.lightsongbook.ui

import androidx.compose.foundation.background
import com.thelightphone.sdk.ui.lightClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import io.github.lightsongbook.core.Song
import io.github.lightsongbook.core.SongbookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SongListViewModel : LightViewModel<Unit>() {
    val query = MutableStateFlow("")

    val results: StateFlow<List<Song>> = combine(SongbookRepository.library, query) { library, q ->
        library.search(q)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SongbookRepository.library.value.sortedSongs)
}

class SongListScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SongListViewModel>(sealedActivity) {

    override val viewModelClass: Class<SongListViewModel>
        get() = SongListViewModel::class.java

    override fun createViewModel() = SongListViewModel()

    @Composable
    override fun Content() {
        val results by viewModel.results.collectAsState()
        val query by viewModel.query.collectAsState()

        ToolScaffold(
            title = "Songs",
            onBack = { goBack() },
            rightButton = LightBarButton.LightIcon(
                icon = LightIcons.SEARCH,
                onClick = {
                    navigateTo(
                        screenFactory = { SearchEditorScreen(it, query) },
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
            if (results.isEmpty()) {
                HintText(if (query.isEmpty()) "Noch keine Songs importiert." else "Keine Treffer.")
            } else {
                LightLazyScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    uniformItemHeightGridUnits = LIST_ROW_UNITS,
                ) {
                    items(results, key = { it.id }) { song ->
                        TwoLineRow(
                            title = song.title,
                            detail = song.artist,
                            onClick = {
                                val ids = results.map { it.id }
                                val request = SongRequest.fromList(ids, ids.indexOf(song.id))
                                navigateTo({ SongScreen(it, request) })
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Full-screen keyboard for entering a search term. */
class SearchEditorScreen(
    sealedActivity: SealedLightActivity,
    private val initial: String,
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val textState = rememberTextFieldState(initial)
        val themeColors by LightThemeController.colors.collectAsState()
        val keyboardOptionsFlow = rememberKeyboardOptions()
        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = "Suche",
                state = textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { result -> goBack(result.toString()) },
                onBack = { goBack(null) },
                submitLabel = "SUCHEN",
                submitIcon = LightIcons.SEARCH,
                singleLine = true,
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
    }
}
