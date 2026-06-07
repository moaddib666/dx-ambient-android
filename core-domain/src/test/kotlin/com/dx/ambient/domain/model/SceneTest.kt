package com.dx.ambient.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneTest {

    private val video = MediaSource("content://v1", MediaSourceType.LOCAL_VIDEO)
    private val video2 = MediaSource("content://v2", MediaSourceType.LOCAL_VIDEO)

    @Test
    fun `hasVideo is false for NONE source`() {
        assertFalse(Scene(id = "1", name = "s").hasVideo)
    }

    @Test
    fun `hasVideo is true for a video source`() {
        assertTrue(Scene(id = "1", name = "s", videoSource = video).hasVideo)
    }

    @Test
    fun `fullVideoPlaylist puts videoSource at the head followed by the playlist`() {
        val scene = Scene(id = "1", name = "s", videoSource = video, videoPlaylist = listOf(video2))
        assertEquals(listOf(video, video2), scene.fullVideoPlaylist)
    }

    @Test
    fun `fullVideoPlaylist omits a NONE head but keeps the playlist`() {
        val scene = Scene(id = "1", name = "s", videoPlaylist = listOf(video2))
        assertEquals(listOf(video2), scene.fullVideoPlaylist)
    }

    @Test
    fun `hasMask reflects an enabled mask`() {
        assertFalse(Scene(id = "1", name = "s").hasMask)
        assertTrue(Scene(id = "1", name = "s", mask = Mask("content://m")).hasMask)
    }

    @Test
    fun `requiresCompositedRendering is true when masked or dimmed`() {
        assertFalse(Scene(id = "1", name = "s", brightness = 1f).requiresCompositedRendering)
        assertTrue(Scene(id = "1", name = "s", brightness = 0.5f).requiresCompositedRendering)
        assertTrue(Scene(id = "1", name = "s", mask = Mask("content://m")).requiresCompositedRendering)
    }

    @Test
    fun `default brightness is the projector-safe value`() {
        assertEquals(Scene.DEFAULT_BRIGHTNESS, Scene(id = "1", name = "s").brightness)
        assertTrue(Scene.DEFAULT_BRIGHTNESS < 1f)
    }
}
