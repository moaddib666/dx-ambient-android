package com.dx.ambient.navigation

/** Centralised navigation routes for the single-activity, Compose-Navigation app. */
object Routes {
    /** Start destination: black splash that seeds defaults and boots into the last scene. */
    const val BOOT = "boot"

    const val HOME = "home"

    const val PLAYER = "player/{sceneId}"
    fun player(sceneId: String) = "player/$sceneId"

    /** Editor opened for a brand-new scene. */
    const val EDITOR_NEW = "editor"

    /** Editor opened for an existing scene id. */
    const val EDITOR = "editor/{sceneId}"
    fun editor(sceneId: String) = "editor/$sceneId"

    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val DEVICE_INFO = "device-info"

    /** Optional, isolated YouTube IFrame mode. */
    const val YOUTUBE = "youtube"

    const val ARG_SCENE_ID = "sceneId"
}
