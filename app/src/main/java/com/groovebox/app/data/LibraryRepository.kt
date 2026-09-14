package com.groovebox.app.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Local library is built from user-linked folders (Storage Access Framework
 * trees, not a blanket device-wide media scan) — same "reference the files
 * where they are, never copy them" model as the desktop app's linked
 * folders, and it avoids needing the broad READ_MEDIA_AUDIO permission.
 */
class LibraryRepository(private val context: Context) {
    private val db = GrooveBoxDatabase.get(context)
    private val resolver: ContentResolver = context.contentResolver

    private val audioExtensions = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus", "aif", "aiff")

    suspend fun linkFolder(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        resolver.takePersistableUriPermission(
            treeUri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext 0
        db.linkedFolderDao().insert(
            LinkedFolderEntity(uri = treeUri.toString(), name = root.name ?: "Folder", addedAt = System.currentTimeMillis())
        )
        scanFolder(treeUri)
    }

    /**
     * Unlinking must fully undo linkFolder(), or the folder can resurface later:
     * releasing the SAF permission stops rescanAll() (which previously trusted
     * every persisted permission the app had EVER been granted, not just the
     * ones still in the linked_folders table) from silently re-adding this
     * folder's tracks on the next rescan. Any "Add folder as Playlist" wrapper
     * pointing at this folder is deleted too — it has no meaning once the
     * folder it mirrors is gone, and would otherwise linger as an empty ghost
     * playlist.
     */
    suspend fun unlinkFolder(folderUri: String) = withContext(Dispatchers.IO) {
        try {
            resolver.releasePersistableUriPermission(Uri.parse(folderUri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Already released or never held — nothing further to undo.
        }
        db.linkedFolderDao().delete(folderUri)
        db.trackDao().deleteByFolder(folderUri)
        db.playlistDao().deleteByFolderUri(folderUri)
    }

    suspend fun rescanAll() = withContext(Dispatchers.IO) {
        // The linked_folders table (not the raw SAF permission grant list) is the
        // source of truth for what's actually linked — persistedUriPermissions can
        // keep entries around after a folder's own row was deleted (permissions
        // are released explicitly in unlinkFolder, but older/already-unlinked
        // grants from before that fix could still be sitting there).
        val folders = db.linkedFolderDao().observeAll().first()
        folders.forEach { folder -> scanFolder(Uri.parse(folder.uri)) }
    }

    private suspend fun scanFolder(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext 0
        val found = mutableListOf<TrackEntity>()
        walk(root, treeUri.toString(), found)
        db.trackDao().insertAll(found)
        db.trackDao().pruneMissing(treeUri.toString(), found.map { it.uri })
        found.size
    }

    private fun walk(dir: DocumentFile, folderUri: String, out: MutableList<TrackEntity>) {
        for (f in dir.listFiles()) {
            if (f.isDirectory) {
                walk(f, folderUri, out)
            } else {
                val name = f.name ?: continue
                if (name.startsWith(".")) continue // skip AppleDouble/hidden junk files
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in audioExtensions) continue
                val tags = readTags(f.uri)
                out.add(
                    TrackEntity(
                        uri = f.uri.toString(),
                        name = tags.title ?: name.substringBeforeLast('.'),
                        artist = tags.artist ?: "",
                        album = tags.album ?: "",
                        durationMs = tags.durationMs,
                        sizeBytes = f.length(),
                        ext = ext,
                        folderUri = folderUri,
                        addedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private data class Tags(val title: String?, val artist: String?, val album: String?, val durationMs: Long)

    private fun readTags(uri: Uri): Tags {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            Tags(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            )
        } catch (e: Exception) {
            Tags(null, null, null, 0L)
        } finally {
            retriever.release()
        }
    }

    fun observeTracks() = db.trackDao().observeAll()
    fun observePlaylists() = db.playlistDao().observePlaylists()
    fun observeLinkedFolders() = db.linkedFolderDao().observeAll()

    suspend fun toggleLike(uri: String, currentLikes: Int) = withContext(Dispatchers.IO) {
        db.trackDao().setLikes(uri, if (currentLikes > 0) 0 else 1)
    }

    /** Deletes the actual file via SAF, then removes the library entry regardless of
     *  whether the file delete succeeded (e.g. already gone) — never leaves a stale row. */
    suspend fun deleteTrack(track: TrackEntity) = withContext(Dispatchers.IO) {
        try {
            DocumentFile.fromSingleUri(context, Uri.parse(track.uri))?.delete()
        } catch (e: Exception) {
            // File deletion is best-effort; the DB row is cleaned up either way below.
        }
        db.trackDao().deleteByUri(track.uri)
        db.playlistDao().removeFromAllPlaylists(track.uri)
    }

    suspend fun createPlaylist(name: String): String = withContext(Dispatchers.IO) {
        val id = java.util.UUID.randomUUID().toString()
        db.playlistDao().insertPlaylist(PlaylistEntity(id, name, System.currentTimeMillis()))
        id
    }

    /**
     * A folder-backed playlist ("Add folder as Playlist") isn't a standalone
     * entity from the user's point of view — deleting it should remove the whole
     * thing: the Source entry and its scanned tracks too, not just the playlist
     * row, or the folder and its tracks silently linger (visible in Source, still
     * counted in the Library) even though the playlist that represented them is
     * gone. unlinkFolder() already deletes the matching playlist row itself, so
     * it's used in place of a plain playlist delete here rather than in addition.
     */
    suspend fun deletePlaylist(id: String) = withContext(Dispatchers.IO) {
        val playlist = db.playlistDao().getById(id)
        val folderUri = playlist?.folderUri
        if (folderUri != null) {
            unlinkFolder(folderUri)
        } else {
            db.playlistDao().deletePlaylist(id)
        }
    }
    suspend fun renamePlaylist(id: String, name: String) = withContext(Dispatchers.IO) { db.playlistDao().renamePlaylist(id, name) }

    /**
     * Reads the picked gallery image, center-crops it to a square (matching the
     * 1:1 aspect every cover tile in the app renders at — list icon, Home shelf
     * card, playlist header), downsamples/scales to a fixed [coverSizePx], and
     * saves it as a JPEG in app-internal storage. A stable content:// permission
     * for the picked uri isn't guaranteed to outlive this process, so the image
     * must be copied out immediately rather than storing the picked uri itself.
     * The filename embeds a timestamp so re-picking a cover produces a new path —
     * otherwise AlbumArt.kt's LruCache (keyed by path+size) would keep serving the
     * old bitmap forever for a path it already has cached.
     */
    suspend fun setPlaylistCoverArt(playlistId: String, pickedUri: Uri, coverSizePx: Int = 640): Unit = withContext(Dispatchers.IO) {
        val bytes = resolver.openInputStream(pickedUri)?.use { it.readBytes() } ?: return@withContext
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext

        var sample = 1
        val minSide = minOf(bounds.outWidth, bounds.outHeight)
        while (minSide / (sample * 2) >= coverSizePx) sample *= 2
        val rawDecoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@withContext
        // Phone camera photos are frequently stored with the raw pixel data in a
        // fixed sideways/upside-down layout plus an EXIF Orientation tag telling
        // viewers how to rotate it for display — the system photo picker's own
        // preview respects that tag, but BitmapFactory does not, which is exactly
        // why a cover could preview correctly but save out rotated.
        val orientation = ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
        val decoded = applyExifOrientation(rawDecoded, orientation)

        val side = minOf(decoded.width, decoded.height)
        val cropX = (decoded.width - side) / 2
        val cropY = (decoded.height - side) / 2
        val squared = Bitmap.createBitmap(decoded, cropX, cropY, side, side)
        val scaled = if (squared.width == coverSizePx) squared else Bitmap.createScaledBitmap(squared, coverSizePx, coverSizePx, true)

        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        // Remove any previous cover file(s) for this playlist before writing the new
        // one, so old cropped images don't pile up in internal storage over repeated re-picks.
        dir.listFiles { f -> f.name.startsWith("$playlistId-") }?.forEach { it.delete() }
        val file = File(dir, "$playlistId-${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 92, out) }

        db.playlistDao().setCoverArt(playlistId, file.absolutePath)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    suspend fun clearPlaylistCoverArt(playlistId: String) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "covers")
        dir.listFiles { f -> f.name.startsWith("$playlistId-") }?.forEach { it.delete() }
        db.playlistDao().setCoverArt(playlistId, null)
    }

    /**
     * "Add folder as Playlist": links the folder as a normal scanned source (so its
     * tracks exist in the library and stay in sync on rescan) and creates a playlist
     * row tagged with that folder's uri. Its track list is NOT copied into
     * playlist_tracks — GrooveBoxViewModel filters tracks by folderUri directly for
     * any playlist with a non-null folderUri, so the playlist automatically tracks
     * whatever the folder currently contains with no extra sync bookkeeping.
     */
    suspend fun createFolderPlaylist(treeUri: Uri): String = withContext(Dispatchers.IO) {
        resolver.takePersistableUriPermission(treeUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val root = DocumentFile.fromTreeUri(context, treeUri)
        val name = root?.name ?: "Folder"
        db.linkedFolderDao().insert(
            LinkedFolderEntity(uri = treeUri.toString(), name = name, addedAt = System.currentTimeMillis())
        )
        scanFolder(treeUri)
        val id = java.util.UUID.randomUUID().toString()
        db.playlistDao().insertPlaylist(PlaylistEntity(id, name, System.currentTimeMillis(), folderUri = treeUri.toString()))
        id
    }

    suspend fun addToPlaylist(playlistId: String, trackUri: String) = withContext(Dispatchers.IO) {
        val position = db.playlistDao().countInPlaylist(playlistId)
        db.playlistDao().addToPlaylist(PlaylistTrackEntity(playlistId, trackUri, position))
    }

    suspend fun removeFromPlaylist(playlistId: String, trackUri: String) = withContext(Dispatchers.IO) {
        db.playlistDao().removeFromPlaylist(playlistId, trackUri)
    }

    fun observePlaylistTracks(playlistId: String) = db.playlistDao().observePlaylistTracks(playlistId)
}
