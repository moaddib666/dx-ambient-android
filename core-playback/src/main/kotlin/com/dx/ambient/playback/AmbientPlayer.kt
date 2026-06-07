package com.dx.ambient.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.dx.ambient.domain.model.LoopMode
import com.dx.ambient.domain.model.MediaSourceType
import com.dx.ambient.domain.model.Scene
import com.dx.ambient.domain.playback.PlaybackController
import com.dx.ambient.domain.playback.PlaybackState
import com.dx.ambient.domain.playback.PlaybackStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Media3 ExoPlayer implementation of [PlaybackController].
 *
 * Uses two players: [videoPlayer] for the picture-with-its-own-audio and [audioPlayer] for an
 * optional separate soundtrack (e.g. fireplace video + rain audio). When a separate audio
 * source is set, the video player is muted so the two tracks don't double up.
 *
 * Still images and alpha masks are NOT handled here — they belong to the rendering layer.
 * This keeps the player concerned only with decode/timing.
 */
@Singleton
class AmbientPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaybackController {

    /** Exposed so the rendering layer can attach a Surface to it. */
    val videoPlayer: Player get() = video

    private val video: ExoPlayer = buildPlayer(handleAudioFocus = true)
    private val audio: ExoPlayer = buildPlayer(handleAudioFocus = false)

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var hasSeparateAudio = false

    init {
        video.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = syncStatus()
            override fun onIsPlayingChanged(isPlaying: Boolean) = syncStatus()
            override fun onPlayerError(error: PlaybackException) {
                _state.value = _state.value.copy(
                    status = PlaybackStatus.ERROR,
                    errorMessage = error.errorCodeName,
                )
            }
        })
        // Surface separate-soundtrack failures too (e.g. fireplace video + rain audio).
        audio.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                _state.value = _state.value.copy(
                    status = PlaybackStatus.ERROR,
                    errorMessage = "audio: ${error.errorCodeName}",
                )
            }
        })
    }

    override fun load(scene: Scene) {
        val videoItems = scene.fullVideoPlaylist
            .filter { it.type == MediaSourceType.LOCAL_VIDEO }
            .map { MediaItem.fromUri(it.uri) }

        video.applyRepeatMode(scene.loopMode)
        video.shuffleModeEnabled = scene.loopMode == LoopMode.SHUFFLE_PLAYLIST
        video.setMediaItems(videoItems)

        hasSeparateAudio = scene.audioSource.type == MediaSourceType.LOCAL_AUDIO
        if (hasSeparateAudio) {
            audio.setMediaItem(MediaItem.fromUri(scene.audioSource.uri))
            audio.repeatMode = Player.REPEAT_MODE_ALL
            audio.prepare()
        } else {
            audio.stop()
            audio.clearMediaItems()
        }

        applyVolume(scene.muted)
        video.prepare()

        _state.value = PlaybackState(
            activeSceneId = scene.id,
            status = PlaybackStatus.BUFFERING,
            currentBrightness = scene.brightness,
            muted = scene.muted,
        )
        play()
    }

    override fun play() {
        video.playWhenReady = true
        if (hasSeparateAudio) audio.playWhenReady = true
    }

    override fun pause() {
        video.playWhenReady = false
        audio.playWhenReady = false
    }

    override fun stop() {
        video.stop()
        audio.stop()
        _state.value = _state.value.copy(status = PlaybackStatus.IDLE)
    }

    override fun setBrightness(target: Float) {
        _state.value = _state.value.copy(currentBrightness = target.coerceIn(0f, 1f))
    }

    override fun setMuted(muted: Boolean) {
        applyVolume(muted)
        _state.value = _state.value.copy(muted = muted)
    }

    override fun release() {
        video.release()
        audio.release()
    }

    private fun applyVolume(muted: Boolean) {
        when {
            muted -> {
                video.volume = 0f
                audio.volume = 0f
            }
            hasSeparateAudio -> {
                // Soundtrack comes from the audio player; silence the video's own track.
                video.volume = 0f
                audio.volume = 1f
            }
            else -> {
                video.volume = 1f
            }
        }
    }

    private fun Player.applyRepeatMode(loopMode: LoopMode) {
        repeatMode = loopModeToRepeatMode(loopMode)
    }

    companion object {
        /** Pure mapping from domain [LoopMode] to a Media3 `Player.REPEAT_MODE_*` constant. */
        fun loopModeToRepeatMode(loopMode: LoopMode): Int = when (loopMode) {
            LoopMode.PLAY_ONCE -> Player.REPEAT_MODE_OFF
            LoopMode.LOOP_ONE -> Player.REPEAT_MODE_ONE
            LoopMode.LOOP_PLAYLIST, LoopMode.SHUFFLE_PLAYLIST -> Player.REPEAT_MODE_ALL
        }
    }

    private fun syncStatus() {
        val status = when {
            video.playerError != null -> PlaybackStatus.ERROR
            video.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
            video.playbackState == Player.STATE_ENDED -> PlaybackStatus.ENDED
            video.isPlaying -> PlaybackStatus.PLAYING
            video.playbackState == Player.STATE_READY -> PlaybackStatus.PAUSED
            else -> PlaybackStatus.IDLE
        }
        _state.value = _state.value.copy(status = status, errorMessage = null)
    }

    private fun buildPlayer(handleAudioFocus: Boolean): ExoPlayer =
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                handleAudioFocus,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
}
