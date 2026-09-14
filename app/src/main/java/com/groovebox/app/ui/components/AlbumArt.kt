package com.groovebox.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Process-wide so scrolling back to an already-seen row, or reopening the Full
 *  Player for the same track, doesn't re-extract/re-decode its embedded art.
 *  Keyed by uri+targetSizePx together (not uri alone) — a list row and the Full
 *  Player request very different sizes for the same track, and caching only by
 *  uri meant whichever size was decoded first (almost always the tiny 96px list
 *  thumbnail, since a track scrolls through the Library long before it's ever
 *  opened) got reused everywhere, stretched up and visibly blurred in the much
 *  larger Full Player panel. */
private val artCache = LruCache<String, ImageBitmap?>(150)

/**
 * Extracts the embedded cover art from a track's own file tags (MediaMetadataRetriever
 * reads it directly from the SAF uri — no separate art library/cache service needed).
 * Runs off the main thread, is cached by uri+size, and downsamples to roughly
 * [targetSizePx] so a 42dp list thumbnail doesn't pay to decode a multi-megapixel
 * embedded JPEG — relevant since this runs per visible row in the library list
 * while scrolling.
 */
@Composable
fun rememberAlbumArt(uri: String?, targetSizePx: Int = 512): ImageBitmap? {
    val context = LocalContext.current
    val cacheKey = uri?.let { "$it@$targetSizePx" }
    val state = produceState<ImageBitmap?>(initialValue = cacheKey?.let { artCache.get(it) }, key1 = cacheKey) {
        if (uri == null || cacheKey == null) { value = null; return@produceState }
        artCache.get(cacheKey)?.let { value = it; return@produceState }
        value = withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, Uri.parse(uri))
                val bytes = retriever.embeddedPicture
                retriever.release()
                val result = bytes?.let { decodeSampled(it, targetSizePx)?.asImageBitmap() }
                artCache.put(cacheKey, result)
                result
            } catch (e: Exception) {
                null
            }
        }
    }
    return state.value
}

/**
 * Decodes a local file (a saved playlist cover, not a track's embedded art) into
 * an ImageBitmap, downsampled to [targetSizePx] and cached in the same
 * process-wide LruCache as [rememberAlbumArt] — safe to share since a plain
 * absolute file path and a track's "content://..."/SAF uri string never collide.
 */
@Composable
fun rememberFileImage(path: String?, targetSizePx: Int = 512): ImageBitmap? {
    val cacheKey = path?.let { "$it@$targetSizePx" }
    val state = produceState<ImageBitmap?>(initialValue = cacheKey?.let { artCache.get(it) }, key1 = cacheKey) {
        if (path == null || cacheKey == null) { value = null; return@produceState }
        artCache.get(cacheKey)?.let { value = it; return@produceState }
        value = withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                val minSide = minOf(bounds.outWidth, bounds.outHeight)
                while (minSide / (sample * 2) >= targetSizePx) sample *= 2
                val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                val result = bmp?.asImageBitmap()
                artCache.put(cacheKey, result)
                result
            } catch (e: Exception) {
                null
            }
        }
    }
    return state.value
}

/**
 * A track's own embedded art if it has one, otherwise a playlist's custom cover
 * (e.g. when browsing/playing a playlist whose tracks lack their own artwork).
 * Both branches are always called unconditionally (never skip a @Composable call
 * based on a runtime condition) — when [fallbackCoverPath] is null the second call
 * is a cheap no-op that immediately resolves to null.
 */
@Composable
fun rememberAlbumArtWithFallback(trackUri: String?, fallbackCoverPath: String?, targetSizePx: Int = 512): ImageBitmap? {
    val trackArt = rememberAlbumArt(trackUri, targetSizePx)
    val fallbackArt = rememberFileImage(fallbackCoverPath, targetSizePx)
    return trackArt ?: fallbackArt
}

private fun decodeSampled(bytes: ByteArray, targetSizePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetSizePx && bounds.outHeight / (sample * 2) >= targetSizePx) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

@Composable
fun AlbumArtImage(bitmap: ImageBitmap, modifier: Modifier = Modifier) {
    Image(bitmap = bitmap, contentDescription = "Album art", modifier = modifier, contentScale = ContentScale.Crop)
}

private val dominantColorCache = LruCache<ImageBitmap, androidx.compose.ui.graphics.Color?>(50)

/**
 * Pulls a representative accent color out of the album art via Android's Palette
 * library (vibrant swatch preferred, falling back to muted, then null if the art
 * has no usable color — e.g. a near-greyscale cover). Used to tint the Full Player's
 * accent controls (play button, sliders) so they follow the current track's own
 * artwork instead of a single fixed app color.
 */
@Composable
fun rememberDominantColor(art: ImageBitmap?): androidx.compose.ui.graphics.Color? {
    val state = produceState<androidx.compose.ui.graphics.Color?>(initialValue = art?.let { dominantColorCache.get(it) }, key1 = art) {
        if (art == null) { value = null; return@produceState }
        dominantColorCache.get(art)?.let { value = it; return@produceState }
        value = withContext(Dispatchers.Default) {
            try {
                val palette = androidx.palette.graphics.Palette.from(art.asAndroidBitmap()).generate()
                val swatch = palette.vibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
                val result = swatch?.let { androidx.compose.ui.graphics.Color(it.rgb) }
                dominantColorCache.put(art, result)
                result
            } catch (e: Exception) {
                null
            }
        }
    }
    return state.value
}
