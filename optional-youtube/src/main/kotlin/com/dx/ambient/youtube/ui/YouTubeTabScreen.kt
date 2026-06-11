package com.dx.ambient.youtube.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.dx.ambient.youtube.YouTubeMode
import com.dx.ambient.youtube.data.YouTubePlaylist

// TODO(youtube): Remove this hardcoded demo playlist once OAuth sign-in is configured and the
//  tab lists the signed-in user's real playlists (YouTubeViewModel.Playlists). Demo-only path so
//  the IFrame player can be exercised without Google sign-in.
private const val DEMO_PLAYLIST_URL =
    "https://www.youtube.com/playlist?list=PLazDOSmamQaEGEwkFvuuh5jCrmjGfadOr"

/**
 * The YouTube hub. Starts at a login wall ("sign in with your YouTube account") and, once
 * authorized, lists the user's playlists. Selecting one plays it through the official IFrame
 * player. The whole feature is gated to devices with Google Play Services.
 */
@Composable
fun YouTubeTabScreen(
    onPlayPlaylist: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: YouTubeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // TODO(youtube): demo only — replace with the user's real playlists after OAuth.
    val demoPlaylistId = remember { YouTubeMode.extractPlaylistId(DEMO_PLAYLIST_URL) }

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

            when (val s = state) {
                YouTubeUiState.Unsupported -> CenteredMessage(
                    "YouTube needs Google Play Services, which isn't available on this device.",
                )
                YouTubeUiState.NotConfigured -> CenteredMessage(
                    "YouTube sign-in isn't configured yet. Add your OAuth Web client ID in " +
                        "youtube_config.xml to enable it.",
                )
                YouTubeUiState.SignedOut -> SignInPrompt(
                    onSignIn = viewModel::signIn,
                    onPlayDemo = demoPlaylistId?.let { id -> { onPlayPlaylist(id) } },
                )
                YouTubeUiState.Loading -> CenteredMessage("Loading…")
                is YouTubeUiState.Playlists -> {
                    if (s.items.isEmpty()) {
                        CenteredMessage("No playlists found on your account.")
                    } else {
                        PlaylistGrid(playlists = s.items, onPlay = onPlayPlaylist)
                    }
                }
                is YouTubeUiState.Error -> ErrorView(message = s.message, onRetry = viewModel::retry)
            }
        }
    }
}

@Composable
private fun SignInPrompt(onSignIn: () -> Unit, onPlayDemo: (() -> Unit)?) {
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
            // TODO(youtube): demo-only entry — remove once real playlists load after sign-in.
            if (onPlayDemo != null) {
                PrimaryButton(text = "Play demo playlist", onClick = onPlayDemo)
            }
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

@Composable
private fun PlaylistGrid(playlists: List<YouTubePlaylist>, onPlay: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 220.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().padding(top = 16.dp),
    ) {
        items(items = playlists, key = { it.id }) { playlist ->
            PlaylistCard(playlist = playlist, onClick = { onPlay(playlist.id) })
        }
    }
}

@Composable
private fun PlaylistCard(playlist: YouTubePlaylist, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .touchClickable(onClick = onClick),
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
                    text = "${playlist.itemCount} videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}
