package com.dx.ambient.youtube.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import androidx.compose.runtime.remember
import com.dx.ambient.rendering.components.AmbientScreen
import com.dx.ambient.rendering.components.PrimaryButton
import com.dx.ambient.rendering.components.SectionHeader
import com.dx.ambient.rendering.components.isTvDevice
import com.dx.ambient.rendering.components.rememberScreenPadding
import com.dx.ambient.rendering.components.touchClickable
import com.dx.ambient.youtube.data.YouTubePlaylist
import com.dx.ambient.youtube.featured.FeaturedPlaylist

/**
 * The YouTube hub. Starts at a login wall ("sign in with your YouTube account") and, once
 * authorized, lists the user's playlists. Selecting one plays it through the official IFrame
 * player. The whole feature is gated to devices with Google Play Services.
 */
@Composable
fun YouTubeTabScreen(
    /** Play a playlist; the second argument is an optional mask URI to composite over it. */
    onPlayPlaylist: (String, String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: YouTubeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val featuredViewModel: YouTubeFeaturedViewModel = hiltViewModel()
    val featuredState by featuredViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { featuredViewModel.refresh() }

    // Launches the one-time OAuth consent screen when the Authorization API asks for it.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onConsentResult(result.data)
        } else {
            viewModel.onConsentCancelled()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.consentRequests.collect { sender ->
            consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    BackHandler { onBack() }

    AmbientScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(rememberScreenPadding())) {
            SectionHeader(title = "YouTube", subtitle = "Play your YouTube playlists")

            // Long-press a featured card to choose its mask.
            var maskPickerFor by remember {
                androidx.compose.runtime.mutableStateOf<FeaturedPlaylist?>(null)
            }

            FeaturedRail(
                state = featuredState,
                onPlay = { item -> onPlayPlaylist(item.playlistId, item.maskUri) },
                onPickMask = { maskPickerFor = it },
            )

            maskPickerFor?.let { target ->
                MaskPickerDialog(
                    title = target.title,
                    masks = featuredViewModel.bundledMasks(),
                    selectedUri = target.maskUri,
                    onSelect = { uri ->
                        featuredViewModel.setMask(target.playlistId, uri)
                        maskPickerFor = null
                    },
                    onDismiss = { maskPickerFor = null },
                )
            }

            when (val s = state) {
                YouTubeUiState.Unsupported -> CenteredMessage(
                    "YouTube needs Google Play Services, which isn't available on this device.",
                )
                YouTubeUiState.NotConfigured -> CenteredMessage(
                    "YouTube sign-in isn't configured yet. Add your OAuth Web client ID in " +
                        "youtube_config.xml to enable it.",
                )
                YouTubeUiState.SignedOut -> SignInPrompt(onSignIn = viewModel::signIn)
                YouTubeUiState.Loading -> CenteredMessage("Loading…")
                is YouTubeUiState.Playlists -> {
                    if (s.items.isEmpty()) {
                        CenteredMessage("No playlists found on your account.")
                    } else {
                        PlaylistGrid(
                            playlists = s.items,
                            featuredIds = featuredState.featuredIds,
                            onPlay = { id -> onPlayPlaylist(id, null) },
                            onToggleFeatured = featuredViewModel::toggleFeatured,
                        )
                    }
                }
                is YouTubeUiState.Error -> ErrorView(message = s.message, onRetry = viewModel::retry)
            }
        }
    }
}

@Composable
private fun SignInPrompt(onSignIn: () -> Unit) {
    // On TV, give the primary action initial focus so a remote / D-pad has somewhere to land.
    val tvDevice = isTvDevice()
    val signInFocus = remember { FocusRequester() }
    LaunchedEffect(tvDevice) {
        if (tvDevice) runCatching { signInFocus.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Sign in with your YouTube account to use YouTube features",
                style = MaterialTheme.typography.titleMedium,
            )
            PrimaryButton(
                text = "Sign in with YouTube",
                onClick = onSignIn,
                modifier = Modifier.focusRequester(signInFocus),
            )
        }
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            PrimaryButton(text = "Retry", onClick = onRetry)
        }
    }
}

/**
 * Curated playlists pinned above the account grid. Entries are greyed out with a
 * status hint when offline or signed out; the shipped defaults are permanent.
 */
@Composable
private fun FeaturedRail(
    state: FeaturedUiState,
    onPlay: (FeaturedPlaylist) -> Unit,
    onPickMask: (FeaturedPlaylist) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.items.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Featured", style = MaterialTheme.typography.titleMedium)
            if (!state.available && state.statusHint != null) {
                Text(
                    text = "  ⚠ ${state.statusHint}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    text = "  long-press a card to choose its mask",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            items(count = state.items.size, key = { state.items[it].playlistId }) { index ->
                val item = state.items[index]
                FeaturedCard(
                    item = item,
                    enabled = state.available,
                    onClick = { if (state.available) onPlay(item) },
                    onLongClick = { onPickMask(item) },
                )
            }
        }
    }
}

/** Simple gallery dialog: pick a bundled mask (or none) for a featured playlist. */
@Composable
private fun MaskPickerDialog(
    title: String,
    masks: List<Pair<String, String>>,
    selectedUri: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(
                    color = Color(0xFF12161B).copy(alpha = 0.97f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Mask for $title", style = MaterialTheme.typography.titleMedium)
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(count = masks.size + 1) { index ->
                    if (index == 0) {
                        MaskOptionTile(
                            label = "No mask",
                            maskUri = null,
                            selected = selectedUri == null,
                            onClick = { onSelect(null) },
                        )
                    } else {
                        val (label, uri) = masks[index - 1]
                        MaskOptionTile(
                            label = label,
                            maskUri = uri,
                            selected = selectedUri == uri,
                            onClick = { onSelect(uri) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MaskOptionTile(
    label: String,
    maskUri: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(150.dp)
            .touchClickable(onClick = onClick),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF24455A), Color(0xFF1B5A4A), Color(0xFF53306B)),
                            ),
                        ),
                )
                if (maskUri != null) {
                    AsyncImage(
                        model = maskUri,
                        contentDescription = label,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                text = if (selected) "✓ $label" else label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
private fun FeaturedCard(
    item: FeaturedPlaylist,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .width(220.dp)
            .touchClickable(onClick = onClick, onLongClick = onLongClick)
            .alpha(if (enabled) 1f else 0.45f),
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            focusedContainerColor = Color.White.copy(alpha = 0.14f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        scale = CardDefaults.scale(focusedScale = 1.04f),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                if (item.thumbnailUrl != null) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Placeholder until the bundled artwork lands.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0E2A3C), Color(0xFF123B33)),
                                ),
                            ),
                    )
                }
                if (!enabled) {
                    Text(
                        text = "⚠",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (item.isDefault) "Featured • built-in" else "Featured",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun PlaylistGrid(
    playlists: List<YouTubePlaylist>,
    featuredIds: Set<String>,
    onPlay: (String) -> Unit,
    onToggleFeatured: (YouTubePlaylist) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 220.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().padding(top = 16.dp),
    ) {
        items(items = playlists, key = { it.id }) { playlist ->
            PlaylistCard(
                playlist = playlist,
                featured = playlist.id in featuredIds,
                onClick = { onPlay(playlist.id) },
                onLongClick = { onToggleFeatured(playlist) },
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: YouTubePlaylist,
    featured: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        // Long-press (touch) or center-hold (remote) features/unfeatures the playlist.
        onLongClick = onLongClick,
        modifier = Modifier
            .fillMaxWidth()
            .touchClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            focusedContainerColor = Color.White.copy(alpha = 0.14f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        scale = CardDefaults.scale(focusedScale = 1.04f),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                if (playlist.thumbnailUrl != null) {
                    AsyncImage(
                        model = playlist.thumbnailUrl,
                        contentDescription = playlist.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().padding(0.dp))
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (featured) "★ Featured • ${playlist.itemCount} videos" else "${playlist.itemCount} videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}
