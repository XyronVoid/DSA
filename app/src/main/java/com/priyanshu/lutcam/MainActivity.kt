package com.priyanshu.lutcam

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : Activity(), GradeRenderer.Listener {

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: GradeRenderer
    private lateinit var camera: CameraController
    private lateinit var chipRow: LinearLayout
    private lateinit var chipScroll: HorizontalScrollView
    private lateinit var nameLabel: TextView
    private lateinit var intensityValue: TextView
    private lateinit var autoBtn: TextView
    private lateinit var thumb: ImageView
    private lateinit var flash: View
    private lateinit var shutter: View
    private lateinit var orientationListener: OrientationEventListener

    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val presets = mutableListOf<Preset>()
    private var current = 1
    private var surfaceTexture: SurfaceTexture? = null
    private var appActive = false
    @Volatile private var mirrored = false
    @Volatile private var deviceOrientation = 0
    private var busy = false
    private var lastUri: Uri? = null

    private val accent = Color.parseColor("#FF8A3D")

    private val holdRunnable = Runnable {
        renderer.showOriginal = true
        glView.requestRender()
        nameLabel.animate().cancel()
        nameLabel.text = "Original"
        nameLabel.alpha = 1f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGE
            window.attributes = lp
        }
        hideSystemBars()

        camera = CameraController(
            this,
            onPreview = { w, h, front ->
                renderer.contentW = w
                renderer.contentH = h
                mirrored = front
            },
            reportError = { msg -> ui.post { toast(msg) } }
        )

        glView = GLSurfaceView(this)
        glView.setEGLContextClientVersion(2)
        renderer = GradeRenderer(glView, this)
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        setContentView(buildUi())

        presets.addAll(Luts.builtIns())
        presets.addAll(LutStore.loadAll(this))
        rebuildChips()
        selectPreset(1, announce = false)
        styleAuto()

        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(o: Int) {
                if (o != ORIENTATION_UNKNOWN) deviceOrientation = ((o + 45) / 90 * 90) % 360
            }
        }
        requestPerms()
    }

    // ---------------- UI ----------------

    private fun buildUi(): View {
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        root.addView(glView, FrameLayout.LayoutParams(MATCH, MATCH))

        flash = View(this).apply {
            setBackgroundColor(Color.WHITE)
            alpha = 0f
        }
        root.addView(flash, FrameLayout.LayoutParams(MATCH, MATCH))

        // Top bar
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(34), dp(16), dp(22))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xAA000000.toInt(), 0x00000000)
            )
        }
        val title = TextView(this).apply {
            text = "LUT CAM"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.2f
        }
        top.addView(title, LinearLayout.LayoutParams(0, WRAP, 1f))
        autoBtn = TextView(this).apply {
            text = "✦  AI AUTO"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { toggleAuto() }
        }
        top.addView(autoBtn)
        root.addView(top, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        // Big centred name of the current look
        nameLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = 0.08f
            setShadowLayer(14f, 0f, 0f, 0xAA000000.toInt())
            gravity = Gravity.CENTER
            alpha = 0f
        }
        root.addView(nameLabel, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))

        // Bottom panel
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(30), 0, dp(22))
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xE6000000.toInt(), 0x00000000)
            )
        }

        val intRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), dp(4))
        }
        intRow.addView(TextView(this).apply {
            text = "Intensity"
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 12f
        })
        intensityValue = TextView(this).apply {
            text = "100%"
            setTextColor(Color.WHITE)
            textSize = 12f
            minWidth = dp(42)
            gravity = Gravity.END
        }
        val seek = SeekBar(this).apply {
            max = 100
            progress = 100
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList = ColorStateList.valueOf(0x55FFFFFF)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    renderer.intensity = p / 100f
                    intensityValue.text = "$p%"
                    glView.requestRender()
                }

                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        intRow.addView(seek, LinearLayout.LayoutParams(0, WRAP, 1f))
        intRow.addView(intensityValue)
        bottom.addView(intRow)

        chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chipScroll.addView(chipRow)
        bottom.addView(chipScroll, LinearLayout.LayoutParams(MATCH, WRAP))

        val controls = FrameLayout(this)
        thumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = oval(0x33FFFFFF, 0x66FFFFFF)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            setOnClickListener { openLast() }
        }
        controls.addView(thumb, FrameLayout.LayoutParams(dp(50), dp(50), Gravity.START or Gravity.CENTER_VERTICAL).apply {
            marginStart = dp(36)
        })
        shutter = View(this).apply {
            background = shutterDrawable()
            setOnClickListener { capture() }
        }
        controls.addView(shutter, FrameLayout.LayoutParams(dp(78), dp(78), Gravity.CENTER))
        val flip = TextView(this).apply {
            text = "⇄"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = oval(0x33FFFFFF, 0x66FFFFFF)
            setOnClickListener { flipCamera() }
        }
        controls.addView(flip, FrameLayout.LayoutParams(dp(50), dp(50), Gravity.END or Gravity.CENTER_VERTICAL).apply {
            marginEnd = dp(36)
        })
        bottom.addView(controls, LinearLayout.LayoutParams(MATCH, dp(96)).apply { topMargin = dp(4) })

        val hint = TextView(this).apply {
            text = "Swipe = LUT badlo  •  Hold = Original dekho"
            setTextColor(0x88FFFFFF.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
        }
        bottom.addView(hint, LinearLayout.LayoutParams(MATCH, WRAP))

        root.addView(bottom, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        setupGestures()
        return root
    }

    private fun oval(fill: Int, stroke: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun shutterDrawable(): LayerDrawable {
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(dp(4), Color.WHITE)
        }
        val inner = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        return LayerDrawable(arrayOf(ring, inner)).apply {
            setLayerInset(1, dp(9), dp(9), dp(9), dp(9))
        }
    }

    private fun rebuildChips() {
        chipRow.removeAllViews()
        presets.forEachIndexed { i, p ->
            val c1 = Luts.sample(p.data, 0.85f, 0.6f, 0.45f)
            val c2 = Luts.sample(p.data, 0.3f, 0.5f, 0.75f)
            val chip = TextView(this).apply {
                text = p.name
                setTextColor(Color.WHITE)
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(5f, 0f, 1f, 0xCC000000.toInt())
                gravity = Gravity.CENTER
                setPadding(dp(16), 0, dp(16), 0)
                tag = intArrayOf(c1, c2)
                setOnClickListener { selectPreset(i) }
                if (p.file != null) {
                    setOnLongClickListener {
                        confirmDelete(i)
                        true
                    }
                }
            }
            chipRow.addView(chip, LinearLayout.LayoutParams(WRAP, dp(40)).apply { marginEnd = dp(8) })
        }
        val add = TextView(this).apply {
            text = "+  .cube LUT"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0x22FFFFFF)
                setStroke(dp(1), 0x88FFFFFF.toInt(), dp(5).toFloat(), dp(4).toFloat())
            }
            setOnClickListener { pickLut() }
        }
        chipRow.addView(add, LinearLayout.LayoutParams(WRAP, dp(40)))
        styleChips()
    }

    private fun styleChips() {
        for (i in presets.indices) {
            val v = chipRow.getChildAt(i) as? TextView ?: continue
            val cols = v.tag as IntArray
            v.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, cols).apply {
                cornerRadius = dp(20).toFloat()
                if (i == current) setStroke(dp(2), Color.WHITE) else setStroke(dp(1), 0x40FFFFFF)
            }
            v.alpha = if (i == current) 1f else 0.8f
        }
    }

    private fun styleAuto() {
        val on = renderer.autoEnhance
        autoBtn.background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            if (on) setColor(accent) else {
                setColor(0x22FFFFFF)
                setStroke(dp(1), 0x66FFFFFF)
            }
        }
        autoBtn.setTextColor(if (on) Color.BLACK else Color.WHITE)
    }

    private fun selectPreset(i: Int, announce: Boolean = true) {
        if (presets.isEmpty()) return
        current = ((i % presets.size) + presets.size) % presets.size
        val p = presets[current]
        renderer.setLut(p.data, p.vignette)
        styleChips()
        val chip = chipRow.getChildAt(current)
        if (chip != null) {
            chipScroll.post {
                chipScroll.smoothScrollTo((chip.left - (chipScroll.width - chip.width) / 2).coerceAtLeast(0), 0)
            }
        }
        if (announce) showLabel(p.name)
    }

    private fun showLabel(t: String) {
        nameLabel.text = t
        nameLabel.animate().cancel()
        nameLabel.alpha = 1f
        nameLabel.animate().alpha(0f).setStartDelay(700).setDuration(500).start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        val det = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                ui.removeCallbacks(holdRunnable)
                return false
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (abs(velocityX) > abs(velocityY) && abs(velocityX) > 600f) {
                    selectPreset(if (velocityX < 0) current + 1 else current - 1)
                    return true
                }
                return false
            }
        })
        glView.setOnTouchListener { _, e ->
            det.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> ui.postDelayed(holdRunnable, 280)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ui.removeCallbacks(holdRunnable)
                    if (renderer.showOriginal) {
                        renderer.showOriginal = false
                        glView.requestRender()
                        nameLabel.animate().alpha(0f).setStartDelay(0).setDuration(250).start()
                    }
                }
            }
            true
        }
    }

    // ---------------- Actions ----------------

    private fun toggleAuto() {
        renderer.autoEnhance = !renderer.autoEnhance
        styleAuto()
        glView.requestRender()
        showLabel(if (renderer.autoEnhance) "AI Auto ON" else "AI Auto OFF")
    }

    private fun flipCamera() {
        camera.facing = if (camera.facing == CameraCharacteristics.LENS_FACING_BACK)
            CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        tryOpenCamera()
    }

    private fun capture() {
        if (busy || surfaceTexture == null) return
        busy = true
        shutter.animate().scaleX(0.86f).scaleY(0.86f).setDuration(90).withEndAction {
            shutter.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }.start()
        flash.alpha = 0.7f
        flash.animate().alpha(0f).setDuration(280).start()
        renderer.requestCapture()
        ui.postDelayed({ busy = false }, 4000)
    }

    override fun onCaptured(bmp: Bitmap) {
        val rot = if (mirrored) (360 - deviceOrientation) % 360 else deviceOrientation
        io.execute {
            try {
                val m = Matrix()
                m.preScale(1f, -1f)
                m.postRotate(rot.toFloat())
                val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (out !== bmp) bmp.recycle()
                val uri = ImageSaver.save(this, out)
                val tw = 160
                val th = (tw * out.height / out.width.toFloat()).toInt().coerceAtLeast(1)
                val small = Bitmap.createScaledBitmap(out, tw, th, true)
                if (small !== out) out.recycle()
                ui.post {
                    busy = false
                    if (uri != null) {
                        lastUri = uri
                        thumb.setImageBitmap(small)
                        toast("Photo saved ✓  (Pictures/LUTCam)")
                    } else {
                        toast("Photo save nahi hui")
                    }
                }
            } catch (e: Throwable) {
                ui.post {
                    busy = false
                    toast("Save error: ${e.message}")
                }
            }
        }
    }

    override fun onRenderError(msg: String) {
        ui.post {
            busy = false
            toast(msg)
        }
    }

    private fun openLast() {
        val uri = lastUri
        if (uri == null) {
            toast("Pehle ek photo lo")
            return
        }
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "image/jpeg")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: Exception) {
            toast("Gallery nahi khuli")
        }
    }

    private fun pickLut() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        try {
            startActivityForResult(i, REQ_LUT)
        } catch (e: Exception) {
            toast("File picker nahi khula")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_LUT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        toast("LUT load ho raha hai…")
        io.execute {
            try {
                val p = LutStore.import(this, uri)
                ui.post {
                    presets.add(p)
                    rebuildChips()
                    selectPreset(presets.size - 1)
                }
            } catch (e: Exception) {
                ui.post { toast("LUT error: ${e.message}") }
            }
        }
    }

    private fun confirmDelete(i: Int) {
        val p = presets.getOrNull(i) ?: return
        AlertDialog.Builder(this)
            .setTitle("\"${p.name}\" delete karein?")
            .setPositiveButton("Delete") { _, _ ->
                p.file?.delete()
                presets.removeAt(i)
                if (current == i) current = 1 else if (current > i) current--
                rebuildChips()
                selectPreset(current, announce = false)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------- Camera + lifecycle ----------------

    private fun hasCamPerm() = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun requestPerms() {
        val need = mutableListOf<String>()
        if (!hasCamPerm()) need.add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT < 29 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), REQ_PERM)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERM) return
        if (hasCamPerm()) tryOpenCamera() else toast("Camera permission chahiye — Settings se allow karo")
    }

    private fun tryOpenCamera() {
        val st = surfaceTexture ?: return
        if (!appActive || !hasCamPerm()) return
        camera.open(st)
    }

    override fun onSurfaceTextureReady(st: SurfaceTexture) {
        ui.post {
            surfaceTexture = st
            tryOpenCamera()
        }
    }

    override fun onResume() {
        super.onResume()
        appActive = true
        hideSystemBars()
        glView.onResume()
        orientationListener.enable()
    }

    override fun onPause() {
        appActive = false
        camera.close()
        surfaceTexture = null
        glView.onPause()
        orientationListener.disable()
        super.onPause()
    }

    override fun onDestroy() {
        camera.shutdown()
        io.shutdown()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val REQ_LUT = 42
        private const val REQ_PERM = 7
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
