// Atlas iOS — playlist picker, tag editor and playlist creator sheets used by RootView.
import SwiftUI

// MARK: - Add to playlist

struct AddToPlaylistDialog: View {
    private let track: Track?
    private let playlists: [Playlist]
    private let onAdd: (Playlist) -> Void
    private let onDismiss: () -> Void

    @Environment(\.atlasColors) private var injectedColors
    @Environment(\.colorScheme) private var scheme
    @AppStorage("atlas.theme_mode", store: .standard) private var storedTheme: ThemeMode = .dark
    @State private var query = ""

    init(track: Track?, playlists: [Playlist], onAdd: @escaping (Playlist) -> Void,
         onDismiss: @escaping () -> Void) {
        self.track = track
        self.playlists = playlists
        self.onAdd = onAdd
        self.onDismiss = onDismiss
    }

    private var colors: AtlasColors {
        atlasResolvedColors(injected: injectedColors, scheme: scheme, stored: storedTheme)
    }

    private var filtered: [Playlist] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return playlists }
        return playlists.filter { $0.name.lowercased().contains(q) }
    }

    var body: some View {
        let c = colors
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("ADD TO PLAYLIST")
                        .font(AtlasFont.eyebrow())
                        .kerning(1.4)
                        .foregroundStyle(c.accent)
                    if let track {
                        Text(track.displayTitle)
                            .font(AtlasFont.caption())
                            .foregroundStyle(c.textMuted)
                            .lineLimit(1)
                    }
                }
                Spacer()
                AtlasIconButton(systemName: "xmark", label: "Close", action: onDismiss)
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)

            TextField("Search playlists", text: $query)
                .font(AtlasFont.body())
                .foregroundStyle(c.text)
                .padding(12)
                .background(c.surfaceElevated, in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
                .padding(.horizontal, 16)
                .padding(.top, 12)

            if filtered.isEmpty {
                EmptyState(
                    title: playlists.isEmpty ? "No playlists yet" : "No matches",
                    body: playlists.isEmpty
                        ? "Create a playlist from the Playlists tab, then add tracks here."
                        : "Try a different name."
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(filtered) { playlist in
                            Button {
                                onAdd(playlist)
                                onDismiss()
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: "music.note.list")
                                        .font(.system(size: 17))
                                        .foregroundStyle(c.accent)
                                        .frame(width: 28)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(playlist.name)
                                            .font(AtlasFont.rowTitle())
                                            .foregroundStyle(c.text)
                                            .lineLimit(1)
                                        if !playlist.desc.isEmpty {
                                            Text(playlist.desc)
                                                .font(AtlasFont.caption())
                                                .foregroundStyle(c.textMuted)
                                                .lineLimit(1)
                                        }
                                    }
                                    Spacer()
                                    Image(systemName: "plus.circle")
                                        .foregroundStyle(c.textMuted)
                                }
                                .padding(.horizontal, 16)
                                .padding(.vertical, 11)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(DialogRowPressStyle())
                            .overlay(alignment: .bottom) {
                                Rectangle().fill(c.border.opacity(0.5)).frame(height: 0.5).padding(.leading, 56)
                            }
                        }
                    }
                }
                .padding(.top, 8)
            }
        }
        .background(c.surface)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }
}

// MARK: - Tag editor

struct TrackEditorDialog: View {
    private let track: Track
    private let onSave: (String, String, String, String, Int, Int, Int) -> Void
    private let onReset: () -> Void
    private let onDismiss: () -> Void

    @Environment(\.atlasColors) private var injectedColors
    @Environment(\.colorScheme) private var scheme
    @AppStorage("atlas.theme_mode", store: .standard) private var storedTheme: ThemeMode = .dark
    @State private var title: String
    @State private var artist: String
    @State private var album: String
    @State private var genre: String
    @State private var year: Int
    @State private var trackNumber: Int
    @State private var discNumber: Int

    init(track: Track, onSave: @escaping (String, String, String, String, Int, Int, Int) -> Void,
         onReset: @escaping () -> Void, onDismiss: @escaping () -> Void) {
        self.track = track
        self.onSave = onSave
        self.onReset = onReset
        self.onDismiss = onDismiss
        _title = State(initialValue: track.title)
        _artist = State(initialValue: track.artist)
        _album = State(initialValue: track.album)
        _genre = State(initialValue: track.genre)
        _year = State(initialValue: track.year)
        _trackNumber = State(initialValue: track.trackNumber)
        _discNumber = State(initialValue: track.discNumber)
    }

    private var colors: AtlasColors {
        atlasResolvedColors(injected: injectedColors, scheme: scheme, stored: storedTheme)
    }

    var body: some View {
        let c = colors
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("EDIT TAGS")
                        .font(AtlasFont.eyebrow())
                        .kerning(1.4)
                        .foregroundStyle(c.accent)
                    Text(track.displayName.isEmpty ? track.displayTitle : track.displayName)
                        .font(AtlasFont.caption())
                        .foregroundStyle(c.textMuted)
                        .lineLimit(1)
                }
                Spacer()
                AtlasIconButton(systemName: "xmark", label: "Close", action: onDismiss)
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)

            ScrollView {
                VStack(spacing: 12) {
                    AtlasField(label: "Title", text: $title, colors: c)
                    AtlasField(label: "Artist", text: $artist, colors: c)
                    AtlasField(label: "Album", text: $album, colors: c)
                    AtlasField(label: "Genre", text: $genre, colors: c)
                    HStack(spacing: 12) {
                        AtlasNumberField(label: "Year", value: $year, colors: c)
                        AtlasNumberField(label: "Track", value: $trackNumber, colors: c)
                        AtlasNumberField(label: "Disc", value: $discNumber, colors: c)
                    }
                }
                .padding(16)
            }

            HStack(spacing: 10) {
                Button {
                    onReset()
                    onDismiss()
                } label: {
                    Text("Reset")
                        .font(AtlasFont.body())
                        .foregroundStyle(c.textMuted)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(c.surfaceElevated, in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
                }
                .buttonStyle(DialogRowPressStyle())

                Button {
                    onSave(title, artist, album, genre, year, trackNumber, discNumber)
                    onDismiss()
                } label: {
                    Text("Save")
                        .font(AtlasFont.body())
                        .foregroundStyle(c.onAccent)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(c.accent, in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
                }
                .buttonStyle(DialogRowPressStyle())
            }
            .padding(16)
            .overlay(alignment: .top) {
                Rectangle().fill(c.border.opacity(0.5)).frame(height: 0.5)
            }
        }
        .background(c.surface)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }
}

private struct AtlasField: View {
    let label: String
    @Binding var text: String
    let colors: AtlasColors

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(label.uppercased())
                .font(AtlasFont.eyebrow())
                .kerning(1.1)
                .foregroundStyle(colors.textMuted)
            TextField(label, text: $text)
                .font(AtlasFont.body())
                .foregroundStyle(colors.text)
                .padding(11)
                .background(colors.surfaceElevated, in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: Radius.sm, style: .continuous)
                        .stroke(colors.border, lineWidth: 0.5)
                )
        }
    }
}

private struct AtlasNumberField: View {
    let label: String
    @Binding var value: Int
    let colors: AtlasColors

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(label.uppercased())
                .font(AtlasFont.eyebrow())
                .kerning(1.1)
                .foregroundStyle(colors.textMuted)
            TextField(label, value: $value, format: .number)
                .font(AtlasFont.mono(size: 14))
                .monospacedDigit()
                .foregroundStyle(colors.text)
                .keyboardType(.numberPad)
                .padding(11)
                .background(colors.surfaceElevated, in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: Radius.sm, style: .continuous)
                        .stroke(colors.border, lineWidth: 0.5)
                )
        }
        .frame(minWidth: 0, maxWidth: .infinity)
    }
}

// MARK: - Create playlist

struct CreatePlaylistDialog: View {
    private let onCreate: (String) -> Void
    private let onDismiss: () -> Void

    @Environment(\.atlasColors) private var injectedColors
    @Environment(\.colorScheme) private var scheme
    @AppStorage("atlas.theme_mode", store: .standard) private var storedTheme: ThemeMode = .dark
    @State private var name = ""

    init(onCreate: @escaping (String) -> Void, onDismiss: @escaping () -> Void) {
        self.onCreate = onCreate
        self.onDismiss = onDismiss
    }

    private var colors: AtlasColors {
        atlasResolvedColors(injected: injectedColors, scheme: scheme, stored: storedTheme)
    }

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        let c = colors
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("NEW PLAYLIST")
                    .font(AtlasFont.eyebrow())
                    .kerning(1.4)
                    .foregroundStyle(c.accent)
                Spacer()
                AtlasIconButton(systemName: "xmark", label: "Close", action: onDismiss)
            }

            VStack(alignment: .leading, spacing: 5) {
                Text("Name")
                    .font(AtlasFont.caption())
                    .foregroundStyle(c.textMuted)
                TextField("e.g. Night drive", text: $name)
                    .font(AtlasFont.body())
                    .foregroundStyle(c.text)
                    .padding(12)
                    .background(c.surfaceElevated, in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: Radius.sm, style: .continuous)
                            .stroke(c.border, lineWidth: 0.5)
                    )
            }

            HStack(spacing: 10) {
                Button("Cancel", action: onDismiss)
                    .font(AtlasFont.body())
                    .foregroundStyle(c.textMuted)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(c.surfaceElevated, in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
                    .buttonStyle(DialogRowPressStyle())

                Button {
                    guard !trimmedName.isEmpty else { return }
                    onCreate(trimmedName)
                    onDismiss()
                } label: {
                    Text("Create")
                        .font(AtlasFont.body())
                        .foregroundStyle(c.onAccent)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(trimmedName.isEmpty ? c.surfaceHighest : c.accent, in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
                }
                .buttonStyle(DialogRowPressStyle())
                .disabled(trimmedName.isEmpty)
            }
        }
        .padding(16)
        .background(c.surface)
        .frame(maxWidth: .infinity, alignment: .top)
    }
}

private struct DialogRowPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .opacity(configuration.isPressed ? 0.72 : 1)
            .animation(AtlasMotion.quick, value: configuration.isPressed)
    }
}
