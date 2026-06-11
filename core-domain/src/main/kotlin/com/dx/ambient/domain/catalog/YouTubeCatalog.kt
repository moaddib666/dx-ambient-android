package com.dx.ambient.domain.catalog

import com.dx.ambient.domain.model.MediaSource

/** A pickable remote playlist (the user's own or one bundled with the app). */
data class CatalogPlaylist(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
    /** True for playlists shipped with the app (always offered, no sign-in needed). */
    val isBuiltIn: Boolean = false,
)

/**
 * Read-only bridge that lets the scene editor offer YouTube sources without depending on
 * the isolated `optional-youtube` module. Implemented there; bound via Hilt in the app.
 *
 * POLICY: metadata only — playback of any source produced here still happens exclusively
 * through the official IFrame player in the optional module.
 */
interface YouTubeCatalog {
    /** True when YouTube mode can be used right now (configured + silently signed in). */
    suspend fun isAvailable(): Boolean

    /** The signed-in user's playlists; empty when unavailable or the call fails. */
    suspend fun myPlaylists(): List<CatalogPlaylist>

    /** Playlists bundled with the app — always offered. */
    fun builtInPlaylists(): List<CatalogPlaylist>

    /**
     * Parses a user-pasted YouTube video/playlist link (or bare id) into a YOUTUBE
     * [MediaSource]; null when the input isn't a YouTube reference.
     */
    fun parseLink(input: String): MediaSource?

    /** Builds the YOUTUBE [MediaSource] for a playlist picked from this catalog. */
    fun playlistSource(playlist: CatalogPlaylist): MediaSource
}
