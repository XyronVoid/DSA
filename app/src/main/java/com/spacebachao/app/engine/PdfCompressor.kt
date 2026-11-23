package com.spacebachao.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import com.spacebachao.app.data.MediaFile
import com.spacebachao.app.data.Quality
import com.spacebachao.app.data.SkipException
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * For scanned / photo PDFs: renders each page as a clean image and builds a new, smaller PDF.
 * Text PDFs don't benefit, so if the saving is small the file is skipped.
 */
object PdfCompressor {

    private const val MAX_SIDE_PX = 2600

    fun compress(context: Context, file: MediaFile, quality: Quality, out: File, onProgress: (Float) -> Unit) {
        val pfd = context.contentResolver.openFileDescriptor(file.uri, "r")
            ?: throw SkipException("Could not open this PDF")
        val renderer = try {
            PdfRenderer(pfd)
        } catch (e: SecurityException) {
            pfd.close()
            throw SkipException("Password-protected PDF, left as is")
        } catch (e: Exception) {
            pfd.close()
            throw SkipException("Could not read this PDF")
        }

        renderer.use { r ->
            val count = r.pageCount
            if (count == 0) throw SkipException("This PDF has no pages")
            SimplePdfWriter(out).use { writer ->
                for (i in 0 until count) {
                    r.openPage(i).use { page ->
                        val ptW = page.width
                        val ptH = page.height
                        var scale = quality.pdfDpi / 72f
                        val longest = max(ptW, ptH) * scale
                        if (longest > MAX_SIDE_PX) scale *= MAX_SIDE_PX / longest
                        val w = (ptW * scale).roundToInt().coerceAtLeast(1)
                        val h = (ptH * scale).roundToInt().coerceAtLeast(1)

                        val bmp = try {
                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        } catch (e: OutOfMemoryError) {
                            throw SkipException("Not enough memory on this phone")
                        }
                        try {
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            val jpeg = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, quality.pdfJpegQuality, jpeg)
                            writer.addJpegPage(jpeg.toByteArray(), w, h, ptW, ptH)
                        } finally {
                            bmp.recycle()
                        }
                    }
                    onProgress((i + 1f) / count)
                }
            }
        }
    }
}

/** Minimal PDF writer: one JPEG image per page. No library needed. */
private class SimplePdfWriter(file: File) : AutoCloseable {
    private val out = CountingStream(BufferedOutputStream(FileOutputStream(file)))
    private val offsets = HashMap<Int, Long>()
    private val pageIds = ArrayList<Int>()
    private var nextId = 3 // 1 = Catalog, 2 = Pages

    init {
        out.write("%PDF-1.4\n%âãÏÓ\n".toByteArray(Charsets.ISO_8859_1))
        beginObject(1)
        writeText("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
    }

    fun addJpegPage(jpeg: ByteArray, pxW: Int, pxH: Int, ptW: Int, ptH: Int) {
        val pageId = nextId++
        val imageId = nextId++
        val contentId = nextId++
        pageIds += pageId

        beginObject(imageId)
        writeText(
            "<< /Type /XObject /Subtype /Image /Width $pxW /Height $pxH " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n"
        )
        out.write(jpeg)
        writeText("\nendstream\nendobj\n")

        val content = "q $ptW 0 0 $ptH 0 0 cm /Im0 Do Q\n".toByteArray(Charsets.ISO_8859_1)
        beginObject(contentId)
        writeText("<< /Length ${content.size} >>\nstream\n")
        out.write(content)
        writeText("\nendstream\nendobj\n")

        beginObject(pageId)
        writeText(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $ptW $ptH] " +
                "/Resources << /XObject << /Im0 $imageId 0 R >> >> /Contents $contentId 0 R >>\nendobj\n"
        )
    }

    override fun close() {
        beginObject(2)
        val kids = pageIds.joinToString(" ") { "$it 0 R" }
        writeText("<< /Type /Pages /Kids [$kids] /Count ${pageIds.size} >>\nendobj\n")

        val xrefStart = out.count
        val size = nextId
        val sb = StringBuilder()
        sb.append("xref\n0 $size\n")
        sb.append("0000000000 65535 f \n")
        for (id in 1 until size) {
            val off = offsets[id] ?: 0L
            sb.append(off.toString().padStart(10, '0')).append(" 00000 n \n")
        }
        sb.append("trailer\n<< /Size $size /Root 1 0 R >>\nstartxref\n$xrefStart\n%%EOF\n")
        writeText(sb.toString())
        out.close()
    }

    private fun beginObject(id: Int) {
        offsets[id] = out.count
        writeText("$id 0 obj\n")
    }

    private fun writeText(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
}

private class CountingStream(private val inner: OutputStream) : OutputStream() {
    var count = 0L
        private set

    override fun write(b: Int) {
        inner.write(b)
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        inner.write(b, off, len)
        count += len
    }

    override fun flush() = inner.flush()
    override fun close() = inner.close()
}
