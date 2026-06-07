package com.dx.ambient.domain.model

import kotlinx.serialization.Serializable

/**
 * A snapshot of what the host device can realistically do (MVP feature 10).
 *
 * Used both for logging/diagnostics and to drive automatic quality decisions: weak projector
 * builds (e.g. 1GB / Android 6) fall back to simpler 1080p/720p loops with masks disabled,
 * while stronger Android TV / Google TV devices can enable composited masks and 4K.
 */
@Serializable
data class DeviceCapabilities(
    val manufacturer: String,
    val model: String,
    val androidSdkInt: Int,
    val androidRelease: String,
    val totalRamMb: Long,
    val abis: List<String>,

    /** Display info. */
    val displayWidthPx: Int,
    val displayHeightPx: Int,
    val supportedRefreshRatesHz: List<Float>,

    /** Codec/decoder findings. */
    val hardwareAvcDecode: Boolean,
    val hardwareHevcDecode: Boolean,
    val max1080pDecodeInstances: Int,
    val supports4kDecode: Boolean,

    /** Whether Google Play Services is present (absent on most China-market projectors). */
    val hasGooglePlayServices: Boolean,
    val isTelevision: Boolean,
) {
    /**
     * A coarse capability tier used to pick default quality and effects.
     *
     * Note: the 4K-decode probe is best-effort and defaults to false on failure, so it is NOT
     * used to gate the HIGH tier — otherwise a capable 4 GB device whose probe failed would be
     * needlessly downgraded. RAM and OS version are the reliable signals.
     */
    val tier: DeviceTier
        get() = when {
            totalRamMb < 1536 || androidSdkInt < 24 -> DeviceTier.LOW
            totalRamMb < 3072 -> DeviceTier.MID
            else -> DeviceTier.HIGH
        }

    /** Whether composited masks should be offered by default on this device. */
    val recommendMasks: Boolean get() = tier != DeviceTier.LOW
}

@Serializable
enum class DeviceTier {
    /** ~1GB / old Android: 720p–1080p loops, no masks by default. */
    LOW,

    /** ~2GB Android TV 11: 1080p loops, optional masks. */
    MID,

    /** 4GB+ : 4K loops, composited masks. */
    HIGH,
}
