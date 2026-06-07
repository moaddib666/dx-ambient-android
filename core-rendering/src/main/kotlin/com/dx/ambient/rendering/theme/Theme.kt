package com.dx.ambient.rendering.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.darkColorScheme

/**
 * App theme built on Compose for TV (`androidx.tv.material3`). TV apps are dark-first to suit
 * projectors and large screens; we keep a single calm, low-glare palette.
 */
private val AmbientDarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF06121F),
    secondary = Color(0xFF7FD1AE),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE3E6EA),
    surfaceVariant = Color(0xFF1C232B),
    background = Color(0xFF0A0D10),
    onBackground = Color(0xFFE3E6EA),
    border = Color(0xFF2A323B),
)

@Composable
fun AmbientTheme(content: @Composable () -> Unit) {
    // The ambient app is intentionally always dark — best for projectors and large screens.
    MaterialTheme(
        colorScheme = AmbientDarkColors,
        typography = AmbientTypography,
    ) {
        // CRITICAL on Compose for TV: MaterialTheme does NOT establish LocalContentColor —
        // it defaults to black, which makes every default-colored Text invisible on a dark
        // background. A tv-material3 Surface is what propagates the container + content color,
        // so the whole app is wrapped in one root Surface here.
        Surface(
            modifier = Modifier.fillMaxSize(),
            colors = SurfaceDefaults.colors(
                containerColor = AmbientDarkColors.background,
                contentColor = AmbientDarkColors.onBackground,
            ),
        ) {
            content()
        }
    }
}
