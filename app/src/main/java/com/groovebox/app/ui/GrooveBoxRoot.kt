package com.groovebox.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.groovebox.app.ui.components.AmbientBackground
import com.groovebox.app.ui.components.FrostSurface
import com.groovebox.app.ui.components.LocalAmbientPhase
import com.groovebox.app.ui.components.NowPlayingBar
import com.groovebox.app.ui.screens.EqualizerScreen
import com.groovebox.app.ui.screens.FullPlayerScreen
import com.groovebox.app.ui.screens.HomeScreen
import com.groovebox.app.ui.screens.LibraryScreen
import com.groovebox.app.ui.screens.PlaylistsScreen
import com.groovebox.app.ui.screens.SettingsScreen
import com.groovebox.app.ui.screens.SourceScreen
import com.groovebox.app.ui.screens.ThemesScreen
import com.groovebox.app.ui.screens.VisualizerSettingsScreen
import com.groovebox.app.ui.theme.GbColors

private enum class Tab { HOME, LIBRARY, PLAYLISTS, SETTINGS }
private enum class SettingsSubScreen { NONE, SOURCE, EQUALIZER, THEMES, VISUALIZER }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GrooveBoxRoot(
    viewModel: GrooveBoxViewModel,
    onLinkFolder: () -> Unit,
    onAddFolderAsPlaylist: () -> Unit,
    onPickPlaylistCover: (String) -> Unit,
    onContactSupport: () -> Unit,
    notificationsGranted: Boolean,
    batteryExempt: Boolean,
    onRequestBatteryExemption: () -> Unit,
    onRequestNotifications: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    // Playlists whose track list is manually curated (not folder-backed) — the only
    // ones "Add to playlist" menus should offer, since a folder playlist's contents
    // are always exactly whatever's in its linked folder and can't be manually edited.
    val manualPlaylists = remember(state.playlists) { state.playlists.filter { it.folderUri == null } }
    // The custom cover of whichever playlist the current queue was started from (if
    // any) — used as fallback art in the mini player/Full Player for tracks that have
    // no embedded art of their own.
    val playbackSourceCoverPath = remember(state.playlists, state.playbackSourcePlaylistId) {
        state.playlists.find { it.id == state.playbackSourcePlaylistId }?.coverArtPath
    }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var fullPlayerOpen by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    var openPlaylistId by remember { mutableStateOf<String?>(null) }
    var settingsSubScreen by remember { mutableStateOf(SettingsSubScreen.NONE) }
    val queueSheetState = androidx.compose.material3.rememberModalBottomSheetState()

    // The ambient blobs drift slowly (a 22s cycle), but this value is read by
    // AmbientBackground's Modifier.blur(120.dp) over the FULL screen, active behind
    // every single tab for the app's entire lifetime. Driving it with animateFloat
    // (which ticks every vsync — 60-120 times/sec) forced that whole expensive
    // full-screen blur to recompute on every single frame forever, which measured as
    // 96-100% janky frames even just scrolling the Library — the blur recompute was
    // eating most of every frame's budget regardless of what screen was on top.
    // Stepping it manually at ~8 updates/sec is visually indistinguishable for
    // something this slow, but cuts that recompute rate by roughly 10x.
    var ambientPhase by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val stepMs = 120L
        val cycleMs = 22000L
        val steps = (cycleMs / stepMs).toInt().coerceAtLeast(1)
        var i = 0
        var direction = 1
        while (true) {
            ambientPhase = i.toFloat() / steps
            delay(stepMs)
            i += direction
            if (i >= steps) { i = steps; direction = -1 }
            else if (i <= 0) { i = 0; direction = 1 }
        }
    }

    CompositionLocalProvider(LocalAmbientPhase provides ambientPhase) {
    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground(modifier = Modifier.fillMaxSize())

        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            bottomBar = {
                Column {
                    NowPlayingBar(
                        track = state.currentTrack,
                        isPlaying = state.isPlaying,
                        positionMs = viewModel.player.positionMs,
                        durationMs = viewModel.player.durationMs,
                        fallbackCoverPath = playbackSourceCoverPath,
                        onTap = { fullPlayerOpen = true },
                        onToggleLike = { state.currentTrack?.let { viewModel.toggleLike(it) } }
                    )
                    FrostSurface(tint = GbColors.GlassBg.copy(alpha = 0.72f)) {
                        NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                            NavigationBarItem(
                                selected = tab == Tab.HOME,
                                onClick = { tab = Tab.HOME },
                                icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                                label = { Text("Home") },
                                colors = navColors()
                            )
                            NavigationBarItem(
                                selected = tab == Tab.LIBRARY,
                                onClick = { tab = Tab.LIBRARY; openPlaylistId = null; viewModel.setView(LibraryView.ALL) },
                                icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = "Library") },
                                label = { Text("Library") },
                                colors = navColors()
                            )
                            NavigationBarItem(
                                selected = tab == Tab.PLAYLISTS,
                                onClick = { tab = Tab.PLAYLISTS; openPlaylistId = null },
                                icon = { Icon(Icons.Filled.QueueMusic, contentDescription = "Playlists") },
                                label = { Text("Playlists") },
                                colors = navColors()
                            )
                            NavigationBarItem(
                                selected = tab == Tab.SETTINGS,
                                onClick = { tab = Tab.SETTINGS; openPlaylistId = null; settingsSubScreen = SettingsSubScreen.NONE },
                                icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") },
                                colors = navColors()
                            )
                        }
                    }
                }
            }
        ) { padding ->
            // Only the top inset is kept — the bottom inset is deliberately dropped so
            // the scrollable content actually extends full-height behind the mini
            // player/nav bar (each screen already ends its list with its own bottom
            // spacer), letting real content genuinely show/tint through their
            // translucent fills as it scrolls, instead of stopping in a hard line
            // right above them.
            FrostSurface(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    tab == Tab.HOME -> {
                        HomeScreen(
                            allTracks = state.allTracks,
                            playlists = state.playlists,
                            onOpenLiked = { tab = Tab.LIBRARY; viewModel.setView(LibraryView.LIKED) },
                            onOpenPlaylist = { id -> openPlaylistId = id; tab = Tab.LIBRARY; viewModel.setView(LibraryView.PLAYLIST, id) },
                            onOpenAllSongs = { tab = Tab.LIBRARY; viewModel.setView(LibraryView.ALL) },
                            onTrackClick = { t -> viewModel.playTrack(t) }, // Home is never playlist-scoped
                            onShuffleAll = {
                                if (state.allTracks.isNotEmpty()) {
                                    viewModel.playTrack(state.allTracks.random())
                                    if (!viewModel.player.shuffle.value) viewModel.toggleShuffle()
                                }
                            }
                        )
                    }
                    tab == Tab.LIBRARY || openPlaylistId != null -> {
                        val activePlaylist = remember(state.playlists, state.activePlaylistId) {
                            state.playlists.find { it.id == state.activePlaylistId }
                        }
                        LibraryScreen(
                            tracks = state.visibleTracks,
                            playlists = manualPlaylists,
                            activePlaylist = if (state.view == LibraryView.PLAYLIST) activePlaylist else null,
                            currentTrackUri = state.currentTrack?.uri,
                            view = state.view,
                            searchQuery = state.searchQuery,
                            sortMode = state.sortMode,
                            onSearchChange = viewModel::setSearchQuery,
                            onSetView = { v -> viewModel.setView(v) },
                            onSetSortMode = { mode -> viewModel.setSortMode(mode) },
                            onLinkFolder = onLinkFolder,
                            onTrackClick = { t ->
                                viewModel.playTrack(t, sourcePlaylistId = if (state.view == LibraryView.PLAYLIST) state.activePlaylistId else null)
                            },
                            onToggleLike = { t -> viewModel.toggleLike(t) },
                            onPlayNext = { t -> viewModel.playNext(t) },
                            onAddToQueue = { t -> viewModel.addToQueue(t) },
                            onAddToPlaylist = { plId, t -> viewModel.addToPlaylist(plId, t) },
                            onDelete = { t -> viewModel.deleteTrack(t) }
                        )
                    }
                    tab == Tab.PLAYLISTS -> {
                        PlaylistsScreen(
                            playlists = state.playlists,
                            onOpenPlaylist = { id -> openPlaylistId = id; tab = Tab.LIBRARY; viewModel.setView(LibraryView.PLAYLIST, id) },
                            onCreatePlaylist = { name -> viewModel.createPlaylist(name) {} },
                            onDeletePlaylist = { id -> viewModel.deletePlaylist(id) },
                            onRenamePlaylist = { id, name -> viewModel.renamePlaylist(id, name) },
                            onPickCover = onPickPlaylistCover,
                            onClearCover = { id -> viewModel.clearPlaylistCoverArt(id) }
                        )
                    }
                    tab == Tab.SETTINGS && settingsSubScreen == SettingsSubScreen.SOURCE -> {
                        SourceScreen(
                            linkedFolders = state.linkedFolders,
                            onLinkFolder = onLinkFolder,
                            onAddFolderAsPlaylist = onAddFolderAsPlaylist,
                            onUnlinkFolder = { uri -> viewModel.unlinkFolder(uri) },
                            onRescan = { viewModel.rescanAll() },
                            onBack = { settingsSubScreen = SettingsSubScreen.NONE }
                        )
                    }
                    tab == Tab.SETTINGS && settingsSubScreen == SettingsSubScreen.EQUALIZER -> {
                        val bandInfo by viewModel.player.eqBandInfo.collectAsState()
                        EqualizerScreen(
                            eqPreset = state.eqPreset,
                            customBands = state.eqCustomBands,
                            bandInfo = bandInfo,
                            onSetPreset = { name -> viewModel.setEqPreset(name) },
                            onSetBand = { i, gain -> viewModel.setEqCustomBand(i, gain) },
                            onBack = { settingsSubScreen = SettingsSubScreen.NONE }
                        )
                    }
                    tab == Tab.SETTINGS && settingsSubScreen == SettingsSubScreen.THEMES -> {
                        ThemesScreen(
                            themeId = state.themeId,
                            onSetTheme = { id -> viewModel.setThemeId(id) },
                            onBack = { settingsSubScreen = SettingsSubScreen.NONE }
                        )
                    }
                    tab == Tab.SETTINGS && settingsSubScreen == SettingsSubScreen.VISUALIZER -> {
                        VisualizerSettingsScreen(
                            enabled = state.visualizerEnabled,
                            mode = state.visualizerMode,
                            colorHue = state.visualizerColorHue,
                            onSetEnabled = { v -> viewModel.setVisualizerEnabled(v) },
                            onSetMode = { id -> viewModel.setVisualizerMode(id) },
                            onSetColorHue = { hue -> viewModel.setVisualizerColorHue(hue) },
                            onBack = { settingsSubScreen = SettingsSubScreen.NONE }
                        )
                    }
                    tab == Tab.SETTINGS -> {
                        SettingsScreen(
                            linkedFolders = state.linkedFolders,
                            autoplayOnLaunch = state.autoplayOnLaunch,
                            playbackRate = state.playbackRate,
                            themeId = state.themeId,
                            visualizerMode = state.visualizerMode,
                            visualizerEnabled = state.visualizerEnabled,
                            notificationsGranted = notificationsGranted,
                            batteryExempt = batteryExempt,
                            resumeOnHeadset = state.resumeOnHeadset,
                            resumeOnBluetooth = state.resumeOnBluetooth,
                            pauseResumeOnVolume = state.pauseResumeOnVolume,
                            onOpenSource = { settingsSubScreen = SettingsSubScreen.SOURCE },
                            onOpenEqualizer = { settingsSubScreen = SettingsSubScreen.EQUALIZER },
                            onOpenThemes = { settingsSubScreen = SettingsSubScreen.THEMES },
                            onOpenVisualizer = { settingsSubScreen = SettingsSubScreen.VISUALIZER },
                            onSetAutoplayOnLaunch = { v -> viewModel.setAutoplayOnLaunch(v) },
                            onSetPlaybackSpeed = { rate -> viewModel.setPlaybackSpeed(rate) },
                            onRequestBatteryExemption = onRequestBatteryExemption,
                            onRequestNotifications = onRequestNotifications,
                            onSetResumeOnHeadset = { v -> viewModel.setResumeOnHeadset(v) },
                            onSetResumeOnBluetooth = { v -> viewModel.setResumeOnBluetooth(v) },
                            onSetPauseResumeOnVolume = { v -> viewModel.setPauseResumeOnVolume(v) },
                            onContactSupport = onContactSupport
                        )
                    }
                }
            }
            }
        }

        // 180ms rather than the 300ms default — fast enough to read as an instant
        // response to the tap instead of a visible delay, while still keeping just
        // enough motion that the transition doesn't look like a jarring hard-cut.
        val quickTween = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 180)
        AnimatedVisibility(
            visible = fullPlayerOpen,
            enter = slideInVertically(animationSpec = quickTween, initialOffsetY = { it }),
            exit = slideOutVertically(animationSpec = quickTween, targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            val lightBg = state.fullPlayerLightBackground
            val panelGradient = if (lightBg) {
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(androidx.compose.ui.graphics.Color(0xFFFAF9FC), androidx.compose.ui.graphics.Color(0xFFEDEAF3))
                )
            } else {
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(GbColors.PanelTop, GbColors.PanelBottom))
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(panelGradient)
                    // A plain Box with only a background draws over the Library screen
                    // behind it but doesn't consume touches — any tap that lands on the
                    // album art or other non-interactive area of the Full Player was
                    // falling straight through to whatever track row happened to sit at
                    // that same position underneath, playing it. This no-op clickable
                    // absorbs every tap here instead of letting it pass through.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                // Real frosted glass on top of the panel gradient — a live blur of the
                // ambient blobs bleeding through, per the reference the mini player was
                // (mistakenly) given instead of this screen. In light mode the ambient
                // blobs would read as murky smudges on a bright background, so this
                // swaps to a barely-there dark tint just for subtle depth instead.
                FrostSurface(
                    modifier = Modifier.fillMaxSize(),
                    tint = if (lightBg) androidx.compose.ui.graphics.Color(0x0A000000) else GbColors.GlassBg
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    FullPlayerScreen(
                        track = state.currentTrack,
                        isPlaying = state.isPlaying,
                        isLiked = (state.currentTrack?.likes ?: 0) > 0,
                        positionMs = viewModel.player.positionMs,
                        durationMs = viewModel.player.durationMs,
                        shuffle = state.shuffle,
                        repeatMode = state.repeatMode,
                        sleepLabel = state.sleepLabel,
                        playbackRate = state.playbackRate,
                        volume = viewModel.player.deviceVolume,
                        visualizerData = viewModel.player.visualizerData,
                        visualizerMode = state.visualizerMode,
                        visualizerEnabled = state.visualizerEnabled,
                        visualizerColorHue = state.visualizerColorHue,
                        lightBackground = state.fullPlayerLightBackground,
                        playlists = manualPlaylists,
                        fallbackCoverPath = playbackSourceCoverPath,
                        onClose = { fullPlayerOpen = false },
                        onSetLightBackground = { v -> viewModel.setFullPlayerLightBackground(v) },
                        onTogglePlay = viewModel::togglePlay,
                        onToggleLike = { state.currentTrack?.let { viewModel.toggleLike(it) } },
                        onNext = viewModel::next,
                        onPrev = viewModel::prev,
                        onSeek = { ms -> viewModel.seekTo(ms) },
                        onToggleShuffle = viewModel::toggleShuffle,
                        onCycleRepeatMode = viewModel::cycleRepeatMode,
                        onSetPlaybackSpeed = { rate -> viewModel.setPlaybackSpeed(rate) },
                        onSetVolume = { v -> viewModel.setVolume(v) },
                        onSetSleepTimer = { min -> viewModel.setSleepTimer(min) },
                        onSetSleepTimerEndOfTrack = { viewModel.setSleepTimerEndOfTrack() },
                        onCancelSleepTimer = { viewModel.cancelSleepTimer() },
                        onAddToPlaylist = { playlistId -> state.currentTrack?.let { viewModel.addToPlaylist(playlistId, it) } },
                        onOpenQueue = { queueOpen = true }
                    )
                }
            }
        }

        if (queueOpen) {
            val queue by viewModel.player.queue.collectAsState()
            com.groovebox.app.ui.components.QueueSheet(
                queue = queue,
                currentTrackUri = state.currentTrack?.uri,
                sheetState = queueSheetState,
                onDismiss = { queueOpen = false },
                onTrackClick = { index -> viewModel.player.skipToQueueItem(index) },
                onRemove = { index -> viewModel.player.removeFromQueue(index) }
            )
        }
    }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = GbColors.AccentPurple,
    selectedTextColor = GbColors.AccentPurple,
    unselectedIconColor = GbColors.Text3,
    unselectedTextColor = GbColors.Text3,
    indicatorColor = GbColors.GlassFill2
)
