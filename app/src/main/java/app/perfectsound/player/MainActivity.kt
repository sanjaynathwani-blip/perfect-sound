package app.perfectsound.player

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.perfectsound.player.audio.AudioLevels
import app.perfectsound.player.audio.SpectrumAnalyzer
import app.perfectsound.player.capture.PlaybackCaptureService
import app.perfectsound.player.ui.EqLevelBars

class MainActivity : ComponentActivity() {

    private val projectionConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null)
            PlaybackCaptureService.start(this, result.resultCode, result.data!!)
    }

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) requestProjection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val levels by AudioLevels.state.collectAsStateWithLifecycle()
            CaptureTestScreen(
                levels = levels,
                onStart = ::startListening,
                onStop = { PlaybackCaptureService.stop(this) },
            )
        }
    }

    private fun startListening() {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) requestProjection() else permissions.launch(needed.toTypedArray())
    }

    private fun requestProjection() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        projectionConsent.launch(manager.createScreenCaptureIntent())
    }
}

private val Panel = Color(0xFF14171C)
private val Lcd = Color(0xFF39FF6A)
private val Dim = Color(0xFF7A8490)

@Composable
private fun CaptureTestScreen(levels: AudioLevels.Snapshot, onStart: () -> Unit, onStop: () -> Unit) {
    val mono = TextStyle(fontFamily = FontFamily.Monospace, color = Lcd, fontSize = 14.sp)
    val capturing = levels.source == AudioLevels.Source.Capture
    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BasicText("PERFECT SOUND · capture test", style = mono.copy(fontSize = 18.sp))
        BasicText(
            if (!capturing) "Start playing something in Spotify or YouTube Music, then press Listen."
            else "Level: %.0f dBFS  %s".format(levels.rmsDb, if (levels.rmsDb < -80f) "(silent — nothing playing, or the app blocks capture)" else ""),
            style = mono.copy(color = Dim),
        )
        Box(
            Modifier.fillMaxWidth().weight(1f).background(Panel, RoundedCornerShape(8.dp)).padding(16.dp),
        ) {
            Column {
                EqLevelBars(levels.bands, Modifier.fillMaxWidth().weight(1f))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    SpectrumAnalyzer.BAND_LABELS.forEach {
                        BasicText(it, Modifier.weight(1f), style = mono.copy(color = Dim, fontSize = 12.sp, textAlign = TextAlign.Center))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelButton(if (capturing) "Listening…" else "Listen", enabled = !capturing, onClick = onStart)
            PanelButton("Stop", enabled = capturing, onClick = onStop)
        }
    }
}

@Composable
private fun PanelButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .border(1.dp, if (enabled) Lcd else Dim, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(fontFamily = FontFamily.Monospace, color = if (enabled) Lcd else Dim, fontSize = 14.sp))
    }
}
