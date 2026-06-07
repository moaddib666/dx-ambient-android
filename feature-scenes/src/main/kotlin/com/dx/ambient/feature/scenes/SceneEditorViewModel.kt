package com.dx.ambient.feature.scenes

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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val sceneRepository: SceneRepository,
    mediaLibraryRepository: MediaLibraryRepository,
    private val saveScene: SaveSceneUseCase,
) : ViewModel() {

    /** Pickable local videos for the picture source. */
    val videos: StateFlow<List<LibraryMedia>> =
        mediaLibraryRepository.observeMedia(MediaKind.VIDEO).asState()

    /** Pickable local audios for the separate soundtrack. */
    val audios: StateFlow<List<LibraryMedia>> =
        mediaLibraryRepository.observeMedia(MediaKind.AUDIO).asState()

    /** Pickable local images for the alpha mask (MVP feature 5). */
    val images: StateFlow<List<LibraryMedia>> =
        mediaLibraryRepository.observeMedia(MediaKind.IMAGE).asState()

    private val _draft = MutableStateFlow(Scene(id = "", name = "New scene"))

    /** The scene currently being authored. */
    val draft: StateFlow<Scene> = _draft.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)

    /** Non-null when the last save attempt failed validation; shown to the user. */
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Load an existing scene by id, or start a fresh draft for null/blank. */
    fun bind(sceneId: String?) {
        if (sceneId.isNullOrBlank()) {
            _draft.value = Scene(id = "", name = "New scene")
            return
        }
        viewModelScope.launch {
            _draft.value = sceneRepository.getScene(sceneId)
                ?: Scene(id = "", name = "New scene")
        }
    }

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
                .onFailure { _error.value = it.message ?: "Could not save scene" }
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
}
