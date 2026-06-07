package com.dx.ambient.feature.settings

import com.dx.ambient.domain.model.ProjectorSettings
import com.dx.ambient.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeSettingsRepository : SettingsRepository {
        val settings = MutableStateFlow(ProjectorSettings())
        override fun observeSettings(): Flow<ProjectorSettings> = settings
        override suspend fun update(transform: (ProjectorSettings) -> ProjectorSettings) {
            settings.value = transform(settings.value)
        }
        override suspend fun setLastSceneId(sceneId: String?) {
            settings.value = settings.value.copy(lastSceneId = sceneId)
        }
    }

    @Test
    fun `update applies a copy-on-write transform to persisted settings`() = runTest {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        assertTrue(repo.settings.value.masksEnabled)
        vm.update { it.copy(masksEnabled = false, sleepTimerMinutes = 30) }
        assertFalse(repo.settings.value.masksEnabled)
        assertEquals(30, repo.settings.value.sleepTimerMinutes)
    }
}
