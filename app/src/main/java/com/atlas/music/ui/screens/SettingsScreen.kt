package com.atlas.music.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atlas.music.BuildConfig
import kotlinx.coroutines.launch
import com.atlas.music.ui.viewmodel.SettingsViewModel
import com.atlas.music.util.ArtworkStyle
import com.atlas.music.util.SortOption
import com.atlas.music.util.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    lastScanMs: Long,
    onOpenDuplicates: () -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsState()
    val effectsAvailable by viewModel.effectsAvailable.collectAsState()
    val effectsUnavailable = effectsAvailable == false
    val context = LocalContext.current
    var showExcludedInput by remember { mutableStateOf(false) }
    var excludedInput by remember { mutableStateOf("") }
    var showLicenses by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    val destinationLabel = if (settings.downloadDestinationUri.isBlank()) "Music/Atlas Music (automatic)" else "Custom folder"
    val screenScope = androidx.compose.runtime.rememberCoroutineScope()
    val toggleScope = screenScope

    val destinationPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setDownloadDestination(uri.toString())
        }
    }

    val musicFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val name = runCatching {
                androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name
            }.getOrNull() ?: uri.lastPathSegment ?: "Custom folder"
            screenScope.launch { viewModel.setMusicFolderAndRescan(uri.toString(), name) }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
    ) {
        item { TopAppBar(title = { Text("Settings", fontWeight = FontWeight.ExtraBold) }, modifier = Modifier.statusBarsPadding(), colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }

        item { SettingsGroup("Appearance") }
        item {
            SettingsRow("Theme", "Atlas signature by default — me-when-music presets below") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp).horizontalScroll(rememberScrollState())) {
                    listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT).forEach { mode ->
                        FilterChip(selected = settings.themeMode == mode, onClick = { viewModel.setTheme(mode) }, label = { Text(mode.label, maxLines = 1) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    ThemeMode.entries.filter { it !in listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT) }.forEach { mode ->
                        FilterChip(selected = settings.themeMode == mode, onClick = { viewModel.setTheme(mode) }, label = { Text(mode.label, maxLines = 1) })
                    }
                }
            }
        }
        item {
            val presetActive = settings.themeMode in listOf(
                com.atlas.music.util.ThemeMode.MOCHA, com.atlas.music.util.ThemeMode.LATTE,
                com.atlas.music.util.ThemeMode.GRUVBOX, com.atlas.music.util.ThemeMode.TOKYO_NIGHT,
                com.atlas.music.util.ThemeMode.NORD, com.atlas.music.util.ThemeMode.DRACULA,
                com.atlas.music.util.ThemeMode.KANAGAWA, com.atlas.music.util.ThemeMode.ROSE_PINE,
                com.atlas.music.util.ThemeMode.EVERFOREST, com.atlas.music.util.ThemeMode.OXOCARBON,
            )
            SwitchRow(
                title = "Dynamic color",
                subtitle = if (presetActive) "Not used with presets — pick System/Dark/Light to use it" else "Match the system wallpaper palette (Android 13+)",
                checked = settings.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
                enabled = !presetActive,
            )
        }
        item {
            SettingsRow("Album artwork", "How much artwork lists show") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(bottom = 12.dp)) {
                    ArtworkStyle.entries.forEach { style ->
                        SegmentedButton(
                            selected = settings.artworkStyle == style,
                            onClick = { viewModel.setArtworkStyle(style) },
                            shape = SegmentedButtonDefaults.itemShape(index = style.ordinal, count = ArtworkStyle.entries.size),
                        ) { Text(if (style == ArtworkStyle.LARGE) "Large" else "Compact", maxLines = 1) }
                    }
                }
            }
        }

        item { SettingsGroup("Background engine") }
        item {
            SettingsRow("Mode", "Live canvas behind Now Playing (bg.js port, morph excluded)") {
                androidx.compose.foundation.layout.Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp).horizontalScroll(rememberScrollState())) {
                        listOf("wave", "conway", "boids", "dunes", "starfield").forEach { id ->
                            FilterChip(selected = settings.backgroundMode == id, onClick = { viewModel.setBackgroundMode(id) }, label = { Text(id, maxLines = 1) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp).horizontalScroll(rememberScrollState())) {
                        listOf("maze", "sort", "matrix", "flow", "lorenz").forEach { id ->
                            FilterChip(selected = settings.backgroundMode == id, onClick = { viewModel.setBackgroundMode(id) }, label = { Text(id, maxLines = 1) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("pendulum", "plasma", "langton", "off").forEach { id ->
                            FilterChip(selected = settings.backgroundMode == id, onClick = { viewModel.setBackgroundMode(id) }, label = { Text(id, maxLines = 1) })
                        }
                    }
                }
            }
        }
        if (settings.backgroundMode != "off") {
            item {
                SliderRow(label = "Density", value = settings.backgroundDensity.toFloat(), valueRange = 1f..100f, steps = 8, onValueChange = { viewModel.setBackgroundDensity(it.toInt()) }, valueLabel = settings.backgroundDensity.toString())
            }
            item {
                SliderRow(label = "Speed", value = settings.backgroundSpeed.toFloat(), valueRange = 1f..100f, steps = 8, onValueChange = { viewModel.setBackgroundSpeed(it.toInt()) }, valueLabel = settings.backgroundSpeed.toString())
            }
            item {
                SliderRow(label = "Trails", value = settings.backgroundTrail.toFloat(), valueRange = 1f..95f, steps = 8, onValueChange = { viewModel.setBackgroundTrail(it.toInt()) }, valueLabel = settings.backgroundTrail.toString())
            }
            item {
                SwitchRow(title = "Audio reactive", subtitle = "Sim breathes with bass/energy", checked = settings.backgroundReact, onCheckedChange = viewModel::setBackgroundReact)
            }
        }
        if (settings.backgroundMode == "sort") {
            item {
                SettingsRow("Sort algorithm", "Pick one, or random each run") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("random", "bubble", "quick", "merge", "insertion").forEach { id ->
                            FilterChip(selected = settings.sortAlgo == id, onClick = { viewModel.setSortAlgo(id) }, label = { Text(id, maxLines = 1) })
                        }
                    }
                }
            }
        }

        item { SettingsGroup("Playback") }
        item {
            SwitchRow(
                title = "Crossfade",
                subtitle = "Fade out the ending and fade in the next track — ${settings.crossfadeSeconds}s",
                checked = settings.crossfadeEnabled,
                onCheckedChange = viewModel::setCrossfade,
            )
        }
        if (settings.crossfadeEnabled) {
            item {
                SliderRow(
                    label = "Fade duration",
                    value = settings.crossfadeSeconds.toFloat(),
                    valueRange = 2f..12f,
                    steps = 9,
                    onValueChange = { viewModel.setCrossfadeSeconds(it.toInt()) },
                    valueLabel = "${settings.crossfadeSeconds}s",
                )
            }
        }
        item {
            listOf("Gapless playback — always on. ExoPlayer chains tracks with no silent gap.").forEach { note ->
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
        }
        item {
            SwitchRow(
                title = "Equalizer",
                subtitle = if (effectsUnavailable) "Not available on this device" else "System audio effects, applied to the playback session",
                checked = settings.equalizerEnabled,
                onCheckedChange = viewModel::setEqualizer,
                enabled = !effectsUnavailable,
            )
        }
        item {
            SwitchRow(
                title = "Bass boost",
                subtitle = if (effectsUnavailable) "Not available on this device" else "Adds ~60% low-frequency weight when your device supports it",
                checked = settings.bassBoostEnabled,
                onCheckedChange = viewModel::setBassBoost,
                enabled = !effectsUnavailable,
            )
        }
        item {
            SwitchRow(
                title = "Volume normalization",
                subtitle = if (effectsUnavailable) "Not available on this device" else "Applies a +4 dB loudness target so tracks sound consistent",
                checked = settings.normalizationEnabled,
                onCheckedChange = viewModel::setNormalization,
                enabled = !effectsUnavailable,
            )
        }
        item {
            SettingsRow("Sleep timer default", "Used when you tap the sleep button in Now Playing") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        FilterChip(
                            selected = settings.sleepTimerDefaultMinutes == minutes,
                            onClick = { viewModel.setSleepTimerDefault(minutes) },
                            label = { Text("$minutes min") },
                        )
                    }
                }
            }
        }

        item { SettingsGroup("Library") }
        item {
            SettingsRow("Default sort", "Applied to the Songs tab") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    listOf(SortOption.TITLE, SortOption.ARTIST, SortOption.ALBUM, SortOption.DATE_ADDED).forEach { sort ->
                        FilterChip(
                            selected = settings.defaultSort == sort,
                            onClick = { viewModel.setDefaultSort(sort) },
                            label = { Text(sort.label) },
                        )
                    }
                }
            }
        }
        item {
            ListItem(
                headlineContent = { Text("Music folder") },
                supportingContent = {
                    if (settings.musicFolderUri.isBlank()) {
                        Text("Automatic — scans every audio file on the device (MediaStore)")
                    } else {
                        Text("Custom: ${settings.musicFolderName.ifBlank { "folder" }}\nLimit to this folder only: ${if (settings.musicFolderOnly) "On" else "Off (also scans everywhere)"}")
                    }
                },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (settings.musicFolderUri.isNotBlank()) {
                            TextButton(onClick = { viewModel.clearMusicFolder(); viewModel.rescan() }) { Text("Clear") }
                        }
                        TextButton(onClick = { musicFolderPicker.launch(null) }) { Text("Change") }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
        if (settings.musicFolderUri.isNotBlank()) {
            item {
                SwitchRow(
                    title = "Scan only this folder",
                    subtitle = "When on, Atlas ignores files outside the folder above. When off, it scans everywhere plus this folder.",
                    checked = settings.musicFolderOnly,
                    onCheckedChange = { toggleScope.launch { viewModel.setMusicFolderOnlyAndRescan(it) } },
                )
            }
        }
        item {
            ListItem(
                headlineContent = { Text("Excluded folders") },
                supportingContent = { Text(if (settings.excludedFolders.isEmpty()) "None — everything is indexed" else settings.excludedFolders.joinToString()) },
                trailingContent = { TextButton(onClick = { showExcludedInput = true }) { Text("Add") } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
        if (settings.excludedFolders.isNotEmpty()) {
            items(settings.excludedFolders.toList(), key = { it }) { folder ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(folder, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { viewModel.removeExcludedFolder(folder) }) { Text("Remove") }
                }
            }
        }
        item {
            ListItem(
                headlineContent = { Text("Rescan library") },
                supportingContent = { Text("Last scan: ${if (lastScanMs > 0) android.text.format.DateUtils.getRelativeTimeSpanString(lastScanMs) else "never"}") },
                trailingContent = { TextButton(onClick = viewModel::rescan) { Text("Rescan") } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Find duplicates") },
                supportingContent = { Text("Same song saved twice, flagging wasted space") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenDuplicates),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
        }

        item { SettingsGroup("Downloads") }
        item {
            SettingsRow("Preferred format", "Applies to new downloads") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    listOf("opus" to "Opus", "m4a" to "M4A").forEach { (value, label) ->
                        FilterChip(
                            selected = settings.downloadFormat == value,
                            onClick = { viewModel.setDownloadFormat(value) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
        item {
            SwitchRow(
                title = "Import downloads into library",
                subtitle = "After a download completes, Atlas Music rescans and indexes it",
                checked = settings.autoImportDownloads,
                onCheckedChange = viewModel::setAutoImportDownloads,
            )
        }
        item {
            SwitchRow(
                title = "Check yt-dlp updates automatically",
                subtitle = "Atlas Music asks the official release source on launch — never silently updates",
                checked = settings.autoCheckYtdlpUpdates,
                onCheckedChange = viewModel::setAutoCheckYtdlp,
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Download destination") },
                supportingContent = { Text(destinationLabel) },
                trailingContent = { TextButton(onClick = { destinationPicker.launch(null) }) { Text("Change") } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
        item {
            ListItem(
                headlineContent = { Text("yt-dlp version & updates") },
                supportingContent = { Text("Check, apply, or restore the bundled engine") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenDownloads),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
        }

        item { SettingsGroup("Application") }
        item {
            ListItem(
                headlineContent = { Text("Atlas Music") },
                supportingContent = { Text("Version ${BuildConfig.VERSION_NAME}  ·  Ad-less Music.  ·  No accounts, no telemetry you didn't ask for") },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
        item {
            SwitchRow(
                title = "Diagnostics",
                subtitle = "Keep local scan/download error logs for troubleshooting (never uploaded)",
                checked = settings.diagnosticsEnabled,
                onCheckedChange = viewModel::setDiagnostics,
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Open-source licenses") },
                supportingContent = { Text("Everything Atlas Music is built on") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { showLicenses = true },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Privacy") },
                supportingContent = { Text("What stays on your device, and what never happens") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { showPrivacy = true },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    }

    if (showExcludedInput) {
        AlertDialog(
            onDismissRequest = { showExcludedInput = false },
            title = { Text("Exclude a folder") },
            text = {
                OutlinedTextField(
                    value = excludedInput,
                    onValueChange = { excludedInput = it },
                    label = { Text("Folder name (e.g. podcasts)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addExcludedFolder(excludedInput)
                    excludedInput = ""
                    showExcludedInput = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showExcludedInput = false }) { Text("Cancel") } },
        )
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("Open-source licenses") },
            text = {
                Text(
                    "Kotlin & AndroidX (Apache-2.0)\nJetpack Compose & Material 3 (Apache-2.0)\nMedia3 / ExoPlayer (Apache-2.0)\nRoom (Apache-2.0)\nCoil (Apache-2.0)\nOkHttp (Apache-2.0)\nDesugar JDK libs (GPL-2.0 with Classpath Exception)\nChaquopy (MIT)\nyt-dlp (Unlicense)\nmutagen (GPL-2.0)\nTensorFlow Lite — not included\n\nFull texts live in each project's repository. Atlas Music itself is yours to audit — no obfuscated ads SDKs, no analytics.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("Done") } },
        )
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("Privacy") },
            text = {
                Text(
                    "Atlas Music keeps your library, playlists, favorites, history and downloads on this device. It has no account system, no ads, no analytics SDK, and never uploads your listening data.\n\nInternetwork access happens only for two things you control: downloading authorized media through the link you paste, and checking the official yt-dlp release source for updates.\n\nPermission explanations: READ_MEDIA_AUDIO browses your own audio files; INTERNET is used solely for the two features above; notifications are for playback and download progress.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun SettingsGroup(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

@Composable
private fun SettingsRow(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        content()
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    ListItem(
        headlineContent = { Text(title, color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun SliderRow(label: String, value: Float, valueRange: ClosedFloatingPointRange<Float>, steps: Int, onValueChange: (Float) -> Unit, valueLabel: String) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(valueLabel, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps)
    }
}

