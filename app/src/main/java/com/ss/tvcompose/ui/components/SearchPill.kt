package com.ss.tvcompose.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.ss.tvcompose.ui.theme.TvColors

/**
 * The small search control that sits at the top of the browse screen, and the
 * query display at the top of the search screen.
 *
 * NOTE THAT IT IS A BUTTON, NOT A TEXT FIELD.
 *
 * It looks like an input, but it is a clickable [Surface] that renders whatever
 * string it is given. That is deliberate: on TV a focused `TextField` will try
 * to summon the system IME, which is exactly the behaviour we are avoiding (see
 * OnScreenKeyboard's header for why). Keeping this read-only means:
 *
 *   - focus behaviour is identical to every other control in the app,
 *   - no IME can appear and shove the layout around,
 *   - the "text cursor" is ours to draw, so it can be a fat, high-contrast bar
 *     visible from three metres rather than a 1 px caret.
 *
 * The single source of truth for the query string lives in the ViewModel; this
 * composable only displays it and reports clicks.
 */
@Composable
fun SearchPill(
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    /** Draws the block caret. The search screen sets this while typing. */
    showCaret: Boolean = false,
    width: androidx.compose.ui.unit.Dp = 360.dp
) {
    val shape = RoundedCornerShape(28.dp)

    Surface(
        onClick = onClick,
        modifier = modifier.width(width).height(56.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvColors.SurfaceVariant,
            contentColor = TvColors.OnSurfaceMuted,
            focusedContainerColor = TvColors.OnSurface,
            focusedContentColor = TvColors.Background
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, TvColors.FocusBorder), shape = shape)
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = TvColors.FocusGlow, elevation = 10.dp)
        )
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = query.ifEmpty { placeholder },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showCaret) {
                // A 3 dp block, not a hairline caret. At TV viewing distance a
                // 1 dp caret is simply not visible.
                Surface(
                    modifier = Modifier.width(3.dp).height(24.dp),
                    colors = androidx.tv.material3.SurfaceDefaults.colors(
                        containerColor = TvColors.Primary
                    ),
                    content = {}
                )
            }
        }
    }
}
