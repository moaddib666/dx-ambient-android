package com.dx.ambient.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaModelsTest {

    @Test
    fun `MediaSource NONE has empty uri and NONE type`() {
        assertEquals("", MediaSource.NONE.uri)
        assertEquals(MediaSourceType.NONE, MediaSource.NONE.type)
        assertFalse(MediaSource.NONE.isYouTube)
    }

    @Test
    fun `isYouTube reflects the YOUTUBE type`() {
        assertTrue(MediaSource("id", MediaSourceType.YOUTUBE).isYouTube)
        assertFalse(MediaSource("u", MediaSourceType.LOCAL_VIDEO).isYouTube)
    }

    @Test
    fun `Mask NONE is disabled`() {
        assertFalse(Mask.NONE.enabled)
        assertTrue(Mask("content://m").enabled)
    }

    @Test
    fun `LibraryMedia maps each kind to the matching source type`() {
        fun media(kind: MediaKind) = LibraryMedia(
            uri = "u", displayName = "n", mimeType = "x/y", kind = kind, sourceTreeUri = "t",
        )
        assertEquals(MediaSourceType.LOCAL_VIDEO, media(MediaKind.VIDEO).toMediaSource().type)
        assertEquals(MediaSourceType.LOCAL_AUDIO, media(MediaKind.AUDIO).toMediaSource().type)
        assertEquals(MediaSourceType.LOCAL_IMAGE, media(MediaKind.IMAGE).toMediaSource().type)
        assertEquals("u", media(MediaKind.VIDEO).toMediaSource().uri)
        assertEquals("n", media(MediaKind.VIDEO).toMediaSource().displayName)
    }

    @Test
    fun `ProjectorSettings derived flags`() {
        assertFalse(ProjectorSettings().hasSleepTimer)
        assertFalse(ProjectorSettings().hasAutoDim)
        assertTrue(ProjectorSettings(sleepTimerMinutes = 30).hasSleepTimer)
        assertTrue(ProjectorSettings(dimAfterMinutes = 10).hasAutoDim)
    }
}
