package com.dx.ambient.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Scenes are persisted with their stable, queryable fields as columns and the full domain
 * model serialized to JSON in [payloadJson]. This keeps the schema stable as the [Scene]
 * model grows, while still allowing ordering/lookups without deserializing every row.
 */
@Entity(tableName = "scenes")
data class SceneEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
    val updatedAtEpochMs: Long,
    val payloadJson: String,
)
