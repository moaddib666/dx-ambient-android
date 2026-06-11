package com.dx.ambient.youtube.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dx.ambient.youtube.auth.YouTubeAuthClient
import com.dx.ambient.youtube.data.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Resolves a playlist into concrete video ids before the IFrame player starts.
 *
 * The anonymous WebView embed cannot open private/unlisted playlists by id
 * (YouTube answers with embed error 150/152 — "video not reachable"), but the
 * signed-in OAuth token *can* read them. Authorization is silent here: consent
 * was already granted on the playlists screen, so `authorize()` returns a token
 * without UI. When resolution isn't possible (no grant, network failure) the
 * player falls back to the plain `listType: 'playlist'` embed, which still
 * works for public playlists.
 */
@HiltViewModel
class YouTubePlayerViewModel @Inject constructor(
    private val auth: YouTubeAuthClient,
    private val repository: YouTubeRepository,
) : ViewModel() {

    sealed interface Resolution {
        data object Resolving : Resolution

        /** [videoIds] is null when resolution failed and the embed fallback should be used. */
        data class Ready(val videoIds: List<String>?) : Resolution
    }

    private val _resolution = MutableStateFlow<Resolution>(Resolution.Resolving)
    val resolution: StateFlow<Resolution> = _resolution.asStateFlow()

    fun resolve(playlistId: String) {
        if (_resolution.value !is Resolution.Resolving) return
        viewModelScope.launch {
            // withFreshToken retries once on HTTP 401 (stale cached token) before giving up.
            val ids = runCatching {
                auth.withFreshToken { token ->
                    repository.fetchPlaylistVideoIds(token, playlistId)
                }?.takeIf { it.isNotEmpty() }
            }.onFailure {
                Log.w(TAG, "Couldn't resolve playlist $playlistId via Data API; falling back to embed", it)
            }.getOrNull()
            _resolution.value = Resolution.Ready(ids)
        }
    }

    private companion object {
        const val TAG = "YouTubePlayerViewModel"
    }
}
