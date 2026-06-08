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

    /** Optional, isolated YouTube hub (login wall → the user's playlists). */
    const val YOUTUBE = "youtube"

    /** Plays a chosen YouTube playlist via the official IFrame player. */
    const val YOUTUBE_PLAYER = "youtube/player/{playlistId}"
    fun youtubePlayer(playlistId: String) = "youtube/player/$playlistId"

    const val ARG_SCENE_ID = "sceneId"
    const val ARG_PLAYLIST_ID = "playlistId"
}
