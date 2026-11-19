// Atlas iOS — Focus lab: pomodoro timer, daily roll-up, weekly bars and session ledger.
import SwiftUI
import Foundation

/// Pomodoro workspace backed by ToolsViewModel's live timer and SwiftData session ledger.
struct FocusView: View {
    @Environment(ToolsViewModel.self) private var toolsVM
    @Environment(SettingsStore.self) private var settings
    @Environment(\.atlasColors) private var injected

    @State private var showDurations = false

    private var colors: AtlasColors {
        injected ?? AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    private var displayMs: Int64 {
        toolsVM.totalMs > 0
            ? toolsVM.remainingMs
            : Int64(max(1, settings.pomodoroFocusMinutes)) * 60_000
    }

    private var fraction: Double {
        guard toolsVM.totalMs > 0 else { return 0 }
        return min(max(Double(toolsVM.remainingMs) / Double(toolsVM.totalMs), 0), 1)
    }

    private var stateWord: String {
        if toolsVM.running { return "REMAINING" }
        if toolsVM.remainingMs > 0 && toolsVM.totalMs > 0 { return "PAUSED" }
        return "READY"
    }

    var body: some View {
        let c = colors
        ScrollView(showsIndicators: false) {
            VStack(spacing: 12) {
                focusCard(c)
                ledgerCard(c)
            }
            .padding(16)
        }
        .sheet(isPresented: $showDurations) { durationSheet(c) }
    }

    private func focusCard(_ c: AtlasColors) -> some View {
        ToolCard {
            StudioHeader(toolsVM.pomodoroMode == "BREAK" ? "FOCUS LAB · BREAK" : "FOCUS LAB",
                         trailing: "Today \(toolsVM.focusMinutesToday)m · \(toolsVM.sessionsToday) done")

            HStack(alignment: .center, spacing: 14) {
                FocusRing(size: 140, fraction: fraction, mode: toolsVM.pomodoroMode,
                          displayMs: displayMs, stateWord: stateWord)

                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 6) {
                        Image(systemName: "flame.fill").foregroundStyle(c.accent)
                        Text(toolsVM.streakDays > 0 ? "\(toolsVM.streakDays)-day streak" : "Start your streak")
                            .font(AtlasFont.rowTitle())
                            .foregroundStyle(c.text)
                    }
                    WrapChips {
                        ForEach([15, 25, 50], id: \.self) { minutes in
                            AtlasFilterChip("\(minutes)m",
                                            selected: settings.pomodoroFocusMinutes == minutes) {
                                toolsVM.setFocusMinutes(minutes)
                            }
                        }
                        AtlasFilterChip("Adjust") { showDurations = true }
                    }
                    HStack(spacing: 8) {
                        Text("Duck music to 35%")
                            .font(AtlasFont.caption())
                            .foregroundStyle(c.textMuted)
                        Spacer(minLength: 4)
                        Toggle("", isOn: Binding(
                            get: { settings.pomodoroSyncPlayback },
                            set: { toolsVM.setSyncPlayback($0) }
                        )).labelsHidden().tint(c.accent)
                    }
                }
            }
            .padding(.top, 10)

            HStack(spacing: 8) {
                Button {
                    if toolsVM.running {
                        toolsVM.pausePomodoro()
                    } else if toolsVM.remainingMs > 0 && toolsVM.totalMs > 0 {
                        toolsVM.resumePomodoro()
                    } else {
                        toolsVM.startPomodoro()
                    }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: toolsVM.running ? "pause.fill" : "play.fill")
                        Text(toolsVM.running ? "Pause"
                             : (toolsVM.remainingMs > 0 && toolsVM.totalMs > 0 ? "Resume" : "Start"))
                    }
                    .font(AtlasFont.body())
                    .foregroundStyle(c.onAccent)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(c.accent, in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
                }
                .buttonStyle(ToolTilePressStyle())

                Button("Reset") { toolsVM.resetPomodoro() }
                    .font(AtlasFont.label())
                    .foregroundStyle(c.textMuted)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 14)
                    .background(c.surfaceElevated, in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
            }
            .padding(.top, 12)

            HStack(spacing: 8) {
                Button("Switch to \(toolsVM.pomodoroMode == "FOCUS" ? "Break" : "Focus")") {
                    toolsVM.switchMode()
                }
                .font(AtlasFont.label())
                .foregroundStyle(c.accent)
                Spacer(minLength: 4)
                if toolsVM.lastEvent.hasPrefix("Focus") || toolsVM.lastEvent.hasPrefix("Break") {
                    Text(toolsVM.lastEvent)
                        .font(AtlasFont.caption())
                        .foregroundStyle(c.accent)
                        .lineLimit(1)
                }
            }
            .padding(.top, 6)

            Divider().background(c.border.opacity(0.5)).padding(.vertical, 8)
            StudioHeader("THIS WEEK")
            WeekBars(days: lastSevenDays(toolsVM.sessions), colors: c)
                .padding(.top, 4)
        }
    }

    private func ledgerCard(_ c: AtlasColors) -> some View {
        ToolCard {
            StudioHeader("LEDGER", trailing: "last sessions")
            let ledger = Array(toolsVM.sessions.prefix(6))
            if ledger.isEmpty {
                Text("Nothing logged yet. Finish a focus block and it lands here.")
                    .font(AtlasFont.small())
                    .foregroundStyle(c.textMuted)
                    .padding(.top, 4)
            } else {
                VStack(spacing: 6) {
                    ForEach(ledger, id: \.startedAt) { session in
                        HStack(spacing: 8) {
                            Circle()
                                .fill(session.kind == "FOCUS" ? c.accent : c.textMuted)
                                .frame(width: 7, height: 7)
                            Text("\(session.kind == "FOCUS" ? "Focus" : "Break") · \(session.durationMinutes) min · \(ledgerDate(session.startedAt))")
                                .font(AtlasFont.caption())
                                .foregroundStyle(c.textMuted)
                                .lineLimit(1)
                            Spacer(minLength: 0)
                        }
                    }
                }
                .padding(.top, 4)
            }
        }
    }

    private func ledgerDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE HH:mm"
        return formatter.string(from: date)
    }

    private func durationSheet(_ c: AtlasColors) -> some View {
        NavigationStack {
            VStack(spacing: 18) {
                Text("Durations").font(AtlasFont.title()).foregroundStyle(c.text)
                    .frame(maxWidth: .infinity, alignment: .leading)

                VStack(alignment: .leading, spacing: 6) {
                    Text("Focus: \(settings.pomodoroFocusMinutes) min")
                        .font(AtlasFont.small()).foregroundStyle(c.text)
                    Slider(value: Binding(
                        get: { Float(settings.pomodoroFocusMinutes) },
                        set: { toolsVM.setFocusMinutes(Int($0.rounded())) }
                    ), in: 5...180).tint(c.accent)
                }
                VStack(alignment: .leading, spacing: 6) {
                    Text("Break: \(settings.pomodoroBreakMinutes) min")
                        .font(AtlasFont.small()).foregroundStyle(c.text)
                    Slider(value: Binding(
                        get: { Float(settings.pomodoroBreakMinutes) },
                        set: { toolsVM.setBreakMinutes(Int($0.rounded())) }
                    ), in: 1...60).tint(c.accent)
                }
                Spacer(minLength: 0)
            }
            .padding(20)
            .background(c.background)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { showDurations = false }.foregroundStyle(c.accent)
                }
            }
        }
        .presentationDetents([.medium])
    }
}

/// Compact horizontally scrollable chip row; works on every width without overflowing.
private struct WrapChips<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) { content }
        }
    }
}

/// One bar per day for the last seven days; today is gold.
private struct WeekBars: View {
    let days: [(String, Int)]
    let colors: AtlasColors

    var body: some View {
        let maxMinutes = max(days.map { $0.1 }.max() ?? 0, 1)
        HStack(alignment: .bottom, spacing: 6) {
            ForEach(days.indices, id: \.self) { index in
                let day = days[index]
                VStack(spacing: 3) {
                    if day.1 > 0 {
                        Text("\(day.1)")
                            .font(AtlasFont.mono(size: 10))
                            .foregroundStyle(colors.accent)
                    }
                    GeometryReader { geo in
                        let height = max(CGFloat(day.1) / CGFloat(maxMinutes) * geo.size.height, 2)
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .fill(day.1 > 0 ? colors.accent : colors.textMuted.opacity(0.15))
                            .frame(width: geo.size.width * 0.7, height: height)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    }
                    .frame(height: 56)
                    Text(day.0)
                        .font(AtlasFont.mono(size: 9))
                        .foregroundStyle(colors.textMuted)
                }
                .frame(maxWidth: .infinity)
                .opacity(index == days.count - 1 ? 1 : 0.85)
            }
        }
    }
}

/// Rolls completed focus sessions into the trailing seven calendar days.
private func lastSevenDays(_ sessions: [PomodoroSession]) -> [(String, Int)] {
    let calendar = Calendar.current
    let today = calendar.startOfDay(for: Date())
    let formatter = DateFormatter()
    formatter.dateFormat = "EEE"
    let days = (0..<7).compactMap { offset -> (Date, String)? in
        guard let date = calendar.date(byAdding: .day, value: -offset, to: today) else { return nil }
        return (date, formatter.string(from: date))
    }
    return days.reversed().map { dayStart, label in
        let minutes = sessions
            .filter { $0.kind == "FOCUS" && $0.completed && calendar.isDate($0.startedAt, inSameDayAs: dayStart) }
            .reduce(0) { $0 + $1.durationMinutes }
        return (label, minutes)
    }
}
