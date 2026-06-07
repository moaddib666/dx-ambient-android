package com.dx.ambient.domain.usecase

import com.dx.ambient.domain.model.Scene
import com.dx.ambient.domain.repository.SceneRepository
import javax.inject.Inject

/** Validates and persists a scene (MVP feature 7). */
class SaveSceneUseCase @Inject constructor(private val sceneRepository: SceneRepository) {

    suspend operator fun invoke(scene: Scene): Result<Scene> {
        if (scene.name.isBlank()) {
            return Result.failure(IllegalArgumentException("Scene name must not be blank"))
        }
        val sanitized = scene.copy(
            brightness = scene.brightness.coerceIn(0f, 1f),
            mask = scene.mask.copy(opacity = scene.mask.opacity.coerceIn(0f, 1f)),
        )
        return runCatching { sceneRepository.upsertScene(sanitized) }
    }
}
