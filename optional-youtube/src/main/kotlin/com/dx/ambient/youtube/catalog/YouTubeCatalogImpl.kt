package com.dx.ambient.youtube.catalog

import com.dx.ambient.domain.catalog.CatalogPlaylist
import com.dx.ambient.domain.catalog.YouTubeCatalog
import com.dx.ambient.domain.model.MediaSource
import com.dx.ambient.youtube.YouTubeConfig
import com.dx.ambient.youtube.YouTubeMode
import com.dx.ambient.youtube.auth.YouTubeAuthClient
import com.dx.ambient.youtube.auth.YouTubeAuthClient.AuthOutcome
import com.dx.ambient.youtube.data.YouTubeRepository
import com.dx.ambient.youtube.featured.FeaturedPlaylistsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [YouTubeCatalog] backed by the optional YouTube module: silent OAuth + Data API metadata.
 *
 * POLICY: metadata only. Sources produced here always carry [com.dx.ambient.domain.model.MediaSourceType.YOUTUBE]
 * and are rendered exclusively by the official IFrame player.
 */
@Singleton
class YouTubeCatalogImpl @Inject constructor(
    private val config: YouTubeConfig,
    private val auth: YouTubeAuthClient,
    private val repository: YouTubeRepository,
) : YouTubeCatalog {

    override suspend fun isAvailable(): Boolean =
        config.hasGooglePlayServices && config.isConfigured &&
            runCatching { auth.authorize() is AuthOutcome.Token }.getOrDefault(false)

    override suspend fun myPlaylists(): List<CatalogPlaylist> =
        runCatching {
            auth.withFreshToken { token -> repository.fetchMyPlaylists(token) }
                .orEmpty()
                .map { CatalogPlaylist(id = it.id, title = it.title, thumbnailUrl = it.thumbnailUrl) }
        }.getOrDefault(emptyList())

    override fun builtInPlaylists(): List<CatalogPlaylist> =
        FeaturedPlaylistsRepository.DEFAULTS.map {
            CatalogPlaylist(
                id = it.playlistId,
                title = it.title,
                thumbnailUrl = it.thumbnailUrl,
                isBuiltIn = true,
            )
        }

    override fun parseLink(input: String): MediaSource? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val isYouTubeUrl = trimmed.contains("youtube.com", ignoreCase = true) ||
            trimmed.contains("youtu.be", ignoreCase = true)
        // Bare inputs are accepted only when they unambiguously look like YouTube ids
        // (11-char video id or a conventional playlist-id prefix) — not any word.
        val isBareId = !trimmed.contains('/') && !trimmed.contains('?') &&
            (VIDEO_ID_REGEX.matches(trimmed) || YouTubeMode.extractPlaylistId(trimmed) != null)
        val resolvable = YouTubeMode.extractPlaylistId(trimmed) != null ||
            YouTubeMode.extractVideoId(trimmed) != null
        return if ((isYouTubeUrl || isBareId) && resolvable) {
            YouTubeMode.youTubeSource(trimmed)
        } else {
            null
        }
    }

    override fun playlistSource(playlist: CatalogPlaylist): MediaSource =
        YouTubeMode.youTubeSource("https://www.youtube.com/playlist?list=${playlist.id}")
            .copy(displayName = playlist.title)

    private companion object {
        /** Canonical YouTube video id shape. */
        val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class YouTubeCatalogModule {
    @Binds
    abstract fun bindYouTubeCatalog(impl: YouTubeCatalogImpl): YouTubeCatalog
}
