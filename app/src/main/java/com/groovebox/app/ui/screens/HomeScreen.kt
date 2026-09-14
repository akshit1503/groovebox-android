package com.groovebox.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groovebox.app.data.PlaylistEntity
import com.groovebox.app.data.TrackEntity
import com.groovebox.app.ui.components.AlbumArtImage
import com.groovebox.app.ui.components.GlassPanel
import com.groovebox.app.ui.components.rememberAlbumArt
import com.groovebox.app.ui.components.rememberFileImage
import com.groovebox.app.ui.theme.GbColors
import java.util.Calendar

@Composable
fun HomeScreen(
    allTracks: List<TrackEntity>,
    playlists: List<PlaylistEntity>,
    onOpenLiked: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenAllSongs: () -> Unit,
    onTrackClick: (TrackEntity) -> Unit,
    onShuffleAll: () -> Unit
) {
    val likedCount = allTracks.count { it.likes > 0 }
    val recentlyAdded = remember(allTracks) { allTracks.sortedByDescending { it.addedAt }.take(12) }
    val greeting = remember { greetingForNow() }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(greeting, color = GbColors.Text1, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            GlassPanel(modifier = Modifier.size(40.dp), shape = CircleShape) {
                Box(modifier = Modifier.fillMaxSize().clickable(onClick = onShuffleAll), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle all", tint = GbColors.AccentPurple, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Quick-access grid: the two things a returning listener reaches for first —
        // Liked Songs and whatever playlists already exist. A fixed 2-column grid
        // (rather than one row per item) is what keeps this readable once there are
        // many playlists, and matches the reference layout's compact quick tiles.
        if (allTracks.isNotEmpty() || playlists.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height(74.dp * ((playlists.size + 1) / 2 + 1).coerceAtMost(3)).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    QuickTile(
                        title = "Liked Songs",
                        subtitle = "$likedCount song${if (likedCount != 1) "s" else ""}",
                        icon = Icons.Filled.Favorite,
                        iconTint = GbColors.AccentRed,
                        onClick = onOpenLiked
                    )
                }
                items(playlists.take(5), key = { it.id }) { pl ->
                    QuickTile(
                        title = pl.name,
                        subtitle = "Playlist",
                        icon = Icons.Filled.QueueMusic,
                        iconTint = GbColors.AccentTeal,
                        onClick = { onOpenPlaylist(pl.id) }
                    )
                }
            }
        }

        if (recentlyAdded.isNotEmpty()) {
            ShelfHeader("Recently added", onSeeAll = onOpenAllSongs)
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(recentlyAdded, key = { it.uri }) { track ->
                    TrackCard(track = track, onClick = { onTrackClick(track) })
                }
            }
        }

        if (playlists.isNotEmpty()) {
            ShelfHeader("Your playlists", onSeeAll = null)
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(playlists, key = { it.id }) { pl ->
                    PlaylistCard(playlist = pl, onClick = { onOpenPlaylist(pl.id) })
                }
            }
        }

        if (allTracks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = GbColors.Text3, modifier = Modifier.size(40.dp))
                Text("Link a folder to start building your home page", color = GbColors.Text3, modifier = Modifier.padding(top = 12.dp))
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.height(com.groovebox.app.ui.theme.GbDimens.BottomBarClearance))
    }
}

private fun greetingForNow(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
private fun QuickTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth().height(64.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.fillMaxHeight().width(56.dp)
                    .background(Brush.linearGradient(listOf(iconTint.copy(alpha = 0.55f), iconTint.copy(alpha = 0.25f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(title, color = GbColors.Text1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = GbColors.Text3, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ShelfHeader(title: String, onSeeAll: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = GbColors.Text1, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (onSeeAll != null) {
            Text("See all", color = GbColors.AccentPurple, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onSeeAll))
        }
    }
}

@Composable
private fun TrackCard(track: TrackEntity, onClick: () -> Unit) {
    val art = rememberAlbumArt(track.uri, targetSizePx = 300)
    Column(modifier = Modifier.width(120.dp).clickable(onClick = onClick)) {
        GlassPanel(modifier = Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(10.dp), showSheen = false) {
            if (art != null) {
                AlbumArtImage(art, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = GbColors.Text3, modifier = Modifier.size(28.dp))
                }
            }
        }
        Text(
            track.name, color = GbColors.Text1, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            track.artist.ifBlank { "Unknown artist" }, color = GbColors.Text3, fontSize = 10.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaylistCard(playlist: PlaylistEntity, onClick: () -> Unit) {
    val cover = rememberFileImage(playlist.coverArtPath, targetSizePx = 300)
    Column(modifier = Modifier.width(120.dp).clickable(onClick = onClick)) {
        GlassPanel(modifier = Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(10.dp), showSheen = false) {
            if (cover != null) {
                AlbumArtImage(cover, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = GbColors.AccentTeal, modifier = Modifier.size(30.dp))
                }
            }
        }
        Text(
            playlist.name, color = GbColors.Text1, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)
        )
        Text(if (playlist.folderUri != null) "Folder" else "Playlist", color = GbColors.Text3, fontSize = 10.sp)
    }
}
