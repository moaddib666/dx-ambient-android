package com.dx.ambient.domain.usecase

import com.dx.ambient.domain.model.Mask
import com.dx.ambient.domain.model.ProjectorSettings
import com.dx.ambient.domain.model.Scene
import javax.inject.Inject

/**
 * Applies global projector settings on top of a scene to produce the scene that will actually
 * be rendered. This is where the performance-safe fallback (MVP feature 8) and the global
 * mask switch are enforced, independent of what an individual scene requested.
 */
class ResolveEffectiveSceneUseCase @Inject constructor() {
    operator fun invoke(scene: Scene, settings: ProjectorSettings): Scene {
        val masksAllowed = settings.masksEnabled && !settings.performanceSafeMode
        return scene.copy(
            mask = if (masksAllowed) scene.mask else Mask.NONE,
        )
    }
}
