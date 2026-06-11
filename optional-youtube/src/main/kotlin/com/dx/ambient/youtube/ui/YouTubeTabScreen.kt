package com.dx.ambient.youtube.ui

import android.accounts.AccountManager
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    // Featured playlists live on the Home screen only; the tab keeps just the
    // long-press feature/unfeature toggle on the account grid.
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

    // System Google account chooser for "Switch account".
    val accountPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (result.resultCode == Activity.RESULT_OK) viewModel.onAccountChosen(email)
    }
    LaunchedEffect(Unit) {
        viewModel.accountPickerRequests.collect { intent ->
            runCatching { accountPickerLauncher.launch(intent) }
        }
    }

    BackHandler { onBack() }

    AmbientScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(rememberScreenPadding())) {
            SectionHeader(
                title = stringResource(R.string.youtube_label),
                subtitle = stringResource(R.string.yt_subtitle),
            )

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
                    AccountHeader(state = s, onSwitchAccount = viewModel::switchAccount)
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

/** Signed-in identity row: channel avatar + name (or pinned email) and a switch-account action. */
@Composable
private fun AccountHeader(
    state: YouTubeUiState.Playlists,
    onSwitchAccount: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        if (state.channelThumbnailUrl != null) {
            AsyncImage(
                model = state.channelThumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(32.dp)
                    .aspectRatio(1f)
                    .clip(CircleShape),
            )
        }
        val identity = state.channelTitle?.takeIf { it.isNotBlank() }
            ?: state.accountEmail
        Text(
            text = if (identity != null) {
                stringResource(R.string.yt_signed_in_as, identity)
            } else {
                stringResource(R.string.yt_signed_in)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        PrimaryButton(
            text = stringResource(R.string.yt_switch_account),
            onClick = onSwitchAccount,
        )
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
