package com.dx.ambient.data.repository

import com.dx.ambient.data.database.dao.SceneDao
import com.dx.ambient.data.database.entity.SceneEntity
import com.dx.ambient.data.mapper.toDomain
import com.dx.ambient.data.mapper.toEntity
import com.dx.ambient.data.util.TimeProvider
import com.dx.ambient.domain.model.Scene
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneRepositoryImplTest {

    /** In-memory SceneDao backed by a map. */
    private class FakeSceneDao : SceneDao {
        val rows = MutableStateFlow<Map<String, SceneEntity>>(emptyMap())
        override fun observeAll(): Flow<List<SceneEntity>> = rows.map { it.values.toList() }
        override suspend fun getById(id: String): SceneEntity? = rows.value[id]
        override suspend fun upsert(entity: SceneEntity) {
            rows.value = rows.value + (entity.id to entity)
        }
        override suspend fun delete(id: String) {
            rows.value = rows.value - id
        }
    }

    private class FixedTime(var now: Long = 1_000L) : TimeProvider {
        override fun nowEpochMs(): Long = now
    }

    private val dao = FakeSceneDao()
    private val time = FixedTime()
    private val repo = SceneRepositoryImpl(dao, time)

    @Test
    fun `upsert assigns a UUID to a blank id and stamps timestamps`() = runTest {
        val saved = repo.upsertScene(Scene(id = "", name = "New"))
        assertTrue(saved.id.isNotBlank())
        assertEquals(1_000L, saved.createdAtEpochMs)
        assertEquals(1_000L, saved.updatedAtEpochMs)
    }

    @Test
    fun `upsert preserves createdAt but advances updatedAt on a second save`() = runTest {
        val first = repo.upsertScene(Scene(id = "s1", name = "A"))
        time.now = 5_000L
        val second = repo.upsertScene(first.copy(name = "B"))
        assertEquals(first.createdAtEpochMs, second.createdAtEpochMs)
        assertEquals(5_000L, second.updatedAtEpochMs)
        assertNotEquals(second.createdAtEpochMs, second.updatedAtEpochMs)
    }

    @Test
    fun `getScene returns a persisted scene`() = runTest {
        val saved = repo.upsertScene(Scene(id = "s2", name = "Keep"))
        assertEquals(saved, repo.getScene("s2"))
        assertNull(repo.getScene("missing"))
    }

    @Test
    fun `duplicate makes a copy with a fresh id and a copy-suffixed name`() = runTest {
        repo.upsertScene(Scene(id = "orig", name = "Aurora"))
        val copy = repo.duplicateScene("orig")!!
        assertNotEquals("orig", copy.id)
        assertEquals("Aurora (copy)", copy.name)
    }

    @Test
    fun `duplicate of a missing scene returns null`() = runTest {
        assertNull(repo.duplicateScene("nope"))
    }

    @Test
    fun `delete removes the scene`() = runTest {
        repo.upsertScene(Scene(id = "del", name = "Bye"))
        repo.deleteScene("del")
        assertNull(repo.getScene("del"))
    }

    @Test
    fun `entity mapper round trip is stable`() {
        val scene = Scene(id = "m", name = "Map", brightness = 0.5f)
        assertEquals(scene, scene.toEntity().toDomain())
    }
}
