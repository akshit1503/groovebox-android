package com.groovebox.app.playback

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.groovebox.app.data.EqPresets
import com.groovebox.app.data.TrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The single point the UI talks to for playback. Wraps a MediaController
 * bound to PlaybackService (so playback survives the UI going away) and
 * uses ExoPlayer's own playlist/shuffle/repeat instead of reimplementing
 * queue logic by hand.
 */
class PlayerController(private val context: Context, private val scope: CoroutineScope) {
    private var controller: MediaController? = null
    private var equalizer: Equalizer? = null
    private var visualizer: Visualizer? = null
    private var sleepJob: Job? = null
    private val smoothedBars = FloatArray(24)

    // The real Android media (STREAM_MUSIC) volume — the same thing the hardware
    // volume keys and every other app's slider control — not ExoPlayer's own
    // per-instance attenuation, which only ever scaled this app's own output and
    // didn't move the actual phone volume the user asked the slider to control.
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxStreamVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    private val _deviceVolume = MutableStateFlow(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxStreamVolume)
    val deviceVolume: StateFlow<Float> = _deviceVolume

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentTrackUri = MutableStateFlow<String?>(null)
    val currentTrackUri: StateFlow<String?> = _currentTrackUri

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle

    // Player.REPEAT_MODE_OFF / REPEAT_MODE_ALL / REPEAT_MODE_ONE
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode

    private val _sleepLabel = MutableStateFlow("Off")
    val sleepLabel: StateFlow<String> = _sleepLabel

    private val _visualizerData = MutableStateFlow(FloatArray(0))
    val visualizerData: StateFlow<FloatArray> = _visualizerData

    // Current track plus everything after it, in actual playback order (shuffle-aware,
    // walked via Timeline.getNextWindowIndex rather than the raw item list order).
    private val _queue = MutableStateFlow<List<TrackEntity>>(emptyList())
    val queue: StateFlow<List<TrackEntity>> = _queue

    // The real timeline window index behind each entry in `_queue`, same order/length.
    // The same track can legitimately appear more than once in a queue, so looking an
    // item back up by its uri (as skipToQueueItem/removeFromQueue used to) would always
    // hit the first match — wrong item if the user tapped a later duplicate. Queue
    // actions now take the queue's own position and resolve it through this instead.
    private var queueWindowIndices: List<Int> = emptyList()

    // Real per-band info reported by the device's Equalizer effect once it's attached
    // (attach only happens once playback actually starts, since it needs a live audio
    // session id) — lets the Manual EQ screen show accurate center frequencies and gain
    // range for this specific device instead of guessing. Empty until first attach.
    data class EqBandInfo(val index: Int, val centerFreqHz: Int, val minDb: Int, val maxDb: Int)
    private val _eqBandInfo = MutableStateFlow<List<EqBandInfo>>(emptyList())
    val eqBandInfo: StateFlow<List<EqBandInfo>> = _eqBandInfo

    private var lastEqGains: IntArray? = null

    private var sleepEndOfTrack = false
    private var uriToTrack: Map<String, TrackEntity> = emptyMap()

    fun connect(onReady: () -> Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            controller = future.get()
            attachListener()
            startPositionTicker()
            onReady()
        }, MoreExecutors.directExecutor())
    }

    private fun attachListener() {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) ensureEffectsAttached()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentTrackUri.value = mediaItem?.mediaId
                _durationMs.value = controller?.duration?.coerceAtLeast(0) ?: 0L
                if (sleepEndOfTrack && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    controller?.pause()
                    cancelSleepTimer()
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = controller?.duration?.coerceAtLeast(0) ?: 0L
                }
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffle.value = shuffleModeEnabled
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }
            // Fires on every playlist mutation (add/remove/move item, shuffle order
            // change) as well as media item transitions — the one place to refresh
            // the queue from, rather than scattering refreshQueue() calls after
            // every mutating method.
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                refreshQueue()
            }
        })
    }

    private fun refreshQueue() {
        val c = controller ?: return
        val timeline = c.currentTimeline
        if (timeline.isEmpty) { _queue.value = emptyList(); queueWindowIndices = emptyList(); return }
        val tracks = mutableListOf<TrackEntity>()
        val windowIndices = mutableListOf<Int>()
        val window = Timeline.Window()
        // currentMediaItemIndex is C.INDEX_UNSET until something has actually been
        // played this session — e.g. "Add to queue" on an otherwise-empty player adds
        // a media item but never selects/plays it, so there's no "current" index to
        // walk from yet. Falling back to window 0 still lets that item show up in the
        // queue instead of silently rendering empty.
        val startIdx = c.currentMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: 0
        var idx = startIdx
        var guard = 0
        while (guard < timeline.windowCount) {
            val mediaItem = timeline.getWindow(idx, window).mediaItem
            uriToTrack[mediaItem.mediaId]?.let { tracks.add(it); windowIndices.add(idx) }
            guard++
            val nextIdx = timeline.getNextWindowIndex(idx, c.repeatMode, c.shuffleModeEnabled)
            if (nextIdx == C.INDEX_UNSET || nextIdx == startIdx) break
            idx = nextIdx
        }
        _queue.value = tracks
        queueWindowIndices = windowIndices
    }

    /** Jumps playback directly to a track already in the queue (tapped from the queue sheet),
     *  addressed by its position in [queue] rather than its uri — the same track can
     *  legitimately appear more than once, so a uri lookup could resolve to the wrong one. */
    fun skipToQueueItem(queuePosition: Int) {
        val c = controller ?: return
        val windowIndex = queueWindowIndices.getOrNull(queuePosition) ?: return
        c.seekTo(windowIndex, 0L)
    }

    /** Removes a track from the upcoming queue (not the currently playing one), addressed
     *  by its position in [queue] — see [skipToQueueItem] for why not by uri. */
    fun removeFromQueue(queuePosition: Int) {
        val c = controller ?: return
        val windowIndex = queueWindowIndices.getOrNull(queuePosition) ?: return
        if (windowIndex == c.currentMediaItemIndex) return
        c.removeMediaItem(windowIndex)
    }

    private fun startPositionTicker() {
        scope.launch {
            while (true) {
                controller?.let { _positionMs.value = it.currentPosition.coerceAtLeast(0) }
                // Also picks up volume changes made via the hardware keys or another
                // app while the Full Player's slider is on screen, same as position.
                val liveFraction = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxStreamVolume
                if (liveFraction != _deviceVolume.value) _deviceVolume.value = liveFraction
                // 100ms rather than the previous 300ms — the seek slider/time labels
                // visibly stepped instead of gliding at the slower rate.
                delay(100)
            }
        }
    }

    /** Sets the given ordered list as the playing queue and starts at startIndex — mirrors "play within this filtered/sorted view" from the desktop app. */
    fun playFrom(tracks: List<TrackEntity>, startIndex: Int) {
        uriToTrack = tracks.associateBy { it.uri }
        val items = tracks.map { it.toMediaItem() }
        controller?.apply {
            setMediaItems(items, startIndex, 0L)
            prepare()
            play()
        }
    }

    fun togglePlay() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() = controller?.seekToNextMediaItem()
    fun prev() = controller?.seekToPreviousMediaItem()
    fun seekTo(ms: Long) = controller?.seekTo(ms)

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    /** Cycles Off -> All -> One -> Off, the standard player convention. */
    fun cycleRepeatMode() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun playNext(track: TrackEntity) {
        val c = controller ?: return
        uriToTrack = uriToTrack + (track.uri to track)
        val insertAt = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
        c.addMediaItem(insertAt, track.toMediaItem())
    }

    fun addToQueue(track: TrackEntity) {
        val c = controller ?: return
        uriToTrack = uriToTrack + (track.uri to track)
        c.addMediaItem(track.toMediaItem())
    }

    /** Sets the actual phone media volume (STREAM_MUSIC), not ExoPlayer's own attenuation. */
    fun setDeviceVolume(fraction: Float) {
        val index = (fraction.coerceIn(0f, 1f) * maxStreamVolume).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
        _deviceVolume.value = index.toFloat() / maxStreamVolume
    }
    fun setPlaybackSpeed(v: Float) { controller?.setPlaybackSpeed(v) }

    fun trackForUri(uri: String?): TrackEntity? = uri?.let { uriToTrack[it] }

    // ── Equalizer ──────────────────────────────────────────────────
    private var attachedSessionId: Int? = null

    /** Called after RECORD_AUDIO is granted at runtime so a Visualizer attach that failed pre-permission gets retried on the current session. */
    fun retryEffectsAttach() {
        attachedSessionId = null
        ensureEffectsAttached()
    }

    /** Re-attaches Equalizer/Visualizer if the audio session changed (e.g. first playback, or after a service restart). */
    private fun ensureEffectsAttached() {
        val sessionId = PlaybackService.instance?.player?.audioSessionId
        android.util.Log.d("GrooveBoxFx", "ensureEffectsAttached: instance=${PlaybackService.instance}, sessionId=$sessionId, attached=$attachedSessionId")
        if (sessionId == null) return
        if (sessionId == 0 || sessionId == attachedSessionId) return
        attachedSessionId = sessionId
        attachAudioEffects(sessionId)
    }

    private fun attachAudioEffects(audioSessionId: Int) {
        try {
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
            val bands = equalizer?.numberOfBands?.toInt() ?: 0
            _eqBandInfo.value = (0 until bands).map { i ->
                val range = equalizer!!.getBandLevelRange()
                EqBandInfo(
                    index = i,
                    centerFreqHz = equalizer!!.getCenterFreq(i.toShort()) / 1000,
                    minDb = range[0] / 100,
                    maxDb = range[1] / 100
                )
            }
            // Re-apply whatever gain curve (preset or manual) was active before this
            // attach — e.g. after the app was killed and playback restarted, the
            // Equalizer instance is brand new and defaults to flat.
            lastEqGains?.let { gains -> gains.forEachIndexed { i, g -> setEqBand(i, g) } }
            android.util.Log.d("GrooveBoxFx", "Equalizer attached OK, bands=${equalizer?.numberOfBands}")
        } catch (e: Exception) { android.util.Log.e("GrooveBoxFx", "Equalizer attach failed", e) }
        try {
            visualizer?.release()
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        fft ?: return
                        val bars = 24
                        val totalBins = fft.size / 2
                        val out = FloatArray(bars)
                        // Log-scaled bin edges instead of equal-width linear ones: music energy
                        // concentrates at low frequencies, so a linear split left most bars
                        // (the upper spectrum) reading near-silent. A power curve gives the
                        // low/mid range more, narrower bars and compresses the sparse highs
                        // into fewer, wider ones — the shape any real visualizer uses.
                        for (b in 0 until bars) {
                            val loFrac = Math.pow(b.toDouble() / bars, 2.2)
                            val hiFrac = Math.pow((b + 1).toDouble() / bars, 2.2)
                            val loBin = (loFrac * totalBins).toInt().coerceIn(0, totalBins - 1)
                            val hiBin = (hiFrac * totalBins).toInt().coerceIn(loBin + 1, totalBins)
                            var sum = 0f
                            for (i in loBin until hiBin) {
                                val idx = i * 2
                                if (idx + 1 < fft.size) {
                                    val re = fft[idx].toFloat(); val im = fft[idx + 1].toFloat()
                                    sum += kotlin.math.sqrt(re * re + im * im)
                                }
                            }
                            val avg = sum / (hiBin - loBin)
                            // sqrt compression: keeps quiet bars visible instead of flatlining,
                            // without letting loud ones clip at the top.
                            val target = kotlin.math.sqrt((avg / 128f).coerceIn(0f, 1f))
                            // Exponential smoothing right here — cheap arithmetic on the thread
                            // that's already computing this, once per capture. This used to be
                            // done in Compose via 24 separate Animatable coroutines re-triggered
                            // on every capture (~20-30x/sec, so 500+ coroutine launches/sec) —
                            // that was the actual lag source, not the drawing itself.
                            smoothedBars[b] = smoothedBars[b] * 0.55f + target * 0.45f
                            out[b] = smoothedBars[b]
                        }
                        _visualizerData.value = out
                    }
                // Full device max capture rate, not halved: on this hardware that halving
                // was the real bottleneck (~10Hz updates read as visibly choppy/laggy),
                // not the Compose drawing — which is now cheap (one shared Brush, one
                // draw call per bar) and can easily keep up with the faster rate.
                }, Visualizer.getMaxCaptureRate(), false, true)
                enabled = true
            }
            android.util.Log.d("GrooveBoxFx", "Visualizer attached OK, captureSize=${visualizer?.captureSize}")
        } catch (e: Exception) { android.util.Log.e("GrooveBoxFx", "Visualizer attach failed", e) }
    }

    fun setEqBand(bandIndex: Int, gainDb: Int) {
        try {
            equalizer?.setBandLevel(bandIndex.toShort(), (gainDb * 100).toShort())
        } catch (e: Exception) { }
    }

    fun applyEqPreset(name: String) {
        val gains = EqPresets.PRESETS[name] ?: return
        lastEqGains = gains
        gains.forEachIndexed { i, g -> setEqBand(i, g) }
    }

    /** Applies a fully custom, per-band gain curve (Manual EQ screen) instead of a named preset. */
    fun applyCustomGains(gains: IntArray) {
        lastEqGains = gains
        gains.forEachIndexed { i, g -> setEqBand(i, g) }
    }

    // ── Sleep timer ────────────────────────────────────────────────
    fun setSleepTimerMinutes(minutes: Int) {
        cancelSleepTimer()
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            _sleepLabel.value = "Off"
        }
        _sleepLabel.value = "$minutes min"
    }

    fun setSleepTimerEndOfTrack() {
        cancelSleepTimer()
        sleepEndOfTrack = true
        _sleepLabel.value = "End of track"
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel(); sleepJob = null
        sleepEndOfTrack = false
        _sleepLabel.value = "Off"
    }

    private fun TrackEntity.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setUri(android.net.Uri.parse(uri))
            .setMediaId(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .build()
            )
            .build()
}
