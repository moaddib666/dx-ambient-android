package com.dx.ambient.feature.scenes

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dx.ambient.domain.model.LibraryMedia
import com.dx.ambient.domain.model.LoopMode
import com.dx.ambient.domain.model.Mask
import com.dx.ambient.domain.model.MediaKind
import com.dx.ambient.domain.model.MediaSource
import com.dx.ambient.domain.model.Scene
import com.dx.ambient.domain.repository.MediaLibraryRepository
import com.dx.ambient.domain.repository.SceneRepository
import com.dx.ambient.domain.usecase.SaveSceneUseCase
import com.dx.ambient.playback.AmbientPlayer
import com.dx.ambient.rendering.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Edits a scene draft (MVP feature 4) and persists it (MVP feature 7).
 *
 * Exposes the available library media (videos / audios / images) for picking and a
 * mutable [Scene] draft. The draft is the single source of truth for the editor UI;
 * each mutator emits a new immutable copy.
 */
@HiltViewModel
class SceneEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sceneRepository: SceneRepository,
    mediaLibraryRepository: MediaLibraryRepository,
    private val saveScene: SaveSceneUseCase,
    /** Drives the live mask preview: the draft plays full-screen with the mask overlaid. */
    val player: AmbientPlayer,
) : ViewModel() {

    /** Why the last save attempt failed; the UI maps each case to a localized string. */
    enum class SaveError { BLANK_NAME, SAVE_FAILED }

    /** Pickable local videos for the picture source. */
    val videos: StateFlow<List<LibraryMedia>> =
        mediaLibraryRepository.observeMedia(MediaKind.VIDEO).asState()

    /** Pickable local audios for the separate soundtrack. */
    val audios: StateFlow<List<LibraryMedia>> =
        mediaLibraryRepository.observeMedia(MediaKind.AUDIO).asState()

    /** Pickable local images (imported from the library). */
    val images: StateFlow<List<LibraryMedia>> =
        mediaLibraryRepository.observeMedia(MediaKind.IMAGE).asState()

    /** Masks the app ships with — drop PNG/WebP alpha masks in `assets/masks/` to add more. */
    private val defaultMasks: List<LibraryMedia> = loadDefaultMasks()

    /**
     * Pickable masks (MVP feature 5): the bundled default masks first, then any images the user
     * imported into their library, so either can be used as an alpha overlay.
     */
    val masks: StateFlow<List<LibraryMedia>> =
        images.map { imported -> defaultMasks + imported }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = defaultMasks,
            )

    private val _draft = MutableStateFlow(Scene(id = "", name = defaultSceneName()))

    /** The scene currently being authored. */
    val draft: StateFlow<Scene> = _draft.asStateFlow()

    private val _error = MutableStateFlow<SaveError?>(null)

    /** Non-null when the last save attempt failed validation; shown to the user. */
    val error: StateFlow<SaveError?> = _error.asStateFlow()

    /** Load an existing scene by id, or start a fresh draft for null/blank. */
    fun bind(sceneId: String?) {
        if (sceneId.isNullOrBlank()) {
            _draft.value = Scene(id = "", name = defaultSceneName())
            return
        }
        viewModelScope.launch {
            _draft.value = sceneRepository.getScene(sceneId)
                ?: Scene(id = "", name = defaultSceneName())
        }
    }

    private fun defaultSceneName(): String = context.getString(R.string.editor_default_scene_name)

    fun setName(name: String) {
        _draft.value = _draft.value.copy(name = name)
    }

    /** Pick the picture source (MVP feature 4). */
    fun pickVideo(media: LibraryMedia) {
        _draft.value = _draft.value.copy(videoSource = media.toMediaSource())
    }

    /** Pick a separate audio source. */
    fun pickAudio(media: LibraryMedia) {
        _draft.value = _draft.value.copy(audioSource = media.toMediaSource())
    }

    /** Clear the separate audio so the video's own audio track is used. */
    fun clearAudio() {
        _draft.value = _draft.value.copy(audioSource = MediaSource.NONE)
    }

    /** Pick a PNG alpha mask overlay (MVP feature 5). */
    fun pickMask(media: LibraryMedia) {
        _draft.value = _draft.value.copy(
            mask = Mask(uri = media.uri, displayName = media.displayName),
        )
    }

    /** Remove the mask, keeping the performance-safe fast render path (MVP feature 8). */
    fun clearMask() {
        _draft.value = _draft.value.copy(mask = Mask.NONE)
    }

    /**
     * Steps the draft mask forward/backward through `[No mask] + masks`, wrapping at
     * both ends — D-pad left/right and horizontal swipes in the live preview use this.
     */
    fun cycleMask(direction: Int) {
        val options: List<LibraryMedia?> = listOf<LibraryMedia?>(null) + masks.value
        if (options.size < 2) return
        val currentUri = _draft.value.mask.uri.takeIf { it.isNotEmpty() }
        val index = options.indexOfFirst { it?.uri == currentUri }.coerceAtLeast(0)
        val next = options[(index + direction + options.size) % options.size]
        _draft.value = _draft.value.copy(
            mask = next?.let { Mask(uri = it.uri, displayName = it.displayName) } ?: Mask.NONE,
        )
    }

    /** Starts draft playback for the full-screen mask preview. */
    fun startMaskPreview() {
        player.load(_draft.value)
    }

    /** Stops preview playback when the overlay closes. */
    fun stopMaskPreview() {
        player.stop()
    }

    /** Set output brightness, clamped to a valid 0f..1f multiplier. */
    fun setBrightness(value: Float) {
        _draft.value = _draft.value.copy(brightness = value.coerceIn(0f, 1f))
    }

    /** Cycle to the next [LoopMode]. */
    fun cycleLoopMode() {
        val values = LoopMode.entries
        val next = values[(values.indexOf(_draft.value.loopMode) + 1) % values.size]
        _draft.value = _draft.value.copy(loopMode = next)
    }

    /** Toggle audio mute regardless of the chosen source. */
    fun toggleMute() {
        _draft.value = _draft.value.copy(muted = !_draft.value.muted)
    }

    /** Validate and persist the draft (MVP feature 7); invokes [onSaved] on success. */
    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            saveScene(_draft.value)
                .onSuccess {
                    _error.value = null
                    onSaved()
                }
                .onFailure {
                    _error.value = if (it is IllegalArgumentException) {
                        SaveError.BLANK_NAME
                    } else {
                        SaveError.SAVE_FAILED
                    }
                }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun kotlinx.coroutines.flow.Flow<List<LibraryMedia>>.asState(): StateFlow<List<LibraryMedia>> =
        stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Reads the bundled mask images from the `masks/` assets folder. To add masks, drop PNG or
     * WebP files (alpha channel = the overlay) into `core-rendering/src/main/assets/masks/`.
     */
    private fun loadDefaultMasks(): List<LibraryMedia> = runCatching {
        context.assets.list("masks").orEmpty()
            .filter { it.endsWith(".webp", true) || it.endsWith(".png", true) }
            .sorted()
            .map { name ->
                LibraryMedia(
                    uri = "file:///android_asset/masks/$name",
                    displayName = name.substringBeforeLast('.')
                        .replace('_', ' ')
                        .replaceFirstChar { it.uppercase() },
                    mimeType = "image/*",
                    kind = MediaKind.IMAGE,
                    sourceTreeUri = "bundled:masks",
                )
            }
    }.getOrDefault(emptyList())
}
