package com.ss.tvcompose.data

/**
 * How the stream is packaged. ExoPlayer can usually infer this from the URL's
 * extension, but being explicit means we can pick the right MediaSource factory
 * without guessing -- and it documents which of the three `media3-exoplayer-*`
 * artifacts each entry actually needs.
 *
 *  - [DASH]        `.mpd`   -> needs `media3-exoplayer-dash`
 *  - [HLS]         `.m3u8`  -> needs `media3-exoplayer-hls`
 *  - [PROGRESSIVE] `.mp4`   -> built into `media3-exoplayer`, no extra artifact
 */
enum class StreamType { DASH, HLS, PROGRESSIVE }

/**
 * The left drawer's sections. Each one is just a filter over the same catalogue;
 * a real app would back these with different API calls, but the UI contract is
 * identical, so swapping the repository implementation is all it would take.
 */
enum class Category(val label: String) {
    MOVIES("Movies"),
    SHOWS("Series"),
    SHORTS("Shorts"),
    MUSIC("Music")
}

/**
 * One catalogue item.
 *
 * [accentArgb] is not decoration for its own sake: posters are fetched over the
 * network and *will* be missing on first paint or on a flaky connection. Every
 * card falls back to a gradient built from this colour, so the grid never shows
 * a row of grey holes -- which on a 10-foot UI reads as a broken app.
 */
data class Movie(
    val id: String,
    val title: String,
    val studio: String,
    val year: Int,
    val durationLabel: String,
    val description: String,
    val category: Category,
    val posterUrl: String,
    val streamUrl: String,
    val streamType: StreamType,
    val accentArgb: Long,
    /** Shown on the detail strip -- handy for flagging multi-audio content. */
    val badge: String? = null
)
