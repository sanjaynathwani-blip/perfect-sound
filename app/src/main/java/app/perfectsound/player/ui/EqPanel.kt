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
import app.perfectsound.player.audio.AudioLevels
import app.perfectsound.player.audio.SpectrumAnalyzer
import app.perfectsound.player.ui.components.LabelStyle
import app.perfectsound.player.ui.components.LedToggle
import app.perfectsound.player.ui.components.TitleStrip
import app.perfectsound.player.ui.components.VSlider
import app.perfectsound.player.ui.theme.PsColors

/** Equalizer settings: preamp and ten bands, each -1..+1 (maps to ±12 dB). */
data class EqSettings(
    val enabled: Boolean = false,
    val preamp: Float = 0f,
    val bands: List<Float> = List(SpectrumAnalyzer.BAND_CENTERS_HZ.size) { 0f },
)

@Composable
fun EqPanel(
    settings: EqSettings,
    onSettingsChange: (EqSettings) -> Unit,
    levels: AudioLevels.Snapshot,
    capturing: Boolean,
    onToggleCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(PsColors.Panel)) {
        TitleStrip("EQUALIZER")
        Column(Modifier.padding(10.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Sound processing arrives in Phase 2; until then the sliders are visual only.
                LedToggle("ON", settings.enabled, { onSettingsChange(settings.copy(enabled = !settings.enabled)) }, enabled = false)
                LedToggle("AUTO", false, {}, enabled = false)
                Spacer(Modifier.weight(1f))
                LedToggle("OTHER APPS", capturing, onToggleCapture)
                LedToggle("PRESETS", false, {}, enabled = false)
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                // Preamp
                Column(Modifier.width(34.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    VSlider(settings.preamp, { onSettingsChange(settings.copy(preamp = it)) }, Modifier.weight(1f).fillMaxWidth())
                    BasicText("PRE", style = LabelStyle)
                }
                Spacer(Modifier.width(10.dp))
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
                        Row(Modifier.fillMaxSize()) {
                            settings.bands.forEachIndexed { i, v ->
                                VSlider(v, { nv ->
                                    onSettingsChange(settings.copy(bands = settings.bands.toMutableList().also { it[i] = nv }))
                                }, Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        SpectrumAnalyzer.BAND_LABELS.forEach {
                            BasicText(it, Modifier.weight(1f), style = LabelStyle.copy(textAlign = TextAlign.Center))
                        }
                    }
                }
            }
        }
    }
}
