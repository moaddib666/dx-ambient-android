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
import kotlinx.coroutines.flow.firstOrNull
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
    data class Playlists(
        val items: List<YouTubePlaylist>,
        /** The signed-in user's channel name/avatar; null while unknown. */
        val channelTitle: String? = null,
        val channelThumbnailUrl: String? = null,
        /** The pinned Google account email, when the user explicitly chose one. */
        val accountEmail: String? = null,
    ) : YouTubeUiState

    /**
     * The UI maps [kind] to a localized message; [detail] is an optional raw cause
     * (auth/network exception text) appended for diagnostics.
     */
    data class Error(val kind: ErrorKind, val detail: String? = null) : YouTubeUiState {
        enum class ErrorKind { SIGN_IN_FAILED, SIGN_IN_CANCELLED, LOAD_FAILED }
    }
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

    /** Emits the system account-chooser [Intent] when the user asks to switch accounts. */
    private val _accountPickerRequests = Channel<Intent>(Channel.BUFFERED)
    val accountPickerRequests = _accountPickerRequests.receiveAsFlow()

    private var accessToken: String? = null

    init {
        // The Authorization API keeps the grant across app restarts: try a *silent*
        // sign-in up-front so returning users land straight on their playlists
        // instead of a redundant sign-in wall. Consent UI is never launched from
        // here — only an explicit signIn() tap may surface it.
        if (config.hasGooglePlayServices && config.isConfigured) {
            _state.value = YouTubeUiState.Loading
            viewModelScope.launch {
                when (val outcome = auth.authorize()) {
                    is AuthOutcome.Token -> onToken(outcome.accessToken)
                    else -> _state.value = YouTubeUiState.SignedOut
                }
            }
        }
    }

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
                is AuthOutcome.Failure -> _state.value = YouTubeUiState.Error(
                    YouTubeUiState.Error.ErrorKind.SIGN_IN_FAILED,
                    outcome.message,
                )
            }
        }
    }

    fun onConsentResult(data: Intent?) {
        viewModelScope.launch {
            when (val outcome = auth.tokenFromConsentResult(data)) {
                is AuthOutcome.Token -> onToken(outcome.accessToken)
                is AuthOutcome.Failure -> _state.value = YouTubeUiState.Error(
                    YouTubeUiState.Error.ErrorKind.SIGN_IN_FAILED,
                    outcome.message,
                )
                else -> _state.value = YouTubeUiState.Error(
                    YouTubeUiState.Error.ErrorKind.SIGN_IN_CANCELLED,
                )
            }
        }
    }

    fun onConsentCancelled() {
        _state.value = YouTubeUiState.SignedOut
    }

    /** Opens the system Google account chooser so the user can switch accounts. */
    fun switchAccount() {
        viewModelScope.launch { _accountPickerRequests.send(auth.chooseAccountIntent()) }
    }

    /** Pins the chosen account and re-authorizes; null (chooser cancelled) keeps everything. */
    fun onAccountChosen(email: String?) {
        if (email.isNullOrBlank()) return
        viewModelScope.launch {
            accessToken?.let { auth.invalidateToken(it) }
            accessToken = null
            auth.setAccount(email)
            // Re-run sign-in for the new account; first grant may need the consent screen.
            when (val outcome = auth.authorize()) {
                is AuthOutcome.Token -> onToken(outcome.accessToken)
                is AuthOutcome.NeedsConsent -> _consentRequests.send(outcome.intentSender)
                is AuthOutcome.Failure -> _state.value = YouTubeUiState.Error(
                    YouTubeUiState.Error.ErrorKind.SIGN_IN_FAILED,
                    outcome.message,
                )
            }
        }
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
        // A stale cached token makes the Data API answer 401 while the embed still plays.
        // withFreshToken invalidates the cached token on 401 and retries once.
        runCatching {
            auth.withFreshToken { fresh ->
                accessToken = fresh
                val playlists = repository.fetchMyPlaylists(fresh)
                val channel = runCatching { repository.fetchMyChannel(fresh) }.getOrNull()
                playlists to channel
            } ?: (repository.fetchMyPlaylists(token) to null)
        }
            .onSuccess { (playlists, channel) ->
                _state.value = YouTubeUiState.Playlists(
                    items = playlists,
                    channelTitle = channel?.title,
                    channelThumbnailUrl = channel?.thumbnailUrl,
                    accountEmail = auth.accountEmail.firstOrNull(),
                )
            }
            .onFailure {
                _state.value = YouTubeUiState.Error(
                    YouTubeUiState.Error.ErrorKind.LOAD_FAILED,
                    it.message,
                )
            }
    }
}
