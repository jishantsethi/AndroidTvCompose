package com.ss.tvcompose.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ss.tvcompose.ui.HomeScreen
import com.ss.tvcompose.ui.player.PlayerScreen
import com.ss.tvcompose.ui.theme.TvColors

/**
 * Route names in one place, with a builder for the parameterised one, so a
 * typo is a compile error rather than a silent no-op navigation.
 */
object Routes {
    const val HOME = "home"

    const val PLAYER_ARG_MOVIE_ID = "movieId"
    const val PLAYER = "player/{$PLAYER_ARG_MOVIE_ID}"
    fun player(movieId: String) = "player/$movieId"
}

/**
 * ============================================================================
 *  NAVIGATION
 * ============================================================================
 *
 * There are exactly TWO destinations, and that is the design, not a shortcut.
 *
 *   home    the drawer shell. Which drawer section is showing is local state
 *           inside HomeScreen, NOT a destination.
 *   player  full-screen playback.
 *
 * WHY DRAWER SECTIONS ARE NOT DESTINATIONS
 * ----------------------------------------
 * If every drawer section were a NavHost destination, browsing Home -> Movies ->
 * Series -> Music would build a four-deep back stack. The user then has to press
 * BACK four times to leave the app. TV users treat BACK as "up and out", not as
 * "undo my browsing history", and an app that makes them press it repeatedly
 * feels stuck. So sections are state; only a genuine full-screen mode change is
 * navigation.
 *
 * WHY THE PLAYER TAKES AN ID AND NOT A Movie
 * ------------------------------------------
 * Navigation arguments are Bundle-backed, so passing an object means making it
 * Parcelable and shipping the whole thing through the back stack -- which then
 * has to survive process death and can go stale against the catalogue. Passing
 * an id and re-reading from the repository keeps one source of truth. It is the
 * same reasoning as passing a database key instead of a row.
 */
@Composable
fun TvNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
            .fillMaxSize()
            // The NavHost animates between destinations; without an opaque
            // background you see straight through to the window during the
            // crossfade, which on TV reads as a flicker.
            .background(TvColors.Background)
    ) {
        composable(route = Routes.HOME) {
            HomeScreen(
                onPlayMovie = { movie ->
                    navController.navigate(Routes.player(movie.id))
                }
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument(Routes.PLAYER_ARG_MOVIE_ID) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments
                ?.getString(Routes.PLAYER_ARG_MOVIE_ID)
                .orEmpty()

            PlayerScreen(
                movieId = movieId,
                onExit = {
                    // popBackStack rather than navigate(HOME): navigating would
                    // push a SECOND home entry on top of the existing one, and
                    // the ExoPlayer-owning entry would linger in the stack.
                    // Popping destroys the player's ViewModel, which is what
                    // releases the codec.
                    navController.popBackStack()
                }
            )
        }
    }
}
