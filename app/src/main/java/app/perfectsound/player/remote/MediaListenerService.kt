package app.perfectsound.player.remote

import android.annotation.SuppressLint
import android.app.Notification
import android.media.session.MediaSession
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.os.BundleCompat

/**
 * Exists so the user can grant notification access: Android requires it before an app may see and
 * control other apps' media sessions (Spotify, Chrome). The only notifications it looks at are media
 * ones, because Chrome's is the one place that says which site a tab is playing from.
 */
class MediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        connected = this
        onMediaChanged?.invoke()
    }

    override fun onListenerDisconnected() {
        if (connected === this) connected = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.notification.sessionToken() != null) onMediaChanged?.invoke()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.notification.sessionToken() != null) onMediaChanged?.invoke()
    }

    companion object {
        @SuppressLint("StaticFieldLeak") // cleared when the system unbinds the listener
        private var connected: MediaListenerService? = null

        /** Called on the main thread when a media notification is posted, updated or removed. */
        var onMediaChanged: (() -> Unit)? = null

        /** The notification an app shows for the media session [token], if access is on and it shows one. */
        fun mediaNotification(token: MediaSession.Token): Notification? =
            runCatching { connected?.activeNotifications }.getOrNull().orEmpty()
                .map { it.notification }
                .firstOrNull { it.sessionToken() == token }

        private fun Notification.sessionToken(): MediaSession.Token? =
            BundleCompat.getParcelable(extras, Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
    }
}
