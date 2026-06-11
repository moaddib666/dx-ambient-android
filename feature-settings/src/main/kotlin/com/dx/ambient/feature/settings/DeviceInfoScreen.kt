package com.dx.ambient.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.dx.ambient.domain.model.DeviceCapabilities
import com.dx.ambient.rendering.R
import com.dx.ambient.rendering.components.EmptyState
import com.dx.ambient.rendering.components.SectionHeader
import com.dx.ambient.rendering.components.rememberScreenPadding

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
            title = stringResource(R.string.device_probing_title),
            message = stringResource(R.string.device_probing_message),
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(rememberScreenPadding()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.settings_device_info),
                subtitle = stringResource(R.string.device_info_subtitle),
            )
        }

        items(deviceInfoRowSpecs()) { (labelRes, value) ->
            InfoRow(label = stringResource(labelRes), value = value(caps))
        }
    }
}

/**
 * Flattens [DeviceCapabilities] (plus derived fields) into ordered rows of a label
 * resource and a composable value formatter, so every displayed string is localized.
 */
private fun deviceInfoRowSpecs(): List<Pair<Int, @Composable (DeviceCapabilities) -> String>> = listOf(
    R.string.device_label_manufacturer to { it.manufacturer },
    R.string.device_label_model to { it.model },
    R.string.device_label_android_sdk to { it.androidSdkInt.toString() },
    R.string.device_label_android_release to { it.androidRelease },
    R.string.device_label_total_ram to { stringResource(R.string.device_value_mb, it.totalRamMb) },
    R.string.device_label_abis to { it.abis.joinToString(", ").ifEmpty { "—" } },
    R.string.device_label_display_size to {
        stringResource(R.string.device_value_display, it.displayWidthPx, it.displayHeightPx)
    },
    R.string.device_label_refresh_rates to { c ->
        c.supportedRefreshRatesHz
            .map { stringResource(R.string.device_value_hz, it.toString()) }
            .joinToString(", ")
            .ifEmpty { "—" }
    },
    R.string.device_label_avc to { it.hardwareAvcDecode.yesNo() },
    R.string.device_label_hevc to { it.hardwareHevcDecode.yesNo() },
    R.string.device_label_max_1080p to { it.max1080pDecodeInstances.toString() },
    R.string.device_label_4k to { it.supports4kDecode.yesNo() },
    R.string.device_label_play_services to { it.hasGooglePlayServices.yesNo() },
    R.string.device_label_is_tv to { it.isTelevision.yesNo() },
    R.string.device_label_tier to { it.tier.name },
    R.string.device_label_recommend_masks to { it.recommendMasks.yesNo() },
)

@Composable
private fun Boolean.yesNo(): String =
    if (this) stringResource(R.string.device_value_yes) else stringResource(R.string.device_value_no)

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
