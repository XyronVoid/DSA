package com.priyanshu.island

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.CornerPathEffect
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import android.view.animation.Interpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Island ki skin: pure black rounded shape (iPhone jaisa). */
class IslandDrawable(private val density: Float) : Drawable() {
    var radius = 0f
        set(v) {
            field = v
            invalidateSelf()
        }
    var gloss = 0f
        set(v) {
            field = v
            invalidateSelf()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val rect = RectF()

    private fun r(w: Float, h: Float) = max(0f, min(radius, min(w, h) / 2f))

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        rect.set(b)
        val rad = r(rect.width(), rect.height())
        // iPhone jaisa pure black - koi grey nahi
        canvas.drawRoundRect(rect, rad, rad, fill)
    }

    override fun getOutline(outline: Outline) {
        val b = bounds
        outline.setRoundRect(b, r(b.width().toFloat(), b.height().toFloat()))
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** iPhone jaisa "springy" animation - thoda sa bounce karke settle hota hai */
class SpringInterpolator(private val damping: Double, private val freq: Double) : Interpolator {
    override fun getInterpolation(input: Float): Float {
        if (input >= 1f) return 1f
        return (1.0 - exp(-damping * input) * cos(freq * input)).toFloat()
    }
}

/** Play / pause / next / previous icons (code se draw kiye hain) */
class GlyphView(ctx: Context, private var kind: Int) : View(ctx) {
    companion object {
        const val PLAY = 0
        const val PAUSE = 1
        const val NEXT = 2
        const val PREV = 3
    }

    var glyphSize = 0f
    var color = Color.WHITE
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun changeKind(k: Int) {
        if (k != kind) {
            kind = k
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val full = min(width, height).toFloat()
        val s = if (glyphSize > 0f) min(glyphSize, full) else full
        val ox = (width - s) / 2f
        val oy = (height - s) / 2f
        paint.color = color
        paint.pathEffect = null
        path.reset()

        fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            path.moveTo(ox + s * x1, oy + s * y1)
            path.lineTo(ox + s * x2, oy + s * y2)
            path.lineTo(ox + s * x3, oy + s * y3)
            path.close()
        }

        when (kind) {
            PLAY -> tri(0.22f, 0.12f, 0.88f, 0.5f, 0.22f, 0.88f)
            PAUSE -> {
                val r = s * 0.07f
                canvas.drawRoundRect(ox + s * 0.2f, oy + s * 0.12f, ox + s * 0.4f, oy + s * 0.88f, r, r, paint)
                canvas.drawRoundRect(ox + s * 0.6f, oy + s * 0.12f, ox + s * 0.8f, oy + s * 0.88f, r, r, paint)
            }
            NEXT -> {
                tri(0.06f, 0.2f, 0.5f, 0.5f, 0.06f, 0.8f)
                tri(0.5f, 0.2f, 0.94f, 0.5f, 0.5f, 0.8f)
            }
            PREV -> {
                tri(0.94f, 0.2f, 0.5f, 0.5f, 0.94f, 0.8f)
                tri(0.5f, 0.2f, 0.06f, 0.5f, 0.5f, 0.8f)
            }
        }
        paint.pathEffect = CornerPathEffect(s * 0.09f)
        canvas.drawPath(path, paint)
    }
}

/** Music chalte waqt naachne wali bars */
class BarsView(ctx: Context) : View(ctx) {
    var color = Color.WHITE
        set(v) {
            field = v
            invalidate()
        }
    private var playing = true
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val speeds = floatArrayOf(7.3f, 9.1f, 6.1f, 8.2f)
    private val phases = floatArrayOf(0f, 1.4f, 2.3f, 0.6f)

    fun updatePlaying(p: Boolean) {
        playing = p
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = color
        val n = 4
        val gap = width * 0.14f
        val bw = (width - gap * (n - 1)) / n
        val t = (SystemClock.uptimeMillis() % 100000L) / 1000f
        for (i in 0 until n) {
            val f = if (playing) 0.28f + 0.72f * abs(sin(t * speeds[i] + phases[i])) else 0.22f
            val bh = height * f
            val x = i * (bw + gap)
            val top = (height - bh) / 2f
            canvas.drawRoundRect(x, top, x + bw, top + bh, bw / 2f, bw / 2f, paint)
        }
        if (playing && isShown) postInvalidateOnAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        invalidate()
    }
}

/** iPhone jaisa chhota battery icon */
class BatteryView(ctx: Context) : View(ctx) {
    var level = 50
    var fillColor = Color.WHITE
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#80FFFFFF")
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val nub = w * 0.08f
        val sw = h * 0.09f
        stroke.strokeWidth = sw
        body.set(sw / 2f, sw / 2f, w - nub - sw / 2f, h - sw / 2f)
        canvas.drawRoundRect(body, h * 0.3f, h * 0.3f, stroke)

        fill.color = Color.parseColor("#80FFFFFF")
        canvas.drawRoundRect(w - nub, h * 0.33f, w, h * 0.67f, nub / 2f, nub / 2f, fill)

        val inset = sw * 1.6f
        val innerW = (body.width() - 2 * inset) * (level.coerceIn(0, 100) / 100f)
        fill.color = fillColor
        canvas.drawRoundRect(
            body.left + inset, body.top + inset,
            body.left + inset + innerW, body.bottom - inset,
            h * 0.16f, h * 0.16f, fill
        )
    }
}
