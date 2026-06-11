package com.dx.ambient.youtube.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dx.ambient.youtube.YouTubeConfig
import com.dx.ambient.youtube.auth.YouTubeAuthClient
import com.dx.ambient.youtube.auth.YouTubeAuthClient.AuthOutcome
import com.dx.ambient.youtube.data.YouTubePlaylist
import com.dx.ambient.youtube.featured.FeaturedPlaylist
import com.dx.ambient.youtube.featured.FeaturedPlaylistsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the featured-playlists rail (Home screen + YouTube tab).
 *
 * Featured entries are always listed; they are [available] (playable) only when
 * the device is online AND a silent YouTube authorization succeeds. Otherwise
 * the UI greys them out with [statusHint] explaining what's missing.
 */
data class FeaturedUiState(
    val items: List<FeaturedPlaylist> = emptyList(),
    val featuredIds: Set<String> = emptySet(),
    val available: Boolean = false,
    val statusHint: String? = null,
)

@HiltViewModel
class YouTubeFeaturedViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: YouTubeConfig,
    private val auth: YouTubeAuthClient,
    private val repository: FeaturedPlaylistsRepository,
) : ViewModel() {

    private data class Availability(val available: Boolean, val hint: String?)

    private val availability = MutableStateFlow(Availability(available = false, hint = null))

    val state: StateFlow<FeaturedUiState> =
        combine(repository.featured, availability) { items, avail ->
            FeaturedUiState(
                items = items,
                featuredIds = items.map { it.playlistId }.toSet(),
                available = avail.available,
                statusHint = avail.hint,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FeaturedUiState(items = FeaturedPlaylistsRepository.DEFAULTS),
        )

    init {
        refresh()
    }

    /** Re-checks connectivity + silent sign-in (no consent UI is ever launched here). */
    fun refresh() {
        viewModelScope.launch {
            availability.value = when {
                !config.hasGooglePlayServices || !config.isConfigured ->
                    Availability(false, "YouTube isn't available on this device")
                !isOnline() ->
                    Availability(false, "No internet connection")
                !silentlySignedIn() ->
                    Availability(false, "YouTube sign-in required")
                else -> Availability(true, null)
            }
        }
    }

    /** Features/unfeatures a playlist from the YouTube tab. Defaults stay pinned forever. */
    fun toggleFeatured(playlist: YouTubePlaylist) {
        viewModelScope.launch {
            if (repository.isFeatured(playlist.id)) {
                repository.remove(playlist.id)
            } else {
                repository.add(playlist.id, playlist.title, playlist.thumbnailUrl)
            }
        }
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun silentlySignedIn(): Boolean =
        runCatching { auth.authorize() is AuthOutcome.Token }.getOrDefault(false)
}
