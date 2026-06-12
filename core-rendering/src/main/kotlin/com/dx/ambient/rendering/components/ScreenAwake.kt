package com.dx.ambient.rendering.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Keeps the display awake while the calling composable is in composition.
 *
 * Ambient playback (video, slideshow, YouTube embed) produces no touch/key activity for
 * hours by design, so without this phones and tablets dim and lock mid-scene. The flag is
 * scoped to the view and cleared on dispose, so menu screens keep the normal screen timeout.
 */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
