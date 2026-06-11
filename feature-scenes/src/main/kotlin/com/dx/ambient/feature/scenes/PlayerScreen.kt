package com.dx.ambient.feature.scenes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.dx.ambient.rendering.AmbientStage
import com.dx.ambient.rendering.R
import kotlinx.coroutines.delay

/**
 * Full-screen ambient playback (MVP features 2, 3, 5, 9).
 *
 * Renders the effective scene through [AmbientStage] (fast SurfaceView path plus optional
 * mask and dim scrim). The remote center/enter key toggles play/pause; Back stops playback
 * and exits. A brief auto-hiding overlay names the active scene.
 */
@Composable
fun PlayerScreen(
    sceneId: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PlayerViewModel = hiltViewModel()
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(sceneId) {
        viewModel.bind(sceneId)
    }

    BackHandler {
        viewModel.onStop()
        onExit()
    }

    // BACK is not the only way out: pause when the app is backgrounded (home button,
    // HDMI sleep) and fully stop playback + timers when the screen leaves composition.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onBackground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onStop()
        }
    }

    val focusRequester = remember { FocusRequester() }
    var overlayVisible by remember { mutableStateOf(true) }

    val scene = uiState.scene

    // Auto-hide the scene-name overlay shortly after it (re)appears.
    LaunchedEffect(scene?.id) {
        if (scene != null) {
            overlayVisible = true
            delay(2_500)
            overlayVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        viewModel.togglePlay()
                        overlayVisible = true
                        true
                    }
                    Key.DirectionLeft -> {
                        viewModel.previous()
                        true
                    }
                    Key.DirectionRight -> {
                        viewModel.next()
                        true
                    }
                    else -> false
                }
            }
            // Touch/mouse mirror of the remote controls: tap toggles play/pause,
            // a horizontal swipe switches to the previous/next scene.
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        viewModel.togglePlay()
                        overlayVisible = true
                    },
                )
            }
            .pointerInput(Unit) {
                var dragTotal = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = {
                        val threshold = 96.dp.toPx()
                        when {
                            dragTotal <= -threshold -> viewModel.next()
                            dragTotal >= threshold -> viewModel.previous()
                        }
                    },
                ) { _, dragAmount -> dragTotal += dragAmount }
            }
            // Swipe up or down collapses the player: stop playback and return to Home,
            // mirroring the system "dismiss" gesture instead of leaving the video stuck.
            .pointerInput(Unit) {
                var dragTotal = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = {
                        val threshold = 120.dp.toPx()
                        if (dragTotal <= -threshold || dragTotal >= threshold) {
                            viewModel.onStop()
                            onExit()
                        }
                    },
                ) { _, dragAmount -> dragTotal += dragAmount }
            },
    ) {
        if (scene != null) {
            AmbientStage(
                player = viewModel.player,
                scene = scene,
                modifier = Modifier.fillMaxSize(),
            )

            if (overlayVisible) {
                Surface(
                    modifier = Modifier
                        .wrapContentSize()
                        .align(Alignment.BottomStart)
                        .padding(48.dp),
                ) {
                    Text(
                        text = scene.name,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
        } else if (!uiState.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.player_scene_unavailable),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
