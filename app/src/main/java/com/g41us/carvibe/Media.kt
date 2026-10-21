package com.g41us.carvibe

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Needed for two things: reading what's playing (media sessions are only
 * visible to an enabled notification listener) and noticing when the phone
 * app (RLink) shows its "connected" notification.
 */
class MediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        MediaHub.attach(this)
        scan()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = scan()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = scan()

    private fun scan() {
        val list = try { activeNotifications?.toList() ?: emptyList() } catch (e: Exception) { return }
        MediaHub.updateFromNotifications(this, list)
    }

    companion object {
        @Volatile var instance: MediaListenerService? = null
    }
}

data class NowPlaying(
    val title: String?,
    val artist: String?,
    val art: Bitmap?,
    val playing: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val speed: Float,
    val packageName: String?,
) {
    fun currentPosition(): Long {
        if (!playing || positionUpdatedAt <= 0) return positionMs
        val elapsed = SystemClock.elapsedRealtime() - positionUpdatedAt
        return (positionMs + elapsed * speed).toLong().coerceAtLeast(0)
    }
}

object MediaHub {

    fun interface Listener {
        fun onChanged()
    }

    private val listeners = LinkedHashSet<Listener>()
    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var attached = false

    var nowPlaying: NowPlaying? = null
        private set

    /** The phone app (RLink) has an ongoing notification. */
    var phoneAppActive = false
        private set
    var phoneAppHint: String? = null
        private set

    fun add(l: Listener) { listeners.add(l) }
    fun remove(l: Listener) { listeners.remove(l) }
    private fun changed() { for (l in listeners.toList()) l.onChanged() }

    fun accessGranted(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(':').any { ComponentName.unflattenFromString(it)?.packageName == ctx.packageName }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list ?: emptyList())
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = read()
        override fun onPlaybackStateChanged(state: PlaybackState?) = read()
        override fun onSessionDestroyed() {
            controller = null
            refreshSessions()
        }
    }

    fun attach(ctx: Context) {
        if (!accessGranted(ctx)) return
        val app = ctx.applicationContext
        val msm = manager ?: (app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager).also { manager = it }
        val cn = ComponentName(app, MediaListenerService::class.java)
        try {
            if (!attached) {
                msm.addOnActiveSessionsChangedListener(sessionsListener, cn)
                attached = true
            }
            pick(msm.getActiveSessions(cn))
        } catch (e: SecurityException) {
            attached = false
        }
    }

    private fun refreshSessions() {
        val msm = manager ?: return
        val inst = MediaListenerService.instance ?: return
        try { pick(msm.getActiveSessions(ComponentName(inst, MediaListenerService::class.java))) } catch (e: Exception) { }
    }

    private fun pick(sessions: List<MediaController>) {
        val best = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.packageName == controller?.packageName }
            ?: sessions.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback, Net.main)
        }
        read()
    }

    private fun read() {
        val c = controller
        if (c == null) {
            nowPlaying = null
            changed()
            return
        }
        val md = c.metadata
        val st = c.playbackState
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        nowPlaying = NowPlaying(
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            art = art,
            playing = st?.state == PlaybackState.STATE_PLAYING,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            positionMs = st?.position ?: 0L,
            positionUpdatedAt = st?.lastPositionUpdateTime ?: 0L,
            speed = st?.playbackSpeed ?: 1f,
            packageName = c.packageName,
        )
        changed()
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }

    fun updateFromNotifications(ctx: Context, list: List<StatusBarNotification>) {
        val phonePkg = Prefs(ctx).phoneApp
        val n = list.firstOrNull { it.packageName == phonePkg && it.isOngoing }
            ?: list.firstOrNull { it.packageName == phonePkg }
        val active = n != null
        val hint = n?.notification?.extras?.let { extras ->
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        }?.takeIf { it.isNotBlank() && it.length <= 40 }
        if (active != phoneAppActive || hint != phoneAppHint) {
            phoneAppActive = active
            phoneAppHint = hint
            changed()
        }
    }
}
