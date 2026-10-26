package io.github.lightsongbook.ui

import androidx.compose.foundation.ScrollState
import com.thelightphone.sdk.ui.lightClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.audio.DefaultLightAudio
import com.thelightphone.sdk.audio.LightAudioPlayer
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBarItem
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import io.github.lightsongbook.core.Audio
import io.github.lightsongbook.core.SongLine
import io.github.lightsongbook.core.SongRendering
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/** Lyric font sizes for the A−/A+ buttons, in sp. */
private val FONT_SIZES = listOf(13f, 15f, 17f, 20f, 23f, 27f)

/** Auto-scroll speeds (levels 1–15), in dp per second. */
private val SCROLL_SPEEDS_DP = listOf(3f, 4f, 5f, 6f, 8f, 10f, 12f, 15f, 18f, 22f, 27f, 33f, 40f, 50f, 62f)

class SongScreen(
    private val sealed: SealedLightActivity,
    private val request: SongRequest,
) : LightScreen<Unit, SongViewModel>(sealed) {

    override val viewModelClass: Class<SongViewModel>
        get() = SongViewModel::class.java

    override fun createViewModel() =
        SongViewModel(request, Prefs(lightContext.dataStore), DefaultLightAudio(sealed))

    @Composable
    override fun Content() {
        val ui by viewModel.ui.collectAsState()
        val fontStep by viewModel.fontStep.collectAsState()
        val playing by viewModel.playing.collectAsState()
        val speed by viewModel.scrollSpeed.collectAsState()
        val player by viewModel.player.collectAsState()
        val audioName by viewModel.audioName.collectAsState()
        val audioSpeed by viewModel.audioSpeed.collectAsState()
        val audioPlaying by viewModel.audioPlaying.collectAsState()
        val hasAudioFiles by viewModel.hasAudioFiles.collectAsState()
        val message by viewModel.message.collectAsState()
        val removeArmed by viewModel.removeArmed.collectAsState()
        val current = ui

        ToolScaffold(
            title = current?.song?.title ?: "",
            subtitle = current?.song?.artist,
            onBack = { goBack() },
            bottomBar = current?.let { bottomBarItems(it, playing) },
        ) {
            if (current == null) {
                HintText("Song wird geladen …")
                return@ToolScaffold
            }

            val synced = playing && audioPlaying && audioName != null
            ControlsRow(
                keyLabel = current.keyLabel,
                info = current.info,
                playing = playing,
                speed = speed,
                synced = synced,
            )

            val activePlayer = player
            if (audioName != null && activePlayer != null) {
                AudioRow(activePlayer, audioSpeed)
            }

            val scrollState = remember(current.song.id) { ScrollState(0) }
            AutoScroll(scrollState, playing, speed, current.song.id, if (audioName != null) player else null, audioSpeed)

            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (playing || audioPlaying) Modifier.keepScreenOn() else Modifier)
                    .padding(start = 1f.gridUnitsAsDp()),
                scrollState = scrollState,
            ) {
                if (current.error != null) {
                    LightText(text = "Dieser Song konnte nicht angezeigt werden:", variant = LightTextVariant.Paragraph)
                    Text(text = current.error, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = LightThemeTokens.colors.content))
                } else {
                    SongBody(current.blocks, fontStep)
                }

                if (current.song.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                    LightText(text = "NOTIZEN", variant = LightTextVariant.Detail, lighten = true)
                    LightText(text = current.song.notes, variant = LightTextVariant.Paragraph)
                }

                if (hasAudioFiles) {
                    LightText(
                        text = if (audioName != null) "AUFNAHME: $audioName" else "AUFNAHME ZUORDNEN",
                        variant = LightTextVariant.Detail,
                        lighten = audioName != null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable { openAudioPicker(current.song.title, audioName) }
                            .padding(top = 1.5f.gridUnitsAsDp(), bottom = 0.5f.gridUnitsAsDp()),
                    )
                }

                if (current.nextTitle != null) {
                    LightText(
                        text = "WEITER: ${current.nextTitle}  →",
                        variant = LightTextVariant.Copy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable { viewModel.next() }
                            .padding(vertical = 1.5f.gridUnitsAsDp()),
                    )
                }
                FooterLink(message ?: "ZU SETLIST HINZUFÜGEN", lighten = message != null) { openSetPicker() }
                FooterLink(if (removeArmed) "NOCHMAL TIPPEN ZUM ENTFERNEN" else "SONG ENTFERNEN", lighten = !removeArmed) {
                    if (viewModel.removeTapped()) goBack()
                }

                // room to scroll the last lines up to eye level
                Spacer(modifier = Modifier.height(6f.gridUnitsAsDp()))
            }
        }
    }

    @Composable
    private fun FooterLink(text: String, lighten: Boolean, onClick: () -> Unit) {
        LightText(
            text = text,
            variant = LightTextVariant.Detail,
            lighten = lighten,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .lightClickable(onClick = onClick)
                .padding(vertical = 0.75f.gridUnitsAsDp()),
        )
    }

    private fun openSetPicker() {
        navigateTo(
            screenFactory = { SetPickerScreen(it) },
            resultCallback = { setId ->
                if (setId.isEmpty()) {
                    navigateTo(
                        screenFactory = {
                            TextEditorScreen(it, "", title = "Neue Setlist", submitLabel = "ANLEGEN", isSearch = false)
                        },
                        resultCallback = { name -> viewModel.createSetWithCurrent(name) },
                    )
                } else {
                    viewModel.addToSet(setId)
                }
            },
        )
    }

    private fun openAudioPicker(title: String, current: String?) {
        navigateTo(
            screenFactory = { AudioPickerScreen(it, title, current) },
            resultCallback = { choice -> viewModel.assignAudio(choice.fileName) },
        )
    }

    @Composable
    private fun AudioRow(player: LightAudioPlayer, audioSpeed: Float) {
        val position by player.positionMs.collectAsState()
        val duration by player.durationMs.collectAsState()
        val isPlaying by player.isPlaying.collectAsState()
        val error by player.error.collectAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 0.5f.gridUnitsAsDp(), end = 0.5f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlText(if (isPlaying) "PAUSE" else "AUDIO") { viewModel.toggleAudio() }
            ControlText("‹10") { viewModel.skipAudio(-10_000) }
            LightText(
                text = if (error != null) "Fehler" else "${Audio.formatTime(position)}/${Audio.formatTime(duration)}",
                variant = LightTextVariant.Detail,
                lighten = true,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            ControlText("−") { viewModel.changeAudioSpeed(-1) }
            LightText(
                text = "${(audioSpeed * 100).roundToInt()}%",
                variant = LightTextVariant.Detail,
                maxLines = 1,
            )
            ControlText("+") { viewModel.changeAudioSpeed(1) }
        }
    }

    private fun bottomBarItems(ui: SongViewModel.Ui, playing: Boolean): List<LightBottomBarItem?> {
        val play = LightBarButton.LightIcon(
            icon = if (playing) LightIcons.PAUSE else LightIcons.PLAY,
            onClick = { viewModel.togglePlaying() },
        )
        if (!ui.isList) return listOf(play)
        val prev = if (ui.hasPrev) LightBarButton.LightIcon(icon = LightIcons.UP, onClick = { viewModel.previous() }) else null
        val next = if (ui.hasNext) LightBarButton.LightIcon(icon = LightIcons.DOWN, onClick = { viewModel.next() }) else null
        return listOf(prev, play, next)
    }

    @Composable
    private fun ControlsRow(keyLabel: String, info: String, playing: Boolean, speed: Int, synced: Boolean) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.5f.gridUnitsAsDp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlText("−") { viewModel.transpose(-1) }
            LightText(
                text = keyLabel,
                variant = LightTextVariant.Copy,
                modifier = Modifier
                    .lightClickable { viewModel.resetTranspose() }
                    .padding(horizontal = 0.5f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
            )
            ControlText("+") { viewModel.transpose(1) }
            Spacer(modifier = Modifier.weight(1f))
            ControlText("A−") { viewModel.changeFont(-1) }
            ControlText("A+") { viewModel.changeFont(1) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 1f.gridUnitsAsDp(), end = 0.5f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText(
                text = info,
                variant = LightTextVariant.Detail,
                lighten = true,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            ControlText("−") { viewModel.changeSpeed(-1) }
            LightText(
                text = when {
                    synced -> "▶ MIT MP3"
                    playing -> "▶ $speed"
                    else -> "SCROLL $speed"
                },
                variant = LightTextVariant.Detail,
                modifier = Modifier
                    .lightClickable { viewModel.toggleScrolling() }
                    .padding(vertical = 0.5f.gridUnitsAsDp()),
            )
            ControlText("+") { viewModel.changeSpeed(1) }
        }
    }

    @Composable
    private fun ControlText(label: String, onClick: () -> Unit) {
        LightText(
            text = label,
            variant = LightTextVariant.Copy,
            modifier = Modifier
                .lightClickable(onClick = onClick)
                .padding(horizontal = 0.75f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
        )
    }

    /**
     * Scrolls while [playing]. If the song's recording is playing, the speed is chosen so the
     * text reaches its end together with the recording (recomputed every frame, so manual
     * scrolling and tempo changes are absorbed); otherwise the manual speed level is used.
     */
    @Composable
    private fun AutoScroll(
        state: ScrollState,
        playing: Boolean,
        speed: Int,
        songId: String,
        player: LightAudioPlayer?,
        audioSpeed: Float,
    ) {
        val density = LocalDensity.current
        val currentAudioSpeed by rememberUpdatedState(audioSpeed)
        LaunchedEffect(playing, speed, songId, player) {
            if (!playing) return@LaunchedEffect
            val manualPxPerSecond = with(density) { SCROLL_SPEEDS_DP[(speed - 1).coerceIn(0, SCROLL_SPEEDS_DP.lastIndex)].dp.toPx() }
            var last = withFrameNanos { it }
            while (isActive) {
                val now = withFrameNanos { it }
                val seconds = (now - last) / 1_000_000_000f
                last = now
                val duration = player?.durationMs?.value ?: 0L
                val synced = player != null && player.isPlaying.value && duration > 0
                if (!synced && state.maxValue > 0 && state.value >= state.maxValue) {
                    viewModel.stopPlaying()
                    break
                }
                val pxPerSecond = if (synced) {
                    val remainingSeconds = (duration - player!!.positionMs.value) / 1000f / currentAudioSpeed.coerceAtLeast(0.1f)
                    val remainingPx = (state.maxValue - state.value).toFloat()
                    if (remainingSeconds > 1f) remainingPx / remainingSeconds else 0f
                } else {
                    manualPxPerSecond
                }
                if (pxPerSecond > 0f) state.scrollBy(pxPerSecond * seconds)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongBody(blocks: List<SongRendering.Block>, fontStep: Int) {
    val size = FONT_SIZES[fontStep.coerceIn(0, FONT_SIZES.lastIndex)].sp
    val colors = LightThemeTokens.colors
    val family = LightThemeTokens.typography.copy.fontFamily
    val lyricStyle = TextStyle(
        fontSize = size,
        lineHeight = size * 1.25f,
        fontFamily = family,
        color = colors.content,
    )
    val chordStyle = lyricStyle.copy(fontWeight = FontWeight.Bold)
    val sectionStyle = lyricStyle.copy(
        fontSize = size * 0.8f,
        lineHeight = size,
        fontWeight = FontWeight.Bold,
        color = colors.contentSecondary,
    )
    val monoStyle = lyricStyle.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = size * 0.8f,
        lineHeight = size,
    )
    val density = LocalDensity.current
    val spaceWidth = with(density) { (size * 0.3f).toDp() }
    val blankHeight = with(density) { (size * 0.7f).toDp() }

    blocks.forEachIndexed { index, block ->
        key(index) {
            when (block) {
                is SongRendering.Block.Mono -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        for (text in block.lines) {
                            Text(text = text.ifEmpty { " " }, style = monoStyle, softWrap = false, maxLines = 1)
                        }
                    }
                }
                is SongRendering.Block.Line -> when (val line = block.line) {
                    is SongLine.Blank -> Spacer(modifier = Modifier.height(blankHeight))

                    is SongLine.Section -> Text(
                        text = line.label.uppercase(),
                        style = sectionStyle,
                        modifier = Modifier.padding(top = spaceWidth, bottom = spaceWidth / 2),
                    )

                    is SongLine.Mono -> Text(text = line.text, style = monoStyle, softWrap = false)

                    is SongLine.Lyrics -> if (!line.hasChords) {
                        Text(
                            text = line.words.joinToString("") { w -> w.parts.joinToString("") { it.text } },
                            style = lyricStyle,
                        )
                    } else {
                        FlowRow(modifier = Modifier.fillMaxWidth()) {
                            for (word in line.words) {
                                Row {
                                    for (part in word.parts) {
                                        val text = part.text.trimEnd()
                                        val chord = part.chord
                                        val needsGap = part.text.endsWith(' ') ||
                                            (chord != null && chord.length >= text.length)
                                        Column(modifier = Modifier.padding(end = if (needsGap) spaceWidth else 0.dp)) {
                                            Text(text = chord ?: "", style = chordStyle, maxLines = 1, softWrap = false)
                                            Text(text = text, style = lyricStyle, maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
