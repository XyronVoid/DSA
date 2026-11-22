package com.spacebachao.app.data

import android.net.Uri

enum class Kind { IMAGE, VIDEO, PDF }

/** Replace = compressed file original ki jagah. KeepBoth = alag copy banegi. */
enum class Mode { REPLACE, KEEP_BOTH }

enum class Quality(
    val label: String,
    val description: String,
    val jpegQuality: Int,
    val hevcRatio: Double,
    val avcRatio: Double,
    val floorFactor: Double,
    val pdfDpi: Int,
    val pdfJpegQuality: Int,
) {
    HIGH(
        "High quality", "Kam bachat, quality ekdum original jaisi",
        jpegQuality = 90, hevcRatio = 0.60, avcRatio = 0.80, floorFactor = 1.3,
        pdfDpi = 200, pdfJpegQuality = 85,
    ),
    BALANCED(
        "Balanced (recommended)", "Aankh se fark nahi dikhta, achhi bachat",
        jpegQuality = 85, hevcRatio = 0.45, avcRatio = 0.65, floorFactor = 1.0,
        pdfDpi = 150, pdfJpegQuality = 80,
    ),
    MAX_SAVING(
        "Max saving", "Sabse zyada bachat, zoom karne pe thoda fark dikh sakta hai",
        jpegQuality = 78, hevcRatio = 0.33, avcRatio = 0.50, floorFactor = 0.75,
        pdfDpi = 120, pdfJpegQuality = 70,
    ),
}

data class MediaFile(
    val uri: Uri,
    val id: Long,
    val name: String,
    val size: Long,
    val mime: String,
    val kind: Kind,
    val dateTakenMs: Long = 0,
    val dateModifiedMs: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0,
) {
    /** Har file ki pehchaan. PDF ke liye uri hi key hai. */
    val key: String
        get() = keyFor(kind, id, uri)

    companion object {
        fun keyFor(kind: Kind, id: Long, uri: Uri): String =
            if (kind == Kind.PDF) uri.toString() else "${kind.name}:$id"
    }
}

enum class ItemStatus { SAVED, SKIPPED, FAILED }

data class ResultItem(
    val name: String,
    val kind: Kind,
    val before: Long,
    val after: Long,
    val status: ItemStatus,
    val note: String = "",
)

data class BinEntry(
    val id: String,
    val key: String,
    val name: String,
    val kind: Kind,
    val mime: String,
    val uri: String,
    val binPath: String,
    val originalSize: Long,
    val compressedSize: Long,
    val createdAt: Long,
    val dateTakenMs: Long,
)

/**
 * Compressor jab file ko jaan-boojh kar chhodta hai (fail nahi, skip).
 * permanent = true matlab ye file dobara list me nahi dikhegi.
 */
class SkipException(message: String, val permanent: Boolean = true) : Exception(message)
