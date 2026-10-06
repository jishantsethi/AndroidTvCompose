# Flow

How a press on the remote becomes a pixel change. Read this alongside
[ARCHITECTURE.md](ARCHITECTURE.md), which covers *why* each decision was made.

## Screen map

```
                         ┌──────────────────────────────────────────┐
                         │            MainActivity                  │
                         │  (the only Activity in the whole app)    │
                         └────────────────────┬─────────────────────┘
                                              │ setContent
                         ┌────────────────────▼─────────────────────┐
                         │  TvTheme  → androidx.tv.material3        │
                         └────────────────────┬─────────────────────┘
                         ┌────────────────────▼─────────────────────┐
                         │  TvNavGraph  (NavHost, 2 destinations)   │
                         └───────┬──────────────────────┬───────────┘
                                 │ "home"               │ "player/{movieId}"
              ┌──────────────────▼──────────┐   ┌───────▼──────────────────┐
              │       HomeScreen            │   │      PlayerScreen        │
              │  NavigationDrawer (Row)     │   │  PlayerSurface + overlay │
              │ ┌─────────┬───────────────┐ │   │                          │
              │ │ Drawer  │ content pane  │ │   │  ┌────────────────────┐  │
              │ │ 56↔256  │               │ │   │  │  PlayerControls    │  │
              │ │  dp     │  Browse   or  │ │   │  │  TrackSelection…   │  │
              │ │         │  Search   or  │ │   │  └────────────────────┘  │
              │ │         │  About        │ │   │                          │
              │ └─────────┴───────────────┘ │   └──────────────────────────┘
              └─────────────────────────────┘
```

Note what is **not** a destination: the drawer sections. Switching from Movies
to Series is `selectedSection = ...`, a state write, not a navigation event.

## Cold start

```
Process start
 └─ TvApplication.onCreate
     ├─ startKoin { modules(appModule) }        DI graph is ready
     └─ newImageLoader(...)                     Coil: 20% heap cache, 96 MB disk
 └─ MainActivity.onCreate
     ├─ WindowCompat.setDecorFitsSystemWindows(false)
     ├─ WindowInsetsControllerCompat.hide(systemBars)     immersive
     └─ setContent { TvTheme { TvNavGraph() } }
         └─ HomeScreen                          section = HOME
             └─ BrowseScreen
                 ├─ LaunchedEffect(categoryFilter) → viewModel.load(null)
                 │    └─ repository.rows()  ──Flow──▶  BrowseUiState(rows)
                 └─ LaunchedEffect(rows.isNotEmpty())
                      └─ firstCardFocusRequester.requestFocus()
                             ↑ the screen now has focus. Without this the
                               remote appears dead on first paint.
```

## Browsing

```
D-pad RIGHT on a card
 └─ Compose focus search finds the next focusable to the right
     └─ LazyRow scrolls it into view automatically
         └─ MovieCard.onFocusChanged(isFocused = true)
             ├─ local isFocused = true          title brightens
             └─ onFocused(movie)  ──▶  BrowseScreen: focusedMovie = movie
                 └─ HeroPanel recomposes        title / meta / description
                 └─ background wash re-tints    from movie.accentArgb

D-pad DOWN                → next MovieCarouselRow; focusRestorer() gives
                            back the card you were last on in that row
D-pad LEFT (on card 1)    → focus leaves the row, finds the drawer sheet
                            └─ DrawerSheet.onFocusChanged(hasFocus = true)
                                └─ drawerState = Open
                                    └─ each NavigationDrawerItem animates
                                       56dp → 256dp and fades its label in
D-pad UP (from row 1)     → the SearchPill in the top bar
OK on a card              → onMovieClick
                            └─ navController.navigate("player/<id>")
```

Nothing above calls `drawerState.setValue`. The drawer is opened by focus
arriving and closed by focus leaving — see ARCHITECTURE.md §4.

## Search

```
Drawer → "Search"                 selectedSection = SEARCH
 └─ SearchScreen
     keyboardVisible = true       opens ready to type: the user picked Search,
                                  so wanting to type is the safe assumption
     └─ OnScreenKeyboard
         └─ LaunchedEffect → first key ('A') takes focus

Press OK on a key
 └─ onCharacter('S')
     └─ SearchViewModel.append('S')
         ├─ _state.query = "S"          IMMEDIATE — the pill echoes this frame.
         │                              Waiting for the debounce would read as
         │                              a dropped keypress and the user presses
         │                              the key again.
         └─ queryFlow.value = "S"
             └─ debounce(220ms)         waits for a gap in D-pad typing
                 └─ mapLatest { … }     cancels any older in-flight search, so
                     │                  results can never lag behind the query
                     └─ repository.search("S")
                         └─ _state = SearchUiState(results = […])
                             └─ LazyVerticalGrid recomposes

D-pad RIGHT off the last key column  → results grid (geometry does the work)
D-pad LEFT  off the first tile       → back to the keys
"Done" key                           → searchNow() (skips debounce)
                                       + keyboardVisible = false → full-width results
BACK with keyboard open              → closes keyboard only
BACK with keyboard closed            → HomeScreen's BackHandler → section = HOME
```

## Playback

```
navigate("player/<id>")
 └─ PlayerScreen(movieId)
     └─ koinViewModel { parametersOf(movieId) }
         └─ PlayerViewModel.init
             ├─ ExoPlayer.Builder(...)                 30s/90s buffer
             │     .setTrackSelector(DefaultTrackSelector)
             ├─ player.addListener(listener)
             ├─ loadMovie()
             │   └─ repository.byId(id)
             │       └─ buildMediaSource(movie)        DASH / HLS / Progressive
             │           └─ player.setMediaSource(); player.prepare()
             └─ startPositionUpdates()                 poll every 250 ms
```

Two channels update the UI, and the split is deliberate:

```
PUSHED  (Player.Listener)                POLLED (250 ms coroutine)
├─ onPlaybackStateChanged → isBuffering  ├─ positionMs
├─ onIsPlayingChanged     → isPlaying    ├─ bufferedMs
├─ onTracksChanged        → audio/text   └─ durationMs
└─ onPlayerError          → error
```

`Player` has no "playhead moved" callback — that would fire per frame — so
position is the one thing that has to be polled.

### Key handling

**The D-pad means two different things on this screen**, and that is the whole
design:

| Controls | LEFT / RIGHT | OK |
|---|---|---|
| hidden | seek ∓10 s | play / pause |
| shown | move focus along the transport row | activate the focused button |

```
Any key
 └─ Box.onPreviewKeyEvent          runs BEFORE focus search sees it
     ├─ type != KeyDown            → false   (KeyUp too would double-fire:
     │                                        one press = a 20 s seek)
     ├─ a track menu is open       → false   (let the D-pad navigate the menu)
     │
     │   ── dedicated media keys: unconditional ──
     ├─ MediaRewind                → seekBy(-10s)                     → true
     ├─ MediaFastForward           → seekBy(+10s)                     → true
     ├─ MediaPlayPause/Play/Pause  → togglePlayPause()                → true
     │
     │   ── D-pad: depends on whether the controls are up ──
     ├─ LEFT   → controls shown? touch(), false : seekBy(-10s), true
     ├─ RIGHT  → controls shown? touch(), false : seekBy(+10s), true
     ├─ OK     → controls shown? false (Surface handles its own click)
     │                          : togglePlayPause(), true
     ├─ UP     → controls hidden? show them, true : false (focus enters them)
     ├─ DOWN   → controls shown? hide them, true : false
     ├─ MediaStop → onExit()                                          → true
     └─ anything else                                                 → false
```

Two traps here, both found by actually driving the app rather than by reading
the code:

1. **If LEFT/RIGHT always seek, the Audio and Subtitles buttons are
   unreachable.** The transport row still renders, focus still sits on Play, and
   the screen looks completely correct — but no sequence of key presses can ever
   move focus to the end of the row. The controls-visible branch above is what
   makes them reachable.
2. **If OK is swallowed while the controls are up, every button is inert.**
   Returning `false` lets the focused tv-material `Surface` handle its own
   click.

Media transport keys stay unconditional because a remote's rewind button means
rewind regardless of what is on screen — and many real remotes send
`MediaPlayPause` and never `DirectionCenter`.

### Controls lifecycle

```
any interaction → touch()
                   ├─ controlsVisible = true
                   └─ lastInteraction = now
                       └─ LaunchedEffect(lastInteraction, …) restarts
                           └─ delay(4000) → controlsVisible = false

The effect is keyed on lastInteraction, so each press cancels and restarts the
timer rather than stacking a new one. It is suspended while a track menu is
open, or the menu would vanish under the user mid-selection.
```

### Track selection

```
"Audio (7)" button → trackMenu = AUDIO
 └─ TrackSelectionPanel (a side panel, NOT a Dialog/Popup — those get their own
    window, and focus search does not cross window boundaries)
     └─ OK on a row → viewModel.selectTrack(TRACK_TYPE_AUDIO, option)
         └─ player.trackSelectionParameters = …buildUpon()
              .clearOverridesOfType(type)      ← without this, old overrides linger
              .setTrackTypeDisabled(type, false)
              .setOverrideForType(TrackSelectionOverride(group, [index]))
              .build()
             └─ reselection happens on assignment; nothing else to call
                 └─ onTracksChanged fires → isSelected flags refresh
```

### Leaving

```
BACK   → menu open?      close menu
       → controls shown? hide controls
       → otherwise       onExit()
                          └─ navController.popBackStack()
                              └─ back-stack entry destroyed
                                  └─ PlayerViewModel.onCleared()
                                      ├─ player.removeListener(listener)   order
                                      └─ player.release()                  matters

ON_STOP (backgrounded) → viewModel.pauseForBackground()
                          └─ playWhenReady = false     keeps the buffer,
                                                       stops audio playing over
                                                       the TV home screen
```

`popBackStack()` and not `navigate("home")`: navigating would push a *second*
home entry and leave the player's entry — and its ExoPlayer — alive in the
stack. Popping is what releases the video decoder, and on TV hardware there is
usually only one.

## Focus cheat sheet

| Modifier | Where | What breaks without it |
|---|---|---|
| `focusRestorer()` | every LazyRow / grid / control bar | leaving and returning resets focus and scroll to item 1 |
| `focusRequester()` + `LaunchedEffect` | first card, first key, play button | screen renders with nothing focused; remote looks dead |
| `focusGroup()` | OnScreenKeyboard | the keyboard is entered key-by-key instead of as a unit |
| `onFocusChanged` | MovieCard | hero panel never updates; no "focus is the new hover" |
| `focusable()` | PlayerScreen root | the Box gets no key events at all — nothing responds |
| `onPreviewKeyEvent` | PlayerScreen root | focus search eats LEFT/RIGHT and seeking never fires |

All `requestFocus()` calls are wrapped in `runCatching`: it throws if the target
has not been placed yet, which happens on a slow TV SoC when the effect lands
before the first layout pass. Focus staying put is recoverable; a crash is not.
