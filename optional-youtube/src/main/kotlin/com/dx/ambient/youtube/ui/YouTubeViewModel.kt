package com.dx.ambient.youtube.ui

import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dx.ambient.youtube.YouTubeConfig
import com.dx.ambient.youtube.auth.YouTubeAuthClient
import com.dx.ambient.youtube.auth.YouTubeAuthClient.AuthOutcome
import com.dx.ambient.youtube.data.YouTubePlaylist
import com.dx.ambient.youtube.data.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of the YouTube hub (login wall → playlists). */
sealed interface YouTubeUiState {
    /** No Google Play Services — the feature can't run here. */
    data object Unsupported : YouTubeUiState

    /** No OAuth Web client ID configured yet (developer setup). */
    data object NotConfigured : YouTubeUiState

    data object SignedOut : YouTubeUiState
    data object Loading : YouTubeUiState
    data class Playlists(val items: List<YouTubePlaylist>) : YouTubeUiState
    data class Error(val message: String) : YouTubeUiState
}

@HiltViewModel
class YouTubeViewModel @Inject constructor(
    private val config: YouTubeConfig,
    private val auth: YouTubeAuthClient,
    private val repository: YouTubeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<YouTubeUiState> = _state.asStateFlow()

    /** Emits an [IntentSender] when the consent screen must be launched by the UI. */
    private val _consentRequests = Channel<IntentSender>(Channel.BUFFERED)
    val consentRequests = _consentRequests.receiveAsFlow()

    private var accessToken: String? = null

    private fun initialState(): YouTubeUiState = when {
        // Gate the whole feature on Play Services; otherwise always start at the login wall so
        // the tab "begins with sign in", even before an OAuth client ID has been configured.
        !config.hasGooglePlayServices -> YouTubeUiState.Unsupported
        else -> YouTubeUiState.SignedOut
    }

    fun signIn() {
        if (_state.value is YouTubeUiState.Unsupported) return
        // Placeholder build: explain the one-time setup only when the user actually tries to sign in.
        if (!config.isConfigured) {
            _state.value = YouTubeUiState.NotConfigured
            return
        }
        _state.value = YouTubeUiState.Loading
        viewModelScope.launch {
            when (val outcome = auth.authorize()) {
                is AuthOutcome.Token -> onToken(outcome.accessToken)
                is AuthOutcome.NeedsConsent -> _consentRequests.send(outcome.intentSender)
                is AuthOutcome.Failure -> _state.value = YouTubeUiState.Error(outcome.message)
            }
        }
    }

    fun onConsentResult(data: Intent?) {
        viewModelScope.launch {
            when (val outcome = auth.tokenFromConsentResult(data)) {
                is AuthOutcome.Token -> onToken(outcome.accessToken)
                else -> _state.value = YouTubeUiState.Error(
                    (outcome as? AuthOutcome.Failure)?.message ?: "Sign-in was cancelled",
                )
            }
        }
    }

    fun onConsentCancelled() {
        _state.value = YouTubeUiState.SignedOut
    }

    fun retry() {
        if (accessToken != null) loadPlaylists() else _state.value = initialState()
    }

    private suspend fun onToken(token: String) {
        accessToken = token
        loadPlaylistsInternal(token)
    }

    private fun loadPlaylists() {
        val token = accessToken
        if (token == null) {
            _state.value = YouTubeUiState.SignedOut
            return
        }
        viewModelScope.launch { loadPlaylistsInternal(token) }
    }

    private suspend fun loadPlaylistsInternal(token: String) {
        _state.value = YouTubeUiState.Loading
        runCatching { repository.fetchMyPlaylists(token) }
            .onSuccess { _state.value = YouTubeUiState.Playlists(it) }
            .onFailure { _state.value = YouTubeUiState.Error(it.message ?: "Failed to load playlists") }
    }
}
