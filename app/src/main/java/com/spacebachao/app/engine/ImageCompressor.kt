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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Re-encodes a JPEG photo at the same resolution.
 * Date, location, camera info and orientation are copied over.
 */
object ImageCompressor {

    private const val MAX_PIXELS = 64_000_000L

    /** Photos with hidden extras inside (motion video, depth). These are left alone. */
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
     * @param readUri used to read the original (with location, if permission was given)
     * @param out the compressed JPEG is written here
     */
    fun compress(context: Context, file: MediaFile, readUri: Uri, quality: Quality, out: File) {
        val resolver = context.contentResolver

        if (looksLikeMotionPhoto(file.name)) throw SkipException("Motion photo, left as is")

        // 1. Original EXIF info
        val srcExif = resolver.openInputStream(readUri)?.use { ExifInterface(it) }
            ?: throw SkipException("Could not open this photo")
        val xmp = srcExif.getAttribute(ExifInterface.TAG_XMP)
        if (xmp != null && SPECIAL_XMP.any { xmp.contains(it) }) {
            throw SkipException("Motion/Portrait photo, left as is")
        }

        // 2. Size check (huge photos would run out of memory)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw SkipException("Could not read this photo")
        val pixels = bounds.outWidth.toLong() * bounds.outHeight
        if (pixels > MAX_PIXELS) throw SkipException("Very large photo (${pixels / 1_000_000}MP), not supported yet")

        // 3. Decode + re-encode (same resolution)
        val decoded: Bitmap? = try {
            resolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: OutOfMemoryError) {
            throw SkipException("Not enough memory on this phone")
        }
        val bitmap = decoded ?: throw SkipException("Could not read this photo")

        try {
            FileOutputStream(out).use { os ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, os)) {
                    throw IOException("Photo could not be encoded")
                }
            }
        } finally {
            bitmap.recycle()
        }

        // 4. Copy EXIF (date, location, orientation...)
        val dstExif = ExifInterface(out.absolutePath)
        for (tag in EXIF_TAGS) {
            srcExif.getAttribute(tag)?.let { dstExif.setAttribute(tag, it) }
        }
        // 5. Lock the date: add the time-zone offset if the camera didn't write one,
        //    so Android can't guess a wrong date when it re-reads the file.
        lockDate(dstExif, file.dateTakenMs)
        dstExif.saveAttributes()
    }

    private fun lockDate(exif: ExifInterface, takenMs: Long) {
        if (takenMs <= 0) return
        val original = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        if (original == null) {
            // No camera date at all: write the date Android showed, in the phone's time zone.
            val tz = TimeZone.getDefault()
            val local = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply { timeZone = tz }
            val text = local.format(Date(takenMs))
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, text)
            if (exif.getAttribute(ExifInterface.TAG_DATETIME) == null) {
                exif.setAttribute(ExifInterface.TAG_DATETIME, text)
            }
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, formatOffset(tz.getOffset(takenMs) / 60_000))
            return
        }
        if (exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL) != null) return

        val asUtc = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val wallClockAsUtc = runCatching { asUtc.parse(original)?.time }.getOrNull() ?: return
        val minutes = ((wallClockAsUtc - takenMs) / 60_000.0 / 15.0).roundToInt() * 15
        if (abs(minutes) > 14 * 60) return
        val offset = formatOffset(minutes)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
        if (exif.getAttribute(ExifInterface.TAG_OFFSET_TIME) == null) {
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
        }
    }

    private fun formatOffset(minutes: Int): String {
        val sign = if (minutes < 0) "-" else "+"
        val a = abs(minutes)
        return String.format(Locale.US, "%s%02d:%02d", sign, a / 60, a % 60)
    }

    private fun looksLikeMotionPhoto(name: String): Boolean {
        val n = name.uppercase()
        return n.startsWith("MVIMG") || n.contains(".MP.") || n.endsWith(".MP.JPG")
    }
}
