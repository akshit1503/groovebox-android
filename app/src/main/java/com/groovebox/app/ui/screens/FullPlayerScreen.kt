package com.groovebox.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.groovebox.app.data.EqPresets
import com.groovebox.app.data.PlaylistEntity
import com.groovebox.app.data.TrackEntity
import com.groovebox.app.ui.components.AlbumArtImage
import com.groovebox.app.ui.components.BarVisualizer
import com.groovebox.app.ui.components.FrostSurface
import com.groovebox.app.ui.components.GlassDropdownMenu
import com.groovebox.app.ui.components.GlassPanel
import com.groovebox.app.ui.theme.GbColors
import kotlinx.coroutines.flow.StateFlow

private fun fmt(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60; val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

/**
 * positionMs/durationMs/visualizerData are collected here (not passed as plain
 * values) so only this screen recomposes on each tick — see the note in
 * GrooveBoxViewModel about why those never live in the shared UiState.
 */
@Composable
fun FullPlayerScreen(
    track: TrackEntity?,
    isPlaying: Boolean,
    isLiked: Boolean,
    positionMs: StateFlow<Long>,
    durationMs: StateFlow<Long>,
    shuffle: Boolean,
    repeatMode: Int,
    sleepLabel: String,
    playbackRate: Float,
    volume: StateFlow<Float>,
    visualizerData: StateFlow<FloatArray>,
    visualizerMode: String,
    visualizerEnabled: Boolean,
    visualizerColorHue: Float,
    lightBackground: Boolean,
    playlists: List<PlaylistEntity>,
    fallbackCoverPath: String? = null,
    onClose: () -> Unit,
    onSetLightBackground: (Boolean) -> Unit,
    onTogglePlay: () -> Unit,
    onToggleLike: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetVolume: (Float) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onSetSleepTimerEndOfTrack: () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onOpenQueue: () -> Unit
) {
    val positionMsValue by positionMs.collectAsState()
    val durationMsValue by durationMs.collectAsState()
    val volumeValue by volume.collectAsState()
    val visualizerDataValue by visualizerData.collectAsState()

    // Full resolution (not the 512px default used for list thumbnails) — this is
    // the one large, single-instance art view on screen, so it can afford a much
    // higher-res decode instead of visibly blurring when a small thumbnail bitmap
    // gets stretched to fill this whole panel.
    val art = com.groovebox.app.ui.components.rememberAlbumArtWithFallback(track?.uri, fallbackCoverPath, targetSizePx = 1024)

    val dominantColor = if (lightBackground) com.groovebox.app.ui.components.rememberDominantColor(art) else null

    // A per-screen light/dark override independent of the app's own theme system —
    // this only flips how the Full Player's own background and text read, not the
    // rest of the app. Icons/buttons/sliders always keep the app's fixed purple/pink
    // accent regardless of mode — only the light background's white gets a faint
    // tint of the current track's album art, not the controls.
    val textPrimary = if (lightBackground) Color(0xFF1A1625) else GbColors.Text1
    val textSecondary = if (lightBackground) Color(0xFF4A4458) else GbColors.Text2
    val textTertiary = if (lightBackground) Color(0xFF7A7488) else GbColors.Text3
    val sliderTrackColor = if (lightBackground) Color(0x33000000) else GbColors.GlassBorder
    val artPanelTint = if (lightBackground) Color(0x14000000) else GbColors.GlassFill2
    val artPanelBorder = if (lightBackground) Color(0x24000000) else GbColors.GlassHi
    val accent1 = GbColors.AccentPurple
    val accent2 = GbColors.AccentPink

    Box(modifier = Modifier.fillMaxSize()) {
        if (lightBackground) {
            // A gentle tint of the album's own color mixed into white — not a full
            // saturated gradient, just enough that the background doesn't feel like
            // a completely generic blank page.
            val bg = dominantColor?.let { blend(Color.White, it, 0.10f) } ?: Color(0xFFFFFFFF)
            Box(
                modifier = Modifier.fillMaxSize().background(bg)
            )
        }
        FullPlayerContent(
            track = track, isPlaying = isPlaying, isLiked = isLiked,
            positionMsValue = positionMsValue, durationMsValue = durationMsValue,
            shuffle = shuffle, repeatMode = repeatMode, sleepLabel = sleepLabel,
            playbackRate = playbackRate, volumeValue = volumeValue,
            visualizerDataValue = visualizerDataValue, visualizerMode = visualizerMode,
            visualizerEnabled = visualizerEnabled, visualizerColorHue = visualizerColorHue,
            lightBackground = lightBackground, playlists = playlists, art = art,
            textPrimary = textPrimary, textSecondary = textSecondary, textTertiary = textTertiary,
            sliderTrackColor = sliderTrackColor, artPanelTint = artPanelTint, artPanelBorder = artPanelBorder,
            accent1 = accent1, accent2 = accent2,
            onClose = onClose, onTogglePlay = onTogglePlay, onToggleLike = onToggleLike,
            onNext = onNext, onPrev = onPrev, onSeek = onSeek, onToggleShuffle = onToggleShuffle,
            onCycleRepeatMode = onCycleRepeatMode, onSetPlaybackSpeed = onSetPlaybackSpeed,
            onSetVolume = onSetVolume, onSetSleepTimer = onSetSleepTimer,
            onSetSleepTimerEndOfTrack = onSetSleepTimerEndOfTrack, onCancelSleepTimer = onCancelSleepTimer,
            onAddToPlaylist = onAddToPlaylist, onOpenQueue = onOpenQueue,
            onSetLightBackground = onSetLightBackground
        )
    }
}

private fun blend(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FullPlayerContent(
    track: TrackEntity?,
    isPlaying: Boolean,
    isLiked: Boolean,
    positionMsValue: Long,
    durationMsValue: Long,
    shuffle: Boolean,
    repeatMode: Int,
    sleepLabel: String,
    playbackRate: Float,
    volumeValue: Float,
    visualizerDataValue: FloatArray,
    visualizerMode: String,
    visualizerEnabled: Boolean,
    visualizerColorHue: Float,
    lightBackground: Boolean,
    playlists: List<PlaylistEntity>,
    art: androidx.compose.ui.graphics.ImageBitmap?,
    textPrimary: Color,
    textSecondary: Color,
    textTertiary: Color,
    sliderTrackColor: Color,
    artPanelTint: Color,
    artPanelBorder: Color,
    accent1: Color,
    accent2: Color,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleLike: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetVolume: (Float) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onSetSleepTimerEndOfTrack: () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onOpenQueue: () -> Unit,
    onSetLightBackground: (Boolean) -> Unit
) {
    var overflowOpen by remember { mutableStateOf(false) }

    // The real position only arrives in ~100ms ticks from the player, which reads as
    // a visible micro-stutter on a slider drawn every frame. This interpolates the
    // displayed position smoothly between ticks using actual elapsed frame time
    // while playing, then snaps back in sync on the next real tick — the same trick
    // most polished players (PixelPlay included) use for a seekbar that glides
    // instead of hopping every 100ms.
    var visualPositionMs by remember { mutableStateOf(positionMsValue) }
    LaunchedEffect(positionMsValue) { visualPositionMs = positionMsValue }
    LaunchedEffect(isPlaying, playbackRate, durationMsValue) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val deltaMs = (frameNanos - lastFrameNanos) / 1_000_000L
            lastFrameNanos = frameNanos
            visualPositionMs = (visualPositionMs + (deltaMs * playbackRate).toLong())
                .coerceIn(0L, durationMsValue.coerceAtLeast(0L))
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textSecondary)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = textSecondary)
            }
            Box {
                IconButton(onClick = { overflowOpen = true }, enabled = track != null) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = textSecondary)
                }
                GlassDropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (lightBackground) "Dark background" else "Light background") },
                        leadingIcon = {
                            Icon(
                                if (lightBackground) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                                contentDescription = null
                            )
                        },
                        onClick = { onSetLightBackground(!lightBackground); overflowOpen = false }
                    )
                    if (playlists.isEmpty()) {
                        DropdownMenuItem(text = { Text("No playlists yet") }, onClick = {}, enabled = false)
                    } else {
                        playlists.forEach { pl ->
                            DropdownMenuItem(
                                text = { Text("Add to \"${pl.name}\"") },
                                leadingIcon = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) },
                                onClick = { onAddToPlaylist(pl.id); overflowOpen = false }
                            )
                        }
                    }
                }
            }
        }

        // Weighted spacers (rather than fixed dp gaps) around the major groups below,
        // so leftover vertical space on any given screen height distributes evenly
        // across all of them instead of piling into one big gap in a single spot.
        Spacer(Modifier.weight(0.7f))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    // Only cast a shadow when there's real art to ground — Android's
                    // shadow isn't drawn evenly on all sides (it follows a simulated
                    // light angle), so around the empty placeholder square it just
                    // reads as a stray, misaligned gray box rather than depth.
                    .then(
                        if (art != null) Modifier.shadow(
                            elevation = 24.dp,
                            shape = RectangleShape,
                            ambientColor = accent1.copy(alpha = 0.3f),
                            spotColor = accent1.copy(alpha = 0.3f)
                        ) else Modifier
                    ),
                shape = RectangleShape,
                tint = artPanelTint,
                borderColor = artPanelBorder
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (art != null) {
                        AlbumArtImage(art, modifier = Modifier.fillMaxSize())
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = textTertiary, modifier = Modifier.height(64.dp))
                        }
                    }
                    if (visualizerEnabled && isPlaying && visualizerDataValue.isNotEmpty()) {
                        // Scrim so the bars stay legible over bright album art.
                        if (art != null) {
                            Box(
                                modifier = Modifier.fillMaxSize().align(Alignment.BottomCenter).background(
                                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))
                                )
                            )
                        }
                        // Explicit width/height fractions (rather than padding on an
                        // otherwise-unbounded fillMaxSize) so the visualizer always
                        // occupies exactly the bottom half of the square, inset evenly
                        // on all sides — a predictable fit instead of one that shifts
                        // with whatever space padding happens to leave over.
                        BarVisualizer(
                            data = visualizerDataValue,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(0.86f)
                                .fillMaxHeight(0.5f)
                                .padding(bottom = 16.dp),
                            mode = com.groovebox.app.ui.components.VisualizerMode.byId(visualizerMode),
                            accentColor = com.groovebox.app.ui.components.visualizerHueToColor(visualizerColorHue)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(0.5f))
        Text(
            track?.name ?: "Nothing playing",
            color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp,
            modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            track?.artist?.ifBlank { "Unknown artist" } ?: "",
            color = textTertiary, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        // Format badge and like sit on one row, badge at the start and the heart at
        // the end — matches the sketch rather than two separate centered rows.
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (track != null) {
                val lossless = track.ext.lowercase() in setOf("flac", "wav", "aiff", "aif")
                val badgeColor = if (lossless) GbColors.AccentAmber else GbColors.AccentTeal
                Text(
                    track.ext.uppercase(),
                    color = badgeColor,
                    fontSize = 9.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier
                        .border(1.dp, badgeColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            FrostSurface(
                modifier = Modifier.size(36.dp).clickable(enabled = track != null, onClick = onToggleLike),
                shape = CircleShape,
                tint = if (isLiked) GbColors.AccentRed.copy(alpha = 0.28f) else GbColors.GlassBg
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) GbColors.AccentRed else textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Slider(
            value = if (durationMsValue > 0) (visualPositionMs.toFloat() / durationMsValue.toFloat()).coerceIn(0f, 1f) else 0f,
            onValueChange = { f ->
                // Update the visual position immediately on drag too, rather than
                // waiting for the next real tick to catch up — otherwise dragging
                // felt like it fought the frame-interpolation loop above.
                val ms = (f * durationMsValue).toLong()
                visualPositionMs = ms
                onSeek(ms)
            },
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = accent1,
                activeTrackColor = accent1,
                inactiveTrackColor = sliderTrackColor
            ),
            // Material3's newer default Slider (from the toolchain migration) draws a
            // much thicker "expressive" track with a gap/stop-dot — nothing like this
            // app's original thin bar. Rather than fight SliderDefaults' new sizing,
            // this draws the classic thin pill + small circular thumb directly, fully
            // independent of whatever Material3 changes to its own defaults next.
            track = { sliderState ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(sliderTrackColor)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(sliderState.value)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(accent1)
                    )
                }
            },
            thumb = {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(accent1)
                )
            }
        )
        // Slider reserves space for its thumb so it doesn't clip at the edges (the
        // track itself starts/ends inset by the thumb's own radius, not at the true
        // edges of the full-width row) — matching that inset here lines these labels
        // up with where the slider's ends actually are, not the outer container edges.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(fmt(visualPositionMs), color = textTertiary, fontSize = 11.sp)
            Text(fmt(durationMsValue), color = textTertiary, fontSize = 11.sp)
        }

        Spacer(Modifier.weight(0.6f))
        // Shuffle and repeat flank the transport controls on the same row — one
        // control row instead of splitting them off into the time row above.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleShuffle) {
                Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (shuffle) accent1 else textSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = textPrimary, modifier = Modifier.height(32.dp)) }
                Spacer(Modifier.width(20.dp))
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = CircleShape,
                            ambientColor = accent1.copy(alpha = 0.45f),
                            spotColor = accent1.copy(alpha = 0.45f)
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.55f),
                                    accent1.copy(alpha = 0.85f),
                                    accent2.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onTogglePlay, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause", tint = Color.White, modifier = Modifier.height(28.dp)
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = textPrimary, modifier = Modifier.height(32.dp)) }
            }
            IconButton(onClick = onCycleRepeatMode) {
                Icon(
                    if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) accent1 else textSecondary
                )
            }
        }

        Spacer(Modifier.weight(0.4f))
        // Remembers the last non-zero level so a second tap on the icon un-mutes back
        // to where the user had it, instead of always resetting to some fixed value.
        var lastNonZeroVolume by remember { mutableStateOf(if (volumeValue > 0f) volumeValue else 0.5f) }
        if (volumeValue > 0f) lastNonZeroVolume = volumeValue
        // The device only has volumeSteps discrete levels, so snapping the Slider's
        // own thumb to those `steps` made it hop under the finger while dragging.
        // This tracks a continuous value while the user is actively dragging (so the
        // thumb glides smoothly), and only re-syncs to the real quantized device
        // value once the finger lifts and a fresh reading comes back.
        var visualVolume by remember { mutableStateOf(volumeValue) }
        var draggingVolume by remember { mutableStateOf(false) }
        LaunchedEffect(volumeValue) { if (!draggingVolume) visualVolume = volumeValue }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onSetVolume(if (volumeValue > 0f) 0f else lastNonZeroVolume) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (volumeValue <= 0f) Icons.AutoMirrored.Filled.VolumeOff else if (volumeValue < 0.5f) Icons.AutoMirrored.Filled.VolumeDown else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (volumeValue > 0f) "Mute" else "Unmute",
                    tint = textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Slider(
                value = visualVolume,
                onValueChange = { v -> draggingVolume = true; visualVolume = v; onSetVolume(v) },
                onValueChangeFinished = { draggingVolume = false },
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = accent1,
                    activeTrackColor = accent1,
                    inactiveTrackColor = sliderTrackColor
                ),
                track = { sliderState ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(sliderTrackColor)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(sliderState.value)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent1)
                        )
                    }
                },
                thumb = {
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(accent1)
                    )
                }
            )
        }
        Spacer(Modifier.weight(0.4f))
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            tint = if (lightBackground) accent1.copy(alpha = 0.14f) else GbColors.TintPurpleBg,
            borderColor = if (lightBackground) accent1.copy(alpha = 0.3f) else GbColors.TintPurpleBorder
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SpeedButton(
                    playbackRate = playbackRate, onSetPlaybackSpeed = onSetPlaybackSpeed,
                    textPrimary = textPrimary, textTertiary = textTertiary,
                    modifier = Modifier.weight(1f)
                )
                SleepButton(
                    sleepLabel = sleepLabel,
                    onSetSleepTimer = onSetSleepTimer,
                    onSetEndOfTrack = onSetSleepTimerEndOfTrack,
                    onCancel = onCancelSleepTimer,
                    textPrimary = textPrimary, textTertiary = textTertiary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SpeedButton(
    playbackRate: Float,
    onSetPlaybackSpeed: (Float) -> Unit,
    textPrimary: Color,
    textTertiary: Color,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    Box(modifier = modifier) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = true },
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SPEED", color = textTertiary, fontSize = 9.sp)
                val label = if (playbackRate == playbackRate.toInt().toFloat()) "${playbackRate.toInt()}×" else "${playbackRate}×"
                Text(label, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            speeds.forEach { s ->
                val checked = if (kotlin.math.abs(playbackRate - s) < 0.001f) "  ✓" else ""
                DropdownMenuItem(
                    text = { Text((if (s == 1f) "1× (Normal)" else "${s}×") + checked) },
                    onClick = { onSetPlaybackSpeed(s); open = false }
                )
            }
        }
    }
}

@Composable
private fun SleepButton(
    sleepLabel: String,
    onSetSleepTimer: (Int) -> Unit,
    onSetEndOfTrack: () -> Unit,
    onCancel: () -> Unit,
    textPrimary: Color,
    textTertiary: Color,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = true },
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SLEEP TIMER", color = textTertiary, fontSize = 9.sp)
                Text(sleepLabel, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(15, 30, 45, 60).forEach { m ->
                DropdownMenuItem(text = { Text("$m min") }, onClick = { onSetSleepTimer(m); open = false })
            }
            DropdownMenuItem(text = { Text("End of current track") }, onClick = { onSetEndOfTrack(); open = false })
            if (sleepLabel != "Off") {
                DropdownMenuItem(text = { Text("✕ Turn off") }, onClick = { onCancel(); open = false })
            }
        }
    }
}
