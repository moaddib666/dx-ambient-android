package com.dx.ambient.youtube.data

/** A YouTube playlist belonging to the signed-in user (read via the Data API). */
data class YouTubePlaylist(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val itemCount: Int,
)
