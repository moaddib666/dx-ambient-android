package com.dx.ambient.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dx.ambient.domain.model.ImportedFolder
import com.dx.ambient.domain.model.LibraryMedia
import com.dx.ambient.domain.repository.MediaLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the media library (MVP feature 6): imported SAF folders and the
 * media indexed beneath them, plus a flag for in-flight import/refresh work.
 */
data class LibraryUiState(
    val folders: List<ImportedFolder> = emptyList(),
    val media: List<LibraryMedia> = emptyList(),
    val isImporting: Boolean = false,
)

/**
 * ViewModel backing [LibraryScreen] (MVP feature 6).
 *
 * Combines the repository's folder and media streams into a single [LibraryUiState],
 * and exposes intents to import, remove, and re-scan local/USB media folders.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaLibraryRepository: MediaLibraryRepository,
) : ViewModel() {

    private val importingState = kotlinx.coroutines.flow.MutableStateFlow(false)

    val uiState: StateFlow<LibraryUiState> = combine(
        mediaLibraryRepository.observeFolders(),
        mediaLibraryRepository.observeMedia(null),
        importingState,
    ) { folders, media, isImporting ->
        LibraryUiState(
            folders = folders,
            media = media,
            isImporting = isImporting,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    /** Persists access to a newly picked SAF tree and indexes its media. */
    fun importFolder(treeUri: String) {
        viewModelScope.launch {
            importingState.value = true
            runCatching { mediaLibraryRepository.importFolder(treeUri) }
            importingState.value = false
        }
    }

    /** Drops a previously imported folder and its indexed media. */
    fun removeFolder(treeUri: String) {
        viewModelScope.launch {
            runCatching { mediaLibraryRepository.removeFolder(treeUri) }
        }
    }

    /** Re-scans all imported folders for added/removed media. */
    fun refresh() {
        viewModelScope.launch {
            importingState.value = true
            runCatching { mediaLibraryRepository.refresh() }
            importingState.value = false
        }
    }
}
