package com.priyanshu.lutcam

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt
import android.opengl.GLES20 as G

/**
 * Draws the live camera feed through a 3D LUT on the GPU.
 * "AI Auto" measures each few frames (brightness + colour cast) and applies
 * smooth exposure / white-balance correction before the LUT.
 */
class GradeRenderer(
    private val view: GLSurfaceView,
    private val listener: Listener
) : GLSurfaceView.Renderer {

    interface Listener {
        fun onSurfaceTextureReady(st: SurfaceTexture)
        fun onCaptured(bmp: Bitmap)
        fun onRenderError(msg: String)
    }

    @Volatile var contentW = 1080
    @Volatile var contentH = 1920
    @Volatile var intensity = 1f
    @Volatile var vignette = 0f
    @Volatile var showOriginal = false
    @Volatile var autoEnhance = true
    @Volatile private var captureRequested = false

    private val lock = Any()
    private var currentLut: ByteArray? = null
    private var pendingLut: ByteArray? = null

    private var st: SurfaceTexture? = null
    private var oesTex = 0
    private var lutTex = 0
    private var progGrade = 0
    private var progPlain = 0
    private var viewW = 1
    private var viewH = 1
    private val texMatrix = FloatArray(16)
    private var frame = 0L
    private val gains = floatArrayOf(1f, 1f, 1f)
    private val targetGains = floatArrayOf(1f, 1f, 1f)

    private var statFbo = 0
    private var capFbo = 0
    private var capTex = 0
    private var capW = 0
    private var capH = 0
    private val statBuf: ByteBuffer = ByteBuffer.allocateDirect(STAT * STAT * 4).order(ByteOrder.nativeOrder())

    private val posBuf = floatBuf(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val texBuf = floatBuf(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))

    fun setLut(data: ByteArray, vig: Float) {
        synchronized(lock) {
            currentLut = data
            pendingLut = data
        }
        vignette = vig
        view.requestRender()
    }

    fun requestCapture() {
        captureRequested = true
        view.requestRender()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            progGrade = Gl.program(VS, FS_GRADE)
            progPlain = Gl.program(VS, FS_PLAIN)
        } catch (e: Exception) {
            listener.onRenderError(e.message ?: "Shader error")
            return
        }
        val t = IntArray(1)
        G.glGenTextures(1, t, 0)
        oesTex = t[0]
        G.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        Gl.texParams(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)

        // Old GL objects died with the previous context.
        lutTex = 0
        statFbo = 0
        capFbo = 0; capTex = 0; capW = 0; capH = 0

        st?.release()
        val s = SurfaceTexture(oesTex)
        s.setOnFrameAvailableListener { view.requestRender() }
        st = s
        synchronized(lock) { pendingLut = currentLut }
        listener.onSurfaceTextureReady(s)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = width
        viewH = height
    }

    override fun onDrawFrame(gl: GL10?) {
        val s = st ?: return
        try {
            s.updateTexImage()
        } catch (e: Exception) {
            return
        }
        s.getTransformMatrix(texMatrix)

        val lut = synchronized(lock) {
            val p = pendingLut
            pendingLut = null
            p
        }
        if (lut != null) uploadLut(lut)

        frame++
        if (autoEnhance) {
            if (frame % 6L == 0L) measure()
        } else {
            targetGains.fill(1f)
        }
        for (i in 0..2) gains[i] += (targetGains[i] - gains[i]) * 0.08f

        G.glBindFramebuffer(G.GL_FRAMEBUFFER, 0)
        G.glViewport(0, 0, viewW, viewH)
        G.glClearColor(0f, 0f, 0f, 1f)
        G.glClear(G.GL_COLOR_BUFFER_BIT)

        // Centre-crop the camera image so it fills the screen.
        val va = viewW.toFloat() / viewH
        val ca = contentW.toFloat() / contentH
        val sx: Float
        val sy: Float
        if (ca > va) {
            sx = ca / va; sy = 1f
        } else {
            sx = 1f; sy = va / ca
        }
        drawCamera(sx, sy, !showOriginal)

        if (captureRequested) {
            captureRequested = false
            try {
                capture()
            } catch (e: Exception) {
                G.glBindFramebuffer(G.GL_FRAMEBUFFER, 0)
                listener.onRenderError("Capture fail: ${e.message}")
            }
        }
    }

    private fun drawCamera(sx: Float, sy: Float, graded: Boolean) {
        val useGrade = graded && lutTex != 0
        val p = if (useGrade) progGrade else progPlain
        G.glUseProgram(p)
        val aPos = G.glGetAttribLocation(p, "aPos")
        val aTex = G.glGetAttribLocation(p, "aTex")
        G.glEnableVertexAttribArray(aPos)
        G.glVertexAttribPointer(aPos, 2, G.GL_FLOAT, false, 0, posBuf)
        G.glEnableVertexAttribArray(aTex)
        G.glVertexAttribPointer(aTex, 2, G.GL_FLOAT, false, 0, texBuf)
        G.glUniformMatrix4fv(G.glGetUniformLocation(p, "uTexMatrix"), 1, false, texMatrix, 0)
        G.glUniform2f(G.glGetUniformLocation(p, "uScale"), sx, sy)

        G.glActiveTexture(G.GL_TEXTURE0)
        G.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        G.glUniform1i(G.glGetUniformLocation(p, "uCam"), 0)

        if (useGrade) {
            G.glActiveTexture(G.GL_TEXTURE1)
            G.glBindTexture(G.GL_TEXTURE_2D, lutTex)
            G.glUniform1i(G.glGetUniformLocation(p, "uLut"), 1)
            G.glUniform1f(G.glGetUniformLocation(p, "uIntensity"), intensity)
            G.glUniform3f(G.glGetUniformLocation(p, "uGain"), gains[0], gains[1], gains[2])
            G.glUniform1f(G.glGetUniformLocation(p, "uVignette"), vignette)
            G.glActiveTexture(G.GL_TEXTURE0)
        }
        G.glDrawArrays(G.GL_TRIANGLE_STRIP, 0, 4)
        G.glDisableVertexAttribArray(aPos)
        G.glDisableVertexAttribArray(aTex)
    }

    /** Reads a tiny 32x32 copy of the raw frame and works out exposure + white balance. */
    private fun measure() {
        if (statFbo == 0) {
            try {
                statFbo = Gl.makeFbo(STAT, STAT)[0]
            } catch (e: Exception) {
                autoEnhance = false
                return
            }
        }
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, statFbo)
        G.glViewport(0, 0, STAT, STAT)
        drawCamera(1f, 1f, false)
        statBuf.position(0)
        G.glReadPixels(0, 0, STAT, STAT, G.GL_RGBA, G.GL_UNSIGNED_BYTE, statBuf)
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, 0)

        var r = 0f
        var g = 0f
        var b = 0f
        val n = STAT * STAT
        for (i in 0 until n) {
            val o = i * 4
            r += (statBuf.get(o).toInt() and 0xff)
            g += (statBuf.get(o + 1).toInt() and 0xff)
            b += (statBuf.get(o + 2).toInt() and 0xff)
        }
        val div = n * 255f
        r /= div; g /= div; b /= div

        val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val avg = (r + g + b) / 3f
        val ex = sqrt(0.45f / lum.coerceAtLeast(0.03f)).coerceIn(0.8f, 1.5f)
        val ch = floatArrayOf(r, g, b)
        for (i in 0..2) {
            val wb = if (ch[i] < 0.03f) 1f else (1f + (avg / ch[i] - 1f) * 0.6f).coerceIn(0.8f, 1.25f)
            targetGains[i] = wb * ex
        }
    }

    /** Renders the full (uncropped) graded frame offscreen and hands back a Bitmap (upside down). */
    private fun capture() {
        val maxTex = IntArray(1)
        G.glGetIntegerv(G.GL_MAX_TEXTURE_SIZE, maxTex, 0)
        var w = contentW
        var h = contentH
        val lim = maxTex[0].coerceAtLeast(1024)
        if (w > lim || h > lim) {
            val k = lim.toFloat() / maxOf(w, h)
            w = (w * k).toInt(); h = (h * k).toInt()
        }
        if (capFbo == 0 || capW != w || capH != h) {
            if (capFbo != 0) {
                G.glDeleteFramebuffers(1, intArrayOf(capFbo), 0)
                G.glDeleteTextures(1, intArrayOf(capTex), 0)
            }
            val r = Gl.makeFbo(w, h)
            capFbo = r[0]; capTex = r[1]; capW = w; capH = h
        }
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, capFbo)
        G.glViewport(0, 0, w, h)
        drawCamera(1f, 1f, true)
        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        G.glReadPixels(0, 0, w, h, G.GL_RGBA, G.GL_UNSIGNED_BYTE, buf)
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, 0)
        G.glViewport(0, 0, viewW, viewH)
        buf.rewind()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(buf)
        listener.onCaptured(bmp)
    }

    private fun uploadLut(data: ByteArray) {
        if (lutTex == 0) {
            val t = IntArray(1)
            G.glGenTextures(1, t, 0)
            lutTex = t[0]
            G.glBindTexture(G.GL_TEXTURE_2D, lutTex)
            Gl.texParams(G.GL_TEXTURE_2D)
        }
        G.glBindTexture(G.GL_TEXTURE_2D, lutTex)
        G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 1)
        val bb = ByteBuffer.allocateDirect(data.size).order(ByteOrder.nativeOrder())
        bb.put(data)
        bb.position(0)
        G.glTexImage2D(
            G.GL_TEXTURE_2D, 0, G.GL_RGB, Luts.N * Luts.N, Luts.N, 0,
            G.GL_RGB, G.GL_UNSIGNED_BYTE, bb
        )
    }

    companion object {
        private const val STAT = 32

        private fun floatBuf(a: FloatArray): FloatBuffer {
            val fb = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            fb.put(a)
            fb.position(0)
            return fb
        }

        private val VS = """
            attribute vec4 aPos;
            attribute vec4 aTex;
            uniform mat4 uTexMatrix;
            uniform vec2 uScale;
            varying vec2 vTex;
            varying vec2 vPos;
            void main() {
                gl_Position = vec4(aPos.xy * uScale, 0.0, 1.0);
                vTex = (uTexMatrix * aTex).xy;
                vPos = aPos.xy;
            }
        """.trimIndent()

        private val FS_PLAIN = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uCam;
            varying vec2 vTex;
            void main() {
                gl_FragColor = vec4(texture2D(uCam, vTex).rgb, 1.0);
            }
        """.trimIndent()

        private val FS_GRADE = """
            #extension GL_OES_EGL_image_external : require
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform samplerExternalOES uCam;
            uniform sampler2D uLut;
            uniform float uIntensity;
            uniform vec3 uGain;
            uniform float uVignette;
            varying vec2 vTex;
            varying vec2 vPos;
            const float N = 32.0;

            vec3 applyLut(vec3 c) {
                float b = c.b * (N - 1.0);
                float b0 = floor(b);
                float b1 = min(b0 + 1.0, N - 1.0);
                float f = b - b0;
                float x = (c.r * (N - 1.0) + 0.5) / (N * N);
                float y = (c.g * (N - 1.0) + 0.5) / N;
                vec3 s0 = texture2D(uLut, vec2(x + b0 / N, y)).rgb;
                vec3 s1 = texture2D(uLut, vec2(x + b1 / N, y)).rgb;
                return mix(s0, s1, f);
            }

            void main() {
                vec3 c = texture2D(uCam, vTex).rgb;
                c = clamp(c * uGain, 0.0, 1.0);
                vec3 g = applyLut(c);
                c = mix(c, g, uIntensity);
                float d = length(vPos);
                c *= 1.0 - uVignette * smoothstep(0.55, 1.45, d);
                gl_FragColor = vec4(c, 1.0);
            }
        """.trimIndent()
    }
}
