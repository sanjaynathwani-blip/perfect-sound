package app.perfectsound.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.perfectsound.player.audio.AudioLevels
import app.perfectsound.player.audio.SpectrumAnalyzer
import app.perfectsound.player.ui.components.LabelStyle
import app.perfectsound.player.ui.components.LedToggle
import app.perfectsound.player.ui.components.VSlider
import app.perfectsound.player.ui.theme.PsColors

/**
 * Whether the equalizer shows its tuning controls (preamp, band sliders, auto, presets). Off for now:
 * the equalizer is a visualization only. The settings are still kept and saved.
 */
private const val EQ_TUNING_ENABLED = false

/** Equalizer settings: preamp and ten bands, each -1..+1 (maps to ±12 dB). */
data class EqSettings(
    val enabled: Boolean = false,
    val preamp: Float = 0f,
    val bands: List<Float> = List(SpectrumAnalyzer.BAND_CENTERS_HZ.size) { 0f },
)

/**
 * Preamp and ten band sliders, with live levels bouncing behind them. Sits inside the now-playing panel.
 * [onToggle] overrides what ON does: in remote mode it starts capturing audio so the levels bounce,
 * since Android doesn't let us process another app's sound.
 */
@Composable
fun EqSection(
    settings: EqSettings,
    onSettingsChange: (EqSettings) -> Unit,
    levels: AudioLevels.Snapshot,
    modifier: Modifier = Modifier,
    on: Boolean = settings.enabled,
    onToggle: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("EQUALIZER", style = LabelStyle.copy(letterSpacing = 2.sp))
            Spacer(Modifier.weight(1f))
            // Local files bounce without a switch; in remote mode ON starts the audio capture.
            if (onToggle != null) LedToggle("ON", on, onToggle)
            if (EQ_TUNING_ENABLED) {
                if (onToggle == null) LedToggle("ON", on, { onSettingsChange(settings.copy(enabled = !settings.enabled)) }, enabled = false)
                LedToggle("AUTO", false, {}, enabled = false)
                LedToggle("PRESETS", false, {}, enabled = false)
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            // Preamp
            if (EQ_TUNING_ENABLED) Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                VSlider(settings.preamp, { onSettingsChange(settings.copy(preamp = it)) }, Modifier.weight(1f).fillMaxWidth())
                BasicText("PRE", style = LabelStyle)
            }
            if (EQ_TUNING_ENABLED) Spacer(Modifier.width(6.dp))
            // Bands, with live levels bouncing behind the sliders
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(PsColors.LcdBackground, RoundedCornerShape(3.dp))
                        .border(1.dp, PsColors.BevelDark, RoundedCornerShape(3.dp)),
                ) {
                    EqLevelBars(levels.bands, Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 3.dp))
                    if (EQ_TUNING_ENABLED) Row(Modifier.fillMaxSize()) {
                        settings.bands.forEachIndexed { i, v ->
                            VSlider(v, { nv ->
                                onSettingsChange(settings.copy(bands = settings.bands.toMutableList().also { it[i] = nv }))
                            }, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    SpectrumAnalyzer.BAND_LABELS.forEach {
                        BasicText(it, Modifier.weight(1f), style = LabelStyle.copy(textAlign = TextAlign.Center, fontSize = 9.sp, letterSpacing = 0.sp))
                    }
                }
            }
        }
    }
}
