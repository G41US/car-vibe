package com.g41us.carvibe

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Replaces the bezel keys that sit on the dead part of the touch panel.
 * A small floating button on the right edge stays on top of every app
 * (including CarPlay / Android Auto). Tapping it opens:
 *   Volume −   level   Volume +   Mute (hold = screen off)   Screen off
 * Same as the hardware power key: tap mutes, hold turns the screen off.
 */
class ControlsService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var ui: Context
    private lateinit var prefs: Prefs
    private var bubble: View? = null
    private var panel: View? = null
    private var level: TextView? = null
    private var levelBar: View? = null
    private var muteIcon: ImageView? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private val autoHide = Runnable { hidePanel() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        // Same 1024-wide scaling as the activities.
        ui = createConfigurationContext(Scale.override(this))
        startForeground(NOTIFICATION_ID, notification())
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        restyle()
        return START_STICKY
    }

    override fun onDestroy() {
        Net.main.removeCallbacks(autoHide)
        removeView(panel); panel = null
        removeView(bubble); bubble = null
        super.onDestroy()
    }

    private fun dp(v: Float): Int = (v * ui.resources.displayMetrics.density).roundToInt()

    private fun removeView(v: View?) {
        if (v != null) try { wm.removeView(v) } catch (e: Exception) { }
    }

    private fun overlayParams(w: Int, h: Int, focusable: Boolean = false) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE),
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.END }

    private fun notification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Floating controls", NotificationManager.IMPORTANCE_MIN))
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_volume)
            .setContentTitle("Car Vibe controls")
            .setContentText("Volume, mute and screen off buttons")
            .setOngoing(true)
            .build()
    }

    /* ----------------------------------------------------------- bubble */

    @SuppressLint("ClickableViewAccessibility")
    private fun addBubble() {
        val size = dp(56f)
        val view = FrameLayout(ui).apply {
            contentDescription = "Volume and screen controls"
            addView(ImageView(ui).apply {
                setImageResource(R.drawable.ic_volume)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
            }, FrameLayout.LayoutParams(size, size))
        }
        bubbleParams = overlayParams(size, size).apply {
            x = dp(10f)
            // Default: low on the right edge, clear of the unit's own floating ball near the top.
            y = prefs.bubbleY.takeIf { it >= 0 } ?: dp(380f)
        }

        var downY = 0f
        var startY = 0
        var dragging = false
        view.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downY = e.rawY; startY = bubbleParams.y; dragging = false
                    v.alpha = 1f
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - downY
                    if (abs(dy) > dp(8f)) dragging = true
                    if (dragging) {
                        val max = ui.resources.displayMetrics.heightPixels - size
                        bubbleParams.y = (startY + dy).roundToInt().coerceIn(0, max.coerceAtLeast(0))
                        try { wm.updateViewLayout(v, bubbleParams) } catch (ex: Exception) { }
                        if (panel != null) hidePanel()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) prefs.bubbleY = bubbleParams.y
                    else if (panel == null) showPanel() else hidePanel()
                    restyle()
                }
            }
            true
        }
        try { wm.addView(view, bubbleParams) } catch (e: Exception) { stopSelf(); return }
        bubble = view
        restyle()
    }

    private fun restyle() {
        val accent = prefs.accent
        bubble?.let { b ->
            b.background = Ui.oval(Color.parseColor("#E60B0F16")).apply { setStroke(dp(1.5f), Ui.withAlpha(accent, 0xAA)) }
            ((b as FrameLayout).getChildAt(0) as ImageView).setColorFilter(accent)
            b.alpha = if (panel == null) 0.8f else 1f
            // The home screen has its own volume controls, so the button steps aside there.
            b.visibility = if (homeVisible) View.GONE else View.VISIBLE
        }
        if (homeVisible && panel != null) hidePanel()
    }

    /* ----------------------------------------------------------- panel */

    private fun showPanel() {
        if (panel != null) return
        val accent = prefs.accent
        val row = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
            background = Ui.round(Color.parseColor("#F20B0F16"), dp(26f).toFloat(), Color.parseColor("#2A3446"), dp(1f))
            elevation = dp(8f).toFloat()
        }

        row.addView(button(R.drawable.ic_vol_down, "Volume down") { volume(AudioManager.ADJUST_LOWER) })

        val levelBox = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        level = TextView(ui).apply {
            textSize = 20f
            setTextColor(ui.getColor(R.color.text))
            typeface = ui.resources.getFont(R.font.manrope_semibold)
            gravity = Gravity.CENTER
        }
        val track = FrameLayout(ui).apply { background = Ui.round(ui.getColor(R.color.track), dp(2f).toFloat()) }
        levelBar = View(ui).apply {
            background = Ui.round(accent, dp(2f).toFloat())
            pivotX = 0f
        }
        track.addView(levelBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        levelBox.addView(level)
        levelBox.addView(track, LinearLayout.LayoutParams(dp(56f), dp(4f)).apply { topMargin = dp(6f) })
        row.addView(levelBox, LinearLayout.LayoutParams(dp(76f), dp(64f)))

        row.addView(button(R.drawable.ic_vol_up, "Volume up") { volume(AudioManager.ADJUST_RAISE) })
        row.addView(divider())
        val mute = button(R.drawable.ic_mute, "Mute. Hold to turn the screen off") { volume(AudioManager.ADJUST_TOGGLE_MUTE) }
        mute.setOnLongClickListener { screenOff(); true }
        muteIcon = (mute as FrameLayout).getChildAt(0) as ImageView
        row.addView(mute)
        row.addView(button(R.drawable.ic_moon, "Turn the screen off") { screenOff() })

        val params = overlayParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            x = bubbleParams.x + dp(56f) + dp(10f)
            y = (bubbleParams.y - dp(12f)).coerceAtLeast(0)
        }
        try { wm.addView(row, params) } catch (e: Exception) { return }
        panel = row
        refreshLevel()
        bumpAutoHide()
    }

    private fun hidePanel() {
        Net.main.removeCallbacks(autoHide)
        removeView(panel)
        panel = null
        level = null
        levelBar = null
        muteIcon = null
        restyle()
    }

    private fun bumpAutoHide() {
        Net.main.removeCallbacks(autoHide)
        Net.main.postDelayed(autoHide, 5000)
    }

    private fun button(icon: Int, description: String, onClick: () -> Unit): View =
        FrameLayout(ui).apply {
            contentDescription = description
            Ui.clickable(this, Ui.oval(ui.getColor(R.color.chip)))
            addView(ImageView(ui).apply {
                setImageResource(icon)
                setColorFilter(ui.getColor(R.color.text))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
            }, FrameLayout.LayoutParams(dp(60f), dp(60f)))
            setOnClickListener { onClick(); bumpAutoHide() }
            layoutParams = LinearLayout.LayoutParams(dp(60f), dp(60f)).apply {
                marginStart = dp(4f); marginEnd = dp(4f)
            }
        }

    private fun divider(): View = View(ui).apply {
        setBackgroundColor(Color.parseColor("#2A3446"))
        layoutParams = LinearLayout.LayoutParams(dp(1f), dp(40f)).apply { marginStart = dp(6f); marginEnd = dp(6f) }
    }

    /* ----------------------------------------------------------- actions */

    private fun volume(direction: Int) {
        Volume.change(this, direction)
        refreshLevel()
    }

    private fun refreshLevel() {
        val v = Volume.read(this)
        level?.text = if (v.muted) "Muted" else "${v.level}"
        level?.textSize = if (v.muted) 15f else 20f
        levelBar?.scaleX = if (v.muted) 0f else v.fraction
        muteIcon?.setColorFilter(if (v.muted) prefs.accent else ui.getColor(R.color.text))
    }

    private fun screenOff() {
        hidePanel()
        startActivity(Intent(this, ScreenOffActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        private const val CHANNEL = "controls"
        private const val NOTIFICATION_ID = 41

        @Volatile var homeVisible = false

        fun canShow(ctx: Context) = Settings.canDrawOverlays(ctx)

        /** Called by the home screen so the floating button hides while home is showing. */
        fun setHomeVisible(ctx: Context, visible: Boolean) {
            homeVisible = visible
            sync(ctx)
        }

        /** Starts (or restyles) the floating controls if they're on and allowed. */
        fun sync(ctx: Context) {
            val prefs = Prefs(ctx)
            val intent = Intent(ctx, ControlsService::class.java)
            if (prefs.floatingControls && canShow(ctx)) {
                try { ctx.startForegroundService(intent) } catch (e: Exception) { }
            } else {
                ctx.stopService(intent)
            }
        }
    }
}

/**
 * "Screen off": a black window at the lowest backlight. Sound keeps playing.
 * Touch anywhere (on the working part of the screen) to turn it back on.
 */
class ScreenOffActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        val black = View(this).apply {
            setBackgroundColor(Color.BLACK)
            contentDescription = "Screen off. Tap to turn on."
            setOnClickListener { finish() }
        }
        setContentView(black)
    }

    @Deprecated("Back also wakes the screen")
    override fun onBackPressed() = finish()
}
