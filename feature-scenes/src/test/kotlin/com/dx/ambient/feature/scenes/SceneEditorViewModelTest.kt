package com.dx.ambient.feature.scenes

import android.content.Context
import com.dx.ambient.domain.model.LibraryMedia
import com.dx.ambient.domain.model.LoopMode
import com.dx.ambient.domain.model.MediaKind
import com.dx.ambient.domain.model.MediaSourceType
import com.dx.ambient.domain.usecase.SaveSceneUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SceneEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sceneRepo = FakeSceneRepository()
    private val mediaRepo = FakeMediaLibraryRepository()

    // Context is only used to list bundled mask assets; a relaxed mock yields no default masks.
    private val context = mockk<Context>(relaxed = true).also {
        every { it.assets } returns mockk(relaxed = true)
    }

    private fun newViewModel() =
        SceneEditorViewModel(context, sceneRepo, mediaRepo, SaveSceneUseCase(sceneRepo))

    @Test
    fun `bind null starts a fresh draft`() {
        val vm = newViewModel()
        vm.bind(null)
        assertEquals("New scene", vm.draft.value.name)
        assertEquals("", vm.draft.value.id)
    }

    @Test
    fun `setBrightness clamps to 0 to 1`() {
        val vm = newViewModel()
        vm.setBrightness(1.5f)
        assertEquals(1f, vm.draft.value.brightness)
        vm.setBrightness(-0.5f)
        assertEquals(0f, vm.draft.value.brightness)
    }

    @Test
    fun `cycleLoopMode advances and wraps around`() {
        val vm = newViewModel()
        val start = vm.draft.value.loopMode
        val expectedNext = LoopMode.entries[(LoopMode.entries.indexOf(start) + 1) % LoopMode.entries.size]

        vm.cycleLoopMode()
        assertEquals(expectedNext, vm.draft.value.loopMode)

        // Cycling the remaining entries returns to the starting mode (full wrap-around).
        repeat(LoopMode.entries.size - 1) { vm.cycleLoopMode() }
        assertEquals(start, vm.draft.value.loopMode)
    }

    @Test
    fun `pick and clear audio and mask`() {
        val vm = newViewModel()
        val audio = LibraryMedia("u-a", "rain.mp3", "audio/mp3", MediaKind.AUDIO, sourceTreeUri = "t")
        val image = LibraryMedia("u-m", "frame.png", "image/png", MediaKind.IMAGE, sourceTreeUri = "t")
        vm.pickAudio(audio)
        vm.pickMask(image)
        assertEquals(MediaSourceType.LOCAL_AUDIO, vm.draft.value.audioSource.type)
        assertTrue(vm.draft.value.mask.enabled)
        vm.clearAudio()
        vm.clearMask()
        assertEquals(MediaSourceType.NONE, vm.draft.value.audioSource.type)
        assertFalse(vm.draft.value.mask.enabled)
    }

    @Test
    fun `save with a blank name surfaces an error and does not invoke onSaved`() = runTest {
        val vm = newViewModel()
        vm.bind(null)
        vm.setName("   ")
        var savedCalled = false
        vm.save { savedCalled = true }
        assertFalse(savedCalled)
        assertNotNull(vm.error.value)
        assertTrue(sceneRepo.scenes.value.isEmpty())
    }

    @Test
    fun `save with a valid name persists and invokes onSaved`() = runTest {
        val vm = newViewModel()
        vm.bind(null)
        vm.setName("Sunset")
        var savedCalled = false
        vm.save { savedCalled = true }
        assertTrue(savedCalled)
        assertNull(vm.error.value)
        assertEquals(1, sceneRepo.scenes.value.size)
    }
}
