package com.spacebachao.app.data

import android.content.ContentResolver
import android.content.ContentValues
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
        val input = resolver.openInputStream(uri) ?: throw IOException("File khul nahi rahi")
        input.use { inp -> FileOutputStream(dest).use { inp.copyTo(it, BUFFER) } }
    }

    /** Original file ke andar naya data likhta hai. "wt" = pehle purana data poora hatao. */
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
                // agla mode try karo
            }
        }
        throw IOException("File me likh nahi paaye")
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

    /** Nayi file banata hai (Pictures/SpaceBachao, Movies/SpaceBachao ya Download/SpaceBachao). */
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
        val uri = resolver.insert(collection, values) ?: throw IOException("Nayi file nahi bani")
        try {
            val out = resolver.openOutputStream(uri, "w") ?: throw IOException("Nayi file me likh nahi paaye")
            out.use { o -> FileInputStream(src).use { it.copyTo(o, BUFFER) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    fun compressedName(original: String, newExt: String): String {
        val dot = original.lastIndexOf('.')
        val base = if (dot > 0) original.substring(0, dot) else original
        return "${base}_compressed.$newExt"
    }
}
