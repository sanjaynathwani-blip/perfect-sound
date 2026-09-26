package app.perfectsound.player.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the cover picture embedded in an audio file's tags, scaled down to about [maxPx]. */
suspend fun loadEmbeddedArt(context: Context, uri: Uri, maxPx: Int = 640): Bitmap? = withContext(Dispatchers.IO) {
    val bytes = MediaMetadataRetriever().run {
        try {
            setDataSource(context, uri)
            embeddedPicture
        } catch (_: RuntimeException) {
            null // unreadable or missing file
        } finally {
            release()
        }
    } ?: return@withContext null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}
