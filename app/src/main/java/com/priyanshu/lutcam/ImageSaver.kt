package com.priyanshu.lutcam

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves photos to Pictures/LUTCam so they show up in the gallery. */
object ImageSaver {
    fun save(ctx: Context, bmp: Bitmap): Uri? {
        val name = "LUTCam_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val r = ctx.contentResolver
                val v = ContentValues()
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name)
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LUTCam")
                v.put(MediaStore.Images.Media.IS_PENDING, 1)
                val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return null
                r.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                val done = ContentValues()
                done.put(MediaStore.Images.Media.IS_PENDING, 0)
                r.update(uri, done, null, null)
                uri
            } else {
                saveLegacy(ctx, bmp, name)
            }
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(ctx: Context, bmp: Bitmap, name: String): Uri? {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "LUTCam")
        dir.mkdirs()
        val f = File(dir, name)
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        val v = ContentValues()
        v.put(MediaStore.Images.Media.DATA, f.absolutePath)
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name)
        return ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
    }
}
