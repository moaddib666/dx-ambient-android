package com.dx.ambient.feature.scenes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dx.ambient.domain.model.Scene
import com.dx.ambient.domain.playback.PlaybackStatus
import com.dx.ambient.domain.repository.SceneRepository
import com.dx.ambient.domain.repository.SettingsRepository
import com.dx.ambient.domain.usecase.ResolveEffectiveSceneUseCase
import com.dx.ambient.playback.AmbientPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Immutable UI state for the full-screen player. */
data class PlayerUiState(
    val scene: Scene? = null,
    val loading: Boolean = true,
)

/**
 * Owns full-screen ambient playback (MVP features 2, 3, 5, 9).
 *
 * Resolves the effective scene (applying global settings / performance-safe fallback,
 * MVP feature 8), drives the singleton [AmbientPlayer], remembers the last scene for
 * resume-on-launch, and schedules the projector-safety sleep timer and auto-dim
 * (MVP feature 9).
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val sceneRepository: SceneRepository,
    private val settingsRepository: SettingsRepository,
    private val resolveEffectiveScene: ResolveEffectiveSceneUseCase,
    /** Exposed so the screen can attach the rendering surface via [com.dx.ambient.rendering.AmbientStage]. */
    val player: AmbientPlayer,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var autoDimJob: Job? = null

    /** Loads [sceneId], computes its effective form, begins playback and arms the timers. */
    fun bind(sceneId: String) {
        viewModelScope.launch {
            _state.value = PlayerUiState(scene = null, loading = true)

            val scene = sceneRepository.getScene(sceneId) ?: run {
                _state.value = PlayerUiState(scene = null, loading = false)
                return@launch
            }
            val settings = settingsRepository.observeSettings().first()
            val effective = resolveEffectiveScene(scene, settings)

            settingsRepository.setLastSceneId(scene.id)
            player.load(effective)
            _state.value = PlayerUiState(scene = effective, loading = false)

            armSleepTimer(settings.sleepTimerMinutes)
            armAutoDim(settings.dimAfterMinutes, settings.dimBrightness)
        }
    }

    /** Toggle play/pause in response to the remote center/enter key (MVP feature 2). */
    fun togglePlay() {
        if (player.state.value.status == PlaybackStatus.PLAYING) {
            player.pause()
        } else {
            player.play()
        }
    }

    /** Stop playback when leaving the player. Never releases the singleton player. */
    fun onStop() {
        sleepTimerJob?.cancel()
        autoDimJob?.cancel()
        player.stop()
    }

    private fun armSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) return
        sleepTimerJob = viewModelScope.launch {
            delay(minutes.toLong() * 60_000L)
            player.pause()
        }
    }

    private fun armAutoDim(minutes: Int, dimBrightness: Float) {
        autoDimJob?.cancel()
        if (minutes <= 0) return
        autoDimJob = viewModelScope.launch {
            delay(minutes.toLong() * 60_000L)
            player.setBrightness(dimBrightness)
        }
    }
}
