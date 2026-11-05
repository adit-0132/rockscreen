package io.github.adit0132.rockscreen.data.model

data class TrackInfo(
    val title: String,
    val artist: String,
    val album: String = "",
    val albumArtUri: String? = null,
    val durationMs: Long = 0L
)
