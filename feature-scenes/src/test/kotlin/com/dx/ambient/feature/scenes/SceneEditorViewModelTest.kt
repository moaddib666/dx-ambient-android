package com.dx.ambient.feature.scenes

import android.content.Context
import com.dx.ambient.domain.catalog.CatalogPlaylist
import com.dx.ambient.domain.catalog.YouTubeCatalog
import com.dx.ambient.domain.model.LibraryMedia
import com.dx.ambient.domain.model.LoopMode
import com.dx.ambient.domain.model.MediaKind
import com.dx.ambient.domain.model.MediaSource
import com.dx.ambient.domain.model.MediaSourceType
import com.dx.ambient.domain.model.SceneKind
import com.dx.ambient.domain.model.SlideTransition
import com.dx.ambient.domain.model.SlideshowConfig
import com.dx.ambient.domain.usecase.SaveSceneUseCase
import com.dx.ambient.playback.AmbientPlayer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.launch
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

    // Context lists bundled mask assets (relaxed mock yields none) and resolves the
    // localized default scene name (stubbed to the English resource value).
    private val context = mockk<Context>(relaxed = true).also {
        every { it.assets } returns mockk(relaxed = true)
        every { it.getString(any()) } returns "New scene"
    }

    private val player = mockk<AmbientPlayer>(relaxed = true)

    /** Offline fake: YouTube unavailable, recognizes nothing as a YouTube link. */
    private val youTubeCatalog = object : YouTubeCatalog {
        override suspend fun isAvailable(): Boolean = false
        override suspend fun myPlaylists(): List<CatalogPlaylist> = emptyList()
        override fun builtInPlaylists(): List<CatalogPlaylist> = emptyList()
        override fun parseLink(input: String): MediaSource? = null
        override fun playlistSource(playlist: CatalogPlaylist): MediaSource =
            MediaSource(
                uri = "https://www.youtube.com/playlist?list=${playlist.id}",
                type = MediaSourceType.YOUTUBE,
                displayName = playlist.title,
            )
    }

    private fun newViewModel() =
        SceneEditorViewModel(
            context,
            sceneRepo,
            mediaRepo,
            SaveSceneUseCase(sceneRepo),
            youTubeCatalog,
            player,
        )

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
    fun `cycleMask steps through none and imported masks, wrapping both ways`() = runTest {
        val vm = newViewModel()
        mediaRepo.media.value = listOf(
                LibraryMedia("u1", "Mask A", "image/png", MediaKind.IMAGE, 0, 0, "t"),
                LibraryMedia("u2", "Mask B", "image/png", MediaKind.IMAGE, 0, 0, "t"),
            )
        // Subscribe so the stateIn flows actually collect, then let them settle.
        backgroundScope.launch { vm.masks.collect {} }
        testScheduler.advanceUntilIdle()

        vm.bind(null)
        assertFalse(vm.draft.value.mask.enabled)

        vm.cycleMask(1)
        assertEquals("u1", vm.draft.value.mask.uri)
        vm.cycleMask(1)
        assertEquals("u2", vm.draft.value.mask.uri)
        vm.cycleMask(1) // wraps to "no mask"
        assertFalse(vm.draft.value.mask.enabled)
        vm.cycleMask(-1) // wraps backwards to the last mask
        assertEquals("u2", vm.draft.value.mask.uri)
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
    fun `setVideoAlpha and setVideoScale clamp to their ranges`() {
        val vm = newViewModel()
        vm.setVideoAlpha(1.5f)
        assertEquals(1f, vm.draft.value.videoAlpha)
        vm.setVideoAlpha(-1f)
        assertEquals(0f, vm.draft.value.videoAlpha)
        vm.setVideoScale(5f)
        assertEquals(2f, vm.draft.value.videoScale)
        vm.setVideoScale(0.1f)
        assertEquals(0.5f, vm.draft.value.videoScale)
    }

    @Test
    fun `setVideoLink accepts a direct stream URL and rejects garbage`() {
        val vm = newViewModel()
        assertTrue(vm.setVideoLink("https://example.com/ambient.mp4"))
        assertEquals(MediaSourceType.STREAM, vm.draft.value.videoSource.type)
        assertEquals("https://example.com/ambient.mp4", vm.draft.value.videoSource.uri)
        assertFalse(vm.setVideoLink("not a url at all"))
        assertEquals(MediaSourceType.STREAM, vm.draft.value.videoSource.type)
    }

    @Test
    fun `setAudioStream accepts http URLs only`() {
        val vm = newViewModel()
        assertTrue(vm.setAudioStream("http://radio.example.com/lofi"))
        assertEquals(MediaSourceType.STREAM, vm.draft.value.audioSource.type)
        assertFalse(vm.setAudioStream("ftp://radio.example.com/lofi"))
        assertFalse(vm.setAudioStream("just words"))
    }

    @Test
    fun `new draft starts as a video scene`() {
        val vm = newViewModel()
        vm.bind(null)
        assertEquals(SceneKind.VIDEO, vm.draft.value.resolvedKind)
    }

    @Test
    fun `setKind clears a picture source that does not fit the new type`() {
        val vm = newViewModel()
        vm.bind(null)
        vm.pickVideo(LibraryMedia("u-v", "fire.mp4", "video/mp4", MediaKind.VIDEO, sourceTreeUri = "t"))
        assertEquals(MediaSourceType.LOCAL_VIDEO, vm.draft.value.videoSource.type)

        vm.setKind(SceneKind.SLIDESHOW)
        assertEquals(SceneKind.SLIDESHOW, vm.draft.value.resolvedKind)
        assertEquals(MediaSourceType.NONE, vm.draft.value.videoSource.type)
        assertTrue(vm.draft.value.videoPlaylist.isEmpty())
    }

    @Test
    fun `setKind keeps shared parts like audio and mask`() {
        val vm = newViewModel()
        vm.bind(null)
        vm.pickAudio(LibraryMedia("u-a", "rain.mp3", "audio/mp3", MediaKind.AUDIO, sourceTreeUri = "t"))
        vm.pickMask(LibraryMedia("u-m", "frame.png", "image/png", MediaKind.IMAGE, sourceTreeUri = "t"))

        vm.setKind(SceneKind.SLIDESHOW)
        assertEquals(MediaSourceType.LOCAL_AUDIO, vm.draft.value.audioSource.type)
        assertTrue(vm.draft.value.mask.enabled)
    }

    @Test
    fun `toggleSlideshowImage adds and removes images preserving order`() {
        val vm = newViewModel()
        vm.bind(null)
        val a = LibraryMedia("u-1", "a.jpg", "image/jpeg", MediaKind.IMAGE, sourceTreeUri = "t")
        val b = LibraryMedia("u-2", "b.jpg", "image/jpeg", MediaKind.IMAGE, sourceTreeUri = "t")

        vm.toggleSlideshowImage(a)
        vm.toggleSlideshowImage(b)
        assertEquals(SceneKind.SLIDESHOW, vm.draft.value.resolvedKind)
        assertEquals(listOf("u-1", "u-2"), vm.draft.value.slideshowImages.map { it.uri })
        // The list is stored as videoSource head + videoPlaylist tail.
        assertEquals("u-1", vm.draft.value.videoSource.uri)
        assertEquals(listOf("u-2"), vm.draft.value.videoPlaylist.map { it.uri })

        vm.toggleSlideshowImage(a)
        assertEquals(listOf("u-2"), vm.draft.value.slideshowImages.map { it.uri })
        assertEquals("u-2", vm.draft.value.videoSource.uri)
        assertTrue(vm.draft.value.videoPlaylist.isEmpty())
    }

    @Test
    fun `setSlideIntervalMs clamps to the supported range`() {
        val vm = newViewModel()
        vm.setSlideIntervalMs(1L)
        assertEquals(SlideshowConfig.MIN_INTERVAL_MS, vm.draft.value.slideshow.intervalMs)
        vm.setSlideIntervalMs(Long.MAX_VALUE)
        assertEquals(SlideshowConfig.MAX_INTERVAL_MS, vm.draft.value.slideshow.intervalMs)
    }

    @Test
    fun `cycleSlideTransition wraps through all transitions`() {
        val vm = newViewModel()
        val start = vm.draft.value.slideshow.transition
        repeat(SlideTransition.entries.size) { vm.cycleSlideTransition() }
        assertEquals(start, vm.draft.value.slideshow.transition)
    }

    @Test
    fun `setVideoStreamLink accepts http URLs only and sets the video kind`() {
        val vm = newViewModel()
        assertTrue(vm.setVideoStreamLink("https://example.com/ambient.mp4"))
        assertEquals(SceneKind.VIDEO, vm.draft.value.resolvedKind)
        assertEquals(MediaSourceType.STREAM, vm.draft.value.videoSource.type)
        assertFalse(vm.setVideoStreamLink("not a url"))
    }

    @Test
    fun `setYouTubeLink rejects input the catalog does not recognize`() {
        val vm = newViewModel()
        assertFalse(vm.setYouTubeLink("https://example.com/ambient.mp4"))
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
