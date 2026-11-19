# Atlas Music — iOS port

Swift 5.10 / iOS 17+, SwiftUI + SwiftData + AVFoundation. Local-first music player, no accounts,
no ads, no tracking. This directory is the iOS counterpart of the Android app in `app/`.

## Layout

```
ios/
  AtlasMusic/
    AtlasMusicApp.swift            @main App, DI graph, onOpenURL, audio session + BGTask
    Core/                          Models, Persistence, Repositories, Utils, Audio, Downloads, Theme
    UI/                            Root, Screens, Components, Tools, ViewModels, Navigation
    Widgets/                       WidgetKit provider + App Intents + App Group state
    Resources/                     Fonts (.ttf) and Assets.xcassets (app icon)
    Info.plist
  generate_xcodeproj.py            regenerates the Xcode project (idempotent)
  AtlasMusic.xcodeproj             generated — do not hand-edit
  CONTRACT.md                      shared API contract between all port agents
```

## Build & run

1. Generate the project (run from any directory):

   ```bash
   python ios/generate_xcodeproj.py
   # prints: OK — wrote ios/AtlasMusic.xcodeproj (… swift, … fonts, … plist)
   ```

2. Open `ios/AtlasMusic.xcodeproj` in Xcode 15.3+ (iOS 17 SDK or newer).

3. Pick the `AtlasMusic` scheme and any iOS 17+ simulator, then **⌘R**.
   The scheme is shared (`xcshareddata/xcschemes/AtlasMusic.xcscheme`).

Continuous integration: `.github/workflows/ios-build.yml` runs on push/PR to `main`/`master`
and on demand (`workflow_dispatch`). It uses a `macos-14` runner, regenerates the project,
builds all three targets for the simulator with signing disabled, runs the XCTest suite, and
uploads the `.xcresult` plus failure logs. To reproduce it locally on a Mac, run
`bash ios/build_ci.sh`. Build artifacts go to `ios/.build/` (git-ignored).

`xcodebuild` errors are converted into GitHub `::error::` annotations by `ios/xcode_annotate.py`,
so they show up in the Checks API. `python ios/ci_errors.py --wait` polls until the run for the
current local commit finishes and prints only the failure annotations — no need to paste logs.

There is no Swift Package Manager manifest by design: the generated project references the
checked-in sources directly, so Xcode picks up new files as soon as you re-run the generator.

### Tests

The XCTest cases in `ios/AtlasMusicTests/` (`AtlasMusicTests.swift` and `AudioDspTests.swift`)
live in a third native target, `AtlasMusicTests` (bundle id `com.atlas.music.tests`). Running
`python ios/generate_xcodeproj.py` generates that target for you — its Sources phase with both
test files, its dependency on the `AtlasMusic` app target, and the shared scheme's test action —
so there is no manual Xcode setup.

To run the suite, open `ios/AtlasMusic.xcodeproj`, select the `AtlasMusic` scheme and an iOS 17+
simulator, then press **⌘U** (Product → Test). The tests are hosted by the app target, so ⌘U
builds the app first; **⌘R** still just launches the app.

### Signing

The generated project sets `CODE_SIGNING_ALLOWED = NO` and an empty `DEVELOPMENT_TEAM`, which
makes simulator builds work on any machine with no Apple ID. For a device or TestFlight build,
open target **Signing & Capabilities**, enable automatic signing, set your team, and change the
bundle identifier if `com.atlas.music` is already taken. You must also add the
`group.com.atlas.music` App Group capability to the app target (and to a widget extension, see
below) before widgets and intents can share playback state.

## Honest platform tradeoffs

This is a faithful *behavioural* port, not a line-for-line one. iOS and Android expose different
system APIs, so a few subsystems are necessarily different:

- **Downloads: no embedded Python / yt-dlp.** Android bundles a Python bridge and downloads
  yt-dlp releases. iOS forbids shipping an interpreter that executes downloaded code, so the
  iOS app downloads **direct audio links** (`https … .mp3/.m4a/.ogg/.opus/.wav/.flac`) with a
  plain `URLSession` and exposes a small `DownloadResolver` protocol for an optional
  **server-side yt-dlp bridge**. Enter the bridge base URL in the Downloads screen; it is
  persisted in `UserDefaults` and the app calls `GET /preview`, `/search`, `/playlist`, `/resolve`.
  YouTube/SoundCloud links without a configured bridge show a real explanation (never a dead
  button). The state machine, progress, speed/ETA, cancellation, SoundCloud Creative-Commons
  gate, auto-import and history are otherwise the same as Android. Files land in the app's
  Documents/`Atlas Music` folder and appear in the iOS Files app.

- **MediaStore → Files / Photos.** Android writes downloads through `MediaStore` and scans with
  `MediaStore`-backed queries. iOS has no MediaStore; `LibraryScanner` uses `FileManager` over
  the Documents directory plus an optional security-scoped folder bookmark, and completed
  downloads insert a `Track` row directly. Files are visible in **Files → On My iPhone → Atlas
  Music**. There is no global "music library" database to scan, so metadata is stored in
  SwiftData rather than written into the audio file (Android's best-effort tag write has no
  public iOS equivalent).

- **Equalizer / Android platform effects.** `AVAudioUnitEQ`, `AVAudioUnitVarispeed` and a mixer
  stand in for Android's `BassBoost`, `Virtualizer`, `LoudnessEnhancer` and `SoundCheck`. iOS
  has no public equivalents, so those effects are approximated with EQ curves + gain
  following the same `AudioDsp` math. Presets and the 9-band manual curve are real and audible;
  the exact device DSP will differ from Android.

- **Volume in iPod mode.** iOS does not allow an app to set the system volume. The click wheel's
  vertical volume gesture drives the app's audio-bus volume (`setFocusVolume`) and shows the
  classic volume HUD; hardware buttons still control the real system volume.

- **Widgets.** `AtlasWidgets.swift` contains a fully implemented `TimelineProvider` with
  small/medium/large views, and `PlaybackIntents.swift` contains the App Intents. WidgetKit
  requires a **separate Widget Extension target** with its own `@main`/`WidgetBundle` (a single
  Xcode target can only have one `@main`, the app). The generator now emits that
  `AtlasMusicWidget` app-extension target automatically — no manual Xcode step is needed. Its
  entry point lives in `Widgets/AtlasWidgets.swift` behind `#if ATLAS_WIDGET_EXTENSION`
  (the generator defines that flag only for the extension), and both `Widgets/*.swift` files
  are compiled into the app *and* the extension. The app and extension
  share the `group.com.atlas.music` App Group via the checked-in `.entitlements` files. The
  widget code is deliberately self-contained (it reads only App Group `UserDefaults`) so it
  compiles unchanged in the extension. Intents write a command into the suite and the running
  app polls it, so widget buttons control real playback.

- **Background audio.** The `audio`, `fetch` and `processing` background modes are declared in
  `Info.plist`. `BGTaskScheduler` registers `com.atlas.music.downloads` to resume queued work;
  iOS background execution time is best-effort and the OS decides when it runs.

- **Cover art.** Android uses its own bitmap loader; iOS uses the deterministic procedural
  `CoverArtKit` (FNV-1a + mulberry32) from the shared utilities. No network artwork is embedded.

- **Fonts.** Space Grotesk and JetBrains Mono are copied verbatim from the Android `res/font`
  folder and registered through `AtlasFont`; system fonts are the fallback.

- **Haptics.** Android's `HapticFeedbackConstants.CLOCK_TICK` maps to
  `UIImpactFeedbackGenerator(.light)`; the optional wheel click uses `playInputClick()`.

## Behavioural parity checklist

- Library scan, favorites, playlists, smart lists, duplicates, search, tag editing — SwiftData repos.
- Gapless/crossfade playback, queue, shuffle/repeat, sleep timer, speed, output routing — AVFoundation.
- Focus/pomodoro with persisted `PomodoroSession` rows, stats and streak — ToolsViewModel.
- iPod mode with four shell finishes, LCD menu tree, cover flow, now-playing scrub and wheel gestures.
- Downloads with preview, search, playlist batch, consent gate, retry/cancel/history and auto-import.
- Widgets and App Intents share state via the `group.com.atlas.music` App Group suite.

All UI uses `AtlasColors` / `AtlasFont` / `Radius` from the shared theme; every screen implements
loading / empty / filled / error states and every control performs real work.

---

PS: this is a vibe coded port.
