package com.dx.ambient.domain.model

import kotlinx.serialization.Serializable

/**
 * An alpha mask / overlay applied on top of the scene's video output.
 *
 * Masks are PNGs with an alpha channel (MVP feature 5/6). Rendering a mask forces the
 * composited render path (TextureView/GL or Media3 Composition) which is heavier than the
 * default [android.view.SurfaceView] fast path, so it can always be turned off
 * ([Mask.NONE]) for the performance-safe fallback (MVP feature 8).
 */
@Serializable
data class Mask(
    /** Persistable URI of the PNG alpha mask, or empty for [NONE]. */
    val uri: String,
    val displayName: String? = null,
    val scaleMode: MaskScaleMode = MaskScaleMode.FILL,
    /** 0f (mask invisible) .. 1f (fully applied). */
    val opacity: Float = 1f,
) {
    val enabled: Boolean get() = uri.isNotEmpty()

    companion object {
        /** The "no mask" fallback — keeps the fast SurfaceView render path. */
        val NONE = Mask(uri = "")
    }
}

@Serializable
enum class MaskScaleMode {
    /** Stretch the mask to fill the output surface. */
    FILL,
    /** Preserve mask aspect ratio, fitting inside the surface. */
    FIT,
    /** Tile the mask across the surface. */
    TILE,
}
