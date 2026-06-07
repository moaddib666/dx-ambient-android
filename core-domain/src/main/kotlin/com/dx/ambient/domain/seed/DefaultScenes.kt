package com.dx.ambient.domain.seed

import com.dx.ambient.domain.model.LoopMode
import com.dx.ambient.domain.model.Mask
import com.dx.ambient.domain.model.MediaSource
import com.dx.ambient.domain.model.MediaSourceType
import com.dx.ambient.domain.model.Scene

/**
 * Scenes the app ships with. Their media lives in the app's `assets/scenes/...` folder and is
 * referenced via `file:///android_asset/...` URIs, so they work offline with no import step.
 */
object DefaultScenes {

    const val CAMPFIRE_ID = "default-campfire"

    private const val CAMPFIRE_BASE = "file:///android_asset/scenes/campfire"

    /**
     * Digital Campfire: two short fire loops alternated for variety, a separate crackle
     * soundtrack, and an alpha vignette mask so the picture feathers into black (projector-safe,
     * no visible frame). Full brightness — the footage is already dark.
     */
    val campfire: Scene = Scene(
        id = CAMPFIRE_ID,
        name = "Digital Campfire",
        videoSource = MediaSource(
            uri = "$CAMPFIRE_BASE/p1.mp4",
            type = MediaSourceType.LOCAL_VIDEO,
            displayName = "Campfire loop 1",
        ),
        videoPlaylist = listOf(
            MediaSource(
                uri = "$CAMPFIRE_BASE/p2.mp4",
                type = MediaSourceType.LOCAL_VIDEO,
                displayName = "Campfire loop 2",
            ),
        ),
        audioSource = MediaSource(
            uri = "$CAMPFIRE_BASE/sound.m4a",
            type = MediaSourceType.LOCAL_AUDIO,
            displayName = "Campfire ambience",
        ),
        mask = Mask(
            uri = "$CAMPFIRE_BASE/mask.webp",
            displayName = "Campfire vignette",
        ),
        brightness = 1f,
        loopMode = LoopMode.LOOP_PLAYLIST,
        thumbnailUri = "$CAMPFIRE_BASE/preview.webp",
        sortOrder = -100,
    )

    /** All bundled default scenes, in display order. */
    val all: List<Scene> = listOf(campfire)
}
