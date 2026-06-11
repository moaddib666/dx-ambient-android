package com.dx.ambient.data.repository

import android.content.Context
import com.dx.ambient.domain.catalog.MaskCatalog
import com.dx.ambient.domain.model.Mask
import com.dx.ambient.domain.model.MediaKind
import com.dx.ambient.domain.repository.MediaLibraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Masks the players can cycle through live: the bundled `assets/masks/` set (shipped in
 * core-rendering, merged into the APK) followed by the user's imported library images —
 * the same set the scene editor offers.
 */
@Singleton
class MaskCatalogImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaLibraryRepository: MediaLibraryRepository,
) : MaskCatalog {

    override suspend fun masks(): List<Mask> = bundledMasks() + importedMasks()

    private fun bundledMasks(): List<Mask> = runCatching {
        context.assets.list("masks").orEmpty()
            .filter { it.endsWith(".webp", true) || it.endsWith(".png", true) }
            .sorted()
            .map { name ->
                Mask(
                    uri = "file:///android_asset/masks/$name",
                    displayName = name.substringBeforeLast('.')
                        .replace('_', ' ')
                        .replaceFirstChar { it.uppercase() },
                )
            }
    }.getOrDefault(emptyList())

    private suspend fun importedMasks(): List<Mask> = runCatching {
        mediaLibraryRepository.observeMedia(MediaKind.IMAGE).first()
            .map { Mask(uri = it.uri, displayName = it.displayName) }
    }.getOrDefault(emptyList())
}
