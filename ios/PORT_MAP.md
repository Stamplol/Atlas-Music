# Atlas Music — Android → iOS port map

| Android (Kotlin / Compose) | iOS (Swift / SwiftUI) | Notes |
|---|---|---|
| `data/db/*` Room v3 (`atlas_music.db`) | `Core/Models/*` SwiftData `@Model`, `Core/Persistence/Persistence.swift` | 6 entities, unique URI, cascade playlist entries |
| `di/SettingsRepository` DataStore | `Core/Persistence/SettingsStore.swift` (`UserDefaults`) | same defaults + clamps, plus EQ bands / saved queue / sleep end |
| `data/repo/MusicRepository` | `Core/Repositories/MusicRepository.swift` | same queries re-expressed as SwiftData predicates/sorts |
| `data/repo/PlaylistRepository` | `Core/Repositories/PlaylistRepository.swift` | position-ordered entries |
| `data/repo/LyricsRepository` | `Core/Repositories/LyricsRepository.swift` + `Core/Utils/LyricsParser.swift` | sidecar `.lrc` + embedded metadata via AVAsset |
| MediaStore / SAF scan | `Core/Repositories/LibraryScanner.swift` | Files/Photos + security-scoped folder bookmark |
| `playback/AtlasPlaybackService` (Media3/ExoPlayer) | `Core/Audio/AudioEngine.swift` (AVAudioEngine) | 2 player nodes, 10-band EQ, varispeed, crossfade watcher |
| `playback/PlaybackConnection` | `Core/Audio/PlayerController.swift` | identical `PlayerUiState` fields, queue ops, sleep timer |
| `playback/AtlasBitmapLoader` procedural art | `Core/Utils/CoverArtKit.swift` | same FNV-1a + mulberry32 + 12 variants |
| `ui/components/BackgroundEngine` (14 modes) | `Core/Utils/BackgroundEngine.swift` | SwiftUI Canvas + TimelineView, reduced-motion stand-down |
| `util/AudioDspMath`, `EqCurves`, `HeadphoneProfiles` | `Core/Utils/AudioDsp.swift`, `EqCurves.swift`, `HeadphoneProfiles.swift` | constants ported verbatim |
| Remaining `util/*` | `Core/Utils/*` | Format, UrlValidator, DuplicateDetector, ClickWheelMath, etc. |
| `ui/AtlasMusicApp` navigation | `UI/Root/RootView.swift`, `UI/Navigation/AppTab.swift` | 5 tabs + mini player + sheets |
| `ui/screens/*` | `UI/Screens/*` | all screens ported with native SwiftUI idiom |
| `ui/screens/tools/*` | `UI/Tools/*` | Audio Studio, Focus, Sleep, Library Care, Hub |
| `ui/screens/IpodModeScreen` | `UI/Screens/IpodModeView.swift` | click wheel math reused |
| `widget/*` RemoteViews (7 widgets) | `Widgets/AtlasWidgets.swift` | WidgetKit small/medium/large + App Intents |
| `downloads/*` Chaquopy yt-dlp + foreground service | `Core/Downloads/DownloadManager.swift` | URLSession + pluggable Resolver (no embedded Python on iOS) |
| `ui/theme/*` | `Core/Theme/*` + copied `.ttf` files | Space Grotesk + JetBrains Mono, gold accent, all 13 themes |
| JUnit tests (60) | `ios/AtlasMusicTests/*` | XCTest port of the pure-logic tests |

## Non-portable / adapted by design

1. **yt-dlp / Chaquopy** — iOS forbids embedded Python interpreters in the same way Android allows
   Chaquopy. Downloads use a native `URLSession` pipeline for direct audio URLs and a `Resolver`
   protocol seam for an optional server-side yt-dlp bridge. The SoundCloud CC gate, state machine,
   progress, cancellation, persistence and auto-import are preserved.
2. **DSP effects** — `DynamicsProcessing`/`BassBoost`/`Virtualizer`/`LoudnessEnhancer` are Android
   platform effects. iOS uses `AVAudioUnitEQ` + mixer gain + `AVAudioUnitVarispeed`; the exact
   curve math from `AudioDspMath` is preserved, so the response graph and profiles are identical.
3. **MediaStore / SAF** — replaced with a Documents/Files scan plus an optional user-granted
   security-scoped folder bookmark.
4. **Home-screen widgets / QS tile** — WidgetKit + App Intents; the QS tile has no iOS equivalent
   (Control Center / Lock Screen controls are provided by the audio session + widgets instead).
