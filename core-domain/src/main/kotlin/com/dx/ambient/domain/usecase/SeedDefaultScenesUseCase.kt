package com.dx.ambient.domain.usecase

import com.dx.ambient.domain.repository.SceneRepository
import com.dx.ambient.domain.repository.SettingsRepository
import com.dx.ambient.domain.seed.DefaultScenes
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Seeds the bundled default scenes (e.g. Digital Campfire, Space Odyssey) on launch.
 *
 * Tracks which defaults have already been seeded via [ProjectorSettings.seededDefaultIds], so a
 * default shipped in a later app version appears on existing installs — yet a default the user
 * deliberately deleted is never resurrected (its id stays in the seeded set).
 *
 * On the very first seed it also points `lastSceneId` at the campfire so the app can boot into it.
 */
class SeedDefaultScenesUseCase @Inject constructor(
    private val sceneRepository: SceneRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke() {
        val settings = settingsRepository.observeSettings().first()

        // Migrate installs seeded under the old boolean-only gate: back then only the campfire
        // shipped, so treat it as already-seeded to preserve the "deleted defaults stay gone" rule.
        val alreadySeeded = when {
            settings.seededDefaultIds.isNotEmpty() -> settings.seededDefaultIds
            settings.defaultsSeeded -> setOf(DefaultScenes.CAMPFIRE_ID)
            else -> emptySet()
        }

        val toSeed = DefaultScenes.all.filter { it.id !in alreadySeeded }
        if (toSeed.isEmpty()) {
            // Nothing new, but persist the migrated set once so we don't recompute it forever.
            if (settings.seededDefaultIds != alreadySeeded) {
                settingsRepository.update { it.copy(seededDefaultIds = alreadySeeded) }
            }
            return
        }

        toSeed.forEach { sceneRepository.upsertScene(it) }

        settingsRepository.update { current ->
            current.copy(
                defaultsSeeded = true,
                seededDefaultIds = alreadySeeded + toSeed.map { it.id },
                lastSceneId = current.lastSceneId ?: DefaultScenes.CAMPFIRE_ID,
            )
        }
    }
}
