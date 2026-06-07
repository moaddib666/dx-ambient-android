package com.dx.ambient.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.dx.ambient.domain.model.DeviceCapabilities
import com.dx.ambient.rendering.components.EmptyState
import com.dx.ambient.rendering.components.ScreenPadding
import com.dx.ambient.rendering.components.SectionHeader

/**
 * Device capability diagnostics screen (MVP feature 10).
 *
 * Triggers a probe via [DeviceInfoViewModel] on first composition, shows a loading state while
 * it runs, then lists every [DeviceCapabilities] field plus the derived tier and mask
 * recommendation. The probe result is also logged for offline diagnostics.
 *
 * @param onBack invoked on the remote BACK gesture.
 */
@Composable
fun DeviceInfoScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }

    val viewModel: DeviceInfoViewModel = hiltViewModel()
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    val caps = uiState.capabilities
    if (uiState.loading || caps == null) {
        EmptyState(
            modifier = modifier,
            title = "Probing device…",
            message = "Reading capabilities (feature 10)",
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SectionHeader(
                title = "Device info & capabilities",
                subtitle = "What this device can realistically do (feature 10)",
            )
        }

        DeviceInfoRows(caps).forEach { (label, value) ->
            item { InfoRow(label = label, value = value) }
        }
    }
}

/** Flattens [DeviceCapabilities] (plus derived fields) into ordered label/value pairs. */
private fun DeviceInfoRows(caps: DeviceCapabilities): List<Pair<String, String>> = listOf(
    "Manufacturer" to caps.manufacturer,
    "Model" to caps.model,
    "Android SDK" to caps.androidSdkInt.toString(),
    "Android release" to caps.androidRelease,
    "Total RAM" to "${caps.totalRamMb} MB",
    "ABIs" to caps.abis.joinToString(", ").ifEmpty { "—" },
    "Display size" to "${caps.displayWidthPx} × ${caps.displayHeightPx} px",
    "Refresh rates" to caps.supportedRefreshRatesHz
        .joinToString(", ") { "$it Hz" }
        .ifEmpty { "—" },
    "Hardware AVC decode" to caps.hardwareAvcDecode.yesNo(),
    "Hardware HEVC decode" to caps.hardwareHevcDecode.yesNo(),
    "Max 1080p decode instances" to caps.max1080pDecodeInstances.toString(),
    "Supports 4K decode" to caps.supports4kDecode.yesNo(),
    "Google Play Services" to caps.hasGooglePlayServices.yesNo(),
    "Is television" to caps.isTelevision.yesNo(),
    "Tier" to caps.tier.name,
    "Recommend masks" to caps.recommendMasks.yesNo(),
)

private fun Boolean.yesNo(): String = if (this) "Yes" else "No"

@Composable
private fun InfoRow(label: String, value: String) {
    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Text(text = value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
