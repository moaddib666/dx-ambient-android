package com.dx.ambient.rendering

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import coil.compose.AsyncImage
import com.dx.ambient.domain.model.MediaSourceType
import com.dx.ambient.domain.model.Scene
import com.dx.ambient.domain.playback.PlaybackStatus
import com.dx.ambient.playback.AmbientPlayer

/**
 * Renders the active [scene]'s picture plus the projector-oriented overlays.
 *
 * Render strategy:
 *  - Default fast path: a Media3 [PlayerSurface] backed by a [android.view.SurfaceView] — the
 *    cheapest, smoothest option and the right default for weak projector hardware.
 *  - Still-image scenes are drawn with Coil instead of the player.
 *  - A PNG alpha mask (MVP feature 5) is drawn on TOP of the picture as a frame/overlay. Because
 *    SurfaceView sits behind the window, the Compose overlay composites over it directly.
 *  - Brightness (< 1f) is a black scrim, giving the projector-safe dim mode (MVP feature 9).
 *
 * Masks here are decorative alpha overlays. For true alpha-channel cutouts on capable devices,
 * the upgrade path is Media3 effects (`Player.setVideoEffects` with a `BitmapOverlay`) — see the
 * `optional` effect wiring; the overlay approach is kept as the always-works fallback.
 */
@Composable
fun AmbientStage(
    player: AmbientPlayer,
    scene: Scene,
    modifier: Modifier = Modifier,
) {
    val state by player.state.collectAsStateWithLifecycle()
    val brightness = state.currentBrightness

    // Boot/scene-change fade: hold black until the picture is actually playing, then cross-fade
    // the scene in. Combined with the black launch splash this gives a seamless "black → scene".
    val revealed = state.status == PlaybackStatus.PLAYING
    val coverAlpha by animateFloatAsState(
        targetValue = if (revealed) 0f else 1f,
        animationSpec = tween(durationMillis = 900),
        label = "scene-reveal",
    )

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        when (scene.videoSource.type) {
            MediaSourceType.LOCAL_IMAGE -> {
                AsyncImage(
                    model = scene.videoSource.uri,
                    contentDescription = scene.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            MediaSourceType.LOCAL_VIDEO -> {
                PlayerSurface(
                    player = player.videoPlayer as Player,
                    surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                // Audio-only or empty scene: keep a calm black stage.
            }
        }

        // PNG alpha mask overlay (MVP feature 5). Drawn above the picture.
        if (scene.hasMask) {
            AsyncImage(
                model = scene.mask.uri,
                contentDescription = null,
                contentScale = scene.mask.scaleMode.toContentScale(),
                alpha = scene.mask.opacity,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Brightness / dim scrim (MVP feature 9). brightness == 1f draws nothing.
        if (brightness < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (1f - brightness).coerceIn(0f, 1f))),
            )
        }

        // Reveal cover — fades from opaque black to transparent once playback starts.
        if (coverAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = coverAlpha)),
            )
        }
    }
}

private fun com.dx.ambient.domain.model.MaskScaleMode.toContentScale(): ContentScale = when (this) {
    com.dx.ambient.domain.model.MaskScaleMode.FILL -> ContentScale.FillBounds
    com.dx.ambient.domain.model.MaskScaleMode.FIT -> ContentScale.Fit
    com.dx.ambient.domain.model.MaskScaleMode.TILE -> ContentScale.FillBounds
}
