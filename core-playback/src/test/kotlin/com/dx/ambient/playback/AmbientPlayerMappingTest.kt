package com.dx.ambient.playback

import androidx.media3.common.Player
import com.dx.ambient.domain.model.LoopMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the pure loop-mode → ExoPlayer repeat-mode mapping (no player instance needed). */
class AmbientPlayerMappingTest {

    @Test
    fun `play once maps to repeat off`() {
        assertEquals(Player.REPEAT_MODE_OFF, AmbientPlayer.loopModeToRepeatMode(LoopMode.PLAY_ONCE))
    }

    @Test
    fun `loop one maps to repeat one`() {
        assertEquals(Player.REPEAT_MODE_ONE, AmbientPlayer.loopModeToRepeatMode(LoopMode.LOOP_ONE))
    }

    @Test
    fun `playlist modes map to repeat all`() {
        assertEquals(Player.REPEAT_MODE_ALL, AmbientPlayer.loopModeToRepeatMode(LoopMode.LOOP_PLAYLIST))
        assertEquals(Player.REPEAT_MODE_ALL, AmbientPlayer.loopModeToRepeatMode(LoopMode.SHUFFLE_PLAYLIST))
    }
}
