package com.priyanshu.teji

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/** Stop-loss / target / liquidation / pending-order triggers and fired events, shared with the web app. */
object TriggerStore {
    private const val PREFS = "teji_bg"
    private val lock = Any()

    fun setTriggers(ctx: Context, part: String, json: String) = synchronized(lock) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("trig_$part", json).apply()
    }

    fun triggers(ctx: Context, part: String): JSONArray = synchronized(lock) {
        try {
            JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("trig_$part", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
    }

    fun total(ctx: Context): Int = triggers(ctx, "stocks").length() + triggers(ctx, "crypto").length()

    /** Records fired events and removes every trigger of the same position or order. */
    fun fire(ctx: Context, part: String, events: List<JSONObject>) = synchronized(lock) {
        if (events.isEmpty()) return@synchronized
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val refs = events.map { it.optString("ref") }.toSet()
        val old = try { JSONArray(prefs.getString("trig_$part", "[]")) } catch (e: Exception) { JSONArray() }
        val keep = JSONArray()
        for (i in 0 until old.length()) {
            val t = old.getJSONObject(i)
            if (t.optString("ref") !in refs) keep.put(t)
        }
        val ev = try { JSONArray(prefs.getString("ev_$part", "[]")) } catch (e: Exception) { JSONArray() }
        events.forEach { ev.put(it) }
        prefs.edit().putString("trig_$part", keep.toString()).putString("ev_$part", ev.toString()).apply()
    }

    fun hasEvents(ctx: Context, part: String): Boolean = synchronized(lock) {
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("ev_$part", "[]") ?: "[]"
        s.length > 2
    }

    fun takeEvents(ctx: Context, part: String): String = synchronized(lock) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val s = prefs.getString("ev_$part", "[]") ?: "[]"
        prefs.edit().putString("ev_$part", "[]").apply()
        s
    }
}

/**
 * Keeps paper trades working while Teji is minimised or closed: checks live prices
 * every few seconds and fires stop loss, target, liquidation, pending limit / SL-M
 * orders and the 15:20 intraday square-off. The web app applies the fills when it reopens.
 */
class TriggerService : Service() {

    companion object {
        @Volatile var appVisible = false
        @Volatile var isRunning = false
        private const val CHANNEL = "teji_watch"
        private const val ALERTS = "teji_alerts"
        private const val NOTIF_ID = 7001

        fun ensureRunning(ctx: Context) {
            try {
                val i = Intent(ctx, TriggerService::class.java)
                ctx.startForegroundService(i)
            } catch (e: Exception) {
                // the system refused to start it right now; the next sync tries again
            }
        }
    }

    private val api = LiveApi()
    @Volatile private var running = false
    private var worker: Thread? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Net.init(this)
        channels()
        val n = watchNotification(TriggerStore.total(this))
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(NOTIF_ID, n)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        isRunning = true
        if (!running) {
            running = true
            worker = Thread({ loop() }, "teji-watch").also { it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        isRunning = false
        worker?.interrupt()
        api.shutdown()
        releaseWake()
        super.onDestroy()
    }

    private fun loop() {
        var lastCount = -1
        while (running) {
            try {
                val total = TriggerStore.total(this)
                if (total == 0) break
                if (!appVisible) {
                    holdWake()
                    checkPart("crypto")
                    checkPart("stocks")
                } else {
                    releaseWake()
                }
                val now = TriggerStore.total(this)
                if (now != lastCount) {
                    lastCount = now
                    getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, watchNotification(now))
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                // network hiccup; try again next round
            }
            try {
                Thread.sleep(if (appVisible) 4000 else 2500)
            } catch (e: InterruptedException) {
                break
            }
        }
        running = false
        isRunning = false
        releaseWake()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
        }
        stopSelf()
    }

    private fun istMinutes(): Pair<Boolean, Int> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return Pair(dow != Calendar.SATURDAY && dow != Calendar.SUNDAY, cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))
    }

    private fun checkPart(part: String) {
        val trig = TriggerStore.triggers(this, part)
        if (trig.length() == 0) return
        if (part == "stocks" && !api.marketOpenNow()) return
        val (weekday, mins) = istMinutes()
        val syms = HashSet<String>()
        for (i in 0 until trig.length()) syms.add(trig.getJSONObject(i).optString("sym"))
        val prices = api.lastPrices(part, syms.toList())
        val fired = ArrayList<JSONObject>()
        val firedRefs = HashSet<String>()
        for (i in 0 until trig.length()) {
            val t = trig.getJSONObject(i)
            val ref = t.optString("ref")
            if (ref in firedRefs) continue
            val ltp = prices[t.optString("sym")] ?: continue
            val dir = t.optInt("dir", 1)
            val px = t.optDouble("price", 0.0)
            val kind = t.optString("kind")
            val hit = when (kind) {
                "sl", "liq" -> if (dir > 0) ltp <= px else ltp >= px
                "tgt" -> if (dir > 0) ltp >= px else ltp <= px
                "lim" -> if (dir > 0) ltp <= px else ltp >= px
                "slm" -> if (dir > 0) ltp >= px else ltp <= px
                "sq" -> part == "stocks" && weekday && mins >= 15 * 60 + 20
                else -> false
            }
            if (!hit) continue
            // a pending intraday order cannot fill after 15:20
            if ((kind == "lim" || kind == "slm") && t.optBoolean("mis") && part == "stocks" && mins >= 15 * 60 + 20) continue
            val fill = if (kind == "liq") px else ltp
            fired.add(JSONObject().put("ref", ref).put("kind", kind).put("sym", t.optString("sym")).put("price", fill).put("time", System.currentTimeMillis()))
            firedRefs.add(ref)
        }
        if (fired.isEmpty()) return
        TriggerStore.fire(this, part, fired)
        fired.forEach { alert(part, it) }
    }

    private fun alert(part: String, ev: JSONObject) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val title = when (ev.optString("kind")) {
            "sl" -> "Stop loss hit"
            "tgt" -> "Target hit"
            "liq" -> "Position liquidated"
            "sq" -> "Intraday square-off"
            "lim" -> "Limit order filled"
            "slm" -> "SL-M order filled"
            else -> "Order update"
        }
        val cur = if (part == "crypto") "$" else "₹"
        val price = ev.optDouble("price")
        val text = "${ev.optString("sym")} at $cur${if (price >= 100) String.format("%,.2f", price) else String.format("%.4f", price)} · paper trade"
        val b = Notification.Builder(this, ALERTS)
        val n = b.setSmallIcon(R.drawable.ic_stat_teji)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        nm.notify((ev.optString("ref") + ev.optString("kind")).hashCode(), n)
    }

    private fun openApp(): PendingIntent {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun watchNotification(count: Int): Notification {
        val b = Notification.Builder(this, CHANNEL)
        return b.setSmallIcon(R.drawable.ic_stat_teji)
            .setContentTitle("Teji is watching your trades")
            .setContentText(if (count == 1) "1 stop loss, target or pending order" else "$count stop losses, targets and pending orders")
            .setContentIntent(openApp())
            .setOngoing(true)
            .build()
    }

    private fun channels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Background order watching", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while Teji checks your stop losses and pending orders in the background"
        })
        nm.createNotificationChannel(NotificationChannel(ALERTS, "Order alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Stop loss, target and order fills that happened while the app was closed"
        })
    }

    private fun holdWake() {
        if (wake?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "teji:watch").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWake() {
        try {
            if (wake?.isHeld == true) wake?.release()
        } catch (e: Exception) {
        }
    }
}
