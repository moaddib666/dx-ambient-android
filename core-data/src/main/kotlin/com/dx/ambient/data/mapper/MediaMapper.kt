package com.dx.ambient.data.mapper

import com.dx.ambient.data.database.entity.ImportedFolderEntity
import com.dx.ambient.data.database.entity.LibraryMediaEntity
import com.dx.ambient.domain.model.ImportedFolder
import com.dx.ambient.domain.model.LibraryMedia
import com.dx.ambient.domain.model.MediaKind

internal fun ImportedFolderEntity.toDomain() = ImportedFolder(
    treeUri = treeUri,
    displayName = displayName,
    addedAtEpochMs = addedAtEpochMs,
)

internal fun ImportedFolder.toEntity() = ImportedFolderEntity(
    treeUri = treeUri,
    displayName = displayName,
    addedAtEpochMs = addedAtEpochMs,
)

internal fun LibraryMediaEntity.toDomain() = LibraryMedia(
    uri = uri,
    displayName = displayName,
    mimeType = mimeType,
    // Tolerate an unexpected/corrupted stored value rather than crashing the whole library query.
    kind = runCatching { MediaKind.valueOf(kind) }.getOrDefault(MediaKind.VIDEO),
    sizeBytes = sizeBytes,
    durationMs = durationMs,
    sourceTreeUri = sourceTreeUri,
)

internal fun LibraryMedia.toEntity() = LibraryMediaEntity(
    uri = uri,
    displayName = displayName,
    mimeType = mimeType,
    kind = kind.name,
    sizeBytes = sizeBytes,
    durationMs = durationMs,
    sourceTreeUri = sourceTreeUri,
)
