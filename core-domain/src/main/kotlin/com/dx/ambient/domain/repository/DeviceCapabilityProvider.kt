package com.dx.ambient.domain.repository

import com.dx.ambient.domain.model.DeviceCapabilities

/** Probes the host device for its capabilities (MVP feature 10). */
interface DeviceCapabilityProvider {
    /** Reads current device capabilities. May be relatively expensive; cache the result. */
    suspend fun probe(): DeviceCapabilities
}
