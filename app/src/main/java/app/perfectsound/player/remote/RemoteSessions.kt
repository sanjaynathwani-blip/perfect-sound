package app.perfectsound.player.remote

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remote mode: follows the media sessions of streaming apps (Spotify, YouTube Music) and forwards
 * transport commands to them. The audio stays in those apps; we only show and control it.
 */
class RemoteSessions(private val context: Context) {

    enum class App(val packageName: String, val label: String) {
        Spotify("com.spotify.music", "Spotify"),
        YouTubeMusic("com.google.android.apps.youtube.music", "YouTube Music"),
    }

    data class NowPlaying(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val durationMs: Long = 0,
        val art: Bitmap? = null,
        val isPlaying: Boolean = false,
        /** Position at [positionUpdatedAt] (elapsedRealtime); extrapolate with [positionAt]. */
        val positionMs: Long = 0,
        val positionUpdatedAt: Long = 0,
        val speed: Float = 1f,
        val canSeek: Boolean = false,
        val shuffle: Boolean = false,
        val repeatMode: Int = PlaybackStateCompat.REPEAT_MODE_NONE,
    ) {
        fun positionAt(now: Long = SystemClock.elapsedRealtime()): Long {
            if (!isPlaying || positionUpdatedAt == 0L) return positionMs
            val p = positionMs + ((now - positionUpdatedAt) * speed).toLong()
            return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
        }
    }

    data class State(
        val hasAccess: Boolean = false,
        /** Apps with an active media session right now. */
        val active: Set<App> = emptySet(),
        val installed: Set<App> = emptySet(),
        val nowPlaying: Map<App, NowPlaying> = emptyMap(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val listenerComponent = ComponentName(context, MediaListenerService::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val controllers = mutableMapOf<App, MediaController>()
    private val callbacks = mutableMapOf<App, MediaController.Callback>()
    /** Compat view of the same sessions: the framework API has no shuffle/repeat. */
    private val compat = mutableMapOf<App, MediaControllerCompat>()
    private val compatCallbacks = mutableMapOf<App, MediaControllerCompat.Callback>()
    private var started = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> update(list.orEmpty()) }

    fun hasAccess(): Boolean = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Opens the system screen where the user grants notification access to Perfect Sound. */
    fun accessSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= 30)
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent.flattenToString())
        else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /** Call when the UI becomes visible (access may have been granted meanwhile). */
    fun start() {
        val installed = App.entries.filter { isInstalled(it.packageName) }.toSet()
        if (!hasAccess()) {
            stop()
            _state.value = State(hasAccess = false, installed = installed)
            return
        }
        _state.value = _state.value.copy(hasAccess = true, installed = installed)
        if (started) return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, main)
            update(manager.getActiveSessions(listenerComponent))
            started = true
        } catch (_: SecurityException) {
            _state.value = State(hasAccess = false, installed = installed)
        }
    }

    fun stop() {
        if (started) manager.removeOnActiveSessionsChangedListener(sessionsListener)
        started = false
        controllers.forEach { (app, c) -> callbacks[app]?.let(c::unregisterCallback) }
        compat.forEach { (app, c) -> compatCallbacks[app]?.let(c::unregisterCallback) }
        controllers.clear()
        callbacks.clear()
        compat.clear()
        compatCallbacks.clear()
    }

    private fun update(sessions: List<MediaController>) {
        val found = sessions.mapNotNull { c -> App.entries.firstOrNull { it.packageName == c.packageName }?.let { it to c } }
            .distinctBy { it.first }.toMap()
        // Drop controllers whose sessions went away or were replaced.
        controllers.keys.toList().forEach { app ->
            val current = found[app]
            if (current == null || current.sessionToken != controllers[app]?.sessionToken) {
                callbacks.remove(app)?.let { controllers[app]?.unregisterCallback(it) }
                compatCallbacks.remove(app)?.let { compat[app]?.unregisterCallback(it) }
                controllers.remove(app)
                compat.remove(app)
            }
        }
        found.forEach { (app, c) ->
            if (app in controllers) return@forEach
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                override fun onSessionDestroyed() = update(manager.getActiveSessions(listenerComponent))
            }
            c.registerCallback(cb, main)
            controllers[app] = c
            callbacks[app] = cb
            runCatching { MediaControllerCompat(context, MediaSessionCompat.Token.fromToken(c.sessionToken)) }.getOrNull()?.let { cc ->
                val ccb = object : MediaControllerCompat.Callback() {
                    override fun onRepeatModeChanged(repeatMode: Int) = publish()
                    override fun onShuffleModeChanged(shuffleMode: Int) = publish()
                }
                cc.registerCallback(ccb, main)
                compat[app] = cc
                compatCallbacks[app] = ccb
            }
        }
        publish()
    }

    private fun publish() {
        _state.value = _state.value.copy(
            active = controllers.keys.toSet(),
            nowPlaying = controllers.mapValues { (app, c) -> c.toNowPlaying(compat[app]) },
        )
    }

    private fun MediaController.toNowPlaying(cc: MediaControllerCompat?): NowPlaying {
        val m = metadata
        val s = playbackState
        return NowPlaying(
            title = m?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            album = m?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            durationMs = m?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0,
            art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            isPlaying = s?.state == PlaybackState.STATE_PLAYING || s?.state == PlaybackState.STATE_BUFFERING,
            positionMs = s?.position ?: 0,
            positionUpdatedAt = s?.lastPositionUpdateTime ?: 0,
            speed = s?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            canSeek = (s?.actions ?: 0) and PlaybackState.ACTION_SEEK_TO != 0L,
            shuffle = (cc?.shuffleMode ?: PlaybackStateCompat.SHUFFLE_MODE_NONE) != PlaybackStateCompat.SHUFFLE_MODE_NONE,
            repeatMode = cc?.repeatMode?.takeIf { it >= 0 } ?: PlaybackStateCompat.REPEAT_MODE_NONE,
        )
    }

    // --- commands ---

    private fun controls(app: App) = controllers[app]?.transportControls

    fun play(app: App) = controls(app)?.play()
    fun pause(app: App) = controls(app)?.pause()
    fun togglePause(app: App) {
        if (_state.value.nowPlaying[app]?.isPlaying == true) pause(app) else play(app)
    }
    fun stop(app: App) = controls(app)?.run { pause(); seekTo(0) }
    fun next(app: App) = controls(app)?.skipToNext()
    fun previous(app: App) = controls(app)?.skipToPrevious()
    fun seekTo(app: App, positionMs: Long) = controls(app)?.seekTo(positionMs)

    fun toggleShuffle(app: App) {
        val cc = compat[app] ?: return
        val on = cc.shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_NONE
        cc.transportControls.setShuffleMode(if (on) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE)
    }

    /** Off -> repeat all -> repeat one -> off. */
    fun cycleRepeat(app: App) {
        val cc = compat[app] ?: return
        cc.transportControls.setRepeatMode(when (cc.repeatMode) {
            PlaybackStateCompat.REPEAT_MODE_NONE, PlaybackStateCompat.REPEAT_MODE_INVALID -> PlaybackStateCompat.REPEAT_MODE_ALL
            PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> PlaybackStateCompat.REPEAT_MODE_ONE
            else -> PlaybackStateCompat.REPEAT_MODE_NONE
        })
    }

    /** Streaming apps play through the system media volume, so remote volume is the media stream. */
    fun systemVolume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    }

    fun setSystemVolume(fraction: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction.coerceIn(0f, 1f) * max + 0.5f).toInt(), 0)
    }

    /** Brings the streaming app to the front, e.g. to pick something to play. */
    fun launch(app: App) {
        context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let(context::startActivity)
    }

    private fun isInstalled(pkg: String) = runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
}
