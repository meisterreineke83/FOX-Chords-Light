package io.github.lightsongbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lightsongbook.core.Audio
import io.github.lightsongbook.core.SongbookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import com.thelightphone.sdk.ui.lightClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightBottomBarItem
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarButton
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp

/** Height of one row in song and set lists, in LP3 grid units. */
const val LIST_ROW_UNITS = 4f

/**
 * Common frame for every screen: Light theme, top bar with back button,
 * content area and an optional bottom bar.
 */
@Composable
fun ToolScaffold(
    title: String?,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    rightButton: LightTopBarButton? = null,
    bottomBar: List<LightBottomBarItem?>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val themeColors by LightThemeController.colors.collectAsState()
    LightTheme(colors = themeColors) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LightThemeTokens.colors.background),
        ) {
            LightTopBar(
                leftButton = onBack?.let { LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = it) },
                center = when {
                    title == null -> null
                    subtitle.isNullOrBlank() -> LightTopBarCenter.Text(title)
                    else -> LightTopBarCenter.TwoLineDetail(title, subtitle)
                },
                rightButton = rightButton,
                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                content = content,
            )
            if (bottomBar != null) {
                LightBottomBar(
                    items = bottomBar,
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )
            }
        }
    }
}

/** A list row with a main line and an optional lighter second line. */
@Composable
fun TwoLineRow(
    title: String,
    detail: String?,
    onClick: () -> Unit,
    leading: String? = null,
    hasAudio: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LIST_ROW_UNITS.gridUnitsAsDp())
            .lightClickable(onClick = onClick)
            .padding(horizontal = 1f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            LightText(
                text = leading,
                variant = LightTextVariant.Copy,
                lighten = true,
                modifier = Modifier.width(2.5f.gridUnitsAsDp()),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            LightText(
                text = title,
                variant = LightTextVariant.Copy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                LightText(
                    text = detail,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hasAudio) AudioBadge()
    }
}

/** Small ♪ in a circle: this song has a practice recording. */
@Composable
fun AudioBadge() {
    val color = LightThemeTokens.colors.content
    Box(
        modifier = Modifier
            .padding(start = 0.5f.gridUnitsAsDp())
            .size(1.4f.gridUnitsAsDp())
            .border(width = 1.dp, color = color, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "♪", style = TextStyle(color = color, fontSize = 11.sp, lineHeight = 11.sp))
    }
}

/** Ids of all songs with a recording, kept up to date. */
fun songsWithAudio(prefs: Prefs): Flow<Set<String>> =
    combine(SongbookRepository.library, SongbookRepository.audioFiles, prefs.audioAssignments) { library, files, assigned ->
        Audio.songsWithAudio(library.songs, files, assigned)
    }.flowOn(Dispatchers.Default)

/** Upper-case menu entry like in LightOS, with an optional value on the right. */
@Composable
fun MenuItem(label: String, value: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(text = label, variant = LightTextVariant.Copy)
        Spacer(modifier = Modifier.weight(1f))
        if (value != null) {
            LightText(text = value, variant = LightTextVariant.Copy, lighten = true)
        }
    }
}

/** Centered hint text for empty states. */
@Composable
fun HintText(text: String) {
    LightText(
        text = text,
        variant = LightTextVariant.Paragraph,
        lighten = true,
        modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp()),
    )
}
