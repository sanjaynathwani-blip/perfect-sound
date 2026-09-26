package app.perfectsound.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.perfectsound.player.audio.AudioLevels
import app.perfectsound.player.playback.PlayerConnection
import app.perfectsound.player.ui.theme.PsColors

private val MainPanelWidth = 470.dp
private val WideLayoutMinWidth = 820.dp
private val StackedMinHeight = 600.dp

/**
 * One window, no floating parts: wide windows put the player and equalizer in a left column with
 * the playlist filling the rest; narrow windows stack all three.
 */
@Composable
fun PerfectSoundApp(
    state: PlayerConnection.State,
    levels: AudioLevels.Snapshot,
    eq: EqSettings,
    onEqChange: (EqSettings) -> Unit,
    equalizerVisible: Boolean,
    playlistVisible: Boolean,
    capturing: Boolean,
    onToggleCapture: () -> Unit,
    mainActions: MainPanelActions,
    playlistActions: PlaylistActions,
) {
    val gap = 2.dp
    BoxWithConstraints(Modifier.fillMaxSize().background(PsColors.Background)) {
        val main: @Composable (Modifier) -> Unit = {
            MainPanel(state, levels.bands, equalizerVisible, playlistVisible, mainActions, it)
        }
        val equalizer: @Composable (Modifier) -> Unit = {
            EqPanel(eq, onEqChange, levels, capturing, onToggleCapture, it)
        }
        val playlist: @Composable (Modifier) -> Unit = { PlaylistPanel(state, playlistActions, it) }

        if (maxWidth >= WideLayoutMinWidth && playlistVisible) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(Modifier.width(MainPanelWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    main(Modifier.fillMaxWidth())
                    if (equalizerVisible) equalizer(Modifier.fillMaxWidth().weight(1f))
                    else PanelFiller(Modifier.fillMaxWidth().weight(1f))
                }
                playlist(Modifier.weight(1f).fillMaxHeight())
            }
        } else if (maxHeight < StackedMinHeight) {
            // Too short to share the height sensibly: keep natural panel sizes and scroll.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(gap)) {
                main(Modifier.fillMaxWidth())
                if (equalizerVisible) equalizer(Modifier.fillMaxWidth().height(220.dp))
                if (playlistVisible) playlist(Modifier.fillMaxWidth().height(320.dp))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                main(Modifier.fillMaxWidth())
                when {
                    equalizerVisible && playlistVisible -> {
                        // Share the remaining height; the equalizer stops growing past a useful size.
                        equalizer(Modifier.fillMaxWidth().weight(0.45f).heightIn(max = 260.dp))
                        playlist(Modifier.fillMaxWidth().weight(0.55f))
                    }
                    equalizerVisible -> equalizer(Modifier.fillMaxWidth().weight(1f))
                    playlistVisible -> playlist(Modifier.fillMaxWidth().weight(1f))
                    else -> PanelFiller(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

/** Plain panel surface so hidden panels never leave a black hole in the window. */
@Composable
private fun PanelFiller(modifier: Modifier) {
    androidx.compose.foundation.layout.Box(modifier.background(PsColors.Panel))
}
