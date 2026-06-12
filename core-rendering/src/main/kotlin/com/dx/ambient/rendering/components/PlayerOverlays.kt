package com.dx.ambient.rendering.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.dx.ambient.rendering.R

/**
 * Shared pause overlay for ambient playback (regular scenes and the YouTube player).
 *
 * Projector-friendly by design: never a dead black screen — it paints a full
 * [AmbientBackground] (a randomly picked ambient image, the same backdrop the menu
 * screens use) with the scene identity, the live-switchable mask, and control hints
 * matched to the input mode (remote keys on TV, gestures on touch devices).
 */
@Composable
fun PlayerPauseOverlay(
    sceneName: String?,
    maskName: String?,
    modifier: Modifier = Modifier,
    /**
     * Current scene mask, composited over the backdrop so live ▲ ▼ mask tuning is
     * previewed even while paused.
     */
    maskUri: String? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AmbientBackground()
        if (maskUri != null) {
            AsyncImage(
                model = maskUri,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
        OverlayTextBlock(
            headline = stringResource(R.string.player_paused),
            sceneName = sceneName,
            maskName = maskName,
            showHints = true,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/**
 * Shared loading/buffering overlay: full ambient backdrop with the scene identity and a
 * gently pulsing loading line — visibly alive, never a stuck black screen.
 */
@Composable
fun PlayerLoadingOverlay(
    sceneName: String?,
    modifier: Modifier = Modifier,
    /** Current scene mask, composited over the backdrop (see [PlayerPauseOverlay]). */
    maskUri: String? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AmbientBackground()
        if (maskUri != null) {
            AsyncImage(
                model = maskUri,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val pulse = rememberInfiniteTransition(label = "loading-pulse")
        val pulseAlpha by pulse.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "loading-alpha",
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.align(Alignment.Center).padding(32.dp),
        ) {
            if (!sceneName.isNullOrBlank()) {
                Text(text = sceneName, style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                text = stringResource(R.string.common_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.alpha(pulseAlpha),
            )
        }
    }
}

@Composable
private fun OverlayTextBlock(
    headline: String,
    sceneName: String?,
    maskName: String?,
    showHints: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.padding(32.dp),
    ) {
        Text(text = headline, style = MaterialTheme.typography.headlineMedium)
        if (!sceneName.isNullOrBlank()) {
            Text(
                text = sceneName,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        if (maskName != null) {
            Text(
                text = stringResource(R.string.player_mask_format, maskName),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        if (showHints) {
            Text(
                text = if (isTvDevice()) {
                    stringResource(R.string.player_pause_hint)
                } else {
                    stringResource(R.string.player_pause_hint_touch)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}
