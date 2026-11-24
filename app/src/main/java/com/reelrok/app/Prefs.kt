package com.reelrok.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saari settings aur stats ek jagah. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("reelrok", Context.MODE_PRIVATE)

    var blockShorts: Boolean
        get() = sp.getBoolean("block_shorts", true)
        set(v) = sp.edit().putBoolean("block_shorts", v).apply()

    var blockReels: Boolean
        get() = sp.getBoolean("block_reels", true)
        set(v) = sp.edit().putBoolean("block_reels", v).apply()

    var disclosureAccepted: Boolean
        get() = sp.getBoolean("disclosure_ok", false)
        set(v) = sp.edit().putBoolean("disclosure_ok", v).apply()

    /** Is time tak blocking band (millis). 0 = pause nahi hai. */
    var pausedUntil: Long
        get() = sp.getLong("paused_until", 0L)
        set(v) = sp.edit().putLong("paused_until", v).apply()

    val isPaused: Boolean get() = System.currentTimeMillis() < pausedUntil

    val totalBlocks: Int get() = sp.getInt("total", 0)

    val todayBlocks: Int
        get() = if (sp.getString("day", "") == today()) sp.getInt("today", 0) else 0

    fun addBlock() {
        val t = today()
        val todayCount = if (sp.getString("day", "") == t) sp.getInt("today", 0) else 0
        sp.edit()
            .putString("day", t)
            .putInt("today", todayCount + 1)
            .putInt("total", totalBlocks + 1)
            .apply()
    }

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
