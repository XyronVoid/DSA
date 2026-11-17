package com.priyanshu.island

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build

/** Ek notification jo island pe dikhega */
data class NotifInfo(
    val key: String,
    val appName: String,
    val title: String,
    val text: String,
    val icon: Drawable?,
    val pic: Drawable?,
    val intent: PendingIntent?
)

/** Jo gaana abhi chal raha hai */
data class MediaInfo(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val pkg: String
)

/** Saari settings ek jagah */
object Prefs {
    const val ENABLED = "enabled"
    const val NOTIF = "notif"
    const val MUSIC = "music"
    const val CHARGE = "charge"
    const val BLOCK = "block_popups"
    // Ye sab camera ki auto-position ke upar "adjustment" hain (0 = bilkul camera pe)
    const val Y = "y_adj"
    const val X = "x_adj"
    const val IDLE = "size_adj"
    const val DUR = "duration"

    const val DEF_DUR = 5

    fun get(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("island", Context.MODE_PRIVATE)

    fun enabled(p: SharedPreferences) = p.getBoolean(ENABLED, true)
    fun notif(p: SharedPreferences) = p.getBoolean(NOTIF, true)
    fun music(p: SharedPreferences) = p.getBoolean(MUSIC, true)
    fun charge(p: SharedPreferences) = p.getBoolean(CHARGE, true)
    fun block(p: SharedPreferences) = p.getBoolean(BLOCK, false)
    fun yAdj(p: SharedPreferences) = p.getInt(Y, 0)
    fun xAdj(p: SharedPreferences) = p.getInt(X, 0)
    fun sizeAdj(p: SharedPreferences) = p.getInt(IDLE, 0)
    fun dur(p: SharedPreferences) = p.getInt(DUR, DEF_DUR)
}

object Util {
    /** Notification pe tap karne pe us app ko kholta hai */
    fun sendPending(ctx: Context, pi: PendingIntent?): Boolean {
        if (pi == null) return false
        return try {
            if (Build.VERSION.SDK_INT >= 34) {
                val o = ActivityOptions.makeBasic()
                o.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                pi.send(ctx, 0, null, null, null, null, o.toBundle())
            } else {
                pi.send()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun launchApp(ctx: Context, pkg: String) {
        try {
            val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (_: Exception) {
        }
    }
}
