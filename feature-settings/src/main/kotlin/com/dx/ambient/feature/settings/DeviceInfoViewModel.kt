package com.dx.ambient.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dx.ambient.domain.model.DeviceCapabilities
import com.dx.ambient.domain.repository.DeviceCapabilityProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the device-capabilities diagnostics screen (MVP feature 10).
 *
 * While [loading] is true the probe is still running; once it completes [capabilities]
 * holds the snapshot used for both display and logging.
 */
data class DeviceInfoUiState(
    val loading: Boolean = true,
    val capabilities: DeviceCapabilities? = null,
    val error: String? = null,
)

/**
 * Probes the host device on creation and logs the result (MVP feature 10).
 *
 * The probe is potentially expensive, so it runs once in [init] on [viewModelScope]; the
 * resulting [DeviceCapabilities] is logged under the `DeviceCapabilities` tag for diagnostics
 * and exposed for the screen to render.
 */
@HiltViewModel
class DeviceInfoViewModel @Inject constructor(
    private val deviceCapabilityProvider: DeviceCapabilityProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(DeviceInfoUiState())

    /** Loading flag plus the probed capabilities (null until the probe completes). */
    val state: StateFlow<DeviceInfoUiState> = _state.asStateFlow()

    init {
        probe()
    }

    /** Runs (or retries) the capability probe. */
    fun probe() {
        _state.value = DeviceInfoUiState(loading = true)
        viewModelScope.launch {
            runCatching { deviceCapabilityProvider.probe() }
                .onSuccess { caps ->
                    Log.i("DeviceCapabilities", caps.toString())
                    _state.value = DeviceInfoUiState(loading = false, capabilities = caps)
                }
                .onFailure { e ->
                    Log.w("DeviceCapabilities", "probe failed", e)
                    _state.value = DeviceInfoUiState(loading = false, error = e.message ?: "Probe failed")
                }
        }
    }
}
