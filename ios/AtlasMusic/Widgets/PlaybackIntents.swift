// Atlas iOS — App Intents used by WidgetKit buttons and system shortcuts.
import AppIntents
import Foundation

/// Intents write a command into the shared App Group suite. The running app polls that key in
/// AtlasMusicApp.mirrorToWidgets and executes the real PlayerController action, so the widget
/// never needs access to the app's SwiftData container or audio engine.
private func send(_ command: String) {
    AtlasWidgetState.enqueueCommand(command)
}

struct TogglePlaybackIntent: AppIntent {
    static let title: LocalizedStringResource = "Toggle Playback"
    static let description = IntentDescription("Plays or pauses the current Atlas Music track.")

    func perform() async throws -> some IntentResult {
        send("toggle")
        return .result()
    }
}

struct NextTrackIntent: AppIntent {
    static let title: LocalizedStringResource = "Next Track"
    static let description = IntentDescription("Skips to the next track in Atlas Music.")

    func perform() async throws -> some IntentResult {
        send("next")
        return .result()
    }
}

struct StartSleepTimerIntent: AppIntent {
    static let title: LocalizedStringResource = "Start Sleep Timer"
    static let description = IntentDescription("Starts a 30 minute sleep timer in Atlas Music.")

    func perform() async throws -> some IntentResult {
        send("sleep30")
        return .result()
    }
}

struct PreviousTrackIntent: AppIntent {
    static let title: LocalizedStringResource = "Previous Track"
    static let description = IntentDescription("Returns to the previous track in Atlas Music.")

    func perform() async throws -> some IntentResult {
        send("previous")
        return .result()
    }
}

struct WidgetShuffleIntent: AppIntent {
    static let title: LocalizedStringResource = "Shuffle Atlas Music"
    static let description = IntentDescription("Toggles shuffle for the current Atlas Music queue.")

    func perform() async throws -> some IntentResult {
        send("shuffle")
        return .result()
    }
}
