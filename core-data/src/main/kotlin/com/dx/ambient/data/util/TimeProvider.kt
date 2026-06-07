package com.dx.ambient.data.util

import javax.inject.Inject

/** Injectable clock so repositories stay testable. */
interface TimeProvider {
    fun nowEpochMs(): Long
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowEpochMs(): Long = System.currentTimeMillis()
}
