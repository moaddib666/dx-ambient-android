package com.dx.ambient.domain.model

import kotlinx.serialization.Serializable

/**
 * A reference to a piece of media used by a [Scene].
 *
 * URIs are kept as plain [String]s so the domain module stays free of any Android types.
 * Persistable SAF/`content://` URIs, `file://` paths, and (for the optional module) YouTube
 * references all flow through this single type.
 */
@Serializable
data class MediaSource(
    val uri: String,
    val type: MediaSourceType,
    /** Optional human-friendly label, e.g. the original file name. */
    val displayName: String? = null,
) {
    val isYouTube: Boolean get() = type == MediaSourceType.YOUTUBE

    companion object {
        val NONE = MediaSource(uri = "", type = MediaSourceType.NONE)
    }
}

@Serializable
enum class MediaSourceType {
    /** No source selected (e.g. a video-only or audio-only scene). */
    NONE,
    LOCAL_VIDEO,
    LOCAL_AUDIO,
    LOCAL_IMAGE,

    /**
     * A YouTube playlist or video, played ONLY through the official IFrame player in the
     * isolated `optional-youtube` module. Never extracted, never backgrounded.
     */
    YOUTUBE,
}
