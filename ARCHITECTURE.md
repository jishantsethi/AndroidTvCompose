# Architecture

## The short version

One Activity. One composable tree. Two navigation destinations. Everything
else is state.

```
MainActivity
└── TvTheme                          androidx.tv.material3.MaterialTheme
    └── TvNavGraph                   NavHost, exactly 2 destinations
        ├── "home"    → HomeScreen   NavigationDrawer + content pane
        └── "player/{movieId}" → PlayerScreen
```

## If you are coming from Leanback (2019-era)

This is the part worth reading carefully. Leanback is not deprecated-ish; it is
formally deprecated, and Compose for TV (`androidx.tv:tv-material`) is the
supported replacement. The mapping is:

| Leanback | Here | Note |
|---|---|---|
| `BrowseSupportFragment` | `BrowseScreen` composable | ~150 lines, no adapters |
| `ArrayObjectAdapter` + `ListRow` | `List<MovieRow>` | a plain data class |
| `ListRowPresenter` | `MovieCarouselRow` | a composable |
| `Presenter` + `ViewHolder` + `ImageCardView` | `MovieCard` | one function |
| `ClassPresenterSelector` | `when` on a sealed type, if you need it at all |
| `HeadersSupportFragment` | `NavigationDrawer` | focus-driven, no headers fragment |
| `SearchSupportFragment` | `SearchScreen` + `OnScreenKeyboard` | you draw the keyboard |
| `VideoSupportFragment` + `PlaybackTransportControlGlue` | `PlayerScreen` + `PlayerControls` | no glue classes |
| `PlaybackSupportFragment` overlay | your own composables | full control |
| `com.google.android.exoplayer2.*` | `androidx.media3.*` | ExoPlayer's new home |

Three things that no longer exist and that people keep looking for:

1. **`androidx.tv:tv-foundation` is frozen.** `TvLazyRow` / `TvLazyColumn` were
   folded back into ordinary `androidx.compose.foundation` lazy lists. Use
   `LazyRow`, `LazyColumn`, `LazyVerticalGrid`. Adding tv-foundation today gives
   you two competing focus implementations.
2. **No presenter/adapter layer.** Data goes straight into a composable.
3. **No `nextFocusLeft`/`nextFocusRight` wiring.** Compose focus search is
   geometric — it finds the nearest focusable in the direction you pressed.
   Lay things out where they belong and D-pad navigation follows.

## Layers

```
ui/          Compose. Knows about state, not about where it came from.
  theme/       TvTheme, TvColors, TvTypography, TvDimens
  navigation/  TvNavGraph, Routes
  components/  MovieCard, MovieCarouselRow, OnScreenKeyboard, SearchPill
  browse/      BrowseScreen + BrowseViewModel
  search/      SearchScreen + SearchViewModel
  player/      PlayerScreen + PlayerControls + PlayerViewModel
  HomeScreen   the drawer shell

data/        MovieRepository (interface) + InMemoryMovieRepository, Movie, MovieCatalog
di/          Koin module
```

Dependency direction is strictly downward: `ui → data`. Nothing in `data/`
imports Compose, so the repository and models are plain Kotlin and testable
without an emulator.

### State flow

```
Repository ──Flow──▶ ViewModel ──StateFlow──▶ Screen ──events──▶ ViewModel
```

Each screen has exactly one `UiState` data class. Not three flows for
loading/error/data — with three, the UI can render "loading AND error AND has
data", and eventually will.

## The eight TV-specific decisions

Each of these is a place where the phone answer is wrong.

### 1. `androidx.tv.material3.MaterialTheme`, never `androidx.compose.material3`

They are different classes with the same name and nearly the same API. Mix them
and the code compiles, runs, and silently loses every focus affordance. This
project does not depend on `androidx.compose.material3` at all — that is the
cheapest way to make the mistake impossible.

### 2. Focus is the interaction model

There is no cursor and no touch. Three consequences:

- **The screen must never render with nothing focused.** `BrowseScreen` requests
  focus on the first card once data arrives.
- **Focused, not clicked, drives the detail panel.** The hero panel describes
  whatever the D-pad is currently on.
- **`Modifier.focusRestorer()` on every row and grid.** Without it, leaving a row
  and coming back resets focus and scroll to item 1. Leanback did this inside
  `HorizontalGridView`, which is why nobody thinks to add it.

### 3. Three redundant focus signals

`MovieCard` uses scale *and* border *and* glow. Motion catches peripheral
vision; the border says where focus is on a still screen; the glow survives on
a washed-out panel. Shipping one of the three tests fine on a monitor and gets
"I can't tell what's selected" from a living room.

### 4. The drawer is opened by focus, not by a button

`NavigationDrawer` is internally `Row { DrawerSheet(); content() }`, and the
sheet wraps itself in `onFocusChanged { setValue(if (hasFocus) Open else Closed) }`.
You never call `setValue` yourself. It pushes content rather than covering it,
so the user does not lose their place. `ModalNavigationDrawer` is the overlay
variant if you want that instead.

### 5. Drawer sections are state; only the player is a destination

If each section were a NavHost destination, browsing four sections would build a
four-deep back stack the user has to press BACK through. TV users read BACK as
"up and out". So sections are `rememberSaveable` state inside `HomeScreen`, and
BACK from any section returns to Home.

### 6. The app draws its own keyboard

Never summon the system IME on TV: many devices ship none, the ones that do
cover the screen and eat D-pad events unpredictably, and you cannot style it.
`OnScreenKeyboard` is a `Column` of `Row`s of `Surface` keys. Because they sit
in a real spatial grid, D-pad navigation between them needs no code at all — the
only focus logic in the file is the initial `requestFocus` and a `focusRestorer`.

It is composed **inline**, not in a `Dialog` or `Popup`. Those get their own
window, and focus search does not cross window boundaries — you would have to
hand-wire every route in and out.

Layout is keyboard-left / results-right so RIGHT exits the keyboard into results
and LEFT comes back. Stacked vertically, that trip costs six presses.

### 7. The player is the one screen that intercepts keys

Everywhere else the D-pad moves focus. During playback LEFT/RIGHT must scrub.
`onPreviewKeyEvent` (not `onKeyEvent`) runs before focus search gets the event.
Media transport keys are handled alongside the D-pad because many remotes send
only `MediaPlayPause` and never `DirectionCenter`.

But the interception is **conditional on whether the controls are visible**:

| Controls | LEFT / RIGHT | OK |
|---|---|---|
| hidden | seek ∓10 s | play / pause |
| shown | move focus along the transport row | activate the focused button |

Intercepting unconditionally is a bug that hides completely on inspection: the
transport row renders, focus sits on Play, everything looks right — and the
Audio and Subtitles buttons at the end of the row can never be reached by any
sequence of key presses. Likewise, swallowing OK while the controls are up
makes every button inert. See FLOW.md for the full decision table.

`PlayerSurface` from `media3-ui-compose` is used rather than
`AndroidView(PlayerView)`: `PlayerView` brings a View-based control overlay with
its own focus handling that fights Compose's. `PlayerSurface` is only the video
output, so there is exactly one focus system in play.

### 8. Overscan padding is not a style choice

Older TVs crop 3–5% off every edge. `TvDimens.screenPadding = 48.dp` is why the
first card is not sliced in half on a customer's set. Rows use `contentPadding`
rather than `Modifier.padding` so the focus scale of the first and last card is
not clipped.

## ExoPlayer ownership

`PlayerViewModel` owns the `ExoPlayer`, and `onCleared()` is the only place that
calls `release()`. Two reasons:

1. It survives configuration changes, so playback does not restart from zero.
2. Most TV hardware has **one** video decoder. A second un-released ExoPlayer
   fails to acquire it and you get a black screen with no obvious error.

`TvNavGraph` uses `popBackStack()` rather than `navigate("home")` when leaving
the player, because popping is what destroys the entry, clears the ViewModel and
releases the codec.

**Position is polled; everything else is pushed.** `Player.Listener` reports
state, track and error changes. It does not report that the playhead moved —
that would be a callback per frame. So position is polled at 250 ms: smooth to
the eye, cheap on a weak SoC. Polling at 16 ms to "match the frame rate"
recomposes the overlay 60 times a second and makes a TV app stutter.

Buffering is tuned up from the defaults (30 s min / 90 s max). The defaults
assume a phone on metered mobile data; a TV is on mains power and usually worse
Wi-Fi.

## Track selection

The modern API is nothing like ExoPlayer2's `ParametersBuilder.setSelectionOverride`:

```kotlin
player.trackSelectionParameters = player.trackSelectionParameters
    .buildUpon()
    .clearOverridesOfType(trackType)      // or old overrides linger
    .setTrackTypeDisabled(trackType, false)
    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
    .build()
```

Assigning the parameters triggers reselection; there is nothing else to call.
Tracks arrive in `onTracksChanged` **after** the manifest parses — reading them
during first composition gives you an empty list. Unsupported renditions are
filtered out, because a menu row that silently does nothing is worse than no row.

## Dependency notes

`media3` is pinned to **1.10.1**, not 1.11.0. 1.11.0's `media3-ui-compose`
imports Compose BOM 2026.05.00, which drags in Compose 1.12 and demands AGP 9.1+
and `compileSdk 37`. 1.10.1 sits on Compose BOM 2024.12.01 and coexists with the
BOM here.

`coil3` is pinned to **3.3.0** for the same reason — 3.6.x requires Compose 1.12.

If you deliberately move to AGP 9, note that it applies Kotlin itself: the
`org.jetbrains.kotlin.android` plugin must be *removed* or the build fails
outright, and `android { kotlinOptions { } }` no longer exists. This project
already uses the `kotlin { compilerOptions { } }` form, which works on both.

## Testing

`InMemoryMovieRepository`, `Movie`/`MovieCatalog` and the two catalogue
ViewModels are plain Kotlin with no Android dependencies and are cheap to unit
test. Compose UI tests for a TV focus tree are expensive and brittle; run the
app instead.
