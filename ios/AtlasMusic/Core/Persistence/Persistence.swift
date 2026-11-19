import Foundation
import SwiftData

// Atlas iOS — Builds the shared SwiftData container holding every persisted model.

enum Persistence {
    /// The Android Room database has six entities; SwiftData splits playlist membership into its own
    /// `PlaylistEntry` model, so seven model types are registered here.
    static func makeContainer() -> ModelContainer {
        let schema = Schema([
            Track.self,
            Playlist.self,
            PlaylistEntry.self,
            PlayEvent.self,
            DownloadTask.self,
            PomodoroSession.self,
        ])
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)
        do {
            return try ModelContainer(for: schema, configurations: [configuration])
        } catch {
            // Never let a corrupt/unavailable store prevent the app from launching.
            let fallback = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
            return try! ModelContainer(for: schema, configurations: [fallback])
        }
    }
}
