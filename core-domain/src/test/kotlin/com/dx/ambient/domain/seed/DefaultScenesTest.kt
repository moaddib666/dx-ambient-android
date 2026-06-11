package com.dx.ambient.domain.seed

import com.dx.ambient.domain.model.MediaSourceType
import com.dx.ambient.domain.model.SceneKind
import com.dx.ambient.domain.model.SlideTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultScenesTest {

    @Test
    fun `default scene ids are unique`() {
        val ids = DefaultScenes.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `bundled slideshow is a Ken Burns slideshow over the DX World slides`() {
        val scene = DefaultScenes.slideshow
        assertEquals(SceneKind.SLIDESHOW, scene.resolvedKind)
        assertEquals(SlideTransition.KEN_BURNS, scene.slideshow.transition)
        assertEquals(110, scene.slideshowImages.size)
        // Asset names are generated — make sure padding matches the bundled files.
        assertEquals(
            "file:///android_asset/scenes/slideshow/slide_001.webp",
            scene.slideshowImages.first().uri,
        )
        assertEquals(
            "file:///android_asset/scenes/slideshow/slide_110.webp",
            scene.slideshowImages.last().uri,
        )
        assertEquals(MediaSourceType.LOCAL_AUDIO, scene.audioSource.type)
        assertTrue(scene.audioSource.uri.endsWith("/sound.m4a"))
    }
}
