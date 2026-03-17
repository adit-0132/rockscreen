package com.verselock.service

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import com.verselock.data.model.TrackInfo

class MediaListenerService : NotificationListenerService() {

    private var activeController: MediaController? = null
    private var currentTrack: TrackInfo? = null

    private val controllerCallback = object : MediaController.Callback() {

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            metadata ?: return
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: return
            val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
            
            var artUriStr = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)

            if (artUriStr == null) {
                // Try embedded bitmap from metadata (may be null due to IPC Binder size limits)
                var bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)

                // Fallback: extract album art from the media app's notification large icon.
                // Apps like Spotify embed art as a bitmap that gets stripped during IPC,
                // but the notification's large icon is always available in-process.
                if (bitmap == null) {
                    bitmap = extractNotificationArt()
                }

                if (bitmap != null) {
                    try {
                        val uniqueFileName = "album_art_${System.currentTimeMillis()}.png"
                        val file = java.io.File(cacheDir, uniqueFileName)
                        java.io.FileOutputStream(file).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        artUriStr = android.net.Uri.fromFile(file).toString()
                        
                        cacheDir.listFiles()?.forEach { cachedFile ->
                            if (cachedFile.name.startsWith("album_art_") && cachedFile.name != uniqueFileName) {
                                cachedFile.delete()
                            }
                        }
                    } catch(e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

            val newTrack = TrackInfo(title, artist, album, albumArtUri = artUriStr, durationMs = duration)
            if (newTrack != currentTrack) {
                currentTrack = newTrack
                LyricsService.onTrackChanged(this@MediaListenerService, newTrack)
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            when (state?.state) {
                PlaybackState.STATE_PLAYING -> {
                    LyricsService.onPlaybackStarted(
                        this@MediaListenerService,
                        state.position,
                        state.lastPositionUpdateTime
                    )
                }
                PlaybackState.STATE_PAUSED,
                PlaybackState.STATE_STOPPED -> {
                    LyricsService.onPlaybackPaused(this@MediaListenerService)
                }
                else -> {}
            }
        }
    }

    /**
     * Extracts album art from the active media notification's large icon.
     * This is the reliable fallback for apps like Spotify where the MediaMetadata
     * bitmap is silently stripped during Binder IPC due to transaction size limits.
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
            e.printStackTrace()
        }
        return null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshActiveController()
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        refreshActiveController()
    }

    private fun refreshActiveController() {
        val manager = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        val component = ComponentName(this, MediaListenerService::class.java)
        try {
            val controllers = manager.getActiveSessions(component)
            val newController = controllers.firstOrNull()
            if (newController?.sessionToken != activeController?.sessionToken) {
                activeController?.unregisterCallback(controllerCallback)
                activeController = newController
                newController?.registerCallback(controllerCallback)
                // Immediately read current state
                newController?.metadata?.let { controllerCallback.onMetadataChanged(it) }
                newController?.playbackState?.let { controllerCallback.onPlaybackStateChanged(it) }
            }
        } catch (e: SecurityException) {
            // Notification access not granted
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "com.verselock.ACTION_PLAY" -> activeController?.transportControls?.play()
            "com.verselock.ACTION_PAUSE" -> activeController?.transportControls?.pause()
            "com.verselock.ACTION_NEXT" -> activeController?.transportControls?.skipToNext()
            "com.verselock.ACTION_PREV" -> activeController?.transportControls?.skipToPrevious()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        activeController?.unregisterCallback(controllerCallback)
        super.onDestroy()
    }
}
