package com.ss.tvcompose.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
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
 * ============================================================================
 *  THE TV ON-SCREEN KEYBOARD
 * ============================================================================
 *
 * WHY THIS EXISTS AT ALL
 * ----------------------
 * You cannot use a normal `TextField` + soft IME on Android TV and get a usable
 * result. What goes wrong:
 *
 *   - Many TV devices ship no IME at all, or ship a manufacturer one that
 *     covers most of the screen and steals D-pad events unpredictably.
 *   - The IME that *is* there is designed for a remote's LEFT/RIGHT/OK, and
 *     wraps around at edges in ways that fight your own layout's focus search.
 *   - You have no control over its appearance, so it will not match your app.
 *
 * Every serious TV app draws its own keyboard. Leanback shipped
 * `SearchSupportFragment` which wrapped a speech/IME flow; in Compose you just
 * build the grid, and it is roughly a hundred lines.
 *
 * WHY IT NEEDS ALMOST NO FOCUS CODE
 * ---------------------------------
 * This is the part that surprises people coming from Leanback. The keys are
 * laid out in a real spatial grid (Column of Rows). Compose's focus search is
 * *two-dimensional and geometric*: pressing RIGHT looks for the nearest
 * focusable whose bounds lie to the right. Because the keys physically sit in a
 * grid, D-pad navigation between them already works. There is no adapter, no
 * `nextFocusRightId`, no key handler. The only focus code below is the initial
 * `requestFocus` and a `focusRestorer` so re-opening the keyboard returns you
 * to the key you were on.
 *
 * ----------------------------------------------------------------------------
 *  SIZING: 7 COLUMNS x 6 ROWS OF 48 dp, AND WHY THOSE NUMBERS
 * ----------------------------------------------------------------------------
 * A 1080p TV is 960 x 540 dp. The search screen has to fit, vertically:
 *
 *     screen padding (top)   40
 *     search pill            56
 *     spacing                28
 *     keyboard              318   (6 rows x 48 + 5 gaps x 6)
 *     legend                 22
 *     ────────────────────────────
 *                           464 dp   of 540
 *
 * The obvious 6x6 grid of 56 dp keys plus a separate action row comes to about
 * 564 dp and puts Space and Done *below the bottom of the screen*, where the
 * user cannot reach them and -- worse -- cannot see that they exist. Do this
 * arithmetic against 540 dp, not against whatever emulator you have open; a
 * phone in landscape is a different shape and will not show you the bug.
 *
 * 48 dp is the floor for a key. Below that, aiming with a D-pad at speed
 * becomes error-prone because the focus target is visually ambiguous.
 *
 * 36 characters (A-Z plus 0-9) over 7 columns fills five rows exactly and
 * leaves one character over, so the final row is that character plus the four
 * action keys. No wasted space, and no separate action row to fall off the edge.
 *
 * OTHER DESIGN NOTES
 * ------------------
 *  - Alphabetical, not QWERTY. On a D-pad users *scan* for a letter rather than
 *    touch-type, and A-Z ordering measurably beats QWERTY for scan time. Almost
 *    every TV platform keyboard is alphabetical for this reason.
 *  - The action keys are icon-only so they fit the grid width. Their glyphs are
 *    standard, and the legend line underneath names them, so nothing depends on
 *    the user recognising an icon.
 */

/** A-Z then 0-8, seven per row. The leftover '9' starts the action row. */
private val KEY_ROWS = listOf(
    "ABCDEFG",
    "HIJKLMN",
    "OPQRSTU",
    "VWXYZ01",
    "2345678"
)
private const val LEFTOVER_KEY = '9'

private val KEY_SIZE = 48.dp
private val KEY_GAP = 6.dp

/** Grid width minus the '9' key and one gap, split four ways. */
private val ACTION_KEY_WIDTH = 75.dp

@Composable
fun OnScreenKeyboard(
    onCharacter: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** Set true the frame the keyboard becomes visible so it grabs the D-pad. */
    requestInitialFocus: Boolean = true
) {
    // Focus is handed to the first key when the keyboard appears. Skip this and
    // the keyboard opens with focus still on the search bar behind it -- the
    // user presses DOWN, focus jumps somewhere unrelated, and the keyboard
    // looks broken.
    val firstKeyFocusRequester = remember { FocusRequester() }

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            // requestFocus() throws if the target has not been placed yet, and
            // on a slow TV SoC the first composition can land after this
            // effect. Swallowing it is correct: the worst case is that focus
            // stays where it was, which is recoverable, whereas a crash is not.
            runCatching { firstKeyFocusRequester.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            // focusGroup() makes the whole keyboard one focus target from the
            // outside, so DOWN from the search bar enters it as a unit...
            .focusGroup()
            // ...and focusRestorer() means re-entering returns to the last key
            // you used rather than resetting to 'A'.
            .focusRestorer(),
        verticalArrangement = Arrangement.spacedBy(KEY_GAP)
    ) {
        KEY_ROWS.forEachIndexed { rowIndex, rowChars ->
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                rowChars.forEachIndexed { colIndex, char ->
                    KeyboardKey(
                        onClick = { onCharacter(char) },
                        modifier = if (rowIndex == 0 && colIndex == 0) {
                            Modifier.focusRequester(firstKeyFocusRequester)
                        } else {
                            Modifier
                        }
                    ) {
                        Text(
                            text = char.toString(),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        // Final row: the leftover character, then the four actions.
        Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
            KeyboardKey(onClick = { onCharacter(LEFTOVER_KEY) }) {
                Text(
                    text = LEFTOVER_KEY.toString(),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            ActionKey(Icons.Outlined.SpaceBar, "Space") { onCharacter(' ') }
            ActionKey(Icons.AutoMirrored.Outlined.Backspace, "Delete", onBackspace)
            ActionKey(Icons.Outlined.DeleteOutline, "Clear all", onClear)
            ActionKey(Icons.Outlined.Check, "Done", onDone)
        }

        // Names the icon-only keys, so nothing depends on the user recognising
        // a glyph. Cheap in vertical space, and it removes the only guess in
        // the whole layout.
        Text(
            text = "space   ·   delete   ·   clear   ·   done",
            style = MaterialTheme.typography.labelMedium,
            color = TvColors.OnSurfaceMuted,
            modifier = Modifier.padding(top = 2.dp, start = KEY_SIZE + KEY_GAP)
        )
    }
}

/**
 * One key.
 *
 * Uses tv-material's clickable [Surface] rather than a plain Box + clickable:
 * Surface brings the focused container colour, border, scale and glow for free,
 * and -- importantly -- it is already wired so that the D-pad CENTER / ENTER /
 * NUMPAD_ENTER keys all fire `onClick`. Rolling your own with
 * `Modifier.clickable` works on ENTER but silently misses DPAD_CENTER on some
 * remotes, which is a bug you will only find on real hardware.
 */
@Composable
private fun KeyboardKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = KEY_SIZE,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.width(width).height(KEY_SIZE),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvColors.SurfaceVariant,
            contentColor = TvColors.OnSurface,
            focusedContainerColor = TvColors.Primary,
            focusedContentColor = TvColors.OnPrimary,
            pressedContainerColor = TvColors.PrimaryDim,
            pressedContentColor = TvColors.OnBackground
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, TvColors.FocusBorder), shape = shape)
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = TvColors.FocusGlow, elevation = 8.dp)
        )
    ) {
        Box(
            modifier = Modifier.height(KEY_SIZE).width(width),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/** A wider, icon-only key. [label] is its accessibility description. */
@Composable
private fun ActionKey(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    KeyboardKey(onClick = onClick, width = ACTION_KEY_WIDTH) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp)
        )
    }
}
