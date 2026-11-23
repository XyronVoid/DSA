package com.spacebachao.app.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.Mp4LocationData
import androidx.media3.container.Mp4TimestampData
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EncoderUtil
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMuxer
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.spacebachao.app.data.MediaFile
import com.spacebachao.app.data.Quality
import com.spacebachao.app.data.SkipException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Converts a video to H.265 (HEVC) with the phone's hardware encoder.
 * Resolution and FPS stay the same, only the bitrate goes down.
 * The original date and location are written back into the MP4 so the gallery order stays the same.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
object VideoCompressor {

    /** Seconds from 1904-01-01 to 1970-01-01 (MP4 time starts in 1904). */
    private const val MP4_EPOCH_OFFSET_S = 2_082_844_800L

    private data class Probe(
        val durationMs: Long,
        val bitrate: Long,
        val width: Int,
        val height: Int,
        val hasVideo: Boolean,
        val latitude: Float?,
        val longitude: Float?,
    )

    private val hevcSupported: Boolean by lazy {
        runCatching { EncoderUtil.getSupportedEncoders(MimeTypes.VIDEO_H265).isNotEmpty() }
            .getOrDefault(false)
    }

    suspend fun compress(
        context: Context,
        file: MediaFile,
        readUri: Uri,
        quality: Quality,
        out: File,
        onProgress: (Float) -> Unit,
    ) {
        val probe = withContext(Dispatchers.IO) { probe(context, readUri) }
        if (!probe.hasVideo) throw SkipException("No video track in this file")

        val durationMs = if (probe.durationMs > 0) probe.durationMs else file.durationMs
        val originalBitrate = when {
            probe.bitrate > 0 -> probe.bitrate
            durationMs > 0 -> file.size * 8_000L / durationMs
            else -> 0L
        }
        if (originalBitrate <= 0) throw SkipException("Could not read video info")

        val useHevc = hevcSupported
        val target = targetBitrate(originalBitrate, probe.width, probe.height, useHevc, quality)
            ?: throw SkipException("Already compressed, nothing to gain")

        val createdMs = if (file.dateTakenMs > 0) file.dateTakenMs else file.dateModifiedMs
        val lat = probe.latitude
        val lon = probe.longitude

        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { cont ->
                val handler = Handler(Looper.getMainLooper())
                val holder = ProgressHolder()
                lateinit var transformer: Transformer

                val poll = object : Runnable {
                    override fun run() {
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress / 100f)
                        }
                        handler.postDelayed(this, 400)
                    }
                }

                // Original date + location into the new MP4
                val muxerFactory = InAppMuxer.Factory.Builder()
                    .setMetadataProvider { entries ->
                        if (createdMs > 0) {
                            val mp4Time = createdMs / 1000L + MP4_EPOCH_OFFSET_S
                            entries.removeAll { it is Mp4TimestampData }
                            entries.add(Mp4TimestampData(mp4Time, mp4Time))
                        }
                        if (lat != null && lon != null) {
                            entries.removeAll { it is Mp4LocationData }
                            entries.add(Mp4LocationData(lat, lon))
                        }
                    }
                    .build()

                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder().setBitrate(target).build()
                    )
                    .setEnableFallback(true)
                    .build()

                transformer = Transformer.Builder(context)
                    .setVideoMimeType(if (useHevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
                    .setEncoderFactory(encoderFactory)
                    .setMuxerFactory(muxerFactory)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            handler.removeCallbacks(poll)
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            handler.removeCallbacks(poll)
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    SkipException("This video could not be compressed (code ${exportException.errorCode})")
                                )
                            }
                        }
                    })
                    .build()

                cont.invokeOnCancellation {
                    handler.post {
                        handler.removeCallbacks(poll)
                        transformer.cancel()
                    }
                }

                if (cont.isActive) {
                    transformer.start(MediaItem.fromUri(readUri), out.absolutePath)
                    handler.post(poll)
                }
            }
        }
    }

    /** null = not worth compressing (bitrate already low). */
    private fun targetBitrate(original: Long, width: Int, height: Int, hevc: Boolean, q: Quality): Int? {
        val pixels = width.toLong() * height
        val floor = when {
            pixels >= 7_500_000 -> 12_000_000L // 4K
            pixels >= 3_300_000 -> 7_000_000L  // 1440p
            pixels >= 1_900_000 -> 4_000_000L  // 1080p
            pixels >= 850_000 -> 2_200_000L    // 720p
            else -> 1_000_000L
        }
        val minBitrate = ((if (hevc) floor else floor * 3 / 2) * q.floorFactor).toLong()
        val ratio = if (hevc) q.hevcRatio else q.avcRatio
        val target = maxOf((original * ratio).toLong(), minBitrate)
        if (target > original * 0.8) return null
        return target.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun probe(context: Context, uri: Uri): Probe {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val (lat, lon) = parseIso6709(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION))
            return Probe(
                durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                bitrate = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L,
                width = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                hasVideo = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes",
                latitude = lat,
                longitude = lon,
            )
        } catch (e: Exception) {
            throw SkipException("Could not read this video")
        } finally {
            runCatching { r.release() }
        }
    }

    /** Parses a location string like "+28.6139+077.2090/" into lat/lon. */
    private fun parseIso6709(value: String?): Pair<Float?, Float?> {
        if (value.isNullOrBlank()) return null to null
        val m = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""").find(value) ?: return null to null
        val lat = m.groupValues[1].toFloatOrNull()
        val lon = m.groupValues[2].toFloatOrNull()
        if (lat == null || lon == null) return null to null
        if (lat == 0f && lon == 0f) return null to null
        return lat to lon
    }
}
