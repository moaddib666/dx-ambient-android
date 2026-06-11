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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.dx.ambient.rendering.R
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
            SectionHeader(
                title = stringResource(R.string.youtube_label),
                subtitle = stringResource(R.string.yt_subtitle),
            )

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
                    stringResource(R.string.yt_unsupported),
                )
                YouTubeUiState.NotConfigured -> CenteredMessage(
                    stringResource(R.string.yt_not_configured),
                )
                YouTubeUiState.SignedOut -> SignInPrompt(onSignIn = viewModel::signIn)
                YouTubeUiState.Loading -> CenteredMessage(stringResource(R.string.common_loading))
                is YouTubeUiState.Playlists -> {
                    if (s.items.isEmpty()) {
                        CenteredMessage(stringResource(R.string.yt_no_playlists))
                    } else {
                        PlaylistGrid(
                            playlists = s.items,
                            featuredIds = featuredState.featuredIds,
                            onPlay = { id -> onPlayPlaylist(id, null) },
                            onToggleFeatured = featuredViewModel::toggleFeatured,
                        )
                    }
                }
                is YouTubeUiState.Error -> ErrorView(error = s, onRetry = viewModel::retry)
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
                text = stringResource(R.string.yt_sign_in_prompt),
                style = MaterialTheme.typography.titleMedium,
            )
            PrimaryButton(
                text = stringResource(R.string.yt_sign_in_button),
                onClick = onSignIn,
                modifier = Modifier.focusRequester(signInFocus),
            )
        }
    }
}

/** Localized hint for why featured playlists are unplayable right now. */
@Composable
fun featuredStatusText(status: FeaturedStatus): String = when (status) {
    FeaturedStatus.UNAVAILABLE -> stringResource(R.string.yt_status_unavailable)
    FeaturedStatus.OFFLINE -> stringResource(R.string.yt_status_offline)
    FeaturedStatus.SIGN_IN_REQUIRED -> stringResource(R.string.yt_status_sign_in_required)
}

@Composable
private fun CenteredMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ErrorView(error: YouTubeUiState.Error, onRetry: () -> Unit) {
    val kindText = when (error.kind) {
        YouTubeUiState.Error.ErrorKind.SIGN_IN_FAILED ->
            stringResource(R.string.yt_error_sign_in_failed)
        YouTubeUiState.Error.ErrorKind.SIGN_IN_CANCELLED ->
            stringResource(R.string.yt_error_sign_in_cancelled)
        YouTubeUiState.Error.ErrorKind.LOAD_FAILED ->
            stringResource(R.string.yt_error_load_failed)
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = error.detail?.let { "$kindText: $it" } ?: kindText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            PrimaryButton(text = stringResource(R.string.common_retry), onClick = onRetry)
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
            Text(text = stringResource(R.string.yt_featured), style = MaterialTheme.typography.titleMedium)
            val status = state.status
            if (!state.available && status != null) {
                Text(
                    text = "  ⚠ ${featuredStatusText(status)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    text = "  " + stringResource(R.string.yt_featured_hint),
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
                    text = if (item.isDefault) {
                        stringResource(R.string.yt_featured_builtin)
                    } else {
                        stringResource(R.string.yt_featured)
                    },
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
                val videosText = pluralStringResource(
                    R.plurals.yt_video_count,
                    playlist.itemCount,
                    playlist.itemCount,
                )
                Text(
                    text = if (featured) {
                        stringResource(R.string.yt_featured_video_format, videosText)
                    } else {
                        videosText
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}
