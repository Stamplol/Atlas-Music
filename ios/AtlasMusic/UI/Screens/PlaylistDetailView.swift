// Atlas iOS — playlist inspector with reorder, rename, delete and track picker; faithful to Android PlaylistDetailScreen.
import SwiftUI

struct PlaylistDetailView: View {
    @Environment(PlaylistRepository.self) private var playlists
    @Environment(MusicRepository.self) private var music
    @Environment(PlayerController.self) private var player
    @Environment(SettingsStore.self) private var settings
    @Environment(TrackDialogCoordinator.self) private var dialogs
    @Environment(\.dismiss) private var dismiss

    let playlist: Playlist
    let onPlayAll: ([Track], Int) -> Void

    @State private var tracks: [Track] = []
    @State private var renaming = false
    @State private var renameInput = ""
    @State private var confirmDelete = false
    @State private var showPicker = false
    @State private var selectedURIs: Set<String> = []

    init(playlist: Playlist, onPlayAll: @escaping ([Track], Int) -> Void) {
        self.playlist = playlist
        self.onPlayAll = onPlayAll
        self._renameInput = State(initialValue: playlist.name)
    }

    private var colors: AtlasColors {
        AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    private var totalMs: Int64 { tracks.reduce(Int64(0)) { $0 + $1.durationMs } }

    private var candidates: [Track] {
        let existing = Set(tracks.map { $0.uri })
        return music.allTracks(sort: .title).filter { !existing.contains($0.uri) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                Button { dismiss() } label: {
                    Image(systemName: "chevron.left")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(colors.accent)
                }
                .accessibilityLabel("Back")
                VStack(alignment: .leading) {
                    Text(playlist.name)
                        .font(AtlasFont.headline())
                        .foregroundStyle(colors.text)
                        .lineLimit(1)
                    Text("\(tracks.count) tracks  ·  \(Format.totalDuration(totalMs))")
                        .font(AtlasFont.caption())
                        .foregroundStyle(colors.textMuted)
                }
                Spacer()
                toolButton("plus", label: "Add tracks") { showPicker = true }
                toolButton("pencil", label: "Rename playlist") {
                    renameInput = playlist.name
                    renaming = true
                }
                toolButton("trash", label: "Delete playlist") { confirmDelete = true }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)

            HStack {
                Button("Play all") { onPlayAll(tracks, 0) }
                    .font(AtlasFont.body().weight(.semibold))
                    .foregroundStyle(colors.onAccent)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .background(colors.accent)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
                    .disabled(tracks.isEmpty)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 6)

            if tracks.isEmpty {
                EmptyState(
                    title: "Nothing in this playlist yet",
                    body: "Tap + above or use “Add to playlist” on any track."
                )
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(Array(tracks.enumerated()), id: \.element.uri) { index, track in
                            playlistRow(track, index: index)
                        }
                    }
                    .padding(.bottom, 120)
                }
            }
        }
        .background(colors.background.ignoresSafeArea())
        .onAppear { reload() }
        .alert("Rename playlist", isPresented: $renaming) {
            TextField("Playlist name", text: $renameInput)
            Button("Rename") { rename() }
            Button("Cancel", role: .cancel) {}
        }
        .alert("Delete playlist?", isPresented: $confirmDelete) {
            Button("Delete", role: .destructive) {
                playlists.delete(playlist)
                dismiss()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("\"\(playlist.name)\" will be deleted. The audio files stay on your device.")
        }
        .sheet(isPresented: $showPicker) { trackPicker }
    }

    private func toolButton(_ icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).foregroundStyle(colors.accent)
        }
        .accessibilityLabel(label)
        .buttonStyle(.borderless)
    }

    private func playlistRow(_ track: Track, index: Int) -> some View {
        HStack(spacing: 10) {
            Text(String(format: "%02d", index + 1))
                .font(AtlasFont.mono(size: 12))
                .foregroundStyle(colors.textMuted)
            AlbumArt(track: track, size: 44, corner: Radius.sm, showStrip: false)
            VStack(alignment: .leading) {
                Text(track.displayTitle)
                    .font(AtlasFont.body())
                    .foregroundStyle(colors.text)
                    .lineLimit(1)
                Text("\(track.displayArtist)  ·  \(Format.duration(track.durationMs))")
                    .font(AtlasFont.caption())
                    .foregroundStyle(colors.textMuted)
                    .lineLimit(1)
            }
            Spacer()
            VStack(spacing: 2) {
                Button {
                    playlists.move(track, in: playlist, to: max(index - 1, 0))
                    reload()
                } label: {
                    Image(systemName: "arrow.up").font(.caption)
                }
                .disabled(index == 0)
                Button {
                    playlists.move(track, in: playlist, to: min(index + 1, tracks.count - 1))
                    reload()
                } label: {
                    Image(systemName: "arrow.down").font(.caption)
                }
                .disabled(index == tracks.count - 1)
            }
            .buttonStyle(.borderless)
            .foregroundStyle(colors.accent)
            Button {
                dialogs.editTags(track)
            } label: {
                Image(systemName: "pencil").font(.caption)
            }
            .accessibilityLabel("Edit tags")
            .buttonStyle(.borderless)
            Button {
                playlists.remove(track, from: playlist)
                reload()
            } label: {
                Image(systemName: "trash").font(.caption)
            }
            .accessibilityLabel("Remove from playlist")
            .buttonStyle(.borderless)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .contentShape(Rectangle())
        .onTapGesture { onPlayAll(tracks, index) }
    }

    private var trackPicker: some View {
        let list = candidates
        return NavigationStack {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Button("Cancel") { showPicker = false }.foregroundStyle(colors.accent)
                    Spacer()
                    Text("Add tracks").font(AtlasFont.headline())
                    Spacer()
                    Button("Add \(selectedURIs.count)") {
                        let chosen = list.filter { selectedURIs.contains($0.uri) }
                        playlists.addTracks(chosen, to: playlist)
                        showPicker = false
                        reload()
                    }
                    .disabled(selectedURIs.isEmpty)
                    .foregroundStyle(colors.accent)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

                if list.isEmpty {
                    EmptyState(
                        title: "Nothing left to add",
                        body: "Everything in your library is already in this playlist. Nice."
                    )
                } else {
                    ScrollView {
                        LazyVStack {
                            ForEach(list, id: \.uri) { track in
                                Button {
                                    if selectedURIs.contains(track.uri) {
                                        selectedURIs.remove(track.uri)
                                    } else {
                                        selectedURIs.insert(track.uri)
                                    }
                                } label: {
                                    HStack {
                                        Image(systemName: selectedURIs.contains(track.uri) ? "checkmark.square.fill" : "square")
                                            .foregroundStyle(colors.accent)
                                        VStack(alignment: .leading) {
                                            Text(track.displayTitle)
                                                .font(AtlasFont.body())
                                                .foregroundStyle(colors.text)
                                                .lineLimit(1)
                                            Text("\(track.displayArtist) · \(track.displayAlbum)")
                                                .font(AtlasFont.caption())
                                                .foregroundStyle(colors.textMuted)
                                                .lineLimit(1)
                                        }
                                        Spacer()
                                    }
                                    .padding(.vertical, 5)
                                    .padding(.horizontal, 16)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            }
            .background(colors.background.ignoresSafeArea())
        }
    }

    private func reload() { tracks = playlists.tracks(in: playlist) }

    private func rename() {
        let name = renameInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }
        playlists.rename(playlist, name: name, description: nil)
    }
}
