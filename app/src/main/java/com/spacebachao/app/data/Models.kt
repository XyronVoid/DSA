package com.spacebachao.app.data

import android.net.Uri

enum class Kind(val label: String, val plural: String) {
    IMAGE("Photo", "Photos"),
    VIDEO("Video", "Videos"),
    PDF("PDF", "PDFs"),
}

/** REPLACE = compressed file takes the original's place. KEEP_BOTH = a separate smaller copy. */
enum class Mode { REPLACE, KEEP_BOTH }

enum class Quality(
    val label: String,
    val description: String,
    val proOnly: Boolean,
    val jpegQuality: Int,
    val heicQuality: Int,
    val hevcRatio: Double,
    val avcRatio: Double,
    val floorFactor: Double,
    val pdfDpi: Int,
    val pdfJpegQuality: Int,
) {
    HIGH(
        "High quality", "Smaller savings, looks identical even when zoomed", proOnly = true,
        jpegQuality = 90, heicQuality = 90, hevcRatio = 0.60, avcRatio = 0.80, floorFactor = 1.3,
        pdfDpi = 200, pdfJpegQuality = 85,
    ),
    BALANCED(
        "Balanced", "Recommended. No visible difference, great savings", proOnly = false,
        jpegQuality = 85, heicQuality = 82, hevcRatio = 0.45, avcRatio = 0.65, floorFactor = 1.0,
        pdfDpi = 150, pdfJpegQuality = 80,
    ),
    MAX_SAVING(
        "Max saving", "Biggest savings, slight softness when zoomed in", proOnly = true,
        jpegQuality = 78, heicQuality = 72, hevcRatio = 0.33, avcRatio = 0.50, floorFactor = 0.75,
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
    /** Real file path (used only to put the original date back on the file). */
    val path: String? = null,
    /** Folder inside phone storage, e.g. "DCIM/Camera/". */
    val relativePath: String? = null,
) {
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
    val key: String = "",
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
    val dateModifiedMs: Long = 0,
    val path: String? = null,
    /** Folder of the original, so it can be put back in the same place. */
    val relativePath: String? = null,
    /** When the original was replaced by a new file (HEIC), the uri of that new file. */
    val replacementUri: String? = null,
)

/**
 * Thrown when a file is deliberately left alone (not an error).
 * permanent = true means the file won't be offered again.
 */
class SkipException(message: String, val permanent: Boolean = true) : Exception(message)
