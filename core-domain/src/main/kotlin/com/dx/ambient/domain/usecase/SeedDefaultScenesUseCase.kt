package com.dx.ambient.domain.usecase

import com.dx.ambient.domain.repository.SceneRepository
import com.dx.ambient.domain.repository.SettingsRepository
import com.dx.ambient.domain.seed.DefaultScenes
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Seeds the bundled default scenes (e.g. Digital Campfire) on first launch, exactly once.
 *
 * Idempotent via [ProjectorSettings.defaultsSeeded] — deleting a default scene will not bring it
 * back. On first seed it also points `lastSceneId` at the campfire so the app can boot into it.
 */
class SeedDefaultScenesUseCase @Inject constructor(
    private val sceneRepository: SceneRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke() {
        val settings = settingsRepository.observeSettings().first()
        if (settings.defaultsSeeded) return

        DefaultScenes.all.forEach { sceneRepository.upsertScene(it) }

        settingsRepository.update { current ->
            current.copy(
                defaultsSeeded = true,
                lastSceneId = current.lastSceneId ?: DefaultScenes.CAMPFIRE_ID,
            )
        }
    }
}
