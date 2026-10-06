package com.ss.tvcompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ss.tvcompose.ui.navigation.TvNavGraph
import com.ss.tvcompose.ui.theme.TvTheme

/**
 * The one and only Activity.
 *
 * A Leanback app was a pile of Fragments (BrowseSupportFragment,
 * SearchSupportFragment, VideoSupportFragment...), each with its own lifecycle
 * and its own adapter/presenter wiring. In Compose the whole app is one
 * Activity hosting one composable tree, and "screens" are just composables
 * behind a NavHost. That is the single biggest structural change if you are
 * coming from a 2019 Leanback codebase.
 *
 * TWO TV-SPECIFIC THINGS HAPPEN HERE:
 *
 * 1. Immersive mode. TV has no system bars in the phone sense, but when this
 *    same APK runs on a handset (we left the plain LAUNCHER category in the
 *    manifest so it can) the status and navigation bars would otherwise sit on
 *    top of a full-bleed video. Hiding them here means the layout code below
 *    never has to think about insets.
 *
 * 2. We do NOT call enableEdgeToEdge(). On TV it is meaningless, and on a phone
 *    it would fight the immersive setup above.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind the system bars, then hide them.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            // If the user swipes them back (handset only), let them auto-hide
            // again rather than permanently shifting the layout.
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            TvTheme {
                TvNavGraph()
            }
        }
    }
}
