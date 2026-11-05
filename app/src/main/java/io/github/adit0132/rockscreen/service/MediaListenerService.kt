package io.github.adit0132.rockscreen.service

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat
import io.github.adit0132.rockscreen.data.model.TrackInfo
import io.github.adit0132.rockscreen.util.TrackKeyUtils
import io.github.adit0132.rockscreen.util.lockScreenEnabledFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val KEY_ADVERTISEMENT = "android.media.metadata.ADVERTISEMENT" // MediaMetadataCompat's key; 0 = not an ad

class MediaListenerService : NotificationListenerService() {

    private var activeController: MediaController? = null
    private var currentTrack: TrackInfo? = null
    private var lockScreenEnabled = true
    private var scope: CoroutineScope? = null
    private var metadataJob: Job? = null
    private var connected = false

    private val controllerCallback = object : MediaController.Callback() {

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            metadata?.let { handleMetadata(it) }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            handlePlaybackState(state)
        }

        override fun onSessionDestroyed() {
            // The player went away without a final PAUSED/STOPPED (closed tab, crash, force-stop).
            PlaybackHub.onNotPlaying()
            refreshActiveController()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        selectController(controllers.orEmpty())
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                // Unlocked before the system opened the lyrics screen: don't leave "Lyrics ready" in the shade.
                LockScreenLauncher.dismissNotification(context)
            } else if (lockScreenEnabled) {
                LockScreenLauncher.maybeShow(context)
            }
        }
    }

    private fun handleMetadata(metadata: MediaMetadata) {
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (title.isNullOrBlank() || metadata.getLong(KEY_ADVERTISEMENT) != 0L) {
            // No title, or an ad (Spotify flags these): nothing to look up, and the last song shouldn't linger.
            currentTrack = null
            PlaybackHub.onTrackCleared()
            return
        }
        val artist = TrackKeyUtils.cleanArtist(
            metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: ""
        )
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

        val uriArt = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)

        // Without a URI: embedded bitmap from metadata, then the media notification's large icon.
        val bitmap = if (uriArt == null) {
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: extractNotificationArt()
        } else null

        val s = scope ?: return
        metadataJob?.cancel() // only the newest metadata may publish
        metadataJob = s.launch {
            val artUri = if (bitmap != null) withContext(Dispatchers.IO) { saveArt(bitmap) } else uriArt
            val newTrack = TrackInfo(title, artist, album, albumArtUri = artUri, durationMs = duration)
            if (newTrack != currentTrack) {
                currentTrack = newTrack
                PlaybackHub.onTrackChanged(this@MediaListenerService, newTrack)
            }
        }
    }

    /** Writes album art to the cache under a unique name (so image caches see a change) and deletes older copies. */
    private fun saveArt(bitmap: Bitmap): String? = try {
        val name = "album_art_${System.currentTimeMillis()}.png"
        val file = java.io.File(cacheDir, name)
        java.io.FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cacheDir.listFiles()?.forEach { f -> if (f.name.startsWith("album_art_") && f.name != name) f.delete() }
        android.net.Uri.fromFile(file).toString()
    } catch (e: Exception) {
        null
    }

    private fun handlePlaybackState(state: PlaybackState?) {
        if (state?.state == PlaybackState.STATE_PLAYING) {
            PlaybackHub.onPlaying(state.position, state.lastPositionUpdateTime, state.playbackSpeed)
            if (lockScreenEnabled) LockScreenLauncher.maybeShow(this) // acts only while asleep or locked
        } else {
            // Paused, stopped, buffering, error or none: never leave the UI "playing".
            PlaybackHub.onNotPlaying(state?.position?.takeIf { it >= 0 })
        }
    }

    /**
     * Extracts album art from the active media notification's large icon.
     * Fallback for apps that don't put a bitmap or URI into their MediaMetadata.
     */
    private fun extractNotificationArt(): Bitmap? {
        try {
            val notifications = activeNotifications ?: return null
            for (sbn in notifications) {
                val notification = sbn.notification ?: continue
                val extras = notification.extras ?: continue

                // Only consider notifications that belong to a media session
                @Suppress("DEPRECATION")
                val token = extras.getParcelable<MediaSession.Token>(
                    Notification.EXTRA_MEDIA_SESSION
                )
                if (token == null) continue

                // Try the extras bitmap first (works on most devices)
                @Suppress("DEPRECATION")
                val extrasBitmap = extras.getParcelable<Bitmap>(
                    Notification.EXTRA_LARGE_ICON
                )
                if (extrasBitmap != null) return extrasBitmap

                // Fallback: load from the Icon object
                val icon = notification.getLargeIcon() ?: continue
                val drawable = icon.loadDrawable(this@MediaListenerService)
                if (drawable is BitmapDrawable) {
                    return drawable.bitmap
                }
            }
        } catch (e: Exception) {
            // Best effort; no art is fine.
        }
        return null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (connected) return
        connected = true
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        s.launch { lockScreenEnabledFlow().collect { lockScreenEnabled = it } }
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
            },
            // USER_PRESENT comes from SystemUI, not the system server, so a not-exported receiver never gets it.
            // All three are protected broadcasts that other apps can't send.
            ContextCompat.RECEIVER_EXPORTED
        )
        try {
            sessionManager()?.addOnActiveSessionsChangedListener(sessionsListener, componentName())
        } catch (e: SecurityException) {
            // Notification access not granted
        }
        refreshActiveController()
    }

    override fun onListenerDisconnected() {
        disconnect()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        refreshActiveController()
    }

    private fun sessionManager() = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager

    private fun componentName() = ComponentName(this, MediaListenerService::class.java)

    private fun refreshActiveController() {
        val manager = sessionManager() ?: return
        try {
            selectController(manager.getActiveSessions(componentName()))
        } catch (e: SecurityException) {
            // Notification access not granted
        }
    }

    /** Follows the session that is playing; otherwise the most recently active one. */
    private fun selectController(controllers: List<MediaController>) {
        val newController = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()
        if (newController?.sessionToken == activeController?.sessionToken) return
        activeController?.unregisterCallback(controllerCallback)
        activeController = newController
        if (newController == null) {
            PlaybackHub.onNotPlaying()
            return
        }
        newController.registerCallback(controllerCallback)
        // Immediately read current state
        newController.metadata?.let { handleMetadata(it) }
        handlePlaybackState(newController.playbackState)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "io.github.adit0132.rockscreen.ACTION_PLAY" -> activeController?.transportControls?.play()
            "io.github.adit0132.rockscreen.ACTION_PAUSE" -> activeController?.transportControls?.pause()
            "io.github.adit0132.rockscreen.ACTION_NEXT" -> activeController?.transportControls?.skipToNext()
            "io.github.adit0132.rockscreen.ACTION_PREV" -> activeController?.transportControls?.skipToPrevious()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun disconnect() {
        if (!connected) return
        connected = false
        try { sessionManager()?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) {}
        try { unregisterReceiver(screenReceiver) } catch (_: IllegalArgumentException) {}
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
        currentTrack = null
        PlaybackHub.onNotPlaying()
        scope?.cancel()
        scope = null
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }
}
