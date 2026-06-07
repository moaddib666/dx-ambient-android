package com.dx.ambient.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dx.ambient.data.database.entity.ImportedFolderEntity
import com.dx.ambient.data.database.entity.LibraryMediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM imported_folders ORDER BY addedAtEpochMs DESC")
    fun observeFolders(): Flow<List<ImportedFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: ImportedFolderEntity)

    @Query("DELETE FROM imported_folders WHERE treeUri = :treeUri")
    suspend fun deleteFolder(treeUri: String)

    @Query("SELECT * FROM library_media ORDER BY displayName COLLATE NOCASE ASC")
    fun observeAllMedia(): Flow<List<LibraryMediaEntity>>

    @Query("SELECT * FROM library_media WHERE kind = :kind ORDER BY displayName COLLATE NOCASE ASC")
    fun observeMediaByKind(kind: String): Flow<List<LibraryMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedia(media: List<LibraryMediaEntity>)

    @Query("DELETE FROM library_media WHERE sourceTreeUri = :treeUri")
    suspend fun deleteMediaForTree(treeUri: String)
}
