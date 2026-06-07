package com.dx.ambient.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dx.ambient.domain.model.ProjectorSettings
import com.dx.ambient.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override fun observeSettings(): Flow<ProjectorSettings> =
        dataStore.data.map { it.toSettings() }

    override suspend fun update(transform: (ProjectorSettings) -> ProjectorSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[Keys.MASKS_ENABLED] = updated.masksEnabled
            prefs[Keys.PERF_SAFE] = updated.performanceSafeMode
            prefs[Keys.DIM_AFTER] = updated.dimAfterMinutes
            prefs[Keys.DIM_BRIGHTNESS] = updated.dimBrightness
            prefs[Keys.SLEEP_TIMER] = updated.sleepTimerMinutes
            prefs[Keys.BURN_IN] = updated.burnInProtection
            prefs[Keys.RESUME_LAST] = updated.resumeLastSceneOnLaunch
            updated.lastSceneId?.let { prefs[Keys.LAST_SCENE] = it } ?: prefs.remove(Keys.LAST_SCENE)
        }
    }

    override suspend fun setLastSceneId(sceneId: String?) {
        dataStore.edit { prefs ->
            if (sceneId == null) prefs.remove(Keys.LAST_SCENE) else prefs[Keys.LAST_SCENE] = sceneId
        }
    }

    private fun Preferences.toSettings(): ProjectorSettings {
        val defaults = ProjectorSettings()
        return ProjectorSettings(
            masksEnabled = this[Keys.MASKS_ENABLED] ?: defaults.masksEnabled,
            performanceSafeMode = this[Keys.PERF_SAFE] ?: defaults.performanceSafeMode,
            dimAfterMinutes = this[Keys.DIM_AFTER] ?: defaults.dimAfterMinutes,
            dimBrightness = this[Keys.DIM_BRIGHTNESS] ?: defaults.dimBrightness,
            sleepTimerMinutes = this[Keys.SLEEP_TIMER] ?: defaults.sleepTimerMinutes,
            burnInProtection = this[Keys.BURN_IN] ?: defaults.burnInProtection,
            resumeLastSceneOnLaunch = this[Keys.RESUME_LAST] ?: defaults.resumeLastSceneOnLaunch,
            lastSceneId = this[Keys.LAST_SCENE],
        )
    }

    private object Keys {
        val MASKS_ENABLED = booleanPreferencesKey("masks_enabled")
        val PERF_SAFE = booleanPreferencesKey("performance_safe_mode")
        val DIM_AFTER = intPreferencesKey("dim_after_minutes")
        val DIM_BRIGHTNESS = floatPreferencesKey("dim_brightness")
        val SLEEP_TIMER = intPreferencesKey("sleep_timer_minutes")
        val BURN_IN = booleanPreferencesKey("burn_in_protection")
        val RESUME_LAST = booleanPreferencesKey("resume_last_scene")
        val LAST_SCENE = stringPreferencesKey("last_scene_id")
    }
}
