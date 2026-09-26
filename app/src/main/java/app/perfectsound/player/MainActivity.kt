package app.perfectsound.player

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import app.perfectsound.player.audio.AudioLevels
import app.perfectsound.player.capture.PlaybackCaptureService
import app.perfectsound.player.library.TrackImporter
import app.perfectsound.player.playback.PlayerConnection
import app.perfectsound.player.remote.RemoteSessions
import app.perfectsound.player.ui.EqSettings
import app.perfectsound.player.ui.MainPanelActions
import app.perfectsound.player.ui.PerfectSoundApp
import app.perfectsound.player.ui.PlaylistActions
import app.perfectsound.player.ui.PlaylistPanel
import app.perfectsound.player.ui.RemotePanel
import app.perfectsound.player.ui.Source
import app.perfectsound.player.ui.SourceOption
import app.perfectsound.player.ui.toDisplay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var player: PlayerConnection
    private lateinit var remote: RemoteSessions
    private lateinit var importer: TrackImporter
    private val eq = MutableStateFlow(EqSettings())
    private val source = MutableStateFlow<Source>(Source.Local)
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
        remote = RemoteSessions(applicationContext)
        importer = TrackImporter(applicationContext)
        player.connect()

        setContent {
            val local by player.state.collectAsStateWithLifecycle()
            val remoteState by remote.state.collectAsStateWithLifecycle()
            val levels by AudioLevels.state.collectAsStateWithLifecycle()
            val eqSettings by eq.collectAsStateWithLifecycle()
            val currentSource by source.collectAsStateWithLifecycle()
            var equalizerVisible by rememberSaveable { mutableStateOf(true) }
            var playlistVisible by rememberSaveable { mutableStateOf(true) }
            val capturing = levels.source == AudioLevels.Source.Capture

            // Remote apps report position occasionally; extrapolate it and read the system volume.
            var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
            var systemVolume by remember { mutableFloatStateOf(remote.systemVolume()) }
            LaunchedEffect(currentSource) {
                while (currentSource is Source.Remote) {
                    now = SystemClock.elapsedRealtime()
                    systemVolume = remote.systemVolume()
                    delay(100)
                }
            }

            val display = when (val s = currentSource) {
                Source.Local -> local.toDisplay()
                is Source.Remote -> remoteState.nowPlaying[s.app].let { np ->
                    np.toDisplay(s.app, np?.positionAt(now) ?: 0, systemVolume)
                }
            }
            val sources = listOf(SourceOption(Source.Local, "LOCAL", true)) +
                RemoteSessions.App.entries.map { app ->
                    SourceOption(Source.Remote(app), if (app == RemoteSessions.App.YouTubeMusic) "YT MUSIC" else app.label.uppercase(),
                        available = app in remoteState.installed)
                }
            val toggleCapture = { if (capturing) PlaybackCaptureService.stop(this) else startCapture() }

            PerfectSoundApp(
                display = display,
                source = currentSource,
                sources = sources,
                levels = levels,
                eq = eqSettings,
                onEqChange = { eq.value = it },
                equalizerVisible = equalizerVisible,
                playlistVisible = playlistVisible,
                capturing = capturing,
                onToggleCapture = toggleCapture,
                mainActions = object : MainPanelActions {
                    override fun play() = route({ player.play() }) { remote.play(it) }
                    override fun pause() = route({ player.pause() }) { remote.togglePause(it) }
                    override fun stop() = route({ player.stop() }) { remote.stop(it) }
                    override fun next() = route({ player.next() }) { remote.next(it) }
                    override fun previous() = route({ player.previous() }) { remote.previous(it) }
                    override fun open() = openFiles(replace = true)
                    override fun seekTo(positionMs: Long) = route({ player.seekTo(positionMs) }) { remote.seekTo(it, positionMs) }
                    override fun setVolume(volume: Float) = route({ player.setVolume(volume) }) {
                        remote.setSystemVolume(volume)
                        systemVolume = remote.systemVolume()
                    }
                    override fun toggleShuffle() = route({ player.toggleShuffle() }) { remote.toggleShuffle(it) }
                    override fun cycleRepeat() = route({ player.cycleRepeat() }) { remote.cycleRepeat(it) }
                    override fun toggleEqualizer() { equalizerVisible = !equalizerVisible }
                    override fun togglePlaylist() { playlistVisible = !playlistVisible }
                    override fun selectSource(source: Source) { this@MainActivity.source.value = source }
                },
                rightPanel = { modifier ->
                    when (val s = currentSource) {
                        Source.Local -> PlaylistPanel(local, playlistActions, modifier)
                        is Source.Remote -> RemotePanel(
                            app = s.app,
                            remote = remoteState,
                            capturing = capturing,
                            onGrantAccess = { startActivity(remote.accessSettingsIntent()) },
                            onOpenApp = { remote.launch(s.app) },
                            onToggleCapture = toggleCapture,
                            modifier = modifier,
                        )
                    }
                },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        remote.start() // re-checks notification access, which may have just been granted
    }

    override fun onStop() {
        remote.stop()
        super.onStop()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    private val playlistActions = object : PlaylistActions {
        override fun playAt(index: Int) { player.playAt(index) }
        override fun addFiles() = openFiles(replace = false)
        override fun addFolder() = pickFolder.launch(null)
        override fun remove(indices: Set<Int>) { player.removeTracks(indices) }
        override fun clear() { player.clear() }
    }

    /** Sends a command to the built-in player or to the streaming app being remote-controlled. */
    private inline fun route(local: () -> Unit, remoteCommand: (RemoteSessions.App) -> Unit) {
        when (val s = source.value) {
            Source.Local -> local()
            is Source.Remote -> remoteCommand(s.app)
        }
    }

    private fun openFiles(replace: Boolean) {
        replaceOnImport = replace
        pickFiles.launch(arrayOf("audio/*"))
    }

    private fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        lifecycleScope.launch { addItems(importer.fromDocuments(uris)) }
    }

    private fun addItems(items: List<MediaItem>) {
        if (items.isEmpty()) return
        source.value = Source.Local
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
