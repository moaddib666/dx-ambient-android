package com.dx.ambient.feature.settings

import com.dx.ambient.domain.model.DeviceCapabilities
import com.dx.ambient.domain.repository.DeviceCapabilityProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class DeviceInfoViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sampleCaps = DeviceCapabilities(
        manufacturer = "Dangbei", model = "X5 Pro", androidSdkInt = 30, androidRelease = "11",
        totalRamMb = 4096, abis = listOf("arm64-v8a"), displayWidthPx = 3840, displayHeightPx = 2160,
        supportedRefreshRatesHz = listOf(60f), hardwareAvcDecode = true, hardwareHevcDecode = true,
        max1080pDecodeInstances = 4, supports4kDecode = true, hasGooglePlayServices = false,
        isTelevision = true,
    )

    @Test
    fun `successful probe exposes capabilities and clears loading`() = runTest {
        val vm = DeviceInfoViewModel(object : DeviceCapabilityProvider {
            override suspend fun probe() = sampleCaps
        })
        assertFalse(vm.state.value.loading)
        assertEquals(sampleCaps, vm.state.value.capabilities)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `failed probe surfaces an error instead of hanging on loading`() = runTest {
        val vm = DeviceInfoViewModel(object : DeviceCapabilityProvider {
            override suspend fun probe(): DeviceCapabilities = throw RuntimeException("no window manager")
        })
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.capabilities)
        assertNotNull(vm.state.value.error)
    }
}
