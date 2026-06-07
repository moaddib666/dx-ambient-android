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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.dx.ambient.rendering.components.EmptyState
import com.dx.ambient.rendering.components.IconTextButton
import com.dx.ambient.rendering.components.ScreenPadding
import com.dx.ambient.rendering.components.SectionHeader

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
            .padding(ScreenPadding),
    ) {
        SectionHeader(
            title = "Media Library",
            subtitle = "Import folders from local storage or USB, then browse your media.",
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconTextButton(
                text = if (state.isImporting) "Importing…" else "Import folder",
                icon = Icons.Filled.Add,
                onClick = { folderPicker.launch(null) },
            )
            IconTextButton(
                text = "Refresh",
                icon = Icons.Filled.Refresh,
                onClick = viewModel::refresh,
            )
        }

        if (state.folders.isEmpty()) {
            EmptyState(
                title = "No folders yet",
                message = "Import a folder from local storage or a USB drive to start your library.",
                action = {
                    IconTextButton(
                        text = "Import folder",
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
                text = "Imported folders",
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
                text = "Remove",
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

private fun sectionLabel(kind: MediaKind): String = when (kind) {
    MediaKind.VIDEO -> "Videos"
    MediaKind.AUDIO -> "Audio"
    MediaKind.IMAGE -> "Images"
}
