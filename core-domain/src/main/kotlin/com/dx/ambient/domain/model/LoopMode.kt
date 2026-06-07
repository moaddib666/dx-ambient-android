package com.dx.ambient.domain.model

import kotlinx.serialization.Serializable

/** How a scene's media repeats while it is the active ambient scene. */
@Serializable
enum class LoopMode {
    /** Repeat the single source indefinitely. */
    LOOP_ONE,
    /** Play the source once, then stop (and dim, depending on settings). */
    PLAY_ONCE,
    /** Cycle through a playlist of sources, repeating the whole list. */
    LOOP_PLAYLIST,
    /** Cycle through the playlist in a shuffled order, repeating. */
    SHUFFLE_PLAYLIST,
}
