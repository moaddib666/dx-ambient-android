package com.dx.ambient.domain.repository

import com.dx.ambient.domain.model.ProjectorSettings
import kotlinx.coroutines.flow.Flow

/** Boundary for global projector settings (MVP features 8 & 9). */
interface SettingsRepository {
    fun observeSettings(): Flow<ProjectorSettings>

    suspend fun update(transform: (ProjectorSettings) -> ProjectorSettings)

    suspend fun setLastSceneId(sceneId: String?)
}
