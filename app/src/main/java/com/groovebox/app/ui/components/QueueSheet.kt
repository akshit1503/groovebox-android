package com.groovebox.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groovebox.app.data.TrackEntity
import com.groovebox.app.ui.theme.GbColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<TrackEntity>,
    currentTrackUri: String?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GbColors.GlassOpaque,
        contentColor = GbColors.Text1
    ) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            Text(
                "Up Next",
                color = GbColors.Text1,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
            )
            if (queue.isEmpty()) {
                Text(
                    "Nothing queued",
                    color = GbColors.Text3,
                    modifier = Modifier.padding(start = 20.dp, bottom = 20.dp)
                )
            } else {
                LazyColumn {
                    // Keyed by position, not just the track's own uri: the same track
                    // can legitimately appear twice in a queue (queued again, or
                    // queued while it's already the current track). Compose's
                    // LazyColumn crashes outright on a duplicate key, which is exactly
                    // what "add to queue" on an already-queued track used to do.
                    itemsIndexed(queue, key = { index, track -> "$index:${track.uri}" }) { index, track ->
                        val isCurrent = track.uri == currentTrackUri
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTrackClick(index) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val art = rememberAlbumArt(track.uri, targetSizePx = 96)
                            Box(
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(GbColors.GlassFill2),
                                contentAlignment = Alignment.Center
                            ) {
                                if (art != null) {
                                    AlbumArtImage(art, modifier = Modifier.size(40.dp))
                                } else {
                                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = GbColors.Text3, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.name,
                                    color = if (isCurrent) GbColors.AccentPurple else GbColors.Text1,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (isCurrent) "Now playing" else track.artist.ifBlank { "Unknown artist" },
                                    color = if (isCurrent) GbColors.AccentPurple.copy(alpha = 0.7f) else GbColors.Text3,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!isCurrent) {
                                IconButton(onClick = { onRemove(index) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove from queue", tint = GbColors.Text3)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
