package app.perfectsound.player.remote

import android.app.ActivityOptions
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Browser
import android.provider.Settings
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remote mode: follows Chrome's media session and forwards transport commands to it. The audio stays in
 * Chrome; we only show and control it. A tab playing from Spotify's web player counts as the Spotify
 * source and anything else as Chrome. The Spotify app itself isn't followed: it marks its audio as not
 * capturable by other apps, so the visualizer couldn't move to it.
 */
class RemoteSessions(private val context: Context) {

    /**
     * A source: the media session of [packageName], limited to tabs playing from [site] when it's set.
     * Android doesn't reveal another app's stream format, so [typicalKbps] / [typicalSampleRateHz]
     * are what the site usually streams: Spotify's web player for Premium (AAC 256 kbps, 44.1 kHz),
     * and YouTube Music on the web (Opus, about 160 kbps at 48 kHz).
     */
    enum class App(
        val packageName: String,
        val site: String?,
        val label: String,
        val typicalKbps: Int,
        val typicalSampleRateHz: Int,
    ) {
        Spotify("com.android.chrome", "open.spotify.com", "Spotify", 256, 44_100),
        /** Whatever else a Chrome tab is playing (YouTube Music on the web, YouTube, SoundCloud…). */
        Chrome("com.android.chrome", null, "Chrome", 160, 48_000),
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
        /** Whether the app accepts shuffle / repeat changes from us (Spotify and Chrome don't). */
        val canShuffle: Boolean = false,
        val canRepeat: Boolean = false,
    ) {
        fun positionAt(now: Long = SystemClock.elapsedRealtime()): Long {
            if (!isPlaying || positionUpdatedAt == 0L) return positionMs
            val p = positionMs + ((now - positionUpdatedAt) * speed).toLong()
            return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
        }
    }

    data class State(
        val hasAccess: Boolean = false,
        /**
         * Installed from a downloaded APK, so Android may grey out notification access as a
         * "Restricted setting" until the user allows it in Perfect Sound's App info.
         */
        val accessRestricted: Boolean = false,
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
    /** One session per followed package, keyed by package name. */
    private val controllers = mutableMapOf<String, MediaController>()
    private val callbacks = mutableMapOf<String, MediaController.Callback>()
    /** Compat view of the same sessions: the framework API has no shuffle/repeat. */
    private val compat = mutableMapOf<String, MediaControllerCompat>()
    private val compatCallbacks = mutableMapOf<String, MediaControllerCompat.Callback>()
    /** Which source each session counts as right now; a Chrome session moves as tabs take turns playing. */
    private var sessionFor = emptyMap<App, MediaController>()
    private var started = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> update(list.orEmpty()) }

    fun hasAccess(): Boolean = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Opens the system screen where the user grants notification access to Perfect Sound. */
    fun accessSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= 30)
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent.flattenToString())
        else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /** Opens Perfect Sound's App info, whose ⋮ menu has "Allow restricted settings". */
    fun appInfoIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    /**
     * Android 13+ restricts notification access for apps installed from an APK file (a Chrome download
     * or the Files app), but not for store or adb installs. Apps can't tell whether the user has since
     * allowed it, so this only means the switch may be greyed out.
     */
    private fun isAccessRestricted(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        val source = runCatching { context.packageManager.getInstallSourceInfo(context.packageName).packageSource }.getOrNull()
        return source == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE || source == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
    }

    /** Call when the UI becomes visible or regains focus (access may have been granted meanwhile). */
    fun start() {
        val installed = App.entries.filter { isInstalled(it.packageName) }.toSet()
        // The playing site comes from Chrome's media notification, which can change after its session does.
        MediaListenerService.onMediaChanged = { if (started) publish() }
        if (!hasAccess()) {
            stop()
            _state.value = State(hasAccess = false, accessRestricted = isAccessRestricted(), installed = installed)
            return
        }
        _state.value = _state.value.copy(hasAccess = true, installed = installed)
        if (started) return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, main)
            update(manager.getActiveSessions(listenerComponent))
            started = true
        } catch (_: SecurityException) {
            _state.value = State(hasAccess = false, accessRestricted = isAccessRestricted(), installed = installed)
        }
    }

    fun stop() {
        if (started) manager.removeOnActiveSessionsChangedListener(sessionsListener)
        started = false
        controllers.forEach { (pkg, c) -> callbacks[pkg]?.let(c::unregisterCallback) }
        compat.forEach { (pkg, c) -> compatCallbacks[pkg]?.let(c::unregisterCallback) }
        controllers.clear()
        callbacks.clear()
        compat.clear()
        compatCallbacks.clear()
        sessionFor = emptyMap()
        MediaListenerService.onMediaChanged = null
    }

    private fun update(sessions: List<MediaController>) {
        val followed = App.entries.map { it.packageName }.toSet()
        val found = sessions.filter { it.packageName in followed }.distinctBy { it.packageName }.associateBy { it.packageName }
        // Drop controllers whose sessions went away or were replaced.
        controllers.keys.toList().forEach { pkg ->
            val current = found[pkg]
            if (current == null || current.sessionToken != controllers[pkg]?.sessionToken) {
                callbacks.remove(pkg)?.let { controllers[pkg]?.unregisterCallback(it) }
                compatCallbacks.remove(pkg)?.let { compat[pkg]?.unregisterCallback(it) }
                controllers.remove(pkg)
                compat.remove(pkg)
            }
        }
        found.forEach { (pkg, c) ->
            if (pkg in controllers) return@forEach
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                override fun onSessionDestroyed() = update(manager.getActiveSessions(listenerComponent))
            }
            c.registerCallback(cb, main)
            controllers[pkg] = c
            callbacks[pkg] = cb
            runCatching { MediaControllerCompat(context, MediaSessionCompat.Token.fromToken(c.sessionToken)) }.getOrNull()?.let { cc ->
                val ccb = object : MediaControllerCompat.Callback() {
                    override fun onRepeatModeChanged(repeatMode: Int) = publish()
                    override fun onShuffleModeChanged(shuffleMode: Int) = publish()
                }
                cc.registerCallback(ccb, main)
                compat[pkg] = cc
                compatCallbacks[pkg] = ccb
            }
        }
        publish()
    }

    private fun publish() {
        sessionFor = controllers.values.mapNotNull { c -> sourceOf(c)?.let { it to c } }.toMap()
        _state.value = _state.value.copy(
            active = sessionFor.keys,
            nowPlaying = sessionFor.mapValues { (_, c) -> c.toNowPlaying(compat[c.packageName]) },
        )
    }

    /** The source [c] counts as: the one for the site its media notification names, else the catch-all. */
    private fun sourceOf(c: MediaController): App? {
        val candidates = App.entries.filter { it.packageName == c.packageName }
        val site = MediaListenerService.mediaNotification(c.sessionToken)
            ?.extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        return candidates.firstOrNull { it.site != null && it.site == site } ?: candidates.firstOrNull { it.site == null }
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
            canShuffle = (s?.actions ?: 0) and PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE != 0L,
            canRepeat = (s?.actions ?: 0) and PlaybackStateCompat.ACTION_SET_REPEAT_MODE != 0L,
        )
    }

    // --- commands ---

    private fun controls(app: App) = sessionFor[app]?.transportControls

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
        val cc = sessionFor[app]?.let { compat[it.packageName] } ?: return
        val on = cc.shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_NONE
        cc.transportControls.setShuffleMode(if (on) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE)
    }

    /** Off -> repeat all -> repeat one -> off. */
    fun cycleRepeat(app: App) {
        val cc = sessionFor[app]?.let { compat[it.packageName] } ?: return
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

    /** Brings the source to the front, e.g. to pick something to play. */
    fun launch(app: App) {
        val site = app.site
        if (site == null) {
            context.packageManager.getLaunchIntentForPackage(app.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let(context::startActivity)
            return
        }
        // A tab already playing from the site: bring it forward, as clicking Chrome's media notification does.
        val tab = sessionFor[app]?.let { MediaListenerService.mediaNotification(it.sessionToken) }?.contentIntent
        if (tab != null && runCatching { tab.send(context, 0, null, null, null, null, allowActivityStart()) }.isSuccess) return
        // Otherwise open the site in Chrome itself, not in an app that claims its links (the Spotify app).
        // Chrome keeps reusing one tab for the same application id, so this doesn't pile up tabs.
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://$site/"))
                .setPackage(app.packageName)
                .putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Lets another app's PendingIntent open its activity on our behalf; we're in front when this runs. */
    private fun allowActivityStart(): Bundle? =
        if (Build.VERSION.SDK_INT >= 34) {
            @Suppress("DEPRECATION") // ALLOW_ALWAYS replaces it from API 36 on
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else null

    private fun isInstalled(pkg: String) = runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
}
