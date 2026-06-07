package com.dx.ambient.data.repository

import android.net.Uri
import com.dx.ambient.data.database.dao.MediaDao
import com.dx.ambient.data.mapper.toDomain
import com.dx.ambient.data.mapper.toEntity
import com.dx.ambient.data.saf.SafMediaIndexer
import com.dx.ambient.data.util.TimeProvider
import com.dx.ambient.domain.model.ImportedFolder
import com.dx.ambient.domain.model.LibraryMedia
import com.dx.ambient.domain.model.MediaKind
import com.dx.ambient.domain.repository.MediaLibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MediaLibraryRepositoryImpl @Inject constructor(
    private val mediaDao: MediaDao,
    private val indexer: SafMediaIndexer,
    private val time: TimeProvider,
) : MediaLibraryRepository {

    override fun observeFolders(): Flow<List<ImportedFolder>> =
        mediaDao.observeFolders().map { rows -> rows.map { it.toDomain() } }

    override fun observeMedia(kind: MediaKind?): Flow<List<LibraryMedia>> {
        val source = if (kind == null) mediaDao.observeAllMedia()
        else mediaDao.observeMediaByKind(kind.name)
        return source.map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun importFolder(treeUri: String): ImportedFolder {
        val uri = Uri.parse(treeUri)
        indexer.persistPermission(uri)
        val folder = ImportedFolder(
            treeUri = treeUri,
            displayName = indexer.readTreeName(uri),
            addedAtEpochMs = time.nowEpochMs(),
        )
        mediaDao.upsertFolder(folder.toEntity())
        reindex(treeUri)
        return folder
    }

    override suspend fun removeFolder(treeUri: String) {
        indexer.releasePermission(Uri.parse(treeUri))
        mediaDao.deleteMediaForTree(treeUri)
        mediaDao.deleteFolder(treeUri)
    }

    override suspend fun refresh() {
        val folders = mediaDao.observeFolders().first()
        folders.forEach { reindex(it.treeUri) }
    }

    private suspend fun reindex(treeUri: String) {
        val media = indexer.index(Uri.parse(treeUri))
        mediaDao.deleteMediaForTree(treeUri)
        mediaDao.upsertMedia(media.map { it.toEntity() })
    }
}
