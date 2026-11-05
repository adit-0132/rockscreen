package io.github.adit0132.rockscreen.service

import android.content.Context
import android.os.SystemClock
import io.github.adit0132.rockscreen.data.model.LyricsResult
import io.github.adit0132.rockscreen.data.model.TrackInfo
import io.github.adit0132.rockscreen.RockscreenApplication
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Process-wide playback and lyrics state. MediaListenerService (bound by the system while
 * notification access is granted) feeds it; the lock screen observes it. This replaces the old
 * always-on LyricsService foreground service: nothing here runs unless the listener reports a change,
 * and the active lyric line is computed by the lock screen only while it is on screen.
 */
object PlaybackHub {

    /** Last reported player position. [now] extrapolates while playing and honours playback speed. */
    data class Position(
        val positionMs: Long = 0L,
        val updatedAtRealtime: Long = 0L,
        val speed: Float = 1f,
        val playing: Boolean = false,
    ) {
        fun now(): Long =
            if (playing) positionMs + ((SystemClock.elapsedRealtime() - updatedAtRealtime) * speed).toLong()
            else positionMs
    }

    private val _currentTrack = MutableStateFlow<TrackInfo?>(null)
    private val _lyricsResult = MutableStateFlow<LyricsResult>(LyricsResult.Loading)
    private val _isPlaying = MutableStateFlow(false)
    private val _position = MutableStateFlow(Position())

    val currentTrack: StateFlow<TrackInfo?> = _currentTrack.asStateFlow()
    val lyricsResult: StateFlow<LyricsResult> = _lyricsResult.asStateFlow()
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    val position: StateFlow<Position> = _position.asStateFlow()

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, _ -> _lyricsResult.value = LyricsResult.NotFound }
    )

    fun onTrackChanged(context: Context, track: TrackInfo) {
        _currentTrack.value = track
        _lyricsResult.value = LyricsResult.Loading
        if (track.artist.isBlank()) {
            // No artist (e.g. a video or some web players): there's nothing reliable to look up.
            _lyricsResult.value = LyricsResult.NotFound
            return
        }
        val app = context.applicationContext as RockscreenApplication
        scope.launch {
            val result = withContext(Dispatchers.IO) { app.lyricsRepository.getLyrics(track.artist, track.title) }
            _lyricsResult.value = result
        }
    }

    /** Media without a title: clear the track instead of leaving the previous song on screen. */
    fun onTrackCleared() {
        _currentTrack.value = null
        _lyricsResult.value = LyricsResult.NotFound
    }

    fun onPlaying(positionMs: Long, updatedAtRealtime: Long, speed: Float) {
        val updated = if (updatedAtRealtime > 0L) updatedAtRealtime else SystemClock.elapsedRealtime()
        _position.value = Position(positionMs.coerceAtLeast(0L), updated, if (speed > 0f) speed else 1f, playing = true)
        _isPlaying.value = true
    }

    /** Paused, stopped, buffering, errored, or the session is gone. Keeps the last (or reported) position. */
    fun onNotPlaying(positionMs: Long? = null) {
        val last = _position.value
        _position.value = Position(positionMs ?: last.now(), SystemClock.elapsedRealtime(), last.speed, playing = false)
        _isPlaying.value = false
    }
}
