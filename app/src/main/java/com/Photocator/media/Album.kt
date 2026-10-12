package com.Photocator.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import java.io.File
import java.io.FileInputStream

/** One photo or video of the app album. [durationMs] is 0 for photos. */
data class MediaItem(val uri: Uri, val durationMs: Long, val name: String = "")

sealed interface RenameResult {
    data object Ok : RenameResult
    data object Failed : RenameResult
    /** The system must ask the user for write access first; launch [sender] and retry afterwards. */
    data class NeedsAccess(val sender: android.content.IntentSender) : RenameResult
}

/** Saving to and reading from the app's album (Pictures/Photocator, Movies/Photocator). */
class Album(private val ctx: Context) {
    companion object { const val FOLDER = "Photocator" }

    private fun newName(ext: String) = "${FOLDER}_${System.currentTimeMillis()}_${(0..999).random()}.$ext"

    /** Copies [src] into the album; returns the new media uri or null on failure. The source is left untouched. */
    fun savePhoto(src: File): Uri? = save(src, "image/jpeg", "jpg", Environment.DIRECTORY_PICTURES, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

    fun saveVideo(src: File): Uri? = save(src, "video/mp4", "mp4", Environment.DIRECTORY_MOVIES, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)

    private fun save(src: File, mime: String, ext: String, dirType: String, collection: Uri): Uri? {
        val name = newName(ext)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$dirType/$FOLDER")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = ctx.contentResolver.insert(collection, values) ?: return null
                try {
                    ctx.contentResolver.openOutputStream(uri)?.use { out -> FileInputStream(src).use { it.copyTo(out) } }
                        ?: throw IllegalStateException("no stream")
                    val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    ctx.contentResolver.update(uri, done, null, null)
                    uri
                } catch (e: Exception) {
                    try { ctx.contentResolver.delete(uri, null, null) } catch (_: Exception) { }
                    null
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(dirType), FOLDER)
                if (!dir.exists() && !dir.mkdirs()) return null
                val dest = File(dir, name)
                FileInputStream(src).use { input -> dest.outputStream().use { input.copyTo(it) } }
                MediaScannerConnection.scanFile(ctx, arrayOf(dest.absolutePath), arrayOf(mime), null)
                Uri.fromFile(dest)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Newest image of the app album (date added, then id), or null. */
    fun latestPhoto(): Uri? = try {
        val (sel, args) = pathFilter()
        ctx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            sel, args,
            "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC",
        )?.use { c ->
            if (c.moveToFirst()) ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)) else null
        }
    } catch (_: Exception) { null }

    /** Videos stored in the app folder, newest first. */
    fun listVideos(): List<MediaItem> = try {
        val (sel, args) = pathFilter()
        val out = ArrayList<MediaItem>()
        ctx.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.DISPLAY_NAME),
            sel, args,
            "${MediaStore.Video.Media.DATE_ADDED} DESC, ${MediaStore.Video.Media._ID} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                out.add(MediaItem(ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(0)), c.getLong(1), c.getString(2) ?: ""))
            }
        }
        out
    } catch (_: Exception) { emptyList() }

    private fun pathFilter(): Pair<String, Array<String>> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" to arrayOf("%$FOLDER%")
        else
            "${MediaStore.MediaColumns.DATA} LIKE ?" to arrayOf("%/$FOLDER/%")

    /** Square thumbnail (360 px from the system on Android 10+, otherwise the first frame). */
    fun videoThumb(uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ctx.contentResolver.loadThumbnail(uri, Size(360, 360), null)
        } else {
            val r = MediaMetadataRetriever()
            try { r.setDataSource(ctx, uri); r.getFrameAtTime(0) } finally { r.release() }
        }
    } catch (_: Exception) { null }

    /** Newest photos of the app album first. */
    fun listPhotos(): List<MediaItem> = try {
        val (sel, args) = pathFilter()
        val out = ArrayList<MediaItem>()
        ctx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
            sel, args,
            "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                out.add(MediaItem(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)), 0L, c.getString(1) ?: ""))
            }
        }
        out
    } catch (_: Exception) { emptyList() }

    /** Deletes photos or videos; returns the ones that could not be deleted without the user's confirmation. */
    fun delete(uris: List<Uri>): List<Uri> {
        val failed = ArrayList<Uri>()
        for (u in uris) {
            val ok = try { ctx.contentResolver.delete(u, null, null) > 0 } catch (_: Exception) { false }
            if (!ok) failed.add(u)
        }
        return failed
    }

    /**
     * Renames a photo or video (the extension is kept). Files left by an earlier install belong to no one, so
     * Android asks the user before the app may change them: that case returns [RenameResult.NeedsAccess].
     * Pass [afterAccessGranted] = true on the retry so a second refusal is reported as a failure.
     */
    fun rename(uri: Uri, newBaseName: String, afterAccessGranted: Boolean = false): RenameResult {
        val clean = newBaseName.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().trim('.')
        if (clean.isEmpty()) return RenameResult.Failed
        return try {
            val old = ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "file.mp4"
            val ext = old.substringAfterLast('.', "")
            val full = if (ext.isEmpty()) clean else "$clean.$ext"
            val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, full) }
            if (ctx.contentResolver.update(uri, values, null, null) > 0) RenameResult.Ok
            else if (!afterAccessGranted && Build.VERSION.SDK_INT >= 30) accessRequest(uri) ?: RenameResult.Failed
            else RenameResult.Failed
        } catch (e: SecurityException) {
            if (afterAccessGranted) RenameResult.Failed
            else if (Build.VERSION.SDK_INT >= 30) accessRequest(uri) ?: RenameResult.Failed
            else if (Build.VERSION.SDK_INT == 29 && e is android.app.RecoverableSecurityException)
                RenameResult.NeedsAccess(e.userAction.actionIntent.intentSender)
            else RenameResult.Failed
        } catch (_: Exception) {
            RenameResult.Failed
        }
    }

    private fun accessRequest(uri: Uri): RenameResult? = try {
        RenameResult.NeedsAccess(MediaStore.createWriteRequest(ctx.contentResolver, listOf(uri)).intentSender)
    } catch (_: Exception) { null }
}
