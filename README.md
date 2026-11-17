# Atlas Music

**A local-first Android music player.** No accounts, no ads, no subscriptions, no listening history uploaded.
Your files stay on your device — read by MediaStore and by any SAF tree you point at.

```
┌─────────────────────────┐
│  YOUR DEVICE            │
│                         │
│  MediaStore ──┐         │
│  SAF folder ──┼─► Room ─┼─► Compose UI
│  Music dir   ─┘        │          ▲
│                ▲       ▼          │
│  yt-dlp queue  │  TrackEntity ────┘
│  (HTTPS only)  │       │
│                ▼       ▼
│            [ Media3 Service ]
│            ExoPlayer + DSP chain ──► Notification / QS tile / widgets
└─────────────────────────┘
```

---

## Quick start

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME

./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest    # 60 tests, offline
```

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
# Grant music access → open the app → Library rebuilds itself
```

Custom folder: **Settings → Library → Music folder → Change** (persistent SAF permission).

---

## What it does

### Library

- **MediaStore + SAF scan** — recursive, retriever-per-file. Untagged files are included (IS_MUSIC often NULL), or excluded on demand via **Excluded folders**.
- Songs · Albums · Artists · Genres · Folders · Playlists, all with live counts and durations.
- **Smart Lists** — favorites, recently played, recently added — computed live, never stored.
- **Search** — debounced, substring, matches artist / album / title / genre / folder.
- **Duplicates** — fingerprint on `title | artist | duration`, keeps the oldest by default; manual pick "Keep this".
- **Track info sheet** — shows everything the DB has, including checksum-seeded procedural cover (12 variants, SHA-256 of the file head).
- **Tag editing** — row menu → **Edit tags**. Writes the DB instantly and flips `userEdited` (survives rescans). Best-effort embedded rewrite via mutagen (MP3/FLAC/Vorbis/MP4). **Fix unknowns** batch-fixes `Unknown artist` rows. Reset to file values any time.

### Playing

- Gapless, shuffle, repeat (one/all/off), seek, speed 0.5–2×.
- **10-band dynamics chain** on the player session: preamp + AutoEq calibration curves + L/R balance + peak limiter (falls back to `Equalizer` on DACs without `DynamicsProcessing`).
- **Harman-target headphone profiles** — Sony, Apple, Sennheiser, AKG, Shure, Audio-Technica, Beyerdynamic, Bose, JBL, Audeze, Beats, Focal — searchable by name, with an A/B bypass at the top of Audio Studio.
- ** Bass boost, Loudness, Virtualizer** on the same session; **Sound Check** warmth/punch as a gentle nudge.
- **Boundary crossfade** — 2 / 5 / 8 / 12 second fade-out on the outgoing track (gapless always on; crossfade is ornament, not a DJ engine).
- **Stop at end of track** — sleep timer ends before the next track starts, even mid-queue.
- **Sleep** with minute or end-of-track modes. **Speed** slider. **Output** picker (USB DAC / Bluetooth / speaker / headset).
- **Queue** — reorder, remove one, clear, **Save as playlist** (prefilled name, order preserved).
- **Notification deck** — Prev · Play/Pause · Next · Shuffle · Repeat · ♥ · title · art · progress. **Lockscreen / QS tile / widgets** (7 of them) speak the same language.

### Lyrics

Fully **offline**. Sidecar `.lrc` wins; embedded USLT/Vorbis/MP4 read via mutagen. Synced lines highlight as the track plays. No feed, no fetch, no server.

### Audio Studio

17 presets (Flat → Classical). **Manual 9-band EQ** with named groups (LOW END / MIDRANGE / HIGH END), ±6 dB in 0.5 dB steps. The EQ bypass flips the whole DSP chain off for instant A/B. "Reset audio" returns everything to flat + bypass.

### Tools

Tiled hub — **Audio Studio** (EQ, bass, loudness, balance, crossfade, speed), **Focus** (Pomodoro with streak, bar chart, rest toggle), **Sleep** (minute or end-of-track), **Library care** (origin counts, tag-repair panel, duplicates chevron), **Downloads**, **Settings**. Landscape → 3-column compact grid.

**Focus LAB** — gold arc burns down the timer (**/ READY / PAUSED / REMAINING**), today's minutes + streak, ledger of the last sessions, duck-to-35% toggle, break auto-switch. **End of track** auto-pause with queue clears.

**iPod mode** (hidden) — tap the version string in Settings five times and Atlas collapses into a fully playable "first generation" experience: matte body (silver / black / gold / red / blue), click-wheel navigation, circular LCD, menu trees, play-all, shuffle, haptics. Tap the top-left badge to return.

### Downloads (opt-in, user-initiated)

Paste a **YouTube or SoundCloud** link (track, Short, playlist) **or a bare query** (`never gonna give you up`) — Atlas searches the provider directly (`ytsearchN`, `scsearchN`) and lists the first eight results with art. Paste a bare playlist link and the entries are flattened in (AuraFetch logic).

- **Creative Commons gate** — SoundCloud downloads are limited to tracks the artist licensed for reuse (`cc-*` / `cc0`); the license is checked before any bytes move, enforced per-track even in batch sets.
- **Duplicate detection** per video ID. **Playlist summary** — `N queued, M already in your library`.
- **In-app progress** — `FETCHING_INFO → DOWNLOADING (X%) → COMPLETE`. Retry from History. No app reopens during a multi-gig playlist.
- Metadata comes from the downloaded file — artist, title, album, year, genre — written with mutagen for real files or `applyDownloadMetadata` for SAF-only imports.

### Widgets & notification

7 widgets (Now Playing, Small, Large, Sleep, Focus, Tools, Stats). Color-matched to the cover or the current theme.

---

## Honest tradeoffs

| Thing | What it actually means |
|---|---|
| **DB v3 destructive migration** | upgrades from pre-v3 wipe Room data; playlists/favorites/history start blank. Library, playlists, queue survive except history. |
| **Sleep timer + process death** | the timer doesn't survive process kill (no WorkManager wake). |
| **Crossfade** | boundary fade, not DJ beat-sync. Gapless is the default. |
| **File rewrite is best-effort** | works for real `.mp3` / `.flac` / `.m4a` / `.ogg` files via mutagen. SAF-only paths save in Atlas, not back. |
| **Queue skim** | durations decoded per file — untagged/broken files show 0:00 in every player, including this one. |
| **DSP needs vendor effects support** | devices whose audio HAL lacks `DynamicsProcessing` (some Bluetooth DACs, some OEM-enhanced audio stacks) get the fallback path; truly unsupported routes the app explicitly warns you — effects are not simulated. |
| **No drag-reorder** | Playlists reorder with subtle up/down arrows. No drag-and-drop to this day. |
| **No Chromecast-style casting** | Output picker routes through Android AudioManager. BLE/Auto not in scope. |
| **No built-in code license for third-party** | All bundled licenses visible in-app and on GitHub. |
| **No signed release** | `versionName 1.0.3`, built locally per commit. |

---

## Close-up

**Audio focused** — 33 Harman-target headphone profiles + 17 EQ preset curves are computed on-device (`AudioDspMath`) — 0.5 dB steps, preamp hits as headroom (not as a flat boost), balance feels like a pan knob not a volume knob for one ear — and **soundstage** remains on while EQ/bass is bypassed.

**Respect** — Atlas refuses to rip content the artist did not license for reuse, even though `yt-dlp` *can*. That is the line not to be crossed.

**Simple** — no settings for things that don't change outcomes. Listen, pick, play.

---

## Architecture (in one line)

```
UI (Compose) ── Flow ── Room ── MediaStore/SAF ─ scanner
         │
         ▼ MediaController (Media3)
Service (Media3 + ExoPlayer + DynamicsProcessing|Equalizer)
         ▲ notification / widget / QS tile
```

- `ui/` — screens, theme, components, viewmodels (one VM per screen, manual DI)
- `di/` — `ServiceLocator` (manual), settings via DataStore, repository combos
- `data/db/` — Room, v3. v1/v2 → v3 destructive-wipe; further bumps need explicit migrations (currently `.fallbackToDestructiveMigration()` is still set — see tradeoffs)
- `playback/` — `MediaLibraryService`, session plumbing, crossfade watcher, output device routing
- `downloads/` — yt-dlp bridge (Chaquopy, pinned 2025.10.22), `DownloadManager` (queue + foreground service + persistence), `YtdlpUpdater` (PyPI-first, SHA-verified; rollback one tap)

---

## Status

| Gate | Status |
|---|---|
| `assembleDebug` | ✅ green |
| `assembleRelease` | ✅ green, R8 + lintVital |
| `testDebugUnitTest` | ✅ 60/60 |
| AVD API 35 x86_64 | ✅ home/library/search/tools/now-playing/visual |
| Real device | — not yet |

**License** — free to audit. Non-commercial convenience, not a commercial product. Bundled libs (`yt-dlp`, `Chaquopy`, `Coil`, `Media3`) keep their original licenses.

## Legal

Only download what you are authorized to download. No DRM bypass, no paywall circumvention, no ToS workaround. The license enforcement (Creative Commons check on SoundCloud) is by design — not a payment gate — and you can inspect and extend the policy yourself in `util/SoundCloudGate.kt`.
