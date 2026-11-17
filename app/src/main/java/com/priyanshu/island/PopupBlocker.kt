package com.priyanshu.island

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenPolicy

/**
 * System ke notification popups (heads-up) band karta hai taaki sab kuch
 * sirf island pe aaye.
 *
 * Android ka ek apna "mode" (Do Not Disturb jaisa) banata hai jisme:
 *  - messages / app notifications ka popup aur sound band
 *  - notifications phir bhi upar wali list (shade) me rehti hain, kuch delete nahi hota
 *  - calls, alarms, media, system sounds bilkul normal
 * Island khud halki sound + vibration deta hai.
 */
object PopupBlocker {
    private const val RULE_KEY = "zen_rule_id"
    @Volatile
    var lastError: String? = null
    private val COND: Uri = Uri.parse("condition://com.priyanshu.island/popups")

    fun supported(): Boolean = Build.VERSION.SDK_INT >= 29

    private fun nm(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun hasAccess(ctx: Context): Boolean = try {
        nm(ctx).isNotificationPolicyAccessGranted
    } catch (e: Exception) {
        false
    }

    /** Settings ke hisaab se mode ON/OFF. Return: popups abhi block hain ya nahi */
    fun sync(ctx: Context, serviceRunning: Boolean): Boolean {
        val p = Prefs.get(ctx)
        return set(ctx, serviceRunning && Prefs.enabled(p) && Prefs.block(p))
    }

    private fun set(ctx: Context, on: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        if (!hasAccess(ctx)) return false
        val nm = nm(ctx)
        val p = Prefs.get(ctx)
        return try {
            val existing = p.getString(RULE_KEY, null)?.takeIf { nm.getAutomaticZenRule(it) != null }
            if (!on) {
                if (existing != null) {
                    nm.setAutomaticZenRuleState(existing, Condition(COND, "Island", Condition.STATE_FALSE))
                }
                return false
            }
            val ruleId: String = existing ?: createRule(ctx, nm).also { p.edit().putString(RULE_KEY, it).apply() }
            nm.setAutomaticZenRuleState(ruleId, Condition(COND, "Island", Condition.STATE_TRUE))
            lastError = null
            true
        } catch (e: Exception) {
            lastError = e.javaClass.simpleName + ": " + (e.message ?: "")
            false
        }
    }

    private fun createRule(ctx: Context, nm: NotificationManager): String {
        val policy = ZenPolicy.Builder()
            .disallowAllSounds()
            .allowAlarms(true)
            .allowMedia(true)
            .allowSystem(true)
            .allowCalls(ZenPolicy.PEOPLE_TYPE_ANYONE)
            .allowRepeatCallers(true)
            .showAllVisualEffects()
            .showPeeking(false)
            .build()
        val rule = AutomaticZenRule(
            "Dynamic Island",
            null,
            ComponentName(ctx, MainActivity::class.java),
            COND,
            policy,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            true
        )
        return nm.addAutomaticZenRule(rule)
    }

    /** Settings screen pe dikhane ke liye: blocker sach me chal raha hai ya nahi */
    fun status(ctx: Context, serviceRunning: Boolean): String {
        val p = Prefs.get(ctx)
        if (!Prefs.block(p)) return "Switch OFF hai"
        if (!supported()) return "❌ Ye feature Android 10+ pe hi chalega"
        if (!hasAccess(ctx)) return "❌ Do Not Disturb access nahi mila. Neeche wala button dabao"
        if (!serviceRunning) return "❌ Pehle Accessibility ON karo"
        if (!Prefs.enabled(p)) return "❌ Island OFF hai"
        val filter = try {
            nm(ctx).currentInterruptionFilter
        } catch (e: Exception) {
            -1
        }
        if (filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
            filter == NotificationManager.INTERRUPTION_FILTER_ALARMS ||
            filter == NotificationManager.INTERRUPTION_FILTER_NONE
        ) return "✅ System popups band hain. Sab island pe aayega"
        val err = lastError
        return if (err != null) "❌ Mode ON nahi hua: $err" else "❌ Mode ON nahi hua (filter=$filter). Switch OFF-ON karke dekho"
    }
}
