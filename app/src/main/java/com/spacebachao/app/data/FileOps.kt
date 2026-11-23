package com.spacebachao.app.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

object FileOps {
    private const val BUFFER = 256 * 1024
    const val FOLDER = "SpaceBachao"

    fun copyUriToFile(resolver: ContentResolver, uri: Uri, dest: File) {
        val input = resolver.openInputStream(uri) ?: throw IOException("Could not open the file")
        input.use { inp -> FileOutputStream(dest).use { inp.copyTo(it, BUFFER) } }
    }

    /** Writes new data into the original file. "wt" = truncate the old data first. */
    fun overwrite(resolver: ContentResolver, uri: Uri, src: File) {
        val out: OutputStream = openTruncating(resolver, uri)
        out.use { o -> FileInputStream(src).use { it.copyTo(o, BUFFER) } }
    }

    private fun openTruncating(resolver: ContentResolver, uri: Uri): OutputStream {
        for (mode in arrayOf("wt", "rwt")) {
            try {
                resolver.openOutputStream(uri, mode)?.let { return it }
            } catch (e: SecurityException) {
                throw e
            } catch (e: Exception) {
                // try the next mode
            }
        }
        throw IOException("Could not write to the file")
    }

    fun sizeOf(resolver: ContentResolver, uri: Uri): Long = try {
        resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
    } catch (e: Exception) {
        -1L
    }

    fun exists(resolver: ContentResolver, uri: Uri): Boolean = try {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { it.moveToFirst() } ?: false
    } catch (e: Exception) {
        false
    }

    /** Creates a new file in Pictures/SpaceBachao, Movies/SpaceBachao or Download/SpaceBachao. */
    fun insertNew(
        resolver: ContentResolver,
        kind: Kind,
        displayName: String,
        mime: String,
        src: File,
        dateTakenMs: Long,
        subFolder: String? = null,
    ): Uri {
        val folder = if (subFolder == null) FOLDER else "$FOLDER/$subFolder"
        val (collection, relPath) = when (kind) {
            Kind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_PICTURES}/$folder"
            Kind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MOVIES}/$folder"
            Kind.PDF -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_DOWNLOADS}/$folder"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            if (kind != Kind.PDF && dateTakenMs > 0) put(MediaStore.MediaColumns.DATE_TAKEN, dateTakenMs)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Could not create the new file")
        try {
            val out = resolver.openOutputStream(uri, "w") ?: throw IOException("Could not write the new file")
            out.use { o -> FileInputStream(src).use { it.copyTo(o, BUFFER) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    /**
     * Puts the original dates back after a file was rewritten, so the gallery keeps it in place:
     * 1. file "last modified" time = original
     * 2. ask Android to re-read the file (date comes from EXIF / MP4 + last modified)
     * 3. also set DATE_TAKEN directly where Android allows it
     */
    fun restoreDates(context: Context, uri: Uri, path: String?, modifiedMs: Long, takenMs: Long) {
        val resolver = context.contentResolver
        val realPath = path ?: queryPath(resolver, uri)
        if (realPath != null && modifiedMs > 0) {
            runCatching { File(realPath).setLastModified(modifiedMs) }
        }
        val setTaken: () -> Unit = {
            if (takenMs > 0) {
                runCatching {
                    val v = ContentValues().apply { put(MediaStore.MediaColumns.DATE_TAKEN, takenMs) }
                    resolver.update(uri, v, null, null)
                }
            }
        }
        if (realPath != null) {
            MediaScannerConnection.scanFile(context, arrayOf(realPath), null) { _, _ -> setTaken() }
        } else {
            setTaken()
        }
    }

    @Suppress("DEPRECATION")
    fun queryPath(resolver: ContentResolver, uri: Uri): String? = try {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    fun compressedName(original: String, newExt: String): String {
        val dot = original.lastIndexOf('.')
        val base = if (dot > 0) original.substring(0, dot) else original
        return "${base}_compressed.$newExt"
    }
}
