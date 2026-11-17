package com.priyanshu.island

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service sirf isliye hai taaki island status bar ke UPAR
 * draw ho sake (normal apps wahan draw nahi kar sakte).
 */
class IslandService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: IslandService? = null
    }

    var controller: IslandController? = null
        private set

    private var receiverOn = false
    private val handler = Handler(Looper.getMainLooper())

    private val power = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_POWER_CONNECTED) {
                // Thoda ruko taaki battery % update ho jaye
                handler.postDelayed({ controller?.onPowerConnected() }, 700)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        controller?.stop()
        controller = IslandController(this).also { it.start() }

        val f = IntentFilter(Intent.ACTION_POWER_CONNECTED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(power, f, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(power, f)
        }
        receiverOn = true

        PopupBlocker.sync(this, true)

        // Agar gaana pehle se chal raha hai to turant dikhao
        NotifListener.instance?.refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onConfigChanged()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (receiverOn) {
            try {
                unregisterReceiver(power)
            } catch (_: Exception) {
            }
            receiverOn = false
        }
        handler.removeCallbacksAndMessages(null)
        // Island band = system popups wapas normal
        PopupBlocker.sync(this, false)
        controller?.stop()
        controller = null
        if (instance === this) instance = null
    }
}
