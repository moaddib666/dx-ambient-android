package com.dx.ambient.youtube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject

/**
 * Reads the signed-in user's YouTube data via the official Data API v3.
 *
 * POLICY: this only fetches *metadata* (the user's own playlists). Playback always happens
 * through the official IFrame player — never extraction, never background.
 */
class YouTubeRepository @Inject constructor() {

    /** Fetches the user's playlists. [accessToken] is a `youtube.readonly` OAuth token. */
    suspend fun fetchMyPlaylists(accessToken: String): List<YouTubePlaylist> =
        withContext(Dispatchers.IO) {
            val body = getJson(
                "https://www.googleapis.com/youtube/v3/playlists" +
                    "?part=snippet,contentDetails&mine=true&maxResults=50",
                accessToken,
            )
            AmbientYouTubeJson.decodeFromString(PlaylistListResponse.serializer(), body)
                .items.map { it.toDomain() }
        }

    /**
     * Resolves a playlist into its video ids via the *authorized* Data API.
     *
     * The anonymous IFrame embed cannot open private/unlisted playlists
     * (`listType: 'playlist'` fails with embed error 150/152), but the OAuth
     * token can read them — so the player is fed concrete video ids instead.
     * Capped at [maxVideos] (the IFrame array-playlist limit is ~200).
     */
    suspend fun fetchPlaylistVideoIds(
        accessToken: String,
        playlistId: String,
        maxVideos: Int = 200,
    ): List<String> = withContext(Dispatchers.IO) {
        val ids = mutableListOf<String>()
        var pageToken: String? = null
        do {
            val body = getJson(
                "https://www.googleapis.com/youtube/v3/playlistItems" +
                    "?part=contentDetails&maxResults=50" +
                    "&playlistId=${URLEncoder.encode(playlistId, "UTF-8")}" +
                    (pageToken?.let { "&pageToken=${URLEncoder.encode(it, "UTF-8")}" } ?: ""),
                accessToken,
            )
            val (pageIds, nextToken) = parsePlaylistItemIds(body)
            ids += pageIds
            pageToken = nextToken
        } while (pageToken != null && ids.size < maxVideos)
        ids.take(maxVideos)
    }

    /**
     * Fetches the signed-in user's own channel (title + avatar) — the identity shown
     * in the YouTube tab header. Returns null when the account has no channel.
     */
    suspend fun fetchMyChannel(accessToken: String): YouTubeChannel? =
        withContext(Dispatchers.IO) {
            val body = getJson(
                "https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true",
                accessToken,
            )
            AmbientYouTubeJson.decodeFromString(ChannelListResponse.serializer(), body)
                .items.firstOrNull()
                ?.let { item ->
                    YouTubeChannel(
                        title = item.snippet?.title.orEmpty(),
                        thumbnailUrl = item.snippet?.thumbnails?.bestUrl(),
                    )
                }
        }

    private fun getJson(urlString: String, accessToken: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
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
                throw YouTubeHttpException(code, "YouTube API HTTP $code: ${err.take(300)}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

/** Data API failure carrying the HTTP status, so auth can react to 401 (stale token). */
class YouTubeHttpException(val code: Int, message: String) : IOException(message)

/** The signed-in user's channel identity, shown in the YouTube tab header. */
data class YouTubeChannel(val title: String, val thumbnailUrl: String?)

internal val AmbientYouTubeJson = Json { ignoreUnknownKeys = true }

/** Parses one `playlistItems.list` page into (video ids, nextPageToken). Exposed for tests. */
internal fun parsePlaylistItemIds(body: String): Pair<List<String>, String?> {
    val response = AmbientYouTubeJson.decodeFromString(PlaylistItemsResponse.serializer(), body)
    val ids = response.items.mapNotNull { item ->
        item.contentDetails?.videoId?.takeIf { it.isNotBlank() }
    }
    return ids to response.nextPageToken
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

@Serializable
private data class ChannelListResponse(val items: List<ChannelItem> = emptyList())

@Serializable
private data class ChannelItem(val snippet: Snippet? = null)

@Serializable
internal data class PlaylistItemsResponse(
    val items: List<PlaylistVideoItem> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
internal data class PlaylistVideoItem(val contentDetails: PlaylistVideoDetails? = null)

@Serializable
internal data class PlaylistVideoDetails(val videoId: String? = null)
