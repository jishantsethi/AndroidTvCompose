package com.ss.tvcompose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * ============================================================================
 *  THE SINGLE MOST IMPORTANT IMPORT IN THIS FILE
 * ============================================================================
 *
 *      import androidx.tv.material3.MaterialTheme        <-- TV
 *  NOT import androidx.compose.material3.MaterialTheme   <-- phone
 *
 * They are two different classes with nearly identical names and APIs. If you
 * mix them the code still compiles and still runs, but every tv-material
 * component (Card, Surface, Button, NavigationDrawer) silently falls back to
 * defaults and you lose focus scale, glow and border. That is *the* classic
 * Compose-for-TV bug and it costs people an afternoon every time.
 *
 * Rule of thumb for this project: nothing under `ui/` imports
 * `androidx.compose.material3.*`. Icons come from
 * `androidx.compose.material.icons` (that one is shared and fine), everything
 * else themed comes from `androidx.tv.material3`.
 *
 * ============================================================================
 *  WHY THERE IS NO LIGHT THEME
 * ============================================================================
 * TV UIs are dark. Always. A light TV UI in a dim living room is unusable, and
 * Android TV itself has no system light/dark toggle for apps. [isSystemInDarkTheme]
 * is deliberately ignored -- we read it only so the intent is explicit rather
 * than accidental.
 */
private val TvDarkColorScheme = darkColorScheme(
    primary = TvColors.Primary,
    onPrimary = TvColors.OnPrimary,
    primaryContainer = TvColors.PrimaryDim,
    onPrimaryContainer = TvColors.OnBackground,

    secondary = TvColors.Secondary,
    onSecondary = TvColors.OnPrimary,

    background = TvColors.Background,
    onBackground = TvColors.OnBackground,

    surface = TvColors.Surface,
    onSurface = TvColors.OnSurface,
    surfaceVariant = TvColors.SurfaceVariant,
    onSurfaceVariant = TvColors.OnSurfaceMuted,

    // tv-material draws its focused-container fill from these. Leave them at
    // the defaults and a focused card is barely distinguishable from a resting
    // one on a real panel.
    inverseSurface = TvColors.OnSurface,
    inverseOnSurface = TvColors.Background,

    border = TvColors.FocusBorder,
    borderVariant = TvColors.PrimaryDim,

    error = TvColors.Error,
    onError = TvColors.OnPrimary
)

/**
 * Dimensions that recur across screens. Held in a CompositionLocal rather than
 * as top-level constants so a future "compact TV" variant can override them in
 * one place.
 */
data class TvDimens(
    /**
     * OVERSCAN.
     *
     * Older TVs and many set-top boxes crop 3-5% off every edge of the picture.
     * Anything you draw inside that margin may simply not be on screen for the
     * viewer. Every root-level screen in this app pads by [screenPadding] for
     * exactly this reason -- it is not a taste decision, it is the reason your
     * beautifully edge-aligned layout gets a bug report saying "the first card
     * is cut in half".
     */
    val screenPadding: Dp = 48.dp,
    /**
     * THE VERTICAL BUDGET.
     *
     * A 1080p TV is 1920x1080 px at xhdpi, which is **960 x 540 dp**. That is
     * all the height you get, and it is far less than a modern phone in
     * landscape. The browse screen has to fit, above the fold:
     *
     *     top padding      24
     *     search bar       56
     *     spacing          24
     *     hero panel      160
     *     spacing          24
     *     row title        44
     *     one card row    192   (8 + 124 poster + 52 text + 8)
     *     ─────────────────────
     *                     524 dp   of 540
     *
     * Every value below is sized against that sum. Increase any of them and the
     * first row of cards drops below the fold, so the app opens on a hero panel
     * with no visible content -- which looks broken even though nothing is.
     * Check the arithmetic before changing one.
     */
    val rowSpacing: Dp = 24.dp,
    val cardSpacing: Dp = 16.dp,
    val posterWidth: Dp = 220.dp,
    val posterHeight: Dp = 124.dp,
    val drawerCollapsedWidth: Dp = 84.dp,
    val drawerExpandedWidth: Dp = 268.dp
)

val LocalTvDimens = staticCompositionLocalOf { TvDimens() }

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme() // read and ignored on purpose -- see the KDoc above.

    CompositionLocalProvider(LocalTvDimens provides TvDimens()) {
        MaterialTheme(
            colorScheme = TvDarkColorScheme,
            typography = TvTypography,
            content = content
        )
    }
}
