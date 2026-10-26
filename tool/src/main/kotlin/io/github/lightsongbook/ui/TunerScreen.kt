package io.github.lightsongbook.ui

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.audio.CaptureConfig
import com.thelightphone.sdk.audio.DefaultLightAudio
import com.thelightphone.sdk.audio.LightAudio
import com.thelightphone.sdk.audio.MicSource
import com.thelightphone.sdk.checkPermission
import com.thelightphone.sdk.rememberPermissionRequestLauncher
import com.thelightphone.sdk.shared.LightServiceMethod
import com.thelightphone.sdk.shared.asKotlinResult
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import io.github.lightsongbook.core.Tuner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

class TunerViewModel(private val audio: LightAudio) : LightViewModel<Unit>() {

    enum class State { Checking, NeedsPermission, Listening, Failed }

    val state = MutableStateFlow(State.Checking)
    val reading = MutableStateFlow<Tuner.Reading?>(null)
    val error = MutableStateFlow<String?>(null)

    private var job: Job? = null
    private val sampleRate = audio.capabilities.sampleRate.takeIf { it > 0 } ?: 48_000

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch {
            val granted = checkPermission(Manifest.permission.RECORD_AUDIO).asKotlinResult
                .map { it.permissionResult == LightServiceMethod.GetPermission.Result.Granted }
                .getOrDefault(false)
            if (granted) start() else state.value = State.NeedsPermission
        }
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) {
        super.onScreenHide(screen)
        stop()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private fun start() {
        if (job?.isActive == true) return
        state.value = State.Listening
        error.value = null
        val capture = audio.newCapture(
            CaptureConfig(sampleRate = sampleRate, bufferFrames = 4096, source = MicSource.Unprocessed),
        )
        val smoother = Tuner.Smoother()
        job = viewModelScope.launch {
            capture.asFlow()
                .conflate()
                .map { samples -> smoother.add(Tuner.detect(samples, sampleRate = sampleRate)) }
                .flowOn(Dispatchers.Default)
                .catch { cause ->
                    error.value = cause.message ?: "Mikrofon nicht verfügbar"
                    state.value = State.Failed
                }
                .collect { f -> reading.value = f?.let { Tuner.reading(it) } }
        }
    }

    private fun stop() {
        job?.cancel()
        job = null
        reading.value = null
    }
}

class TunerScreen(private val sealed: SealedLightActivity) :
    LightScreen<Unit, TunerViewModel>(sealed) {

    override val viewModelClass: Class<TunerViewModel>
        get() = TunerViewModel::class.java

    override fun createViewModel() = TunerViewModel(DefaultLightAudio(sealed))

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        val reading by viewModel.reading.collectAsState()
        val error by viewModel.error.collectAsState()
        val permissionLauncher = rememberPermissionRequestLauncher(Manifest.permission.RECORD_AUDIO)

        ToolScaffold(
            title = "Stimmgerät",
            onBack = { goBack() },
            bottomBar = if (state == TunerViewModel.State.NeedsPermission) {
                listOf(LightBarButton.Text(text = "MIKROFON ERLAUBEN", onClick = { permissionLauncher?.launch() }))
            } else {
                null
            },
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .keepScreenOn()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (state) {
                    TunerViewModel.State.Checking -> HintText("…")
                    TunerViewModel.State.NeedsPermission ->
                        HintText("Das Stimmgerät braucht das Mikrofon. Es hört nur zu, solange diese Seite offen ist, und speichert nichts.")
                    TunerViewModel.State.Failed -> HintText(error ?: "Mikrofon nicht verfügbar")
                    TunerViewModel.State.Listening -> TunerDisplay(reading)
                }
            }
        }
    }
}

@Composable
private fun TunerDisplay(reading: Tuner.Reading?) {
    val colors = LightThemeTokens.colors
    val inTune = reading != null && abs(reading.cents) <= 5

    Text(
        text = reading?.note ?: "–",
        style = TextStyle(
            color = colors.content,
            fontSize = 96.sp,
            lineHeight = 100.sp,
            fontWeight = FontWeight.Light,
            fontFamily = LightThemeTokens.typography.title.fontFamily,
            textAlign = TextAlign.Center,
        ),
    )
    LightText(
        text = reading?.let { "${it.note}${it.octave} · ${"%.1f".format(it.frequency)} Hz" } ?: "Saite anschlagen",
        variant = LightTextVariant.Detail,
        lighten = true,
    )

    // needle: centre line = in tune, marker moves left (too low) or right (too high)
    BoxWithConstraints(
        modifier = Modifier
            .padding(vertical = 2f.gridUnitsAsDp())
            .fillMaxWidth()
            .height(3f.gridUnitsAsDp()),
    ) {
        val half = maxWidth / 2
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .height(3f.gridUnitsAsDp())
                .background(colors.contentSecondary),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.contentSecondary),
        )
        if (reading != null) {
            val offset = half * (reading.cents / 50f) * 0.9f
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = offset)
                    .width(if (inTune) 10.dp else 6.dp)
                    .height(3f.gridUnitsAsDp())
                    .background(colors.content),
            )
        }
    }

    LightText(
        text = when {
            reading == null -> " "
            inTune -> "STIMMT"
            reading.cents < 0 -> "${-reading.cents} Cent zu tief"
            else -> "${reading.cents} Cent zu hoch"
        },
        variant = LightTextVariant.Copy,
    )
}
