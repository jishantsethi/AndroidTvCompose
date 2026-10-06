package com.ss.tvcompose.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ss.tvcompose.data.Movie
import com.ss.tvcompose.ui.components.MoviePosterTile
import com.ss.tvcompose.ui.components.OnScreenKeyboard
import com.ss.tvcompose.ui.components.SearchPill
import com.ss.tvcompose.ui.theme.LocalTvDimens
import com.ss.tvcompose.ui.theme.TvColors
import org.koin.androidx.compose.koinViewModel

/**
 * ============================================================================
 *  SEARCH
 * ============================================================================
 *
 * LAYOUT
 * ------
 *   Column
 *     SearchPill                        <- the query, top-left
 *     Row
 *       OnScreenKeyboard  (left)        <- appears when the pill is activated
 *       Results grid      (right)       <- LazyVerticalGrid, live-updating
 *
 * Keyboard on the LEFT and results on the RIGHT is not arbitrary. D-pad focus
 * search is geometric, so this arrangement gives you the navigation you want
 * for free: RIGHT from the last keyboard column lands in the results, LEFT from
 * the first result column goes back to the keys. Stack them vertically instead
 * and every trip between typing and browsing becomes six DOWN presses.
 *
 * THE KEYBOARD IS A PANEL, NOT A DIALOG
 * -------------------------------------
 * A `Dialog` or `Popup` gets its own window, and a separate window has its own
 * focus root. D-pad focus search does not cross window boundaries, so you would
 * have to hand-wire every route in and out of it. Composing the keyboard inline
 * keeps it in the same focus tree as everything else, and all the navigation
 * described above simply works.
 *
 * BACK BEHAVIOUR
 * --------------
 * BACK closes the keyboard first and only then leaves the screen. Skipping that
 * step is a real usability bug on TV: the user opens the keyboard, changes their
 * mind, presses BACK and is thrown out of the section entirely.
 */
@Composable
fun SearchScreen(
    onMovieClick: (Movie) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel()
) {
    val dimens = LocalTvDimens.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The keyboard starts open: the user chose "Search" from the drawer, so
    // wanting to type is the safe assumption, and it saves one OK press.
    var keyboardVisible by remember { mutableStateOf(true) }

    BackHandler(enabled = keyboardVisible) { keyboardVisible = false }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(horizontal = dimens.screenPadding, vertical = 40.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SearchPill(
                query = state.query,
                onClick = { keyboardVisible = !keyboardVisible },
                showCaret = keyboardVisible,
                placeholder = "Search films and series",
                width = 440.dp
            )
            Text(
                // The status line has to describe the state the user is
                // actually in. Showing "Press OK to open the keyboard" while the
                // keyboard is already open -- the first thing this screen does --
                // reads as the app not knowing what it is doing.
                text = when {
                    state.isSearching -> "Searching…"
                    state.query.isNotBlank() ->
                        "${state.results.size} result" +
                            if (state.results.size == 1) "" else "s"
                    keyboardVisible -> "Pick letters with the D-pad"
                    else -> "Press OK to open the keyboard"
                },
                style = MaterialTheme.typography.titleMedium,
                color = TvColors.OnSurfaceMuted
            )
        }

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(36.dp)
        ) {
            AnimatedVisibility(
                visible = keyboardVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                OnScreenKeyboard(
                    onCharacter = viewModel::append,
                    onBackspace = viewModel::backspace,
                    onClear = viewModel::clear,
                    onDone = {
                        viewModel.searchNow()
                        // Collapsing on Done hands the whole width to the
                        // results, which is what the user wants next.
                        keyboardVisible = false
                    },
                    // Re-request focus each time the panel is shown, not once
                    // per composition.
                    requestInitialFocus = keyboardVisible
                )
            }

            ResultsPane(
                results = state.results,
                query = state.query,
                hasSearched = state.hasSearched,
                onMovieClick = onMovieClick,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * The results grid.
 *
 * `GridCells.Adaptive` rather than a fixed column count, because the available
 * width here changes twice: once when the drawer expands, and again when the
 * keyboard panel opens or closes. A hard-coded `Fixed(4)` produces squashed,
 * unreadable tiles the moment either of those happens.
 */
@Composable
private fun ResultsPane(
    results: List<Movie>,
    query: String,
    hasSearched: Boolean,
    onMovieClick: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when {
            query.isBlank() -> Hint(
                "Type a title, a studio or a category.\n" +
                    "Try SINTEL, BLENDER or SERIES."
            )

            results.isEmpty() && hasSearched -> Hint("No matches for \"$query\"")

            else -> LazyVerticalGrid(
                // 160 dp, not 190: with the keyboard open the results pane is
                // only ~385 dp wide, and Adaptive fits floor((w + gap) / (min +
                // gap)) columns -- 190 dp yields a single column and wastes half
                // the pane. 160 dp gives two columns beside the keyboard and four
                // once it closes.
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier
                    .fillMaxSize()
                    // Same reasoning as the carousel rows: coming back from a
                    // result should return focus to the tile you opened.
                    .focusRestorer(),
                contentPadding = PaddingValues(bottom = 32.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items = results, key = { it.id }) { movie ->
                    MoviePosterTile(
                        movie = movie,
                        onClick = { onMovieClick(movie) }
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = TvColors.OnSurfaceMuted,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 24.dp)
                .width(520.dp)
        )
    }
}
