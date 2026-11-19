import Foundation
import SwiftData

// Atlas iOS — Completed focus/break session ledger for streaks and totals (mirrors Android PomodoroSessionEntity).

@Model
final class PomodoroSession {
    var startedAt: Date
    var endedAt: Date
    var durationMinutes: Int
    var kind: String
    var completed: Bool

    init(
        startedAt: Date = Date(),
        endedAt: Date = Date(),
        durationMinutes: Int = 0,
        kind: String = "FOCUS",
        completed: Bool = true
    ) {
        self.startedAt = startedAt
        self.endedAt = endedAt
        self.durationMinutes = durationMinutes
        self.kind = kind
        self.completed = completed
    }
}
