package io.github.lightsongbook.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp

/** Shows the saved crash report so it can be photographed. "LÖSCHEN" returns and clears it. */
class CrashScreen(
    sealedActivity: SealedLightActivity,
    private val report: String,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        ToolScaffold(
            title = "Letzter Fehler",
            onBack = { goBack() },
            bottomBar = listOf(LightBarButton.Text(text = "LÖSCHEN", onClick = { goBack(Unit) })),
        ) {
            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                LightText(
                    text = "Bitte fotografieren und an Claude schicken.",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                )
                Text(
                    text = report,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = LightThemeTokens.colors.content,
                    ),
                )
            }
        }
    }
}
