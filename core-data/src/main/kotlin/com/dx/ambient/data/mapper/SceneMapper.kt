package com.dx.ambient.data.mapper

import android.util.Log
import com.dx.ambient.data.database.entity.SceneEntity
import com.dx.ambient.domain.model.Scene
import kotlinx.serialization.json.Json

/** Shared lenient JSON used for persisting domain models as blobs. */
internal val AmbientJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun Scene.toEntity(): SceneEntity = SceneEntity(
    id = id,
    name = name,
    sortOrder = sortOrder,
    updatedAtEpochMs = updatedAtEpochMs,
    payloadJson = AmbientJson.encodeToString(Scene.serializer(), this),
)

internal fun SceneEntity.toDomain(): Scene =
    AmbientJson.decodeFromString(Scene.serializer(), payloadJson)

/**
 * Null-on-corruption variant: a single undecodable row (bad payloadJson from a
 * future/old version or disk corruption) must not crash boot or the home grid.
 */
internal fun SceneEntity.toDomainOrNull(): Scene? =
    runCatching { toDomain() }
        .onFailure { Log.e("SceneMapper", "Dropping corrupt scene row '$id'", it) }
        .getOrNull()
