package com.dx.ambient.feature.scenes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dx.ambient.domain.model.Scene
import com.dx.ambient.domain.repository.SceneRepository
import com.dx.ambient.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the TV remote-first home grid (MVP feature 1).
 *
 * Exposes the saved scenes as immutable UI state and provides the per-card
 * management actions (duplicate / delete) backed by [SceneRepository].
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sceneRepository: SceneRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Live list of saved scenes, ordered as the repository emits them. */
    val state: StateFlow<List<Scene>> = sceneRepository.observeScenes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** True until the user dismisses the first-launch guide (false while loading). */
    val showOnboarding: StateFlow<Boolean> = settingsRepository.observeSettings()
        .map { !it.onboardingCompleted }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    /** Persists that the guide was seen; it never auto-shows again. */
    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(onboardingCompleted = true) }
        }
    }

    /** Clone an existing scene into a fresh, independent copy (MVP feature 7). */
    fun duplicate(id: String) {
        viewModelScope.launch {
            sceneRepository.duplicateScene(id)
        }
    }

    /** Permanently remove a saved scene. */
    fun delete(id: String) {
        viewModelScope.launch {
            sceneRepository.deleteScene(id)
        }
    }
}
