package io.github.adit0132.rockscreen.data.repository

import io.github.adit0132.rockscreen.data.db.AppDatabase
import io.github.adit0132.rockscreen.data.db.CachedLyrics
import io.github.adit0132.rockscreen.data.model.LrcLine
import io.github.adit0132.rockscreen.data.model.LyricsResult
import io.github.adit0132.rockscreen.data.network.LrcLibApi
import io.github.adit0132.rockscreen.data.parser.LrcParser
import io.github.adit0132.rockscreen.util.TrackKeyUtils

class LyricsRepository(
    private val db: AppDatabase,
    private val api: LrcLibApi
) {
    companion object {
        private const val TTL_MS = 30L * 24 * 60 * 60 * 1000  // 30 days
        private const val MAX_CACHE_ENTRIES = 500
    }

    /**
     * Returns parsed lyrics for a track.
     * Checks Room cache first; fetches from LRCLib on miss.
     * Caches both hits and misses (miss = hasLyrics=false) to avoid hammering the API.
     */
    suspend fun getLyrics(artist: String, title: String): LyricsResult {
        val key = TrackKeyUtils.make(artist, title)
        val dao = db.lyricsDao()
        val now = System.currentTimeMillis()

        // 1. Cache check
        val cached = dao.getByKey(key)
        if (cached != null && (now - cached.fetchedAt) < TTL_MS) {
            return if (cached.hasLyrics) {
                LyricsResult.Found(LrcParser.parse(cached.lrcContent))
            } else {
                LyricsResult.NotFound
            }
        }

        // 2. Fetch from LRCLib
        val lrc = api.fetchSyncedLyrics(artist, title)
        val hasLyrics = lrc != null

        // 3. Store in cache
        dao.insert(
            CachedLyrics(
                trackKey = key,
                lrcContent = lrc ?: "",
                fetchedAt = now,
                hasLyrics = hasLyrics,
                title = title,
                artist = artist
            )
        )

        return if (hasLyrics) {
            LyricsResult.Found(LrcParser.parse(lrc!!))
        } else {
            LyricsResult.NotFound
        }
    }
}
