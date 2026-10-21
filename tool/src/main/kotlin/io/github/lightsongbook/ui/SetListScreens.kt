package io.github.lightsongbook.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightLazyScrollView
import io.github.lightsongbook.core.SongbookRepository

class LibraryViewModel : LightViewModel<Unit>() {
    val library = SongbookRepository.library
}

class SetListsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, LibraryViewModel>(sealedActivity) {

    override val viewModelClass: Class<LibraryViewModel>
        get() = LibraryViewModel::class.java

    override fun createViewModel() = LibraryViewModel()

    @Composable
    override fun Content() {
        val library by viewModel.library.collectAsState()

        ToolScaffold(title = "Setlists", onBack = { goBack() }) {
            if (library.sets.isEmpty()) {
                HintText("Keine Setlists. Setlists kommen mit dem Songbook-Pro-Backup.")
            } else {
                LightLazyScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    uniformItemHeightGridUnits = LIST_ROW_UNITS,
                ) {
                    itemsIndexed(library.sets, key = { _, set -> set.id }) { _, set ->
                        val count = set.entries.size
                        val detail = listOf(if (count == 1) "1 Song" else "$count Songs", set.date)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        TwoLineRow(
                            title = set.name,
                            detail = detail,
                            onClick = { navigateTo({ SetDetailScreen(it, set.id) }) },
                        )
                    }
                }
            }
        }
    }
}

class SetDetailScreen(
    sealedActivity: SealedLightActivity,
    private val setId: String,
) : LightScreen<Unit, LibraryViewModel>(sealedActivity) {

    override val viewModelClass: Class<LibraryViewModel>
        get() = LibraryViewModel::class.java

    override fun createViewModel() = LibraryViewModel()

    @Composable
    override fun Content() {
        val library by viewModel.library.collectAsState()
        val set = library.set(setId)

        ToolScaffold(title = set?.name ?: "Setlist", onBack = { goBack() }) {
            if (set == null || set.entries.isEmpty()) {
                HintText("Diese Setlist ist leer.")
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
                    TwoLineRow(
                        leading = "${index + 1}",
                        title = song?.title ?: "Unbekannter Song",
                        detail = song?.artist,
                        onClick = { navigateTo({ SongScreen(it, SongRequest.fromSet(set, index)) }) },
                    )
                }
            }
        }
    }
}
