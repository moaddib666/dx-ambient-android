package com.dx.ambient.feature.scenes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.dx.ambient.domain.model.MediaSourceType
import com.dx.ambient.domain.model.Scene
import com.dx.ambient.rendering.components.AmbientScreen
import com.dx.ambient.rendering.components.EmptyState
import com.dx.ambient.rendering.components.PrimaryButton
import com.dx.ambient.rendering.components.ScreenPadding
import com.dx.ambient.rendering.components.SectionHeader

/** Neon cyan used for the focused-card outline and glow. */
private val NeonCyan = Color(0xFF00E5FF)

/**
 * TV remote-first home screen (MVP feature 1).
 *
 * Shows the saved scenes in a focusable grid with top-level actions (New Scene,
 * Library, Settings). Selecting a card plays the scene; each card also exposes
 * Edit / Duplicate / Delete actions.
 */
@Composable
fun HomeScreen(
    onPlayScene: (String) -> Unit,
    onEditScene: (String) -> Unit,
    onCreateScene: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** Optional entry to the isolated YouTube mode. Null hides it (e.g. devices without Play). */
    onOpenYouTube: (() -> Unit)? = null,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val scenes by viewModel.state.collectAsStateWithLifecycle()

    // Give the first action initial focus so a D-pad/remote has somewhere to land on launch.
    val firstActionFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstActionFocus.requestFocus() } }

    AmbientScreen(modifier = modifier) {
      Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ScreenPadding),
      ) {
        SectionHeader(title = "DX Ambient", subtitle = "Ambient projector scenes")

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PrimaryButton(
                text = "New Scene",
                onClick = onCreateScene,
                modifier = Modifier.focusRequester(firstActionFocus),
            )
            PrimaryButton(text = "Library", onClick = onOpenLibrary)
            PrimaryButton(text = "Settings", onClick = onOpenSettings)
            if (onOpenYouTube != null) {
                PrimaryButton(text = "YouTube", onClick = onOpenYouTube)
            }
        }

        if (scenes.isEmpty()) {
            EmptyState(
                title = "No scenes yet",
                message = "Create your first ambient scene to get started.",
                modifier = Modifier.fillMaxSize(),
                action = { PrimaryButton(text = "New Scene", onClick = onCreateScene) },
            )
        } else {
            LazyVerticalGrid(
                // Compact, adaptive columns so several cards fit per row and whole rows stay
                // visible — instead of a few oversized cards that get cropped.
                columns = GridCells.Adaptive(minSize = 200.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                // Inner padding so a focused (scaled) card has room to grow without being clipped
                // by the grid bounds — especially the top row.
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp, start = 4.dp, end = 4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items = scenes, key = { it.id }) { scene ->
                    SceneCard(
                        scene = scene,
                        onPlay = { onPlayScene(scene.id) },
                        onEdit = { onEditScene(scene.id) },
                        onDuplicate = { viewModel.duplicate(scene.id) },
                        onDelete = { viewModel.delete(scene.id) },
                    )
                }
            }
        }
      }
    }
}

/**
 * A compact, glassy scene card: a 16:9 thumbnail with the scene name overlaid on a gradient.
 * Click plays the scene; long-press (or center-hold on a remote) opens Edit / Duplicate / Delete,
 * which keeps the grid tight instead of bloating every card with a button row.
 */
@Composable
private fun SceneCard(
    scene: Scene,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showActions by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    Card(
        onClick = onPlay,
        onLongClick = { showActions = true },
        modifier = modifier.fillMaxWidth(),
        shape = CardDefaults.shape(shape),
        // Semi-transparent "glass" that brightens on focus.
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), shape = shape),
            // Thin crisp neon-cyan outline on focus.
            focusedBorder = Border(BorderStroke(1.dp, NeonCyan), shape = shape),
        ),
        // Tight cyan glow on focus (low elevation = less blur/spread).
        glow = CardDefaults.glow(
            glow = Glow(elevationColor = Color.Black, elevation = 3.dp),
            focusedGlow = Glow(elevationColor = NeonCyan, elevation = 6.dp),
        ),
        // Gentle scale so the focused card lifts without overflowing/cropping.
        scale = CardDefaults.scale(focusedScale = 1.03f),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            SceneThumbnail(scene = scene, modifier = Modifier.fillMaxSize())
            // Bottom gradient so the title stays legible over any image.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Text(
                    text = scene.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = loopModeLabel(scene),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (showActions) {
        SceneActionsDialog(
            sceneName = scene.name,
            onEdit = { showActions = false; onEdit() },
            onDuplicate = { showActions = false; onDuplicate() },
            onDelete = { showActions = false; onDelete() },
            onDismiss = { showActions = false },
        )
    }
}

/** A small glass action sheet shown on long-press of a scene card. */
@Composable
private fun SceneActionsDialog(
    sceneName: String,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(
                    color = Color(0xFF12161B).copy(alpha = 0.96f),
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = sceneName, style = MaterialTheme.typography.titleMedium)
            PrimaryButton(text = "Edit", onClick = onEdit, modifier = Modifier.fillMaxWidth())
            PrimaryButton(text = "Duplicate", onClick = onDuplicate, modifier = Modifier.fillMaxWidth())
            PrimaryButton(text = "Delete", onClick = onDelete, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Thumbnail for a scene. Images render directly; videos fall back to a colored Box
 * since a still frame isn't always extractable on TV hardware.
 */
@Composable
private fun SceneThumbnail(scene: Scene, modifier: Modifier = Modifier) {
    // Prefer an explicit preview image (e.g. the bundled campfire preview).
    scene.thumbnailUri?.let { thumbnail ->
        AsyncImage(
            model = thumbnail,
            contentDescription = scene.name,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        return
    }
    val source = scene.videoSource
    when (source.type) {
        MediaSourceType.LOCAL_IMAGE -> {
            AsyncImage(
                model = source.uri,
                contentDescription = scene.name,
                contentScale = ContentScale.Crop,
                modifier = modifier,
            )
        }
        MediaSourceType.LOCAL_VIDEO -> {
            Box(
                modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = source.displayName ?: "Video",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
            }
        }
        else -> {
            Box(modifier = modifier.background(Color(0xFF1C232B)))
        }
    }
}

private fun loopModeLabel(scene: Scene): String = when (scene.loopMode.name) {
    "LOOP_ONE" -> "Loop one"
    "PLAY_ONCE" -> "Play once"
    "LOOP_PLAYLIST" -> "Loop playlist"
    "SHUFFLE_PLAYLIST" -> "Shuffle playlist"
    else -> scene.loopMode.name
}
