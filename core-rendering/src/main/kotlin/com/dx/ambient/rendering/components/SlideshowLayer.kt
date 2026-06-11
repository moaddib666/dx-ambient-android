package com.dx.ambient.rendering.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.dx.ambient.domain.model.LoopMode
import com.dx.ambient.domain.model.Scene
import com.dx.ambient.domain.model.SlideTransition
import com.dx.ambient.domain.model.SlideshowConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

/** Cross-fade length between slides; [SlideTransition.NONE] cuts instead. */
private const val SLIDE_FADE_MS = 1_400

/** Maximum extra zoom applied by the Ken Burns drift (1f → 1.12f). */
private const val KEN_BURNS_ZOOM = 0.12f

/** Fraction of the zoom's safe margin used for panning, so edges never show. */
private const val KEN_BURNS_PAN = 0.6f

/**
 * The picture layer for [com.dx.ambient.domain.model.SceneKind.SLIDESHOW] scenes: shows the
 * scene's images one at a time, advancing on [SlideshowConfig.intervalMs] and honoring the
 * scene's [LoopMode] (loop, play once, shuffle; [LoopMode.LOOP_ONE] holds the first image).
 *
 * Transitions: hard cut, cross-fade, or Ken Burns — a slow linear zoom/pan whose direction
 * alternates per slide, drifting back and forth while a slide stays on screen.
 *
 * [paused] freezes both the advance timer and the Ken Burns drift (player pause).
 */
@Composable
fun SlideshowLayer(
    scene: Scene,
    paused: Boolean,
    modifier: Modifier = Modifier,
) {
    val images = scene.slideshowImages
    if (images.isEmpty()) return
    val intervalMs = scene.slideshow.intervalMs
        .coerceIn(SlideshowConfig.MIN_INTERVAL_MS, SlideshowConfig.MAX_INTERVAL_MS)

    // Which image is showing, plus a monotonically increasing ordinal so every advance
    // (even shuffle revisiting an image) re-keys the transition and the Ken Burns drift.
    var imageIndex by remember(scene.id, images.size) { mutableIntStateOf(0) }
    var ordinal by remember(scene.id, images.size) { mutableIntStateOf(0) }

    LaunchedEffect(scene.id, images.size, intervalMs, scene.loopMode, paused, ordinal) {
        if (paused || images.size < 2) return@LaunchedEffect
        when (scene.loopMode) {
            LoopMode.LOOP_ONE -> return@LaunchedEffect
            LoopMode.PLAY_ONCE -> if (imageIndex == images.lastIndex) return@LaunchedEffect
            else -> Unit
        }
        delay(intervalMs)
        imageIndex = if (scene.loopMode == LoopMode.SHUFFLE_PLAYLIST) {
            images.indices.filter { it != imageIndex }.random()
        } else {
            (imageIndex + 1) % images.size
        }
        ordinal += 1
    }

    Crossfade(
        targetState = imageIndex to ordinal,
        animationSpec = tween(
            durationMillis = if (scene.slideshow.transition == SlideTransition.NONE) 0 else SLIDE_FADE_MS,
        ),
        label = "slideshow",
        modifier = modifier.fillMaxSize(),
    ) { (index, slideOrdinal) ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (scene.slideshow.transition == SlideTransition.KEN_BURNS) {
                KenBurnsImage(
                    uri = images[index].uri,
                    contentDescription = scene.name,
                    ordinal = slideOrdinal,
                    driftMs = intervalMs + SLIDE_FADE_MS,
                    paused = paused,
                )
            } else {
                AsyncImage(
                    model = images[index].uri,
                    contentDescription = scene.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * One slide with the Ken Burns drift: progress runs 0→1 over [driftMs] and then back,
 * repeating while the slide stays on screen (matters for single-image / LOOP_ONE scenes).
 * Zoom direction and pan corner derive from [ordinal] so consecutive slides vary.
 * Translation is tied to the current zoom margin, so the image always covers the screen.
 */
@Composable
private fun KenBurnsImage(
    uri: String,
    contentDescription: String?,
    ordinal: Int,
    driftMs: Long,
    paused: Boolean,
) {
    val progress = remember(ordinal) { Animatable(0f) }
    var towardEnd by remember(ordinal) { mutableStateOf(true) }

    LaunchedEffect(ordinal, paused) {
        if (paused) return@LaunchedEffect
        while (isActive) {
            val target = if (towardEnd) 1f else 0f
            val distance = abs(target - progress.value)
            if (distance > 0.001f) {
                progress.animateTo(
                    targetValue = target,
                    animationSpec = tween(
                        durationMillis = (distance * driftMs).toInt().coerceAtLeast(1),
                        easing = LinearEasing,
                    ),
                )
            }
            towardEnd = !towardEnd
        }
    }

    val zoomIn = ordinal % 2 == 0
    val panX = if (ordinal % 4 < 2) 1f else -1f
    val panY = if (ordinal % 3 == 0) 1f else -1f

    AsyncImage(
        model = uri,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val t = progress.value
                val zoom = if (zoomIn) t else 1f - t
                val scale = 1f + KEN_BURNS_ZOOM * zoom
                scaleX = scale
                scaleY = scale
                // Pan within the cropped margin created by the zoom — never exposes edges.
                val margin = (scale - 1f) / 2f * KEN_BURNS_PAN
                translationX = panX * margin * size.width
                translationY = panY * margin * size.height
            },
    )
}
