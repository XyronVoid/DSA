package com.spacebachao.app

import java.util.Locale

private const val KB = 1024.0
private const val MB = KB * 1024
private const val GB = MB * 1024

fun Long.readableSize(): String {
    val b = this.toDouble().coerceAtLeast(0.0)
    return when {
        b >= GB -> String.format(Locale.US, "%.2f GB", b / GB)
        b >= 100 * MB -> String.format(Locale.US, "%.0f MB", b / MB)
        b >= MB -> String.format(Locale.US, "%.1f MB", b / MB)
        b >= KB -> String.format(Locale.US, "%.0f KB", b / KB)
        else -> "${this.coerceAtLeast(0)} B"
    }
}

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
