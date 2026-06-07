package com.dx.ambient.domain.model

import kotlinx.serialization.Serializable

/**
 * A media file discovered in the user's imported folders (MVP feature 6).
 *
 * Produced by indexing a SAF tree the user granted (internal storage, USB, gallery folder).
 */
@Serializable
data class LibraryMedia(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val kind: MediaKind,
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    /** URI of the SAF tree this item was discovered under. */
    val sourceTreeUri: String,
) {
    fun toMediaSource(): MediaSource = MediaSource(
        uri = uri,
        type = when (kind) {
            MediaKind.VIDEO -> MediaSourceType.LOCAL_VIDEO
            MediaKind.AUDIO -> MediaSourceType.LOCAL_AUDIO
            MediaKind.IMAGE -> MediaSourceType.LOCAL_IMAGE
        },
        displayName = displayName,
    )
}

@Serializable
enum class MediaKind { VIDEO, AUDIO, IMAGE }

/** A SAF tree (folder) the user imported and granted persistable read access to. */
@Serializable
data class ImportedFolder(
    val treeUri: String,
    val displayName: String,
    val addedAtEpochMs: Long = 0L,
)
