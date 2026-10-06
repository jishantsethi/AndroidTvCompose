package com.ss.tvcompose.ui.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ss.tvcompose.data.Category
import com.ss.tvcompose.data.Movie
import com.ss.tvcompose.ui.components.MovieCarouselRow
import com.ss.tvcompose.ui.components.SearchPill
import com.ss.tvcompose.ui.theme.LocalTvDimens
import com.ss.tvcompose.ui.theme.TvColors
import org.koin.androidx.compose.koinViewModel

/**
 * ============================================================================
 *  BROWSE: the vertical stack of horizontal rows.
 * ============================================================================
 *
 * This is Leanback's `BrowseSupportFragment` in about 150 lines, and without
 * ArrayObjectAdapter, ListRow, ListRowPresenter or ClassPresenterSelector.
 *
 * LAYOUT SHAPE
 * ------------
 *   LazyColumn                 <- vertical: rows of content
 *     item  { hero panel }     <- describes whatever card currently has focus
 *     item  { search pill }
 *     items { MovieCarouselRow }  <- each is a LazyRow
 *
 * That nesting -- a lazy vertical list of lazy horizontal lists -- is exactly
 * what Compose is happiest with, and it is why only the visible rows and only
 * the visible cards in them are ever composed.
 *
 * THE HERO PANEL AND "FOCUS IS THE NEW HOVER"
 * -------------------------------------------
 * On a phone you tap a card and a detail screen opens. On TV that is a poor
 * trade: the user is one D-pad press away from ten other cards and does not
 * want a full screen transition to look at each. Instead the panel at the top
 * describes whatever is *focused* right now, updating as the user scans. That
 * is the single behaviour that makes a TV app feel like a TV app rather than a
 * tablet app on a big screen.
 *
 * INITIAL FOCUS
 * -------------
 * A TV screen must never render with nothing focused -- the remote would appear
 * dead. The first card of the first row claims focus once data arrives. See
 * `firstCardFocusRequester` below and the note on why the request is wrapped in
 * runCatching.
 */
@Composable
fun BrowseScreen(
    categoryFilter: Category?,
    onMovieClick: (Movie) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel = koinViewModel()
) {
    val dimens = LocalTvDimens.current

    // collectAsStateWithLifecycle, not collectAsState: it stops collecting when
    // the screen is not STARTED. On TV this matters more than on a phone because
    // launching the player leaves this screen composed but invisible, and you do
    // not want it doing work behind full-screen video.
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Keyed on the filter so switching drawer sections triggers a reload.
    LaunchedEffect(categoryFilter) { viewModel.load(categoryFilter) }

    // The card the hero panel describes. Seeded from the first row so the panel
    // is never blank before the user moves the D-pad.
    var focusedMovie by remember(categoryFilter) { mutableStateOf<Movie?>(null) }
    val heroMovie = focusedMovie ?: state.rows.firstOrNull()?.movies?.firstOrNull()

    val firstCardFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.rows.isNotEmpty()) {
        if (state.rows.isNotEmpty()) {
            // The composable may not be placed yet on the frame this fires.
            // requestFocus() throws IllegalStateException in that case; the
            // recovery is "focus stays put", which is survivable, whereas the
            // crash is not.
            runCatching { firstCardFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvColors.Background)
    ) {
        // A soft wash tinted by the focused card. Cheap, and it makes the whole
        // screen respond to focus rather than just the card itself.
        AnimatedVisibility(
            visible = heroMovie != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                androidx.compose.ui.graphics.Color(
                                    heroMovie?.accentArgb ?: 0xFF1A2340
                                ).copy(alpha = 0.30f),
                                TvColors.Background
                            )
                        )
                    )
            )
        }

        when {
            state.isLoading -> CenteredMessage("Loading catalogue…")

            state.error != null -> CenteredMessage(
                state.error ?: "Something went wrong",
                isError = true
            )

            state.rows.isEmpty() -> CenteredMessage("Nothing here yet")

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(dimens.rowSpacing),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 24.dp,
                    bottom = 48.dp
                )
            ) {
                item(key = "top-bar") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.screenPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // The "small search on top". It is a focusable control
                        // in normal reading order, so pressing UP from the first
                        // row reaches it -- no special-casing needed.
                        SearchPill(
                            query = "",
                            onClick = onSearchClick,
                            width = 320.dp
                        )
                        Text(
                            text = categoryFilter?.label ?: "Home",
                            style = MaterialTheme.typography.titleLarge,
                            color = TvColors.OnSurfaceMuted
                        )
                    }
                }

                item(key = "hero") {
                    HeroPanel(movie = heroMovie)
                }

                items(items = state.rows, key = { it.title }) { row ->
                    MovieCarouselRow(
                        title = row.title,
                        movies = row.movies,
                        onMovieClick = onMovieClick,
                        onMovieFocused = { focusedMovie = it },
                        firstItemFocusRequester =
                            if (row.title == state.rows.first().title) {
                                firstCardFocusRequester
                            } else {
                                null
                            }
                    )
                }
            }
        }
    }
}

/**
 * The detail block above the rows. Updates on focus change, not on click.
 *
 * Note the fixed [height]. Without it the panel grows and shrinks as
 * descriptions of different lengths scroll past, which shoves every row below
 * it up and down while the user is trying to aim at a card -- one of those bugs
 * that is invisible in a screenshot and unbearable in motion.
 */
@Composable
private fun HeroPanel(movie: Movie?, modifier: Modifier = Modifier) {
    val dimens = LocalTvDimens.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(horizontal = dimens.screenPadding),
        verticalArrangement = Arrangement.Center
    ) {
        if (movie == null) {
            Spacer(Modifier.height(1.dp))
            return@Column
        }

        Text(
            text = movie.title,
            style = MaterialTheme.typography.displayMedium,
            color = TvColors.OnBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetaChip(movie.year.toString())
            MetaChip(movie.durationLabel)
            MetaChip(movie.studio)
            movie.badge?.let { MetaChip(it, highlight = true) }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = movie.description,
            style = MaterialTheme.typography.bodyLarge,
            color = TvColors.OnSurfaceMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // Capped width: full-bleed body text on a 55" screen forces the eye
            // to track too far and is measurably harder to read.
            modifier = Modifier.width(820.dp)
        )
    }
}

@Composable
private fun MetaChip(text: String, highlight: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (highlight) TvColors.Secondary else TvColors.OnSurfaceMuted
    )
}

@Composable
private fun CenteredMessage(text: String, isError: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = if (isError) TvColors.Error else TvColors.OnSurfaceMuted
        )
    }
}
