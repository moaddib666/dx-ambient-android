/*
 * ============================================================================
 *  ISOLATED, OPTIONAL YOUTUBE MODULE — POLICY (DO NOT VIOLATE)
 * ============================================================================
 *  YouTube playback in this app MUST use ONLY the official IFrame Player API
 *  inside a WebView.
 *
 *  - DO NOT implement audio extraction.
 *  - DO NOT separate audio from video.
 *  - DO NOT implement background playback. The player MUST pause whenever the
 *    hosting screen is not in the RESUMED lifecycle state.
 *  - The embedded player viewport must be at least 200x200 px (the YouTube
 *    embed requirement). Fill the screen; never crop the live player to a tiny
 *    portal.
 *
 *  This module is intentionally isolated (its own Gradle module, no Hilt) so
 *  the rest of the app never gains the ability to do any of the above. Keep it
 *  that way.
 * ============================================================================
 */
package com.dx.ambient.youtube

import com.dx.ambient.domain.model.MediaSource
import com.dx.ambient.domain.model.MediaSourceType

/**
 * Pure-Kotlin helpers for the optional YouTube mode.
 *
 * No Android types, no side effects — this is just gating + URL parsing so it
 * can be unit-tested in isolation. Actual playback lives in
 * [YouTubeIFrameScreen], which is the ONLY place a YouTube video is ever
 * rendered, and only ever through the official IFrame Player API.
 *
 * See the file-level policy KDoc above: no audio extraction, no background
 * playback, no cropping below 200x200 px.
 */
object YouTubeMode {

    /**
     * Whether YouTube mode may be offered on this device.
     *
     * The official IFrame Player relies on Google Play Services being present,
     * so we gate on that single signal. If Play Services are unavailable the
     * caller should hide/disable YouTube affordances entirely rather than
     * attempting any fallback (there is no policy-compliant fallback).
     */
    fun isSupported(hasGooglePlayServices: Boolean): Boolean = hasGooglePlayServices

    /**
     * Build a [MediaSource] of type [MediaSourceType.YOUTUBE] from a raw video
     * id or URL. The raw input is stored verbatim as the [MediaSource.uri];
     * extraction of the concrete video/playlist id happens at render time via
     * [extractVideoId] / [extractPlaylistId].
     */
    fun youTubeSource(idOrUrl: String): MediaSource =
        MediaSource(uri = idOrUrl, type = MediaSourceType.YOUTUBE)

    /**
     * Extract a YouTube video id from a variety of inputs:
     *  - `https://www.youtube.com/watch?v=VIDEOID`
     *  - `https://youtu.be/VIDEOID`
     *  - `https://www.youtube.com/embed/VIDEOID`
     *  - `https://www.youtube.com/shorts/VIDEOID`
     *  - `https://www.youtube.com/live/VIDEOID`
     *  - a bare `VIDEOID`
     *
     * Returns `null` if the input looks like a URL but contains no recognizable
     * video id (e.g. a playlist-only URL). If the input has no recognizable URL
     * structure at all it is treated as already being an id and returned
     * sanitized.
     */
    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        if (!looksLikeUrl(trimmed)) {
            return sanitizeId(trimmed).takeIf { it.isNotEmpty() }
        }

        // watch?v=... (also covers any other query carrying a v= param)
        WATCH_V_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let {
            return sanitizeId(it).takeIf { id -> id.isNotEmpty() }
        }

        // youtu.be/ID , /embed/ID , /shorts/ID , /live/ID , /v/ID
        PATH_ID_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let {
            return sanitizeId(it).takeIf { id -> id.isNotEmpty() }
        }

        return null
    }

    /**
     * Extract a YouTube playlist id from inputs carrying a `list=` query
     * parameter, e.g. `https://www.youtube.com/playlist?list=PLAYLISTID` or
     * `https://www.youtube.com/watch?v=VIDEOID&list=PLAYLISTID`.
     *
     * If the input is not URL-shaped but already looks like a playlist id
     * (conventionally prefixed with `PL`, `OL`, `RD`, `UU`, `FL`, `LL`), it is
     * returned as-is. Otherwise returns `null`.
     */
    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        if (!looksLikeUrl(trimmed)) {
            return if (PLAYLIST_ID_PREFIX_REGEX.containsMatchIn(trimmed)) {
                sanitizeId(trimmed).takeIf { it.isNotEmpty() }
            } else {
                null
            }
        }

        return LIST_PARAM_REGEX.find(trimmed)?.groupValues?.getOrNull(1)
            ?.let { sanitizeId(it).takeIf { id -> id.isNotEmpty() } }
    }

    private fun looksLikeUrl(input: String): Boolean =
        input.contains("://") ||
            input.contains("youtube.com", ignoreCase = true) ||
            input.contains("youtu.be", ignoreCase = true) ||
            input.contains("/") ||
            input.contains("?") ||
            input.contains("&") ||
            input.contains("=")

    /** Keep only characters valid in YouTube ids (alphanumerics, `-`, `_`). */
    private fun sanitizeId(raw: String): String =
        raw.takeWhile { it != '&' && it != '?' && it != '#' && it != '/' }
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' }

    private val WATCH_V_REGEX = Regex("""[?&]v=([^&#?/]+)""", RegexOption.IGNORE_CASE)

    private val PATH_ID_REGEX =
        Regex(
            """(?:youtu\.be/|/embed/|/shorts/|/live/|/v/)([^&#?/]+)""",
            RegexOption.IGNORE_CASE,
        )

    private val LIST_PARAM_REGEX = Regex("""[?&]list=([^&#?/]+)""", RegexOption.IGNORE_CASE)

    private val PLAYLIST_ID_PREFIX_REGEX =
        Regex("""^(PL|OL|RD|UU|FL|LL)""", RegexOption.IGNORE_CASE)
}
