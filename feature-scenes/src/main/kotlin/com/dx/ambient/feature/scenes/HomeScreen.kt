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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.alpha
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
import com.dx.ambient.rendering.components.CircleIconButton
import com.dx.ambient.rendering.components.EmptyState
import com.dx.ambient.rendering.components.PrimaryButton
import com.dx.ambient.rendering.components.isTvDevice
import com.dx.ambient.rendering.components.rememberScreenPadding
import com.dx.ambient.rendering.components.touchClickable

/** Neon cyan used for the focused-card outline and glow. */
private val NeonCyan = Color(0xFF00E5FF)

/**
 * A curated (YouTube) playlist tile pinned on the home screen. Pure UI model so
 * this module stays decoupled from the optional YouTube module — the app layer
 * maps and injects these.
 */
data class FeaturedTile(
    val id: String,
    val title: String,
    val enabled: Boolean,
    /** Why the tile is disabled (offline / sign-in required); shown when [enabled] is false. */
    val statusHint: String? = null,
    val thumbnailUrl: String? = null,
)

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
    /** Curated playlists merged into the scene row; empty hides them. */
    featuredTiles: List<FeaturedTile> = emptyList(),
    onPlayFeatured: ((String) -> Unit)? = null,
    /** Edit-mode action for a featured tile (e.g. pick its mask). */
    onEditFeatured: ((String) -> Unit)? = null,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val scenes by viewModel.state.collectAsStateWithLifecycle()
    val showOnboarding by viewModel.showOnboarding.collectAsStateWithLifecycle()

    // TV remotes can't long-press reliably, so editing is an explicit mode:
    // while ON, selecting a card edits it instead of playing it.
    var editMode by remember { mutableStateOf(false) }

    // On TV, give the first action initial focus so a D-pad/remote has somewhere to land on
    // launch. On touch devices no focus is requested — the user just taps.
    val tvDevice = isTvDevice()
    val firstActionFocus = remember { FocusRequester() }
    LaunchedEffect(tvDevice) {
        if (tvDevice) runCatching { firstActionFocus.requestFocus() }
    }

    AmbientScreen(modifier = modifier) {
      Box(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(rememberScreenPadding()),
      ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // New scene: a native circular "+" button.
            CircleIconButton(
                onClick = onCreateScene,
                contentDescription = "New scene",
                modifier = Modifier.focusRequester(firstActionFocus),
            )
            PrimaryButton(text = "Library", onClick = onOpenLibrary)
            PrimaryButton(text = "Settings", onClick = onOpenSettings)
            if (onOpenYouTube != null) {
                PrimaryButton(text = "YouTube", onClick = onOpenYouTube)
            }
            PrimaryButton(
                text = if (editMode) "✎ Editing…" else "Edit",
                onClick = { editMode = !editMode },
            )
        }

        // One combined row: local scenes first, then featured playlists,
        // unavailable featured entries aligned at the end.
        val sortedFeatured = featuredTiles.sortedBy { !it.enabled }
        val disabledHint = featuredTiles.firstOrNull { !it.enabled }?.statusHint

        Text(
            text = when {
                editMode -> "Edit mode: select a card to edit it • press Edit again to finish"
                disabledHint != null -> "⚠ $disabledHint"
                else -> " "
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (editMode) NeonCyan else Color(0xFFFFB74D),
            modifier = Modifier.padding(top = 12.dp),
        )

        if (scenes.isEmpty() && sortedFeatured.isEmpty()) {
            EmptyState(
                title = "No scenes yet",
                message = "Create your first ambient scene to get started.",
                modifier = Modifier.fillMaxSize(),
                action = { PrimaryButton(text = "New Scene", onClick = onCreateScene) },
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                // Inner padding so a focused (scaled) card has room to grow without
                // being clipped by the row bounds.
                contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp, start = 4.dp, end = 4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(items = scenes, key = { it.id }) { scene ->
                    SceneCard(
                        scene = scene,
                        editMode = editMode,
                        onPlay = { onPlayScene(scene.id) },
                        onEdit = { onEditScene(scene.id) },
                        onDuplicate = { viewModel.duplicate(scene.id) },
                        onDelete = { viewModel.delete(scene.id) },
                        modifier = Modifier.width(220.dp),
                    )
                }
                if (onPlayFeatured != null) {
                    items(items = sortedFeatured, key = { it.id }) { tile ->
                        FeaturedTileCard(
                            tile = tile,
                            editMode = editMode,
                            onPlay = onPlayFeatured,
                            onEdit = onEditFeatured,
                        )
                    }
                }
            }
        }
      }

      if (showOnboarding) {
          OnboardingOverlay(onDismiss = viewModel::completeOnboarding)
      }
      }
    }
}

/**
 * One-time first-launch guide. Shown until dismissed, then never again
 * (persisted in settings). One screen, remote-first: OK lands on "Got it".
 */
@Composable
private fun OnboardingOverlay(onDismiss: () -> Unit) {
    val okFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { okFocus.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = Color(0xFF12161B).copy(alpha = 0.97f),
                    shape = RoundedCornerShape(20.dp),
                )
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = "Welcome to DX Ambient", style = MaterialTheme.typography.headlineSmall)
            GuideLine("▶", "Select a scene card with OK to play it — your room becomes the scene.")
            GuideLine("✎", "Press Edit in the top bar, then select a card to edit it (on touch screens you can also long-press).")
            GuideLine("◀ ▶", "While playing: left/right switches scenes, OK pauses, BACK or swipe down returns here.")
            GuideLine("★", "Featured playlists stream via YouTube — they need internet and a YouTube sign-in.")
            GuideLine("◐", "Masks frame your video: pick one in the scene editor and preview it live.")
            PrimaryButton(
                text = "Got it",
                onClick = onDismiss,
                modifier = Modifier.focusRequester(okFocus).padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun GuideLine(symbol: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleMedium,
            color = NeonCyan,
            modifier = Modifier.width(48.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
        )
    }
}

@Composable
private fun FeaturedTileCard(
    tile: FeaturedTile,
    editMode: Boolean,
    onPlay: (String) -> Unit,
    onEdit: ((String) -> Unit)?,
) {
    val shape = RoundedCornerShape(14.dp)
    // Edit mode works regardless of availability (a mask can be picked offline);
    // playing requires the tile to be enabled.
    val activate = {
        when {
            editMode && onEdit != null -> onEdit(tile.id)
            tile.enabled -> onPlay(tile.id)
            else -> Unit
        }
    }
    Card(
        onClick = activate,
        modifier = Modifier
            .width(220.dp)
            .touchClickable(onClick = activate)
            .alpha(if (tile.enabled || editMode) 1f else 0.45f),
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), shape = shape),
            focusedBorder = Border(BorderStroke(1.dp, NeonCyan), shape = shape),
        ),
        scale = CardDefaults.scale(focusedScale = 1.03f),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            if (tile.thumbnailUrl != null) {
                AsyncImage(
                    model = tile.thumbnailUrl,
                    contentDescription = tile.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF0E2A3C), Color(0xFF123B33))),
                        ),
                )
            }
            if (!tile.enabled && !editMode) {
                Text(
                    text = "⚠",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (editMode) {
                Text(
                    text = "✎",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NeonCyan,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
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
            Text(
                text = tile.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
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
    editMode: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showActions by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    // In edit mode (the TV-friendly path — remotes can't long-press) selecting the
    // card opens the actions sheet instead of playing. Long-press still works on touch.
    val activate = { if (editMode) showActions = true else onPlay() }

    Card(
        onClick = activate,
        onLongClick = { showActions = true },
        // Touch/mouse bridge: tv-material Card only reacts to D-pad ENTER, so taps and
        // long-presses are wired up explicitly for phones/tablets.
        modifier = modifier
            .fillMaxWidth()
            .touchClickable(onClick = activate, onLongClick = { showActions = true }),
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
            if (editMode) {
                Text(
                    text = "✎",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NeonCyan,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
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
