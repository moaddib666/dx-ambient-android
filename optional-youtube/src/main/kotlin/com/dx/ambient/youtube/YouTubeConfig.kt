package com.dx.ambient.youtube

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * YouTube feature configuration and availability gating.
 *
 * - [hasGooglePlayServices] gates the whole feature (absent on China-market projectors).
 * - [isConfigured] is true once a Web OAuth client ID has been set in `youtube_config.xml`.
 */
class YouTubeConfig @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val webClientId: String
        get() = context.getString(R.string.youtube_web_client_id).trim()

    val isConfigured: Boolean
        get() = webClientId.isNotEmpty()

    val hasGooglePlayServices: Boolean
        get() = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}
