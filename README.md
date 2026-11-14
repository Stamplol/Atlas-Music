# Atlas Music

Local-first Android music player. No ads, no subscriptions, no accounts.

Not a streaming service with local caching on the side — local library first.

> **Runtime:** `JDK 17` + `Gradle 8.10` (wrapper). `ANDROID_HOME`/`ANDROID_SDK_ROOT` point at SDK 35.
> **Python:** `Python 3.14` on the build host — must match Chaquopy `3.14` in `app/build.gradle.kts`.
>
> **Storage:** `Room` + `DataStore`, on-device only. `READ_MEDIA_AUDIO` indexes your files; `INTERNET` fires only when you paste a link or check yt-dlp.

```
┌─────────────────────────────┐
│   YOUR DEVICE (no accounts) │
│                             │
│  [ MediaStore ] ──┐         │
│    ▲              │  scan   │
│    │         ┌────▼─────┐   │
│  [ SAF folder ] ─┤  Room  │ ─► [ Compose UI ]
│                   │  DB v3 │◄── Favorites / Playlists
│  [ Music/Atlas ]  └────┬───┘
│         ▲              │ MediaItem (URI + extras)
│  [ yt-dlp queue ]      │
│    via Chaquopy         ▼
│              [ MediaService ]
│              ExoPlayer + EQ
│               │      │
│          [ Notification ] [ Widget / Tile ]
│         6-button deck   cover + progress
└─────────────────────────────┘
```

## Components

| Folder | What | Tech | Status |
|--------|------|------|--------|
| `app/src/main/java/com/atlas/music` | App + playback + downloads + UI | Kotlin, Compose, Material 3, Media3, Room, DataStore, WorkManager | v1.0 (debug) |
| `app/src/main/assets/python` | yt-dlp bridge only | Python 3.14, Chaquopy, `yt-dlp 2025.10.22`, `mutagen 1.47` | pinned |
| `app/src/main/res` | Themes, fonts, widget layouts, vector icons | Space Grotesk + JetBrains Mono, Satins Gold `#E5A83D` | shipping |
| `app/build/outputs/apk` | Debug APK (built, not committed) | — | ~113 MB |

## Quick start

### 1. Build

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:assembleDebug   # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest   # 32 tests: links, fingerprints, semver, state machine, folders
```

APK is arm64-v8a + x86_64 only (Chaquopy has no 32-bit). Most of the size is CPython.

### 2. Install + scan

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
# Grant music access → Home → Scan library
# Custom folder: Settings → Library → Music folder → Change (persistable SAF tree)
```

### 3. Play

Tap any track → background playback, lockscreen + QS tile + shade deck. Swipe up from mini-player → Now Playing.

## Layout

- `AtlasApplication` — `ServiceLocator` (manual DI), Python start, channels (`atlas.playback` / `atlas.downloads` / `atlas.ytdlp`)
- `MainActivity` — permission gate, edge-to-edge, `ACTION_VIEW` music intents
- `di/` — `ServiceLocator`, `SettingsRepository`, `AtlasSettings` (theme, EQ, crossfade, speed, sleep, folder, pomodoro, background)
- `data/db/` — `tracks`, `playlists`, `playlist_tracks`, `play_events`, `download_tasks`, `pomodoro_sessions` (v3, destructive from v1/v2)
- `data/repo/` — `MusicRepository` (MediaStore + SAF scan), `PlaylistRepository`
- `playback/` — `AtlasPlaybackService` (MediaLibraryService), `PlaybackConnection`, `MediaItemFactory` (+ extras: track id, mime, genre, year, folder), `AtlasNotificationProvider` (6-button deck), `AtlasBitmapLoader` (512 px covers)
- `downloads/` — `PythonBridge`, `DownloadManager`, `DownloadForegroundService`, `YtdlpUpdater`
- `widget/` — 7 providers: Now Playing 4×1, Small 2×1, Large 4×2, Focus 4×1, Tools 2×1, Stats 4×1, Sleep 2×1
- `ui/` — `AtlasMusicApp` (NavHost, bottom bar / rail), `theme/`, `components/` (covers, `TrackEditorDialog`), `screens/` (Home, Library, Search, **Tools**, NowPlaying, Queue, PlaylistDetail, Duplicates, SmartList, TrackInfo, Downloads), `viewmodel/` (one VM per screen + `TrackActions`)
- `assets/python/atlas_ytdlp.py` — only Python in-process (`fetch_info`, `list_playlist_entries`, `download`, `sha256_file`, `write_tags`/`read_lyrics` via Mutagen)

Data: `MediaStore / SAF → scan() → Room → Flow → Compose`. Playback: `UI → MediaController → Service → ExoPlayer`. UI never owns the player.

## Features

**Library** — MediaStore scan (`IS_MUSIC NULL`-inclusive), SAF custom folder (recursive, retriever per file), excluded folders, incremental rebuild, Songs/Albums/Artists/Genres/Folders/Playlists with live counts, ranked substring search (SQL `LIKE`, debounced), Smart Lists, duplicates by `title|artist|duration` fingerprint, track info sheet, default sort.

**Playback** — gapless, optional boundary crossfade, shuffle/repeat, seek, speed 0.5–2×, EQ + BassBoost + LoudnessEnhancer on the player session, sleep timer, queue reorder/remove/clear, position+queue persisted (restores paused, never autoplays), headset/Bluetooth button handling, audio-focus + output routing (DAC/BT/speaker).

**Tag editing** — row menu → *Edit tags…* or info sheet → Title/Artist/Album/Genre/Year/Track/Disc. Writes DB instantly with `userEdited` flag (survives rescans) + best-effort Mutagen embedded rewrite (ID3/Vorbis/MP4). *Fix unknowns* batch mode + *Reset to file values*.

**Now Playing** — Meridian arc (gold dot rides progress), checksum-seeded covers (SHA-256 of first 1 MB + size), blurred veil, live output label, speed pill. Landscape: cover left, controls right, **immersive** (status/nav hidden, swipe to peek), SVG wrench bottom-right toggles left-pane tools. **Lyrics (offline)** — sidecar `.lrc` next to the track wins; falls back to embedded USLT/Vorbis/MP4 tags via mutagen. Synced lines highlight the current position; raw text renders as a scroll. Toggle in the top bar — both orientations.

**Notification / QS / widgets** — `atlas.playback`, `VISIBILITY_PUBLIC`, gold light, mono small icon. Deck: Prev · Play/Pause · Next · Shuffle · Repeat · ♥ (+ rich text `Artist · Album · Year · Format`). Tap rhythm: shade chip → expanded deck. Tile: one-tap play/pause. 7 widgets (covers via `AtlasBitmapLoader`), Pomodoro/sleep from Tools.

**Tools** (own screen, back to Home) — one scroll, no tabs. **FOCUS LAB** hero: gold ring burns down the session (amber arc, ready/paused/running states), today's minutes + session count, 15/25/50 presets, custom durations, streak counter, duck-to-35% toggle, ledger of the last sessions. **SOUND**: EQ presets as live 9-band sparkbars (Flat bypasses the EQ entirely), playback speed, output picker. **SLEEP**: timer chips + countdown and a first-class "Stop at end of track" tile. **LIBRARY CARE**: `tracks · hours · size` mono line, gold panel when tracks lack tags (one tap fixes all), duplicates chevron. Dry maintenance footer (downloads, settings). Landscape → two-column table dashboard: PLAYING (5-button transport) / SOUND with EQ chips + speed / FOCUS with timer + end-of-track / LIBRARY.

**Downloads** — paste a YouTube link (video, Short, or playlist) → Validate → preview → format (Opus/M4A) → consent → queue (`temp/` staging → verified move to `Music/Atlas Music` or SAF) → auto-import rescan. No transcoder (no FFmpeg). History, retry, duplicate detection. **Playlists**: paste a `/playlist?list=` link and Atlas flat-lists the entries (AuraFetch logic), shows title/count + the first five tracks, and enqueues every playable entry as its own task — private/deleted ones are skipped with a per-video summary (`N queued · M already in library`), not fatal. Watch URLs with a `list=` param offer the same toggle. Robustness: `ignoreerrors` + `skip_unavailable_fragments` + `windowsfilenames`, 4 concurrent fragments, 10 MiB chunks, 10 retries, format-unavailable falls back to `bestaudio/best`.

**yt-dlp** — bundled `2025.10.22`; Check → PyPI wheel → SHA-256 vs PyPI JSON → stage under `filesDir/ytdlp-updates/` → import verify → rollback on fail. `Restore bundled` one tap. Updates only from official wheels.

**Themes / backgrounds** — Atlas Dark/Light + Dynamic Color + 10 me-when-music presets (Mocha, Latte, Gruvbox, Tokyo Night, Nord, Dracula, Kanagawa, Rosé Pine, Everforest, Oxocarbon). Covers: checksum-seeded, 12 variants (geo/rings/grid/diag/dunes/aurora/meridian/arcs/noise/spark/horizon/orbit). Live canvas behind Now Playing (port of `me-when-music` bg.js, morph skipped): conway/boids/wave/maze/sort/starfield/matrix/flow/lorenz/pendulum/plasma/langton/dunes. Density/speed/trails/react knobs, reduced-motion aware.

## Privacy

No accounts, no telemetry SDK, no uploads of library/history. `READ_MEDIA_AUDIO` for your files only; `INTERNET` only for the two user-triggered paths above; notifications only for playback/downloads. Everything (Room/DataStore) stays on-device.

## Status

| Gate | Status |
|---|---|
| `assembleDebug` | ✅ green |
| `testDebugUnitTest` | ✅ green (32) |
| Emulator, API 35 x86_64 | ✅ passed — home/library/search/tools/dashboard/landscape player, notification, prefs, scan |
| Downloads end-to-end | ✅ passed (emulator) |
| yt-dlp update + rollback | ✅ passed both directions (2025 → 2026 → restore) |
| Real device + signing | not configured |

## Honest tradeoffs

- **DB v3 is destructive** (`fallbackToDestructiveMigration`, v1/v2 → v3). One Rescan after upgrade restores the library; playlists/favorites/history do not survive the bump.
- **Sleep timer dies with the process.** Playback state survives; the countdown doesn't (no WorkManager wake).
- **Crossfade is a boundary fade**, not DJ beat-matching; gapless is always on.
- **File tag rewrite is best-effort.** Works when the source is a real file (mutagen handles MP3/FLAC/Ogg/M4A). SAF-only files save to Atlas only.
- **Queue skim** — notification/scans hold decoded durations only when the extractor succeeds; fake/untagged test files sit at `0:00` in every player.
- **Lyrics are offline from your file** — dropping an `.lrc` with the same stem next to the audio is enough. Embedded USLT/Vorbis/MP4 tags are read on your device, nothing is fetched.
- **Drag-reorder** is up/down arrows; no FFmpeg (see Downloads); no BLE/Auto interop.

## Legal

Includes yt-dlp for **content you are authorized to download** only. No DRM/paywall bypass, no circumvention code. Comply with YouTube ToS and applicable copyright law. yt-dlp © its authors, Unlicense; bundled updates only from official PyPI wheels.

## Design

Satins Gold `#E5A83D` as the single accent, dark observatory base `#0E1016`, warm-paper light, rounded 8/12/20/28 dp, Space Grotesk display + JetBrains Mono metadata. Motion: Meridian Settle (cover spring, gold dot rides the arc), home entrance spring, tab fadeIn210/fadeOut90, Now Playing slide+fade. Transform/opacity only, reduced-motion respected.

## License

Atlas Music is free to audit. Bundled deps retain their licenses (Settings → Application → Open-source licenses).
