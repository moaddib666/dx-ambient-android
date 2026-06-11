package com.dx.ambient.youtube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.dx.ambient.rendering.R
import com.dx.ambient.rendering.components.touchClickable

/**
 * Gallery dialog: pick a bundled mask (or none) for a featured playlist.
 * Used from the YouTube tab (long-press a featured card) and from the Home
 * screen's edit mode, where TV remotes have no long-press.
 */
@Composable
fun MaskPickerDialog(
    title: String,
    masks: List<Pair<String, String>>,
    selectedUri: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(
                    color = Color(0xFF12161B).copy(alpha = 0.97f),
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.yt_mask_for, title),
                style = MaterialTheme.typography.titleMedium,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(count = masks.size + 1) { index ->
                    if (index == 0) {
                        MaskOptionTile(
                            label = stringResource(R.string.mask_none),
                            maskUri = null,
                            selected = selectedUri == null,
                            onClick = { onSelect(null) },
                        )
                    } else {
                        val (label, uri) = masks[index - 1]
                        MaskOptionTile(
                            label = label,
                            maskUri = uri,
                            selected = selectedUri == uri,
                            onClick = { onSelect(uri) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MaskOptionTile(
    label: String,
    maskUri: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(150.dp)
            .touchClickable(onClick = onClick),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF24455A), Color(0xFF1B5A4A), Color(0xFF53306B)),
                            ),
                        ),
                )
                if (maskUri != null) {
                    AsyncImage(
                        model = maskUri,
                        contentDescription = label,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                text = if (selected) "✓ $label" else label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
