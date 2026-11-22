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

    /** 0 = Bin off (seedha replace). */
    var binDays: Int
        get() = sp.getInt("binDays", 7)
        set(value) = sp.edit().putInt("binDays", value).apply()

    /** Jo files pehle compress ya check ho chuki hain, unhe dobara nahi chhedte. */
    @Synchronized
    fun processed(): MutableSet<String> = HashSet(sp.getStringSet("processed", emptySet()) ?: emptySet())

    @Synchronized
    fun markProcessed(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val set = processed()
        set.addAll(keys)
        sp.edit().putStringSet("processed", set).apply()
    }

    @Synchronized
    fun unmarkProcessed(key: String) {
        val set = processed()
        if (set.remove(key)) sp.edit().putStringSet("processed", set).apply()
    }
}
