# Android TV — Compose reference app

A working Android TV app built with **Compose for TV**, not Leanback: browse
rows, a focus-driven left drawer, an app-drawn on-screen keyboard for search,
and an ExoPlayer (Media3) playback screen with seek, play/pause and audio and
subtitle track selection.

Every non-obvious decision is commented in the source. Two companion documents:

- **[ARCHITECTURE.md](ARCHITECTURE.md)** — layers, the eight TV-specific
  decisions, ExoPlayer ownership, and a Leanback → Compose mapping table.
- **[FLOW.md](FLOW.md)** — how a remote press becomes a pixel change, screen by
  screen, plus a focus-modifier cheat sheet.

## Is Leanback really gone?

Yes. `androidx.leanback` is formally deprecated and Compose for TV
(`androidx.tv:tv-material`) reached 1.0 stable and is the recommended path. You
can use it confidently. Two footnotes that trip people up:

- **`androidx.tv:tv-foundation` is frozen** — do not add it. `TvLazyRow` and
  `TvLazyColumn` were folded back into the ordinary
  `androidx.compose.foundation` lazy lists, which now handle TV focus correctly.
  This project uses plain `LazyRow` / `LazyVerticalGrid`.
- **`com.google.android.exoplayer2` is end-of-life** — ExoPlayer now ships as
  `androidx.media3`. Same player, new package.

If you are porting a 2019 project, also note: the
`storage.googleapis.com/gtv-videos-bucket/sample/*` MP4s that every Leanback
tutorial used now return **HTTP 403**. That, not your code, is why the old
sample player shows a black screen. The catalogue here uses the Shaka demo
bucket and Apple's reference HLS stream instead.

## Build and run

Needs JDK 17+ (Android Studio's bundled JBR works) and a `local.properties`
containing `sdk.dir`.

```bash
./gradlew :app:assembleDebug
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.ss.tvcompose/.MainActivity
```

### Running on a TV emulator

There is no TV system image installed by default. To create one:

```bash
sdkmanager "system-images;android-34;android-tv;x86"
```

```bash
avdmanager create avd -n tv_api34 -k "system-images;android-34;android-tv;x86" -d tv_1080p
```

The manifest deliberately keeps `android.software.leanback` at
`required="false"` and includes a plain `LAUNCHER` category alongside
`LEANBACK_LAUNCHER`, so **the same APK also installs and runs on a phone or
tablet emulator** — useful while developing, since a TV image is a large
download. For a TV-only release, flip `leanback` to `required="true"` and drop
the `LAUNCHER` category. `android.hardware.touchscreen` must stay
`required="false"` either way, or the Play Store will not offer the app on TV.

## Controls

| Key | Where | Action |
|---|---|---|
| D-pad | everywhere | moves focus; the focused card drives the hero panel |
| LEFT from first card | browse | opens the drawer (it is opened by focus, not a button) |
| UP from first row | browse | reaches the search pill in the top bar |
| OK | browse | opens the player |
| OK | search pill | shows / hides the on-screen keyboard |
| LEFT / RIGHT | **player** | seek −10 s / +10 s |
| OK / play-pause key | player | toggle playback |
| UP / DOWN | player | show / hide the transport controls |
| BACK | anywhere | closes one layer at a time; from Home it exits |

## Stack

| | |
|---|---|
| UI | `androidx.tv:tv-material` 1.1.0 + `androidx.compose.foundation` |
| Playback | `androidx.media3` 1.10.1 (ExoPlayer) + DASH and HLS extensions |
| Images | Coil 3.3.0 with the OkHttp fetcher |
| DI | Koin 4.1.1 |
| Navigation | `navigation-compose` 2.9.6 |
| Build | AGP 8.13.0, Kotlin 2.2.20, Gradle 9.1.0, compileSdk 36, minSdk 23 |

`media3` and `coil3` are pinned below their latest releases on purpose: the
newest versions of both require Compose 1.12, which demands AGP 9.1+ and
`compileSdk 37`. See the dependency notes at the end of ARCHITECTURE.md before
bumping either.

The project does **not** depend on `androidx.compose.material3`. Mixing that
`MaterialTheme` with the tv-material one compiles and runs while silently
stripping every focus affordance — keeping it off the classpath makes the
mistake impossible.

## Layout

```
app/src/main/java/com/ss/tvcompose/
├── MainActivity.kt          single Activity, immersive setup
├── TvApplication.kt         Koin start + Coil image loader tuning
├── data/                    Movie, MovieCatalog, MovieRepository
├── di/AppModule.kt
└── ui/
    ├── theme/               TvTheme, TvColors, TvTypography, TvDimens
    ├── navigation/          TvNavGraph, Routes
    ├── components/          MovieCard, MovieCarouselRow, OnScreenKeyboard, SearchPill
    ├── browse/              BrowseScreen + BrowseViewModel
    ├── search/              SearchScreen + SearchViewModel
    ├── player/              PlayerScreen, PlayerControls, PlayerViewModel
    └── HomeScreen.kt        NavigationDrawer shell
```

## Sample content

Public test streams: the Shaka Player demo bucket (Sintel, Tears of Steel, Angel
One, Big Buck Bunny — DASH and HLS) and Apple's `bipbop` reference HLS stream.
"Angel One" and "Bip Bop" carry multiple audio renditions, which is what gives
the player's audio-track picker something real to show.
