package app.perfectsound.player

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.perfectsound.player.audio.AudioLevels
import app.perfectsound.player.capture.PlaybackCaptureService
import app.perfectsound.player.library.TrackImporter
import app.perfectsound.player.playback.PlayerConnection
import app.perfectsound.player.ui.EqSettings
import app.perfectsound.player.ui.MainPanelActions
import app.perfectsound.player.ui.PerfectSoundApp
import app.perfectsound.player.ui.PlaylistActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var player: PlayerConnection
    private lateinit var importer: TrackImporter
    private val eq = MutableStateFlow(EqSettings())
    /** Eject/open replaces the playlist and plays; ADD appends. */
    private var replaceOnImport = false

    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> import(uris) }
    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) lifecycleScope.launch { addItems(importer.fromTree(uri)) }
    }

    private val projectionConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null)
            PlaybackCaptureService.start(this, result.resultCode, result.data!!)
    }
    private val capturePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) requestProjection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = PlayerConnection(applicationContext, lifecycleScope)
        importer = TrackImporter(applicationContext)
        player.connect()

        setContent {
            val state by player.state.collectAsStateWithLifecycle()
            val levels by AudioLevels.state.collectAsStateWithLifecycle()
            val eqSettings by eq.collectAsStateWithLifecycle()
            var equalizerVisible by rememberSaveable { mutableStateOf(true) }
            var playlistVisible by rememberSaveable { mutableStateOf(true) }
            val capturing = levels.source == AudioLevels.Source.Capture

            PerfectSoundApp(
                state = state,
                levels = levels,
                eq = eqSettings,
                onEqChange = { eq.value = it },
                equalizerVisible = equalizerVisible,
                playlistVisible = playlistVisible,
                capturing = capturing,
                onToggleCapture = { if (capturing) PlaybackCaptureService.stop(this) else startCapture() },
                mainActions = object : MainPanelActions {
                    override fun play() { player.play() }
                    override fun pause() { player.pause() }
                    override fun stop() { player.stop() }
                    override fun next() { player.next() }
                    override fun previous() { player.previous() }
                    override fun open() = openFiles(replace = true)
                    override fun seekTo(positionMs: Long) { player.seekTo(positionMs) }
                    override fun setVolume(volume: Float) { player.setVolume(volume) }
                    override fun toggleShuffle() { player.toggleShuffle() }
                    override fun cycleRepeat() { player.cycleRepeat() }
                    override fun toggleEqualizer() { equalizerVisible = !equalizerVisible }
                    override fun togglePlaylist() { playlistVisible = !playlistVisible }
                },
                playlistActions = object : PlaylistActions {
                    override fun playAt(index: Int) { player.playAt(index) }
                    override fun addFiles() = openFiles(replace = false)
                    override fun addFolder() = pickFolder.launch(null)
                    override fun remove(indices: Set<Int>) { player.removeTracks(indices) }
                    override fun clear() { player.clear() }
                },
            )
        }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    private fun openFiles(replace: Boolean) {
        replaceOnImport = replace
        pickFiles.launch(arrayOf("audio/*"))
    }

    private fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        lifecycleScope.launch { addItems(importer.fromDocuments(uris)) }
    }

    private fun addItems(items: List<androidx.media3.common.MediaItem>) {
        if (items.isEmpty()) return
        if (replaceOnImport) {
            replaceOnImport = false
            player.clear()
            player.addTracks(items)
            player.playAt(0)
        } else {
            player.addTracks(items)
        }
    }

    private fun startCapture() {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) requestProjection() else capturePermissions.launch(needed.toTypedArray())
    }

    private fun requestProjection() {
        projectionConsent.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }
}
