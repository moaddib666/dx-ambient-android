package com.dx.ambient.data.database

import androidx.room.migration.Migration

/**
 * Every released schema migration, in order. When bumping
 * [AmbientDatabase] `version`, add the corresponding `Migration(n, n+1)`
 * here and commit the new schema JSON from `core-data/schemas/`.
 *
 * Entries must never be removed — users can upgrade from any old version.
 */
internal object AmbientDatabaseMigrations {
    val ALL: Array<Migration> = emptyArray()
}
