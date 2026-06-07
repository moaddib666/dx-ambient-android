package com.dx.ambient.domain.playback

import com.dx.ambient.domain.model.Scene
import kotlinx.coroutines.flow.StateFlow

/**
 * Control surface for ambient playback, kept Android-free in the domain.
 *
 * The concrete implementation (core-playback) wraps Media3 ExoPlayer(s): one for the picture
 * (video/image loop) and, when the scene specifies a separate audio source, a second for sound.
 * The render attachment (Surface/Composition) lives in core-rendering.
 */
interface PlaybackController {
    val state: StateFlow<PlaybackState>

    /** Loads the scene's sources and begins playback honoring its loop mode and brightness. */
    fun load(scene: Scene)

    fun play()
    fun pause()
    fun stop()

    /** Applies a runtime brightness/dim multiplier (0f..1f) without reloading the scene. */
    fun setBrightness(target: Float)

    fun setMuted(muted: Boolean)

    /** Releases all underlying players. The controller must not be reused afterward. */
    fun release()
}

data class PlaybackState(
    val activeSceneId: String? = null,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val currentBrightness: Float = Scene.DEFAULT_BRIGHTNESS,
    val muted: Boolean = false,
    val errorMessage: String? = null,
)

enum class PlaybackStatus { IDLE, BUFFERING, PLAYING, PAUSED, ENDED, ERROR }
