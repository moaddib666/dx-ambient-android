package com.dx.ambient.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dx.ambient.domain.model.ProjectorSettings
import com.dx.ambient.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the global projector settings screen (MVP features 8 & 9).
 *
 * Exposes the persisted [ProjectorSettings] as immutable UI state and funnels every edit
 * through [update], which delegates the copy-on-write transform to [SettingsRepository].
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Live projector settings, seeded with defaults until the repository emits. */
    val state: StateFlow<ProjectorSettings> = settingsRepository.observeSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProjectorSettings(),
        )

    /**
     * Apply a copy-on-write change to the persisted settings.
     *
     * UI rows call this with a `it.copy(...)` lambda; persistence is handled by the repository.
     */
    fun update(transform: (ProjectorSettings) -> ProjectorSettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
        }
    }
}
