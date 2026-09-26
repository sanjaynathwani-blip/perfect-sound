package app.perfectsound.player.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.perfectsound.player.remote.RemoteSessions
import app.perfectsound.player.ui.components.LabelStyle
import app.perfectsound.player.ui.components.LcdTextStyle
import app.perfectsound.player.ui.components.LedToggle
import app.perfectsound.player.ui.components.TitleStrip
import app.perfectsound.player.ui.theme.PsColors

/** Replaces the playlist in remote mode: what the streaming app is playing, plus setup hints. */
@Composable
fun RemotePanel(
    app: RemoteSessions.App,
    remote: RemoteSessions.State,
    capturing: Boolean,
    onGrantAccess: () -> Unit,
    onOpenApp: () -> Unit,
    onToggleCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val np = remote.nowPlaying[app]
    Column(modifier.background(PsColors.Panel)) {
        TitleStrip(app.label.uppercase())
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .background(PsColors.LcdBackground, RoundedCornerShape(3.dp))
                .border(1.dp, PsColors.BevelDark, RoundedCornerShape(3.dp))
                .padding(16.dp),
        ) {
            when {
                !remote.hasAccess -> Message(
                    title = "Allow Perfect Sound to see what's playing",
                    body = "To show and control ${app.label}, Android needs you to turn on notification access " +
                        "for Perfect Sound. It's only used to read and control media playback.",
                    button = "GRANT ACCESS", onButton = onGrantAccess,
                )
                np?.title == null -> Message(
                    title = "Nothing playing in ${app.label}",
                    body = "Start something in ${app.label}, then control it from here.",
                    button = "OPEN ${app.label.uppercase()}", onButton = onOpenApp,
                )
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth > maxHeight * 1.2f
                    val details: @Composable (Modifier) -> Unit = { m -> TrackDetails(app, np, capturing, onOpenApp, onToggleCapture, m) }
                    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AlbumArt(np, Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
                        details(Modifier.weight(1f))
                    } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AlbumArt(np, Modifier.weight(1f, fill = false).aspectRatio(1f).align(Alignment.CenterHorizontally))
                        details(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumArt(np: RemoteSessions.NowPlaying, modifier: Modifier) {
    val bitmap = remember(np.art) { np.art?.asImageBitmap() }
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(PsColors.PanelBottom), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, contentDescription = "Album art", contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
        else BasicText("♪", style = LcdTextStyle.copy(fontSize = 48.sp, color = PsColors.LcdDim))
    }
}

@Composable
private fun TrackDetails(
    app: RemoteSessions.App,
    np: RemoteSessions.NowPlaying,
    capturing: Boolean,
    onOpenApp: () -> Unit,
    onToggleCapture: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(np.title.orEmpty(), style = LcdTextStyle.copy(color = PsColors.Current, fontSize = 18.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        np.artist?.let { BasicText(it, style = LcdTextStyle.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        np.album?.let { BasicText(it, style = LcdTextStyle.copy(color = PsColors.TextDim, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LedToggle("OPEN ${app.label.uppercase()}", false, onOpenApp)
            LedToggle("EQ LEVELS", capturing, onToggleCapture)
        }
        if (!capturing) BasicText(
            "Turn on EQ LEVELS to make the equalizer bounce to ${app.label}. Android will ask to capture audio each time.",
            style = LabelStyle.copy(color = PsColors.TextDim, fontSize = 11.sp, letterSpacing = 0.sp),
        )
    }
}

@Composable
private fun Message(title: String, body: String, button: String, onButton: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(title, style = LcdTextStyle.copy(color = PsColors.Current, fontSize = 16.sp))
        BasicText(body, style = LcdTextStyle.copy(color = PsColors.TextDim, fontSize = 13.sp))
        LedToggle(button, false, onButton, Modifier.padding(top = 4.dp))
    }
}
