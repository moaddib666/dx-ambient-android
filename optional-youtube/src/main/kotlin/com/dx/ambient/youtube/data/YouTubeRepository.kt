package com.dx.ambient.youtube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * Reads the signed-in user's YouTube data via the official Data API v3.
 *
 * POLICY: this only fetches *metadata* (the user's own playlists). Playback always happens
 * through the official IFrame player — never extraction, never background.
 */
class YouTubeRepository @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    /** Fetches the user's playlists. [accessToken] is a `youtube.readonly` OAuth token. */
    suspend fun fetchMyPlaylists(accessToken: String): List<YouTubePlaylist> =
        withContext(Dispatchers.IO) {
            val url = URL(
                "https://www.googleapis.com/youtube/v3/playlists" +
                    "?part=snippet,contentDetails&mine=true&maxResults=50",
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IOException("YouTube API HTTP $code: ${err.take(300)}")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString(PlaylistListResponse.serializer(), body).items.map { it.toDomain() }
            } finally {
                conn.disconnect()
            }
        }
}

private fun PlaylistItem.toDomain() = YouTubePlaylist(
    id = id,
    title = snippet?.title.orEmpty().ifBlank { "Untitled playlist" },
    thumbnailUrl = snippet?.thumbnails?.bestUrl(),
    itemCount = contentDetails?.itemCount ?: 0,
)

@Serializable
private data class PlaylistListResponse(val items: List<PlaylistItem> = emptyList())

@Serializable
private data class PlaylistItem(
    val id: String,
    val snippet: Snippet? = null,
    val contentDetails: ContentDetails? = null,
)

@Serializable
private data class Snippet(val title: String = "", val thumbnails: Thumbnails? = null)

@Serializable
private data class Thumbnails(
    val high: Thumb? = null,
    val medium: Thumb? = null,
    val default: Thumb? = null,
) {
    fun bestUrl(): String? = (high ?: medium ?: default)?.url
}

@Serializable
private data class Thumb(val url: String = "")

@Serializable
private data class ContentDetails(@SerialName("itemCount") val itemCount: Int = 0)
