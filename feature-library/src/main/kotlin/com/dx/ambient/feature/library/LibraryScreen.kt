package com.dx.ambient.feature.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.dx.ambient.domain.model.ImportedFolder
import com.dx.ambient.domain.model.LibraryMedia
import com.dx.ambient.domain.model.MediaKind
import com.dx.ambient.rendering.R
import com.dx.ambient.rendering.components.EmptyState
import com.dx.ambient.rendering.components.IconTextButton
import com.dx.ambient.rendering.components.SectionHeader
import com.dx.ambient.rendering.components.rememberScreenPadding

/**
 * Media Library screen (MVP feature 6).
 *
 * Lets the user import a media folder from local storage / USB via the Storage Access
 * Framework ([ActivityResultContracts.OpenDocumentTree]) and browse the indexed media,
 * grouped by [MediaKind]. Imported folders can be refreshed or removed.
 */
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler { onBack() }

    // SAF folder picker: on a granted tree URI, hand it to the ViewModel to persist + index.
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.importFolder(uri.toString())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(rememberScreenPadding()),
    ) {
        SectionHeader(
            title = stringResource(R.string.library_title),
            subtitle = stringResource(R.string.library_subtitle),
        )

        // Import/remove/refresh failures were previously silent; surface them here.
        state.error?.let { error ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (error) {
                        LibraryError.IMPORT_FAILED -> stringResource(R.string.library_error_import)
                        LibraryError.REMOVE_FAILED -> stringResource(R.string.library_error_remove)
                        LibraryError.REFRESH_FAILED -> stringResource(R.string.library_error_refresh)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                IconTextButton(
                    text = stringResource(R.string.common_dismiss),
                    icon = Icons.Default.Close,
                    onClick = viewModel::dismissError,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconTextButton(
                text = if (state.isImporting) {
                    stringResource(R.string.library_importing)
                } else {
                    stringResource(R.string.library_import_folder)
                },
                icon = Icons.Filled.Add,
                onClick = { folderPicker.launch(null) },
            )
            IconTextButton(
                text = stringResource(R.string.library_refresh),
                icon = Icons.Filled.Refresh,
                onClick = viewModel::refresh,
            )
        }

        if (state.folders.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.library_empty_title),
                message = stringResource(R.string.library_empty_message),
                action = {
                    IconTextButton(
                        text = stringResource(R.string.library_import_folder),
                        icon = Icons.Filled.Add,
                        onClick = { folderPicker.launch(null) },
                    )
                },
            )
        } else {
            LibraryContent(
                folders = state.folders,
                media = state.media,
                onRemoveFolder = viewModel::removeFolder,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LibraryContent(
    folders: List<ImportedFolder>,
    media: List<LibraryMedia>,
    onRemoveFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.library_imported_folders),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(folders, key = { it.treeUri }) { folder ->
            ImportedFolderRow(
                folder = folder,
                onRemove = { onRemoveFolder(folder.treeUri) },
            )
        }

        // Each media kind gets a labeled section with its own grid.
        for (kind in MediaKind.entries) {
            val itemsForKind = media.filter { it.kind == kind }
            if (itemsForKind.isEmpty()) continue

            item(key = "header-$kind") {
                Text(
                    text = sectionLabel(kind),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
            }
            item(key = "grid-$kind") {
                MediaGrid(items = itemsForKind)
            }
        }
    }
}

@Composable
private fun ImportedFolderRow(
    folder: ImportedFolder,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = false,
        onClick = {},
        modifier = modifier.fillMaxWidth(),
        headlineContent = { Text(folder.displayName) },
        supportingContent = {
            Text(
                text = folder.treeUri,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        },
        trailingContent = {
            IconTextButton(
                text = stringResource(R.string.common_remove),
                icon = Icons.Filled.Delete,
                onClick = onRemove,
            )
        },
    )
}

/**
 * A non-scrolling grid of media cards. Height is bounded so it can live inside the parent
 * [LazyColumn] without nesting two vertically-scrolling lazy lists.
 */
@Composable
private fun MediaGrid(
    items: List<LibraryMedia>,
    modifier: Modifier = Modifier,
) {
    val columns = 4
    val rows = (items.size + columns - 1) / columns
    // Card aspect ratio 16:9 plus label + spacing => approx height per row.
    val rowHeight = 180.dp
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .height(rowHeight * rows),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false,
    ) {
        items(items, key = { it.uri }) { item ->
            MediaCard(item = item)
        }
    }
}

@Composable
private fun MediaCard(
    item: LibraryMedia,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = {},
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentAlignment = Alignment.Center,
            ) {
                when (item.kind) {
                    MediaKind.IMAGE, MediaKind.VIDEO -> {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.displayName,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    MediaKind.AUDIO -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun sectionLabel(kind: MediaKind): String = when (kind) {
    MediaKind.VIDEO -> stringResource(R.string.library_section_videos)
    MediaKind.AUDIO -> stringResource(R.string.library_section_audio)
    MediaKind.IMAGE -> stringResource(R.string.library_section_images)
}
