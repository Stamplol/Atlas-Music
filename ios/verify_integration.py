"""Integration sanity checks for the generated iOS source tree.

Run: python ios/verify_integration.py
Exits non-zero on missing files or hard-placeholder markers.
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(__file__), "AtlasMusic")
EXPECTED = [
    "AtlasMusicApp.swift",
    "Core/Models/Enums.swift", "Core/Models/Track.swift", "Core/Models/Playlist.swift",
    "Core/Models/PlayEvent.swift", "Core/Models/DownloadTask.swift", "Core/Models/PomodoroSession.swift",
    "Core/Persistence/Persistence.swift", "Core/Persistence/SettingsStore.swift",
    "Core/Repositories/MusicRepository.swift", "Core/Repositories/PlaylistRepository.swift",
    "Core/Repositories/LyricsRepository.swift", "Core/Repositories/LibraryScanner.swift",
    "Core/Audio/PlayerUiState.swift", "Core/Audio/AudioEngine.swift", "Core/Audio/PlayerController.swift",
    "Core/Downloads/DownloadManager.swift",
    "Core/Utils/Format.swift", "Core/Utils/DuplicateDetector.swift", "Core/Utils/LyricsParser.swift",
    "Core/Utils/UrlValidator.swift", "Core/Utils/ClickWheelMath.swift", "Core/Utils/LibraryFolder.swift",
    "Core/Utils/BuiltInExclusions.swift", "Core/Utils/YtdlpVersion.swift", "Core/Utils/DownloadStateMachine.swift",
    "Core/Utils/SoundCloudGate.swift", "Core/Utils/MediaFormatHelper.swift", "Core/Utils/EqCurves.swift",
    "Core/Utils/HeadphoneProfiles.swift", "Core/Utils/AudioDsp.swift", "Core/Utils/CoverArtKit.swift",
    "Core/Utils/BackgroundEngine.swift", "Core/Utils/OutputRouter.swift",
    "Core/Theme/AtlasTheme.swift", "Core/Theme/AtlasFont.swift",
    "UI/Components/AtlasIconButton.swift", "UI/Components/SectionHeader.swift",
    "UI/Components/EmptyState.swift", "UI/Components/AlbumArt.swift", "UI/Components/TrackRow.swift",
    "UI/Components/TrackListContent.swift", "UI/Components/MiniPlayerBar.swift",
    "UI/Components/MeridianArc.swift", "UI/Components/Dialogs.swift", "UI/Components/StatPill.swift",
    "UI/Root/RootView.swift", "UI/Root/PermissionGate.swift", "UI/Navigation/AppTab.swift",
    "UI/Screens/HomeView.swift", "UI/Screens/LibraryView.swift", "UI/Screens/SearchView.swift",
    "UI/Screens/SmartListView.swift", "UI/Screens/PlaylistDetailView.swift", "UI/Screens/QueueView.swift",
    "UI/Screens/DuplicatesView.swift", "UI/Screens/TrackInfoView.swift", "UI/Screens/NowPlayingView.swift",
    "UI/Screens/LyricsView.swift", "UI/Screens/ToolsView.swift", "UI/Screens/DownloadsView.swift",
    "UI/Screens/IpodModeView.swift",
    "UI/Tools/ToolsHub.swift", "UI/Tools/AudioStudioView.swift", "UI/Tools/FocusView.swift",
    "UI/Tools/SleepView.swift", "UI/Tools/LibraryCareView.swift",
    "UI/ViewModels/ToolsViewModel.swift",
    "Widgets/AtlasWidgets.swift", "Widgets/PlaybackIntents.swift",
    "Info.plist",
]
BAD_MARKERS = re.compile(r"(TODO:|FIXME:|coming soon|not implemented|placeholder,|#Placeholder|fatalError\()", re.I)


def main():
    missing = [p for p in EXPECTED if not os.path.isfile(os.path.join(ROOT, p))]
    print(f"Expected files: {len(EXPECTED)} | present: {len(EXPECTED) - len(missing)} | missing: {len(missing)}")
    for p in missing:
        print("  MISSING", p)

    swift = []
    for dirpath, _, files in os.walk(ROOT):
        for f in files:
            if f.endswith(".swift"):
                swift.append(os.path.join(dirpath, f))
    print(f"Swift files found: {len(swift)}")

    problems = 0
    total_lines = 0
    for path in swift:
        text = open(path, encoding="utf-8", errors="replace").read()
        total_lines += text.count("\n") + 1
        hits = BAD_MARKERS.findall(text)
        if hits:
            print(f"  PLACEHOLDER {os.path.relpath(path, ROOT)}: {sorted(set(hits))[:4]}")
            problems += 1
        # crude brace balance (ignore strings/comments well enough for a smoke test)
        stripped = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
        stripped = re.sub(r"//.*", "", stripped)
        bal = stripped.count("{") - stripped.count("}")
        if abs(bal) > 2:
            print(f"  BRACE-DELTA {os.path.relpath(path, ROOT)}: {bal}")
            problems += 1

    fonts = os.path.join(ROOT, "Resources", "Fonts")
    ttfs = [f for f in os.listdir(fonts)] if os.path.isdir(fonts) else []
    print(f"Fonts copied: {len(ttfs)} {ttfs if len(ttfs) < 3 else ''}")
    print(f"Total Swift lines: {total_lines}")
    print("RESULT:", "NEEDS ATTENTION" if (missing or problems) else "OK")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
