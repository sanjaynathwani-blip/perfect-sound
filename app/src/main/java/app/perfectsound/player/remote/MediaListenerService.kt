package app.perfectsound.player.remote

import android.service.notification.NotificationListenerService

/**
 * Exists only so the user can grant notification access: Android requires it before an app may
 * see and control other apps' media sessions (Spotify, Chrome). Notifications are ignored.
 */
class MediaListenerService : NotificationListenerService()
