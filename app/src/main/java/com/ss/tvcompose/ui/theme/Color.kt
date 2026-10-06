package com.ss.tvcompose.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A 10-foot palette.
 *
 * TV colour rules that differ from a phone, and are easy to get wrong:
 *
 * 1. NEVER pure black (#000000) and never pure white (#FFFFFF) for large areas.
 *    OLED panels smear on pure black transitions, and full-white surfaces at
 *    TV brightness across a 55" panel are physically uncomfortable in a dark
 *    room. [Background] is a very dark blue-grey; [OnSurface] is off-white.
 *
 * 2. Contrast targets are *higher* than on a phone because the viewer is three
 *    metres away and half the TVs in the world are miscalibrated. Body text
 *    here is > 12:1 against the background.
 *
 * 3. The focused state has to be readable from across the room. A subtle 2 dp
 *    outline that looks tasteful on a laptop disappears on a TV. [FocusBorder]
 *    is deliberately loud, and cards also scale and glow -- three redundant
 *    signals, because on TV "which thing is selected" must never be a guess.
 */
object TvColors {
    val Background = Color(0xFF0B0D12)
    val Surface = Color(0xFF141821)
    val SurfaceVariant = Color(0xFF1D2331)
    val SurfaceElevated = Color(0xFF232A3A)

    val Primary = Color(0xFF4F8DFD)
    val PrimaryDim = Color(0xFF2C5AA8)
    val Secondary = Color(0xFF7BE3C8)

    val OnBackground = Color(0xFFEDF1F8)
    val OnSurface = Color(0xFFE6EAF2)
    /** Captions and metadata. Still ~7:1 on [Surface]; do not darken further. */
    val OnSurfaceMuted = Color(0xFFA5AFC4)
    val OnPrimary = Color(0xFF06101F)

    val FocusBorder = Color(0xFFFFFFFF)
    val FocusGlow = Color(0xFF4F8DFD)

    val Error = Color(0xFFFF6B6B)
    val Scrim = Color(0xCC05070C)
}
