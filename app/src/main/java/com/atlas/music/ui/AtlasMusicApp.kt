@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.atlas.music.ui

import com.atlas.music.ui.components.AtlasIconButton

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import com.atlas.music.util.isLandscape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.atlas.music.data.db.SmartPlaylist
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.di.ServiceLocator
import com.atlas.music.ui.components.MiniPlayerBar
import com.atlas.music.ui.screens.DownloadsScreen
import com.atlas.music.ui.screens.DuplicatesScreen
import com.atlas.music.ui.screens.HomeScreen
import com.atlas.music.ui.screens.LibraryScreen
import com.atlas.music.ui.screens.NowPlayingScreen
import com.atlas.music.ui.screens.PlaylistDetailScreen
import com.atlas.music.ui.screens.QueueScreen
import com.atlas.music.ui.screens.SearchScreen
import com.atlas.music.ui.screens.SettingsScreen
import com.atlas.music.ui.screens.ToolsScreen
import com.atlas.music.ui.viewmodel.ToolsViewModel
import com.atlas.music.ui.screens.SmartListScreen
import com.atlas.music.ui.screens.TrackInfoSheet
import com.atlas.music.ui.viewmodel.DownloadsViewModel
import com.atlas.music.ui.viewmodel.DuplicatesViewModel
import com.atlas.music.ui.viewmodel.HomeViewModel
import com.atlas.music.ui.viewmodel.LibraryViewModel
import com.atlas.music.ui.viewmodel.PlayerViewModel
import com.atlas.music.ui.viewmodel.QueueViewModel
import com.atlas.music.ui.viewmodel.SearchViewModel
import com.atlas.music.ui.viewmodel.SettingsViewModel
import com.atlas.music.ui.viewmodel.TrackActions
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("library", "Library", Icons.Filled.LibraryMusic),
    Tab("search", "Search", Icons.Filled.Search),
    Tab("tools", "Tools", Icons.Filled.Build),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun AtlasMusicApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route?.substringBefore('/') ?: "home"

    val playerVm: PlayerViewModel = viewModel()
    val toolsVm: com.atlas.music.ui.viewmodel.ToolsViewModel = viewModel()
    val playerState by playerVm.state.collectAsState()
    LaunchedEffect(playerState.lastError) {
        playerState.lastError?.let { snackbar.showSnackbar(it) }
    }
    val sleepTimerEndAt by playerVm.sleepTimerEndAt.collectAsState()

    // Shared track actions
    var pendingTrack by remember { mutableStateOf<TrackEntity?>(null) }
    var editorTrack by remember { mutableStateOf<TrackEntity?>(null) }
    val onEditTags: (TrackEntity) -> Unit = { editorTrack = it }
    val onShowInfo: (TrackEntity) -> Unit = { track -> navController.navigate("trackinfo/${track.id}") }
    val onAddToQueue: (TrackEntity) -> Unit = { track ->
        TrackActions.addToQueue(track)
        scope.launch { snackbar.showSnackbar("Added to queue") }
    }
    val onPlayNext: (TrackEntity) -> Unit = { track ->
        TrackActions.playNext(track)
        scope.launch { snackbar.showSnackbar("Playing next") }
    }
    val onAddToPlaylist: (TrackEntity) -> Unit = { track -> pendingTrack = track }
    val onToggleFavorite: (TrackEntity) -> Unit = { track ->
        scope.launch { TrackActions.toggleFavorite(track) }
    }
    val onPlayTracks: (List<TrackEntity>, Int) -> Unit = { tracks, index ->
        ServiceLocator.playbackConnection.playTracks(tracks, index)
    }
    val showBottomBar = TABS.any { it.route == currentRoute && it.route != "tools" }

    val landscape = isLandscape()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar && !landscape) {
                Column {
                    MiniPlayerBar(
                        state = playerState,
                        onOpenNowPlaying = { navController.navigate("nowplaying") },
                        onTogglePlayPause = playerVm::togglePlayPause,
                        onSkipNext = playerVm::skipNext,
                    )
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        TABS.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
            if (landscape && showBottomBar) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.width(84.dp)) {
                    // Mini player condensed for rail
                    AtlasIconButton(text = "Open Now Playing", onClick = { navController.navigate("nowplaying") }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Now playing", tint = MaterialTheme.colorScheme.primary)
                    }
                    TABS.forEach { tab ->
                        NavigationRailItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize(),
        ) {
            composable("home", enterTransition = { fadeIn(tween(210)) }, exitTransition = { fadeOut(tween(90)) }, popEnterTransition = { fadeIn(tween(210)) }, popExitTransition = { fadeOut(tween(90)) }) {
                val homeVm: HomeViewModel = viewModel()
                val state by homeVm.state.collectAsState()
                HomeScreen(
                    state = state,
                    onRescan = homeVm::rescan,
                    onOpenTrack = { track, list, index -> onPlayTracks(list, index) },
                    onToggleFavorite = onToggleFavorite,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowInfo = onShowInfo,
                    onEditTags = onEditTags,
                    onOpenLibrary = { navController.navigate("library") },
                )
            }

            composable("library", enterTransition = { fadeIn(tween(210)) }, exitTransition = { fadeOut(tween(90)) }, popEnterTransition = { fadeIn(tween(210)) }, popExitTransition = { fadeOut(tween(90)) }) {
                val libraryVm: LibraryViewModel = viewModel()
                val state by libraryVm.state.collectAsState()
                val sort by libraryVm.sort.collectAsState()
                val tracks by libraryVm.tracks.collectAsState(initial = emptyList())
                LibraryScreen(
                    viewModel = libraryVm,
                    tracks = tracks,
                    sort = sort,
                    onSetSort = libraryVm::setSort,
                    onPlay = { track, list, index -> onPlayTracks(list, index) },
                    onToggleFavorite = onToggleFavorite,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowInfo = onShowInfo,
                    onEditTags = onEditTags,
                    onOpenAlbum = { name -> navController.navigate("album/${Uri.encode(name)}") },
                    onOpenArtist = { name -> navController.navigate("artist/${Uri.encode(name)}") },
                    onOpenGenre = { name -> navController.navigate("genre/${Uri.encode(name)}") },
                    onOpenFolder = { folder -> navController.navigate("folder/${Uri.encode(folder)}") },
                    onOpenPlaylist = { id -> navController.navigate("playlist/$id") },
                    onOpenSmart = { smart -> navController.navigate("smart/${smart.name}") },
                    onOpenDuplicates = { navController.navigate("duplicates") },
                )
            }

            composable("search", enterTransition = { fadeIn(tween(210)) }, exitTransition = { fadeOut(tween(90)) }, popEnterTransition = { fadeIn(tween(210)) }, popExitTransition = { fadeOut(tween(90)) }) {
                val searchVm: SearchViewModel = viewModel()
                val state by searchVm.state.collectAsState()
                SearchScreen(
                    state = state,
                    onQueryChange = searchVm::onQueryChange,
                    onClear = searchVm::clear,
                    onPlay = { track, list, index -> onPlayTracks(list, index) },
                    onToggleFavorite = onToggleFavorite,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowInfo = onShowInfo,
                    onEditTags = onEditTags,
                    onOpenAlbum = { name -> navController.navigate("album/${Uri.encode(name)}") },
                    onOpenArtist = { name -> navController.navigate("artist/${Uri.encode(name)}") },
                    onOpenGenre = { name -> navController.navigate("genre/${Uri.encode(name)}") },
                )
            }

            composable("tools", enterTransition = { fadeIn(tween(210)) }, exitTransition = { fadeOut(tween(90)) }, popEnterTransition = { fadeIn(tween(210)) }, popExitTransition = { fadeOut(tween(90)) }) {
                ToolsScreen(
                    viewModel = toolsVm,
                    onExit = { navController.navigate("home") { popUpTo("home") { inclusive = false }; launchSingleTop = true } },
                    onOpenNowPlaying = { navController.navigate("nowplaying") },
                    onOpenQueue = { navController.navigate("queue") },
                    onOpenDuplicates = { navController.navigate("duplicates") },
                    onOpenDownloads = { navController.navigate("downloads") },
                    onOpenSettings = { navController.navigate("settings") },
                )
            }

            composable("downloads", enterTransition = { fadeIn(tween(210)) }, exitTransition = { fadeOut(tween(90)) }, popEnterTransition = { fadeIn(tween(210)) }, popExitTransition = { fadeOut(tween(90)) }) {
                val downloadsVm: DownloadsViewModel = viewModel()
                val state by downloadsVm.state.collectAsState()
                DownloadsScreen(
                    state = state,
                    onUrlChange = downloadsVm::onUrlChange,
                    onFetchPreview = downloadsVm::fetchPreview,
                    onSetFormat = downloadsVm::setFormat,
                    onConsentChange = downloadsVm::setConsentAccepted,
                    onStartDownload = downloadsVm::startDownload,
                    onCancel = downloadsVm::cancel,
                    onRetry = downloadsVm::retry,
                    onDelete = downloadsVm::delete,
                    onClearCompleted = downloadsVm::clearCompleted,
                    onClearHistory = downloadsVm::clearHistory,
                    onCheckUpdate = downloadsVm::checkYtdlpUpdate,
                    onApplyUpdate = downloadsVm::applyYtdlpUpdate,
                    onRestoreBundled = downloadsVm::restoreBundledYtdlp,
                    onSetPlaylistMode = downloadsVm::setPlaylistMode,
                )
            }

            composable("settings", enterTransition = { fadeIn(tween(210)) }, exitTransition = { fadeOut(tween(90)) }, popEnterTransition = { fadeIn(tween(210)) }, popExitTransition = { fadeOut(tween(90)) }) {
                val settingsVm: SettingsViewModel = viewModel()
                val lastScan by settingsVm.lastScanMs.collectAsState()
                SettingsScreen(
                    viewModel = settingsVm,
                    lastScanMs = lastScan,
                    onOpenDuplicates = { navController.navigate("duplicates") },
                    onOpenDownloads = { navController.navigate("downloads") },
                )
            }

            composable("nowplaying", enterTransition = { fadeIn(tween(280)) + slideInVertically(animationSpec = tween(280)) }, exitTransition = { fadeOut(tween(90)) }, popEnterTransition = { fadeIn(tween(300)) }, popExitTransition = { fadeOut(tween(90)) + slideOutVertically(animationSpec = tween(200)) }) {
                val trackId = playerState.currentTrackId
                val favoriteFlow = remember(trackId) {
                    trackId?.let { ServiceLocator.musicRepository.observeFavorite(it) } ?: kotlinx.coroutines.flow.flowOf(false)
                }
                val isFavoriteState by favoriteFlow.collectAsState(initial = false)
                val isFavorite = isFavoriteState == true
                var currentTrack by remember(trackId) { mutableStateOf<TrackEntity?>(null) }
                LaunchedEffect(trackId) {
                    currentTrack = trackId?.let { ServiceLocator.musicRepository.getTrack(it) }
                }
                NowPlayingScreen(
                    state = playerState,
                    track = currentTrack,
                    isFavorite = isFavorite,
                    sleepTimerEndAt = sleepTimerEndAt,
                    onClose = { navController.popBackStack() },
                    onTogglePlayPause = playerVm::togglePlayPause,
                    onSkipNext = playerVm::skipNext,
                    onSkipPrevious = playerVm::skipPrevious,
                    onSeekTo = playerVm::seekTo,
                    onToggleShuffle = playerVm::toggleShuffle,
                    onCycleRepeatMode = playerVm::cycleRepeatMode,
                    onToggleFavorite = {
                        ServiceLocator.playbackConnection.toggleFavoriteCurrent()
                    },
                    onStartSleepTimer = playerVm::startSleepTimer,
                    onClearSleepTimer = playerVm::clearSleepTimer,
                    onOpenQueue = { navController.navigate("queue") },
                    onOpenInfo = { playerState.currentTrackId?.let { navController.navigate("trackinfo/$it") } },
                    onSetSpeed = playerVm::setPlaybackSpeed,
                    onSetOutputDevice = { deviceId -> ServiceLocator.playbackConnection.setPreferredOutputDevice(deviceId) },
                    toolsVm = toolsVm,
                )
            }

            composable("queue") {
                val queueVm: QueueViewModel = viewModel()
                QueueScreen(
                    viewModel = queueVm,
                    currentTrackId = playerState.currentTrackId,
                    onClose = { navController.popBackStack() },
                    onPlayAt = { index ->
                        ServiceLocator.playbackConnection.controller?.seekTo(index, 0)
                    },
                )
            }

            composable(
                "playlist/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("playlistId") ?: return@composable
                val detailVm = remember(id) { com.atlas.music.ui.viewmodel.PlaylistDetailViewModel(id) }
                PlaylistDetailScreen(
                    viewModel = detailVm,
                    onBack = { navController.popBackStack() },
                    onPlayAll = { tracks -> onPlayTracks(tracks, 0) },
                    onToggleFavorite = onToggleFavorite,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowInfo = onShowInfo,
                    onEditTags = onEditTags,
                )
            }

            composable(
                "smart/{type}",
                arguments = listOf(navArgument("type") { type = NavType.StringType }),
            ) { entry ->
                val type = entry.arguments?.getString("type") ?: return@composable
                val repo = ServiceLocator.musicRepository
                val flow = remember(type) {
                    when (type) {
                        SmartPlaylist.MOST_PLAYED.name -> repo.observeMostPlayed()
                        SmartPlaylist.RECENTLY_PLAYED.name -> repo.observeRecentlyPlayed()
                        SmartPlaylist.RECENTLY_ADDED.name -> repo.observeRecentlyAdded()
                        SmartPlaylist.FAVORITES.name -> repo.observeFavorites()
                        else -> repo.observeRecentlyAdded()
                    }
                }
                val tracks by flow.collectAsState(initial = emptyList())
                val title = runCatching { SmartPlaylist.valueOf(type).label }.getOrDefault("Smart playlist")
                SmartListScreen(
                    title = title,
                    subtitle = "Generated from your listening",
                    tracks = tracks,
                    onPlay = { track, index -> onPlayTracks(tracks, index) },
                    onToggleFavorite = onToggleFavorite,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowInfo = onShowInfo,
                    onEditTags = onEditTags,
                    onPlayAll = { onPlayTracks(tracks, 0) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                "album/{name}",
                arguments = listOf(navArgument("name") { type = NavType.StringType }),
            ) { entry ->
                val name = Uri.decode(entry.arguments?.getString("name") ?: "")
                val tracks by remember(name) { ServiceLocator.musicRepository.observeAlbumTracks(name) }.collectAsState(initial = emptyList())
                SmartListScreen(
                    title = name,
                    subtitle = "Album",
                    tracks = tracks,
                    onPlay = { track, index -> onPlayTracks(tracks, index) },
                    onToggleFavorite = onToggleFavorite,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowInfo = onShowInfo,
                    onEditTags = onEditTags,
                    onPlayAll = { onPlayTracks(tracks, 0) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                "artist/{name}",
                arguments = listOf(navArgument("name") { type = NavType.StringType }),
            ) { entry ->
                val name = Uri.decode(entry.arguments?.getString("name") ?: "")
                val tracks by remember(name) { ServiceLocator.musicRepository.observeArtistTracks(name) }.collectAsState(initial = emptyList())
                SmartListScreen(
                    title = name,
                    subtitle = "Artist",
                    tracks = tracks,
                    onPlay = { track, index -> onPlayTracks(tracks, index) },
                    onToggleFavorite = onToggleFavorite,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowInfo = onShowInfo,
                    onEditTags = onEditTags,
                    onPlayAll = { onPlayTracks(tracks, 0) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                "genre/{name}",
                arguments = listOf(navArgument("name") { type = NavType.StringType }),
            ) { entry ->
                val name = Uri.decode(entry.arguments?.getString("name") ?: "")
                val tracks by remember(name) { ServiceLocator.musicRepository.observeGenreTracks(name) }.collectAsState(initial = emptyList())
                SmartListScreen(
                    title = name,
                    subtitle = "Genre",
                    tracks = tracks,
                    onPlay = { track, index -> onPlayTracks(tracks, index) },
                    onToggleFavorite = onToggleFavorite,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowInfo = onShowInfo,
                    onEditTags = onEditTags,
                    onPlayAll = { onPlayTracks(tracks, 0) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                "folder/{folder}",
                arguments = listOf(navArgument("folder") { type = NavType.StringType }),
            ) { entry ->
                val folder = Uri.decode(entry.arguments?.getString("folder") ?: "")
                val tracks by remember(folder) { ServiceLocator.musicRepository.observeFolderTracks(folder) }.collectAsState(initial = emptyList())
                SmartListScreen(
                    title = folder,
                    subtitle = "Folder",
                    tracks = tracks,
                    onPlay = { track, index -> onPlayTracks(tracks, index) },
                    onToggleFavorite = onToggleFavorite,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowInfo = onShowInfo,
                    onEditTags = onEditTags,
                    onPlayAll = { onPlayTracks(tracks, 0) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable("duplicates") {
                val duplicatesVm: DuplicatesViewModel = viewModel()
                DuplicatesScreen(
                    viewModel = duplicatesVm,
                    onBack = { navController.popBackStack() },
                    onRemoveFromLibrary = { track ->
                        scope.launch {
                            TrackActions.removeFromLibrary(track)
                            snackbar.showSnackbar("Removed from library")
                            duplicatesVm.refresh()
                        }
                    },
                    onKeepOnly = { keep, group ->
                        scope.launch {
                            group.filter { it.id != keep.id }.forEach { ServiceLocator.musicRepository.deleteTrack(it.id) }
                            snackbar.showSnackbar("Kept “${keep.title}”, removed ${group.size - 1} ${if (group.size - 1 == 1) "copy" else "copies"}")
                            duplicatesVm.refresh()
                        }
                    },
                )
            }

            composable(
                "trackinfo/{trackId}",
                arguments = listOf(navArgument("trackId") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("trackId") ?: return@composable
                var track by remember { mutableStateOf<TrackEntity?>(null) }
                LaunchedEffect(id) {
                    track = ServiceLocator.musicRepository.getTrack(id)
                }
                val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
                track?.let { current ->
                    TrackInfoSheet(
                        track = current,
                        sheetState = sheetState,
                        onDismiss = { navController.popBackStack() },
                        onEditTags = { t -> navController.popBackStack(); editorTrack = t },
                    )
                }
            }
            }
        }
        }
    }

    pendingTrack?.let { track ->
        AddToPlaylistDialog(
            track = track,
            onDismiss = { pendingTrack = null },
            onAdded = { playlistName ->
                pendingTrack = null
                scope.launch { snackbar.showSnackbar("Added to “$playlistName”") }
            },
        )
    }

    editorTrack?.let { track ->
        com.atlas.music.ui.components.TrackEditorDialog(
            track = track,
            onDismiss = { editorTrack = null },
            onSave = { title, artist, album, genre, year, trackNumber, discNumber ->
                val t = track
                editorTrack = null
                scope.launch {
                    val res = TrackActions.updateTags(t, title, artist, album, genre, year, trackNumber, discNumber)
                    val msg = if (res.fileOk) "Tags saved" else if (res.fileError == null) "Tags saved" else "Tags saved to library\n${res.fileError}"
                    snackbar.showSnackbar(msg)
                }
            },
            onReset = {
                val t = track
                editorTrack = null
                scope.launch {
                    TrackActions.resetTags(t)
                    snackbar.showSnackbar("Tags reset to file values")
                }
            },
        )
    }
}

@Composable
private fun AddToPlaylistDialog(
    track: TrackEntity,
    onDismiss: () -> Unit,
    onAdded: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val playlists by ServiceLocator.playlistRepository.observePlaylists().collectAsState(initial = emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            if (playlists.isEmpty()) {
                Text("No playlists yet. Create one from the Library tab first.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Text(
                            playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    scope.launch {
                                        ServiceLocator.playlistRepository.addTrack(playlist.id, track.id)
                                        onAdded(playlist.name)
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
