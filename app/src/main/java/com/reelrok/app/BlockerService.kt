package com.reelrok.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Ye service YouTube aur Instagram ki screen padhti hai.
 * Jaise hi Shorts / Reels ka player dikhe -> Back daba deti hai + "ruk ja" screen dikhati hai.
 */
class BlockerService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private var lastCheck = 0L
    private var lastBlock = 0L
    private var overlay: View? = null

    companion object {
        const val YT = "com.google.android.youtube"
        const val IG = "com.instagram.android"

        // YouTube Shorts player ke view IDs (alag-alag YouTube versions me)
        private val SHORTS_IDS = listOf(
            "$YT:id/reel_recycler",
            "$YT:id/reel_player_page_container",
            "$YT:id/reel_watch_player",
            "$YT:id/reel_watch_fragment_root",
            "$YT:id/shorts_container"
        )

        // Instagram Reels viewer ke view IDs
        private val REELS_IDS = listOf(
            "$IG:id/clips_viewer_view_pager",
            "$IG:id/clips_viewer_container",
            "$IG:id/clips_video_container",
            "$IG:id/clips_swipe_refresh_container"
        )

        private val MESSAGES = listOf(
            "Reel band. Life chalu. ✋",
            "Bas bhai, scroll nahi. 🚫",
            "Ye 30 sec ka chakkar 2 ghante ka hota hai. ⏳",
            "Thumb ko rest de. 👍",
            "Kuch apna bana, unka mat dekh. 🔨"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != YT && pkg != IG) return
        if (!::prefs.isInitialized) prefs = Prefs(this)
        if (prefs.isPaused) return
        if (pkg == YT && !prefs.blockShorts) return
        if (pkg == IG && !prefs.blockReels) return

        // Content-changed events bahut aate hain, isliye har 250ms me ek hi check
        val now = SystemClock.uptimeMillis()
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && now - lastCheck < 250) return
        lastCheck = now

        val root = rootInActiveWindow ?: return
        val ids = if (pkg == YT) SHORTS_IDS else REELS_IDS
        if (isVisible(root, ids)) block()
    }

    private fun isVisible(root: AccessibilityNodeInfo, ids: List<String>): Boolean {
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id) ?: continue
            if (nodes.any { it.isVisibleToUser }) return true
        }
        return false
    }

    private fun block() {
        val now = SystemClock.uptimeMillis()
        if (now - lastBlock < 900) return   // ek hi reel pe baar-baar back na dabe
        lastBlock = now

        performGlobalAction(GLOBAL_ACTION_BACK)
        // Kabhi-kabhi ek back se nahi nikalta (jaise Shorts tab) — 400ms baad dobara check
        handler.postDelayed({
            val r = rootInActiveWindow ?: return@postDelayed
            val pkg = r.packageName?.toString()
            val ids = when (pkg) { YT -> SHORTS_IDS; IG -> REELS_IDS; else -> null }
            if (ids != null && isVisible(r, ids)) performGlobalAction(GLOBAL_ACTION_BACK)
        }, 400)

        prefs.addBlock()
        showOverlay()
    }

    private fun showOverlay() {
        if (overlay != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F20B0B12"))
            setPadding(64, 64, 64, 64)
        }
        val big = TextView(this).apply {
            text = "🛑"
            textSize = 64f
            gravity = Gravity.CENTER
        }
        val msg = TextView(this).apply {
            text = MESSAGES.random()
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 16)
        }
        val sub = TextView(this).apply {
            text = "Aaj ${prefs.todayBlocks} baar bacha liya"
            textSize = 14f
            setTextColor(Color.parseColor("#9AA0B4"))
            gravity = Gravity.CENTER
        }
        box.addView(big); box.addView(msg); box.addView(sub)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(box, lp)
            overlay = box
            box.alpha = 0f
            box.animate().alpha(1f).setDuration(150).start()
            handler.postDelayed({ removeOverlay() }, 1400)
        } catch (_: Exception) {
            overlay = null
        }
    }

    private fun removeOverlay() {
        val v = overlay ?: return
        overlay = null
        v.animate().alpha(0f).setDuration(200).withEndAction {
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) } catch (_: Exception) {}
        }.start()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlay?.let { try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {} }
        overlay = null
        super.onDestroy()
    }
}
