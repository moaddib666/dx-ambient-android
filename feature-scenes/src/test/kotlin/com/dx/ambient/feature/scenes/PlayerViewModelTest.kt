package com.dx.ambient.feature.scenes

import com.dx.ambient.domain.catalog.MaskCatalog
import com.dx.ambient.domain.model.Mask
import com.dx.ambient.domain.playback.PlaybackState
import com.dx.ambient.domain.playback.PlaybackStatus
import com.dx.ambient.domain.usecase.ResolveEffectiveSceneUseCase
import com.dx.ambient.playback.AmbientPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class PlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(status: PlaybackStatus): Pair<PlayerViewModel, AmbientPlayer> {
        val player = mockk<AmbientPlayer>(relaxed = true)
        every { player.state } returns MutableStateFlow(PlaybackState(status = status))
        val vm = PlayerViewModel(
            sceneRepository = FakeSceneRepository(),
            settingsRepository = FakeSettingsRepository(),
            resolveEffectiveScene = ResolveEffectiveSceneUseCase(),
            maskCatalog = object : MaskCatalog {
                override suspend fun masks(): List<Mask> = emptyList()
            },
            player = player,
        )
        return vm to player
    }

    @Test
    fun `togglePlay pauses when currently playing`() {
        val (vm, player) = viewModel(PlaybackStatus.PLAYING)
        vm.togglePlay()
        verify { player.pause() }
    }

    @Test
    fun `togglePlay plays when not currently playing`() {
        val (vm, player) = viewModel(PlaybackStatus.PAUSED)
        vm.togglePlay()
        verify { player.play() }
    }

    @Test
    fun `onStop stops playback`() {
        val (vm, player) = viewModel(PlaybackStatus.PLAYING)
        vm.onStop()
        verify { player.stop() }
    }
}
