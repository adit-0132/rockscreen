package io.github.adit0132.rockscreen.service

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.adit0132.rockscreen.R
import io.github.adit0132.rockscreen.data.model.LyricsResult
import io.github.adit0132.rockscreen.ui.LockScreenActivity

/**
 * Puts the lyrics screen over the keyguard. Android blocks starting an activity from the background,
 * so this uses one of two supported paths:
 *  1. a full-screen-intent notification (the system launches the activity), or
 *  2. if the user granted "Display over other apps", a direct start (that grant exempts background starts).
 * It only acts while the device is asleep or locked, so it never covers an app the user is using.
 */
object LockScreenLauncher {
    private const val CHANNEL_ID = "lock_screen_lyrics"
    private const val NOTIF_ID = 2001

    fun maybeShow(context: Context) {
        if (!PlaybackHub.isPlaying.value) return
        if (PlaybackHub.lyricsResult.value is LyricsResult.NotFound) return
        if (LockScreenActivity.isAlive) return
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)

        if (!power.isInteractive) {
            // Screen off: SystemUI wakes the display for a full-screen intent (verified on Android 16),
            // so only the overlay path may prepare the lyrics screen now; it appears when the user wakes the phone.
            if (Settings.canDrawOverlays(context)) context.startActivity(LockScreenActivity.intent(context))
            return
        }
        if (!keyguard.isKeyguardLocked) return // in use and unlocked: never cover the user's app

        // Screen on and locked (the user just woke the phone, or pressed play on the lock screen).
        when {
            canUseFullScreenIntent(context) -> postFullScreenNotification(context)
            Settings.canDrawOverlays(context) -> context.startActivity(LockScreenActivity.intent(context))
        }
    }

    fun dismissNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }

    fun notificationsAllowed(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun fullScreenIntentAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun canUseFullScreenIntent(context: Context): Boolean =
        notificationsAllowed(context) && fullScreenIntentAllowed(context)

    private fun postFullScreenNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_lock_screen), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_lock_screen_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
        val intent = PendingIntent.getActivity(
            context, 0, LockScreenActivity.intent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(PlaybackHub.currentTrack.value?.title ?: context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notif_lyrics_ready))
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(intent, true)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
            // Notification permission revoked between the check and the post.
        }
    }
}
