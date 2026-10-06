package com.ss.tvcompose.data

/**
 * Hard-coded sample catalogue.
 *
 * WHY NOT THE OLD GOOGLE SAMPLE URLS?
 * The `storage.googleapis.com/gtv-videos-bucket/sample/...` MP4s that nearly
 * every Leanback tutorial (and the 2019-era Google TV samples) used are dead --
 * they now return HTTP 403. If you are porting an old Leanback project and the
 * player silently shows a black screen, that is very likely why. Everything
 * below was checked live against the Shaka Player demo bucket and Apple's
 * reference HLS stream, both of which are public and stable.
 *
 * Two entries are deliberately chosen to exercise the player UI:
 *  - "Angel One" carries a large number of alternate AUDIO languages, so the
 *    audio-track picker in the player has something real to show.
 *  - "Bip Bop" (Apple's reference stream) carries alternate audio *and*
 *    subtitle renditions plus many bitrate variants, which is the easiest way
 *    to see adaptive switching actually happen.
 */
object MovieCatalog {

    private const val ICONS = "https://storage.googleapis.com/shaka-asset-icons"
    private const val SHAKA = "https://storage.googleapis.com/shaka-demo-assets"

    val all: List<Movie> = listOf(
        Movie(
            id = "sintel",
            title = "Sintel",
            studio = "Blender Foundation",
            year = 2010,
            durationLabel = "14 min",
            description = "A lone girl crosses a hostile world searching for the dragon " +
                "she raised from a hatchling. Blender's third open movie, and still the " +
                "reference clip everyone tests adaptive streaming with.",
            category = Category.MOVIES,
            posterUrl = "$ICONS/sintel.png",
            streamUrl = "$SHAKA/sintel/dash.mpd",
            streamType = StreamType.DASH,
            accentArgb = 0xFF3B5BDB,
            badge = "DASH / multi-subtitle"
        ),
        Movie(
            id = "tears-of-steel",
            title = "Tears of Steel",
            studio = "Blender Foundation",
            year = 2012,
            durationLabel = "12 min",
            description = "Live action and CG in a ruined Amsterdam. This copy is the " +
                "surround-sound master, so the audio track picker has more than one " +
                "channel layout to choose from.",
            category = Category.MOVIES,
            posterUrl = "$ICONS/tears_of_steel.png",
            streamUrl = "$SHAKA/tos-surround/dash.mpd",
            streamType = StreamType.DASH,
            accentArgb = 0xFFB0431E,
            badge = "DASH / surround"
        ),
        Movie(
            id = "angel-one-dash",
            title = "Angel One",
            studio = "Shaka Reference",
            year = 2015,
            durationLabel = "4 min",
            description = "The multi-language reference asset. Open the audio menu " +
                "during playback: this one ships a long list of alternate language " +
                "tracks, which is exactly what the track selector was built for.",
            category = Category.MOVIES,
            posterUrl = "$ICONS/angel_one.png",
            streamUrl = "$SHAKA/angel-one/dash.mpd",
            streamType = StreamType.DASH,
            accentArgb = 0xFF2B8A6E,
            badge = "DASH / many audio languages"
        ),
        Movie(
            id = "bbb-movies",
            title = "Big Buck Bunny",
            studio = "Blender Foundation",
            year = 2008,
            durationLabel = "10 min",
            description = "The original open movie that every codec test in the world " +
                "has been run against at some point.",
            category = Category.MOVIES,
            posterUrl = "$ICONS/big_buck_bunny.png",
            streamUrl = "$SHAKA/bbb-dark-truths-hls/hls.m3u8",
            streamType = StreamType.HLS,
            accentArgb = 0xFF9A7B22,
            badge = "HLS"
        ),
        Movie(
            id = "bbb-dark-truths",
            title = "Big Buck Bunny: Dark Truths",
            studio = "Blender Foundation",
            year = 2008,
            durationLabel = "10 min",
            description = "The cheerful rabbit, re-cut. Delivered over HLS, so this is " +
                "the entry to open if you want to watch ExoPlayer's HLS pipeline rather " +
                "than DASH.",
            category = Category.SHOWS,
            posterUrl = "$ICONS/big_buck_bunny.png",
            streamUrl = "$SHAKA/bbb-dark-truths-hls/hls.m3u8",
            streamType = StreamType.HLS,
            accentArgb = 0xFF8A6D2B,
            badge = "HLS"
        ),
        Movie(
            id = "angel-one-hls",
            title = "Angel One (HLS)",
            studio = "Shaka Reference",
            year = 2015,
            durationLabel = "4 min",
            description = "The same asset as the DASH entry, repackaged as HLS. Useful " +
                "side by side: identical content, two completely different manifest " +
                "formats, one Player API.",
            category = Category.SHOWS,
            posterUrl = "$ICONS/angel_one.png",
            streamUrl = "$SHAKA/angel-one-hls/hls.m3u8",
            streamType = StreamType.HLS,
            accentArgb = 0xFF1F6F8B,
            badge = "HLS / many audio languages"
        ),
        Movie(
            id = "bipbop",
            title = "Bip Bop",
            studio = "Apple Reference",
            year = 2016,
            durationLabel = "loop",
            description = "Apple's public reference HLS stream. Many bitrate variants " +
                "plus alternate audio and subtitle renditions -- the fastest way to see " +
                "adaptive bitrate switching and track selection working for real.",
            category = Category.SHOWS,
            posterUrl = "$ICONS/art_of_motion.png",
            streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/" +
                "bipbop_16x9/bipbop_16x9_variant.m3u8",
            streamType = StreamType.HLS,
            accentArgb = 0xFF6B3FA0,
            badge = "HLS / ABR + subtitles"
        ),
        Movie(
            id = "heliocentrism",
            title = "Heliocentrism",
            studio = "Shaka Reference",
            year = 2015,
            durationLabel = "2 min",
            description = "A short animated explainer. Small, quick to start, and a good " +
                "smoke test when you just want to confirm the player pipeline is alive.",
            category = Category.SHORTS,
            posterUrl = "$ICONS/art_of_motion.png",
            streamUrl = "$SHAKA/heliocentrism/heliocentrism.mpd",
            streamType = StreamType.DASH,
            accentArgb = 0xFF2F6FED,
            badge = "DASH"
        ),
        Movie(
            id = "sintel-mp4",
            title = "Sintel (MP4 only)",
            studio = "Blender Foundation",
            year = 2010,
            durationLabel = "14 min",
            description = "Sintel packaged with MP4 segments only, no WebM. Handy on " +
                "cheap TV boxes whose VP9 decoder is unreliable.",
            category = Category.SHORTS,
            posterUrl = "$ICONS/sintel.png",
            streamUrl = "$SHAKA/sintel-mp4-only/dash.mpd",
            streamType = StreamType.DASH,
            accentArgb = 0xFF4C5BD4,
            badge = "DASH / MP4 only"
        ),
        Movie(
            id = "tos-shorts",
            title = "Tears of Steel: Trailer Cut",
            studio = "Blender Foundation",
            year = 2012,
            durationLabel = "12 min",
            description = "The surround master again, filed under Shorts. Useful for " +
                "checking that the same source can legitimately appear in more than one " +
                "row without breaking focus restoration.",
            category = Category.SHORTS,
            posterUrl = "$ICONS/tears_of_steel.png",
            streamUrl = "$SHAKA/tos-surround/dash.mpd",
            streamType = StreamType.DASH,
            accentArgb = 0xFFC2571A,
            badge = "DASH / surround"
        ),
        Movie(
            id = "dig-the-uke",
            title = "Dig the Uke",
            studio = "Shaka Reference",
            year = 2015,
            durationLabel = "3 min",
            description = "A short music piece, unencrypted. Sits in the Music section " +
                "to show that a drawer category is nothing more than a filter over the " +
                "same repository.",
            category = Category.MUSIC,
            posterUrl = "$ICONS/art_of_motion.png",
            streamUrl = "$SHAKA/dig-the-uke-clear/dash.mpd",
            streamType = StreamType.DASH,
            accentArgb = 0xFFB03A6E,
            badge = "DASH"
        ),
        Movie(
            id = "angel-one-music",
            title = "Angel One: Score",
            studio = "Shaka Reference",
            year = 2015,
            durationLabel = "4 min",
            description = "Same source asset, filed under Music so the Music row is not " +
                "a single lonely card. Swap in a real endpoint and nothing above this " +
                "line changes.",
            category = Category.MUSIC,
            posterUrl = "$ICONS/angel_one.png",
            streamUrl = "$SHAKA/angel-one/dash.mpd",
            streamType = StreamType.DASH,
            accentArgb = 0xFF7A2BA0,
            badge = "DASH"
        )
    )
}
