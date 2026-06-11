package com.dx.ambient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dx.ambient.navigation.AmbientNavHost
import com.dx.ambient.playback.AmbientPlayer
import com.dx.ambient.rendering.theme.AmbientTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var ambientPlayer: AmbientPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        // Show the branded system splash (Android 12+) and hand off to the app theme.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Immersive, edge-to-edge ambient surface: draw under the system bars and hide them.
        // A projector/TV experience should show no status or navigation bar; on a phone they
        // reappear transiently on a swipe.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            AmbientTheme {
                AmbientNavHost()
            }
        }
    }

    override fun onDestroy() {
        // Free the ExoPlayer codecs when the app is truly going away (not on a
        // configuration change); the singleton rebuilds its players on next load.
        if (isFinishing) {
            ambientPlayer.release()
        }
        super.onDestroy()
    }
}
