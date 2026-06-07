package com.dx.ambient.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dx.ambient.feature.library.LibraryScreen
import com.dx.ambient.feature.scenes.HomeScreen
import com.dx.ambient.feature.scenes.PlayerScreen
import com.dx.ambient.feature.scenes.SceneEditorScreen
import com.dx.ambient.feature.settings.DeviceInfoScreen
import com.dx.ambient.feature.settings.SettingsScreen
import com.dx.ambient.rendering.components.AmbientScreen
import com.dx.ambient.youtube.YouTubeIFrameScreen

/**
 * App navigation graph. Single-activity, Compose Navigation. The optional YouTube destination
 * is wired here but lives entirely in the isolated `optional-youtube` module.
 *
 * Replace [DEMO_YOUTUBE_VIDEO_ID] with a real source picker before shipping YouTube mode; it is
 * intentionally blank so the prototype never embeds an arbitrary third-party video.
 */
private const val DEMO_YOUTUBE_VIDEO_ID = ""

@Composable
fun AmbientNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onPlayScene = { sceneId -> navController.navigate(Routes.player(sceneId)) },
                onEditScene = { sceneId -> navController.navigate(Routes.editor(sceneId)) },
                onCreateScene = { navController.navigate(Routes.EDITOR_NEW) },
                onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenYouTube = { navController.navigate(Routes.YOUTUBE) },
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

        composable(Routes.YOUTUBE) {
            YouTubeIFrameScreen(
                videoId = DEMO_YOUTUBE_VIDEO_ID.ifBlank { null },
                playlistId = null,
                onExit = { navController.popBackStack() },
            )
        }
    }
}
