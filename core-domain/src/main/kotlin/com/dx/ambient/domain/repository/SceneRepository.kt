package com.dx.ambient.domain.repository

import com.dx.ambient.domain.model.Scene
import kotlinx.coroutines.flow.Flow

/** Persistence boundary for scenes (MVP feature 7: save/load scenes). */
interface SceneRepository {
    fun observeScenes(): Flow<List<Scene>>

    suspend fun getScene(id: String): Scene?

    /** Inserts or updates the scene, returning the persisted copy (with timestamps set). */
    suspend fun upsertScene(scene: Scene): Scene

    suspend fun deleteScene(id: String)

    suspend fun duplicateScene(id: String): Scene?
}
