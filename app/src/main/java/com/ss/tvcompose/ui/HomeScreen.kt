package com.ss.tvcompose.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.ss.tvcompose.data.Category
import com.ss.tvcompose.data.Movie
import com.ss.tvcompose.ui.browse.BrowseScreen
import com.ss.tvcompose.ui.search.SearchScreen
import com.ss.tvcompose.ui.theme.TvColors

/**
 * Sections in the left drawer. A section is either a filter over the catalogue
 * ([category] non-null), the search experience, or an informational page.
 */
enum class DrawerSection(
    val label: String,
    val icon: ImageVector,
    val category: Category? = null
) {
    SEARCH("Search", Icons.Outlined.Search),
    HOME("Home", Icons.Outlined.Home),
    MOVIES("Movies", Icons.Outlined.Movie, Category.MOVIES),
    SHOWS("Series", Icons.Outlined.LiveTv, Category.SHOWS),
    SHORTS("Shorts", Icons.Outlined.Bolt, Category.SHORTS),
    MUSIC("Music", Icons.Outlined.MusicNote, Category.MUSIC),
    ABOUT("About", Icons.Outlined.Settings)
}

/**
 * ============================================================================
 *  THE APP SHELL: LEFT DRAWER + CONTENT
 * ============================================================================
 *
 * HOW THE TV DRAWER ACTUALLY WORKS
 * --------------------------------
 * This is the part that catches everyone. `NavigationDrawer` from tv-material
 * is **not** a phone drawer. There is no hamburger, no swipe, no scrim to tap.
 * Internally it is just:
 *
 *     Row { DrawerSheet(...); content() }
 *
 * The sheet opens and closes purely from FOCUS. It wraps itself in
 * `onFocusChanged { drawerState.setValue(if (hasFocus) Open else Closed) }`.
 * So the interaction is:
 *
 *     user presses LEFT from the leftmost card
 *       -> focus search finds the drawer sheet
 *       -> sheet reports hasFocus = true
 *       -> drawerState flips to Open
 *       -> NavigationDrawerItem animates 56dp -> 256dp and fades in its label
 *
 * You do not open it. You do not close it. You never call setValue yourself in
 * normal operation. If you find yourself writing code to toggle it, something
 * upstream is wrong -- usually a `Modifier.focusable()` swallowing the LEFT key.
 *
 * WHY THE SHEET IS A SIBLING, NOT AN OVERLAY
 * ------------------------------------------
 * Because it is a `Row`, the drawer *pushes* the content instead of covering
 * it, and the content re-lays-out as it expands. That is intentional on TV: an
 * overlay would hide the row the user just came from, losing their place. If
 * you genuinely want the overlay behaviour, `ModalNavigationDrawer` is the
 * other function in the same file -- same API, adds a scrim.
 *
 * WHY SECTION STATE LIVES HERE AND NOT IN THE NavHost
 * ---------------------------------------------------
 * Switching drawer sections is not navigation in the back-stack sense: pressing
 * BACK from "Series" should exit the app, not walk back through every section
 * the user browsed. Modelling sections as NavHost destinations gives you a back
 * stack twenty entries deep that users have to press BACK through. So sections
 * are local state; only the player is a real navigation destination.
 */
@Composable
fun HomeScreen(
    onPlayMovie: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // rememberSaveable so the section survives process death / config change.
    var selectedSection by rememberSaveable { mutableStateOf(DrawerSection.HOME) }

    // BACK from any non-Home section returns to Home rather than exiting. This
    // matches what TV users expect and costs three lines. When already on Home
    // the handler is disabled, so BACK falls through to the system and exits.
    BackHandler(enabled = selectedSection != DrawerSection.HOME) {
        selectedSection = DrawerSection.HOME
    }

    NavigationDrawer(
        modifier = modifier.fillMaxSize(),
        drawerState = drawerState,
        drawerContent = { drawerValue ->
            // `this` here is NavigationDrawerScope -- NavigationDrawerItem is an
            // extension on it, which is how it knows whether to show labels.
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(TvColors.Surface)
                    .padding(vertical = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Brand mark. Gated on Open because anything of fixed width in
                // here would pin the sheet open at that width -- the sheet sizes
                // itself to its content via animateContentSize().
                if (drawerValue == DrawerValue.Open) {
                    Text(
                        text = "TV COMPOSE",
                        style = MaterialTheme.typography.labelMedium,
                        color = TvColors.OnSurfaceMuted,
                        modifier = Modifier.padding(start = 20.dp, bottom = 14.dp)
                    )
                } else {
                    Spacer(Modifier.height(34.dp))
                }

                DrawerSection.entries.forEach { section ->
                    NavigationDrawerItem(
                        selected = section == selectedSection,
                        onClick = { selectedSection = section },
                        leadingContent = {
                            Icon(
                                imageVector = section.icon,
                                contentDescription = section.label,
                                modifier = Modifier.size(NavigationDrawerItemDefaults.IconSize)
                            )
                        }
                    ) {
                        Text(text = section.label)
                    }
                }
            }
        }
    ) {
        // ── Content pane ────────────────────────────────────────────────────
        // Sized to whatever the drawer leaves. Because the drawer animates its
        // width, this pane animates too; nothing here needs to know about it.
        when (selectedSection) {
            DrawerSection.SEARCH -> SearchScreen(onMovieClick = onPlayMovie)

            DrawerSection.HOME -> BrowseScreen(
                categoryFilter = null,
                onMovieClick = onPlayMovie,
                onSearchClick = { selectedSection = DrawerSection.SEARCH }
            )

            DrawerSection.ABOUT -> AboutPane()

            else -> BrowseScreen(
                categoryFilter = selectedSection.category,
                onMovieClick = onPlayMovie,
                onSearchClick = { selectedSection = DrawerSection.SEARCH }
            )
        }
    }
}

/**
 * A static page, kept here so the drawer has a section that is not a card grid.
 * It doubles as a crib sheet you can read on the device.
 */
@Composable
private fun AboutPane(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 56.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "About this app",
            style = MaterialTheme.typography.displayMedium,
            color = TvColors.OnBackground
        )
        Text(
            text = "A Compose for TV reference app. No Leanback, no Fragments, " +
                "no Presenters -- one Activity, one composable tree.",
            style = MaterialTheme.typography.bodyLarge,
            color = TvColors.OnSurfaceMuted,
            modifier = Modifier.width(760.dp)
        )
        Spacer(Modifier.height(10.dp))
        AboutLine("Remote", "LEFT from the first card opens the drawer. It is driven by focus, not by a button.")
        AboutLine("Search", "The keyboard is drawn by the app. The system IME is never summoned.")
        AboutLine("Player", "LEFT / RIGHT seek 10s. OK toggles play. UP shows controls. BACK exits.")
        AboutLine("UI toolkit", "androidx.tv:tv-material + androidx.compose.foundation lazy lists.")
        AboutLine("Playback", "androidx.media3 (ExoPlayer), with DASH and HLS extensions.")
    }
}

@Composable
private fun AboutLine(label: String, value: String) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = TvColors.Primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TvColors.OnSurface,
            modifier = Modifier.width(760.dp)
        )
    }
}
