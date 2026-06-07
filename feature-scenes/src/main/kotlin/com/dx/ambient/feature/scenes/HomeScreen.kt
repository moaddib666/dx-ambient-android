package com.dx.ambient.feature.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
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
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 24.dp),
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

@Composable
private fun SceneCard(
    scene: Scene,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth(),
            scale = CardDefaults.scale(focusedScale = 1.05f),
        ) {
            Column {
                SceneThumbnail(scene = scene, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = scene.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = loopModeLabel(scene),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(text = "Edit", onClick = onEdit)
            PrimaryButton(text = "Duplicate", onClick = onDuplicate)
            PrimaryButton(text = "Delete", onClick = onDelete)
        }
    }
}

/**
 * Thumbnail for a scene. Images render directly; videos fall back to a colored Box
 * since a still frame isn't always extractable on TV hardware.
 */
@Composable
private fun SceneThumbnail(scene: Scene, modifier: Modifier = Modifier) {
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
