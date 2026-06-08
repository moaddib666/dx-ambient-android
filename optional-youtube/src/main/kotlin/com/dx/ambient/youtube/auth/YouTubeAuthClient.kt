package com.dx.ambient.youtube.auth

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Obtains a `youtube.readonly` OAuth access token via the Play Services Authorization API.
 *
 * The flow can complete silently (already-granted) or require a one-time consent screen, which
 * the caller must launch as an Activity result — hence the [AuthOutcome.NeedsConsent] branch.
 */
class YouTubeAuthClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val authorizationClient = Identity.getAuthorizationClient(context)

    private val request: AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(YOUTUBE_READONLY_SCOPE)))
            .build()

    sealed interface AuthOutcome {
        data class Token(val accessToken: String) : AuthOutcome
        data class NeedsConsent(val intentSender: IntentSender) : AuthOutcome
        data class Failure(val message: String) : AuthOutcome
    }

    /** Requests authorization; returns a token, or a consent intent to launch, or a failure. */
    suspend fun authorize(): AuthOutcome = try {
        val result = authorizationClient.authorize(request).await()
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

    companion object {
        const val YOUTUBE_READONLY_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
    }
}
