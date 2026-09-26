package app.perfectsound.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.perfectsound.player.playback.PlayerConnection
import app.perfectsound.player.ui.components.LabelStyle
import app.perfectsound.player.ui.components.LcdTextStyle
import app.perfectsound.player.ui.components.LedToggle
import app.perfectsound.player.ui.components.MenuItem
import app.perfectsound.player.ui.components.PopupMenu
import app.perfectsound.player.ui.components.TitleStrip
import app.perfectsound.player.ui.theme.PsColors

interface PlaylistActions {
    fun playAt(index: Int)
    fun addFiles()
    fun addFolder()
    fun remove(indices: Set<Int>)
    fun clear()
}

@Composable
fun PlaylistPanel(
    state: PlayerConnection.State,
    actions: PlaylistActions,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var addMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Keep the playing track in view when it changes.
    LaunchedEffect(state.currentIndex) {
        if (state.currentIndex >= 0) {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.none { it.index == state.currentIndex }) listState.animateScrollToItem(state.currentIndex)
        }
    }
    LaunchedEffect(state.playlist.size) { selected = selected.filter { it < state.playlist.size }.toSet() }

    Column(modifier.background(PsColors.Panel)) {
        TitleStrip("PLAYLIST")
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                .background(PsColors.LcdBackground, RoundedCornerShape(3.dp))
                .border(1.dp, PsColors.BevelDark, RoundedCornerShape(3.dp)),
        ) {
            if (state.playlist.isEmpty()) {
                BasicText(
                    "Add music with the ADD button, or open files with the eject button.",
                    style = LcdTextStyle.copy(color = PsColors.TextDim),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize().padding(vertical = 4.dp), state = listState) {
                itemsIndexed(state.playlist, key = { _, t -> t.id }) { index, track ->
                    val isCurrent = index == state.currentIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (index in selected) PsColors.Selection else PsColors.LcdBackground)
                            .pointerInput(index) {
                                detectTapGestures(
                                    onDoubleTap = { actions.playAt(index) },
                                    onTap = { selected = setOf(index) },
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        val color = if (isCurrent) PsColors.Current else PsColors.Lcd
                        BasicText(
                            "${index + 1}. ${track.displayName}",
                            style = LcdTextStyle.copy(color = color, fontSize = 13.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        BasicText(formatTime(track.durationMs), style = LcdTextStyle.copy(color = color, fontSize = 13.sp))
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box {
                LedToggle("ADD", addMenu, { addMenu = true })
                if (addMenu) PopupMenu(
                    listOf(MenuItem("Add files…", onClick = actions::addFiles), MenuItem("Add folder…", onClick = actions::addFolder)),
                    offset = IntOffset(0, -170),
                    onDismiss = { addMenu = false },
                )
            }
            LedToggle("REM", false, { actions.remove(selected); selected = emptySet() }, enabled = selected.isNotEmpty())
            LedToggle("CLR", false, { actions.clear(); selected = emptySet() }, enabled = state.playlist.isNotEmpty())
            Spacer(Modifier.weight(1f))
            BasicText(
                "${state.playlist.size} tracks · ${formatTime(state.totalDurationMs)}",
                style = LabelStyle.copy(color = PsColors.Text, fontSize = 11.sp, letterSpacing = 0.sp),
            )
        }
    }
}
