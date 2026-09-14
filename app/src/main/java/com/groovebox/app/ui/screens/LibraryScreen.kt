package com.groovebox.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groovebox.app.data.PlaylistEntity
import com.groovebox.app.data.TrackEntity
import com.groovebox.app.ui.LibraryView
import com.groovebox.app.ui.components.AlbumArtImage
import com.groovebox.app.ui.components.GlassDropdownMenu
import com.groovebox.app.ui.components.GlassPanel
import com.groovebox.app.ui.components.TrackRow
import com.groovebox.app.ui.components.rememberFileImage
import com.groovebox.app.ui.theme.GbColors

private val sortOptions = listOf("name" to "Name", "artist" to "Artist", "recent" to "Recently added", "duration" to "Duration")

@Composable
fun LibraryScreen(
    tracks: List<TrackEntity>,
    playlists: List<PlaylistEntity>,
    activePlaylist: PlaylistEntity? = null,
    currentTrackUri: String?,
    view: LibraryView,
    searchQuery: String,
    sortMode: String,
    onSearchChange: (String) -> Unit,
    onSetView: (LibraryView) -> Unit,
    onSetSortMode: (String) -> Unit,
    onLinkFolder: () -> Unit,
    onTrackClick: (TrackEntity) -> Unit,
    onToggleLike: (TrackEntity) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onAddToPlaylist: (String, TrackEntity) -> Unit,
    onDelete: (TrackEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (activePlaylist != null) {
            PlaylistHeader(activePlaylist, trackCount = tracks.size)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search your library…", color = GbColors.Text3) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = GbColors.Text3) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Filled.Cancel, contentDescription = "Clear search", tint = GbColors.Text3)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = GbColors.GlassFill2,
                    unfocusedContainerColor = GbColors.GlassFill,
                    focusedTextColor = GbColors.Text1,
                    unfocusedTextColor = GbColors.Text1,
                    focusedIndicatorColor = GbColors.AccentPurple,
                    unfocusedIndicatorColor = GbColors.GlassBorder
                )
            )
            IconButton(onClick = onLinkFolder) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "Link folder", tint = GbColors.AccentPurple)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = view == LibraryView.ALL,
                onClick = { onSetView(LibraryView.ALL) },
                label = { Text("All Songs") }
            )
            FilterChip(
                selected = view == LibraryView.LIKED,
                onClick = { onSetView(LibraryView.LIKED) },
                label = { Text("Liked") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                "${tracks.size} track${if (tracks.size != 1) "s" else ""}",
                color = GbColors.Text3,
                style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
            )
            var sortMenuOpen by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.clickable { sortMenuOpen = true }.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Sort, contentDescription = "Sort", tint = GbColors.Text3, modifier = Modifier.size(15.dp))
                Text(
                    "  ${sortOptions.find { it.first == sortMode }?.second ?: "Name"}",
                    color = GbColors.Text3,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                )
                GlassDropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    sortOptions.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label + if (id == sortMode) "  ✓" else "") },
                            onClick = { onSetSortMode(id); sortMenuOpen = false }
                        )
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Your library is empty", color = GbColors.Text2)
                Text("Tap the folder icon to link a music folder", color = GbColors.Text3, modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(tracks, key = { it.uri }) { track ->
                    TrackRow(
                        track = track,
                        isCurrent = track.uri == currentTrackUri,
                        playlists = playlists,
                        fallbackCoverPath = activePlaylist?.coverArtPath,
                        onClick = { onTrackClick(track) },
                        onToggleLike = { onToggleLike(track) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onAddToPlaylist = { plId -> onAddToPlaylist(plId, track) },
                        onDelete = { onDelete(track) }
                    )
                }
                // See GbDimens.BottomBarClearance.
                item { androidx.compose.foundation.layout.Spacer(Modifier.height(com.groovebox.app.ui.theme.GbDimens.BottomBarClearance)) }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(playlist: PlaylistEntity, trackCount: Int) {
    val cover = rememberFileImage(playlist.coverArtPath, targetSizePx = 200)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassPanel(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(10.dp), showSheen = false) {
            if (cover != null) {
                AlbumArtImage(cover, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = GbColors.AccentPurple)
                }
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(playlist.name, color = GbColors.Text1, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "$trackCount track${if (trackCount != 1) "s" else ""}" + if (playlist.folderUri != null) " · Folder" else "",
                color = GbColors.Text3,
                fontSize = 12.sp
            )
        }
    }
}
