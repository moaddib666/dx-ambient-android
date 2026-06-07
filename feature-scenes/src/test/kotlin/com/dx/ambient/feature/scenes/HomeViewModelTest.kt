package com.dx.ambient.feature.scenes

import app.cash.turbine.test
import com.dx.ambient.domain.model.Scene
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `state emits the repository scenes`() = runTest {
        val repo = FakeSceneRepository(listOf(Scene(id = "a", name = "Aurora")))
        val vm = HomeViewModel(repo)
        vm.state.test {
            // stateIn seeds with an empty list; depending on conflation timing the first item
            // collected may already be the repository content. Skip an empty seed if present.
            var item = awaitItem()
            if (item.isEmpty()) item = awaitItem()
            assertEquals(listOf("Aurora"), item.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete delegates to the repository`() = runTest {
        val repo = FakeSceneRepository(listOf(Scene(id = "a", name = "Aurora")))
        val vm = HomeViewModel(repo)
        vm.delete("a")
        assertEquals("a", repo.deleteCalledWith)
        assertEquals(null, repo.scenes.value["a"])
    }

    @Test
    fun `duplicate delegates to the repository`() = runTest {
        val repo = FakeSceneRepository(listOf(Scene(id = "a", name = "Aurora")))
        val vm = HomeViewModel(repo)
        vm.duplicate("a")
        assertEquals("a", repo.duplicateCalledWith)
        assertEquals(2, repo.scenes.value.size)
    }
}
