package com.groovebox.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.groovebox.app.data.EqPresets
import com.groovebox.app.data.LibraryRepository
import com.groovebox.app.data.LinkedFolderEntity
import com.groovebox.app.data.PlaylistEntity
import com.groovebox.app.data.SettingsStore
import com.groovebox.app.data.TrackEntity
import com.groovebox.app.playback.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class LibraryView { ALL, LIKED, PLAYLIST }

data class UiState(
    val allTracks: List<TrackEntity> = emptyList(),
    val visibleTracks: List<TrackEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val linkedFolders: List<LinkedFolderEntity> = emptyList(),
    val view: LibraryView = LibraryView.ALL,
    val activePlaylistId: String? = null,
    val searchQuery: String = "",
    val sortMode: String = "name",
    val currentTrack: TrackEntity? = null,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = androidx.media3.common.Player.REPEAT_MODE_OFF,
    val sleepLabel: String = "Off",
    val playbackRate: Float = 1f,
    val eqPreset: String = "Flat",
    val autoplayOnLaunch: Boolean = false,
    val themeId: String = "midnight_violet",
    val visualizerMode: String = "bars",
    val visualizerEnabled: Boolean = true,
    val visualizerColorHue: Float = -1f,
    val fullPlayerLightBackground: Boolean = false,
    val resumeOnHeadset: Boolean = false,
    val resumeOnBluetooth: Boolean = false,
    val pauseResumeOnVolume: Boolean = false,
    val eqCustomBands: List<Int> = List(EqPresets.FREQS.size) { 0 },
    val onboardingComplete: Boolean? = null, // null = not loaded yet, don't flash onboarding
    // The playlist the CURRENTLY PLAYING queue was started from, if any — independent
    // of whatever the user is browsing right now (they may have since switched tabs).
    // Used to fall back to that playlist's cover for tracks with no embedded art of
    // their own, in the mini player and Full Player.
    val playbackSourcePlaylistId: String? = null
)

class GrooveBoxViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LibraryRepository(app)
    val settings = SettingsStore(app)
    val player = PlayerController(app, viewModelScope)

    private val _view = MutableStateFlow(LibraryView.ALL)
    private val _activePlaylistId = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    // Switches to the active playlist's track URIs whenever _activePlaylistId
    // changes; empty when no playlist is open (ALL/LIKED views).
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val activePlaylistTrackUris = _activePlaylistId.flatMapLatest { id ->
        if (id == null) flowOf(emptySet()) else repo.observePlaylistTracks(id).map { refs -> refs.map { it.trackUri }.toSet() }
    }

    init {
        player.connect { }

        viewModelScope.launch {
            data class Base(val tracks: List<TrackEntity>, val playlists: List<PlaylistEntity>, val folders: List<LinkedFolderEntity>)
            val base = combine(repo.observeTracks(), repo.observePlaylists(), repo.observeLinkedFolders()) { t, p, f -> Base(t, p, f) }

            data class Filtered(val base: Base, val tracks: List<TrackEntity>, val sortMode: String)

            combine(base, _view, _searchQuery, activePlaylistTrackUris, settings.sortMode) { b, view, query, playlistUris, sortMode ->
                // A folder-backed playlist (see LibraryRepository.createFolderPlaylist)
                // has no rows in playlist_tracks at all — its membership is always
                // "whatever's currently in that folder", read straight off
                // TrackEntity.folderUri so it never needs re-syncing after a rescan.
                val activePlaylist = b.playlists.find { it.id == _activePlaylistId.value }
                val filtered = when (view) {
                    LibraryView.ALL -> b.tracks
                    LibraryView.LIKED -> b.tracks.filter { it.likes > 0 }
                    LibraryView.PLAYLIST -> {
                        val folderUri = activePlaylist?.folderUri
                        if (folderUri != null) b.tracks.filter { it.folderUri == folderUri }
                        else b.tracks.filter { it.uri in playlistUris }
                    }
                }
                val searched = if (query.isBlank()) filtered else filtered.filter {
                    it.name.contains(query, true) || it.artist.contains(query, true)
                }
                val sorted = when (sortMode) {
                    "artist" -> searched.sortedBy { it.artist.lowercase() }
                    "recent" -> searched.sortedByDescending { it.addedAt }
                    "duration" -> searched.sortedByDescending { it.durationMs }
                    else -> searched.sortedBy { it.name.lowercase() }
                }
                Filtered(b, sorted, sortMode)
            }.collect { (b, sorted, sortMode) ->
                _uiState.value = _uiState.value.copy(
                    allTracks = b.tracks,
                    visibleTracks = sorted,
                    playlists = b.playlists,
                    linkedFolders = b.folders,
                    view = _view.value,
                    activePlaylistId = _activePlaylistId.value,
                    searchQuery = _searchQuery.value,
                    sortMode = sortMode
                )
            }
        }

        viewModelScope.launch {
            player.isPlaying.collect { playing -> _uiState.value = _uiState.value.copy(isPlaying = playing) }
        }
        viewModelScope.launch {
            player.currentTrackUri.collect { uri ->
                val track = uri?.let { u -> _uiState.value.allTracks.find { it.uri == u } } ?: player.trackForUri(uri)
                _uiState.value = _uiState.value.copy(currentTrack = track)
            }
        }
        // positionMs, durationMs and visualizerData are deliberately NOT folded into
        // UiState: position ticks every 300ms and visualizer data ~20-30x/sec during
        // ALL playback, and UiState is collected once at the root (GrooveBoxRoot) —
        // bundling either in there would force the entire screen tree (192-track list
        // included) to recompose continuously any time something was playing. The few
        // composables that actually need them (mini bar, full player) collect these
        // StateFlows directly instead, so only those recompose on each tick.
        viewModelScope.launch {
            var lastSaveAt = 0L
            player.positionMs.collect { p ->
                val now = System.currentTimeMillis()
                if (now - lastSaveAt > 5000) { lastSaveAt = now; saveLastPlayed(p) }
            }
        }
        viewModelScope.launch {
            player.shuffle.collect { s -> _uiState.value = _uiState.value.copy(shuffle = s) }
        }
        viewModelScope.launch {
            player.repeatMode.collect { r -> _uiState.value = _uiState.value.copy(repeatMode = r) }
        }
        viewModelScope.launch {
            player.sleepLabel.collect { s -> _uiState.value = _uiState.value.copy(sleepLabel = s) }
        }
        viewModelScope.launch {
            settings.playbackRate.collect { r ->
                player.setPlaybackSpeed(r)
                _uiState.value = _uiState.value.copy(playbackRate = r)
            }
        }
        viewModelScope.launch {
            settings.eqPreset.collect { name ->
                if (name == "Custom") {
                    player.applyCustomGains(settings.getEqCustomBands())
                } else {
                    player.applyEqPreset(name)
                }
                _uiState.value = _uiState.value.copy(eqPreset = name)
            }
        }
        // No longer restoring a saved app-level volume on launch — the Full Player's
        // slider now controls the real phone media volume (STREAM_MUSIC), which the
        // system already persists and restores on its own.
        viewModelScope.launch {
            settings.autoplayOnLaunch.collect { v -> _uiState.value = _uiState.value.copy(autoplayOnLaunch = v) }
        }
        viewModelScope.launch {
            settings.onboardingComplete.collect { v -> _uiState.value = _uiState.value.copy(onboardingComplete = v) }
        }
        viewModelScope.launch {
            settings.themeId.collect { id ->
                com.groovebox.app.ui.theme.GbColors.applyTheme(com.groovebox.app.ui.theme.GbThemes.byId(id))
                _uiState.value = _uiState.value.copy(themeId = id)
            }
        }
        viewModelScope.launch {
            settings.visualizerMode.collect { id -> _uiState.value = _uiState.value.copy(visualizerMode = id) }
        }
        viewModelScope.launch {
            settings.visualizerEnabled.collect { v -> _uiState.value = _uiState.value.copy(visualizerEnabled = v) }
        }
        viewModelScope.launch {
            settings.visualizerColorHue.collect { v -> _uiState.value = _uiState.value.copy(visualizerColorHue = v) }
        }
        viewModelScope.launch {
            settings.fullPlayerLightBackground.collect { v -> _uiState.value = _uiState.value.copy(fullPlayerLightBackground = v) }
        }
        // Mirrored into a plain static flag (see MediaEventsReceiver) because that
        // receiver runs outside any coroutine scope and needs a synchronous read.
        viewModelScope.launch {
            settings.resumeOnHeadset.collect { v ->
                com.groovebox.app.playback.MediaEventsPrefs.resumeOnHeadset = v
                _uiState.value = _uiState.value.copy(resumeOnHeadset = v)
            }
        }
        viewModelScope.launch {
            settings.resumeOnBluetooth.collect { v ->
                com.groovebox.app.playback.MediaEventsPrefs.resumeOnBluetooth = v
                _uiState.value = _uiState.value.copy(resumeOnBluetooth = v)
            }
        }
        viewModelScope.launch {
            settings.pauseResumeOnVolume.collect { v ->
                com.groovebox.app.playback.MediaEventsPrefs.pauseResumeOnVolume = v
                _uiState.value = _uiState.value.copy(pauseResumeOnVolume = v)
            }
        }
        viewModelScope.launch {
            val bands = settings.getEqCustomBands()
            _uiState.value = _uiState.value.copy(eqCustomBands = bands.toList())
        }

        // Resume last played (only if the user enabled it in Settings) — waits
        // for the first non-empty library load so the saved track can actually
        // be found, then seeks to the saved position and starts playing.
        viewModelScope.launch {
            if (!settings.autoplayOnLaunch.first()) return@launch
            val last = settings.getLastPlayed() ?: return@launch
            val tracks = uiState.first { it.allTracks.isNotEmpty() }.allTracks
            val track = tracks.find { it.uri == last.first } ?: return@launch
            player.playFrom(listOf(track), 0)
            player.seekTo(last.second)
        }
    }

    /** Called periodically (throttled to every few seconds while playing) so a killed app can resume where it left off. */
    fun saveLastPlayed(positionMs: Long) = viewModelScope.launch {
        val t = uiState.value.currentTrack ?: return@launch
        settings.setLastPlayed(t.uri, positionMs)
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun setSortMode(mode: String) = viewModelScope.launch { settings.setSortMode(mode) }
    fun setView(v: LibraryView, playlistId: String? = null) {
        _view.value = v; _activePlaylistId.value = playlistId
        _uiState.value = _uiState.value.copy(view = v, activePlaylistId = playlistId)
    }

    /**
     * [sourcePlaylistId] records which playlist (if any) this queue was started from,
     * so the mini player/Full Player can fall back to that playlist's custom cover for
     * a track with no embedded art of its own — explicit per call site rather than
     * inferred from the ViewModel's current browsing state, since that state can be
     * stale by the time a track is tapped (e.g. Home's shelves are never
     * playlist-scoped even if the user was last browsing one in the Library tab).
     */
    fun playTrack(track: TrackEntity, sourcePlaylistId: String? = null) {
        val list = _uiState.value.visibleTracks
        val idx = list.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0)
        _uiState.value = _uiState.value.copy(playbackSourcePlaylistId = sourcePlaylistId)
        player.playFrom(list, idx)
    }

    fun togglePlay() = player.togglePlay()
    fun next() = player.next()
    fun prev() = player.prev()
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeatMode() = player.cycleRepeatMode()
    fun playNext(track: TrackEntity) = player.playNext(track)
    fun addToQueue(track: TrackEntity) = player.addToQueue(track)

    /**
     * Flips the like state in the UI immediately instead of waiting for the DB write to
     * round-trip through Room's Flow invalidation and the full filter/sort/combine
     * recompute over the whole library — that pipeline visibly lagged (several seconds)
     * under real-world load, and there's no reason to make the user wait to see a toggle
     * this reversible take effect. The eventual Flow re-emission just confirms the same
     * value, so there's no flicker.
     */
    fun toggleLike(track: TrackEntity) {
        val newLikes = if (track.likes > 0) 0 else 1
        _uiState.value = _uiState.value.let { s ->
            s.copy(
                allTracks = s.allTracks.map { if (it.uri == track.uri) it.copy(likes = newLikes) else it },
                visibleTracks = s.visibleTracks.map { if (it.uri == track.uri) it.copy(likes = newLikes) else it },
                currentTrack = if (s.currentTrack?.uri == track.uri) s.currentTrack.copy(likes = newLikes) else s.currentTrack
            )
        }
        viewModelScope.launch { repo.toggleLike(track.uri, track.likes) }
    }
    fun deleteTrack(track: TrackEntity) = viewModelScope.launch { repo.deleteTrack(track) }

    fun linkFolder(uri: Uri) = viewModelScope.launch { repo.linkFolder(uri) }
    fun unlinkFolder(folderUri: String) = viewModelScope.launch { repo.unlinkFolder(folderUri) }
    fun rescanAll() = viewModelScope.launch { repo.rescanAll() }

    fun createPlaylist(name: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        val id = repo.createPlaylist(name); onCreated(id)
    }
    fun deletePlaylist(id: String) = viewModelScope.launch { repo.deletePlaylist(id) }
    fun addToPlaylist(playlistId: String, track: TrackEntity) = viewModelScope.launch { repo.addToPlaylist(playlistId, track.uri) }
    fun renamePlaylist(id: String, name: String) = viewModelScope.launch { repo.renamePlaylist(id, name) }
    fun setPlaylistCoverArt(playlistId: String, uri: Uri) = viewModelScope.launch { repo.setPlaylistCoverArt(playlistId, uri) }
    fun clearPlaylistCoverArt(playlistId: String) = viewModelScope.launch { repo.clearPlaylistCoverArt(playlistId) }
    fun createFolderPlaylist(uri: Uri, onCreated: (String) -> Unit = {}) = viewModelScope.launch {
        val id = repo.createFolderPlaylist(uri); onCreated(id)
    }

    fun setPlaybackSpeed(rate: Float) = viewModelScope.launch { settings.setPlaybackRate(rate) }
    fun setEqPreset(name: String) = viewModelScope.launch { settings.setEqPreset(name) }
    fun setEqBand(index: Int, gainDb: Int) = player.setEqBand(index, gainDb)
    fun setVolume(v: Float) = player.setDeviceVolume(v)

    fun setSleepTimer(minutes: Int) = player.setSleepTimerMinutes(minutes)
    fun setSleepTimerEndOfTrack() = player.setSleepTimerEndOfTrack()
    fun cancelSleepTimer() = player.cancelSleepTimer()

    fun setAutoplayOnLaunch(v: Boolean) = viewModelScope.launch { settings.setAutoplayOnLaunch(v) }
    fun setThemeId(id: String) = viewModelScope.launch { settings.setThemeId(id) }
    fun completeOnboarding() = viewModelScope.launch { settings.setOnboardingComplete(true) }
    fun setVisualizerMode(id: String) = viewModelScope.launch { settings.setVisualizerMode(id) }
    fun setVisualizerEnabled(v: Boolean) = viewModelScope.launch { settings.setVisualizerEnabled(v) }
    fun setVisualizerColorHue(v: Float) = viewModelScope.launch { settings.setVisualizerColorHue(v) }
    fun setFullPlayerLightBackground(v: Boolean) = viewModelScope.launch { settings.setFullPlayerLightBackground(v) }
    fun setResumeOnHeadset(v: Boolean) = viewModelScope.launch { settings.setResumeOnHeadset(v) }
    fun setResumeOnBluetooth(v: Boolean) = viewModelScope.launch { settings.setResumeOnBluetooth(v) }
    fun setPauseResumeOnVolume(v: Boolean) = viewModelScope.launch { settings.setPauseResumeOnVolume(v) }

    /** Sets one band's gain from the Manual EQ screen — switches the active preset to
     *  "Custom" and persists the full curve so it can be re-applied after a fresh
     *  Equalizer attach (app restart, first playback of a session). */
    fun setEqCustomBand(index: Int, gainDb: Int) = viewModelScope.launch {
        // If a named preset was active, seed the custom curve from ITS values first —
        // otherwise the very first slider drag would silently snap every other band
        // back to whatever custom curve (often all-zero) was last saved, undoing the
        // preset the sliders were just visually showing.
        val base = EqPresets.PRESETS[_uiState.value.eqPreset]?.toList() ?: _uiState.value.eqCustomBands
        val updated = base.toMutableList().apply { this[index] = gainDb }
        _uiState.value = _uiState.value.copy(eqCustomBands = updated, eqPreset = "Custom")
        settings.setEqCustomBands(updated.toIntArray())
        settings.setEqPreset("Custom")
        player.applyCustomGains(updated.toIntArray())
    }
}
