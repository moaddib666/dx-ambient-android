package com.dx.ambient.rendering.components

import android.content.res.Configuration
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * Whether the app is running in a leanback (TV / projector) UI mode, i.e. the primary input is
 * a D-pad remote rather than a touchscreen. Drives input-mode decisions such as initial focus
 * placement and overscan padding.
 */
@Composable
fun isTvDevice(): Boolean {
    val uiMode = LocalConfiguration.current.uiMode
    return (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
}

/**
 * Screen content padding adapted to the device class: generous overscan-safe margins on TV,
 * regular margins on phones/tablets where every pixel is visible and space is scarcer.
 */
@Composable
fun rememberScreenPadding(): PaddingValues =
    if (isTvDevice()) {
        PaddingValues(horizontal = 48.dp, vertical = 32.dp)
    } else {
        PaddingValues(horizontal = 24.dp, vertical = 20.dp)
    }

/**
 * Bridges touch and mouse input onto tv-material clickables.
 *
 * tv-material 1.0.0 surfaces (`Button`, `Card`, `Surface`, `ListItem`) only react to D-pad
 * ENTER and accessibility semantics — they attach no pointer-input handling at all, so taps
 * and mouse clicks are silently ignored. Applying this modifier on (or around) such a
 * component makes tap → [onClick] and long-press → [onLongClick] work on touchscreens and
 * with a mouse, while D-pad behaviour is untouched.
 */
fun Modifier.touchClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
): Modifier = composed {
    val currentEnabled = rememberUpdatedState(enabled)
    val currentOnClick = rememberUpdatedState(onClick)
    val currentOnLongClick = rememberUpdatedState(onLongClick)
    pointerInput(onLongClick != null) {
        detectTapGestures(
            onTap = { if (currentEnabled.value) currentOnClick.value() },
            onLongPress =
                if (onLongClick != null) {
                    { if (currentEnabled.value) currentOnLongClick.value?.invoke() }
                } else {
                    null
                },
        )
    }
}
