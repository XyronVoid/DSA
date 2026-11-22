package com.priyanshu.lutcam

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** A colour look. All LUTs are stored as 32x32x32 RGB tables laid out as a (32*32) x 32 strip. */
class Preset(val name: String, val data: ByteArray, val vignette: Float, val file: File? = null)

/** Parameters used to generate the built-in looks. */
class Grade(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 1f,
    val vibrance: Float = 0f,
    val temp: Float = 0f,
    val tint: Float = 0f,
    val shadow: FloatArray = floatArrayOf(0f, 0f, 0f),
    val highlight: FloatArray = floatArrayOf(0f, 0f, 0f),
    val fade: Float = 0f,
    val bw: Boolean = false
)

object Luts {
    const val N = 32

    fun builtIns(): List<Preset> = listOf(
        Preset("Original", build(Grade()), 0f),
        Preset("Cinematic", build(Grade(
            contrast = 0.35f, saturation = 0.92f, temp = 0.08f, fade = 0.03f,
            shadow = floatArrayOf(-0.06f, 0.02f, 0.07f), highlight = floatArrayOf(0.08f, 0.03f, -0.06f)
        )), 0.35f),
        Preset("Teal & Orange", build(Grade(
            contrast = 0.3f, saturation = 1.1f,
            shadow = floatArrayOf(-0.10f, 0.03f, 0.10f), highlight = floatArrayOf(0.12f, 0.04f, -0.08f)
        )), 0.3f),
        Preset("Golden Hour", build(Grade(
            contrast = 0.2f, saturation = 1.15f, temp = 0.55f, tint = 0.1f,
            highlight = floatArrayOf(0.06f, 0.03f, -0.03f)
        )), 0.25f),
        Preset("Moody", build(Grade(
            exposure = -0.2f, contrast = 0.4f, saturation = 0.7f, fade = 0.05f,
            shadow = floatArrayOf(-0.03f, 0.02f, 0.05f)
        )), 0.45f),
        Preset("Desi Wedding", build(Grade(
            exposure = 0.1f, contrast = 0.25f, saturation = 1.2f, vibrance = 0.3f, temp = 0.35f,
            shadow = floatArrayOf(0.03f, 0f, -0.02f), highlight = floatArrayOf(0.05f, 0.03f, 0f)
        )), 0.15f),
        Preset("Vintage", build(Grade(
            contrast = 0.15f, saturation = 0.75f, temp = 0.25f, fade = 0.1f,
            shadow = floatArrayOf(-0.01f, 0.03f, 0f), highlight = floatArrayOf(0.05f, 0.04f, -0.04f)
        )), 0.4f),
        Preset("Film Warm", build(Grade(
            contrast = 0.25f, saturation = 1.05f, temp = 0.3f, fade = 0.04f,
            shadow = floatArrayOf(0f, 0.01f, 0.03f), highlight = floatArrayOf(0.05f, 0.03f, -0.02f)
        )), 0.2f),
        Preset("Film Green", build(Grade(
            contrast = 0.2f, saturation = 0.9f, tint = -0.25f, fade = 0.03f,
            shadow = floatArrayOf(-0.03f, 0.04f, 0.02f), highlight = floatArrayOf(0.03f, 0.03f, -0.01f)
        )), 0.2f),
        Preset("Cyberpunk", build(Grade(
            contrast = 0.35f, saturation = 1.25f, temp = -0.3f, tint = 0.4f,
            shadow = floatArrayOf(0.08f, -0.04f, 0.10f), highlight = floatArrayOf(-0.06f, 0.04f, 0.08f)
        )), 0.35f),
        Preset("Cold Winter", build(Grade(
            exposure = 0.05f, contrast = 0.2f, saturation = 0.8f, temp = -0.45f,
            highlight = floatArrayOf(-0.02f, 0.01f, 0.04f)
        )), 0.2f),
        Preset("Pastel", build(Grade(
            exposure = 0.15f, contrast = -0.3f, saturation = 0.8f, fade = 0.12f,
            highlight = floatArrayOf(0.03f, 0f, 0.04f)
        )), 0f),
        Preset("Vivid", build(Grade(
            contrast = 0.25f, saturation = 1.35f, vibrance = 0.4f
        )), 0.1f),
        Preset("Bleach Bypass", build(Grade(
            contrast = 0.55f, saturation = 0.45f
        )), 0.3f),
        Preset("Noir", build(Grade(
            contrast = 0.5f, bw = true
        )), 0.5f),
        Preset("Sepia", build(Grade(
            contrast = 0.2f, bw = true, fade = 0.06f,
            shadow = floatArrayOf(0.02f, 0f, -0.03f), highlight = floatArrayOf(0.10f, 0.05f, -0.04f)
        )), 0.35f)
    )

    fun build(p: Grade): ByteArray {
        val out = ByteArray(N * N * N * 3)
        val tmp = FloatArray(3)
        val d = (N - 1).toFloat()
        for (gi in 0 until N) for (bi in 0 until N) for (ri in 0 until N) {
            apply(p, ri / d, gi / d, bi / d, tmp)
            val o = (gi * N * N + bi * N + ri) * 3
            out[o] = toByte(tmp[0])
            out[o + 1] = toByte(tmp[1])
            out[o + 2] = toByte(tmp[2])
        }
        return out
    }

    fun toByte(v: Float): Byte = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()

    private fun luma(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b

    private fun curve(x0: Float, k: Float): Float {
        val x = x0.coerceIn(0f, 1f)
        return if (k >= 0f) {
            val s = x * x * (3f - 2f * x)
            x + (s - x) * k
        } else {
            val f = 0.5f + (x - 0.5f) * 0.55f
            x + (f - x) * (-k)
        }
    }

    fun apply(p: Grade, ri: Float, gi: Float, bi: Float, out: FloatArray) {
        val e = 2f.pow(p.exposure)
        var r = ri * e * (1f + p.temp * 0.12f)
        var g = gi * e * (1f - p.tint * 0.08f)
        var b = bi * e * (1f - p.temp * 0.12f)

        r = curve(r, p.contrast); g = curve(g, p.contrast); b = curve(b, p.contrast)

        if (p.bw) {
            val v = r * 0.30f + g * 0.59f + b * 0.11f
            r = v; g = v; b = v
        }

        val l = luma(r, g, b).coerceIn(0f, 1f)
        val ws = (1f - l) * (1f - l)
        val wh = l * l
        r += p.shadow[0] * ws + p.highlight[0] * wh
        g += p.shadow[1] * ws + p.highlight[1] * wh
        b += p.shadow[2] * ws + p.highlight[2] * wh

        if (!p.bw) {
            val l2 = luma(r, g, b)
            val sat = (max(r, max(g, b)) - min(r, min(g, b))).coerceIn(0f, 1f)
            val s = p.saturation * (1f + p.vibrance * (1f - sat))
            r = l2 + (r - l2) * s
            g = l2 + (g - l2) * s
            b = l2 + (b - l2) * s
        }

        r = p.fade + r * (1f - p.fade)
        g = p.fade + g * (1f - p.fade)
        b = p.fade + b * (1f - p.fade)

        out[0] = r.coerceIn(0f, 1f)
        out[1] = g.coerceIn(0f, 1f)
        out[2] = b.coerceIn(0f, 1f)
    }

    /** Nearest-neighbour lookup, used for the coloured preview chips. */
    fun sample(data: ByteArray, r: Float, g: Float, b: Float): Int {
        val d = (N - 1).toFloat()
        val ri = (r * d + 0.5f).toInt().coerceIn(0, N - 1)
        val gi = (g * d + 0.5f).toInt().coerceIn(0, N - 1)
        val bi = (b * d + 0.5f).toInt().coerceIn(0, N - 1)
        val o = (gi * N * N + bi * N + ri) * 3
        return Color.rgb(data[o].toInt() and 0xff, data[o + 1].toInt() and 0xff, data[o + 2].toInt() and 0xff)
    }
}

/** Parser for Adobe/Resolve .cube 3D LUT files. Any size is resampled to 32^3. */
object CubeLut {
    fun parse(text: String): ByteArray {
        var size = 0
        var values: FloatArray? = null
        var idx = 0
        val ws = Regex("\\s+")
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val c = line[0]
            if (c.isDigit() || c == '-' || c == '.' || c == '+') {
                val arr = values ?: throw IllegalArgumentException("LUT_3D_SIZE missing")
                val p = line.split(ws)
                if (p.size < 3) continue
                if (idx + 3 > arr.size) throw IllegalArgumentException("LUT me extra values hain")
                arr[idx] = p[0].toFloat()
                arr[idx + 1] = p[1].toFloat()
                arr[idx + 2] = p[2].toFloat()
                idx += 3
            } else {
                val p = line.split(ws)
                when (p[0].uppercase()) {
                    "LUT_3D_SIZE" -> {
                        size = p.getOrNull(1)?.toIntOrNull() ?: 0
                        if (size !in 2..128) throw IllegalArgumentException("LUT size $size support nahi hai")
                        values = FloatArray(size * size * size * 3)
                        idx = 0
                    }
                    "LUT_1D_SIZE" -> throw IllegalArgumentException("1D LUT support nahi hai, 3D .cube use karo")
                }
            }
        }
        val v = values ?: throw IllegalArgumentException("Ye valid .cube file nahi hai")
        if (idx != v.size) throw IllegalArgumentException("LUT data incomplete hai")
        return resample(v, size)
    }

    private fun resample(src: FloatArray, s: Int): ByteArray {
        val n = Luts.N
        val out = ByteArray(n * n * n * 3)
        fun at(r: Int, g: Int, b: Int, c: Int): Float = src[((b * s + g) * s + r) * 3 + c]
        val k = (s - 1).toFloat() / (n - 1)
        for (gi in 0 until n) for (bi in 0 until n) for (ri in 0 until n) {
            val fr = ri * k
            val fg = gi * k
            val fb = bi * k
            val r0 = fr.toInt().coerceAtMost(s - 2)
            val g0 = fg.toInt().coerceAtMost(s - 2)
            val b0 = fb.toInt().coerceAtMost(s - 2)
            val dr = fr - r0
            val dg = fg - g0
            val db = fb - b0
            val o = (gi * n * n + bi * n + ri) * 3
            for (c in 0..2) {
                val c00 = at(r0, g0, b0, c) * (1f - dr) + at(r0 + 1, g0, b0, c) * dr
                val c10 = at(r0, g0 + 1, b0, c) * (1f - dr) + at(r0 + 1, g0 + 1, b0, c) * dr
                val c01 = at(r0, g0, b0 + 1, c) * (1f - dr) + at(r0 + 1, g0, b0 + 1, c) * dr
                val c11 = at(r0, g0 + 1, b0 + 1, c) * (1f - dr) + at(r0 + 1, g0 + 1, b0 + 1, c) * dr
                val c0 = c00 * (1f - dg) + c10 * dg
                val c1 = c01 * (1f - dg) + c11 * dg
                out[o + c] = Luts.toByte(c0 * (1f - db) + c1 * db)
            }
        }
        return out
    }
}

/** Keeps imported .cube files inside the app so they survive restarts. */
object LutStore {
    private fun dir(ctx: Context): File = File(ctx.filesDir, "luts").apply { mkdirs() }

    fun loadAll(ctx: Context): List<Preset> {
        val files = dir(ctx).listFiles()?.filter { it.name.endsWith(".cube", ignoreCase = true) } ?: return emptyList()
        return files.sortedBy { it.lastModified() }.mapNotNull { f ->
            try {
                Preset(f.nameWithoutExtension, CubeLut.parse(f.readText()), 0f, f)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun import(ctx: Context, uri: Uri): Preset {
        val display = displayName(ctx, uri) ?: "LUT.cube"
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            ?: throw IOException("File read nahi hui")
        val data = CubeLut.parse(text)
        val base = display.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9 _-]"), "_")
            .take(24)
            .ifBlank { "LUT" }
        var f = File(dir(ctx), "$base.cube")
        var i = 2
        while (f.exists()) {
            f = File(dir(ctx), "$base $i.cube"); i++
        }
        f.writeText(text)
        return Preset(f.nameWithoutExtension, data, 0f, f)
    }

    private fun displayName(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }
}
