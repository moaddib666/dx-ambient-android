package com.dx.ambient.domain.model

import kotlinx.serialization.Serializable

/**
 * Global, projector-oriented settings (MVP features 8 & 9).
 */
@Serializable
data class ProjectorSettings(
    /** Master switch for masks/overlays. When false, ALL scenes render on the fast path. */
    val masksEnabled: Boolean = true,

    /** Force the performance-safe path regardless of per-scene settings (feature 8). */
    val performanceSafeMode: Boolean = false,

    /** Auto-dim to [dimBrightness] after this many minutes of playback. 0 disables (feature 9). */
    val dimAfterMinutes: Int = 0,
    val dimBrightness: Float = 0.25f,

    /** Auto-stop playback after this many minutes. 0 disables (feature 9). */
    val sleepTimerMinutes: Int = 0,

    /** Burn-in mitigation: subtly shift the image over time (projector/OLED safety). */
    val burnInProtection: Boolean = true,

    /** Re-open the last active scene on app launch / boot. */
    val resumeLastSceneOnLaunch: Boolean = true,

    /** Id of the scene to restore on launch, if any. */
    val lastSceneId: String? = null,

    /** True once the app has seeded its bundled default scenes (e.g. Digital Campfire). */
    val defaultsSeeded: Boolean = false,

    /**
     * Ids of bundled default scenes that have already been seeded. Lets newly-shipped defaults
     * appear on existing installs without resurrecting ones the user deliberately deleted.
     */
    val seededDefaultIds: Set<String> = emptySet(),
) {
    val hasSleepTimer: Boolean get() = sleepTimerMinutes > 0
    val hasAutoDim: Boolean get() = dimAfterMinutes > 0
}
