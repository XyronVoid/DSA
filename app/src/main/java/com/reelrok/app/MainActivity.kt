package com.reelrok.app

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var statusCard: MaterialCardView
    private lateinit var statusTitle: TextView
    private lateinit var statusSub: TextView
    private lateinit var enableBtn: MaterialButton
    private lateinit var todayText: TextView
    private lateinit var totalText: TextView
    private lateinit var shortsSwitch: MaterialSwitch
    private lateinit var reelsSwitch: MaterialSwitch
    private lateinit var pauseBtn: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        statusCard = findViewById(R.id.statusCard)
        statusTitle = findViewById(R.id.statusTitle)
        statusSub = findViewById(R.id.statusSub)
        enableBtn = findViewById(R.id.enableBtn)
        todayText = findViewById(R.id.todayCount)
        totalText = findViewById(R.id.totalCount)
        shortsSwitch = findViewById(R.id.shortsSwitch)
        reelsSwitch = findViewById(R.id.reelsSwitch)
        pauseBtn = findViewById(R.id.pauseBtn)

        shortsSwitch.isChecked = prefs.blockShorts
        reelsSwitch.isChecked = prefs.blockReels
        shortsSwitch.setOnCheckedChangeListener { _, on -> confirmToggle(on, shortsSwitch) { prefs.blockShorts = it } }
        reelsSwitch.setOnCheckedChangeListener { _, on -> confirmToggle(on, reelsSwitch) { prefs.blockReels = it } }

        enableBtn.setOnClickListener { openAccessibility() }
        pauseBtn.setOnClickListener {
            if (prefs.isPaused) { prefs.pausedUntil = 0; refresh() } else askPause()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val on = isServiceOn()
        val paused = prefs.isPaused
        when {
            !on -> {
                statusTitle.text = "Blocker OFF hai"
                statusSub.text = "Ek baar Accessibility me ReelRok ON karo, bas."
                statusCard.setCardBackgroundColor(Color.parseColor("#2A1418"))
                enableBtn.visibility = View.VISIBLE
            }
            paused -> {
                val t = SimpleDateFormat("h:mm a", Locale.US).format(Date(prefs.pausedUntil))
                statusTitle.text = "Pause hai ⏸"
                statusSub.text = "$t pe apne aap wapas ON ho jayega."
                statusCard.setCardBackgroundColor(Color.parseColor("#2A2412"))
                enableBtn.visibility = View.GONE
            }
            else -> {
                statusTitle.text = "Protected ✅"
                statusSub.text = "Shorts aur Reels khulte hi band ho jayenge."
                statusCard.setCardBackgroundColor(Color.parseColor("#11261C"))
                enableBtn.visibility = View.GONE
            }
        }
        pauseBtn.text = if (paused) "Pause hatao, abhi ON karo" else "5 min ke liye pause"
        pauseBtn.isEnabled = on
        todayText.text = prefs.todayBlocks.toString()
        totalText.text = prefs.totalBlocks.toString()
    }

    /** Switch OFF karne se pehle 10 sec ruk — impulse wala OFF rokne ke liye. */
    private fun confirmToggle(on: Boolean, sw: MaterialSwitch, save: (Boolean) -> Unit) {
        if (on) { save(true); return }
        sw.isChecked = true
        frictionDialog("Sach me band karna hai?", "Ye blocking hata dega.") {
            sw.setOnCheckedChangeListener(null)
            sw.isChecked = false
            save(false)
            sw.setOnCheckedChangeListener { _, v -> confirmToggle(v, sw, save) }
        }
    }

    private fun askPause() {
        frictionDialog("5 minute ka pause?", "5 min baad blocker khud wapas ON ho jayega.") {
            prefs.pausedUntil = System.currentTimeMillis() + 5 * 60_000
            refresh()
        }
    }

    private fun frictionDialog(title: String, msg: String, onConfirm: () -> Unit) {
        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage("$msg\n\nThoda ruk… soch le.")
            .setPositiveButton("10", null)
            .setNegativeButton("Rehne do", null)
            .create()
        dlg.setOnShowListener {
            val ok = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
            ok.isEnabled = false
            object : CountDownTimer(10_000, 1_000) {
                override fun onTick(ms: Long) { ok.text = "Haan (${ms / 1000 + 1})" }
                override fun onFinish() {
                    ok.text = "Haan"
                    ok.isEnabled = true
                    ok.setOnClickListener { onConfirm(); dlg.dismiss() }
                }
            }.start()
        }
        dlg.show()
    }

    /** Play Store policy: Accessibility use karne se pehle saaf batana zaroori hai. */
    private fun openAccessibility() {
        if (prefs.disclosureAccepted) { goToSettings(); return }
        MaterialAlertDialogBuilder(this)
            .setTitle("Accessibility permission kyun?")
            .setMessage(
                "ReelRok Accessibility Service use karta hai sirf ye pehchanne ke liye ki " +
                "YouTube ya Instagram me Shorts/Reels screen khuli hai, taaki use band kar sake.\n\n" +
                "• Koi data collect, save ya bheja nahi jaata.\n" +
                "• Sirf YouTube aur Instagram dekhe jaate hain.\n" +
                "• App me internet permission hi nahi hai."
            )
            .setPositiveButton("Samajh gaya, aage") { _, _ ->
                prefs.disclosureAccepted = true
                goToSettings()
            }
            .setNegativeButton("Abhi nahi", null)
            .show()
    }

    private fun goToSettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isServiceOn(): Boolean {
        val me = ComponentName(this, BlockerService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (s in splitter) {
            val cn = ComponentName.unflattenFromString(s)
            if (cn != null && cn == me) return true
        }
        return false
    }
}
