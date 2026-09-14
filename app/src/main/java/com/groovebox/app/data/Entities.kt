package com.groovebox.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Primary key is the SAF document URI string — stable across app restarts
 * as long as the folder permission is still granted, and naturally unique
 * per file without needing a separate id scheme.
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val ext: String,
    val folderUri: String,
    val addedAt: Long,
    val likes: Int = 0
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    // Absolute path to a cropped-to-square cover image saved in app-internal storage
    // (not the raw picked content:// uri — that permission isn't guaranteed to survive
    // app restarts). Null means "no custom cover, show the default icon".
    val coverArtPath: String? = null,
    // Non-null only for a "folder as playlist" entry: the SAF tree uri of the linked
    // folder this playlist mirrors. Its track list is computed live from TrackEntity.folderUri
    // rather than the playlist_tracks join table, so it always reflects the folder's
    // current contents with no separate sync step needed on rescan.
    val folderUri: String? = null
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackUri"])
data class PlaylistTrackEntity(
    val playlistId: String,
    val trackUri: String,
    val position: Int
)

@Entity(tableName = "linked_folders")
data class LinkedFolderEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val addedAt: Long
)
