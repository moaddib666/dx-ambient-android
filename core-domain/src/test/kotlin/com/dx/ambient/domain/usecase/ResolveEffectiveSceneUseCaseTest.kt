package com.dx.ambient.domain.usecase

import com.dx.ambient.domain.model.Mask
import com.dx.ambient.domain.model.ProjectorSettings
import com.dx.ambient.domain.model.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveEffectiveSceneUseCaseTest {

    private val useCase = ResolveEffectiveSceneUseCase()
    private val maskedScene = Scene(
        id = "1",
        name = "Fireplace",
        mask = Mask(uri = "content://mask.png"),
    )

    @Test
    fun `keeps mask when masks enabled and not in safe mode`() {
        val result = useCase(maskedScene, ProjectorSettings(masksEnabled = true, performanceSafeMode = false))
        assertTrue(result.hasMask)
        assertEquals("content://mask.png", result.mask.uri)
    }

    @Test
    fun `drops mask when global mask switch is off`() {
        val result = useCase(maskedScene, ProjectorSettings(masksEnabled = false))
        assertFalse(result.hasMask)
        assertEquals(Mask.NONE, result.mask)
    }

    @Test
    fun `performance safe mode forces mask off even when masks enabled`() {
        val result = useCase(
            maskedScene,
            ProjectorSettings(masksEnabled = true, performanceSafeMode = true),
        )
        assertFalse(result.hasMask)
    }

    @Test
    fun `resolving only gates the mask and leaves brightness and loop mode untouched`() {
        val scene = maskedScene.copy(brightness = 0.4f, loopMode = com.dx.ambient.domain.model.LoopMode.LOOP_PLAYLIST)
        val result = useCase(scene, ProjectorSettings(masksEnabled = false))
        assertEquals(0.4f, result.brightness)
        assertEquals(com.dx.ambient.domain.model.LoopMode.LOOP_PLAYLIST, result.loopMode)
    }
}
