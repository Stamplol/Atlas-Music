// Atlas iOS — faithful port of Android DownloadsScreen: paste/search/preview, queue, history, gate and resolver.
import SwiftUI
import SwiftData
import UIKit

struct DownloadsView: View {
    @Environment(DownloadManager.self) private var downloads
    @Environment(SettingsStore.self) private var settings
    @Environment(\.modelContext) private var modelContext

    @State private var url = ""
    @State private var searchProvider = "youtube"
    @State private var previewLoading = false
    @State private var preview: DownloadPreview?
    @State private var previewError: String?
    @State private var searching = false
    @State private var searchError: String?
    @State private var searchResults: [DownloadSearchItem] = []
    @State private var format = "opus"
    @State private var consentAccepted = false
    @State private var playlistMode = false
    @State private var enqueueMessage: String?
    @State private var resolverDraft = UserDefaults.standard.string(forKey: HTTPDownloadResolver.configurationKey) ?? ""
    @State private var resolverNotice: String?

    private var onDismiss: () -> Void
    private var colors: AtlasColors { AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor) }

    init(onDismiss: @escaping () -> Void = {}) {
        self.onDismiss = onDismiss
    }

    var body: some View {
        ZStack {
            colors.background.ignoresSafeArea()
            content
        }
        .navigationTitle("Downloads")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                AtlasIconButton(systemName: "chevron.left", label: "Back", action: onDismiss)
            }
        }
        .onAppear {
            let saved = settings.downloadFormat
            if ["opus", "m4a", "best"].contains(saved) { format = saved }
            resolverDraft = UserDefaults.standard.string(forKey: HTTPDownloadResolver.configurationKey) ?? ""
            downloads.recoverInterrupted()
        }
    }

    private var content: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !downloads.isAvailable {
                    ErrorBanner(message: "The downloader engine couldn't start on this device. Everything else in Atlas Music still works offline.")
                }

                pasteCard

                if let error = searchError { ErrorBanner(message: error) }

                if !searchResults.isEmpty, !url.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, !downloads.isCompleteURL(url) {
                    searchResultsCard
                }

                if let error = previewError { ErrorBanner(message: error) }

                if let message = enqueueMessage {
                    Text(message)
                        .font(AtlasFont.body())
                        .foregroundStyle(colors.accent)
                        .padding(.horizontal, 16)
                }

                if let preview { previewCard(preview) }

                let active = downloads.tasks.filter { DownloadStateMachine.isActive($0.status) }
                let finished = downloads.tasks.filter { DownloadStateMachine.isTerminal($0.status) }

                if !active.isEmpty {
                    SectionHeader("In progress")
                    ForEach(active, id: \.url) { task in taskRow(task) }
                }

                if !finished.isEmpty {
                    SectionHeader("History", actionLabel: "Clear completed") {
                        downloads.clearCompleted()
                    }
                    ForEach(finished.prefix(50), id: \.url) { task in taskRow(task) }
                    Button("Clear all history") { downloads.clearHistory() }
                        .font(AtlasFont.caption())
                        .foregroundStyle(colors.textMuted)
                        .padding(.leading, 12)
                }

                if downloads.tasks.isEmpty {
                    EmptyState(
                        title: "No downloads yet",
                        body: "Paste a link to a video, track or playlist you're authorized to save, and it will land in your library when it's done."
                    )
                }

                resolverCard
                ytdlpCard
                legalNote
            }
            .padding(.bottom, 32)
        }
    }

    // MARK: - Paste / search

    private var pasteCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "link")
                    .foregroundStyle(colors.accent)
                Text("Paste a direct audio, YouTube or SoundCloud link")
                    .font(AtlasFont.headline())
            }
            HStack(spacing: 8) {
                providerChip("youtube", label: "YouTube")
                providerChip("soundcloud", label: "SoundCloud")
            }
            HStack {
                TextField("Link — or a song to search", text: Binding(
                    get: { url },
                    set: { newValue in
                        url = newValue
                        preview = nil
                        previewError = nil
                        enqueueMessage = nil
                        searchError = nil
                        if !searching { searchResults = [] }
                    }
                ))
                .textFieldStyle(.roundedBorder)
                .autocorrectionDisabled()
                #if os(iOS)
                .textInputAutocapitalization(.never)
                #endif
                AtlasIconButton(systemName: "doc.on.clipboard", label: "Paste from clipboard") {
                    if let pasted = UIPasteboard.general.string { url = pasted }
                }
            }
            Button {
                submitSearchOrURL()
            } label: {
                HStack {
                    if previewLoading || searching {
                        ProgressView().tint(colors.onAccent)
                        Text(previewLoading ? "Reading link…" : "Searching \(searchProvider.capitalized)…")
                    } else {
                        Text(downloads.isCompleteURL(url) ? "Analyze link" : "Search")
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(colors.accent)
            .disabled(url.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || previewLoading || searching)
        }
        .padding(16)
        .background(colors.surface, in: RoundedRectangle(cornerRadius: Radius.lg))
        .padding(.horizontal, 16)
    }

    private func providerChip(_ value: String, label: String) -> some View {
        Button {
            searchProvider = value
            searchResults = []
            searchError = nil
        } label: {
            Text(label)
                .font(AtlasFont.caption())
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(searchProvider == value ? colors.accent.opacity(0.22) : colors.surfaceElevated,
                            in: Capsule())
                .overlay(Capsule().stroke(searchProvider == value ? colors.accent : colors.border, lineWidth: 1))
                .foregroundStyle(searchProvider == value ? colors.accent : colors.textMuted)
        }
        .buttonStyle(.plain)
    }

    private func submitSearchOrURL() {
        let raw = url.trimmingCharacters(in: .whitespacesAndNewlines)
        if downloads.isCompleteURL(raw) {
            Task { await fetchPreview() }
            return
        }
        guard !raw.isEmpty else {
            searchError = "Type an artist/song, or paste a link."
            return
        }
        searching = true
        searchError = nil
        searchResults = []
        preview = nil
        Task {
            let result = await downloads.search(raw, provider: searchProvider)
            searching = false
            searchError = result.error
            searchResults = result.entries
        }
    }

    private func fetchPreview() async {
        let raw = url.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !raw.isEmpty else {
            previewError = "Paste a link first."
            return
        }
        previewLoading = true
        previewError = nil
        preview = nil
        let result = await downloads.preview(raw)
        previewLoading = false
        preview = result.ok ? result : nil
        previewError = result.error
        playlistMode = result.ok && result.isPlaylist
        enqueueMessage = nil
    }

    // MARK: - Search results

    private var searchResultsCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Search results · \(searchProvider.capitalized)")
                .font(AtlasFont.eyebrow())
                .foregroundStyle(colors.accent)
            consentRow
            ForEach(searchResults) { item in
                HStack(spacing: 10) {
                    Thumbnail(url: item.thumbnailURL)
                        .frame(width: 64, height: 44)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.title).font(AtlasFont.body()).lineLimit(2)
                        Text(item.uploader + (item.durationSec > 0 ? "  ·  \(Format.duration(Int64(item.durationSec) * 1000))" : ""))
                            .font(AtlasFont.caption())
                            .foregroundStyle(colors.textMuted)
                            .lineLimit(1)
                    }
                    Spacer()
                    AtlasIconButton(systemName: "arrow.down.circle", label: "Download") {
                        guard consentAccepted else {
                            searchError = "Please confirm you're authorized to download this content."
                            return
                        }
                        Task {
                            do {
                                try await downloads.enqueue(url: item.url, title: item.title,
                                                            uploader: item.uploader, thumbnail: item.thumbnailURL,
                                                            format: format)
                                enqueueMessage = "Queued “\(String(item.title.prefix(60)))”."
                                searchError = nil
                            } catch {
                                searchError = error.localizedDescription
                            }
                        }
                    }
                }
                Divider().background(colors.border.opacity(0.4))
            }
            Text("Tapping a download sends the track through the normal queue.")
                .font(AtlasFont.caption())
                .foregroundStyle(colors.textMuted)
        }
        .padding(14)
        .background(colors.surface, in: RoundedRectangle(cornerRadius: Radius.lg))
        .padding(.horizontal, 16)
    }

    // MARK: - Preview

    private func previewCard(_ preview: DownloadPreview) -> some View {
        let wholePlaylist = preview.isPlaylist && playlistMode
        let blockedSoundCloud = preview.license.map { !SoundCloudGate.licenseAllowsDownload($0) } ?? false
        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 14) {
                Thumbnail(url: preview.thumbnailURL)
                    .frame(width: 148, height: 84)
                VStack(alignment: .leading, spacing: 4) {
                    Text(preview.title).font(AtlasFont.headline()).lineLimit(3)
                    Text(preview.uploader).font(AtlasFont.caption()).foregroundStyle(colors.textMuted).lineLimit(1)
                    Text(preview.isPlaylist ? "PLAYLIST · \(preview.entries.count) tracks" : "AUDIO · \(Format.duration(Int64(preview.durationSec) * 1000))")
                        .font(AtlasFont.caption())
                        .foregroundStyle(colors.accent)
                }
            }
            if !preview.entries.isEmpty {
                HStack {
                    VStack(alignment: .leading) {
                        Text("Entire playlist").font(AtlasFont.body())
                        Text("\(preview.entries.count) tracks in \"\(preview.playlistTitle.isEmpty ? "playlist" : preview.playlistTitle)\"")
                            .font(AtlasFont.caption()).foregroundStyle(colors.textMuted).lineLimit(2)
                    }
                    Spacer()
                    Toggle("", isOn: $playlistMode).labelsHidden().tint(colors.accent)
                }
                ForEach(Array(preview.entries.prefix(5).enumerated()), id: \.offset) { index, title in
                    HStack(spacing: 8) {
                        Text("\(index + 1).").font(AtlasFont.caption()).foregroundStyle(colors.textMuted)
                        Text(title).font(AtlasFont.caption()).lineLimit(1)
                        Spacer()
                    }
                }
                if preview.entries.count > 5 {
                    Text("… and \(preview.entries.count - 5) more")
                        .font(AtlasFont.caption()).foregroundStyle(colors.textMuted)
                }
            }

            Text("Audio format").font(AtlasFont.body()).foregroundStyle(colors.textMuted)
            HStack(spacing: 8) {
                formatChip("opus", "Opus (WebM)")
                formatChip("m4a", "M4A (AAC)")
                formatChip("best", "Original")
            }
            Text("Formats come straight from the source — nothing is converted without an encoder.")
                .font(AtlasFont.caption()).foregroundStyle(colors.textMuted)

            if let license = preview.license {
                let allowed = SoundCloudGate.licenseAllowsDownload(license)
                Text(allowed ? "Licensed for reuse (\(SoundCloudGate.licenseLabel(license)))" : "All rights reserved — not licensed for download")
                    .font(AtlasFont.caption())
                    .foregroundStyle(allowed ? colors.accent : colors.error)
            }

            if blockedSoundCloud {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(colors.error)
                    Text("Atlas only downloads SoundCloud tracks the artist licensed for reuse (Creative Commons) or explicitly enabled. This one isn't.")
                        .font(AtlasFont.caption())
                }
                .padding(14)
                .background(colors.error.opacity(0.10), in: RoundedRectangle(cornerRadius: 12))
                Button("Not licensed for download") {}.disabled(true)
                    .frame(maxWidth: .infinity)
            } else {
                consentRow
                Button {
                    guard consentAccepted else {
                        previewError = "Please confirm you're authorized to download this content."
                        return
                    }
                    Task { await startDownload(wholePlaylist: wholePlaylist, preview: preview) }
                } label: {
                    Text(wholePlaylist ? "Download all \(preview.entries.count) tracks" : "Download audio")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(colors.accent)
                .disabled(!consentAccepted)
            }
        }
        .padding(16)
        .background(colors.surface, in: RoundedRectangle(cornerRadius: Radius.lg))
        .padding(.horizontal, 16)
    }

    private func startDownload(wholePlaylist: Bool, preview: DownloadPreview) async {
        if wholePlaylist {
            let result = await downloads.enqueuePlaylist(url: url, format: format)
            var extras: [String] = []
            if result.duplicates > 0 { extras.append("\(result.duplicates) already in your downloads") }
            if result.failed > 0 { extras.append("\(result.failed) couldn't be read") }
            let suffix = extras.isEmpty ? "" : " (\(extras.joined(separator: ", ")))"
            enqueueMessage = result.enqueued > 0
                ? "Queued \(result.enqueued) item\(result.enqueued == 1 ? "" : "s") from the playlist\(suffix)."
                : "Nothing new to queue\(suffix)."
        } else {
            do {
                try await downloads.enqueue(url: url, title: preview.title, uploader: preview.uploader,
                                            thumbnail: preview.thumbnailURL, format: format)
                url = ""
                self.preview = nil
                consentAccepted = false
                enqueueMessage = nil
            } catch {
                previewError = error.localizedDescription
            }
        }
    }

    private func formatChip(_ value: String, _ label: String) -> some View {
        Button {
            format = value
        } label: {
            Text(label).font(AtlasFont.caption())
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(format == value ? colors.accent.opacity(0.22) : colors.surfaceElevated, in: Capsule())
                .overlay(Capsule().stroke(format == value ? colors.accent : colors.border, lineWidth: 1))
                .foregroundStyle(format == value ? colors.accent : colors.textMuted)
        }
        .buttonStyle(.plain)
    }

    private var consentRow: some View {
        Button {
            consentAccepted.toggle()
        } label: {
            HStack(spacing: 10) {
                Image(systemName: consentAccepted ? "checkmark.square.fill" : "square")
                    .foregroundStyle(consentAccepted ? colors.accent : colors.textMuted)
                Text("I own this content or have permission to download it.")
                    .font(AtlasFont.caption())
                    .foregroundStyle(colors.text)
                Spacer()
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // MARK: - Task rows

    private func taskRow(_ task: DownloadTask) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 12) {
                statusIcon(task.status)
                VStack(alignment: .leading, spacing: 2) {
                    Text(task.title).font(AtlasFont.body()).lineLimit(1)
                    Text(statusText(task))
                        .font(AtlasFont.caption())
                        .foregroundStyle(task.status == .failed ? colors.error : colors.textMuted)
                        .lineLimit(2)
                }
                Spacer()
                if DownloadStateMachine.isActive(task.status) {
                    AtlasIconButton(systemName: "xmark.circle", label: "Cancel download") { downloads.cancel(task) }
                } else if DownloadStateMachine.retry(task.status) != nil {
                    AtlasIconButton(systemName: "arrow.clockwise", label: "Retry download") { downloads.retry(task) }
                }
                AtlasIconButton(systemName: "trash", label: "Remove from history") { downloads.delete(task) }
            }
            if task.status == .downloading {
                ProgressView(value: min(1, max(0, task.progress)))
                    .tint(colors.accent)
            }
            Divider().background(colors.border.opacity(0.5))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    private func statusText(_ task: DownloadTask) -> String {
        switch task.status {
        case .queued: return "Queued"
        case .fetchingInfo: return "Reading link…"
        case .downloading: return "\(Format.speed(task.speedBytesPerSec))  ·  \(Format.eta(task.etaSeconds))"
        case .completed: return "Saved to your library"
        case .failed: return task.error ?? "Failed"
        case .cancelled: return task.error ?? "Cancelled"
        }
    }

    @ViewBuilder private func statusIcon(_ status: DownloadStatus) -> some View {
        let tint: Color = status == .completed ? colors.accent : (status == .failed ? colors.error : colors.textMuted)
        let name = status == .completed ? "checkmark.circle.fill" : (status == .failed ? "exclamationmark.circle.fill" : "arrow.triangle.2.circlepath")
        Image(systemName: name).font(.system(size: 22)).foregroundStyle(tint)
    }

    // MARK: - Resolver card (a real, persisted setting — no dead buttons on iOS)

    private var resolverCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "server.rack").foregroundStyle(colors.accent)
                Text("Download resolver").font(AtlasFont.headline())
            }
            Text("iOS cannot embed Python or yt-dlp. Direct .mp3/.m4a/.ogg/.opus/.wav/.flac links download locally; YouTube and SoundCloud links use an optional server-side bridge.")
                .font(AtlasFont.caption()).foregroundStyle(colors.textMuted)
            HStack {
                TextField("https://your-bridge.example.com", text: $resolverDraft)
                    .textFieldStyle(.roundedBorder)
                    .autocorrectionDisabled()
                    #if os(iOS)
                    .textInputAutocapitalization(.never)
                    #endif
                Button("Save") {
                    let trimmed = resolverDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                    UserDefaults.standard.set(trimmed, forKey: HTTPDownloadResolver.configurationKey)
                    resolverNotice = trimmed.isEmpty ? "Resolver removed — direct audio only."
                        : "Resolver saved. Downloads will use \(trimmed)."
                }
                .buttonStyle(.bordered).tint(colors.accent)
            }
            if let notice = resolverNotice {
                Text(notice).font(AtlasFont.caption()).foregroundStyle(colors.accent)
            }
            Text("Current mode: \(downloads.resolverDescription()). The bridge implements GET /preview, /search, /playlist and /resolve.")
                .font(AtlasFont.caption()).foregroundStyle(colors.textMuted)
        }
        .padding(16)
        .background(colors.surface, in: RoundedRectangle(cornerRadius: Radius.lg))
        .padding(.horizontal, 16)
    }

    private var ytdlpCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Image(systemName: "shield.lefthalf.filled").foregroundStyle(colors.accent)
                Text("Engine status").font(AtlasFont.headline())
            }
            Text("Direct-audio transfer: available. Server resolver: \(downloads.resolverDescription()). Android's bundled yt-dlp updater has no iOS equivalent — the bridge is maintained on the server.")
                .font(AtlasFont.caption()).foregroundStyle(colors.textMuted)
        }
        .padding(16)
        .background(colors.surface, in: RoundedRectangle(cornerRadius: Radius.lg))
        .padding(.horizontal, 16)
    }

    private var legalNote: some View {
        Text("Atlas Music downloads only what you're authorized to save. Don't use it to rip content you don't own, to bypass paywalls, or to strip DRM. SoundCloud: Creative Commons-licensed tracks only — everything else is refused. The files you download stay on your device.")
            .font(AtlasFont.caption())
            .foregroundStyle(colors.textMuted)
            .padding(.horizontal, 20)
    }
}

// MARK: - Small local pieces

private struct Thumbnail: View {
    let url: String
    var body: some View {
        Group {
            if !url.isEmpty, let remote = URL(string: url) {
                AsyncImage(url: remote) { phase in
                    switch phase {
                    case .success(let image): image.resizable().scaledToFill()
                    default: Color.gray.opacity(0.25)
                    }
                }
            } else {
                Color.gray.opacity(0.25)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

private struct ErrorBanner: View {
    let message: String
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
            Text(message).font(AtlasFont.caption())
            Spacer()
        }
        .padding(12)
        .background(Color.red.opacity(0.14), in: RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal, 16)
    }
}
