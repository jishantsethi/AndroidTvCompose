package com.ss.tvcompose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ss.tvcompose.data.Movie
import com.ss.tvcompose.ui.theme.LocalTvDimens
import com.ss.tvcompose.ui.theme.TvColors

/**
 * ============================================================================
 *  A titled horizontal strip of cards -- Leanback's `ListRow` + `ListRowPresenter`.
 * ============================================================================
 *
 * ----------------------------------------------------------------------------
 *  THE ONE THING THAT MATTERS HERE: Modifier.focusRestorer()
 * ----------------------------------------------------------------------------
 * Without it, this is what happens, and it is maddening to debug because
 * nothing errors:
 *
 *   1. User is on card 7 of the "Movies" row.
 *   2. They press DOWN into the "Series" row.
 *   3. They press UP to come back.
 *   4. Focus lands on card 1. The row has scrolled itself back to the start.
 *
 * `focusRestorer()` records which child last held focus in this group and
 * hands focus back to it when the group is re-entered. Leanback did this for
 * you inside HorizontalGridView, which is exactly why people porting from
 * Leanback do not think to look for it -- they only notice the behaviour is
 * gone. One modifier, and the row behaves like every TV app the user knows.
 *
 * ----------------------------------------------------------------------------
 *  WHY PLAIN LazyRow AND NOT TvLazyRow
 * ----------------------------------------------------------------------------
 * `androidx.tv:tv-foundation` (TvLazyRow / TvLazyColumn) is frozen and
 * deprecated. Its TV-specific focus and scroll handling was upstreamed into
 * ordinary `androidx.compose.foundation` lazy lists, which now do the right
 * thing: they scroll the focused item into view and keep focus attached across
 * recomposition. If a tutorial tells you to add tv-foundation, it predates that
 * merge. Do not add it -- the two implementations fight each other.
 *
 * ----------------------------------------------------------------------------
 *  contentPadding, NOT padding
 * ----------------------------------------------------------------------------
 * `contentPadding` is inside the scrollable area, so the first card starts
 * clear of the overscan margin AND can still scroll all the way to the edge.
 * `Modifier.padding` would shrink the viewport instead, clipping the focus
 * scale/glow of the first and last cards -- a subtle, ugly bug where the
 * outermost card looks flat while every other card grows.
 */
@Composable
fun MovieCarouselRow(
    title: String,
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    modifier: Modifier = Modifier,
    onMovieFocused: (Movie) -> Unit = {},
    /**
     * Supplied only for the first row on the screen, which needs to claim
     * initial focus so the user is never looking at a screen with nothing
     * highlighted. See BrowseScreen for how it is requested.
     */
    firstItemFocusRequester: FocusRequester? = null
) {
    val dimens = LocalTvDimens.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = TvColors.OnBackground,
            modifier = Modifier.padding(
                start = dimens.screenPadding,
                bottom = 10.dp
            )
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                // Order matters: focusRestorer() must wrap the lazy row so it
                // sees the children as its focus group.
                .focusRestorer(),
            contentPadding = PaddingValues(
                start = dimens.screenPadding,
                end = dimens.screenPadding,
                // Vertical breathing room so the 8% focus scale is not clipped
                // by the row's own bounds.
                top = 8.dp,
                bottom = 8.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
        ) {
            items(items = movies, key = { it.id }) { movie ->
                MovieCard(
                    movie = movie,
                    onClick = { onMovieClick(movie) },
                    onFocused = onMovieFocused,
                    modifier = if (
                        firstItemFocusRequester != null && movie.id == movies.first().id
                    ) {
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}
