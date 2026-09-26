package app.perfectsound.player.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.perfectsound.player.MainActivity
import app.perfectsound.player.audio.AudioLevels
import app.perfectsound.player.audio.SpectrumAnalyzer
import app.perfectsound.player.audio.SpectrumTapProcessor
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/** Plays local audio in the background with a media notification, lock-screen and media-key controls. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val tap = SpectrumTapProcessor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var levelsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(tap)).build()
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(), /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startLevels() else stopLevels()
            }
        })

        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                // Items added by the UI carry their URI in requestMetadata; resolve it for the player.
                override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
                                             mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> =
                    Futures.immediateFuture(mediaItems.map { item ->
                        if (item.localConfiguration != null) item
                        else item.buildUpon().setUri(item.requestMetadata.mediaUri).build()
                    }.toMutableList())
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * Publishes band levels ~60 times a second. The decoder writes audio ahead of the speaker in
     * bursts, so a playhead advances in real time [LATENCY_S] behind the write position.
     */
    private fun startLevels() {
        if (levelsJob?.isActive == true) return
        levelsJob = scope.launch {
            val frame = FloatArray(FFT_SIZE)
            var analyzer = SpectrumAnalyzer(tap.sampleRate, FFT_SIZE)
            var playhead = -1.0
            var last = System.nanoTime()
            while (isActive) {
                val now = System.nanoTime()
                val dt = (now - last) / 1e9
                last = now
                val rate = tap.sampleRate
                if (analyzer.sampleRate != rate) analyzer = SpectrumAnalyzer(rate, FFT_SIZE)
                val target = tap.framesWritten() - LATENCY_S * rate
                playhead = if (playhead < 0 || abs(playhead - target) > rate * 0.25) target
                else playhead + dt * rate + (target - playhead - dt * rate) * 0.05
                tap.read(playhead.toLong(), frame)
                var sumSquares = 0.0
                for (s in frame) sumSquares += s * s
                val rmsDb = 20f * log10(maxOf(sqrt(sumSquares / frame.size).toFloat(), 1e-5f))
                analyzer.analyze(frame)?.let { AudioLevels.publish(AudioLevels.Snapshot(AudioLevels.Source.Local, it, rmsDb)) }
                delay(16)
            }
        }
    }

    private fun stopLevels() {
        levelsJob?.cancel()
        levelsJob = null
        if (AudioLevels.state.value.source == AudioLevels.Source.Local) AudioLevels.clear()
    }

    private companion object {
        const val FFT_SIZE = 2048
        const val LATENCY_S = 0.25
    }
}
