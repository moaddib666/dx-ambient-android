package com.dx.ambient.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilitiesTest {

    private fun caps(
        ramMb: Long,
        sdk: Int = 30,
        supports4k: Boolean = true,
    ) = DeviceCapabilities(
        manufacturer = "m",
        model = "x",
        androidSdkInt = sdk,
        androidRelease = "11",
        totalRamMb = ramMb,
        abis = listOf("arm64-v8a"),
        displayWidthPx = 1920,
        displayHeightPx = 1080,
        supportedRefreshRatesHz = listOf(60f),
        hardwareAvcDecode = true,
        hardwareHevcDecode = true,
        max1080pDecodeInstances = 2,
        supports4kDecode = supports4k,
        hasGooglePlayServices = false,
        isTelevision = true,
    )

    @Test
    fun `low RAM is LOW tier`() {
        assertEquals(DeviceTier.LOW, caps(ramMb = 1024).tier)
    }

    @Test
    fun `old android is LOW tier regardless of RAM`() {
        assertEquals(DeviceTier.LOW, caps(ramMb = 4096, sdk = 23).tier)
    }

    @Test
    fun `mid RAM is MID tier`() {
        assertEquals(DeviceTier.MID, caps(ramMb = 2048).tier)
    }

    @Test
    fun `high RAM is HIGH tier even when the 4K probe fails`() {
        assertEquals(DeviceTier.HIGH, caps(ramMb = 4096, supports4k = false).tier)
    }

    @Test
    fun `masks are recommended on every tier except LOW`() {
        assertFalse(caps(ramMb = 1024).recommendMasks)
        assertTrue(caps(ramMb = 2048).recommendMasks)
        assertTrue(caps(ramMb = 4096).recommendMasks)
    }

    @Test
    fun `tier boundary at 1536MB`() {
        assertEquals(DeviceTier.LOW, caps(ramMb = 1535).tier)
        assertEquals(DeviceTier.MID, caps(ramMb = 1536).tier)
    }

    @Test
    fun `tier boundary at 3072MB`() {
        assertEquals(DeviceTier.MID, caps(ramMb = 3071).tier)
        assertEquals(DeviceTier.HIGH, caps(ramMb = 3072).tier)
    }
}
