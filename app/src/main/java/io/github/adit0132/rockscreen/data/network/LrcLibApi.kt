package io.github.adit0132.rockscreen.data.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.github.adit0132.rockscreen.BuildConfig

@Serializable
data class LrcLibResponse(
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
    val title: String? = null,
    val artistName: String? = null
)

class LrcLibApi {

    private val client = HttpClient(Android) {
        // LRCLIB asks every client to identify itself (name, version, project URL).
        install(UserAgent) {
            agent = "Rockscreen/${BuildConfig.VERSION_NAME} (+https://github.com/adit-0132/rockscreen)"
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
            requestTimeoutMillis = 20_000
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    /**
     * Returns the raw LRC string, or null if not found / network error.
     * Uses the /api/get endpoint with track_name + artist_name.
     */
    suspend fun fetchSyncedLyrics(artist: String, title: String): String? {
        return try {
            val response: LrcLibResponse = client.get("https://lrclib.net/api/get") {
                parameter("artist_name", artist)
                parameter("track_name", title)
            }.body()
            response.syncedLyrics?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    fun close() = client.close()
}
