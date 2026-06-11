package com.dx.ambient.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dx.ambient.BuildConfig
import com.dx.ambient.boot.BootDecision
import com.dx.ambient.boot.BootScreen
import com.dx.ambient.boot.BootViewModel
import com.dx.ambient.feature.library.LibraryScreen
import com.dx.ambient.feature.scenes.HomeScreen
import com.dx.ambient.feature.scenes.PlayerScreen
import com.dx.ambient.feature.scenes.SceneEditorScreen
import com.dx.ambient.feature.settings.DeviceInfoScreen
import com.dx.ambient.feature.settings.SettingsScreen
import com.dx.ambient.rendering.components.AmbientScreen
import com.dx.ambient.youtube.YouTubeIFrameScreen
import com.dx.ambient.youtube.ui.YouTubeTabScreen

/**
 * App navigation graph. Single-activity, Compose Navigation. The optional YouTube destinations
 * (the login/playlists hub and the IFrame player) live entirely in the isolated
 * `optional-youtube` module.
 */
@Composable
fun AmbientNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.BOOT) {
        // Black splash: seed bundled defaults, then boot into the last scene or land on Home.
        composable(Routes.BOOT) {
            val bootViewModel: BootViewModel = hiltViewModel()
            val decision by bootViewModel.decision.collectAsStateWithLifecycle()
            BootScreen()
            LaunchedEffect(decision) {
                when (val d = decision) {
                    BootDecision.Loading -> Unit
                    BootDecision.OpenHome ->
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.BOOT) { inclusive = true }
                        }
                    is BootDecision.OpenScene -> {
                        // Land on Home first so BACK from the player returns here, not exits.
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.BOOT) { inclusive = true }
                        }
                        navController.navigate(Routes.player(d.sceneId))
                    }
                }
            }
        }

        composable(Routes.HOME) {
            HomeScreen(
                onPlayScene = { sceneId -> navController.navigate(Routes.player(sceneId)) },
                onEditScene = { sceneId -> navController.navigate(Routes.editor(sceneId)) },
                onCreateScene = { navController.navigate(Routes.EDITOR_NEW) },
                onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenYouTube = if (BuildConfig.YOUTUBE_MODE_ENABLED) {
                    { navController.navigate(Routes.YOUTUBE) }
                } else {
                    null
                },
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(navArgument(Routes.ARG_SCENE_ID) { type = NavType.StringType }),
        ) { entry ->
            val sceneId = entry.arguments?.getString(Routes.ARG_SCENE_ID).orEmpty()
            PlayerScreen(sceneId = sceneId, onExit = { navController.popBackStack() })
        }

        composable(Routes.EDITOR_NEW) {
            AmbientScreen {
                SceneEditorScreen(sceneId = null, onDone = { navController.popBackStack() })
            }
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument(Routes.ARG_SCENE_ID) { type = NavType.StringType }),
        ) { entry ->
            val sceneId = entry.arguments?.getString(Routes.ARG_SCENE_ID)
            AmbientScreen {
                SceneEditorScreen(sceneId = sceneId, onDone = { navController.popBackStack() })
            }
        }

        composable(Routes.LIBRARY) {
            AmbientScreen {
                LibraryScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Routes.SETTINGS) {
            AmbientScreen {
                SettingsScreen(
                    onOpenDeviceInfo = { navController.navigate(Routes.DEVICE_INFO) },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.DEVICE_INFO) {
            AmbientScreen {
                DeviceInfoScreen(onBack = { navController.popBackStack() })
            }
        }

        // YouTube hub: login wall → the signed-in user's playlists.
        composable(Routes.YOUTUBE) {
            YouTubeTabScreen(
                onPlayPlaylist = { playlistId -> navController.navigate(Routes.youtubePlayer(playlistId)) },
                onBack = { navController.popBackStack() },
            )
        }

        // Plays a chosen YouTube playlist via the official IFrame player.
        composable(
            route = Routes.YOUTUBE_PLAYER,
            arguments = listOf(navArgument(Routes.ARG_PLAYLIST_ID) { type = NavType.StringType }),
        ) { entry ->
            val playlistId = entry.arguments?.getString(Routes.ARG_PLAYLIST_ID)
            YouTubeIFrameScreen(
                videoId = null,
                playlistId = playlistId,
                onExit = { navController.popBackStack() },
            )
        }
    }
}
