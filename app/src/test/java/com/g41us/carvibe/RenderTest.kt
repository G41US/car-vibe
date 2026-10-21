package com.g41us.carvibe

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Starts each screen on a simulated 1024x552 Android 8.1 head unit (the area
 * under its top bar) and saves what it draws as PNGs in build/screens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27], qualifiers = "w1024dp-h552dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderTest {

    private fun snap(activity: Activity, name: String) {
        val root: View = activity.window.decorView
        val w = 1024
        val h = 552
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun home() {
        val prefs = Prefs(RuntimeEnvironment.getApplication())
        prefs.setupDone = true
        prefs.carModel = "[Car model]"
        val activity = Robolectric.buildActivity(HomeActivity::class.java).setup().get()
        snap(activity, "home")
    }

    @Test
    fun homeConnected() {
        val prefs = Prefs(RuntimeEnvironment.getApplication())
        prefs.setupDone = true
        prefs.carModel = "[Car model]"
        val activity = Robolectric.buildActivity(HomeActivity::class.java).setup().get()
        val field = HomeActivity::class.java.getDeclaredField("phoneState").apply { isAccessible = true }
        field.set(activity, PhoneState(true, "Marvelous's S21", 82))
        HomeActivity::class.java.getDeclaredMethod("renderPhone").apply { isAccessible = true }.invoke(activity)
        snap(activity, "home_connected")
    }

    @Test
    fun settings() {
        Prefs(RuntimeEnvironment.getApplication()).setupDone = true
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        snap(activity, "settings")
    }

    @Test
    fun setup() {
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        snap(activity, "setup")
    }

    @Test
    fun apps() {
        val activity = Robolectric.buildActivity(AppsActivity::class.java).setup().get()
        snap(activity, "apps")
    }
}
