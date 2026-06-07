package com.dx.ambient.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "imported_folders")
data class ImportedFolderEntity(
    @PrimaryKey val treeUri: String,
    val displayName: String,
    val addedAtEpochMs: Long,
)

@Entity(
    tableName = "library_media",
    indices = [Index("sourceTreeUri"), Index("kind")],
)
data class LibraryMediaEntity(
    @PrimaryKey val uri: String,
    val displayName: String,
    val mimeType: String,
    val kind: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val sourceTreeUri: String,
)
