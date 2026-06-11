package com.dx.ambient.data.repository

import com.dx.ambient.data.database.dao.SceneDao
import com.dx.ambient.data.mapper.toDomainOrNull
import com.dx.ambient.data.mapper.toEntity
import com.dx.ambient.data.util.TimeProvider
import com.dx.ambient.domain.model.Scene
import com.dx.ambient.domain.repository.SceneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class SceneRepositoryImpl @Inject constructor(
    private val sceneDao: SceneDao,
    private val time: TimeProvider,
) : SceneRepository {

    override fun observeScenes(): Flow<List<Scene>> =
        sceneDao.observeAll().map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

    override suspend fun getScene(id: String): Scene? = sceneDao.getById(id)?.toDomainOrNull()

    override suspend fun upsertScene(scene: Scene): Scene {
        val now = time.nowEpochMs()
        val persisted = scene.copy(
            id = scene.id.ifBlank { UUID.randomUUID().toString() },
            createdAtEpochMs = if (scene.createdAtEpochMs == 0L) now else scene.createdAtEpochMs,
            updatedAtEpochMs = now,
        )
        sceneDao.upsert(persisted.toEntity())
        return persisted
    }

    override suspend fun deleteScene(id: String) = sceneDao.delete(id)

    override suspend fun duplicateScene(id: String): Scene? {
        val original = sceneDao.getById(id)?.toDomainOrNull() ?: return null
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (copy)",
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
        )
        return upsertScene(copy)
    }
}
