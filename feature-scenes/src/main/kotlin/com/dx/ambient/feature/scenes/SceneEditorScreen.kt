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
import androidx.compose.ui.res.stringResource
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
import com.dx.ambient.domain.model.SceneKind
import com.dx.ambient.domain.model.SlideTransition
import com.dx.ambient.domain.model.SlideshowConfig
import com.dx.ambient.rendering.AmbientStage
import com.dx.ambient.rendering.R
import com.dx.ambient.rendering.components.KeepScreenOn
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
    val images by viewModel.images.collectAsStateWithLifecycle()
    val masks by viewModel.masks.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val youTubeAvailable by viewModel.youTubeAvailable.collectAsStateWithLifecycle()
    val myPlaylists by viewModel.myPlaylists.collectAsStateWithLifecycle()

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
                title = if (sceneId.isNullOrBlank()) {
                    stringResource(R.string.editor_title_new)
                } else {
                    stringResource(R.string.editor_title_edit)
                },
            )

            NameField(name = draft.name, onNameChange = viewModel::setName)

            // The scene type decides which source form is shown below; the sections after
            // the form (audio, mask, appearance) are shared by every type.
            SceneTypeRow(selected = draft.resolvedKind, onSelect = viewModel::setKind)

            when (draft.resolvedKind) {
                SceneKind.VIDEO -> {
                    MediaPickerRow(
                        label = stringResource(R.string.editor_video_source),
                        media = videos,
                        selectedUri = draft.videoSource.uri.takeIf {
                            draft.videoSource.type == MediaSourceType.LOCAL_VIDEO
                        },
                        onPick = viewModel::pickVideo,
                        emptyHint = stringResource(R.string.editor_video_empty_hint),
                    )
                    LinkInputRow(
                        label = stringResource(R.string.editor_video_stream_link),
                        hint = stringResource(R.string.editor_video_stream_hint),
                        invalidMessage = stringResource(R.string.editor_link_invalid_stream),
                        selectedName = draft.videoSource.displayName.takeIf {
                            draft.videoSource.type == MediaSourceType.STREAM
                        },
                        onApply = viewModel::setVideoStreamLink,
                    )
                }

                SceneKind.YOUTUBE -> {
                    LinkInputRow(
                        label = stringResource(R.string.editor_youtube_link),
                        hint = stringResource(R.string.editor_youtube_link_hint),
                        invalidMessage = stringResource(R.string.editor_link_invalid_youtube),
                        selectedName = (draft.videoSource.displayName ?: draft.videoSource.uri)
                            .takeIf { draft.videoSource.isYouTube },
                        onApply = viewModel::setYouTubeLink,
                    )
                    if (youTubeAvailable && myPlaylists.isNotEmpty()) {
                        PlaylistChipsRow(
                            label = stringResource(R.string.editor_my_playlists),
                            playlists = myPlaylists,
                            selectedUri = draft.videoSource.uri.takeIf { draft.videoSource.isYouTube },
                            onPick = viewModel::pickYouTubePlaylist,
                        )
                    }
                    if (viewModel.builtInPlaylists.isNotEmpty()) {
                        PlaylistChipsRow(
                            label = stringResource(R.string.editor_builtin_playlists),
                            playlists = viewModel.builtInPlaylists,
                            selectedUri = draft.videoSource.uri.takeIf { draft.videoSource.isYouTube },
                            onPick = viewModel::pickYouTubePlaylist,
                        )
                    }
                }

                SceneKind.SLIDESHOW -> {
                    ImageGalleryRow(
                        images = images,
                        selected = draft.slideshowImages.map { it.uri },
                        onToggle = viewModel::toggleSlideshowImage,
                    )
                    SecondsStepper(
                        label = stringResource(R.string.editor_slide_interval),
                        valueMs = draft.slideshow.intervalMs,
                        onChange = viewModel::setSlideIntervalMs,
                    )
                    ToggleRow(label = stringResource(R.string.editor_slide_transition)) {
                        PrimaryButton(
                            text = transitionLabel(draft.slideshow.transition),
                            onClick = viewModel::cycleSlideTransition,
                        )
                    }
                }
            }

            MediaPickerRow(
                label = stringResource(R.string.editor_audio_source),
                media = audios,
                selectedUri = draft.audioSource.uri.takeIf {
                    draft.audioSource.type == MediaSourceType.LOCAL_AUDIO
                },
                onPick = viewModel::pickAudio,
                clearLabel = stringResource(R.string.editor_use_video_audio),
                onClear = viewModel::clearAudio,
            )

            // Separate soundtrack from a remote stream (e.g. internet radio).
            LinkInputRow(
                label = stringResource(R.string.editor_audio_stream),
                hint = null,
                invalidMessage = stringResource(R.string.editor_link_invalid_stream),
                selectedName = draft.audioSource.displayName.takeIf {
                    draft.audioSource.type == MediaSourceType.STREAM
                },
                onApply = viewModel::setAudioStream,
            )

            MaskGalleryRow(
                masks = masks,
                selectedUri = draft.mask.uri.takeIf { draft.mask.enabled },
                onPick = viewModel::pickMask,
                onClear = viewModel::clearMask,
                onPreview = { showMaskPreview = true }.takeIf {
                    draft.videoSource.type == MediaSourceType.LOCAL_VIDEO ||
                        draft.slideshowImages.isNotEmpty()
                },
            )

            BrightnessStepper(
                brightness = draft.brightness,
                onDecrease = { viewModel.setBrightness(draft.brightness - 0.05f) },
                onIncrease = { viewModel.setBrightness(draft.brightness + 0.05f) },
            )

            // Video-only appearance, independent of the whole-stage brightness above.
            PercentStepper(
                label = stringResource(R.string.editor_video_opacity),
                value = draft.videoAlpha,
                range = 0f..1f,
                onChange = viewModel::setVideoAlpha,
            )
            PercentStepper(
                label = stringResource(R.string.editor_video_scale),
                value = draft.videoScale,
                range = 0.5f..2f,
                onChange = viewModel::setVideoScale,
            )

            // Loop mode applies to ExoPlayer playlists and slideshows; the YouTube IFrame
            // player loops its own playlist, so the control is hidden there.
            if (draft.resolvedKind != SceneKind.YOUTUBE) {
                ToggleRow(label = stringResource(R.string.editor_loop_mode)) {
                    PrimaryButton(text = loopModeLabel(draft), onClick = viewModel::cycleLoopMode)
                }
            }

            ToggleRow(label = stringResource(R.string.editor_muted)) {
                PrimaryButton(
                    text = if (draft.muted) {
                        stringResource(R.string.common_on)
                    } else {
                        stringResource(R.string.common_off)
                    },
                    onClick = viewModel::toggleMute,
                )
            }

            error?.let { saveError ->
                Text(
                    text = when (saveError) {
                        SceneEditorViewModel.SaveError.BLANK_NAME ->
                            stringResource(R.string.editor_error_blank_name)
                        SceneEditorViewModel.SaveError.SAVE_FAILED ->
                            stringResource(R.string.editor_error_save_failed)
                    },
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
                text = stringResource(R.string.common_save),
                onClick = { viewModel.save { onDone() } },
                modifier = Modifier.focusRequester(saveFocus),
            )
            PrimaryButton(text = stringResource(R.string.common_cancel), onClick = onDone)
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

    // The live preview plays media — don't let the device dim/lock while it's open.
    KeepScreenOn()

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
                text = draft.mask.displayName
                    ?: if (draft.mask.enabled) {
                        stringResource(R.string.mask_fallback_name)
                    } else {
                        stringResource(R.string.mask_none)
                    },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.editor_preview_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun NameField(name: String, onNameChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.editor_name), style = MaterialTheme.typography.titleMedium)
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
            Text(text = stringResource(R.string.editor_mask), style = MaterialTheme.typography.titleMedium)
            if (onPreview != null) {
                PrimaryButton(text = stringResource(R.string.editor_preview_on_video), onClick = onPreview)
            } else {
                Text(
                    text = stringResource(R.string.editor_preview_needs_video),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                MaskTile(
                    label = stringResource(R.string.mask_none),
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

/** The three scene types as a chips row; selecting one swaps the source form. */
@Composable
private fun SceneTypeRow(selected: SceneKind, onSelect: (SceneKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.editor_scene_type),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SceneKind.entries.forEach { kind ->
                MediaChip(
                    text = when (kind) {
                        SceneKind.VIDEO -> stringResource(R.string.scene_type_video)
                        SceneKind.YOUTUBE -> stringResource(R.string.youtube_label)
                        SceneKind.SLIDESHOW -> stringResource(R.string.scene_type_slideshow)
                    },
                    selected = selected == kind,
                    onClick = { onSelect(kind) },
                )
            }
        }
    }
}

/**
 * Multi-select image gallery for slideshows: every imported image renders as a 16:9
 * thumbnail; selecting toggles membership and shows the slide's position in the show.
 */
@Composable
private fun ImageGalleryRow(
    images: List<LibraryMedia>,
    selected: List<String>,
    onToggle: (LibraryMedia) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.editor_images),
            style = MaterialTheme.typography.titleMedium,
        )
        if (images.isEmpty()) {
            Text(
                text = stringResource(R.string.editor_images_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items = images, key = { it.uri }) { image ->
                    ImageTile(
                        image = image,
                        selectionIndex = selected.indexOf(image.uri).takeIf { it >= 0 },
                        onClick = { onToggle(image) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageTile(
    image: LibraryMedia,
    /** Zero-based position in the slideshow, or null when the image isn't selected. */
    selectionIndex: Int?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val selected = selectionIndex != null
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(168.dp)
            .touchClickable(onClick = onClick),
        shape = androidx.tv.material3.CardDefaults.shape(shape),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AsyncImage(
                model = image.uri,
                contentDescription = image.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
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
                text = if (selectionIndex != null) {
                    "${selectionIndex + 1} · ${image.displayName}"
                } else {
                    image.displayName
                },
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

/** Stepper over a millisecond value displayed as whole seconds (slide duration). */
@Composable
private fun SecondsStepper(
    label: String,
    valueMs: Long,
    onChange: (Long) -> Unit,
) {
    ToggleRow(label = label) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            PrimaryButton(
                text = "-",
                onClick = { onChange(valueMs - 1_000L) },
                enabled = valueMs > SlideshowConfig.MIN_INTERVAL_MS,
            )
            Text(
                text = stringResource(R.string.seconds_format, (valueMs / 1_000L).toInt()),
                style = MaterialTheme.typography.titleMedium,
            )
            PrimaryButton(
                text = "+",
                onClick = { onChange(valueMs + 1_000L) },
                enabled = valueMs < SlideshowConfig.MAX_INTERVAL_MS,
            )
        }
    }
}

@Composable
private fun transitionLabel(transition: SlideTransition): String = when (transition) {
    SlideTransition.NONE -> stringResource(R.string.transition_none)
    SlideTransition.CROSSFADE -> stringResource(R.string.transition_crossfade)
    SlideTransition.KEN_BURNS -> stringResource(R.string.transition_ken_burns)
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

/**
 * Single-line URL entry with an apply button — used for YouTube/stream video links and
 * audio stream URLs. [onApply] returns false for unusable input, which shows
 * [invalidMessage] inline; on success the field clears and [selectedName] echoes the
 * chosen source.
 */
@Composable
private fun LinkInputRow(
    label: String,
    hint: String?,
    invalidMessage: String,
    selectedName: String?,
    onApply: (String) -> Boolean,
) {
    var text by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            if (selectedName != null) {
                Text(
                    text = "✓ $selectedName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.border,
                        shape = RoundedCornerShape(8.dp),
                    ),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        invalid = false
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            PrimaryButton(
                text = stringResource(R.string.editor_link_apply),
                onClick = {
                    if (text.isBlank()) return@PrimaryButton
                    if (onApply(text)) {
                        text = ""
                        invalid = false
                    } else {
                        invalid = true
                    }
                },
            )
        }
        when {
            invalid -> Text(
                text = invalidMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            hint != null -> Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

/** Chips row of remote playlists (selected = the draft already points at that playlist). */
@Composable
private fun PlaylistChipsRow(
    label: String,
    playlists: List<com.dx.ambient.domain.catalog.CatalogPlaylist>,
    selectedUri: String?,
    onPick: (com.dx.ambient.domain.catalog.CatalogPlaylist) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items = playlists, key = { it.id }) { playlist ->
                MediaChip(
                    text = playlist.title,
                    selected = selectedUri?.contains(playlist.id) == true,
                    onClick = { onPick(playlist) },
                )
            }
        }
    }
}

/** Generic percent stepper in 5% increments, shared by opacity and scale. */
@Composable
private fun PercentStepper(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    ToggleRow(label = label) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            PrimaryButton(
                text = "-",
                onClick = { onChange(value - 0.05f) },
                enabled = value > range.start,
            )
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
            )
            PrimaryButton(
                text = "+",
                onClick = { onChange(value + 0.05f) },
                enabled = value < range.endInclusive,
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
    ToggleRow(label = stringResource(R.string.editor_brightness)) {
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

@Composable
private fun loopModeLabel(scene: Scene): String = when (scene.loopMode.name) {
    "LOOP_ONE" -> stringResource(R.string.loop_mode_loop_one)
    "PLAY_ONCE" -> stringResource(R.string.loop_mode_play_once)
    "LOOP_PLAYLIST" -> stringResource(R.string.loop_mode_loop_playlist)
    "SHUFFLE_PLAYLIST" -> stringResource(R.string.loop_mode_shuffle_playlist)
    else -> scene.loopMode.name
}
