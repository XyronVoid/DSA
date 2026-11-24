package com.spacebachao.app.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var mode: Mode
        get() = runCatching { Mode.valueOf(sp.getString("mode", null) ?: Mode.REPLACE.name) }
            .getOrDefault(Mode.REPLACE)
        set(value) = sp.edit().putString("mode", value.name).apply()

    var quality: Quality
        get() = runCatching { Quality.valueOf(sp.getString("quality", null) ?: Quality.BALANCED.name) }
            .getOrDefault(Quality.BALANCED)
        set(value) = sp.edit().putString("quality", value.name).apply()

    /** 0 = Bin off (replace directly). */
    var binDays: Int
        get() = sp.getInt("binDays", 7)
        set(value) = sp.edit().putInt("binDays", value).apply()

    /** Last known Pro state, so Pro keeps working if Play is briefly unreachable. */
    var proCached: Boolean
        get() = sp.getBoolean("proCached", false)
        set(value) = sp.edit().putBoolean("proCached", value).apply()

    /** Total bytes saved since install. */
    var totalSaved: Long
        get() = sp.getLong("totalSaved", 0L)
        set(value) = sp.edit().putLong("totalSaved", value).apply()

    // ---------- Files already handled ----------

    /** Every file that was compressed or checked (never offered again). */
    @Synchronized
    fun processed(): MutableSet<String> = HashSet(sp.getStringSet("processed", emptySet()) ?: emptySet())

    /** Only files that were actually compressed. */
    @Synchronized
    fun saved(): MutableSet<String> = HashSet(sp.getStringSet("saved", emptySet()) ?: emptySet())

    @Synchronized
    fun markProcessed(keys: Collection<String>) = addTo("processed", keys)

    @Synchronized
    fun markSaved(keys: Collection<String>) {
        addTo("processed", keys)
        addTo("saved", keys)
    }

    @Synchronized
    fun unmark(key: String) {
        for (name in arrayOf("processed", "saved")) {
            val set = HashSet(sp.getStringSet(name, emptySet()) ?: emptySet())
            if (set.remove(key)) sp.edit().putStringSet(name, set).apply()
        }
    }

    private fun addTo(name: String, keys: Collection<String>) {
        if (keys.isEmpty()) return
        val set = HashSet(sp.getStringSet(name, emptySet()) ?: emptySet())
        set.addAll(keys)
        sp.edit().putStringSet(name, set).apply()
    }

    /** Original size of all files compressed on the free plan (limit: 5 GB). */
    var freeUsed: Long
        get() = sp.getLong("freeUsed", 0L)
        set(value) = sp.edit().putLong("freeUsed", value).apply()
}
