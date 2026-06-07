package com.dx.ambient.feature.library

import app.cash.turbine.test
import com.dx.ambient.domain.model.ImportedFolder
import com.dx.ambient.domain.model.LibraryMedia
import com.dx.ambient.domain.model.MediaKind
import com.dx.ambient.domain.repository.MediaLibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeRepo : MediaLibraryRepository {
        val folders = MutableStateFlow<List<ImportedFolder>>(emptyList())
        val media = MutableStateFlow<List<LibraryMedia>>(emptyList())
        var refreshed = false
        override fun observeFolders(): Flow<List<ImportedFolder>> = folders
        override fun observeMedia(kind: MediaKind?): Flow<List<LibraryMedia>> =
            if (kind == null) media else media.map { l -> l.filter { it.kind == kind } }
        override suspend fun importFolder(treeUri: String): ImportedFolder =
            ImportedFolder(treeUri, "Imported").also { folders.value = folders.value + it }
        override suspend fun removeFolder(treeUri: String) {
            folders.value = folders.value.filterNot { it.treeUri == treeUri }
        }
        override suspend fun refresh() { refreshed = true }
    }

    @Test
    fun `uiState combines folders and media`() = runTest {
        val repo = FakeRepo()
        val vm = LibraryViewModel(repo)
        vm.uiState.test {
            assertEquals(LibraryUiState(), awaitItem())
            repo.media.value = listOf(
                LibraryMedia("u", "clip.mp4", "video/mp4", MediaKind.VIDEO, sourceTreeUri = "t"),
            )
            val withMedia = awaitItem()
            assertEquals(1, withMedia.media.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `importFolder adds a folder and ends not importing`() = runTest {
        val repo = FakeRepo()
        val vm = LibraryViewModel(repo)
        vm.importFolder("content://tree")
        assertEquals(1, repo.folders.value.size)
        assertFalse(vm.uiState.value.isImporting)
    }

    @Test
    fun `removeFolder drops the folder`() = runTest {
        val repo = FakeRepo()
        repo.folders.value = listOf(ImportedFolder("content://tree", "F"))
        val vm = LibraryViewModel(repo)
        vm.removeFolder("content://tree")
        assertTrue(repo.folders.value.isEmpty())
    }
}
