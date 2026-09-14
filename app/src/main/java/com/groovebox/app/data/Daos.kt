package com.groovebox.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE folderUri = :folderUri")
    suspend fun deleteByFolder(folderUri: String)

    @Query("DELETE FROM tracks WHERE uri NOT IN (:keepUris) AND folderUri = :folderUri")
    suspend fun pruneMissing(folderUri: String, keepUris: List<String>)

    @Query("UPDATE tracks SET likes = :likes WHERE uri = :uri")
    suspend fun setLikes(uri: String, likes: Int)

    @Query("SELECT * FROM tracks WHERE uri = :uri")
    suspend fun getByUri(uri: String): TrackEntity?

    @Query("DELETE FROM tracks WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt ASC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: String): PlaylistEntity?

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: String, name: String)

    @Query("DELETE FROM playlists WHERE folderUri = :folderUri")
    suspend fun deleteByFolderUri(folderUri: String)

    @Query("UPDATE playlists SET coverArtPath = :path WHERE id = :id")
    suspend fun setCoverArt(id: String, path: String?)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id")
    suspend fun clearPlaylistTracks(id: String)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :id ORDER BY position ASC")
    fun observePlaylistTracks(id: String): Flow<List<PlaylistTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToPlaylist(ref: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackUri = :trackUri")
    suspend fun removeFromPlaylist(playlistId: String, trackUri: String)

    @Query("DELETE FROM playlist_tracks WHERE trackUri = :trackUri")
    suspend fun removeFromAllPlaylists(trackUri: String)

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun countInPlaylist(playlistId: String): Int
}

@Dao
interface LinkedFolderDao {
    @Query("SELECT * FROM linked_folders ORDER BY addedAt ASC")
    fun observeAll(): Flow<List<LinkedFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: LinkedFolderEntity)

    @Query("DELETE FROM linked_folders WHERE uri = :uri")
    suspend fun delete(uri: String)
}
