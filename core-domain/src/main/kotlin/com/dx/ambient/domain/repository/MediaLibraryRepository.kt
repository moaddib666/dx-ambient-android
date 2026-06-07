package com.dx.ambient.domain.repository

import com.dx.ambient.domain.model.ImportedFolder
import com.dx.ambient.domain.model.LibraryMedia
import com.dx.ambient.domain.model.MediaKind
import kotlinx.coroutines.flow.Flow

/**
 * Boundary for the local media library (MVP feature 6).
 *
 * Folders are SAF tree URIs the user picked (internal/USB/gallery). Implementations take
 * persistable read permission and index media beneath each tree.
 */
interface MediaLibraryRepository {
    fun observeFolders(): Flow<List<ImportedFolder>>

    fun observeMedia(kind: MediaKind? = null): Flow<List<LibraryMedia>>

    /** Persists access to a newly picked SAF tree and indexes it. */
    suspend fun importFolder(treeUri: String): ImportedFolder

    suspend fun removeFolder(treeUri: String)

    /** Re-scans all imported folders for added/removed media. */
    suspend fun refresh()
}
