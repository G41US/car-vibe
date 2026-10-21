package com.g41us.carvibe

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * The whole UI is designed on a 1024-wide canvas (the head unit's screen).
 * We override the density so that 1dp = screenWidth / 1024 px on any screen,
 * which also means 1dp = 1px on this 1024x600 unit, matching the
 * "Pointer location" numbers used for the dead touch zone.
 */
object Scale {
    private const val DESIGN_WIDTH = 1024f

    fun override(base: Context): Configuration {
        val wm = base.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val real = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(real)
        val width = maxOf(real.widthPixels, real.heightPixels).coerceAtLeast(320)
        val height = minOf(real.widthPixels, real.heightPixels).coerceAtLeast(240)
        val dpi = (width * 160f / DESIGN_WIDTH).roundToInt().coerceIn(60, 640)
        val config = Configuration()
        config.densityDpi = dpi
        config.fontScale = 1f
        config.screenWidthDp = (width * 160f / dpi).roundToInt()
        config.screenHeightDp = (height * 160f / dpi).roundToInt()
        return config
    }
}

/** Every screen extends this so it gets the 1024-wide scaling and the saved settings. */
open class BaseActivity : Activity() {
    lateinit var prefs: Prefs

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        applyOverrideConfiguration(Scale.override(newBase))
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
    }

    fun dp(v: Float): Float = v * resources.displayMetrics.density
    fun dpi(v: Float): Int = dp(v).roundToInt()
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("carvibe", Context.MODE_PRIVATE)

    var setupDone: Boolean
        get() = sp.getBoolean("setup_done", false)
        set(v) = sp.edit().putBoolean("setup_done", v).apply()

    var brandName: String
        get() = sp.getString("brand_name", "") ?: ""
        set(v) = sp.edit().putString("brand_name", v).apply()

    var carModel: String
        get() = sp.getString("car_model", "") ?: ""
        set(v) = sp.edit().putString("car_model", v).apply()

    /** 0 = silver (monochrome), 1 = original colours. */
    var logoStyle: Int
        get() = sp.getInt("logo_style", 0)
        set(v) = sp.edit().putInt("logo_style", v).apply()

    var accent: Int
        get() = sp.getInt("accent", DEFAULT_ACCENT)
        set(v) = sp.edit().putInt("accent", v).apply()

    /** Width of the untouchable left strip, in design pixels (1024-wide canvas). */
    var deadZone: Int
        get() = sp.getInt("dead_zone", 170)
        set(v) = sp.edit().putInt("dead_zone", v).apply()

    var glowLine: Boolean
        get() = sp.getBoolean("glow_line", true)
        set(v) = sp.edit().putBoolean("glow_line", v).apply()

    var phonePulse: Boolean
        get() = sp.getBoolean("phone_pulse", true)
        set(v) = sp.edit().putBoolean("phone_pulse", v).apply()

    /** App that runs CarPlay / Android Auto. RLink by default. */
    var phoneApp: String
        get() = sp.getString("phone_app", RLINK) ?: RLINK
        set(v) = sp.edit().putString("phone_app", v).apply()

    fun shortcut(key: String): String? = sp.getString("shortcut_$key", null)
    fun setShortcut(key: String, pkg: String?) {
        val e = sp.edit()
        if (pkg == null) e.remove("shortcut_$key") else e.putString("shortcut_$key", pkg)
        e.apply()
    }

    companion object {
        const val RLINK = "yellow.android.carplay.receiver"
        const val GOOGLE_MAPS = "com.google.android.apps.maps"
        val DEFAULT_ACCENT = Color.parseColor("#5AB4FF")
        val ACCENTS = intArrayOf(
            Color.parseColor("#5AB4FF"),
            Color.parseColor("#3DDBC8"),
            Color.parseColor("#B48CFF"),
            Color.parseColor("#FF8A5C"),
            Color.parseColor("#E8C27A"),
            Color.parseColor("#EEF2F8"),
        )
    }
}

object Ui {
    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    fun round(fill: Int, radius: Float, stroke: Int = 0, strokeWidth: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            if (strokeWidth > 0) setStroke(strokeWidth, stroke)
        }

    fun oval(fill: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
    }

    fun pressable(content: Drawable, pressColor: Int = 0x33FFFFFF): Drawable =
        RippleDrawable(ColorStateList.valueOf(pressColor), content, null)

    fun clickable(view: View, content: Drawable) {
        view.background = pressable(content)
        view.isClickable = true
        view.isFocusable = true
    }
}

/** Tiny HTTP helper; every network call runs on [io] and reports back on [main]. */
object Net {
    const val UA = "CarVibe/1.0 (personal car head unit launcher)"
    val io = Executors.newFixedThreadPool(3)
    val main = Handler(Looper.getMainLooper())

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
        }

    fun getBytes(url: String): ByteArray {
        var target = url
        repeat(5) {
            val c = open(target)
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw java.io.IOException("Redirect without location")
                    target = URL(URL(target), loc).toString()
                    return@repeat
                }
                if (code !in 200..299) throw java.io.IOException("HTTP $code for $target")
                c.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    input.copyTo(out)
                    return out.toByteArray()
                }
            } finally {
                c.disconnect()
            }
        }
        throw java.io.IOException("Too many redirects")
    }

    fun get(url: String): String = String(getBytes(url), Charsets.UTF_8)

    fun <T> async(work: () -> T, done: (T?) -> Unit) {
        io.execute {
            val result = try { work() } catch (e: Exception) { null }
            main.post { done(result) }
        }
    }
}
