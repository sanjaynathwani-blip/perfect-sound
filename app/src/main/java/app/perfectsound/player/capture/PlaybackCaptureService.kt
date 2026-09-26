package app.perfectsound.player.capture

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import app.perfectsound.player.R
import app.perfectsound.player.audio.AudioLevels
import app.perfectsound.player.audio.SpectrumAnalyzer
import kotlin.concurrent.thread
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Captures what other apps (Spotify, Chrome, ...) are playing via AudioPlaybackCapture and
 * publishes equalizer band levels. Apps can opt out of capture, in which case we only read silence.
 */
class PlaybackCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var record: AudioRecord? = null
    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_RESULT_DATA, Intent::class.java) }
        if (resultCode != Activity.RESULT_OK || data == null || running) return START_NOT_STICKY

        // Must be in the foreground before the projection is created (Android 14+).
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        val manager = getSystemService(MediaProjectionManager::class.java)
        val mp = manager.getMediaProjection(resultCode, data) ?: run { stopSelf(); return START_NOT_STICKY }
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = stopSelf()
        }, null)
        projection = mp
        @Suppress("MissingPermission") // checked by the activity before starting the service
        startCapture(mp)
        return START_NOT_STICKY
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startCapture(mp: MediaProjection) {
        val config = AudioPlaybackCaptureConfiguration.Builder(mp)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val ar = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(format)
            .setBufferSizeInBytes(max(minBuffer, FFT_SIZE * 4))
            .build()
        record = ar
        running = true
        ar.startRecording()

        thread(name = "playback-capture") {
            val analyzer = SpectrumAnalyzer(SAMPLE_RATE, FFT_SIZE)
            val hop = ShortArray(FFT_SIZE / 4) // ~21 ms at 48 kHz -> smooth animation
            val frame = FloatArray(FFT_SIZE)
            var lastLog = 0L
            while (running) {
                val read = ar.read(hop, 0, hop.size)
                if (read <= 0) continue
                // Slide the analysis window along by the samples just read.
                System.arraycopy(frame, read, frame, 0, FFT_SIZE - read)
                var sumSquares = 0.0
                for (i in 0 until read) {
                    val s = hop[i] / 32768f
                    frame[FFT_SIZE - read + i] = s
                    sumSquares += s * s
                }
                val rmsDb = 20f * log10(max(sqrt(sumSquares / read).toFloat(), 1e-5f))
                val bands = analyzer.analyze(frame) ?: continue
                AudioLevels.publish(AudioLevels.Snapshot(AudioLevels.Source.Capture, bands, rmsDb))

                val now = System.currentTimeMillis()
                if (now - lastLog > 1000) {
                    lastLog = now
                    Log.i(TAG, "capture rms=%.1f dBFS bands=%s".format(rmsDb, bands.joinToString { "%.2f".format(it) }))
                }
            }
        }
    }

    override fun onDestroy() {
        running = false
        record?.run { stop(); release() }
        record = null
        projection?.stop()
        projection = null
        AudioLevels.clear()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.capture_channel_name),
            NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "PerfectSound"
        private const val CHANNEL_ID = "capture"
        private const val NOTIFICATION_ID = 1
        private const val SAMPLE_RATE = 48_000
        private const val FFT_SIZE = 4096
        private const val EXTRA_RESULT_CODE = "resultCode"
        private const val EXTRA_RESULT_DATA = "resultData"
        private const val ACTION_STOP = "app.perfectsound.player.capture.STOP"

        fun start(context: Context, resultCode: Int, data: Intent) {
            context.startForegroundService(Intent(context, PlaybackCaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, PlaybackCaptureService::class.java).setAction(ACTION_STOP))
        }
    }
}
