package com.spacebachao.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.spacebachao.app.data.MediaFile
import com.spacebachao.app.data.Quality
import com.spacebachao.app.data.SkipException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * JPEG photo ko dobara encode karta hai (resolution same rehta hai).
 * Date, location, camera info, orientation sab copy hota hai.
 */
object ImageCompressor {

    private const val MAX_PIXELS = 64_000_000L

    /** Aisi photos jinke andar extra cheez chupi hoti hai (motion video, depth). Inhe nahi chhedte. */
    private val SPECIAL_XMP = listOf("MotionPhoto", "MicroVideo", "GDepth", "GImage", "GContainer")

    private val EXIF_TAGS = listOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
    )

    /**
     * @param readUri original padhne ke liye (location ke saath, agar permission hai)
     * @param out yahan compressed JPEG likha jaata hai
     */
    fun compress(context: Context, file: MediaFile, readUri: Uri, quality: Quality, out: File) {
        val resolver = context.contentResolver

        if (looksLikeMotionPhoto(file.name)) throw SkipException("Motion photo hai, isliye chhoda")

        // 1. Original ki EXIF info
        val srcExif = resolver.openInputStream(readUri)?.use { ExifInterface(it) }
            ?: throw SkipException("Photo khul nahi rahi")
        val xmp = srcExif.getAttribute(ExifInterface.TAG_XMP)
        if (xmp != null && SPECIAL_XMP.any { xmp.contains(it) }) {
            throw SkipException("Motion/Portrait photo hai, isliye chhoda")
        }

        // 2. Size check (bahut badi photo memory khatam kar degi)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw SkipException("Photo padh nahi paaye")
        val pixels = bounds.outWidth.toLong() * bounds.outHeight
        if (pixels > MAX_PIXELS) throw SkipException("Bahut badi photo (${pixels / 1_000_000}MP), abhi support nahi")

        // 3. Decode + dobara encode (resolution same)
        val decoded: Bitmap? = try {
            resolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: OutOfMemoryError) {
            throw SkipException("Phone ki memory kam pad gayi")
        }
        val bitmap = decoded ?: throw SkipException("Photo padh nahi paaye")

        try {
            FileOutputStream(out).use { os ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, os)) {
                    throw IOException("Photo encode nahi hui")
                }
            }
        } finally {
            bitmap.recycle()
        }

        // 4. EXIF copy (date, location, orientation...)
        val dstExif = ExifInterface(out.absolutePath)
        for (tag in EXIF_TAGS) {
            srcExif.getAttribute(tag)?.let { dstExif.setAttribute(tag, it) }
        }
        dstExif.saveAttributes()
    }

    private fun looksLikeMotionPhoto(name: String): Boolean {
        val n = name.uppercase()
        return n.startsWith("MVIMG") || n.contains(".MP.") || n.endsWith(".MP.JPG")
    }
}
