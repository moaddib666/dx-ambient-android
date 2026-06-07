package com.dx.ambient.rendering.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import coil.compose.AsyncImage

/**
 * Full-screen ambient backdrop for menu/navigation screens.
 *
 * Picks a RANDOM `.webp` image from the `assets/ambient` folder, then layers a contrast scrim and a radial
 * edge-vignette so the picture fades to the background color at every edge. On a projector this
 * removes any visible frame — the image bleeds into black ("infinite border"), keeping the UI
 * immersive while leaving the centre clear and bright.
 *
 * The pick is stable for the lifetime of this composition, so each menu/navigation screen shows
 * one (different) ambient image and it doesn't flicker on recomposition.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background

    val asset = remember {
        runCatching { context.assets.list("ambient")?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.endsWith(".webp", ignoreCase = true) }
            .randomOrNull()
    }

    Box(modifier.fillMaxSize().background(background)) {
        if (asset != null) {
            AsyncImage(
                model = "file:///android_asset/ambient/$asset",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Overlay: a gentle vertical contrast scrim (for legible text) plus a radial vignette
        // that ramps from transparent in the centre to the opaque background at the edges.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val center = Offset(size.width * 0.5f, size.height * 0.42f)
                    val radius = size.maxDimension * 0.82f
                    val vignette = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1.0f to background,
                        ),
                        center = center,
                        radius = radius,
                    )
                    val contrast = Brush.verticalGradient(
                        colors = listOf(
                            background.copy(alpha = 0.55f),
                            background.copy(alpha = 0.28f),
                            background.copy(alpha = 0.62f),
                        ),
                    )
                    onDrawBehind {
                        drawRect(contrast)
                        drawRect(vignette)
                    }
                },
        )
    }
}

/**
 * Convenience wrapper that places menu/navigation [content] on top of a random [AmbientBackground].
 * Do NOT give the content its own opaque background, or it will hide the image.
 */
@Composable
fun AmbientScreen(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        AmbientBackground()
        content()
    }
}
