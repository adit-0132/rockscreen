package io.github.adit0132.rockscreen.util

import java.text.Normalizer

object TrackKeyUtils {
    private val NOT_TEXT = Regex("[^\\p{L}\\p{M}\\p{N} ]")
    private val SPACES = Regex("\\s+")

    /**
     * Normalized cache key: NFKC, lowercased, punctuation stripped, whitespace collapsed.
     * Letters, combining marks and digits of every script are kept, so non-Latin titles stay distinct.
     * "The Strokes" + "The Adults Are Talking" → "the strokes::the adults are talking"
     * "米津玄師" + "感電" → "米津玄師::感電"
     */
    fun make(artist: String, title: String): String {
        val norm = { s: String ->
            Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase().replace(NOT_TEXT, "").replace(SPACES, " ").trim()
        }
        return "${norm(artist)}::${norm(title)}"
    }

    /**
     * Drops a player's annotation from the artist field. Spotify appends " • Recommended for you"
     * (localized) on smart-shuffle tracks, which makes every lyrics lookup for them miss.
     */
    fun cleanArtist(artist: String): String = artist.substringBefore(" • ").trim()
}
