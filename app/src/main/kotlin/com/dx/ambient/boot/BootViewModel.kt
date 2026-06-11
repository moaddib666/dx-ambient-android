package com.dx.ambient.boot

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dx.ambient.domain.repository.SceneRepository
import com.dx.ambient.domain.repository.SettingsRepository
import com.dx.ambient.domain.usecase.SeedDefaultScenesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the app should go once boot work (seeding + resume decision) is done. */
sealed interface BootDecision {
    data object Loading : BootDecision
    data class OpenScene(val sceneId: String) : BootDecision
    data object OpenHome : BootDecision
}

/**
 * Runs first-launch work behind a black splash: seeds bundled default scenes (Digital Campfire)
 * and decides whether to boot straight into the last/seeded scene or land on Home.
 */
@HiltViewModel
class BootViewModel @Inject constructor(
    private val seedDefaultScenes: SeedDefaultScenesUseCase,
    private val sceneRepository: SceneRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _decision = MutableStateFlow<BootDecision>(BootDecision.Loading)
    val decision: StateFlow<BootDecision> = _decision.asStateFlow()

    init {
        viewModelScope.launch {
            // Boot must proceed even if seeding fails, but never silently.
            runCatching { seedDefaultScenes() }
                .onFailure { Log.e(TAG, "Seeding default scenes failed", it) }
            val settings = settingsRepository.observeSettings().first()
            val resumeId = settings.lastSceneId?.takeIf { settings.resumeLastSceneOnLaunch }
            _decision.value = when {
                resumeId != null && sceneRepository.getScene(resumeId) != null ->
                    BootDecision.OpenScene(resumeId)
                else -> BootDecision.OpenHome
            }
        }
    }

    private companion object {
        const val TAG = "BootViewModel"
    }
}
