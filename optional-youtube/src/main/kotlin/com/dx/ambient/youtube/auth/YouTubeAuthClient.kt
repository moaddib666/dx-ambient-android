package com.dx.ambient.youtube.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.AccountPicker
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Obtains a `youtube.readonly` OAuth access token via the Play Services Authorization API.
 *
 * The flow can complete silently (already-granted) or require a one-time consent screen, which
 * the caller must launch as an Activity result — hence the [AuthOutcome.NeedsConsent] branch.
 *
 * The user can pin a specific Google account (see [setAccount] / [chooseAccountIntent]); when
 * none is pinned, Play Services picks its default. Stale cached access tokens (the Data API
 * answers 401 while the embed still plays) are dropped via [invalidateToken] so the next
 * [authorize] mints a fresh one.
 */
class YouTubeAuthClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val authorizationClient = Identity.getAuthorizationClient(context)

    private fun buildRequest(accountEmail: String?): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(YOUTUBE_READONLY_SCOPE)))
            .apply {
                if (!accountEmail.isNullOrBlank()) {
                    setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
                }
            }
            .build()

    sealed interface AuthOutcome {
        data class Token(val accessToken: String) : AuthOutcome
        data class NeedsConsent(val intentSender: IntentSender) : AuthOutcome
        data class Failure(val message: String) : AuthOutcome
    }

    /** The Google account the user explicitly picked, or null for the Play Services default. */
    val accountEmail: Flow<String?> =
        context.youtubeAuthDataStore.data.map { it[ACCOUNT_EMAIL_KEY]?.takeIf { e -> e.isNotBlank() } }

    /** Pins (or with null clears) the Google account used for authorization. */
    suspend fun setAccount(email: String?) {
        context.youtubeAuthDataStore.edit { prefs ->
            if (email.isNullOrBlank()) prefs.remove(ACCOUNT_EMAIL_KEY) else prefs[ACCOUNT_EMAIL_KEY] = email
        }
    }

    /** System account chooser limited to Google accounts; result extra is the account name. */
    fun chooseAccountIntent(): Intent =
        AccountPicker.newChooseAccountIntent(
            AccountPicker.AccountChooserOptions.Builder()
                .setAllowableAccountsTypes(listOf(GOOGLE_ACCOUNT_TYPE))
                .build(),
        )

    /** Requests authorization; returns a token, or a consent intent to launch, or a failure. */
    suspend fun authorize(): AuthOutcome = try {
        val pinnedAccount = accountEmail.first()
        val result = authorizationClient.authorize(buildRequest(pinnedAccount)).await()
        when {
            result.hasResolution() -> {
                val sender = result.pendingIntent?.intentSender
                if (sender != null) AuthOutcome.NeedsConsent(sender)
                else AuthOutcome.Failure("Consent required but no intent was provided")
            }
            result.accessToken != null -> AuthOutcome.Token(result.accessToken!!)
            else -> AuthOutcome.Failure("No access token returned")
        }
    } catch (e: Exception) {
        AuthOutcome.Failure(e.message ?: "Authorization failed")
    }

    /** Extracts the access token from the consent Activity result. */
    fun tokenFromConsentResult(data: Intent?): AuthOutcome = try {
        val result = authorizationClient.getAuthorizationResultFromIntent(data)
        result.accessToken?.let { AuthOutcome.Token(it) }
            ?: AuthOutcome.Failure("Consent completed but no access token was returned")
    } catch (e: Exception) {
        AuthOutcome.Failure(e.message ?: "Authorization failed")
    }

    /**
     * Drops a stale access token from the Play Services cache. The Authorization API
     * sometimes keeps serving an expired token (the Data API answers 401); clearing it
     * forces the next [authorize] to mint a fresh one.
     */
    suspend fun invalidateToken(token: String) {
        withContext(Dispatchers.IO) {
            runCatching { GoogleAuthUtil.clearToken(context, token) }
        }
    }

    /**
     * Runs [block] with a silently obtained access token; on an HTTP 401 the cached token is
     * invalidated and the call retried once with a fresh one. Returns null when no token is
     * silently available (signed out / consent pending) — callers fall back to their flows.
     */
    suspend fun <T> withFreshToken(block: suspend (String) -> T): T? {
        val first = authorize() as? AuthOutcome.Token ?: return null
        return try {
            block(first.accessToken)
        } catch (e: Exception) {
            if (!e.isUnauthorized()) throw e
            invalidateToken(first.accessToken)
            val second = authorize() as? AuthOutcome.Token ?: throw e
            block(second.accessToken)
        }
    }

    companion object {
        const val YOUTUBE_READONLY_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}

private fun Exception.isUnauthorized(): Boolean =
    this is com.dx.ambient.youtube.data.YouTubeHttpException && code == 401

private val Context.youtubeAuthDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "youtube_auth",
)

private val ACCOUNT_EMAIL_KEY = stringPreferencesKey("account_email")
