package com.dx.ambient.feature.scenes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.dx.ambient.domain.model.LibraryMedia
import com.dx.ambient.domain.model.MediaSourceType
import com.dx.ambient.domain.model.Scene
import com.dx.ambient.rendering.components.PrimaryButton
import com.dx.ambient.rendering.components.ScreenPadding
import com.dx.ambient.rendering.components.SectionHeader

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

    // Land initial focus on Save (not the name field) so the soft keyboard never auto-pops on
    // entry; the user navigates up to a field and only then taps to type.
    val saveFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { saveFocus.requestFocus() } }

    BackHandler { onDone() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ScreenPadding),
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

            MediaPickerRow(
                label = "Mask",
                media = masks,
                selectedUri = draft.mask.uri.takeIf { draft.mask.enabled },
                onPick = viewModel::pickMask,
                clearLabel = "No mask",
                onClear = viewModel::clearMask,
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

@Composable
private fun MediaChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick) {
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
