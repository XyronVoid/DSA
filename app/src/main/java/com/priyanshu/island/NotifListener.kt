package com.priyanshu.island

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Notifications (WhatsApp, Instagram, SMS...) aur music player ki info
 * padh ke island ko bhejta hai.
 */
class NotifListener : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: NotifListener? = null
    }

    private var msm: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val lastShown = HashMap<String, String>()
    private val main = Handler(Looper.getMainLooper())

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            pick(currentSessions())
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            push()
        }

        override fun onSessionDestroyed() {
            pick(currentSessions())
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        msm = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        try {
            msm?.addOnActiveSessionsChangedListener(sessionsListener, myComponent())
        } catch (_: Exception) {
        }
        pick(currentSessions())
    }

    override fun onListenerDisconnected() {
        try {
            msm?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
        }
        controller?.unregisterCallback(callback)
        controller = null
        if (instance === this) instance = null
        IslandService.instance?.controller?.onMedia(null)
        super.onListenerDisconnected()
    }

    private fun myComponent() = ComponentName(this, NotifListener::class.java)

    private fun currentSessions(): List<MediaController>? = try {
        msm?.getActiveSessions(myComponent())
    } catch (e: Exception) {
        null
    }

    /** Jo player abhi baj raha hai use choose karo */
    private fun pick(list: List<MediaController>?) {
        val playing = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val keep = list?.firstOrNull { it.sessionToken == controller?.sessionToken }
        val best = playing ?: keep ?: list?.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback, main)
        }
        push()
    }

    private fun push() {
        val c = controller
        val md = c?.metadata
        val info = if (c == null || md == null) {
            null
        } else {
            val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
            val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
            val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            val isPlaying = c.playbackState?.state == PlaybackState.STATE_PLAYING
            if (title.isBlank()) null else MediaInfo(title, artist, art, isPlaying, c.packageName)
        }
        IslandService.instance?.controller?.onMedia(info)
    }

    fun refresh() = pick(currentSessions())

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun prev() {
        controller?.transportControls?.skipToPrevious()
    }

    fun openPlayer() {
        val c = controller ?: return
        if (!Util.sendPending(this, c.sessionActivity)) Util.launchApp(this, c.packageName)
    }

    // ------------------------------------------------------- notifications

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val island = IslandService.instance?.controller ?: return
        if (sbn.packageName == packageName || sbn.isOngoing) return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val cat = n.category
        if (cat == Notification.CATEGORY_TRANSPORT || cat == Notification.CATEGORY_PROGRESS ||
            cat == Notification.CATEGORY_SERVICE || cat == Notification.CATEGORY_SYSTEM
        ) return

        // Silent notifications skip karo
        try {
            val r = NotificationListenerService.Ranking()
            if (currentRanking.getRanking(sbn.key, r) &&
                r.importance < NotificationManager.IMPORTANCE_DEFAULT
            ) return
        } catch (_: Exception) {
        }

        val ex = n.extras ?: return
        if (ex.getString(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true) return

        var title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        var text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()

        // WhatsApp/Telegram jaise chat apps: sabse latest message nikaalo
        @Suppress("DEPRECATION")
        val msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
        val last = msgs?.lastOrNull() as? Bundle
        if (last != null) {
            val t = last.getCharSequence("text")?.toString()
            if (!t.isNullOrBlank()) {
                val sender = last.getCharSequence("sender")?.toString()
                text = if (!sender.isNullOrBlank() && sender != title) "$sender: $t" else t
            }
        }
        if (title.isBlank() && text.isBlank()) return

        // Same message dobara na dikhe
        val sig = "$title|$text"
        if (lastShown[sbn.key] == sig) return
        if (lastShown.size > 200) lastShown.clear()
        lastShown[sbn.key] = sig

        val pm = packageManager
        val appName = try {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            sbn.packageName
        }
        val icon = try {
            pm.getApplicationIcon(sbn.packageName)
        } catch (e: Exception) {
            null
        }
        val pic = try {
            n.getLargeIcon()?.loadDrawable(this)
        } catch (e: Exception) {
            null
        }
        if (title.isBlank()) title = appName

        island.onNotification(NotifInfo(sbn.key, appName, title, text, icon, pic, n.contentIntent))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        lastShown.remove(sbn.key)
        IslandService.instance?.controller?.onNotificationRemoved(sbn.key)
    }
}
