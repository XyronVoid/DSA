package com.spacebachao.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodecList
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.heifwriter.HeifWriter
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
        val (srcExif, bitmap) = prepare(context, file, readUri)
        try {
            FileOutputStream(out).use { os ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, os)) {
                    throw IOException("Photo could not be encoded")
                }
            }
        } finally {
            bitmap.recycle()
        }

        // Copy EXIF (date, location, orientation...)
        val dstExif = ExifInterface(out.absolutePath)
        for (tag in EXIF_TAGS) {
            srcExif.getAttribute(tag)?.let { dstExif.setAttribute(tag, it) }
        }
        // Lock the date: add the time-zone offset if the camera didn't write one,
        // so Android can't guess a wrong date when it re-reads the file.
        lockDate(dstExif, file.dateTakenMs)
        dstExif.saveAttributes()
    }

    /** Phone has an HEVC encoder (needed for HEIC). */
    val heicSupported: Boolean by lazy {
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                info.isEncoder && info.supportedTypes.any {
                    it.equals("image/vnd.android.heic", ignoreCase = true) || it.equals("video/hevc", ignoreCase = true)
                }
            }
        }.getOrDefault(false)
    }

    /**
     * Same resolution, HEIC format (much smaller than JPEG at the same visual quality).
     * Date, location and camera info are kept. Returns false if this photo should stay JPEG
     * (e.g. mirrored orientation), so the caller can fall back.
     */
    fun compressHeic(context: Context, file: MediaFile, readUri: Uri, quality: Quality, out: File): Boolean {
        if (!heicSupported) return false
        val (srcExif, bitmap) = prepare(context, file, readUri)
        try {
            val rotation = when (srcExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> 0
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> return false // mirrored photos stay JPEG
            }
            val exifBlock = buildExifBlock(context, srcExif, file.dateTakenMs)
            val writer = HeifWriter.Builder(out.absolutePath, bitmap.width, bitmap.height, HeifWriter.INPUT_MODE_BITMAP)
                .setQuality(quality.heicQuality)
                .setRotation(rotation)
                .setGridEnabled(true)
                .setMaxImages(1)
                .build()
            try {
                writer.start()
                writer.addBitmap(bitmap)
                if (exifBlock != null) writer.addExifData(0, exifBlock, 0, exifBlock.size)
                writer.stop(60_000L)
            } finally {
                writer.close()
            }
        } finally {
            bitmap.recycle()
        }
        if (out.length() <= 0L) throw IOException("HEIC file is empty")
        return true
    }

    /** Checks + EXIF + decoded pixels (same resolution). */
    private fun prepare(context: Context, file: MediaFile, readUri: Uri): Pair<ExifInterface, Bitmap> {
        val resolver = context.contentResolver

        if (looksLikeMotionPhoto(file.name)) throw SkipException("Motion photo, left as is")

        val srcExif = resolver.openInputStream(readUri)?.use { ExifInterface(it) }
            ?: throw SkipException("Could not open this photo")
        val xmp = srcExif.getAttribute(ExifInterface.TAG_XMP)
        if (xmp != null && SPECIAL_XMP.any { xmp.contains(it) }) {
            throw SkipException("Motion/Portrait photo, left as is")
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw SkipException("Could not read this photo")
        val pixels = bounds.outWidth.toLong() * bounds.outHeight
        if (pixels > MAX_PIXELS) throw SkipException("Very large photo (${pixels / 1_000_000}MP), not supported yet")

        val decoded: Bitmap? = try {
            resolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: OutOfMemoryError) {
            throw SkipException("Not enough memory on this phone")
        }
        val bitmap = decoded ?: throw SkipException("Could not read this photo")
        return srcExif to bitmap
    }

    /**
     * EXIF block for HEIC ("Exif\0\0" + TIFF data). Built by writing the tags into a tiny
     * JPEG with ExifInterface and cutting out its EXIF segment.
     */
    private fun buildExifBlock(context: Context, src: ExifInterface, takenMs: Long): ByteArray? {
        val tmp = File(context.cacheDir, "work_exif_${System.nanoTime()}.jpg")
        return try {
            val dot = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            FileOutputStream(tmp).use { dot.compress(Bitmap.CompressFormat.JPEG, 50, it) }
            dot.recycle()
            val e = ExifInterface(tmp.absolutePath)
            for (tag in EXIF_TAGS) src.getAttribute(tag)?.let { e.setAttribute(tag, it) }
            lockDate(e, takenMs)
            // Rotation is stored in the HEIC itself
            e.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            e.saveAttributes()
            exifSegment(tmp.readBytes())
        } catch (e: Exception) {
            null
        } finally {
            tmp.delete()
        }
    }

    private fun exifSegment(jpeg: ByteArray): ByteArray? {
        var i = 2 // after FFD8
        while (i + 4 <= jpeg.size) {
            if (jpeg[i] != 0xFF.toByte()) return null
            val marker = jpeg[i + 1].toInt() and 0xFF
            if (marker == 0xDA || marker == 0xD9) return null
            val len = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            if (len < 2 || i + 2 + len > jpeg.size) return null
            if (marker == 0xE1 && len > 8 &&
                jpeg[i + 4] == 'E'.code.toByte() && jpeg[i + 5] == 'x'.code.toByte() &&
                jpeg[i + 6] == 'i'.code.toByte() && jpeg[i + 7] == 'f'.code.toByte() &&
                jpeg[i + 8] == 0.toByte() && jpeg[i + 9] == 0.toByte()
            ) {
                return jpeg.copyOfRange(i + 4, i + 2 + len)
            }
            i += 2 + len
        }
        return null
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
