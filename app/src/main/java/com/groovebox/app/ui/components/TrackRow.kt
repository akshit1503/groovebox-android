package com.groovebox.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groovebox.app.data.TrackEntity
import com.groovebox.app.data.PlaylistEntity
import com.groovebox.app.ui.theme.GbColors

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: TrackEntity,
    isCurrent: Boolean,
    playlists: List<PlaylistEntity>,
    fallbackCoverPath: String? = null,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var playlistSubmenuOpen by remember { mutableStateOf(false) }
    var confirmDeleteOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val art = rememberAlbumArtWithFallback(track.uri, fallbackCoverPath, targetSizePx = 96)
        // No sheen/gradient border here: this composes fresh for every row that
        // scrolls into view for the first time, 227+ times in a full library list —
        // the specular highlight is barely visible at 42dp anyway, and the extra
        // gradient Brush + Box per thumbnail directly adds draw cost during fling
        // scroll, which is exactly where jank was reported.
        GlassPanel(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(8.dp), showSheen = false) {
            if (art != null) {
                AlbumArtImage(art, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = GbColors.Text3, modifier = Modifier.padding(10.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.name,
                color = if (isCurrent) GbColors.AccentPurple else GbColors.Text1,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.artist.ifBlank { "Unknown artist" },
                color = GbColors.Text3,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = GbColors.Text3)
        }

        GlassDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (track.likes > 0) "Unlike" else "Like") },
                leadingIcon = {
                    Icon(
                        if (track.likes > 0) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (track.likes > 0) GbColors.AccentRed else GbColors.Text2
                    )
                },
                onClick = { onToggleLike(); menuOpen = false }
            )
            DropdownMenuItem(
                text = { Text("Play next") },
                leadingIcon = { Icon(Icons.Filled.SkipNext, contentDescription = null) },
                onClick = { onPlayNext(); menuOpen = false }
            )
            DropdownMenuItem(
                text = { Text("Add to queue") },
                leadingIcon = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
                onClick = { onAddToQueue(); menuOpen = false }
            )
            DropdownMenuItem(
                text = { Text("Add to playlist") },
                leadingIcon = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) },
                onClick = { menuOpen = false; playlistSubmenuOpen = true }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = GbColors.AccentRed) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = GbColors.AccentRed) },
                onClick = { menuOpen = false; confirmDeleteOpen = true }
            )
        }

        // A second, small dropdown anchored on the same spot for picking which playlist
        // — DropdownMenu has no native nested-submenu support, so this opens right after
        // the first one closes rather than nesting inside it.
        GlassDropdownMenu(expanded = playlistSubmenuOpen, onDismissRequest = { playlistSubmenuOpen = false }) {
            if (playlists.isEmpty()) {
                DropdownMenuItem(text = { Text("No playlists yet") }, onClick = {}, enabled = false)
            } else {
                playlists.forEach { pl ->
                    DropdownMenuItem(
                        text = { Text(pl.name) },
                        leadingIcon = { Icon(Icons.Filled.PlaylistPlay, contentDescription = null) },
                        onClick = { onAddToPlaylist(pl.id); playlistSubmenuOpen = false }
                    )
                }
            }
        }

        if (confirmDeleteOpen) {
            AlertDialog(
                onDismissRequest = { confirmDeleteOpen = false },
                title = { Text("Delete \"${track.name}\"?") },
                text = { Text("This permanently deletes the file from your device. This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = { confirmDeleteOpen = false; onDelete() }) {
                        Text("Delete", color = GbColors.AccentRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteOpen = false }) { Text("Cancel") }
                }
            )
        }
    }
}
