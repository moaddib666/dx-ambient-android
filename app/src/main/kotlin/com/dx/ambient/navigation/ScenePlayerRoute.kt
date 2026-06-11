package com.dx.ambient.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dx.ambient.domain.model.MediaSource
import com.dx.ambient.domain.model.MediaSourceType
import com.dx.ambient.domain.model.Scene
import com.dx.ambient.domain.repository.SceneRepository
import com.dx.ambient.domain.repository.SettingsRepository
import com.dx.ambient.feature.scenes.PlayerScreen
import com.dx.ambient.playback.AmbientPlayer
import com.dx.ambient.youtube.YouTubeIFrameScreen
import com.dx.ambient.youtube.YouTubeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Entry point for playing a scene: local/stream scenes render through the regular
 * [PlayerScreen]; scenes whose picture source is YouTube render through the official
 * IFrame player with the scene's mask, brightness, opacity, scale and (optionally) a
 * separate ExoPlayer soundtrack — the embed itself is then muted, never extracted.
 */
@Composable
fun ScenePlayerRoute(
    sceneId: String,
    onExit: () -> Unit,
) {
    val viewModel: SceneRouteViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(sceneId) { viewModel.bind(sceneId) }

    val scene = state.scene
    when {
        state.loading -> Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        scene != null && scene.videoSource.isYouTube ->
            YouTubeSceneScreen(scene = scene, viewModel = viewModel, onExit = onExit)
        else -> PlayerScreen(sceneId = sceneId, onExit = onExit)
    }
}

@Composable
private fun YouTubeSceneScreen(
    scene: Scene,
    viewModel: SceneRouteViewModel,
    onExit: () -> Unit,
) {
    val hasSeparateAudio = scene.audioSource.isPlayableAudio

    // Separate soundtrack (local file or remote stream) plays through AmbientPlayer
    // while the embed is muted; paused with the app, stopped when the screen closes.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(scene.id) {
        if (hasSeparateAudio) viewModel.startSceneAudio(scene)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.pauseSceneAudio()
                Lifecycle.Event.ON_RESUME -> if (hasSeparateAudio) viewModel.resumeSceneAudio()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopSceneAudio()
        }
    }

    YouTubeIFrameScreen(
        videoId = YouTubeMode.extractVideoId(scene.videoSource.uri)
            .takeIf { YouTubeMode.extractPlaylistId(scene.videoSource.uri) == null },
        playlistId = YouTubeMode.extractPlaylistId(scene.videoSource.uri),
        onExit = onExit,
        maskUri = scene.mask.uri.takeIf { scene.hasMask },
        brightness = scene.brightness,
        videoAlpha = scene.videoAlpha,
        videoScale = scene.videoScale,
        muted = scene.muted || hasSeparateAudio,
    )
}

private val MediaSource.isPlayableAudio: Boolean
    get() = type == MediaSourceType.LOCAL_AUDIO || type == MediaSourceType.STREAM

/** Loads the scene to route it to the right player; owns the YouTube-scene soundtrack. */
@HiltViewModel
class SceneRouteViewModel @Inject constructor(
    private val sceneRepository: SceneRepository,
    private val settingsRepository: SettingsRepository,
    private val player: AmbientPlayer,
) : ViewModel() {

    data class State(val scene: Scene? = null, val loading: Boolean = true)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var audioStarted = false

    fun bind(sceneId: String) {
        viewModelScope.launch {
            val scene = sceneRepository.getScene(sceneId)
            // Remember YouTube scenes for resume-on-launch too (PlayerScreen does
            // this itself for the regular path).
            if (scene != null && scene.videoSource.isYouTube) {
                settingsRepository.setLastSceneId(scene.id)
            }
            _state.value = State(scene = scene, loading = false)
        }
    }

    /** Plays only the scene's separate soundtrack — the picture stays in the IFrame. */
    fun startSceneAudio(scene: Scene) {
        player.load(
            scene.copy(
                videoSource = MediaSource.NONE,
                videoPlaylist = emptyList(),
            ),
        )
        audioStarted = true
    }

    fun pauseSceneAudio() {
        if (audioStarted) player.pause()
    }

    fun resumeSceneAudio() {
        if (audioStarted) player.play()
    }

    fun stopSceneAudio() {
        if (audioStarted) player.stop()
        audioStarted = false
    }

    override fun onCleared() {
        stopSceneAudio()
    }
}
