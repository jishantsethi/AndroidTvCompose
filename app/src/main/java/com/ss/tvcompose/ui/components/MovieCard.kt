package com.ss.tvcompose.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.ss.tvcompose.data.Movie
import com.ss.tvcompose.ui.theme.LocalTvDimens
import com.ss.tvcompose.ui.theme.TvColors

/**
 * ============================================================================
 *  A single catalogue tile. This is the Compose replacement for a Leanback
 *  `Presenter` + `ImageCardView` + `ViewHolder` triple.
 * ============================================================================
 *
 * In Leanback you wrote a Presenter subclass, overrode onCreateViewHolder /
 * onBindViewHolder / onUnbindViewHolder, registered it against a
 * ClassPresenterSelector, and the focus visuals came from ImageCardView's own
 * styling that you fought with via themes. Here it is one function, and focus
 * state is a value you can read.
 *
 * ----------------------------------------------------------------------------
 *  THE THREE FOCUS SIGNALS, AND WHY ALL THREE
 * ----------------------------------------------------------------------------
 * `Card` from tv-material gives you three independent focus affordances, and
 * this app uses every one of them on purpose:
 *
 *   scale  - the card grows 8%. Motion is the fastest thing peripheral vision
 *            picks up, so this is what tells you focus MOVED.
 *   border - a thick light outline. This is what tells you WHERE focus is when
 *            you glance back at a still screen.
 *   glow   - a coloured drop shadow. This survives on washed-out or badly
 *            calibrated panels where a thin border can disappear into the
 *            poster art behind it.
 *
 * Shipping only one of them is the most common reason a TV app tests fine on a
 * monitor and gets "I can't tell what's selected" feedback from a living room.
 *
 * ----------------------------------------------------------------------------
 *  WHY THE GRADIENT UNDERNEATH THE IMAGE
 * ----------------------------------------------------------------------------
 * Posters come off the network. During the fetch -- and permanently, if the
 * fetch fails -- `AsyncImage` draws nothing. Without the gradient plate behind
 * it a scrolling row shows a line of empty holes, which reads as a crash. The
 * gradient is derived from the movie's own accent colour so even the fallback
 * looks deliberate.
 *
 * @param onFocused fired when this card takes focus. The browse screen uses it
 *   to drive the hero detail panel: on TV the focused item, not the clicked
 *   item, is what the UI describes. This is the "focus is the new hover"
 *   principle, and getting it right is most of what makes a TV app feel native.
 */
@Composable
fun MovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (Movie) -> Unit = {}
) {
    val dimens = LocalTvDimens.current
    val shape = RoundedCornerShape(12.dp)

    // Local mirror of the focus state so we can restyle our own children.
    // tv-material handles the container's own focus visuals; anything *inside*
    // the card (the title colour here) has to react by itself.
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(dimens.posterWidth)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) onFocused(movie)
            },
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(
            containerColor = TvColors.SurfaceVariant,
            contentColor = TvColors.OnSurface,
            focusedContainerColor = TvColors.SurfaceElevated,
            focusedContentColor = TvColors.OnBackground
        ),
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1.08f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, TvColors.FocusBorder),
                shape = shape
            )
        ),
        glow = CardDefaults.glow(
            focusedGlow = Glow(elevationColor = TvColors.FocusGlow, elevation = 14.dp)
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.posterHeight)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                // Fallback plate. Always drawn; the poster covers it once loaded.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(movie.accentArgb),
                                    Color(movie.accentArgb).copy(alpha = 0.35f)
                                )
                            )
                        )
                )
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = movie.title,
                    modifier = Modifier.fillMaxSize(),
                    // Crop, never Fit: a letterboxed poster inside a card leaves
                    // mismatched bars that look like a rendering bug on TV.
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFocused) TvColors.OnBackground else TvColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${movie.year}  ·  ${movie.durationLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TvColors.OnSurfaceMuted,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Compact variant used by the search results grid, where the metadata line
 * would compete with the query and the on-screen keyboard for attention.
 */
@Composable
fun MoviePosterTile(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (Movie) -> Unit = {}
) {
    val shape = RoundedCornerShape(10.dp)
    Card(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused(movie) },
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(
            containerColor = TvColors.SurfaceVariant,
            focusedContainerColor = TvColors.SurfaceElevated
        ),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, TvColors.FocusBorder), shape = shape)
        ),
        glow = CardDefaults.glow(
            focusedGlow = Glow(elevationColor = TvColors.FocusGlow, elevation = 10.dp)
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(110.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(movie.accentArgb),
                                Color(movie.accentArgb).copy(alpha = 0.3f)
                            )
                        )
                    )
            )
            AsyncImage(
                model = movie.posterUrl,
                contentDescription = movie.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Bottom scrim so the title stays legible over any poster.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to TvColors.Background.copy(alpha = 0.92f)
                        )
                    )
            )
            Text(
                text = movie.title,
                style = MaterialTheme.typography.labelLarge,
                color = TvColors.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}
