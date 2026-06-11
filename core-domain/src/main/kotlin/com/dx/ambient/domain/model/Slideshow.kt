package com.dx.ambient.domain.model

import kotlinx.serialization.Serializable

/**
 * Configuration for [SceneKind.SLIDESHOW] scenes — how long each image stays on screen and
 * how one image hands over to the next. Present on every [Scene] (with defaults) so the
 * payload JSON stays forward/backward compatible; non-slideshow scenes simply ignore it.
 */
@Serializable
data class SlideshowConfig(
    /** How long a single image is shown before advancing, in milliseconds. */
    val intervalMs: Long = DEFAULT_INTERVAL_MS,
    val transition: SlideTransition = SlideTransition.CROSSFADE,
) {
    companion object {
        const val DEFAULT_INTERVAL_MS = 10_000L
        const val MIN_INTERVAL_MS = 3_000L
        const val MAX_INTERVAL_MS = 120_000L
    }
}

/** How a slideshow moves from one image to the next. */
@Serializable
enum class SlideTransition {
    /** Hard cut. */
    NONE,

    /** Images cross-fade into each other. */
    CROSSFADE,

    /**
     * Ken Burns effect: each slide slowly zooms and pans while displayed, with a cross-fade
     * between slides. Zoom direction and pan corner alternate per slide for variety.
     */
    KEN_BURNS,
}
