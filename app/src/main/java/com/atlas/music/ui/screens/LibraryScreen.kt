package com.atlas.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlas.music.data.db.PlaylistEntity
import com.atlas.music.data.db.SmartPlaylist
import com.atlas.music.ui.components.AlbumArt
import com.atlas.music.ui.components.TrackListContent
import com.atlas.music.ui.viewmodel.LibraryViewModel
import com.atlas.music.util.SortOption
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    tracks: List<com.atlas.music.data.db.TrackEntity>,
    sort: SortOption,
    onSetSort: (SortOption) -> Unit,
    onPlay: (com.atlas.music.data.db.TrackEntity, List<com.atlas.music.data.db.TrackEntity>, Int) -> Unit,
    onToggleFavorite: (com.atlas.music.data.db.TrackEntity) -> Unit,
    onPlayNext: (com.atlas.music.data.db.TrackEntity) -> Unit,
    onAddToQueue: (com.atlas.music.data.db.TrackEntity) -> Unit,
    onAddToPlaylist: (com.atlas.music.data.db.TrackEntity) -> Unit,
    onShowInfo: (com.atlas.music.data.db.TrackEntity) -> Unit,
    onEditTags: (com.atlas.music.data.db.TrackEntity) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSmart: (SmartPlaylist) -> Unit,
    onOpenDuplicates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(0) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Library", fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold) },
            modifier = Modifier.statusBarsPadding(),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            actions = {
                Box {
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = "Sort library")
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        SortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label, color = if (option == sort) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                onClick = { sortMenuOpen = false; onSetSort(option) },
                            )
                        }
                    }
                }
            },
        )
        val tabs = listOf("Songs", "Albums", "Artists", "Genres", "Folders", "Playlists")
        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.surface,
            edgePadding = 0.dp,
        ) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    modifier = Modifier.width(88.dp),
                    text = {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                        )
                    },
                )
            }
        }

        when (tab) {
            0 -> TrackListContent(
                tracks = tracks,
                onPlay = { track, _ -> onPlay(track, tracks, tracks.indexOf(track)) },
                onToggleFavorite = onToggleFavorite,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onAddToPlaylist = onAddToPlaylist,
                onShowInfo = onShowInfo,
            onEditTags = onEditTags,
            )
            1 -> CountList(
                items = state.albumCounts.map { it.name to it.count },
                emptyTitle = "No albums found",
                emptyBody = "Albums appear here after you scan your library.",
                onClick = onOpenAlbum,
                rowKey = { "album-$it" },
            )
            2 -> CountList(
                items = state.artistCounts.map { it.name to it.count },
                emptyTitle = "No artists found",
                emptyBody = "Artists appear here after you scan your library.",
                onClick = onOpenArtist,
                rowKey = { "artist-$it" },
            )
            3 -> CountList(
                items = state.genreCounts.map { it.name to it.count },
                emptyTitle = "No genres found",
                emptyBody = "Genres appear here after you scan your library.",
                onClick = onOpenGenre,
                rowKey = { "genre-$it" },
            )
            4 -> FoldersList(
                folders = state.folders,
                onOpenFolder = onOpenFolder,
            )
            5 -> PlaylistsList(
                playlists = state.playlists,
                onCreate = { showCreatePlaylist = true },
                onOpenPlaylist = onOpenPlaylist,
                onOpenSmart = onOpenSmart,
                onOpenDuplicates = onOpenDuplicates,
            )
        }
    }

    if (showCreatePlaylist) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylist = false },
            onCreate = { name ->
                showCreatePlaylist = false
                viewModel.createPlaylist(name) { id -> onOpenPlaylist(id) }
            },
        )
    }
}

@Composable
private fun CountList(
    items: List<Pair<String, Int>>,
    emptyTitle: String,
    emptyBody: String,
    onClick: (String) -> Unit,
    rowKey: (String) -> String,
) {
    if (items.isEmpty()) {
        com.atlas.music.ui.components.EmptyState(emptyTitle, emptyBody)
        return
    }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp)) {
        items(items, key = { rowKey(it.first) }) { (name, count) ->
            CollectionRow(title = name, subtitle = "$count ${if (count == 1) "track" else "tracks"}") { onClick(name) }
        }
    }
}

@Composable
private fun FoldersList(
    folders: List<com.atlas.music.data.db.FolderCount>,
    onOpenFolder: (String) -> Unit,
) {
    if (folders.isEmpty()) {
        com.atlas.music.ui.components.EmptyState("No folders indexed", "Folders appear when your audio files carry a folder path.")
        return
    }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp)) {
        items(folders, key = { "folder-${it.folder}" }) { folder ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenFolder(folder.folder) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(folder.folder, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${folder.count} ${if (folder.count == 1) "track" else "tracks"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PlaylistsList(
    playlists: List<PlaylistEntity>,
    onCreate: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSmart: (SmartPlaylist) -> Unit,
    onOpenDuplicates: () -> Unit,
) {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Smart playlists", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onCreate) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("New playlist")
                }
            }
        }
        items(SmartPlaylist.entries, key = { "smart-${it.name}" }) { smart ->
            CollectionRow(title = smart.label, subtitle = "Updates itself", icon = Icons.Filled.QueueMusic) { onOpenSmart(smart) }
        }
        item {
            Text(
                "Your playlists",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        items(playlists, key = { "playlist-${it.id}" }) { playlist ->
            CollectionRow(title = playlist.name, subtitle = playlist.description.ifBlank { "Playlist" }, icon = Icons.Filled.QueueMusic) { onOpenPlaylist(playlist.id) }
        }
        item {
            CollectionRow(
                title = "Duplicates",
                subtitle = "Find the same song saved twice",
                icon = Icons.Filled.ContentCopy,
                onClick = onOpenDuplicates,
            )
        }
    }
}

@Composable
private fun CollectionRow(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Filled.PlaylistAdd,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

