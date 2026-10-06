package com.ss.tvcompose.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.ss.tvcompose.ui.theme.TvColors
import java.util.concurrent.TimeUnit

/**
 * The transport bar.
 *
 * WHY EVERY BUTTON HERE IS ALSO A KEY SHORTCUT
 * --------------------------------------------
 * These buttons duplicate what LEFT / RIGHT / OK already do from the raw key
 * handler in PlayerScreen. That redundancy is intentional and standard on TV:
 *
 *   - The keys are for the user who knows the app. No focus movement, no
 *     visual hunting -- press RIGHT, it seeks.
 *   - The buttons are for the user who does not, and for the affordances that
 *     have no dedicated remote key at all (audio track, subtitles, replay).
 *
 * The bar is wrapped in `focusRestorer()` so that leaving it and coming back
 * returns to the same button.
 *
 * THE GRADIENT SCRIM
 * ------------------
 * Controls sit over arbitrary video. White-on-bright-sky is unreadable, so the
 * bar carries its own vertical scrim rather than relying on the content behind
 * it being dark. This is the same reason broadcast lower-thirds always have a
 * plate behind them.
 */
@Composable
fun PlayerControls(
    state: PlayerUiState,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onRestart: () -> Unit,
    onOpenAudioMenu: () -> Unit,
    onOpenSubtitleMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playPauseFocusRequester = remember { FocusRequester() }

    // Play/pause takes focus when the bar appears -- the most likely next
    // action, so the user can act without moving the D-pad at all.
    LaunchedEffect(Unit) { runCatching { playPauseFocusRequester.requestFocus() } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        androidx.compose.ui.graphics.Color.Transparent,
                        TvColors.Background.copy(alpha = 0.75f),
                        TvColors.Background.copy(alpha = 0.96f)
                    )
                )
            )
            .padding(horizontal = 48.dp)
            .padding(top = 48.dp, bottom = 36.dp)
    ) {
        state.movie?.let { movie ->
            Text(
                text = movie.title,
                style = MaterialTheme.typography.headlineLarge,
                color = TvColors.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    movie.studio,
                    movie.year.toString(),
                    movie.badge
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = TvColors.OnSurfaceMuted
            )
            Spacer(Modifier.height(18.dp))
        }

        SeekBar(state = state)

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(state.positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = TvColors.OnSurface
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "/  ${formatTime(state.durationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = TvColors.OnSurfaceMuted
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(
                icon = Icons.Outlined.Replay10,
                label = "Back 10s",
                onClick = onSeekBack
            )
            ControlButton(
                icon = if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                label = if (state.isPlaying) "Pause" else "Play",
                onClick = onPlayPause,
                modifier = Modifier.focusRequester(playPauseFocusRequester)
            )
            ControlButton(
                icon = Icons.Outlined.Forward10,
                label = "Forward 10s",
                onClick = onSeekForward
            )
            ControlButton(
                icon = Icons.Outlined.Replay,
                label = "Restart",
                onClick = onRestart
            )

            Spacer(Modifier.width(20.dp))

            // Track pickers are hidden when the stream has nothing to pick.
            // A menu that opens onto one greyed row is worse than no menu.
            if (state.audioTracks.size > 1) {
                ControlButton(
                    icon = Icons.Outlined.VolumeUp,
                    label = "Audio (${state.audioTracks.size})",
                    onClick = onOpenAudioMenu
                )
            }
            if (state.subtitleTracks.isNotEmpty()) {
                ControlButton(
                    icon = Icons.Outlined.ClosedCaption,
                    label = "Subtitles (${state.subtitleTracks.size})",
                    onClick = onOpenSubtitleMenu
                )
            }
        }
    }
}

/**
 * Progress bar with a buffered-ahead track.
 *
 * Drawn rather than using a Slider: a Material Slider on TV wants drag
 * gestures, renders a thumb sized for a fingertip, and its focus behaviour
 * fights the raw key handling on this screen. Seeking here is done with the
 * D-pad, so the bar only has to *display*.
 *
 * It is 8 dp tall. A 2 dp bar disappears entirely at three metres.
 */
@Composable
private fun SeekBar(state: PlayerUiState, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(TvColors.OnSurfaceMuted.copy(alpha = 0.28f))
    ) {
        // Buffered ahead, behind the played portion.
        Box(
            modifier = Modifier
                .fillMaxWidth(state.bufferedProgress)
                .fillMaxHeight()
                .background(TvColors.OnSurfaceMuted.copy(alpha = 0.55f))
        )
        // Played.
        Box(
            modifier = Modifier
                .fillMaxWidth(state.progress)
                .fillMaxHeight()
                .background(TvColors.Primary)
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvColors.SurfaceVariant.copy(alpha = 0.85f),
            contentColor = TvColors.OnSurface,
            focusedContainerColor = TvColors.OnSurface,
            focusedContentColor = TvColors.Background
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, TvColors.FocusBorder), shape = shape)
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = TvColors.FocusGlow, elevation = 10.dp)
        )
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * The audio / subtitle picker.
 *
 * A side panel rather than a centred dialog, for two reasons: the video stays
 * visible so the user can hear the effect of an audio change immediately, and
 * being in the same focus tree means D-pad navigation into and out of it needs
 * no wiring (see the note in SearchScreen about Popup windows and focus roots).
 */
@Composable
fun TrackSelectionPanel(
    menu: TrackMenu,
    state: PlayerUiState,
    onSelect: (trackType: Int, option: TrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (menu == TrackMenu.NONE) return

    val isAudio = menu == TrackMenu.AUDIO
    val options = if (isAudio) state.audioTracks else state.subtitleTracks
    val trackType = if (isAudio) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT

    val firstOptionFocusRequester = remember { FocusRequester() }
    LaunchedEffect(menu) { runCatching { firstOptionFocusRequester.requestFocus() } }

    Column(
        modifier = modifier
            .width(380.dp)
            .background(TvColors.Surface.copy(alpha = 0.97f), RoundedCornerShape(16.dp))
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
            .focusRestorer(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (isAudio) "Audio track" else "Subtitles",
            style = MaterialTheme.typography.titleLarge,
            color = TvColors.OnBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (!isAudio) {
            TrackRow(
                label = "Off",
                isSelected = state.subtitleTracks.none { it.isSelected },
                onClick = onDisableSubtitles,
                modifier = Modifier.focusRequester(firstOptionFocusRequester)
            )
        }

        options.forEachIndexed { index, option ->
            TrackRow(
                label = option.label,
                isSelected = option.isSelected,
                onClick = { onSelect(trackType, option) },
                modifier = if (isAudio && index == 0) {
                    Modifier.focusRequester(firstOptionFocusRequester)
                } else {
                    Modifier
                }
            )
        }

        Spacer(Modifier.height(8.dp))
        // showGlyph = false: "Close" is an action, not a track. Giving it a
        // radio circle like the rows above implies picking it selects
        // something, which is exactly the wrong hint.
        TrackRow(
            label = "Close",
            isSelected = false,
            onClick = onDismiss,
            showGlyph = false
        )
    }
}

@Composable
private fun TrackRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showGlyph: Boolean = true
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) TvColors.PrimaryDim else TvColors.SurfaceVariant,
            contentColor = TvColors.OnSurface,
            focusedContainerColor = TvColors.Primary,
            focusedContentColor = TvColors.OnPrimary
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, TvColors.FocusBorder), shape = shape)
        )
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showGlyph) {
                Text(
                    text = if (isSelected) "●" else "○",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PlaybackError(
    message: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Box(
        modifier = modifier.background(TvColors.Background.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Playback failed",
                style = MaterialTheme.typography.headlineLarge,
                color = TvColors.Error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TvColors.OnSurfaceMuted,
                modifier = Modifier.width(620.dp)
            )
            Spacer(Modifier.height(8.dp))
            ControlButton(
                icon = Icons.Outlined.Replay,
                label = "Back to browse",
                onClick = onExit,
                modifier = Modifier.focusRequester(focusRequester)
            )
        }
    }
}

/** mm:ss, or h:mm:ss past an hour. Never shows a bare "0" for unknown duration. */
private fun formatTime(millis: Long): String {
    if (millis <= 0L) return "0:00"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}
