package com.dx.ambient.domain.model

import kotlinx.serialization.Serializable

/**
 * The central MVP data model (feature 4).
 *
 * A scene fully describes an ambient experience: where the picture comes from, where the
 * sound comes from, an optional alpha mask, brightness, and how it loops. Scenes are saved
 * and loaded (MVP feature 7) and are the unit the user authors and switches between.
 *
 * Video and audio are intentionally separate sources so a user can, for example, loop a
 * fireplace video with a separate rain soundtrack. When [audioSource] is [MediaSource.NONE]
 * the audio track embedded in [videoSource] (if any) is used instead.
 */
@Serializable
data class Scene(
    val id: String,
    val name: String,

    /** Primary picture source. May be a video, a still image, or NONE for audio-only scenes. */
    val videoSource: MediaSource = MediaSource.NONE,

    /**
     * Additional picture sources for playlist loop modes. [videoSource] is treated as the
     * first entry; this list holds the remainder.
     */
    val videoPlaylist: List<MediaSource> = emptyList(),

    /** Sound source. NONE means "use the video's own audio track". */
    val audioSource: MediaSource = MediaSource.NONE,

    val mask: Mask = Mask.NONE,

    /** Output brightness/dim multiplier, 0f (black) .. 1f (full). Projector-safe default below 1. */
    val brightness: Float = DEFAULT_BRIGHTNESS,

    /**
     * Opacity of the video layer only, 0f..1f. Unlike [brightness] (a scrim over the whole
     * stage, mask included) this fades just the picture, keeping the mask fully lit.
     */
    val videoAlpha: Float = 1f,

    /** Scale factor of the video layer (1f = fit the screen). The mask is not scaled. */
    val videoScale: Float = 1f,

    val loopMode: LoopMode = LoopMode.LOOP_ONE,

    /** Temporarily excluded from the home row and scene switching; editable in edit mode. */
    val hidden: Boolean = false,

    /** True when audio playback is muted regardless of the chosen audio source. */
    val muted: Boolean = false,

    /** Optional preview image (URI) shown on the home grid card; falls back to the video source. */
    val thumbnailUri: String? = null,

    /** Ordering hint for the home/scene grid. */
    val sortOrder: Int = 0,

    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
) {
    val hasVideo: Boolean get() = videoSource.type != MediaSourceType.NONE
    val hasMask: Boolean get() = mask.enabled

    /** All picture sources in playback order, including [videoSource] as the head. */
    val fullVideoPlaylist: List<MediaSource>
        get() = buildList {
            if (hasVideo) add(videoSource)
            addAll(videoPlaylist)
        }

    /** True when this scene needs the heavier composited render path rather than SurfaceView. */
    val requiresCompositedRendering: Boolean
        get() = hasMask || brightness < 1f || videoAlpha < 1f || videoScale != 1f

    companion object {
        const val DEFAULT_BRIGHTNESS = 0.85f
    }
}
