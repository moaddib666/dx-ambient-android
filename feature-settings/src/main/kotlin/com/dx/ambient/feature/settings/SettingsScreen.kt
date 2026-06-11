package com.dx.ambient.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import com.dx.ambient.domain.model.ProjectorSettings
import com.dx.ambient.rendering.R
import com.dx.ambient.rendering.components.PrimaryButton
import com.dx.ambient.rendering.components.SectionHeader
import com.dx.ambient.rendering.components.rememberScreenPadding
import com.dx.ambient.rendering.components.touchClickable

/**
 * Global projector settings screen (MVP features 8 & 9).
 *
 * Renders every [ProjectorSettings] field as a TV-remote-friendly row: booleans flip an
 * ON/OFF state on click, numeric values are stepped with `-` / `+` buttons. Every edit is
 * persisted via [SettingsViewModel.update]. A trailing row navigates to device diagnostics.
 *
 * @param onOpenDeviceInfo invoked when the user selects the device-info row (MVP feature 10).
 * @param onBack invoked on the remote BACK gesture.
 */
@Composable
fun SettingsScreen(
    onOpenDeviceInfo: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }

    val viewModel: SettingsViewModel = hiltViewModel()
    val settings by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(rememberScreenPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_subtitle),
            )
        }

        item {
            ToggleRow(
                label = stringResource(R.string.settings_masks_overlays),
                value = settings.masksEnabled,
            ) { checked -> viewModel.update { it.copy(masksEnabled = checked) } }
        }

        item {
            ToggleRow(
                label = stringResource(R.string.settings_performance_safe),
                value = settings.performanceSafeMode,
            ) { checked -> viewModel.update { it.copy(performanceSafeMode = checked) } }
        }

        item {
            ToggleRow(
                label = stringResource(R.string.settings_burn_in),
                value = settings.burnInProtection,
            ) { checked -> viewModel.update { it.copy(burnInProtection = checked) } }
        }

        item {
            ToggleRow(
                label = stringResource(R.string.settings_resume_last),
                value = settings.resumeLastSceneOnLaunch,
            ) { checked -> viewModel.update { it.copy(resumeLastSceneOnLaunch = checked) } }
        }

        item {
            StepperRow(
                label = stringResource(R.string.settings_auto_dim_after),
                valueText = if (settings.dimAfterMinutes == 0) {
                    stringResource(R.string.value_off)
                } else {
                    stringResource(R.string.minutes_format, settings.dimAfterMinutes)
                },
                onDecrease = {
                    viewModel.update {
                        it.copy(dimAfterMinutes = (it.dimAfterMinutes - 5).coerceIn(0, 120))
                    }
                },
                onIncrease = {
                    viewModel.update {
                        it.copy(dimAfterMinutes = (it.dimAfterMinutes + 5).coerceIn(0, 120))
                    }
                },
            )
        }

        item {
            StepperRow(
                label = stringResource(R.string.settings_dim_level),
                valueText = stringResource(
                    R.string.common_percent,
                    (settings.dimBrightness * 100).toInt(),
                ),
                onDecrease = {
                    viewModel.update {
                        it.copy(dimBrightness = (it.dimBrightness - 0.05f).coerceIn(0f, 1f))
                    }
                },
                onIncrease = {
                    viewModel.update {
                        it.copy(dimBrightness = (it.dimBrightness + 0.05f).coerceIn(0f, 1f))
                    }
                },
            )
        }

        item {
            StepperRow(
                label = stringResource(R.string.settings_sleep_timer),
                valueText = if (settings.sleepTimerMinutes == 0) {
                    stringResource(R.string.value_off)
                } else {
                    stringResource(R.string.minutes_format, settings.sleepTimerMinutes)
                },
                onDecrease = {
                    viewModel.update {
                        it.copy(sleepTimerMinutes = (it.sleepTimerMinutes - 10).coerceIn(0, 240))
                    }
                },
                onIncrease = {
                    viewModel.update {
                        it.copy(sleepTimerMinutes = (it.sleepTimerMinutes + 10).coerceIn(0, 240))
                    }
                },
            )
        }

        item {
            // Single clickable (the Card) — no nested focusable ListItem, so the D-pad lands on
            // exactly one focus target for this row.
            Card(
                onClick = onOpenDeviceInfo,
                modifier = Modifier
                    .fillMaxWidth()
                    .touchClickable(onClick = onOpenDeviceInfo),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_device_info),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.settings_device_info_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

/**
 * A boolean settings row rendered as a clickable [Card] showing an ON/OFF state that flips on
 * click (TV remote friendly — no Switch, which is unavailable in Compose for TV).
 */
@Composable
private fun ToggleRow(
    label: String,
    value: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        onClick = { onToggle(!value) },
        modifier = Modifier
            .fillMaxWidth()
            .touchClickable(onClick = { onToggle(!value) }),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (value) {
                    stringResource(R.string.common_on)
                } else {
                    stringResource(R.string.common_off)
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (value) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                },
            )
        }
    }
}

/**
 * A numeric settings row showing the current value plus `-` / `+` stepper buttons. Bounds and
 * step size are enforced by the caller's update lambdas.
 */
@Composable
private fun StepperRow(
    label: String,
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrimaryButton(text = "-", onClick = onDecrease)
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.width(96.dp),
                )
                PrimaryButton(text = "+", onClick = onIncrease)
            }
        }
    }
}
