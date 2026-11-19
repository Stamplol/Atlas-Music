# Atlas Music (iOS) — SHARED API CONTRACT

All agents MUST read `ios/CONTRACT.md`, `ios/AtlasMusic/Core/Models/Enums.swift`, and
`ios/AtlasMusic/Core/Models/Track.swift` before writing code. Do NOT modify those two files.
Write ONLY the files assigned to you. Swift 5.10 / iOS 17+. SwiftUI + SwiftData + AVFoundation.

## Global conventions
- `@MainActor` on all observable controllers/repositories. `@Observable final class`.
- Dependency injection via SwiftUI environment: `.environment(player)` then
  `@Environment(PlayerController.self) private var player`, same for
  `SettingsStore`, `MusicRepository`, `PlaylistRepository`, `DownloadManager`.
- SwiftData model context via `@Environment(\.modelContext)`.
- Track identity is `track.uri` (String). Player state uses `currentTrackURI: String?`.
- Colors come only from `AtlasColors`; fonts only from `AtlasFont`; radii only from `Radius`.
- Every screen must implement loading / empty / filled / error states where applicable.
- No placeholder "coming soon" buttons. Every control must do real work.
- Comment each file with a one-line `// Atlas iOS — <purpose>` header.

## Already written (DO NOT TOUCH)
- `Core/Models/Enums.swift`: SortOption (.title/.artist/.album/.duration/.dateAdded/.year/.trackNumber),
  ArtworkStyle (.large/.compact), ThemeMode (13 cases), DownloadStatus (.queued/.fetchingInfo/.downloading/
  /.completed/.failed/.cancelled), SmartPlaylist, RepeatMode (Int 0/1/2, `.next`), CoverVariant (12),
  OutputKind, BackgroundMode (14), ClickWheelZone, ToolPage, IpodShellFinish, LibraryDestination, TagUpdateResult.
- `Core/Models/Track.swift`: `@Model final class Track` with fields (all `var`):
  uri, filePath, displayName, title, artist, album, albumArtist, genre, year, trackNumber, discNumber,
  durationMs: Int64, sizeBytes: Int64, dateAddedSec: Int64, dateModifiedSec: Int64, folder, albumId: Int64,
  fingerprint, mimeType, isFavorite: Bool, userEdited: Bool, securityBookmark: Data?;
  computed `resolvedURL`, `displayTitle`, `displayArtist`, `displayAlbum`;
  static `Track.makeFingerprint(title:artist:durationMs:)`.
- `Info.plist`.

## AGENT 1 — data layer (owns these files only)
`Core/Models/Playlist.swift`, `Core/Models/PlayEvent.swift`, `Core/Models/DownloadTask.swift`,
`Core/Models/PomodoroSession.swift`, `Core/Persistence/Persistence.swift`,
`Core/Persistence/SettingsStore.swift`, `Core/Repositories/MusicRepository.swift`,
`Core/Repositories/PlaylistRepository.swift`, `Core/Repositories/LyricsRepository.swift`,
`Core/Repositories/LibraryScanner.swift`.

Required exact APIs:

```swift
@Model final class Playlist { var name: String; var desc: String; var createdAt: Date;
  @Relationship(deleteRule: .cascade) var entries: [PlaylistEntry]; init(name: String, description: String = "") }
@Model final class PlaylistEntry { var position: Int; var addedAt: Date; var track: Track?; var playlist: Playlist? }
@Model final class PlayEvent { var trackURI: String; var playedAt: Date }
@Model final class DownloadTask { var url: String; var videoID: String; var title: String;
  var uploader: String; var thumbnailURL: String; var format: String; var status: DownloadStatus;
  var progress: Double; var bytesDownloaded: Int64; var bytesTotal: Int64; var speedBytesPerSec: Int64;
  var etaSeconds: Int64; var outputPath: String?; var error: String?; var createdAt: Date; var completedAt: Date? }
@Model final class PomodoroSession { var startedAt: Date; var endedAt: Date; var durationMinutes: Int;
  var kind: String; var completed: Bool }

enum Persistence { static func makeContainer() -> ModelContainer }  // all 6 models

@MainActor @Observable final class SettingsStore {
  // ALL settings from Android AtlasSettings, exact names:
  var themeMode: ThemeMode, dynamicColor: Bool, artworkStyle: ArtworkStyle, defaultSort: SortOption
  var gaplessEnabled: Bool, crossfadeEnabled: Bool, crossfadeSeconds: Int
  var equalizerEnabled: Bool, bassBoostEnabled: Bool, bassBoostStrength: Int, normalizationEnabled: Bool, loudnessGainMb: Int
  var headphoneProfileId: String, preampDb: Float, autoHeadroomEnabled: Bool, virtualizerStrength: Int, channelBalance: Float
  var limiterEnabled: Bool, soundCheckWarmth: Float, soundCheckPunch: Float, playbackSpeed: Float
  var ipodModeEnabled: Bool, ipodModeUnlocked: Bool, ipodShellFinish: String, ipodClickSound: Bool
  var sleepTimerDefaultMinutes: Int, excludedFolders: Set<String>, musicFolderURI: String, musicFolderName: String
  var musicFolderOnly: Bool, downloadFormat: String, downloadDestinationURI: String, autoImportDownloads: Bool
  var autoCheckYtdlpUpdates: Bool, diagnosticsEnabled: Bool, libraryIncludesUnsupported: Bool
  var backgroundMode: String, backgroundDensity: Int, backgroundSpeed: Int, backgroundTrail: Int, backgroundReact: Bool
  var sortAlgo: String, pomodoroFocusMinutes: Int, pomodoroBreakMinutes: Int, pomodoroSyncPlayback: Bool
  var sleepTimerEndOfTrack: Bool
  var eqBands: [Int]                 // 9 values, mB
  var sleepTimerEndAt: Date?
  var lastScanMs: Int64
  var ytdlpUpdateNotice: String?
  var savedQueue: SavedQueue?        // struct SavedQueue { ids:[String]; index:Int; positionMs:Int64; hint:String? }
  func saveQueue(ids: [String], index: Int, positionMs: Int64, hint: String?)
  func clearSavedQueue()
  func resetAudio()
  init()                             // reads UserDefaults with all Android defaults/clamps
}
```

Repositories (all `@MainActor final`, `init(context: ModelContext)` except Lyrics):
```swift
struct NameCount: Identifiable { let name: String; let count: Int; var id: String { name } }
struct SearchResults { var tracks: [Track] = []; var albums: [String] = []; var artists: [String] = []; var genres: [String] = [] }
@MainActor final class MusicRepository {
  func allTracks(sort: SortOption) -> [Track]
  func favorites() -> [Track]
  func recentlyPlayed(limit: Int) -> [Track]; func recentlyAdded(limit: Int) -> [Track]; func mostPlayed(limit: Int) -> [Track]
  func albums() -> [NameCount]; func artists() -> [NameCount]; func genres() -> [NameCount]; func folderCounts() -> [NameCount]
  func tracks(album: String) -> [Track]; func tracks(artist: String) -> [Track]
  func tracks(genre: String) -> [Track]; func tracks(folder: String) -> [Track]
  func track(uri: String) -> Track?
  func search(_ query: String, limit: Int = 200) -> SearchResults
  func recordPlay(_ trackURI: String)
  func setFavorite(_ track: Track, _ favorite: Bool)
  func updateTags(_ track: Track, title: String, artist: String, album: String, genre: String, year: Int, trackNumber: Int, discNumber: Int) -> TagUpdateResult.Outcome
  func resetTags(_ track: Track); func applyDownloadMetadata(uri: String, title: String, artist: String, genre: String, year: Int, durationMs: Int64)
  func delete(_ track: Track)
  func findDuplicates() -> [DuplicateGroup]; func unknownCount() -> Int
  func trackCount() -> Int; func totalDurationMs() -> Int64; func totalSizeBytes() -> Int64
  func save()   // try? context.save()
}
```
`DuplicateGroup` is defined by Agent 2 in `Core/Utils/DuplicateDetector.swift` as
`struct DuplicateGroup: Identifiable { let fingerprint: String; let tracks: [Track]; let totalSizeBytes: Int64; var id: String { fingerprint } }` — Agent 1 imports it.

`PlaylistRepository` exact API:
```swift
func playlists() -> [Playlist]
@discardableResult func create(name: String, description: String = "") throws -> Playlist
func rename(_ p: Playlist, name: String, description: String?)
func delete(_ p: Playlist)
func add(_ track: Track, to p: Playlist); func addTracks(_ tracks: [Track], to p: Playlist)
func remove(_ track: Track, from p: Playlist)
func move(_ track: Track, in p: Playlist, to index: Int)
func tracks(in p: Playlist) -> [Track]
func isTrack(_ track: Track, in p: Playlist) -> Bool
@discardableResult func saveQueueAsPlaylist(name: String, tracks: [Track]) -> Playlist?
func save()
```
`LyricsRepository` (not MainActor, but takes no context):
```swift
struct LyricsResult { let lines: [LyricLine]; let source: String; let raw: Bool }
func lyrics(for track: Track) -> LyricsResult?
```
(`LyricLine` + parser from Agent 2.)

`LibraryScanner` (`@MainActor final`, deps music repo + settings):
```swift
struct ScanResult { var added: Int = 0; var updated: Int = 0; var removed: Int = 0; var skippedExcluded: Int = 0; var durationMs: Int64 = 0 }
func scan() async -> ScanResult   // reads a folder bookmark (settings.musicFolderURI / security-scoped bookmark)
```
Scanning on iOS: since MediaStore/SAF don't exist, scan the Documents directory + an optional user-granted folder bookmark, using `FileManager` + `AVAsset` metadata. Skip built-in excluded folders. Preserve userEdited fields. Return a real result. Document the platform difference in comments.

## AGENT 2 — pure utilities / DSP (owns these files only)
`Core/Utils/Format.swift`, `Core/Utils/DuplicateDetector.swift`, `Core/Utils/LyricsParser.swift`,
`Core/Utils/UrlValidator.swift`, `Core/Utils/ClickWheelMath.swift`, `Core/Utils/LibraryFolder.swift`,
`Core/Utils/BuiltInExclusions.swift`, `Core/Utils/YtdlpVersion.swift`, `Core/Utils/DownloadStateMachine.swift`,
`Core/Utils/SoundCloudGate.swift`, `Core/Utils/MediaFormatHelper.swift`, `Core/Utils/EqCurves.swift`,
`Core/Utils/HeadphoneProfiles.swift`, `Core/Utils/AudioDsp.swift`, `Core/Utils/CoverArtKit.swift`,
`Core/Utils/BackgroundEngine.swift`, `Core/Utils/OutputRouter.swift`.

Port the Android algorithms **faithfully** (all constants, arrays, clamps):
- `Format`: duration, eta, speed, bytes, totalDuration, artistAlbumLine.
- `DuplicateDetector`: `DuplicateNormalizer.normalize`, `duplicateFingerprint`, `DuplicateGroup` (exact shape above), `findDuplicates`, `wastedBytes`.
- `LyricsParser`: `LyricLine(timeMs: Int64, text: String)`, `Lyrics.parse(_ raw: String) -> [LyricLine]` (regex `\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]`, emit one line per timestamp, sort).
- `UrlValidator`: `YoutubeLink` (videoID, playlistID, isPlaylist, provider, token, stableKey), `parse`, `canonicalURL`, `canonicalPlaylistURL`, `providerOf`, `isValidYoutubeURL`. Strict https-only, no whitespace, length ≤ 2048.
- `ClickWheelMath`: zones, `angleAt`, `zoneAt`, `shortestAngleDelta`, `accumulateClicks` returning `(Int, Float)`.
- `LibraryFolder`: normalize, label, isExcluded.
- `BuiltInExclusions`: 27 patterns, isBuiltInExcluded, SUMMARY.
- `YtdlpVersion`: Comparable, parse, stringValue.
- `DownloadStateMachine`: canTransition, retry, isTerminal, isActive.
- `SoundCloudGate`: licenseAllowsDownload, licenseLabel, BLOCKED_REASON.
- `MediaFormatHelper`: normalizeDownloadExtension, mimeForExtension, displayAudioFormatLabel, detectExtensionFromMagic.
- `EqCurves`: all 16 preset arrays + PRESET_ORDER + gainsFor + matchingPresetName + MANUAL_BANDS + band groups + BAND_MAX_MB.
- `HeadphoneProfiles`: all 31 profiles with exact ids/brands/models/formFactors/summaries/gainsDb10, ISO_FREQUENCIES_HZ, byID, search.
- `AudioDsp`: `EffectiveAudioCurve`, soundCheckCurveDb, resample9To10BandDb, resample10BandDbToLegacyMb, computeEffectiveCurve, channelGainsDb. Use exact weights/constants from Android.
- `CoverArtKit`: FNV-1a `hashStr`, mulberry32, `variantForSeed`, Catppuccin palette, and `ProceduralCoverView(seed: Int, title: String, ext: String, sizeBytes: Int64, showStrip: Bool)` — a SwiftUI/Canvas deterministic cover with 12 variants + bottom meta strip. Also `static func seed(for track: Track) -> Int` (hash of uri+size+duration) and `seedForPreview(title:sizeBytes:)`.
- `BackgroundEngine`: `AnimatedBackground(mode: BackgroundMode, density: Int, speed: Int, trail: Int, react: Bool, isPlaying: Bool, accent: Color)` — SwiftUI `Canvas` + `TimelineView` (~30fps), with 14 modes (at minimum WAVE, DUNES, STARFIELD, MATRIX, FLOW, AURORA-like PLASMA; others may share a generic deterministic generator but must look distinct and never be empty), reduced-motion stand-down via `@Environment(\.accessibilityReduceMotion)`.
- `OutputRouter`: `struct AudioOutputDevice: Identifiable { id: String; name: String; kind: OutputKind }`, `func listOutputs() -> [AudioOutputDevice]` using `AVAudioSession.sharedInstance().currentRoute.outputs`, `func currentLabel() -> String`.

Swift note: Kotlin Int overflow is intentional in FNV/mulberry — use `&*`, `&+`, `&<<` / `UInt32` shifts.

## AGENT 3 — audio playback (owns these files only)
`Core/Audio/PlayerUiState.swift`, `Core/Audio/AudioEngine.swift`, `Core/Audio/PlayerController.swift`.

```swift
struct PlayerUiState { var connected = false; var isPlaying = false; var isBuffering = false;
  var currentTrackURI: String?; var title = ""; var artist = ""; var album = ""; var artworkSeed: Int?;
  var durationMs: Int64 = 0; var positionMs: Int64 = 0; var bufferedMs: Int64 = 0;
  var repeatMode: RepeatMode = .off; var shuffleEnabled = false; var playbackSpeed: Float = 1;
  var hasQueue = false; var lastError: String? }

@MainActor @Observable final class PlayerController {
  var state = PlayerUiState(); var sleepTimerEndAt: Date?; var queueRevision = 0
  init(music: MusicRepository, settings: SettingsStore)
  func connect(); func disconnect(); func tick()
  func play(); func pause(); func togglePlayPause(); func skipNext(); func skipPrevious(); func seek(to ms: Int64)
  func toggleShuffle(); func cycleRepeatMode(); func setPlaybackSpeed(_ speed: Float)
  func playTracks(_ tracks: [Track], startAt index: Int = 0)
  func addToQueue(_ tracks: [Track]); func playNext(_ tracks: [Track])
  func removeQueueItem(at index: Int); func moveQueueItem(from: Int, to: Int); func clearQueue()
  func queueTracks() -> [Track]
  func toggleFavoriteCurrent()
  func startSleepTimer(minutes: Int); func clearSleepTimer()
  func setFocusVolume(_ volume: Float)
  func applyAudioSettings()   // reads settings + settings.eqBands, calls AudioEngine
  func isFavoriteCurrent() -> Bool
}
```
`AudioEngine` wraps `AVAudioEngine` with two `AVAudioPlayerNode`s for boundary crossfade, a 10-band
`AVAudioUnitEQ`, `AVAudioUnitVarispeed`, and a mixer (volume/balance/preamp). It must handle:
load URL, play/pause/stop, seek, duration/currentTime, rate, crossfade ramps (2/5/8/12 s),
sleep-timer-end-of-track, play-credit hook via a closure `onTrackCompleted: ((String) -> Void)?`,
and `applyEQ(totalBandsDb10: [Float], preampDb: Float, balance: Float, limiter: Bool)`.
Keep all audio-engine work off the main thread where possible but publish state on MainActor.
Document in comments that Android's platform effects (BassBoost/Virtualizer/LoudnessEnhancer) are
approximated with EQ + mixer gain because iOS has no public equivalents.
Handle interruption (phone call), route changes, and audio session category `.playback`.

## AGENT 4 — theme + reusable components (owns these files only)
`Core/Theme/AtlasTheme.swift`, `Core/Theme/AtlasFont.swift`,
`UI/Components/AtlasIconButton.swift`, `UI/Components/SectionHeader.swift`,
`UI/Components/EmptyState.swift`, `UI/Components/AlbumArt.swift`, `UI/Components/TrackRow.swift`,
`UI/Components/TrackListContent.swift`, `UI/Components/MiniPlayerBar.swift`,
`UI/Components/MeridianArc.swift`, `UI/Components/Dialogs.swift`,
`UI/Components/StatPill.swift`.
Also COPY (binary copy, do not edit) `D:\atlas-music\app\src\main\res\font\space_grotesk_{400,500,600,700}.ttf`
and `jetbrains_mono_{400,500,600,700}.ttf` into `ios/AtlasMusic/Resources/Fonts/`.

```swift
struct AtlasColors { var background, surface, surfaceElevated, surfaceHighest, text, textMuted,
  accent, accentBright, accentDim, onAccent, error, scrim, auraTop, glow, scrimSoft, border: Color }
enum AtlasTheme { static func colors(for mode: ThemeMode, dynamic: Bool = false) -> AtlasColors }
// Palettes: AtlasDark "deep observatory" bg 0xFF0B0D12 surface 0xFF12151D elevated 0xFF181D29
// highest 0xFF212839 text 0xFFF4F1E8 muted 0xFFA8A79C accent 0xFFE5A83D ...
// AtlasLight "warm paper" bg 0xFFFAF7EF ... accent 0xFF9A6B12 ...
// + 11 curated presets (Mocha/Latte/Gruvbox/Tokyo Night/Nord/Dracula/Kanagawa/Rose Pine/Everforest/Oxocarbon)
// Use the exact hex values from Android ui/theme/Color.kt & Theme.kt (read them).
enum AtlasFont { static func display()->Font; headline(); title(); eyebrow(); body(); caption(); mono(size: CGFloat) }
// Space Grotesk for text, JetBrains Mono for metadata/eyebrows. Register fonts via a static init
// (Font.register) using the copied .ttf files; fall back to system fonts if unavailable.
enum Radius { static let sm: CGFloat = 10; static let lg: CGFloat = 16; static let xl: CGFloat = 24 }
enum AtlasMotion { static let settle = Spring(duration: 0.45, bounce: 0.18); static let quick = Spring(duration: 0.25, bounce: 0.05) }
```
Component signatures (exact, so callers compile):
```swift
struct AtlasIconButton: View { init(systemName: String, label: String, action: @escaping () -> Void,
  disabled: Bool = false, tint: Color? = nil) }
struct SectionHeader: View { init(_ title: String, actionLabel: String? = nil, action: (() -> Void)? = nil) }
struct EmptyState: View { init(title: String, body: String, actionLabel: String? = nil, action: (() -> Void)? = nil) }
struct AlbumArt: View { init(track: Track?, size: CGFloat = 46, corner: CGFloat = 10, showStrip: Bool = true) }
struct TrackRow: View { init(track: Track, onPlay: @escaping () -> Void, onToggleFavorite: @escaping () -> Void,
  onPlayNext: @escaping () -> Void, onAddToQueue: @escaping () -> Void, onAddToPlaylist: @escaping () -> Void,
  onInfo: @escaping () -> Void, onEditTags: (() -> Void)? = nil, onRemove: (() -> Void)? = nil, compact: Bool = false) }
struct TrackListContent: View { init(tracks: [Track], onPlay: @escaping (Track) -> Void,
  onToggleFavorite: @escaping (Track) -> Void, onPlayNext: @escaping (Track) -> Void,
  onAddToQueue: @escaping (Track) -> Void, onAddToPlaylist: @escaping (Track) -> Void,
  onInfo: @escaping (Track) -> Void, onEditTags: ((Track) -> Void)? = nil,
  onRemove: ((Track) -> Void)? = nil, emptyTitle: String = "Nothing here yet",
  emptyBody: String = "", compact: Bool = false) }
struct MiniPlayerBar: View { init(onOpenNowPlaying: @escaping () -> Void) }   // reads PlayerController from env
struct MeridianArc: View { init(progress: Double, size: CGFloat = 310) }
struct StatPill: View { init(value: String, label: String) }
struct AddToPlaylistDialog: View { init(track: Track?, playlists: [Playlist], onAdd: @escaping (Playlist) -> Void, onDismiss: @escaping () -> Void) }
struct TrackEditorDialog: View { init(track: Track, onSave: @escaping (String,String,String,String,Int,Int,Int) -> Void,
  onReset: @escaping () -> Void, onDismiss: @escaping () -> Void) }
struct CreatePlaylistDialog: View { init(onCreate: @escaping (String) -> Void, onDismiss: @escaping () -> Void) }
```

## AGENT 5 — main screens (owns these files only)
`UI/Root/RootView.swift`, `UI/Root/PermissionGate.swift`, `UI/Screens/HomeView.swift`,
`UI/Screens/LibraryView.swift`, `UI/Screens/SearchView.swift`, `UI/Screens/SmartListView.swift`,
`UI/Screens/PlaylistDetailView.swift`, `UI/Screens/QueueView.swift`, `UI/Screens/DuplicatesView.swift`,
`UI/Screens/TrackInfoView.swift`, `UI/Navigation/AppTab.swift`.

```swift
enum AppTab: String, CaseIterable, Identifiable { case home, library, search, tools, settings; var id: String { rawValue }
  var systemName: String; var label: String }
struct RootView: View { init() }  // owns TabView/ NavigationStack, mini player, now-playing sheet,
  app-level dialogs (add-to-playlist, track editor), iPod-mode short-circuit, snackbar/error banner.
  It reads SettingsStore/MusicRepository/PlaylistRepository/PlayerController from environment.
struct HomeView: View { init(onOpenLibrary: @escaping () -> Void, onOpenTools: @escaping () -> Void,
  onOpenDownloads: @escaping () -> Void, onOpenPlaylists: @escaping () -> Void,
  onPlayAll: @escaping ([Track], Int) -> Void, onSecretUnlock: @escaping () -> Void) }
struct LibraryView: View { init(onOpenPlaylist: @escaping (Playlist) -> Void,
  onOpenCollection: @escaping (String, String) -> Void,   // (kind, value) kind in album/artist/genre/folder/smart
  onOpenDuplicates: @escaping () -> Void, onPlayAll: @escaping ([Track], Int) -> Void) }
struct SearchView: View { init(onOpenCollection: @escaping (String, String) -> Void,
  onPlayAll: @escaping ([Track], Int) -> Void) }
struct SmartListView: View { init(title: String, subtitle: String? = nil, tracks: [Track],
  onPlayAll: @escaping ([Track], Int) -> Void) }
struct PlaylistDetailView: View { init(playlist: Playlist, onPlayAll: @escaping ([Track], Int) -> Void) }
struct QueueView: View { init(onDismiss: @escaping () -> Void) }
struct DuplicatesView: View { init() }
struct TrackInfoView: View { init(track: Track) }
struct PermissionGate: View { init(onGranted: @escaping () -> Void) }
```
All track-row callbacks must be wired to real PlayerController / repository actions.
RootView must present `NowPlayingView` as a sheet, `QueueView`, `DownloadsView`, `ToolsView`,
`SettingsView`, `SearchView`, etc. as navigation destinations. Use `NavigationLink(value:)`.

## AGENT 6 — signature + tools screens (owns these files only)
`UI/Screens/NowPlayingView.swift`, `UI/Screens/LyricsView.swift`, `UI/Screens/ToolsView.swift`,
`UI/Tools/ToolsHub.swift`, `UI/Tools/AudioStudioView.swift`, `UI/Tools/FocusView.swift`,
`UI/Tools/SleepView.swift`, `UI/Tools/LibraryCareView.swift`.

```swift
struct NowPlayingView: View { init(onDismiss: @escaping () -> Void, toolsVM: ToolsViewModel,
  onOpenQueue: @escaping () -> Void) }
struct LyricsView: View { init(track: Track, positionMs: Int64, onSeek: @escaping (Int64) -> Void,
  onClose: @escaping () -> Void) }
struct ToolsView: View { init(initialPage: ToolPage = .hub, onOpenNowPlaying: @escaping () -> Void,
  onOpenDownloads: @escaping () -> Void, onOpenDuplicates: @escaping () -> Void) }
struct ToolsHub: View { init(onOpen: @escaping (ToolPage) -> Void, onOpenDownloads: @escaping () -> Void,
  onOpenSettings: @escaping () -> Void) }
struct AudioStudioView: View { init() }
struct FocusView: View { init() }
struct SleepView: View { init() }
struct LibraryCareView: View { init(onOpenDuplicates: @escaping () -> Void) }
```
`ToolsViewModel` is defined by Agent 7 in `UI/ViewModels/ToolsViewModel.swift` with this exact API:
```swift
@MainActor @Observable final class ToolsViewModel {
  var pomodoroMode: String = "FOCUS"; var running = false; var remainingMs: Int64 = 0; var totalMs: Int64 = 0
  var focusMinutesToday = 0; var sessionsToday = 0; var streakDays = 0
  var sessions: [PomodoroSession] = []; var lastEvent = ""
  init(music: MusicRepository, settings: SettingsStore, player: PlayerController)
  func startPomodoro(); func pausePomodoro(); func resumePomodoro(); func resetPomodoro(); func switchMode()
  func setFocusMinutes(_:); func setBreakMinutes(_:); func setSyncPlayback(_:)
  func startSleepTimer(minutes: Int); func clearSleepTimer()
  func applyEqPreset(_ name: String); func setCustomBands(_ bands: [Int]); func resetAudio()
  func fixUnknowns(artist: String, album: String)
}
```
NowPlayingView must preserve: full-bleed AnimatedBackground + blurred artwork veil + scrim,
the 310pt **Meridian** arc with gold progress dot, spring "settle" artwork scale, title/metadata,
seek slider with monospace times, transport row (shuffle/prev/76pt gold FAB/next/repeat),
secondary row (favorite, sleep chip, queue), output chip, speed chip, Lyrics/Info toggles,
landscape split layout with tools panel, haptic-ish spring feedback. All controls must call PlayerController.

## AGENT 7 — app entry, downloads, iPod, widgets, intents, VM, project (owns these files only)
`AtlasMusicApp.swift`, `UI/Navigation/AppEnvironment.swift` (if needed), `UI/ViewModels/ToolsViewModel.swift`,
`UI/Screens/DownloadsView.swift`, `UI/Screens/IpodModeView.swift`,
`Core/Downloads/DownloadManager.swift`, `Widgets/AtlasWidgets.swift`, `Widgets/PlaybackIntents.swift`,
`ios/generate_xcodeproj.py`, `ios/README_iOS.md`.

```swift
@main struct AtlasMusicApp: App { init(); var body: some Scene { WindowGroup { ... } } }
// Builds ModelContainer + SettingsStore + repos + PlayerController + ToolsViewModel + DownloadManager,
// injects into environment, hosts RootView. Handles file-open URLs (onOpenURL) via player playExternalURI.
@MainActor @Observable final class DownloadManager {
  var tasks: [DownloadTask] = []; var isAvailable = true
  init(context: ModelContext, settings: SettingsStore)
  func isCompleteURL(_ raw: String) -> Bool
  func preview(_ raw: String) async -> DownloadPreview
  func search(_ query: String, provider: String) async -> DownloadSearchResult
  func enqueue(url: String, title: String, uploader: String, thumbnail: String, format: String) async throws
  func enqueuePlaylist(url: String, format: String) async -> (enqueued: Int, duplicates: Int, failed: Int)
  func retry(_ task: DownloadTask); func cancel(_ task: DownloadTask); func delete(_ task: DownloadTask)
  func clearCompleted(); func clearHistory(); func recoverInterrupted()
}
struct DownloadPreview { var ok = false; var title=""; var uploader=""; var durationSec=0; var thumbnailURL="";
  var isPlaylist = false; var playlistTitle=""; var entries: [String] = []; var license: String?; var error: String? }
struct DownloadSearchResult { var ok=false; var provider=""; var query=""; var entries:[DownloadSearchItem]=[]; var error: String? }
struct DownloadSearchItem: Identifiable { var id: String; var title: String; var uploader: String;
  var durationSec: Int; var thumbnailURL: String; var url: String }
```
Downloads on iOS: no embedded Python/yt-dlp. Implement a `URLSession`-based direct-audio downloader
plus a clearly documented `Resolver` protocol hook for a future server-side yt-dlp bridge. Keep the
same state machine, persistence, progress, cancellation, SoundCloud CC gate, and auto-import rescan.
Never present a dead button — when a resolver isn't configured, explain it in the UI with a real action
("Add a resolver URL in Settings" is acceptable only if Settings has that field wired).

`IpodModeView`: full classic-iPod simulation with 4 shell finishes, LCD, click wheel (use ClickWheelMath),
menu tree, cover flow, now playing, haptics, exit. Reads PlayerController.
`AtlasWidgets`: WidgetKit provider with small/medium/large families using App Intents
(`TogglePlaybackIntent`, `NextTrackIntent`, `StartSleepTimerIntent`) and shared state via App Group
UserDefaults suite name `"group.com.atlas.music"`. Timeline for countdowns.
`generate_xcodeproj.py`: run it with Python to emit `ios/AtlasMusic.xcodeproj/project.pbxproj` enumerating
every .swift/.ttf/.plist file under `ios/AtlasMusic`, with valid build settings (iOS 17, Swift 5.10,
bundle id com.atlas.music, app icon asset placeholder, no code signing issues for simulator),
and a shared scheme. Print "OK" when done. It must be idempotent.

## AGENT 8 — tests (owns these files only)
`ios/AtlasMusicTests/AtlasMusicTests.swift`, `ios/AtlasMusicTests/AudioDspTests.swift`.
Port the Android JUnit tests (DownloadStateMachine, DuplicateDetector, LibraryFolder, UrlValidator,
YtdlpVersion, EqCurves, MediaFormatHelper, SoundStudioAndWheel) to XCTest, calling Agent 2's APIs.
