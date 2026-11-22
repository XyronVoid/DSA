package com.spacebachao.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.OpenableColumns

object MediaScanner {

    /** Phone ki JPEG photos / MP4 videos, sabse badi pehle. */
    fun scan(context: Context, kind: Kind): List<MediaFile> {
        val collection: Uri
        val mime: String
        val minSize: Long
        when (kind) {
            Kind.IMAGE -> {
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                mime = "image/jpeg"
                minSize = 200L * 1024
            }
            Kind.VIDEO -> {
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                mime = "video/mp4"
                minSize = 3L * 1024 * 1024
            }
            Kind.PDF -> return emptyList()
        }

        val projection = mutableListOf(
            BaseColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
        )
        if (kind == Kind.VIDEO) projection += MediaStore.MediaColumns.DURATION

        val selection = "${MediaStore.MediaColumns.MIME_TYPE} = ? AND ${MediaStore.MediaColumns.SIZE} > ?"
        val args = arrayOf(mime, minSize.toString())
        val sort = "${MediaStore.MediaColumns.SIZE} DESC"

        val result = ArrayList<MediaFile>()
        context.contentResolver.query(collection, projection.toTypedArray(), selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(BaseColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val modCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val wCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val hCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val durCol = c.getColumnIndex(MediaStore.MediaColumns.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                result += MediaFile(
                    uri = ContentUris.withAppendedId(collection, id),
                    id = id,
                    name = c.getString(nameCol) ?: "file",
                    size = c.getLong(sizeCol),
                    mime = c.getString(mimeCol) ?: mime,
                    kind = kind,
                    dateTakenMs = c.getLong(takenCol),
                    dateModifiedMs = c.getLong(modCol) * 1000L,
                    width = c.getInt(wCol),
                    height = c.getInt(hCol),
                    durationMs = if (durCol >= 0) c.getLong(durCol) else 0L,
                )
            }
        }
        return result
    }

    /** User ne jo PDF chuna, uska naam aur size. */
    fun describePdf(context: Context, uri: Uri): MediaFile? {
        var name = "document.pdf"
        var size = -1L
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val s = c.getColumnIndex(OpenableColumns.SIZE)
                if (n >= 0) name = c.getString(n) ?: name
                if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
            }
        }
        if (size <= 0) size = FileOps.sizeOf(context.contentResolver, uri)
        if (size <= 0) return null
        return MediaFile(uri = uri, id = -1, name = name, size = size, mime = "application/pdf", kind = Kind.PDF)
    }
}
