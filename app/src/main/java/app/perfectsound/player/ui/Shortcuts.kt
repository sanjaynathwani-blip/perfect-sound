package app.perfectsound.player.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/** Classic Winamp keys. Returns true when the key was handled. */
fun handleShortcut(e: KeyEvent, actions: MainPanelActions): Boolean {
    if (e.type != KeyEventType.KeyDown || e.isCtrlPressed) return false
    if (e.isAltPressed) {
        when (e.key) {
            Key.G -> actions.toggleEqualizer()
            Key.E -> actions.togglePlaylist()
            else -> return false
        }
        return true
    }
    when (e.key) {
        Key.Z -> actions.previous()
        Key.X -> actions.play()
        Key.C, Key.Spacebar -> actions.pause()
        Key.V -> actions.stop()
        Key.B -> actions.next()
        Key.L -> if (e.isShiftPressed) actions.addFolder() else actions.open()
        Key.DirectionLeft -> actions.seekBy(-5_000)
        Key.DirectionRight -> actions.seekBy(5_000)
        Key.DirectionUp -> actions.changeVolume(0.05f)
        Key.DirectionDown -> actions.changeVolume(-0.05f)
        Key.S -> actions.toggleShuffle()
        Key.R -> actions.cycleRepeat()
        else -> return false
    }
    return true
}

/** Shown in the About menu entry and the README. */
val SHORTCUT_HELP = listOf(
    "Z / X / C / V / B" to "Previous / Play / Pause / Stop / Next",
    "Space" to "Pause / resume",
    "← / →" to "Seek 5 seconds",
    "↑ / ↓" to "Volume (or move selection in the playlist)",
    "L / Shift+L" to "Open files / Add folder",
    "S / R" to "Shuffle / Repeat",
    "Alt+G / Alt+E" to "Show equalizer / playlist",
    "Enter / Delete / Ctrl+A" to "Play / Remove / Select all (playlist)",
)
