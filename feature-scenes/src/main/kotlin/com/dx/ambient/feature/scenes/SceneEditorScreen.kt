package com.dx.ambient.feature.scenes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.dx.ambient.domain.model.LibraryMedia
import com.dx.ambient.domain.model.MediaSourceType
import com.dx.ambient.domain.model.Scene
import com.dx.ambient.rendering.AmbientStage
import com.dx.ambient.rendering.components.PrimaryButton
import com.dx.ambient.rendering.components.SectionHeader
import com.dx.ambient.rendering.components.isTvDevice
import com.dx.ambient.rendering.components.rememberScreenPadding
import com.dx.ambient.rendering.components.touchClickable

/**
 * Scene authoring screen (MVP features 4 + 7).
 *
 * Edits a [Scene] draft: name, picture source, separate audio source, alpha mask,
 * brightness, loop mode and mute. Saving validates and persists via the use case.
 * Since tv-material3 has no TextField, the name uses a [BasicTextField] on a [Surface],
 * and sliders are replaced by +/- steppers and a cycling loop-mode button.
 */
@Composable
fun SceneEditorScreen(
    sceneId: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SceneEditorViewModel = hiltViewModel()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val audios by viewModel.audios.collectAsStateWithLifecycle()
    val masks by viewModel.masks.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(sceneId) {
        viewModel.bind(sceneId)
    }

    // On TV, land initial focus on Save (not the name field) so the soft keyboard never
    // auto-pops on entry; the user navigates up to a field and only then taps to type.
    // On touch devices no focus is requested.
    // Full-screen "mask on video" preview: ◀ ▶ / swipe cycles masks, BACK returns to the form.
    var showMaskPreview by remember { mutableStateOf(false) }

    val tvDevice = isTvDevice()
    val saveFocus = remember { FocusRequester() }
    LaunchedEffect(tvDevice) {
        if (tvDevice) runCatching { saveFocus.requestFocus() }
    }

    // When the mask preview closes, land focus back on Save — not the name field,
    // which would pop the soft keyboard on TV.
    LaunchedEffect(showMaskPreview) {
        if (!showMaskPreview && tvDevice) runCatching { saveFocus.requestFocus() }
    }

    BackHandler {
        if (showMaskPreview) showMaskPreview = false else onDone()
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(rememberScreenPadding()),
    ) {
        // Scrollable form content. Keeping this in a scroll container guarantees every field is
        // reachable on any screen size; the Save/Cancel bar below stays pinned and always visible.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SectionHeader(
                title = if (sceneId.isNullOrBlank()) "New Scene" else "Edit Scene",
            )

            NameField(name = draft.name, onNameChange = viewModel::setName)

            MediaPickerRow(
                label = "Video source",
                media = videos,
                selectedUri = draft.videoSource.uri.takeIf {
                    draft.videoSource.type == MediaSourceType.LOCAL_VIDEO
                },
                onPick = viewModel::pickVideo,
                emptyHint = "Import videos in Library to pick a picture source.",
            )

            MediaPickerRow(
                label = "Audio source",
                media = audios,
                selectedUri = draft.audioSource.uri.takeIf {
                    draft.audioSource.type == MediaSourceType.LOCAL_AUDIO
                },
                onPick = viewModel::pickAudio,
                clearLabel = "Use video's audio",
                onClear = viewModel::clearAudio,
            )

            MaskGalleryRow(
                masks = masks,
                selectedUri = draft.mask.uri.takeIf { draft.mask.enabled },
                onPick = viewModel::pickMask,
                onClear = viewModel::clearMask,
                onPreview = { showMaskPreview = true }.takeIf {
                    draft.videoSource.type == MediaSourceType.LOCAL_VIDEO ||
                        draft.videoSource.type == MediaSourceType.LOCAL_IMAGE
                },
            )

            BrightnessStepper(
                brightness = draft.brightness,
                onDecrease = { viewModel.setBrightness(draft.brightness - 0.05f) },
                onIncrease = { viewModel.setBrightness(draft.brightness + 0.05f) },
            )

            ToggleRow(label = "Loop mode") {
                PrimaryButton(text = loopModeLabel(draft), onClick = viewModel::cycleLoopMode)
            }

            ToggleRow(label = "Muted") {
                PrimaryButton(
                    text = if (draft.muted) "ON" else "OFF",
                    onClick = viewModel::toggleMute,
                )
            }

            error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // Pinned action bar — always on screen so the scene can always be saved.
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            PrimaryButton(
                text = "Save",
                onClick = { viewModel.save { onDone() } },
                modifier = Modifier.focusRequester(saveFocus),
            )
            PrimaryButton(text = "Cancel", onClick = onDone)
        }
    }

    if (showMaskPreview) {
        MaskPreviewOverlay(
            viewModel = viewModel,
            draft = draft,
            onClose = { showMaskPreview = false },
        )
    }
    }
}

/**
 * Full-screen live preview: the draft plays through [AmbientStage] with the current
 * mask composited on top — exactly what the saved scene will look like. D-pad
 * left/right or a horizontal swipe cycles through `[No mask] + gallery`; BACK or a
 * vertical swipe returns to the form keeping the selection.
 */
@Composable
private fun MaskPreviewOverlay(
    viewModel: SceneEditorViewModel,
    draft: Scene,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Play on entry, stop when the overlay leaves composition for any reason.
    DisposableEffect(Unit) {
        viewModel.startMaskPreview()
        onDispose { viewModel.stopMaskPreview() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        viewModel.cycleMask(-1)
                        true
                    }
                    Key.DirectionRight -> {
                        viewModel.cycleMask(1)
                        true
                    }
                    Key.Back -> {
                        onClose()
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                var dragTotal = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = {
                        val threshold = 96.dp.toPx()
                        when {
                            dragTotal <= -threshold -> viewModel.cycleMask(1)
                            dragTotal >= threshold -> viewModel.cycleMask(-1)
                        }
                    },
                ) { _, dragAmount -> dragTotal += dragAmount }
            }
            .pointerInput(Unit) {
                var dragTotal = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = {
                        val threshold = 120.dp.toPx()
                        if (dragTotal <= -threshold || dragTotal >= threshold) onClose()
                    },
                ) { _, dragAmount -> dragTotal += dragAmount }
            },
    ) {
        AmbientStage(
            player = viewModel.player,
            scene = draft,
            modifier = Modifier.fillMaxSize(),
        )

        // Current mask name + controls hint.
        Column(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomStart)
                .padding(32.dp),
        ) {
            Text(
                text = draft.mask.displayName ?: if (draft.mask.enabled) "Mask" else "No mask",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "◀ ▶ or swipe to change mask  •  BACK to finish",
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun NameField(name: String, onNameChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Name", style = MaterialTheme.typography.titleMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.border,
                    shape = RoundedCornerShape(8.dp),
                ),
        ) {
            BasicTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun MediaPickerRow(
    label: String,
    media: List<LibraryMedia>,
    selectedUri: String?,
    onPick: (LibraryMedia) -> Unit,
    clearLabel: String? = null,
    onClear: (() -> Unit)? = null,
    emptyHint: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        if (media.isEmpty() && onClear == null && emptyHint != null) {
            Text(
                text = emptyHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (clearLabel != null && onClear != null) {
                    item {
                        MediaChip(
                            text = clearLabel,
                            selected = selectedUri == null,
                            onClick = onClear,
                        )
                    }
                }
                items(items = media, key = { it.uri }) { item ->
                    MediaChip(
                        text = item.displayName,
                        selected = selectedUri == item.uri,
                        onClick = { onPick(item) },
                    )
                }
            }
        }
    }
}

/**
 * Visual mask gallery (replaces the old text chips): each mask renders as a real
 * 16:9 thumbnail over a dark backdrop so its alpha shape is visible. The first
 * tile is "No mask"; "Preview on video" opens the full-screen live preview.
 */
@Composable
private fun MaskGalleryRow(
    masks: List<LibraryMedia>,
    selectedUri: String?,
    onPick: (LibraryMedia) -> Unit,
    onClear: () -> Unit,
    onPreview: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(text = "Mask", style = MaterialTheme.typography.titleMedium)
            if (onPreview != null) {
                PrimaryButton(text = "Preview on video", onClick = onPreview)
            } else {
                Text(
                    text = "Pick a video first to preview masks live",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                MaskTile(
                    label = "No mask",
                    maskUri = null,
                    selected = selectedUri == null,
                    onClick = onClear,
                )
            }
            items(items = masks, key = { it.uri }) { mask ->
                MaskTile(
                    label = mask.displayName,
                    maskUri = mask.uri,
                    selected = selectedUri == mask.uri,
                    onClick = { onPick(mask) },
                )
            }
        }
    }
}

@Composable
private fun MaskTile(
    label: String,
    maskUri: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(168.dp)
            .touchClickable(onClick = onClick),
        shape = androidx.tv.material3.CardDefaults.shape(shape),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            // Backdrop stands in for the video so the mask's translucent shape reads clearly.
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.8f),
                        ),
                    ),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(width = 2.dp, color = Color(0xFF00E5FF), shape = shape),
                )
            }
            Text(
                text = if (selected) "✓ $label" else label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) Color(0xFF00E5FF) else Color.White,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomStart)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun MediaChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.touchClickable(onClick = onClick)) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = if (selected) "✓ $text" else text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun BrightnessStepper(
    brightness: Float,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    ToggleRow(label = "Brightness") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(text = "-", onClick = onDecrease, enabled = brightness > 0f)
            Text(
                text = "${(brightness * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
            )
            PrimaryButton(text = "+", onClick = onIncrease, enabled = brightness < 1f)
        }
    }
}

@Composable
private fun ToggleRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 16.dp),
        )
        content()
    }
}

private fun loopModeLabel(scene: Scene): String = when (scene.loopMode.name) {
    "LOOP_ONE" -> "Loop one"
    "PLAY_ONCE" -> "Play once"
    "LOOP_PLAYLIST" -> "Loop playlist"
    "SHUFFLE_PLAYLIST" -> "Shuffle playlist"
    else -> scene.loopMode.name
}
