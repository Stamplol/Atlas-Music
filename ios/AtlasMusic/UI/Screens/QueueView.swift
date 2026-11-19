// Atlas iOS — Up-next queue editor, faithful to Android QueueScreen.
import SwiftUI

struct QueueView: View {
    @Environment(PlayerController.self) private var player
    @Environment(PlaylistRepository.self) private var playlists
    @Environment(SettingsStore.self) private var settings
    @Environment(TrackDialogCoordinator.self) private var dialogs

    let onDismiss: () -> Void

    @State private var tracks: [Track] = []
    @State private var showSave = false
    @State private var playlistName = ""

    init(onDismiss: @escaping () -> Void) { self.onDismiss = onDismiss }

    private var colors: AtlasColors {
        AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    private var totalMs: Int64 { tracks.reduce(Int64(0)) { $0 + $1.durationMs } }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            if tracks.isEmpty {
                EmptyState(title: "Queue is empty", body: "Play something and it will show up here.")
            } else {
                Text("Total: \(Format.totalDuration(totalMs))")
                    .font(AtlasFont.caption())
                    .foregroundStyle(colors.textMuted)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 4)
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(Array(tracks.enumerated()), id: \.element.uri) { index, track in
                            queueRow(track, index: index)
                        }
                    }
                    .padding(.bottom, 120)
                }
            }
        }
        .background(colors.background.ignoresSafeArea())
        .onAppear { reload() }
        .onChange(of: player.queueRevision) { _, _ in reload() }
        .alert("Save queue as playlist", isPresented: $showSave) {
            TextField("Playlist name", text: $playlistName)
            Button("Save") { saveQueue() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Every track in the current order becomes a playlist you can play, renumber, and play differently forever.")
        }
    }

    private var header: some View {
        HStack {
            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(colors.accent)
            }
            .accessibilityLabel("Close queue")
            Text("Queue · \(tracks.count) \(tracks.count == 1 ? "track" : "tracks")")
                .font(AtlasFont.headline())
                .foregroundStyle(colors.text)
            Spacer()
            if !tracks.isEmpty {
                Button {
                    playlistName = "Queue · \(tracks.count) tracks"
                    showSave = true
                } label: {
                    Image(systemName: "square.and.arrow.down.on.square")
                        .foregroundStyle(colors.accent)
                }
                .accessibilityLabel("Save queue")
                Button {
                    player.clearQueue()
                    reload()
                } label: {
                    Image(systemName: "trash")
                        .foregroundStyle(colors.accent)
                }
                .accessibilityLabel("Clear queue")
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private func queueRow(_ track: Track, index: Int) -> some View {
        let isCurrent = track.uri == player.state.currentTrackURI
        return HStack(spacing: 10) {
            Text(String(format: "%02d", index + 1))
                .font(AtlasFont.mono(size: 12))
                .foregroundStyle(isCurrent ? colors.accent : colors.textMuted)
                .frame(width: 30)
            AlbumArt(track: track, size: 44, corner: Radius.sm, showStrip: false)
            VStack(alignment: .leading) {
                Text(track.displayTitle)
                    .font(AtlasFont.body())
                    .foregroundStyle(isCurrent ? colors.accent : colors.text)
                    .lineLimit(1)
                Text("\(track.displayArtist)  ·  \(Format.duration(track.durationMs))")
                    .font(AtlasFont.caption())
                    .foregroundStyle(colors.textMuted)
                    .lineLimit(1)
            }
            Spacer()
            VStack(spacing: 2) {
                Button {
                    player.moveQueueItem(from: index, to: max(index - 1, 0))
                    reload()
                } label: {
                    Image(systemName: "arrow.up")
                        .font(.caption)
                }
                .disabled(index == 0)
                .accessibilityLabel("Move up")
                Button {
                    player.moveQueueItem(from: index, to: min(index + 1, tracks.count - 1))
                    reload()
                } label: {
                    Image(systemName: "arrow.down")
                        .font(.caption)
                }
                .disabled(index == tracks.count - 1)
                .accessibilityLabel("Move down")
            }
            .buttonStyle(.borderless)
            .foregroundStyle(colors.accent)
            Button {
                player.removeQueueItem(at: index)
                reload()
            } label: {
                Image(systemName: "xmark.circle")
                    .foregroundStyle(colors.textMuted)
            }
            .accessibilityLabel("Remove from queue")
            .buttonStyle(.borderless)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .contentShape(Rectangle())
        .onTapGesture {
            player.playTracks(tracks, startAt: index)
            reload()
        }
    }

    private func reload() {
        tracks = player.queueTracks()
    }

    private func saveQueue() {
        let name = playlistName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, !tracks.isEmpty else {
            dialogs.toast("Couldn't save the playlist — empty name or empty queue.")
            return
        }
        if let saved = playlists.saveQueueAsPlaylist(name: name, tracks: tracks) {
            dialogs.toast("Saved as \"\(saved.name)\" playlist")
        } else {
            dialogs.toast("Couldn't save the playlist — empty name or empty queue.")
        }
    }
}
