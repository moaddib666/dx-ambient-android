/*
 * ============================================================================
 *  ISOLATED, OPTIONAL YOUTUBE MODULE — POLICY (DO NOT VIOLATE)
 * ============================================================================
 *  YouTube playback in this app MUST use ONLY the official IFrame Player API
 *  inside a WebView.
 *
 *  - DO NOT implement audio extraction.
 *  - DO NOT separate audio from video.
 *  - DO NOT implement background playback. The player MUST pause whenever this
 *    screen is not in the RESUMED lifecycle state (enforced below via the
 *    lifecycle observer: ON_PAUSE/ON_STOP pause the WebView and call
 *    player.pauseVideo(); ON_RESUME resumes the WebView; onDispose destroys it).
 *  - The embedded player viewport must be at least 200x200 px (the YouTube
 *    embed requirement). This composable fills the screen via Modifier and the
 *    IFrame is sized to 100% x 100% — never crop the live player to a tiny
 *    portal.
 *
 *  This is the ONLY place a YouTube video is ever rendered, and only ever
 *  through the official IFrame Player API loaded from
 *  https://www.youtube.com/iframe_api . The player itself is hosted on the
 *  official privacy-enhanced endpoint https://www.youtube-nocookie.com .
 * ============================================================================
 */
package com.dx.ambient.youtube

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dx.ambient.rendering.R
import com.dx.ambient.rendering.components.KeepScreenOn
import com.dx.ambient.rendering.components.PlayerLoadingOverlay
import com.dx.ambient.rendering.components.PlayerPauseOverlay
import com.dx.ambient.rendering.components.PrimaryButton
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.dx.ambient.youtube.ui.YouTubePlayerViewModel

/**
 * Renders a YouTube video or playlist using the official IFrame Player API
 * inside a full-screen [WebView].
 *
 * POLICY (see file header): IFrame Player API only; no audio extraction; no
 * background playback (the player is paused whenever this screen leaves the
 * RESUMED state); the player fills the screen and is never cropped below the
 * 200x200 px embed minimum.
 *
 * @param videoId   YouTube video id to play. Used when [playlistId] is null.
 * @param playlistId YouTube playlist id. When non-null it takes precedence and
 *                    the player is configured with `listType: 'playlist'`.
 * @param onExit     Invoked when the user presses BACK.
 * @param modifier   Layout modifier; defaults to filling available space.
 *
 * BACK is handled via a focusable wrapper that intercepts the remote/system
 * BACK key. (This module deliberately avoids the `androidx.activity.compose`
 * dependency so its isolated build graph stays minimal; key-event handling from
 * `androidx.compose.ui` works for both Android TV remotes and the system BACK.)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeIFrameScreen(
    videoId: String?,
    playlistId: String?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    /** Optional alpha mask composited over the player (ambient framing, non-interactive). */
    maskUri: String? = null,
    /** Output brightness multiplier 0f..1f — rendered as a black scrim like scene playback. */
    brightness: Float = 1f,
    /** Video layer opacity 0f..1f, independent of [brightness] (the mask stays fully lit). */
    videoAlpha: Float = 1f,
    /** Video layer scale factor (1f = fit screen). */
    videoScale: Float = 1f,
    /** Mute the embedded player (e.g. a separate scene soundtrack plays instead). */
    muted: Boolean = false,
    /** Scene name shown on the pause/loading overlays. */
    sceneTitle: String? = null,
    /** Current mask display name for the pause overlay (null = no mask). */
    maskName: String? = null,
    /** ◀ ▶ / horizontal-swipe scene switching, owned by the hosting route. */
    onSwitchScene: ((Int) -> Unit)? = null,
    /** ▲ ▼ live mask cycling, owned by the hosting route. */
    onCycleMask: ((Int) -> Unit)? = null,
) {
    // No source configured (e.g. the host hasn't wired a picker yet): show a calm placeholder
    // instead of an empty player, while still honoring BACK.
    if (videoId.isNullOrBlank() && playlistId.isNullOrBlank()) {
        YouTubeMessage(
            message = stringResource(R.string.yt_no_source),
            onExit = onExit,
            modifier = modifier,
        )
        return
    }

    // Private/unlisted playlists are unreachable for the anonymous embed (error
    // 150/152): resolve the playlist into video ids through the authorized Data
    // API first, and only fall back to the bare `listType` embed when that fails.
    var resolvedVideoIds: List<String>? = null
    if (!playlistId.isNullOrBlank()) {
        val playerViewModel: YouTubePlayerViewModel = hiltViewModel()
        val resolution by playerViewModel.resolution.collectAsStateWithLifecycle()
        LaunchedEffect(playlistId) { playerViewModel.resolve(playlistId) }
        when (val r = resolution) {
            YouTubePlayerViewModel.Resolution.Resolving -> {
                YouTubeMessage(
                    message = stringResource(R.string.yt_loading_playlist),
                    onExit = onExit,
                    modifier = modifier,
                )
                return
            }
            is YouTubePlayerViewModel.Resolution.Ready -> resolvedVideoIds = r.videoIds
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    val html = remember(videoId, playlistId, resolvedVideoIds, muted) {
        buildPlayerHtml(videoId, playlistId, resolvedVideoIds, muted)
    }

    // Holds the live WebView so the lifecycle observer / onDispose teardown can
    // reach it. Scoped to this composition instance.
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }

    // Set true when the embed can't play (YouTube's WebView Media Integrity check, or all
    // playlist videos disable embedding). The JS bridge flips this from a background thread.
    val blocked = remember { mutableStateOf(false) }

    // Raw YT.PlayerState: 1 = playing, 2 = paused; anything else is loading/buffering/
    // transitioning. Whenever the state isn't PLAYING, YouTube may be drawing its own
    // text/overlays — the native overlays below hide them all.
    val playerState = remember { mutableStateOf(YT_STATE_UNSTARTED) }
    val bridge = remember {
        YouTubeJsBridge(
            onBlocked = { blocked.value = true },
            onStateChanged = { playerState.value = it },
        )
    }
    val playing = playerState.value == YT_STATE_PLAYING
    val paused = playerState.value == YT_STATE_PAUSED

    // Focusable wrapper that intercepts BACK to exit YouTube mode entirely;
    // playback is torn down in onDispose.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Ambient playback runs for hours without input — never let the device dim/lock here.
    KeepScreenOn()

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                    when (event.key) {
                        Key.Back -> {
                            onExit()
                            true
                        }
                        // Center/enter toggles play/pause, mirroring scene playback —
                        // the YouTube UI itself is never interactive in ambient mode.
                        Key.DirectionCenter, Key.Enter -> {
                            webViewHolder.value?.evaluateJavascript(TOGGLE_JS, null)
                            true
                        }
                        Key.DirectionLeft -> {
                            onSwitchScene?.invoke(-1)
                            onSwitchScene != null
                        }
                        Key.DirectionRight -> {
                            onSwitchScene?.invoke(1)
                            onSwitchScene != null
                        }
                        Key.DirectionUp -> {
                            onCycleMask?.invoke(-1)
                            onCycleMask != null
                        }
                        Key.DirectionDown -> {
                            onCycleMask?.invoke(1)
                            onCycleMask != null
                        }
                        else -> false
                    }
                },
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                // Per-scene video transform: opacity + scale apply to the player only;
                // the mask and scrim layers above stay untouched.
                .graphicsLayer {
                    alpha = videoAlpha.coerceIn(0f, 1f)
                    scaleX = videoScale
                    scaleY = videoScale
                },
            factory = { context ->
                WebView(context).apply {
                    webViewHolder.value = this
                    // Fill parent so the IFrame's 100% x 100% sizing yields a
                    // viewport well above the 200x200 px embed minimum.
                    layoutParams =
                        android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )

                    settings.apply {
                        javaScriptEnabled = true
                        // Required so the IFrame API may start playback
                        // programmatically without a manual user gesture.
                        mediaPlaybackRequiresUserGesture = false
                        domStorageEnabled = true
                        // Help the embed satisfy YouTube's checks inside a WebView.
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        @Suppress("DEPRECATION")
                        databaseEnabled = true
                    }

                    // HTML5 <video>/IFrame media requires a WebChromeClient — without it YouTube
                    // refuses to play (renders "This video is unavailable"). Console messages are
                    // forwarded to logcat so embed errors (101/150/152) are diagnosable on-device.
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                            android.util.Log.d(
                                "YouTubeIFrame",
                                "${message.messageLevel()}: ${message.message()}",
                            )
                            return true
                        }
                    }

                    // Lets the player JS report unplayable/blocked embeds back to native UI.
                    addJavascriptInterface(bridge, "AndroidYT")

                    // The WebView itself is inert: every touch is consumed here so YouTube
                    // can NEVER show its own UI from a tap. Gestures mirror the remote 1:1:
                    // clean tap = play/pause, horizontal swipe = previous/next scene,
                    // vertical swipe = mask tuning; BACK exits.
                    isFocusable = false
                    isFocusableInTouchMode = false
                    var downX = 0f
                    var downY = 0f
                    setOnTouchListener { v, event ->
                        when (event.actionMasked) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                downX = event.x
                                downY = event.y
                            }
                            android.view.MotionEvent.ACTION_UP -> {
                                val density = v.resources.displayMetrics.density
                                val dx = event.x - downX
                                val dy = event.y - downY
                                val slop = android.view.ViewConfiguration.get(v.context).scaledTouchSlop
                                when {
                                    kotlin.math.abs(dx) >= 96 * density &&
                                        kotlin.math.abs(dx) > kotlin.math.abs(dy) ->
                                        onSwitchScene?.invoke(if (dx < 0) 1 else -1)
                                    kotlin.math.abs(dy) >= 120 * density &&
                                        kotlin.math.abs(dy) > kotlin.math.abs(dx) ->
                                        onCycleMask?.invoke(if (dy > 0) 1 else -1)
                                    kotlin.math.abs(dx) <= slop && kotlin.math.abs(dy) <= slop ->
                                        (v as WebView).evaluateJavascript(TOGGLE_JS, null)
                                }
                            }
                        }
                        true
                    }

                    // Keep the user inside the embedded player: block navigations to
                    // non-YouTube origins (e.g. tapping the logo). Uses the String overload
                    // so it also fires on API 23 (minSdk).
                    @Suppress("OVERRIDE_DEPRECATION")
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            val host = url?.let { Uri.parse(it).host }.orEmpty()
                            val allowed = ALLOWED_HOST_SUFFIXES.any { host.endsWith(it) }
                            return !allowed // returning true cancels the navigation
                        }
                    }

                    // Base URL must match the player's `origin`/`host` for the
                    // IFrame API to accept the embed. The privacy-enhanced
                    // youtube-nocookie.com origin is used because YouTube's
                    // embed checks reject WebView pages that claim to be
                    // www.youtube.com itself (error 152-4 "Video unavailable").
                    loadDataWithBaseURL(
                        EMBED_ORIGIN,
                        html,
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            },
            update = { /* No reactive updates: html is keyed via remember(). */ },
        )

        // Ambient alpha mask over the player. Purely decorative and not interactive —
        // taps are consumed by the WebView's own inert touch handler beneath.
        if (!maskUri.isNullOrBlank()) {
            coil.compose.AsyncImage(
                model = maskUri,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Brightness / dim scrim, same as scene playback. brightness == 1f draws nothing.
        if (brightness < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (1f - brightness).coerceIn(0f, 1f))),
            )
        }

        // Ambient cover, mirroring AmbientStage's reveal but never a dead black screen:
        // the embed is visible ONLY while video is actually playing. Whenever YouTube may
        // be drawing its own text/overlays (loading, buffering, between videos, paused),
        // a designed opaque overlay hides it — ambient artwork plus the scene identity.
        //
        // The reveal is additionally DELAYED after playback (re)starts: YouTube draws an
        // auto-hiding title/controls overlay for the first seconds of play, so the cover
        // lifts only once that overlay is gone. Covering is instant.
        var revealed by remember { mutableStateOf(false) }
        LaunchedEffect(playing) {
            if (playing) {
                kotlinx.coroutines.delay(REVEAL_DELAY_MS)
                revealed = true
            } else {
                revealed = false
            }
        }
        val coverAlpha by animateFloatAsState(
            targetValue = if (revealed) 0f else 1f,
            animationSpec = tween(durationMillis = if (revealed) 700 else 80),
            label = "yt-reveal",
        )
        when {
            // Explicit pause: full pause UI (scene identity, live mask control, hints).
            // The current mask stays composited so ▲ ▼ tuning is previewed live.
            paused -> PlayerPauseOverlay(
                sceneName = sceneTitle,
                maskName = maskName,
                maskUri = maskUri,
            )
            // Loading/buffering/transitioning: ambient backdrop with a pulsing loader.
            coverAlpha > 0f -> Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha }) {
                PlayerLoadingOverlay(sceneName = sceneTitle, maskUri = maskUri)
            }
        }

        // Friendly overlay when the embed is blocked (e.g. Media Integrity on emulators /
        // uncertified devices, or every video in the playlist disables embedding).
        if (blocked.value) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0A0D10)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(
                        text = stringResource(R.string.yt_blocked_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.yt_blocked_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    PrimaryButton(text = stringResource(R.string.common_back), onClick = onExit)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE,
                    Lifecycle.Event.ON_STOP,
                    -> {
                        // No-background-playback enforcement: pause the player
                        // and the WebView whenever we leave the RESUMED state.
                        webViewHolder.value?.let {
                            it.evaluateJavascript(PAUSE_JS, null)
                            it.onPause()
                        }
                    }

                    Lifecycle.Event.ON_RESUME -> {
                        webViewHolder.value?.onResume()
                    }

                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webViewHolder.value?.let { wv ->
                wv.evaluateJavascript(PAUSE_JS, null)
                wv.onPause()
                wv.loadUrl("about:blank")
                wv.destroy()
            }
            webViewHolder.value = null
        }
    }
}

/**
 * Builds the minimal HTML page that loads the official IFrame Player API and
 * starts either a single video or a playlist.
 *
 * Player controls are kept minimal, related videos are disabled where the API
 * allows (`rel: 0`), and the iframe fills 100% width/height so it always
 * exceeds the 200x200 px embed minimum.
 */
private fun buildPlayerHtml(
    videoId: String?,
    playlistId: String?,
    resolvedVideoIds: List<String>? = null,
    muted: Boolean = false,
): String {
    val safeVideoId = (videoId ?: "").escapeForJs()
    val safePlaylistId = (playlistId ?: "").escapeForJs()

    // Video ids resolved via the authorized Data API, loaded as an array
    // playlist — works for private/unlisted playlists the anonymous embed
    // can't open by id. Ids are [A-Za-z0-9_-], enforced here defensively.
    val idsJsArray = resolvedVideoIds
        ?.map { id -> id.filter { it.isLetterOrDigit() || it == '-' || it == '_' } }
        ?.filter { it.isNotBlank() }
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString(",") { "'$it'" }

    // Playlist takes precedence when present; resolved ids beat the bare embed.
    // Ambient use: playlists restart from the top when they finish ('loop': 1 /
    // setLoop below), so a playlist can serve as an endless video loop.
    // Ambient/immersive mode: the player chrome is fully hidden (controls 0, no keyboard,
    // no fullscreen button, no annotations) — playback control happens natively.
    val playerVarsBody =
        if (idsJsArray == null && !playlistId.isNullOrEmpty()) {
            """
            'listType': 'playlist',
            'list': '$safePlaylistId',
            'loop': 1,
            'controls': 0,
            'disablekb': 1,
            'fs': 0,
            'iv_load_policy': 3,
            'rel': 0,
            'modestbranding': 1,
            'playsinline': 1
            """.trimIndent()
        } else {
            """
            'controls': 0,
            'disablekb': 1,
            'fs': 0,
            'iv_load_policy': 3,
            'rel': 0,
            'modestbranding': 1,
            'playsinline': 1
            """.trimIndent()
        }

    // videoId is only supplied when there is no playlist at all.
    val videoIdLine =
        if (playlistId.isNullOrEmpty() && resolvedVideoIds == null && !videoId.isNullOrEmpty()) {
            "'videoId': '$safeVideoId',"
        } else {
            ""
        }

    // Array playlists start via loadPlaylist; everything else autoplay-starts directly.
    val muteCall = if (muted) "e.target.mute(); " else ""
    val onReadyBody =
        if (idsJsArray != null) {
            "${muteCall}e.target.loadPlaylist([$idsJsArray]); e.target.setLoop(true);"
        } else {
            "${muteCall}e.target.playVideo();"
        }

    return """
        <!DOCTYPE html>
        <html>
          <head>
            <meta charset="utf-8" />
            <meta name="viewport"
                  content="width=device-width, initial-scale=1.0, user-scalable=no" />
            <style>
              html, body {
                margin: 0;
                padding: 0;
                width: 100%;
                height: 100%;
                background: #000;
                overflow: hidden;
              }
              /* IFrame fills the screen — always >= 200x200 px. Pointer events are
                 disabled so the embed UI can never be opened in ambient mode; all
                 control happens through the native overlay (tap/center = play-pause). */
              #player, iframe {
                width: 100% !important;
                height: 100% !important;
                border: 0;
                pointer-events: none;
              }
            </style>
          </head>
          <body>
            <div id="player"></div>
            <script>
              // Loaded over the official endpoint only.
              var tag = document.createElement('script');
              tag.src = "https://www.youtube.com/iframe_api";
              var firstScriptTag = document.getElementsByTagName('script')[0];
              firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

              // Exposed on window so the host can call player.pauseVideo()
              // via evaluateJavascript when leaving the RESUMED state.
              function onYouTubeIframeAPIReady() {
                window.player = new YT.Player('player', {
                  width: '100%',
                  height: '100%',
                  $videoIdLine
                  playerVars: {
                    $playerVarsBody,
                    'enablejsapi': 1,
                    'origin': '$EMBED_ORIGIN',
                    'widget_referrer': '$EMBED_ORIGIN'
                  },
                  host: '$EMBED_ORIGIN',
                  events: {
                    'onReady': function (e) { $onReadyBody },
                    'onStateChange': function (e) {
                      if (e.data === YT.PlayerState.PLAYING && window.__watchdog) {
                        clearTimeout(window.__watchdog);
                        window.__watchdog = null;
                      }
                      // Drive the native reveal cover: the embed is only shown while
                      // actually PLAYING, so YouTube's own overlays stay invisible.
                      if (window.AndroidYT) {
                        window.AndroidYT.onPlayerStateChanged(e.data);
                      }
                    },
                    'onError': function (e) {
                      // Some videos disable embedding (errors 101/150/152). In a playlist, skip
                      // ahead to the next item so a playable one is found (bounded to avoid loops).
                      console.error('YT player onError code=' + e.data);
                      if (window.__skips === undefined) window.__skips = 0;
                      if (window.__skips < 50) {
                        window.__skips++;
                        try {
                          e.target.nextVideo();
                        } catch (err) {
                          // Skipping is impossible (player broken) — give up loudly.
                          console.error('nextVideo failed: ' + err);
                          if (window.AndroidYT) { window.AndroidYT.onUnplayable(); }
                        }
                      } else if (window.AndroidYT) {
                        window.AndroidYT.onUnplayable();
                      }
                    }
                  }
                });
                // Unconditional watchdog: if playback hasn't started within 9s (blocked embed,
                // Media Integrity, or all videos unplayable), surface a native message. Set here
                // — not inside onReady — because a blocked player may never fire onReady.
                window.__watchdog = setTimeout(function () {
                  if (window.AndroidYT) { window.AndroidYT.onUnplayable(); }
                }, 9000);
              }
            </script>
          </body>
        </html>
    """.trimIndent()
}

/**
 * JS → native bridge. Must be a public, named class: `addJavascriptInterface` reaches
 * `@JavascriptInterface` methods via reflection, which fails on Kotlin anonymous objects.
 */
class YouTubeJsBridge(
    private val onBlocked: () -> Unit,
    private val onStateChanged: (Int) -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onUnplayable() {
        main.post { onBlocked() }
    }

    /** Raw YT.PlayerState value — see [YT_STATE_PLAYING] / [YT_STATE_PAUSED]. */
    @JavascriptInterface
    fun onPlayerStateChanged(state: Int) {
        main.post { onStateChanged(state) }
    }
}

/** YT.PlayerState constants the native layer cares about. */
internal const val YT_STATE_UNSTARTED = -1
internal const val YT_STATE_PLAYING = 1
internal const val YT_STATE_PAUSED = 2

/**
 * Origin the embed page claims and the host the player loads from. The
 * privacy-enhanced youtube-nocookie.com endpoint is an official IFrame Player
 * API host; using it avoids YouTube's origin-mismatch rejection (error 152-4
 * "This video is unavailable — Watch on YouTube") that fires when a WebView
 * page poses as www.youtube.com itself.
 */
private const val EMBED_ORIGIN = "https://www.youtube-nocookie.com"

/** JS evaluated to pause playback when leaving the RESUMED state. */
private const val PAUSE_JS =
    "if (window.player && typeof window.player.pauseVideo === 'function') " +
        "{ window.player.pauseVideo(); }"

/** How long after PLAYING starts the cover stays up, letting YouTube's overlay auto-hide. */
private const val REVEAL_DELAY_MS = 2_800L

/**
 * JS evaluated on tap / remote-center: toggles play (1) ↔ pause, like scene playback.
 * The pause branch reports the paused state to native BEFORE pausing so the black cover
 * is already up when YouTube draws its pause overlay.
 */
private const val TOGGLE_JS =
    "if (window.player && typeof window.player.getPlayerState === 'function') {" +
        " if (window.player.getPlayerState() === 1) {" +
        "   if (window.AndroidYT) { window.AndroidYT.onPlayerStateChanged(2); }" +
        "   window.player.pauseVideo();" +
        " } else { window.player.playVideo(); } }"

/** Minimal escaping for values interpolated into single-quoted JS strings. */
private fun String.escapeForJs(): String =
    replace("\\", "\\\\").replace("'", "\\'").replace("\n", "").replace("\r", "")

/** Host suffixes the embedded player is allowed to navigate within. */
private val ALLOWED_HOST_SUFFIXES = listOf(
    "youtube.com",
    "youtube-nocookie.com",
    "ytimg.com",
    "google.com",
    "gstatic.com",
    "googlevideo.com",
)

/** Simple full-screen message with BACK handling, used when there is no source to play. */
@Composable
private fun YouTubeMessage(message: String, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
                    onExit()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message, style = MaterialTheme.typography.headlineSmall)
    }
}
