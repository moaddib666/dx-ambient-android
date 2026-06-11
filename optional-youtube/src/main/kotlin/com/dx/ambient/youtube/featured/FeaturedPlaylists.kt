package com.dx.ambient.youtube.featured

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A curated playlist pinned on the Home screen and on top of the YouTube tab.
 *
 * [isDefault] entries ship with the app and cannot be removed. [thumbnailUrl]
 * is an optional artwork override (bundled art/asset URI or remote URL) — when
 * null the UI renders a branded gradient placeholder.
 */
data class FeaturedPlaylist(
    val playlistId: String,
    val title: String,
    val isDefault: Boolean = false,
    val thumbnailUrl: String? = null,
)

/**
 * Registry of featured playlists: immutable defaults + user-curated additions
 * persisted in DataStore. Any playlist from the user's YouTube account can be
 * featured (long-press in the YouTube tab); defaults can never be removed.
 */
@Singleton
class FeaturedPlaylistsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Defaults first, then the user's custom entries. */
    val featured: Flow<List<FeaturedPlaylist>> =
        context.featuredDataStore.data.map { prefs ->
            DEFAULTS + decodeCustomFeatured(prefs[CUSTOM_KEY])
        }

    suspend fun isFeatured(playlistId: String): Boolean =
        featured.first().any { it.playlistId == playlistId }

    /** Adds a custom featured playlist (no-op when already present or a default). */
    suspend fun add(playlistId: String, title: String, thumbnailUrl: String? = null) {
        if (DEFAULTS.any { it.playlistId == playlistId }) return
        context.featuredDataStore.edit { prefs ->
            val current = decodeCustomFeatured(prefs[CUSTOM_KEY])
            if (current.none { it.playlistId == playlistId }) {
                prefs[CUSTOM_KEY] = encodeCustomFeatured(
                    current + FeaturedPlaylist(playlistId, title, isDefault = false, thumbnailUrl = thumbnailUrl),
                )
            }
        }
    }

    /** Removes a custom entry. Defaults are not removable — returns false for them. */
    suspend fun remove(playlistId: String): Boolean {
        if (DEFAULTS.any { it.playlistId == playlistId }) return false
        context.featuredDataStore.edit { prefs ->
            val current = decodeCustomFeatured(prefs[CUSTOM_KEY])
            prefs[CUSTOM_KEY] = encodeCustomFeatured(current.filterNot { it.playlistId == playlistId })
        }
        return true
    }

    companion object {
        /** Shipped featured playlists — permanent, not user-removable. */
        val DEFAULTS: List<FeaturedPlaylist> = listOf(
            FeaturedPlaylist(
                playlistId = "PLazDOSmamQaHwJRkOrRBrfB8zT4JBHpgZ",
                title = "DX Ambient — Featured",
                isDefault = true,
                // Artwork + mask to be bundled later; gradient placeholder until then.
                thumbnailUrl = null,
            ),
        )
    }
}

private val Context.featuredDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "youtube_featured",
)

private val CUSTOM_KEY = stringPreferencesKey("custom_featured_json")

private val FeaturedJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class StoredFeatured(
    val playlistId: String,
    val title: String,
    val thumbnailUrl: String? = null,
)

internal fun encodeCustomFeatured(list: List<FeaturedPlaylist>): String =
    FeaturedJson.encodeToString(
        ListSerializer(StoredFeatured.serializer()),
        list.map { StoredFeatured(it.playlistId, it.title, it.thumbnailUrl) },
    )

internal fun decodeCustomFeatured(raw: String?): List<FeaturedPlaylist> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        FeaturedJson.decodeFromString(ListSerializer(StoredFeatured.serializer()), raw)
            .map { FeaturedPlaylist(it.playlistId, it.title, isDefault = false, thumbnailUrl = it.thumbnailUrl) }
    }.getOrDefault(emptyList())
}
