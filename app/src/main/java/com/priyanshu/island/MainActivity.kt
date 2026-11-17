package com.priyanshu.island

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/** Settings screen - permissions, position, size, test buttons */
class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var accStatus: TextView
    private lateinit var notifStatus: TextView
    private lateinit var blockStatus: TextView

    private val gray = Color.parseColor("#8E8E93")
    private val cardColor = Color.parseColor("#1C1C1E")
    private val blue = Color.parseColor("#0A84FF")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)

        val scroll = ScrollView(this).apply { setBackgroundColor(Color.BLACK) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(72), dp(20), dp(56))
        }
        scroll.addView(col)

        col.addView(label("Dynamic Island", 32f, Color.WHITE, true))
        col.addView(label("iPhone jaisa island, ab tere Android pe", 15f, gray), topLp(6))

        // ---- Setup
        val setup = card(col, "Setup (sirf ek baar)")
        accStatus = label("", 14f, Color.WHITE)
        setup.addView(accStatus, topLp(6))
        setup.addView(button("1. Accessibility ON karo") {
            open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, topLp(10))
        notifStatus = label("", 14f, Color.WHITE)
        setup.addView(notifStatus, topLp(18))
        setup.addView(button("2. Notification access do") {
            open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }, topLp(10))
        setup.addView(
            label(
                "Setting grey ya 'Restricted' dikhe? Neeche wala button dabao → upar right ⋮ → 'Allow restricted settings' → phir wapas aake ON karo.",
                13f, gray
            ), topLp(18)
        )
        setup.addView(button("App info kholo", secondary = true) {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }, topLp(10))

        // ---- Features
        val feat = card(col, "Features")
        toggle(feat, "Island ON", Prefs.ENABLED)
        toggle(feat, "Messages / notifications", Prefs.NOTIF)
        toggle(feat, "Music player", Prefs.MUSIC)
        toggle(feat, "Charging animation", Prefs.CHARGE)

        // ---- Popup blocker
        val block = card(col, "System popup band karo")
        block.addView(
            label(
                "ON karne pe WhatsApp, Insta, SMS sab ke upar se aane wale system popup band ho jayenge. Sab sirf island pe aayega, halki sound + vibration ke saath. Notifications upar wali list me safe rahengi. Calls aur alarm normal bajenge.",
                13f, gray
            ), topLp(6)
        )
        toggle(block, "Sab kuch sirf island pe", Prefs.BLOCK, false) { on ->
            if (on) {
                if (!PopupBlocker.supported()) {
                    Toast.makeText(this, "Ye feature Android 10+ pe hi chalega", Toast.LENGTH_LONG).show()
                } else if (!PopupBlocker.hasAccess(this)) {
                    Toast.makeText(this, "List me 'Dynamic Island' ko ON karo, phir wapas aao", Toast.LENGTH_LONG).show()
                    open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }
            PopupBlocker.sync(this, IslandService.instance != null)
            refreshBlockStatus()
        }
        blockStatus = label("", 14f, Color.WHITE)
        block.addView(blockStatus, topLp(8))
        block.addView(button("Do Not Disturb access do", secondary = true) {
            open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }, topLp(12))
        block.addView(
            label("Phir bhi kisi app ka popup aaye to us app ka 'Pop on screen' band kar do:", 13f, gray),
            topLp(16)
        )
        block.addView(button("WhatsApp popup band karo", secondary = true) {
            openAppNotifs("com.whatsapp")
        }, topLp(10))
        block.addView(button("Instagram popup band karo", secondary = true) {
            openAppNotifs("com.instagram.android")
        }, topLp(10))

        // ---- Position
        val pos = card(col, "Position aur size")
        pos.addView(
            label(
                "Island apne aap front camera dhundh ke uske upar baithta hai. Thoda idhar-udhar lage to yahan se adjust karo (0 = auto).",
                13f, gray
            ), topLp(4)
        )
        slider(pos, "Upar / neeche", Prefs.Y, -30, 30, 0, "")
        slider(pos, "Left / right", Prefs.X, -150, 150, 0, "")
        slider(pos, "Circle size", Prefs.IDLE, -12, 30, 0, "")
        slider(pos, "Message kitni der dikhe", Prefs.DUR, 2, 12, Prefs.DEF_DUR, " sec")
        pos.addView(button("Reset (auto camera position)", secondary = true) {
            prefs.edit().putInt(Prefs.Y, 0).putInt(Prefs.X, 0).putInt(Prefs.IDLE, 0).apply()
            recreate()
        }, topLp(14))

        // ---- Test
        val test = card(col, "Test karo")
        test.addView(button("Test message") { withIsland { it.test() } }, topLp(8))
        test.addView(button("Test charging", secondary = true) { withIsland { it.testCharge() } }, topLp(10))
        test.addView(
            label("Tap = khulega  ·  Long press = battery  ·  Upar swipe = band", 13f, gray),
            topLp(16)
        )

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        accStatus.text = if (isAccessibilityOn()) "✅ Accessibility ON hai" else "❌ Accessibility OFF hai"
        notifStatus.text = if (isNotifOn()) "✅ Notification access mil gaya" else "❌ Notification access nahi hai"
        // DND access dekar wapas aaye ho to popup blocker chalu kar do
        PopupBlocker.sync(this, IslandService.instance != null)
        refreshBlockStatus()
    }

    private fun refreshBlockStatus() {
        blockStatus.text = PopupBlocker.status(this, IslandService.instance != null)
    }

    private fun openAppNotifs(pkg: String) {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        try {
            startActivity(i)
            Toast.makeText(this, "Messages / Chats channel kholo → 'Pop on screen' OFF", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ye app phone me nahi mila", Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------ helpers

    private fun isAccessibilityOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return s.contains("$packageName/")
    }

    private fun isNotifOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return s.contains("$packageName/")
    }

    private fun withIsland(block: (IslandController) -> Unit) {
        val c = IslandService.instance?.controller
        if (c == null) {
            Toast.makeText(this, "Pehle Accessibility ON karo", Toast.LENGTH_SHORT).show()
        } else {
            block(c)
        }
    }

    private fun open(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Ye setting nahi khul payi, Settings me manually dhundo", Toast.LENGTH_LONG).show()
        }
    }

    private fun topLp(top: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun label(t: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun card(parent: LinearLayout, title: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(cardColor)
            }
        }
        c.addView(label(title, 17f, Color.WHITE, true))
        parent.addView(c, topLp(22))
        return c
    }

    private fun button(text: String, secondary: Boolean = false, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(if (secondary) blue else Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(16), dp(13), dp(16), dp(13))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (secondary) Color.parseColor("#2C2C2E") else blue)
            }
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun toggle(
        parent: LinearLayout,
        title: String,
        key: String,
        def: Boolean = true,
        onChange: ((Boolean) -> Unit)? = null
    ) {
        val sw = Switch(this).apply {
            text = title
            textSize = 15f
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean(key, def)
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(key, checked).apply()
                onChange?.invoke(checked)
            }
        }
        parent.addView(sw, topLp(4))
    }

    private fun slider(parent: LinearLayout, title: String, key: String, lo: Int, hi: Int, def: Int, unit: String) {
        val value = prefs.getInt(key, def)
        val lbl = label("$title: $value$unit", 14f, Color.WHITE)
        parent.addView(lbl, topLp(16))
        val sb = SeekBar(this)
        sb.max = hi - lo
        sb.progress = value - lo
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = p + lo
                lbl.text = "$title: $v$unit"
                if (fromUser) prefs.edit().putInt(key, v).apply()
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        parent.addView(sb, topLp(6))
    }
}
