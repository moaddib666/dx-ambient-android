package com.dx.ambient.rendering.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Standard TV overscan-safe content padding. */
val ScreenPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp)

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier = modifier.padding(bottom = 16.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * Glass / frosted button colors that sit over the ambient background image: a translucent
 * white pill that brightens to near-solid on focus, so the D-pad target reads clearly across a
 * room while keeping the see-through, immersive feel.
 */
@Composable
private fun ambientButtonColors() = ButtonDefaults.colors(
    containerColor = Color.White.copy(alpha = 0.12f),
    contentColor = Color.White,
    focusedContainerColor = Color.White.copy(alpha = 0.92f),
    focusedContentColor = Color(0xFF0A0D10),
    pressedContainerColor = Color.White.copy(alpha = 0.92f),
    pressedContentColor = Color(0xFF0A0D10),
)

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ambientButtonColors(),
        scale = ButtonDefaults.scale(focusedScale = 1.1f),
    ) {
        Text(text)
    }
}

/**
 * A native circular icon button (e.g. a "+" action). Built on the TV [Button] so it keeps the
 * platform focus scale/glow, just with a circular shape and an icon instead of a text label.
 */
@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Add,
    contentDescription: String? = null,
    diameter: Dp = 40.dp,
) {
    Button(
        onClick = onClick,
        // Same height as the text pills so the row aligns, same glass colors + focus scale.
        modifier = modifier.size(diameter),
        colors = ambientButtonColors(),
        scale = ButtonDefaults.scale(focusedScale = 1.1f),
        shape = ButtonDefaults.shape(shape = CircleShape),
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun IconTextButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ambientButtonColors(),
        scale = ButtonDefaults.scale(focusedScale = 1.1f),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(text)
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            action?.invoke()
        }
    }
}
