package app.perfectsound.player.ui

import android.content.ClipDescription
import android.view.DragEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.IntOffset
import app.perfectsound.player.ui.components.InfoPopup
import app.perfectsound.player.ui.components.MenuItem
import app.perfectsound.player.ui.components.PopupMenu
import app.perfectsound.player.ui.components.onRightClick
import kotlin.math.roundToInt
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
import app.perfectsound.player.ui.theme.PsColors

private val MainPanelWidth = 470.dp
private val WideLayoutMinWidth = 820.dp
private val StackedMinHeight = 600.dp

/**
 * One window, no floating parts: the player on top and the now-playing panel (art, details and
 * equalizer) below. In local mode, wide windows put the playlist beside them and narrow ones below.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PerfectSoundApp(
    display: DisplayState,
    source: Source,
    sources: List<SourceOption>,
    levels: AudioLevels.Snapshot,
    equalizerVisible: Boolean,
    playlistVisible: Boolean,
    mainActions: MainPanelActions,
    /** Album art, track details and the equalizer, for whichever source is playing. */
    nowPlaying: @Composable (Modifier) -> Unit,
    /** The playlist; null in remote mode, where the streaming app owns the queue. */
    playlist: (@Composable (Modifier) -> Unit)?,
    /** Files dropped onto the window; returns true if they were accepted. */
    onDrop: (DragEvent) -> Boolean,
) {
    val gap = 2.dp
    val rootFocus = remember { FocusRequester() }
    val currentActions by rememberUpdatedState(mainActions)
    val currentOnDrop by rememberUpdatedState(onDrop)
    var dropping by remember { mutableStateOf(false) }
    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dropping = false
                return currentOnDrop(event.toAndroidDragEvent())
            }
            override fun onEntered(event: DragAndDropEvent) { dropping = true }
            override fun onExited(event: DragAndDropEvent) { dropping = false }
            override fun onEnded(event: DragAndDropEvent) { dropping = false }
        }
    }
    LaunchedEffect(Unit) { rootFocus.requestFocus() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(PsColors.Background)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { e -> e.mimeTypes().any { it.startsWith("audio/") || it == "application/ogg" || it == ClipDescription.MIMETYPE_TEXT_URILIST } },
                target = dropTarget,
            )
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { handleShortcut(it, currentActions) },
    ) {
        val main: @Composable (Modifier) -> Unit = { m ->
            var menu by remember { mutableStateOf<Offset?>(null) }
            var help by remember { mutableStateOf(false) }
            Box(m.onRightClick { menu = it }) {
                MainPanel(display, source, sources, levels.bands, mainActions, Modifier.fillMaxWidth())
                menu?.let { pos ->
                    PopupMenu(
                        items = listOf(
                            MenuItem("Open files…    L", onClick = mainActions::open),
                            MenuItem("Add folder…    Shift+L", onClick = mainActions::addFolder),
                            MenuItem((if (display.shuffle && display.canShuffle) "✓ " else "") + "Shuffle    S", enabled = display.canShuffle, onClick = mainActions::toggleShuffle),
                            MenuItem((if (display.repeat != Repeat.Off && display.canRepeat) "✓ " else "") + "Repeat    R", enabled = display.canRepeat, onClick = mainActions::cycleRepeat),
                            MenuItem((if (equalizerVisible) "✓ " else "") + "Equalizer    Alt+G", onClick = mainActions::toggleEqualizer),
                            MenuItem((if (playlistVisible) "✓ " else "") + "Playlist    Alt+E", onClick = mainActions::togglePlaylist),
                            MenuItem("Keyboard shortcuts…") { help = true },
                        ),
                        offset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt()),
                        onDismiss = { menu = null },
                    )
                }
                if (help) InfoPopup("KEYBOARD SHORTCUTS", SHORTCUT_HELP, IntOffset(40, 40)) { help = false }
            }
        }
        val showPlaylist = playlist != null && playlistVisible

        if (maxWidth >= WideLayoutMinWidth && showPlaylist) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(Modifier.width(MainPanelWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    main(Modifier.fillMaxWidth())
                    nowPlaying(Modifier.fillMaxWidth().weight(1f))
                }
                playlist!!(Modifier.weight(1f).fillMaxHeight())
            }
        } else if (showPlaylist && maxHeight < StackedMinHeight) {
            // Too short for the player, now playing and the playlist to share: keep useful sizes and scroll.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(gap)) {
                main(Modifier.fillMaxWidth())
                nowPlaying(Modifier.fillMaxWidth().height(if (equalizerVisible) 260.dp else 200.dp))
                playlist!!(Modifier.fillMaxWidth().height(320.dp))
            }
        } else {
            // The player keeps its natural height and now playing always fills the rest, at any window size.
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                main(Modifier.fillMaxWidth())
                if (showPlaylist) {
                    // Share the remaining height; now playing stops growing past a useful size.
                    nowPlaying(Modifier.fillMaxWidth().weight(0.45f).heightIn(max = 300.dp))
                    playlist!!(Modifier.fillMaxWidth().weight(0.55f))
                } else nowPlaying(Modifier.fillMaxWidth().weight(1f))
            }
        }
        if (dropping) Box(Modifier.matchParentSize().border(3.dp, PsColors.Lcd))
    }
}

