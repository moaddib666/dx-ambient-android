package com.dx.ambient.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dx.ambient.data.database.dao.MediaDao
import com.dx.ambient.data.database.dao.SceneDao
import com.dx.ambient.data.database.entity.ImportedFolderEntity
import com.dx.ambient.data.database.entity.LibraryMediaEntity
import com.dx.ambient.data.database.entity.SceneEntity

@Database(
    entities = [
        SceneEntity::class,
        ImportedFolderEntity::class,
        LibraryMediaEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AmbientDatabase : RoomDatabase() {
    abstract fun sceneDao(): SceneDao
    abstract fun mediaDao(): MediaDao

    companion object {
        const val NAME = "dx_ambient.db"
    }
}
