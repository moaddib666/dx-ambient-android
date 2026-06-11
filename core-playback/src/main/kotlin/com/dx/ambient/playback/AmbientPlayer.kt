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

    private var video: ExoPlayer = buildPlayer(handleAudioFocus = true)
    private var audio: ExoPlayer = buildPlayer(handleAudioFocus = false)
    private var released = false

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var hasSeparateAudio = false

    /**
     * False for scenes whose picture is not ExoPlayer's (slideshow, single image, audio-only).
     * Status is then driven by the audio player, or held manually when there is no audio either,
     * so such scenes still reach PLAYING (lifting the reveal cover) and pause correctly.
     */
    private var hasVideoItems = false

    private val videoListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = syncStatus()
        override fun onIsPlayingChanged(isPlaying: Boolean) = syncStatus()
        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                status = PlaybackStatus.ERROR,
                errorMessage = error.errorCodeName,
            )
        }
    }

    // Surface separate-soundtrack failures too (e.g. fireplace video + rain audio),
    // and drive the status when the soundtrack is the only playing media.
    private val audioListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = syncStatus()
        override fun onIsPlayingChanged(isPlaying: Boolean) = syncStatus()
        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                status = PlaybackStatus.ERROR,
                errorMessage = "audio: ${error.errorCodeName}",
            )
        }
    }

    init {
        attachListeners()
    }

    private fun attachListeners() {
        video.addListener(videoListener)
        audio.addListener(audioListener)
    }

    // The instance is a singleton but the underlying ExoPlayers hold codecs, so
    // they are released when the activity finishes and rebuilt on the next load.
    private fun ensurePlayers() {
        if (!released) return
        video = buildPlayer(handleAudioFocus = true)
        audio = buildPlayer(handleAudioFocus = false)
        attachListeners()
        released = false
    }

    override fun load(scene: Scene) {
        ensurePlayers()
        // Local files and direct remote streams both go through ExoPlayer.
        // YouTube sources never reach this player (IFrame-only policy).
        val videoItems = scene.fullVideoPlaylist
            .filter { it.type == MediaSourceType.LOCAL_VIDEO || it.type == MediaSourceType.STREAM }
            .map { MediaItem.fromUri(it.uri) }

        hasVideoItems = videoItems.isNotEmpty()
        video.applyRepeatMode(scene.loopMode)
        video.shuffleModeEnabled = scene.loopMode == LoopMode.SHUFFLE_PLAYLIST
        video.setMediaItems(videoItems)

        hasSeparateAudio = scene.audioSource.type == MediaSourceType.LOCAL_AUDIO ||
            scene.audioSource.type == MediaSourceType.STREAM
        if (hasSeparateAudio) {
            audio.setMediaItem(MediaItem.fromUri(scene.audioSource.uri))
            audio.repeatMode = Player.REPEAT_MODE_ALL
            audio.prepare()
        } else {
            audio.stop()
            audio.clearMediaItems()
        }

        applyVolume(scene.muted)
        if (hasVideoItems) video.prepare() else video.stop()

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
        if (!hasVideoItems && !hasSeparateAudio) {
            // Nothing for ExoPlayer to report on — the picture is rendered elsewhere
            // (slideshow/still image), so the status is held manually.
            _state.value = _state.value.copy(status = PlaybackStatus.PLAYING, errorMessage = null)
        }
    }

    override fun pause() {
        video.playWhenReady = false
        audio.playWhenReady = false
        if (!hasVideoItems && !hasSeparateAudio) {
            _state.value = _state.value.copy(status = PlaybackStatus.PAUSED, errorMessage = null)
        }
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
        if (released) return
        video.release()
        audio.release()
        released = true
        _state.value = PlaybackState()
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
        // The player carrying the scene's actual media drives the status; with neither
        // video nor separate audio the status is held manually by play()/pause().
        val driver = when {
            hasVideoItems -> video
            hasSeparateAudio -> audio
            else -> return
        }
        val status = when {
            driver.playerError != null -> PlaybackStatus.ERROR
            driver.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
            driver.playbackState == Player.STATE_ENDED -> PlaybackStatus.ENDED
            driver.isPlaying -> PlaybackStatus.PLAYING
            driver.playbackState == Player.STATE_READY -> PlaybackStatus.PAUSED
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
