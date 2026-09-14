package com.groovebox.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groovebox.app.data.LinkedFolderEntity
import com.groovebox.app.ui.components.GlassPanel
import com.groovebox.app.ui.theme.GbColors

/**
 * Full screen (not a dropdown) for managing linked music folders: with a growing
 * folder count this scales as a normal scrollable list instead of one that would
 * keep pushing the rest of Settings further down the page.
 */
@Composable
fun SourceScreen(
    linkedFolders: List<LinkedFolderEntity>,
    onLinkFolder: () -> Unit,
    onAddFolderAsPlaylist: () -> Unit,
    onUnlinkFolder: (String) -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GbColors.Text1)
            }
            Text("Source", color = GbColors.Text1, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            TextButton(onClick = onLinkFolder) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = null, tint = GbColors.AccentPurple)
                Text("  Add folder", color = GbColors.AccentPurple)
            }
            TextButton(onClick = onRescan) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = GbColors.Text2)
                Text("  Rescan all", color = GbColors.Text2)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            TextButton(onClick = onAddFolderAsPlaylist) {
                Icon(Icons.Filled.PlaylistAdd, contentDescription = null, tint = GbColors.AccentTeal)
                Text("  Add folder as Playlist", color = GbColors.AccentTeal)
            }
        }

        if (linkedFolders.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Folder, contentDescription = null, tint = GbColors.Text3, modifier = androidx.compose.ui.Modifier.padding(bottom = 8.dp))
                Text("No folders linked yet", color = GbColors.Text2)
                Text("Tap \"Add folder\" to choose where your music lives", color = GbColors.Text3, fontSize = 12.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                items(linkedFolders, key = { it.uri }) { folder ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassPanel(modifier = Modifier, shape = RoundedCornerShape(10.dp)) {
                            Icon(Icons.Filled.Folder, contentDescription = null, tint = GbColors.AccentPurple, modifier = Modifier.padding(10.dp))
                        }
                        Text(folder.name, color = GbColors.Text1, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        IconButton(onClick = { onUnlinkFolder(folder.uri) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Unlink", tint = GbColors.AccentRed)
                        }
                    }
                }
                item { Spacer(Modifier.padding(24.dp)) }
            }
        }
    }
}
