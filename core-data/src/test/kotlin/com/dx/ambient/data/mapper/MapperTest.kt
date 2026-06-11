package com.dx.ambient.data.mapper

import com.dx.ambient.data.database.entity.LibraryMediaEntity
import com.dx.ambient.data.database.entity.SceneEntity
import com.dx.ambient.domain.model.LoopMode
import com.dx.ambient.domain.model.Mask
import com.dx.ambient.domain.model.MediaKind
import com.dx.ambient.domain.model.MediaSource
import com.dx.ambient.domain.model.MediaSourceType
import com.dx.ambient.domain.model.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapperTest {

    @Test
    fun `scene survives a round trip through its entity payload`() {
        val scene = Scene(
            id = "abc",
            name = "Fireplace",
            videoSource = MediaSource("content://v", MediaSourceType.LOCAL_VIDEO, "fire.mp4"),
            audioSource = MediaSource("content://a", MediaSourceType.LOCAL_AUDIO, "rain.mp3"),
            mask = Mask("content://m", "frame.png", opacity = 0.8f),
            brightness = 0.6f,
            loopMode = LoopMode.LOOP_PLAYLIST,
            muted = true,
            sortOrder = 3,
            createdAtEpochMs = 100,
            updatedAtEpochMs = 200,
        )
        val restored = scene.toEntity().toDomain()
        assertEquals(scene, restored)
    }

    @Test
    fun `scene entity columns mirror the domain fields`() {
        val scene = Scene(id = "x", name = "Glow", sortOrder = 7, updatedAtEpochMs = 42)
        val entity = scene.toEntity()
        assertEquals("x", entity.id)
        assertEquals("Glow", entity.name)
        assertEquals(7, entity.sortOrder)
        assertEquals(42L, entity.updatedAtEpochMs)
    }

    @Test
    fun `library media survives a round trip`() {
        val entity = LibraryMediaEntity(
            uri = "content://u",
            displayName = "clip.mp4",
            mimeType = "video/mp4",
            kind = MediaKind.VIDEO.name,
            sizeBytes = 123,
            durationMs = 4567,
            sourceTreeUri = "content://tree",
        )
        val domain = entity.toDomain()
        assertEquals(MediaKind.VIDEO, domain.kind)
        assertEquals("clip.mp4", domain.displayName)
        assertEquals(entity, domain.toEntity())
    }

    @Test
    fun `corrupt scene payload maps to null instead of crashing`() {
        val entity = SceneEntity(
            id = "broken",
            name = "Broken",
            sortOrder = 0,
            updatedAtEpochMs = 0,
            payloadJson = "{ not valid json",
        )
        assertNull(entity.toDomainOrNull())
    }

    @Test
    fun `valid scene payload maps via toDomainOrNull`() {
        val scene = Scene(id = "ok", name = "Glow")
        assertEquals(scene, scene.toEntity().toDomainOrNull())
    }

    @Test
    fun `corrupted kind string falls back to VIDEO instead of crashing`() {
        val entity = LibraryMediaEntity(
            uri = "u", displayName = "n", mimeType = "x/y",
            kind = "NOT_A_KIND", sizeBytes = 0, durationMs = 0, sourceTreeUri = "t",
        )
        assertEquals(MediaKind.VIDEO, entity.toDomain().kind)
    }
}
