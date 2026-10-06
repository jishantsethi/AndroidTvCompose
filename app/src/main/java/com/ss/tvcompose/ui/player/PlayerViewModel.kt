package com.ss.tvcompose.ui.player

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.ss.tvcompose.data.Movie
import com.ss.tvcompose.data.MovieRepository
import com.ss.tvcompose.data.StreamType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One selectable audio (or text) rendition, flattened for the UI. */
data class TrackOption(
    val label: String,
    val isSelected: Boolean,
    val groupIndex: Int,
    val trackIndex: Int
)

data class PlayerUiState(
    val movie: Movie? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = true,
    val positionMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val durationMs: Long = 0L,
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val error: String? = null
) {
    /** 0f..1f, guarded against the unknown-duration case. */
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val bufferedProgress: Float
        get() = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * ============================================================================
 *  THE PLAYER, AND WHY EXOPLAYER LIVES IN THE VIEWMODEL
 * ============================================================================
 *
 * An `ExoPlayer` holds codecs, a surface, buffers and threads. Two rules follow:
 *
 *   1. It must be released exactly once. [onCleared] is the only place in this
 *      class that calls release(), and the ViewModel outlives configuration
 *      changes, so a rotation or a UI-mode switch does not tear down playback
 *      and restart the stream from zero.
 *
 *   2. Only one may be alive at a time on most TV hardware. Cheap TV boxes have
 *      a single hardware video decoder; a second ExoPlayer that has not been
 *      released will fail to acquire it and you get a black screen with no
 *      obvious error. Keeping the instance in one owned place is what prevents
 *      that.
 *
 * WHY POSITION IS POLLED AND EVERYTHING ELSE IS PUSHED
 * ----------------------------------------------------
 * `Player.Listener` is event-driven: it tells you when playback state changes,
 * when tracks change, when an error happens. It does NOT tell you the playhead
 * moved, because that would be a callback per frame.
 *
 * So position is polled -- see [startPositionUpdates]. 250 ms is chosen
 * deliberately: fast enough that a progress bar looks smooth to the eye, slow
 * enough that it costs nothing on a weak SoC. Polling at 16 ms to "match the
 * frame rate" is a classic way to make a TV app stutter, because every tick
 * recomposes the control overlay.
 *
 * The poll loop lives in viewModelScope and checks `isActive`, so it dies with
 * the ViewModel. A loop that outlives its owner is the standard way to leak an
 * ExoPlayer.
 */
@OptIn(UnstableApi::class)
class PlayerViewModel(
    application: Application,
    private val repository: MovieRepository,
    private val movieId: String
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /**
     * Held so the UI can hand it to `PlayerSurface`. Exposed as `Player`, not
     * `ExoPlayer`: the UI has no business calling prepare() or release().
     */
    val player: ExoPlayer = buildPlayer(application)

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.value = _state.value.copy(
                isBuffering = playbackState == Player.STATE_BUFFERING,
                durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            )
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }

        override fun onTracksChanged(tracks: Tracks) {
            // Tracks arrive AFTER the manifest is parsed, which is well after
            // the screen first composes. Reading them in the UI's first frame
            // gets you an empty list; this callback is the only correct moment.
            _state.value = _state.value.copy(
                audioTracks = tracks.extract(C.TRACK_TYPE_AUDIO),
                subtitleTracks = tracks.extract(C.TRACK_TYPE_TEXT)
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                error = "${error.errorCodeName}: ${error.localizedMessage ?: "playback failed"}",
                isBuffering = false
            )
        }
    }

    init {
        player.addListener(listener)
        loadMovie()
        startPositionUpdates()
    }

    // ── Setup ───────────────────────────────────────────────────────────────

    private fun buildPlayer(application: Application): ExoPlayer =
        ExoPlayer.Builder(application)
            .setTrackSelector(DefaultTrackSelector(application))
            .setLoadControl(
                // TV-tuned buffering. The defaults are tuned for a phone on
                // mobile data; a TV is on mains power and usually on worse
                // Wi-Fi, so buffering harder is the right trade. A bigger
                // buffer is the single most effective fix for the "it stutters
                // every thirty seconds" complaint on cheap TV boxes.
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 30_000,
                        /* maxBufferMs = */ 90_000,
                        /* bufferForPlaybackMs = */ 2_500,
                        /* bufferForPlaybackAfterRebufferMs = */ 5_000
                    )
                    .build()
            )
            .build()
            .apply {
                playWhenReady = true
                // Stop at the end rather than looping; the UI shows a replay
                // affordance instead. Silent looping on TV reads as a hang.
                repeatMode = Player.REPEAT_MODE_OFF
            }

    private fun loadMovie() {
        viewModelScope.launch {
            runCatching { repository.byId(movieId) }
                .onSuccess { movie ->
                    if (movie == null) {
                        _state.value = _state.value.copy(
                            error = "That title is no longer in the catalogue",
                            isBuffering = false
                        )
                        return@onSuccess
                    }
                    _state.value = _state.value.copy(movie = movie)
                    player.setMediaSource(buildMediaSource(movie))
                    player.prepare()
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _state.value = _state.value.copy(
                        error = throwable.message ?: "Could not open that title",
                        isBuffering = false
                    )
                }
        }
    }

    /**
     * Picks the MediaSource factory for the container.
     *
     * ExoPlayer can infer this from the URL, but doing it explicitly means an
     * unrecognised extension fails here with a clear cause rather than as a
     * confusing `ParserException` several layers down. The HTTP factory sets
     * `allowCrossProtocolRedirects`, without which a CDN that redirects HTTPS
     * to HTTP mid-stream drops playback with a bare IOException.
     */
    private fun buildMediaSource(movie: Movie): MediaSource {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setUserAgent("TvCompose/1.0 (Android TV)")

        val mediaItem = MediaItem.fromUri(movie.streamUrl)

        return when (movie.streamType) {
            StreamType.DASH ->
                DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            StreamType.HLS ->
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            StreamType.PROGRESSIVE ->
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }
    }

    private fun startPositionUpdates() {
        viewModelScope.launch {
            while (isActive) {
                if (player.playbackState != Player.STATE_IDLE) {
                    _state.value = _state.value.copy(
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
                        durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                    )
                }
                delay(POSITION_POLL_MS)
            }
        }
    }

    // ── Transport controls, called from the UI ───────────────────────────────

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    /**
     * Relative seek. Clamped at both ends because seeking past the duration is
     * undefined on some HLS streams and seeking below zero throws on a few
     * devices.
     */
    fun seekBy(deltaMs: Long) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: return
        val target = (player.currentPosition + deltaMs).coerceIn(0L, duration)
        player.seekTo(target)
        // Update immediately rather than waiting up to 250 ms for the next
        // poll. On a held-down D-pad that lag makes seeking feel unresponsive.
        _state.value = _state.value.copy(positionMs = target)
    }

    fun seekToFraction(fraction: Float) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: return
        val target = (duration * fraction.coerceIn(0f, 1f)).toLong()
        player.seekTo(target)
        _state.value = _state.value.copy(positionMs = target)
    }

    fun restart() {
        player.seekTo(0L)
        player.play()
    }

    /**
     * Applies a track override.
     *
     * The shape is worth reading closely, because the modern API is nothing
     * like the old `DefaultTrackSelector.ParametersBuilder` +
     * `setSelectionOverride(rendererIndex, groups, override)` dance that most
     * ExoPlayer2 tutorials still show:
     *
     *   1. `clearOverridesOfType` drops any previous choice for this track
     *      type. Without it, overrides accumulate and the player may keep
     *      honouring the earlier one.
     *   2. `TrackSelectionOverride(mediaTrackGroup, listOf(index))` names the
     *      exact rendition.
     *   3. Assigning `trackSelectionParameters` triggers reselection; you do
     *      not call anything else.
     */
    fun selectTrack(trackType: Int, option: TrackOption) {
        val group = player.currentTracks.groups
            .filter { it.type == trackType }
            .getOrNull(option.groupIndex) ?: return

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(trackType)
            .setTrackTypeDisabled(trackType, false)
            .setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, listOf(option.trackIndex))
            )
            .build()
    }

    /** Turns subtitles off entirely. There is no "None" track to select. */
    fun disableTextTracks() {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /** Called when the screen stops. Keeps the buffer, stops the audio. */
    fun pauseForBackground() {
        player.playWhenReady = false
    }

    override fun onCleared() {
        // Order matters: remove the listener first so a state change emitted
        // during release() cannot touch a StateFlow whose scope is gone.
        player.removeListener(listener)
        player.release()
        super.onCleared()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Flattens `Tracks` into something the UI can render.
     *
     * `Tracks.groups` is a list of groups, each holding one or more renditions,
     * and only `isSupported` renditions can actually be selected -- a stream may
     * advertise a codec this device cannot decode. Listing unsupported tracks
     * gives the user a menu entry that silently does nothing, so they are
     * filtered out here rather than in the UI.
     */
    private fun Tracks.extract(trackType: Int): List<TrackOption> {
        val groupsOfType = groups.filter { it.type == trackType }

        val raw = buildList {
            groupsOfType.forEachIndexed { groupIndex, group ->
                for (trackIndex in 0 until group.length) {
                    if (!group.isTrackSupported(trackIndex)) continue
                    val format = group.getTrackFormat(trackIndex)
                    add(
                        Triple(
                            format,
                            group.isTrackSelected(trackIndex),
                            groupIndex to trackIndex
                        )
                    )
                }
            }
        }

        // DISAMBIGUATION.
        //
        // Language plus channel layout is not always unique. Sintel's DASH
        // manifest, for example, carries two English stereo audio tracks that
        // differ only by codec, and they render as two identical menu rows --
        // the user picks one at random and cannot tell what changed.
        //
        // So: build the labels first, then append a codec/bitrate suffix only
        // to those that collide. Adding the codec unconditionally would put
        // "English · Stereo · aac" in front of users who have exactly one
        // choice, which is noise.
        val labels = raw.map { (format, _, _) -> format.describe(trackType) }
        val duplicated = labels.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

        return raw.mapIndexed { index, (format, isSelected, position) ->
            val base = labels[index]
            TrackOption(
                label = if (base in duplicated) base + format.technicalSuffix() else base,
                isSelected = isSelected,
                groupIndex = position.first,
                trackIndex = position.second
            )
        }
    }
}

/** Codec and bitrate, used only to tell otherwise identical tracks apart. */
private fun androidx.media3.common.Format.technicalSuffix(): String {
    val codec = sampleMimeType
        ?.substringAfterLast('/')
        ?.uppercase()
        ?.removePrefix("MP4A-LATM")
        ?.ifBlank { "AAC" }
    val kbps = if (bitrate > 0) "${bitrate / 1000} kbps" else null
    val parts = listOfNotNull(codec?.takeIf { it.isNotBlank() }, kbps)
    return if (parts.isEmpty()) "" else "  ·  " + parts.joinToString("  ·  ")
}

/**
 * Builds a human label for a rendition.
 *
 * Language codes in manifests are ISO-639-2 ("eng", "deu"), which is not what
 * you want on screen. `Locale.getDisplayLanguage` turns them into "English" and
 * "German". Channel count is appended because multi-audio streams frequently
 * carry the same language twice -- stereo and 5.1 -- and without it the menu
 * shows two identical rows.
 */
private fun androidx.media3.common.Format.describe(trackType: Int): String {
    val language = language
        ?.let { java.util.Locale.forLanguageTag(it).displayLanguage }
        ?.takeIf { it.isNotBlank() }
        ?: label
        ?: "Track"

    return when (trackType) {
        C.TRACK_TYPE_AUDIO -> buildString {
            append(language)
            if (channelCount > 0) {
                append(
                    when (channelCount) {
                        1 -> "  ·  Mono"
                        2 -> "  ·  Stereo"
                        6 -> "  ·  5.1"
                        8 -> "  ·  7.1"
                        else -> "  ·  ${channelCount}ch"
                    }
                )
            }
        }
        else -> language
    }
}

private const val POSITION_POLL_MS = 250L
