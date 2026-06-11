package com.dx.ambient.domain.seed

import com.dx.ambient.domain.model.LoopMode
import com.dx.ambient.domain.model.Mask
import com.dx.ambient.domain.model.MediaSource
import com.dx.ambient.domain.model.MediaSourceType
import com.dx.ambient.domain.model.Scene
import com.dx.ambient.domain.model.SceneKind
import com.dx.ambient.domain.model.SlideTransition
import com.dx.ambient.domain.model.SlideshowConfig

/**
 * Scenes the app ships with. Their media lives in the app's `assets/scenes/...` folder and is
 * referenced via `file:///android_asset/...` URIs, so they work offline with no import step.
 */
object DefaultScenes {

    const val CAMPFIRE_ID = "default-campfire"
    const val SPACE_ODYSSEY_ID = "default-space-odyssey"
    const val SLIDESHOW_ID = "default-slideshow"

    private const val CAMPFIRE_BASE = "file:///android_asset/scenes/campfire"
    private const val SPACE_ODYSSEY_BASE = "file:///android_asset/scenes/space-odyssey"
    private const val SLIDESHOW_BASE = "file:///android_asset/scenes/slideshow"

    /** Number of bundled `slide_NNN.webp` images in `assets/scenes/slideshow/`. */
    private const val SLIDESHOW_IMAGE_COUNT = 110

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

    /**
     * Space Odyssey: a hyperrealistic ultra-wide drift through space, with a separate ambient
     * soundtrack that loops independently of the footage. No mask — the footage already fades
     * into black at the edges. Full brightness since the picture is dark.
     */
    val spaceOdyssey: Scene = Scene(
        id = SPACE_ODYSSEY_ID,
        name = "Space Odyssey",
        videoSource = MediaSource(
            uri = "$SPACE_ODYSSEY_BASE/loop.mp4",
            type = MediaSourceType.LOCAL_VIDEO,
            displayName = "Space drift",
        ),
        audioSource = MediaSource(
            uri = "$SPACE_ODYSSEY_BASE/sound.m4a",
            type = MediaSourceType.LOCAL_AUDIO,
            displayName = "Space ambience",
        ),
        brightness = 1f,
        loopMode = LoopMode.LOOP_ONE,
        thumbnailUri = "$SPACE_ODYSSEY_BASE/preview.webp",
        sortOrder = -90,
    )

    /**
     * DX World: a bundled Ken Burns slideshow through the Dimension-X locations — stills
     * slowly zoom/pan and cross-fade on a timer, shuffled so the four variants of each
     * location don't run back-to-back. The soundtrack is the DX score concatenated into a
     * single looping track. Full brightness — the stills are already calm.
     */
    val slideshow: Scene = run {
        val images = List(SLIDESHOW_IMAGE_COUNT) { index ->
            val number = (index + 1).toString().padStart(3, '0')
            MediaSource(
                uri = "$SLIDESHOW_BASE/slide_$number.webp",
                type = MediaSourceType.LOCAL_IMAGE,
                displayName = "Slide ${index + 1}",
            )
        }
        Scene(
            id = SLIDESHOW_ID,
            name = "DX World",
            kind = SceneKind.SLIDESHOW,
            videoSource = images.first(),
            videoPlaylist = images.drop(1),
            audioSource = MediaSource(
                uri = "$SLIDESHOW_BASE/sound.m4a",
                type = MediaSourceType.LOCAL_AUDIO,
                displayName = "DX World soundtrack",
            ),
            slideshow = SlideshowConfig(
                intervalMs = 12_000L,
                transition = SlideTransition.KEN_BURNS,
            ),
            brightness = 1f,
            loopMode = LoopMode.SHUFFLE_PLAYLIST,
            thumbnailUri = "$SLIDESHOW_BASE/preview.webp",
            sortOrder = -80,
        )
    }

    /** All bundled default scenes, in display order. */
    val all: List<Scene> = listOf(campfire, spaceOdyssey, slideshow)
}
