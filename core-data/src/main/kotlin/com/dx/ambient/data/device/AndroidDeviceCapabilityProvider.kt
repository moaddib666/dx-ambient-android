package com.dx.ambient.data.device

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.WindowManager
import com.dx.ambient.domain.model.DeviceCapabilities
import com.dx.ambient.domain.repository.DeviceCapabilityProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Probes the host device (MVP feature 10). Built to run on everything from a 1GB Android 6
 * projector to a 4GB Android 11 box, so every lookup is defensive.
 */
class AndroidDeviceCapabilityProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceCapabilityProvider {

    override suspend fun probe(): DeviceCapabilities = withContext(Dispatchers.Default) {
        val (widthPx, heightPx) = displaySize()
        DeviceCapabilities(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidSdkInt = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE ?: "unknown",
            totalRamMb = totalRamMb(),
            abis = Build.SUPPORTED_ABIS?.toList() ?: emptyList(),
            displayWidthPx = widthPx,
            displayHeightPx = heightPx,
            supportedRefreshRatesHz = supportedRefreshRates(),
            hardwareAvcDecode = hasDecoder("video/avc"),
            hardwareHevcDecode = hasDecoder("video/hevc"),
            max1080pDecodeInstances = maxInstances("video/avc"),
            supports4kDecode = supports4k("video/avc") || supports4k("video/hevc"),
            hasGooglePlayServices = hasGooglePlayServices(),
            isTelevision = isTelevision(),
        )
    }

    private fun totalRamMb(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / (1024 * 1024)
    }

    @Suppress("DEPRECATION")
    private fun displaySize(): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return 0 to 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = android.util.DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }

    @Suppress("DEPRECATION")
    private fun supportedRefreshRates(): List<Float> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return emptyList()
        val display = wm.defaultDisplay ?: return emptyList()
        return display.supportedModes.map { it.refreshRate }.distinct().sorted()
    }

    private fun codecInfos(): List<MediaCodecInfo> =
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        }.getOrDefault(emptyList())

    private fun hasDecoder(mime: String): Boolean =
        codecInfos().any { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }

    private fun maxInstances(mime: String): Int =
        codecInfos().firstNotNullOfOrNull { info ->
            runCatching {
                info.getCapabilitiesForType(mime).maxSupportedInstances.takeIf { it > 0 }
            }.getOrNull()
        } ?: 1

    private fun supports4k(mime: String): Boolean =
        codecInfos().any { info ->
            runCatching {
                val caps = info.getCapabilitiesForType(mime).videoCapabilities ?: return@runCatching false
                caps.isSizeSupported(3840, 2160)
            }.getOrDefault(false)
        }

    private fun hasGooglePlayServices(): Boolean = runCatching {
        context.packageManager.getPackageInfo("com.google.android.gms", 0)
        true
    }.getOrDefault(false)

    private fun isTelevision(): Boolean {
        val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        val pm = context.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }
}
