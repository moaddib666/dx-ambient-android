package com.dx.ambient.boot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The launch splash: an intentionally plain black screen shown while the app seeds defaults and
 * decides where to go. Keeping it pure black makes the fade-in to the first scene feel seamless
 * on a projector.
 */
@Composable
fun BootScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black))
}
