package com.groovebox.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groovebox.app.data.PlaylistEntity
import com.groovebox.app.ui.components.AlbumArtImage
import com.groovebox.app.ui.components.GlassPanel
import com.groovebox.app.ui.components.rememberFileImage
import com.groovebox.app.ui.theme.GbColors
import com.groovebox.app.ui.theme.GbDimens

@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistEntity>,
    onOpenPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onPickCover: (String) -> Unit,
    onClearCover: (String) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var editingPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Playlists", color = GbColors.Text1, style = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
            FloatingActionButton(onClick = { showCreate = true }, containerColor = GbColors.AccentPurple) {
                Icon(Icons.Filled.Add, contentDescription = "New playlist")
            }
        }

        if (playlists.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No playlists yet", color = GbColors.Text2)
            }
        } else {
            LazyColumn {
                items(playlists, key = { it.id }) { pl ->
                    val cover = rememberFileImage(pl.coverArtPath, targetSizePx = 96)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPlaylist(pl.id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassPanel(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(10.dp), showSheen = false) {
                            if (cover != null) {
                                AlbumArtImage(cover, modifier = Modifier.fillMaxSize())
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (pl.folderUri != null) Icons.Filled.Folder else Icons.Filled.QueueMusic,
                                        contentDescription = null, tint = GbColors.AccentPurple
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(pl.name, color = GbColors.Text1)
                            if (pl.folderUri != null) {
                                Text("Folder", color = GbColors.Text3, fontSize = 11.sp)
                            }
                        }
                        IconButton(onClick = { editingPlaylist = pl }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = GbColors.Text3)
                        }
                        IconButton(onClick = { onDeletePlaylist(pl.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = GbColors.Text3)
                        }
                    }
                }
                // See GbDimens.BottomBarClearance — without this the last playlist
                // row sits unreachable behind the mini player/nav bar.
                item { Spacer(Modifier.height(GbDimens.BottomBarClearance)) }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, placeholder = { Text("Playlist name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) onCreatePlaylist(newName.trim())
                    newName = ""; showCreate = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }

    editingPlaylist?.let { pl ->
        var editName by remember(pl.id) { mutableStateOf(pl.name) }
        AlertDialog(
            onDismissRequest = { editingPlaylist = null },
            title = { Text("Edit Playlist") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        placeholder = { Text("Playlist name") },
                        singleLine = true,
                        enabled = pl.folderUri == null // a folder playlist's name mirrors its folder's name
                    )
                    TextButton(
                        onClick = { onPickCover(pl.id); editingPlaylist = null },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = GbColors.AccentPurple)
                        Text("  Choose cover from gallery", color = GbColors.AccentPurple)
                    }
                    if (pl.coverArtPath != null) {
                        TextButton(onClick = { onClearCover(pl.id); editingPlaylist = null }) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = GbColors.Text3)
                            Text("  Remove cover", color = GbColors.Text3)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editName.isNotBlank() && pl.folderUri == null) onRenamePlaylist(pl.id, editName.trim())
                    editingPlaylist = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingPlaylist = null }) { Text("Cancel") } }
        )
    }
}
