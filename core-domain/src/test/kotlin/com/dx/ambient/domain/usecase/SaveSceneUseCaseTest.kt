package com.dx.ambient.domain.usecase

import com.dx.ambient.domain.model.Mask
import com.dx.ambient.domain.model.Scene
import com.dx.ambient.domain.repository.SceneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveSceneUseCaseTest {

    /** Minimal in-memory fake; records the last scene passed to upsert. */
    private class FakeSceneRepository(var failUpsert: Boolean = false) : SceneRepository {
        var lastUpserted: Scene? = null
        override fun observeScenes(): Flow<List<Scene>> = flowOf(emptyList())
        override suspend fun getScene(id: String): Scene? = null
        override suspend fun upsertScene(scene: Scene): Scene {
            if (failUpsert) throw IllegalStateException("db down")
            lastUpserted = scene
            return scene
        }
        override suspend fun deleteScene(id: String) = Unit
        override suspend fun duplicateScene(id: String): Scene? = null
    }

    private val repo = FakeSceneRepository()
    private val useCase = SaveSceneUseCase(repo)

    @Test
    fun `blank name fails validation and never touches the repository`() = runTest {
        val result = useCase(Scene(id = "1", name = "   "))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertNull(repo.lastUpserted)
    }

    @Test
    fun `brightness and mask opacity are coerced into 0 to 1`() = runTest {
        val result = useCase(
            Scene(id = "1", name = "Glow", brightness = 1.5f, mask = Mask("m", opacity = 2f)),
        )
        assertTrue(result.isSuccess)
        assertEquals(1f, repo.lastUpserted!!.brightness)
        assertEquals(1f, repo.lastUpserted!!.mask.opacity)
    }

    @Test
    fun `negative values are coerced up to 0`() = runTest {
        useCase(Scene(id = "1", name = "Glow", brightness = -0.5f, mask = Mask("m", opacity = -1f)))
        assertEquals(0f, repo.lastUpserted!!.brightness)
        assertEquals(0f, repo.lastUpserted!!.mask.opacity)
    }

    @Test
    fun `repository failure is surfaced as a failed result`() = runTest {
        val failing = SaveSceneUseCase(FakeSceneRepository(failUpsert = true))
        val result = failing(Scene(id = "1", name = "Glow"))
        assertTrue(result.isFailure)
    }
}
