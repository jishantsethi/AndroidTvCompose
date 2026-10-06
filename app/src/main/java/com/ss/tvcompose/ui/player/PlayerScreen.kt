package com.ss.tvcompose.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ss.tvcompose.ui.theme.TvColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** How far one LEFT/RIGHT press seeks. 10 s is the platform convention. */
private const val SEEK_STEP_MS = 10_000L

/** Controls hide after this long with no input. */
private const val CONTROLS_TIMEOUT_MS = 4_000L

/**
 * ============================================================================
 *  THE PLAYER SCREEN
 * ============================================================================
 *
 * WHY THERE IS A RAW KEY HANDLER HERE AND NOWHERE ELSE
 * ----------------------------------------------------
 * Everywhere else in this app, the D-pad moves focus between controls and that
 * is exactly right. During playback it is exactly wrong: the screen is one
 * video, and LEFT/RIGHT must scrub rather than hunt for a button. So this is the
 * one screen that intercepts keys before focus search sees them.
 *
 * `onPreviewKeyEvent` runs on the way DOWN the tree, before children and before
 * focus search. Using `onKeyEvent` instead would let focus search consume the
 * arrow keys first and your seek would never fire.
 *
 * Return `true` to say "handled, stop here"; return `false` to let it through.
 * BACK returns false so the system and `BackHandler` still see it -- swallowing
 * BACK is how you ship an app nobody can exit.
 *
 * MEDIA KEYS ARE NOT OPTIONAL
 * ---------------------------
 * Real remotes have dedicated transport keys, and many TVs send only those --
 * never DPAD_CENTER -- when the user presses the play button. Handling
 * MediaPlayPause / MediaPlay / MediaPause / MediaFastForward / MediaRewind is
 * what makes the app work with the remote in the user's hand rather than only
 * with the emulator's arrow keys.
 *
 * PLAYERSURFACE, NOT AndroidView(PlayerView)
 * ------------------------------------------
 * The old approach wrapped Leanback's or media3's `PlayerView` in an
 * `AndroidView`. That drags in a whole View-based control overlay with its own
 * focus handling which then fights Compose's. `PlayerSurface` from
 * `media3-ui-compose` is just the video output -- a SurfaceView, nothing else --
 * so every control on top is ours and there is exactly one focus system in play.
 *
 * SURFACE_TYPE_SURFACE_VIEW is the right choice on TV: a SurfaceView gets its
 * own hardware layer and can hit the display composer directly, which on cheap
 * TV SoCs is the difference between smooth 1080p and dropped frames. TextureView
 * is only worth it when you need to animate or transform the video itself.
 */
@Composable
fun PlayerScreen(
    movieId: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: PlayerViewModel = koinViewModel { parametersOf(movieId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    var controlsVisible by remember { mutableStateOf(true) }
    // Bumped on every input. The auto-hide effect keys off it, so each new
    // keypress restarts the timeout instead of stacking timers.
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var trackMenu by remember { mutableStateOf(TrackMenu.NONE) }

    val focusRequester = remember { FocusRequester() }

    // Claim focus so key events arrive at all. A focusable that never receives
    // focus gets no key events, and the screen looks completely dead.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // Auto-hide. Suspended while a track menu is open, otherwise the menu
    // vanishes under the user mid-selection.
    LaunchedEffect(lastInteraction, controlsVisible, trackMenu) {
        if (controlsVisible && trackMenu == TrackMenu.NONE) {
            kotlinx.coroutines.delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    // Pause when the screen leaves the foreground. Without this, backgrounding
    // the app leaves audio playing over the TV home screen -- which users
    // reliably report as a bug, and rightly so.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.pauseForBackground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // BACK closes an open menu first, then the controls, then leaves. Each
    // press undoes exactly one layer, which is what users expect.
    BackHandler {
        when {
            trackMenu != TrackMenu.NONE -> trackMenu = TrackMenu.NONE
            controlsVisible -> controlsVisible = false
            else -> onExit()
        }
    }

    /** Any interaction shows the controls and restarts the hide timer. */
    fun touch() {
        controlsVisible = true
        lastInteraction = System.currentTimeMillis()
    }

    val presentationState = rememberPresentationState(viewModel.player)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                // Act on KeyDown only. Handling KeyUp as well double-fires every
                // press -- a 20 s seek from one tap, which looks like a bug in
                // the seek logic and is not.
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // While a menu is open, let the D-pad move focus inside it
                // normally instead of scrubbing behind it.
                if (trackMenu != TrackMenu.NONE) return@onPreviewKeyEvent false

                // ────────────────────────────────────────────────────────────
                //  THE D-PAD MEANS TWO DIFFERENT THINGS ON THIS SCREEN
                // ────────────────────────────────────────────────────────────
                // Controls HIDDEN  -> the screen is just video, so LEFT/RIGHT
                //                     scrub and OK toggles playback.
                // Controls SHOWN   -> the screen now contains a row of buttons,
                //                     so LEFT/RIGHT must move focus along that
                //                     row and OK must activate the focused one.
                //
                // Getting this wrong is subtle and total: if LEFT/RIGHT always
                // seek, the transport row still *renders*, focus still sits on
                // Play, and everything looks correct -- but the Audio and
                // Subtitles buttons at the end of the row can never be reached
                // by any sequence of key presses. Dedicated media keys are
                // unconditional, because a remote's rewind button means rewind
                // no matter what is on screen.
                when (event.key) {
                    Key.MediaRewind -> {
                        touch(); viewModel.seekBy(-SEEK_STEP_MS); true
                    }
                    Key.MediaFastForward -> {
                        touch(); viewModel.seekBy(SEEK_STEP_MS); true
                    }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        touch(); viewModel.togglePlayPause(); true
                    }

                    Key.DirectionLeft -> if (controlsVisible) {
                        // Keep the bar alive while the user walks along it, but
                        // hand the key to focus search.
                        touch(); false
                    } else {
                        touch(); viewModel.seekBy(-SEEK_STEP_MS); true
                    }
                    Key.DirectionRight -> if (controlsVisible) {
                        touch(); false
                    } else {
                        touch(); viewModel.seekBy(SEEK_STEP_MS); true
                    }
                    Key.DirectionCenter, Key.Enter -> if (controlsVisible) {
                        // Let the focused tv-material Surface handle its own
                        // click; swallowing this would make every button inert.
                        false
                    } else {
                        touch(); viewModel.togglePlayPause(); true
                    }
                    Key.Spacebar -> { touch(); viewModel.togglePlayPause(); true }

                    Key.DirectionUp -> {
                        // UP reveals the controls. Once they are visible it
                        // returns false so focus can move into them.
                        if (!controlsVisible) { touch(); true } else false
                    }
                    Key.DirectionDown -> {
                        if (controlsVisible) { controlsVisible = false; true } else false
                    }
                    Key.MediaStop -> { onExit(); true }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // ── Video ────────────────────────────────────────────────────────────
        PlayerSurface(
            player = viewModel.player,
            modifier = Modifier.fillMaxSize(),
            surfaceType = SURFACE_TYPE_SURFACE_VIEW
        )

        // `coverSurface` is true until the first frame is actually rendered.
        // A SurfaceView shows undefined garbage before that, so we paint over
        // it. Skipping this gives a flash of green or of the previous app's
        // buffer on some devices -- brief, but very visible on a big panel.
        if (presentationState.coverSurface) {
            Box(modifier = Modifier.fillMaxSize().background(TvColors.Background))
        }

        // ── Buffering ────────────────────────────────────────────────────────
        if (state.isBuffering && state.error == null) {
            Text(
                text = "Buffering…",
                style = MaterialTheme.typography.headlineMedium,
                color = TvColors.OnBackground
            )
        }

        // ── Error ────────────────────────────────────────────────────────────
        state.error?.let { message ->
            PlaybackError(
                message = message,
                onExit = onExit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Controls ─────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = controlsVisible && state.error == null,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            PlayerControls(
                state = state,
                onPlayPause = { touch(); viewModel.togglePlayPause() },
                onSeekBack = { touch(); viewModel.seekBy(-SEEK_STEP_MS) },
                onSeekForward = { touch(); viewModel.seekBy(SEEK_STEP_MS) },
                onRestart = { touch(); viewModel.restart() },
                onOpenAudioMenu = { trackMenu = TrackMenu.AUDIO },
                onOpenSubtitleMenu = { trackMenu = TrackMenu.SUBTITLES },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // ── Track menus ──────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = trackMenu != TrackMenu.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            TrackSelectionPanel(
                menu = trackMenu,
                state = state,
                onSelect = { trackType, option ->
                    viewModel.selectTrack(trackType, option)
                    trackMenu = TrackMenu.NONE
                    touch()
                },
                onDisableSubtitles = {
                    viewModel.disableTextTracks()
                    trackMenu = TrackMenu.NONE
                    touch()
                },
                onDismiss = { trackMenu = TrackMenu.NONE; touch() }
            )
        }
    }
}

/** Which track picker, if any, is on screen. */
enum class TrackMenu { NONE, AUDIO, SUBTITLES }
