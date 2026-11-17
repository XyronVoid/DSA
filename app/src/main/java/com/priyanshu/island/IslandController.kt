package com.priyanshu.island

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Island ka dimaag.
 *
 * Do overlay windows hain:
 *  - stage: bada, fixed, touch-through window jisme island DRAW hota hai.
 *           Ye kabhi resize nahi hota, isliye animation smooth rehta hai.
 *  - touch: chhota invisible window, sirf island ke size ka, jo touch pakad ke
 *           island ko de deta hai. Baaki screen pe touch normal kaam karta hai.
 *
 * Island apne aap front camera (display cutout) dhundh ke uske upar baithta hai.
 */
class IslandController(private val ctx: Context) {

    private enum class Mode { IDLE, COMPACT_MUSIC, COMPACT_INFO, EXPANDED_NOTIF, EXPANDED_MUSIC }

    private val gray = Color.parseColor("#8E8E93")
    private val bodyColor = Color.parseColor("#E5E5EA")
    private val green = Color.parseColor("#30D158")
    private val red = Color.parseColor("#FF453A")

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = Prefs.get(ctx)
    private val handler = Handler(Looper.getMainLooper())
    private val density = ctx.resources.displayMetrics.density

    private var attached = false
    private var mode = Mode.IDLE
    private var media: MediaInfo? = null
    private var currentNotif: NotifInfo? = null

    // Abhi ka (animate hota hua) shape
    private var curW = 0f
    private var curH = 0f
    private var curR = 0f
    private var curTop = 0f
    private var curGloss = 0f

    // Touch window ki jagah (target shape)
    private var tgtLeft = 0
    private var tgtTop = 0

    // Front camera ki position (screen pixels)
    private var camX = 0f
    private var camY = 0f
    private var camSize = 0

    private var lastOrientation = -1
    private var lastWidth = -1

    private var anim: ValueAnimator? = null
    private var content: View? = null

    private var mArt: ImageView? = null
    private var mTitle: TextView? = null
    private var mArtist: TextView? = null
    private var mPlay: GlyphView? = null
    private var mBars: BarsView? = null

    private var accentFor: Bitmap? = null
    private var accentVal = Color.WHITE

    private val collapseRunnable = Runnable { goBase() }
    private val pauseRunnable = Runnable {
        if (mode == Mode.COMPACT_MUSIC && media?.playing != true) goBase()
    }

    private val skin = IslandDrawable(density)

    private val island = FrameLayout(ctx).apply {
        background = skin
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
    }

    private val stage = FrameLayout(ctx).apply {
        clipChildren = false
        clipToPadding = false
    }

    private val touch = object : View(ctx) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                onOutsideTouch()
                return false
            }
            // Touch window ke coordinates -> island ke coordinates
            val lp = island.layoutParams as FrameLayout.LayoutParams
            val e = MotionEvent.obtain(event)
            e.offsetLocation((tgtLeft - lp.leftMargin).toFloat(), (tgtTop - lp.topMargin).toFloat())
            val handled = island.dispatchTouchEvent(e)
            e.recycle()
            return handled
        }
    }

    private fun overlayParams(w: Int, h: Int, extraFlags: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            extraFlags,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        x = 0
        y = 0
        if (Build.VERSION.SDK_INT >= 30) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private val stageLp = overlayParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        (340 * density).roundToInt(),
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    )

    private val touchLp = overlayParams(1, 1, WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            handleTap()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            handleLongPress()
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (velocityY < -500f && abs(velocityY) > abs(velocityX)) {
                goBase()
                return true
            }
            if (velocityY > 500f && abs(velocityY) > abs(velocityX) && mode == Mode.COMPACT_MUSIC) {
                showExpandedMusic()
                return true
            }
            return false
        }
    })

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            Prefs.ENABLED -> refreshAttach()
            Prefs.Y, Prefs.X, Prefs.IDLE -> if (attached) {
                if (mode == Mode.IDLE) snapIdle() else goBase()
            }
            Prefs.MUSIC -> if (attached) goBase()
        }
    }

    init {
        stage.addView(island, FrameLayout.LayoutParams(1, 1, Gravity.TOP or Gravity.LEFT))
        island.setOnTouchListener { _, e ->
            gestures.onTouchEvent(e)
            true
        }
    }

    // ---------------------------------------------------------------- sizes

    private fun dp(v: Float): Float = v * density
    private fun dpi(v: Float): Int = (v * density).roundToInt()
    private val screenW: Int get() = ctx.resources.displayMetrics.widthPixels

    private fun idleSize(): Int {
        val auto = (camSize + dpi(8f)).coerceIn(dpi(22f), dpi(36f))
        return max(dpi(14f), auto + dpi(Prefs.sizeAdj(prefs).toFloat()))
    }

    private fun centerX(): Float = camX + dp(Prefs.xAdj(prefs).toFloat())
    private fun centerY(): Float = camY + dp(Prefs.yAdj(prefs).toFloat())
    private fun compactH(): Int = max(idleSize(), dpi(30f))
    private fun compactW(): Int = min((screenW * 0.6f).roundToInt(), dpi(240f))
    private fun expandedW(): Int = min(screenW - dpi(20f), dpi(420f))

    /** Island ka top taaki wo camera ke center pe rahe */
    private fun topFor(h: Int): Float = max(0f, centerY() - h / 2f)

    /** Island ka left: camera pe centered, par screen se bahar nahi */
    private fun leftFor(w: Float): Float {
        val margin = if (w > screenW * 0.5f) dp(10f) else 0f
        val l = centerX() - w / 2f
        return l.coerceIn(margin, max(margin, screenW - w - margin))
    }

    /** Front camera (punch hole) kahan hai, phone se poochho */
    private fun computeCamera() {
        var rect: Rect? = null
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val r = wm.maximumWindowMetrics.windowInsets.displayCutout?.boundingRectTop
                if (r != null && !r.isEmpty) rect = r
            } catch (_: Exception) {
            }
        }
        if (rect == null && Build.VERSION.SDK_INT >= 28) {
            try {
                val r = stage.rootWindowInsets?.displayCutout?.boundingRects
                    ?.firstOrNull { it.top < dpi(80f) }
                if (r != null && !r.isEmpty) rect = r
            } catch (_: Exception) {
            }
        }
        if (rect != null) {
            camX = rect.exactCenterX()
            camY = rect.exactCenterY()
            // Chauda notch ho to sirf uski height lo
            camSize = if (rect.width() > rect.height() * 2) rect.height() else max(rect.width(), rect.height())
        } else {
            camX = screenW / 2f
            camY = dp(20f)
            camSize = dpi(14f)
        }
    }

    // ------------------------------------------------------------ lifecycle

    fun start() {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        lastOrientation = ctx.resources.configuration.orientation
        lastWidth = screenW
        refreshAttach()
    }

    fun stop() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        detach()
    }

    /** Sirf tab react karo jab rotation / screen size sach me badla ho */
    fun onConfigChanged() {
        val o = ctx.resources.configuration.orientation
        val w = screenW
        if (o == lastOrientation && w == lastWidth) return
        lastOrientation = o
        lastWidth = w
        refreshAttach()
        if (attached) {
            computeCamera()
            goBase()
        }
    }

    private fun isLandscape() =
        ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun refreshAttach() {
        if (Prefs.enabled(prefs) && !isLandscape()) attach() else detach()
    }

    private fun attach() {
        if (attached) return
        try {
            wm.addView(stage, stageLp)
        } catch (e: Exception) {
            return
        }
        try {
            wm.addView(touch, touchLp)
        } catch (e: Exception) {
            try {
                wm.removeView(stage)
            } catch (_: Exception) {
            }
            return
        }
        attached = true
        computeCamera()
        curW = 0f
        curH = 0f
        curR = 0f
        curGloss = 0f
        curTop = centerY()
        applyGeometry()
        goBase()

        // Purane Android pe camera ki info window lagne ke baad milti hai
        stage.post {
            val ox = camX
            val oy = camY
            computeCamera()
            if (attached && (ox != camX || oy != camY) && mode == Mode.IDLE) goBase()
        }
    }

    private fun detach() {
        anim?.cancel()
        anim = null
        handler.removeCallbacksAndMessages(null)
        if (!attached) return
        try {
            wm.removeView(touch)
        } catch (_: Exception) {
        }
        try {
            wm.removeView(stage)
        } catch (_: Exception) {
        }
        attached = false
        island.removeAllViews()
        content = null
        clearMusicRefs()
        mode = Mode.IDLE
    }

    // ------------------------------------------------------------ animation

    private fun morph(tw: Int, th: Int, tr: Float, ttop: Float, tgloss: Float, newContent: View?) {
        if (!attached) return
        anim?.cancel()
        val sw = curW
        val sh = curH
        val sr = curR
        val st = curTop
        val sg = curGloss
        val growing = tw.toFloat() * th >= sw * sh

        setTouch(tw, th, ttop)
        swapContent(newContent, tw, th)

        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = if (growing) 560L else 440L
        a.interpolator = if (growing) SpringInterpolator(8.5, 8.0) else SpringInterpolator(10.0, 6.0)
        a.addUpdateListener { va ->
            val f = va.animatedValue as Float
            val fc = f.coerceIn(0f, 1f)
            curW = sw + (tw - sw) * f
            curH = sh + (th - sh) * f
            curTop = st + (ttop - st) * fc
            curR = sr + (tr - sr) * fc
            curGloss = sg + (tgloss - sg) * fc
            applyGeometry()
        }
        a.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) {
                    curW = tw.toFloat()
                    curH = th.toFloat()
                    curR = tr
                    curTop = ttop
                    curGloss = tgloss
                    applyGeometry()
                }
            }
        })
        anim = a
        a.start()
    }

    private fun applyGeometry() {
        val w = max(1f, curW)
        val h = max(1f, curH)
        val lp = island.layoutParams as FrameLayout.LayoutParams
        lp.width = w.roundToInt()
        lp.height = h.roundToInt()
        lp.leftMargin = leftFor(w).roundToInt()
        lp.topMargin = curTop.roundToInt()
        island.layoutParams = lp
        skin.radius = curR
        skin.gloss = curGloss
        island.elevation = dp(14f) * curGloss
        island.invalidateOutline()
    }

    private fun setTouch(w: Int, h: Int, top: Float) {
        tgtLeft = leftFor(w.toFloat()).roundToInt()
        tgtTop = top.roundToInt()
        touchLp.width = max(1, w)
        touchLp.height = max(1, h)
        touchLp.x = tgtLeft
        touchLp.y = tgtTop
        if (attached) {
            try {
                wm.updateViewLayout(touch, touchLp)
            } catch (_: Exception) {
            }
        }
    }

    /** Slider chalate waqt circle turant naye jagah pe aa jaye */
    private fun snapIdle() {
        anim?.cancel()
        val s = idleSize()
        curW = s.toFloat()
        curH = s.toFloat()
        curR = s / 2f
        curTop = topFor(s)
        curGloss = 0f
        applyGeometry()
        setTouch(s, s, curTop)
    }

    private fun swapContent(newView: View?, w: Int, h: Int) {
        val old = content
        if (old != null && old !== newView) {
            old.animate().cancel()
            old.animate().alpha(0f).setStartDelay(0).setDuration(120)
                .withEndAction { island.removeView(old) }.start()
        }
        content = newView
        if (newView != null && newView.parent == null) {
            newView.alpha = 0f
            newView.scaleX = 0.92f
            newView.scaleY = 0.92f
            newView.pivotX = w / 2f
            newView.pivotY = 0f
            island.addView(newView, FrameLayout.LayoutParams(w, h, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            newView.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(110).setDuration(260)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun scheduleCollapse(ms: Long) {
        handler.removeCallbacks(collapseRunnable)
        handler.postDelayed(collapseRunnable, ms)
    }

    // ---------------------------------------------------------------- modes

    fun goBase() {
        if (!attached) return
        handler.removeCallbacks(collapseRunnable)
        handler.removeCallbacks(pauseRunnable)
        currentNotif = null
        val m = media
        if (m != null && m.playing && Prefs.music(prefs)) showCompactMusic(m) else showIdle()
    }

    private fun showIdle() {
        mode = Mode.IDLE
        clearMusicRefs()
        val s = idleSize()
        morph(s, s, s / 2f, topFor(s), 0f, null)
    }

    private fun showCompactMusic(m: MediaInfo) {
        mode = Mode.COMPACT_MUSIC
        val w = compactW()
        val h = compactH()
        morph(w, h, h / 2f, topFor(h), 0.4f, buildCompactMusic(m, h))
    }

    private fun showExpandedMusic() {
        val m = media ?: return
        mode = Mode.EXPANDED_MUSIC
        val w = expandedW()
        val v = buildExpandedMusic(m)
        val h = measureHeight(v, w)
        morph(w, h, dp(36f), topFor(compactH()), 1f, v)
        scheduleCollapse(7000)
    }

    private fun showInfo(charging: Boolean) {
        mode = Mode.COMPACT_INFO
        clearMusicRefs()
        val w = compactW()
        val h = compactH()
        morph(w, h, h / 2f, topFor(h), 0.4f, buildInfoView(charging, batteryPct()))
        scheduleCollapse(if (charging) 3500L else 2500L)
    }

    private fun bounce() {
        val s = idleSize()
        morph((s * 1.6f).roundToInt(), s, s / 2f, topFor(s), 0.2f, null)
        handler.postDelayed({ if (mode == Mode.IDLE) showIdle() }, 220)
    }

    // --------------------------------------------------------------- events

    fun onNotification(n: NotifInfo) {
        if (!attached || !Prefs.notif(prefs)) return
        currentNotif = n
        mode = Mode.EXPANDED_NOTIF
        clearMusicRefs()
        val w = expandedW()
        val v = buildNotifView(n)
        val h = max(measureHeight(v, w), dpi(64f))
        morph(w, h, dp(36f), topFor(compactH()), 1f, v)
        scheduleCollapse(Prefs.dur(prefs) * 1000L)
    }

    fun onNotificationRemoved(key: String) {
        if (mode == Mode.EXPANDED_NOTIF && currentNotif?.key == key) goBase()
    }

    fun onMedia(m: MediaInfo?) {
        media = m
        if (!attached || !Prefs.music(prefs)) return
        when (mode) {
            Mode.IDLE -> if (m != null && m.playing) showCompactMusic(m)
            Mode.COMPACT_MUSIC -> {
                if (m == null) {
                    goBase()
                } else {
                    updateMusicViews(m)
                    handler.removeCallbacks(pauseRunnable)
                    // Pause hone pe turant band mat karo (buffering me flicker hota hai)
                    if (!m.playing) handler.postDelayed(pauseRunnable, 2500)
                }
            }
            Mode.EXPANDED_MUSIC -> if (m == null) goBase() else updateMusicViews(m)
            else -> {}
        }
    }

    fun onPowerConnected() {
        if (!attached || !Prefs.charge(prefs)) return
        if (mode == Mode.EXPANDED_NOTIF) return
        showInfo(true)
    }

    fun test() {
        onNotification(
            NotifInfo(
                key = "island_test",
                appName = "Messages",
                title = "Rahul",
                text = "Bhai ye dynamic island toh ekdum iPhone jaisa lag raha hai! Poora message yahin dikh raha hai, notification kholne ki zarurat hi nahi.",
                icon = appIcon(ctx.packageName),
                pic = null,
                intent = null
            )
        )
    }

    fun testCharge() = showInfo(true)

    private fun handleTap() {
        island.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        when (mode) {
            Mode.IDLE -> if (media != null && Prefs.music(prefs)) showExpandedMusic() else bounce()
            Mode.COMPACT_MUSIC -> showExpandedMusic()
            Mode.COMPACT_INFO -> goBase()
            Mode.EXPANDED_NOTIF -> {
                Util.sendPending(ctx, currentNotif?.intent)
                goBase()
            }
            Mode.EXPANDED_MUSIC -> goBase()
        }
    }

    private fun handleLongPress() {
        island.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        when (mode) {
            Mode.IDLE -> showInfo(isCharging())
            Mode.COMPACT_MUSIC -> showExpandedMusic()
            else -> {}
        }
    }

    private fun onOutsideTouch() {
        if (mode == Mode.EXPANDED_MUSIC) goBase()
    }

    // ------------------------------------------------------------- builders

    private fun tv(size: Float, color: Int, bold: Boolean = false, lines: Int = 1): TextView =
        TextView(ctx).apply {
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = lines
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }

    private fun lpTop(topDp: Float) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dpi(topDp) }

    private fun roundOutline(r: Float) = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, r)
        }
    }

    private val ovalOutline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }

    private fun measureHeight(v: View, w: Int): Int {
        v.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return v.measuredHeight
    }

    private fun buildNotifView(n: NotifInfo): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(18f), dpi(16f), dpi(20f), dpi(16f))
        }
        val size = dpi(44f)
        val iv = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageDrawable(n.pic ?: n.icon)
            if (n.pic != null) {
                outlineProvider = ovalOutline
                clipToOutline = true
            }
        }
        row.addView(iv, LinearLayout.LayoutParams(size, size))

        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tv(12f, gray).apply { text = n.appName + "  ·  now" })
        col.addView(tv(15.5f, Color.WHITE, bold = true).apply { text = n.title }, lpTop(3f))
        if (n.text.isNotBlank()) {
            col.addView(tv(14.5f, bodyColor, lines = 5).apply {
                text = n.text
                setLineSpacing(0f, 1.1f)
            }, lpTop(3f))
        }
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dpi(12f)
        })
        return row
    }

    private fun buildCompactMusic(m: MediaInfo, h: Int): View {
        clearMusicRefs()
        val pad = (h * 0.16f).roundToInt()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad + dpi(2f), 0, pad + dpi(6f), 0)
        }
        val artSize = (h * 0.68f).roundToInt()
        val art = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = roundOutline(artSize * 0.28f)
            clipToOutline = true
        }
        setArt(art, m)
        row.addView(art, LinearLayout.LayoutParams(artSize, artSize))
        row.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        val bars = BarsView(ctx).apply {
            color = accent(m.art)
            updatePlaying(m.playing)
        }
        row.addView(bars, LinearLayout.LayoutParams(dpi(20f), (h * 0.42f).roundToInt()))
        mArt = art
        mBars = bars
        return row
    }

    private fun buildExpandedMusic(m: MediaInfo): View {
        clearMusicRefs()
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(20f), dpi(20f), dpi(20f), dpi(14f))
        }
        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val artSize = dpi(58f)
        val art = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = roundOutline(dp(14f))
            clipToOutline = true
            setOnClickListener { openPlayer() }
        }
        setArt(art, m)
        top.addView(art, LinearLayout.LayoutParams(artSize, artSize))

        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val title = tv(16f, Color.WHITE, bold = true).apply { text = m.title }
        val artist = tv(14f, gray).apply { text = m.artist }
        texts.addView(title)
        texts.addView(artist, lpTop(4f))
        top.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dpi(14f)
            rightMargin = dpi(10f)
        })
        val bars = BarsView(ctx).apply {
            color = accent(m.art)
            updatePlaying(m.playing)
        }
        top.addView(bars, LinearLayout.LayoutParams(dpi(24f), dpi(20f)))
        col.addView(top)

        val controls = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val prev = glyphBtn(GlyphView.PREV, 26f) { NotifListener.instance?.prev() }
        val play = glyphBtn(if (m.playing) GlyphView.PAUSE else GlyphView.PLAY, 30f) {
            NotifListener.instance?.playPause()
        }
        val next = glyphBtn(GlyphView.NEXT, 26f) { NotifListener.instance?.next() }
        controls.addView(prev, LinearLayout.LayoutParams(dpi(56f), dpi(50f)))
        controls.addView(play, LinearLayout.LayoutParams(dpi(64f), dpi(50f)).apply {
            leftMargin = dpi(26f)
            rightMargin = dpi(26f)
        })
        controls.addView(next, LinearLayout.LayoutParams(dpi(56f), dpi(50f)))
        col.addView(controls, lpTop(10f))

        mArt = art
        mTitle = title
        mArtist = artist
        mPlay = play
        mBars = bars
        return col
    }

    private fun glyphBtn(kind: Int, sizeDp: Float, onClick: () -> Unit): GlyphView =
        GlyphView(ctx, kind).apply {
            glyphSize = dp(sizeDp)
            isClickable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
                scheduleCollapse(7000)
            }
        }

    private fun buildInfoView(charging: Boolean, pct: Int): View {
        val pctColor = when {
            charging -> green
            pct in 0..20 -> red
            else -> Color.WHITE
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(14f), 0, dpi(12f), 0)
        }
        row.addView(tv(13f, Color.WHITE, bold = true).apply { text = if (charging) "Charging" else "Battery" })
        row.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(tv(13f, pctColor, bold = true).apply { text = if (pct >= 0) "$pct%" else "--" })
        val batt = BatteryView(ctx).apply {
            level = pct
            fillColor = pctColor
        }
        row.addView(batt, LinearLayout.LayoutParams(dpi(26f), dpi(13f)).apply { leftMargin = dpi(6f) })
        return row
    }

    // -------------------------------------------------------------- helpers

    private fun updateMusicViews(m: MediaInfo) {
        mArt?.let { setArt(it, m) }
        if (mTitle?.text?.toString() != m.title) mTitle?.text = m.title
        if (mArtist?.text?.toString() != m.artist) mArtist?.text = m.artist
        mPlay?.changeKind(if (m.playing) GlyphView.PAUSE else GlyphView.PLAY)
        mBars?.let {
            val c = accent(m.art)
            if (it.color != c) it.color = c
            it.updatePlaying(m.playing)
        }
    }

    private fun clearMusicRefs() {
        mArt = null
        mTitle = null
        mArtist = null
        mPlay = null
        mBars = null
    }

    /** Same photo dobara set mat karo (flicker se bachne ke liye) */
    private fun setArt(iv: ImageView, m: MediaInfo) {
        val art = m.art
        val key: Any = art ?: m.pkg
        if (iv.tag == key) return
        iv.tag = key
        if (art != null) iv.setImageBitmap(art) else iv.setImageDrawable(appIcon(m.pkg))
    }

    private fun openPlayer() {
        val l = NotifListener.instance
        if (l != null) l.openPlayer() else media?.let { Util.launchApp(ctx, it.pkg) }
        goBase()
    }

    private fun appIcon(pkg: String): Drawable? = try {
        ctx.packageManager.getApplicationIcon(pkg)
    } catch (e: Exception) {
        null
    }

    /** Album art ka main colour (bars isi colour me naachti hain) */
    private fun accent(b: Bitmap?): Int {
        if (b == null) return Color.WHITE
        if (b === accentFor) return accentVal
        val c = try {
            val src = if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b
            val px = Bitmap.createScaledBitmap(src, 1, 1, true).getPixel(0, 0)
            val hsv = FloatArray(3)
            Color.colorToHSV(px, hsv)
            hsv[1] = min(1f, hsv[1] * 1.3f + 0.1f)
            hsv[2] = max(0.8f, hsv[2])
            Color.HSVToColor(hsv)
        } catch (e: Exception) {
            Color.WHITE
        }
        accentFor = b
        accentVal = c
        return c
    }

    private fun batteryManager() = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    private fun batteryPct(): Int = try {
        batteryManager().getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    } catch (e: Exception) {
        -1
    }

    private fun isCharging(): Boolean = try {
        batteryManager().isCharging
    } catch (e: Exception) {
        false
    }
}
