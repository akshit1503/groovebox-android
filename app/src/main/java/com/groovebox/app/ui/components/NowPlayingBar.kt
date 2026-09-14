package com.groovebox.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groovebox.app.data.TrackEntity
import com.groovebox.app.ui.theme.GbColors
import kotlinx.coroutines.flow.StateFlow

/**
 * Compact by design — just enough to identify what's playing and like it. Tap it to
 * open the Full Player for actual playback controls (transport, seek, queue).
 * Collects positionMs/durationMs itself instead of receiving a precomputed progress
 * value, so only this composable recomposes on each ~300ms position tick — not
 * GrooveBoxRoot and everything under it (see the comment in GrooveBoxViewModel).
 */
@Composable
fun NowPlayingBar(
    track: TrackEntity?,
    isPlaying: Boolean,
    positionMs: StateFlow<Long>,
    durationMs: StateFlow<Long>,
    onTap: () -> Unit,
    onToggleLike: () -> Unit,
    fallbackCoverPath: String? = null,
    modifier: Modifier = Modifier
) {
    val pos by positionMs.collectAsState()
    val dur by durationMs.collectAsState()
    val progress = if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
    val isLiked = (track?.likes ?: 0) > 0
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(shape)
            .clickable(enabled = track != null, onClick = onTap)
    ) {
        // Genuinely translucent now that the screen's content extends full-height
        // behind this bar (see GrooveBoxRoot's Scaffold padding) — real backdrop
        // blur isn't reliably renderable on this device (confirmed broken across
        // two independent libraries), so this settles for honest alpha-blended
        // translucency: actual content color shows through, just not blurred.
        Box(modifier = Modifier.matchParentSize().background(GbColors.GlassOpaque.copy(alpha = 0.68f)))
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent), endY = 180f)
            )
        )
        Box(modifier = Modifier.matchParentSize().border(1.dp, GbColors.GlassBorder, shape))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopCenter)
                .background(GbColors.GlassHi)
        )

        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val art = rememberAlbumArtWithFallback(track?.uri, fallbackCoverPath)
                GlassPanel(modifier = Modifier.size(38.dp), shape = RoundedCornerShape(8.dp)) {
                    if (art != null) {
                        AlbumArtImage(art, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = GbColors.Text3, modifier = Modifier.padding(9.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        track?.name ?: "Nothing playing",
                        color = GbColors.Text1, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track?.artist?.ifBlank { "" } ?: "",
                        color = GbColors.Text3, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                GlassPanel(
                    modifier = Modifier.size(32.dp).clickable(enabled = track != null, onClick = onToggleLike),
                    shape = CircleShape,
                    tint = if (isLiked) GbColors.AccentRed.copy(alpha = 0.16f) else GbColors.GlassFill,
                    borderColor = if (isLiked) GbColors.AccentRed.copy(alpha = 0.4f) else GbColors.GlassBorder
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) GbColors.AccentRed else GbColors.Text2,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().size(2.dp),
                color = GbColors.AccentPurple,
                trackColor = Color.White.copy(alpha = 0.08f)
            )
        }
    }
}
