package com.g41us.carvibe

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class SettingsActivity : BaseActivity() {

    private lateinit var list: LinearLayout
    private lateinit var spacer: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = hbox(Gravity.TOP).apply { setBackgroundColor(getColor(R.color.bg)) }
        spacer = View(this)
        root.addView(spacer, LinearLayout.LayoutParams(dpi(prefs.deadZone.toFloat()), MATCH))

        val main = vbox().apply { setPadding(dpi(6f), dpi(16f), dpi(18f), 0) }
        val top = hbox()
        val back = ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            setColorFilter(getColor(R.color.text))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = "Back"
            Ui.clickable(this, Ui.round(getColor(R.color.card), dp(16f), getColor(R.color.card_edge), dpi(1f)))
            setOnClickListener { finish() }
        }
        top.addView(back, LinearLayout.LayoutParams(dpi(52f), dpi(52f)))
        top.addView(label("Settings", 26f, getColor(R.color.text), R.font.manrope_semibold),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dpi(16f) })
        main.addView(top)

        list = vbox().apply { setPadding(0, dpi(4f), 0, dpi(24f)) }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(list)
        }
        main.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dpi(8f) })
        root.addView(main, LinearLayout.LayoutParams(0, MATCH, 1f))
        setContentView(root)
    }

    private val updateListener = Updater.Listener { render() }

    override fun onResume() {
        super.onResume()
        Updater.add(updateListener)
        if (Updater.state == Updater.State.IDLE) Updater.check(this)
        render()
    }

    override fun onPause() {
        Updater.remove(updateListener)
        super.onPause()
    }

    private fun render() {
        spacer.layoutParams = spacer.layoutParams.apply { width = dpi(prefs.deadZone.toFloat()) }
        list.removeAllViews()

        section("Updates")
        row("Car Vibe updates", Updater.summary(this)) {
            when (Updater.state) {
                Updater.State.AVAILABLE -> addView(pill("Download", true) { Updater.download(this@SettingsActivity) })
                Updater.State.READY -> addView(pill("Install", true) { Updater.install(this@SettingsActivity) })
                Updater.State.CHECKING, Updater.State.DOWNLOADING -> Unit
                else -> addView(pill("Check", false) { Updater.check(this@SettingsActivity) })
            }
        }

        section("Car")
        row("Car brand", prefs.brandName.ifBlank { "Not set" }) {
            addView(pill("Change", false) { startActivity(Intent(this@SettingsActivity, SetupActivity::class.java)) })
        }
        row("Model", prefs.carModel.ifBlank { "Not set" }) {
            addView(pill("Edit", false) { editModel() })
        }
        row("Logo colour", "Silver matches the dashboard. Original keeps the brand's colours.") {
            addView(pill("Silver", prefs.logoStyle == 0) { prefs.logoStyle = 0; render() })
            addView(gap())
            addView(pill("Original", prefs.logoStyle == 1) { prefs.logoStyle = 1; render() })
        }

        section("Look")
        row("Accent colour", "Glow, dials and highlights") {
            for (c in Prefs.ACCENTS) {
                val selected = c == prefs.accent
                val swatch = View(this@SettingsActivity).apply {
                    val d = Ui.oval(c)
                    if (selected) d.setStroke(dpi(3f), getColor(R.color.text))
                    Ui.clickable(this, d)
                    contentDescription = "Accent colour"
                    setOnClickListener { prefs.accent = c; render() }
                }
                addView(swatch, LinearLayout.LayoutParams(dpi(40f), dpi(40f)).apply { marginStart = dpi(10f) })
            }
        }
        row("Glow line", "Thin light along the bottom edge") {
            addView(pill(if (prefs.glowLine) "On" else "Off", prefs.glowLine) { prefs.glowLine = !prefs.glowLine; render() })
        }
        row("Phone glow pulse", "The connected phone breathes softly") {
            addView(pill(if (prefs.phonePulse) "On" else "Off", prefs.phonePulse) { prefs.phonePulse = !prefs.phonePulse; render() })
        }

        section("Screen")
        row("No-touch strip", "Nothing tappable in the first ${prefs.deadZone}px on the left. Your screen stops responding at about 148px.") {
            addView(roundIcon(R.drawable.ic_minus) { prefs.deadZone = (prefs.deadZone - 10).coerceAtLeast(40); render() })
            addView(label("${prefs.deadZone}", 18f, getColor(R.color.text), R.font.manrope_semibold).apply {
                gravity = Gravity.CENTER
                minWidth = dpi(64f)
            })
            addView(roundIcon(R.drawable.ic_plus) { prefs.deadZone = (prefs.deadZone + 10).coerceAtMost(300); render() })
        }

        section("Phone and apps")
        row("CarPlay / Android Auto app", appName(prefs.phoneApp)) {
            addView(pill("Change", false) {
                Apps.pick(this@SettingsActivity, "Which app runs CarPlay / Android Auto?") { prefs.phoneApp = it.pkg; render() }
            })
        }
        for ((key, title) in listOf("music" to "Music", "radio" to "Radio", "camera" to "Camera", "bluetooth" to "Bluetooth")) {
            val chosen = prefs.shortcut(key)
            val current = Apps.shortcut(this, key)
            val sub = when {
                current == null -> "Not found. Choose an app."
                chosen == null -> "${appName(current)} (found automatically)"
                else -> appName(current)
            }
            row("$title button", sub) {
                if (chosen != null) {
                    addView(pill("Auto", false) { prefs.setShortcut(key, null); render() })
                    addView(gap())
                }
                addView(pill("Change", false) {
                    Apps.pick(this@SettingsActivity, "Which app should $title open?") { prefs.setShortcut(key, it.pkg); render() }
                })
            }
        }

        section("Access")
        val loc = LocationHub.hasPermission(this)
        row("Location", if (loc) "Allowed: speed, map and weather work" else "Needed for speed, map and weather") {
            if (!loc) addView(pill("Allow", true) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 7)
            }) else addView(icon(R.drawable.ic_check, prefs.accent, 26f))
        }
        val media = MediaHub.accessGranted(this)
        row("Media access", if (media) "Allowed: music card and phone detection work" else "Lets the music card show what's playing") {
            if (!media) addView(pill("Open", true) { openNotificationAccess() })
            else addView(icon(R.drawable.ic_check, prefs.accent, 26f))
        }
        row("Default home screen", "Make Car Vibe open when you press Home") {
            addView(pill("Open", false) { openHomeSettings() })
        }

        section("System")
        row("Android settings", "The unit's full Android settings") {
            addView(pill("Open", false) {
                if (!Apps.launch(this@SettingsActivity, "com.android.settings")) start(Intent(Settings.ACTION_SETTINGS))
            })
        }
        row("Developer options", "Pointer location, USB debugging") {
            addView(pill("Open", false) { start(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) })
        }
        row("Run setup again", "Brand, model, phone and permissions") {
            addView(pill("Start", false) { startActivity(Intent(this@SettingsActivity, SetupActivity::class.java)) })
        }
        row("Car Vibe", "Version ${Updater.currentName(this)} · github.com/${Updater.REPO}") {}
    }

    private fun section(title: String) {
        list.addView(label(title, 12f, getColor(R.color.text_mute), R.font.manrope_bold, caps = true, spacing = 0.16f),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dpi(22f); bottomMargin = dpi(8f); marginStart = dpi(4f) })
    }

    private fun row(title: String, subtitle: String, actions: LinearLayout.() -> Unit) {
        val row = hbox().apply {
            background = Ui.round(getColor(R.color.card), dp(20f), getColor(R.color.card_edge), dpi(1f))
            setPadding(dpi(20f), dpi(12f), dpi(14f), dpi(12f))
            minimumHeight = dpi(76f)
        }
        val texts = vbox()
        texts.addView(label(title, 17f, getColor(R.color.text), R.font.manrope_semibold))
        texts.addView(label(subtitle, 13f, getColor(R.color.text_dim)), LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dpi(2f) })
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
        val right = hbox().apply(actions)
        row.addView(right, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dpi(12f) })
        list.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dpi(8f) })
    }

    private fun gap(): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(dpi(8f), 1) }

    private fun roundIcon(res: Int, onClick: () -> Unit): ImageView = ImageView(this).apply {
        setImageResource(res)
        setColorFilter(getColor(R.color.text))
        scaleType = ImageView.ScaleType.CENTER
        Ui.clickable(this, Ui.oval(getColor(R.color.chip)))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dpi(52f), dpi(52f))
    }

    private fun appName(pkg: String?): String {
        if (pkg == null) return "Not set"
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { "$pkg (not installed)" }
    }

    private fun editModel() {
        val input = field("Your car model", prefs.carModel)
        val box = FrameLayout(this).apply {
            setPadding(dpi(24f), dpi(12f), dpi(24f), 0)
            addView(input, FrameLayout.LayoutParams(MATCH, dpi(54f)))
        }
        AlertDialog.Builder(this, R.style.Dialog_CarVibe)
            .setTitle("Car model")
            .setView(box)
            .setPositiveButton("Save") { _, _ -> prefs.carModel = input.text.toString().trim(); render() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openNotificationAccess() {
        if (!start(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))) {
            Toast.makeText(this, "Open Android settings › Apps › Special access › Notification access", Toast.LENGTH_LONG).show()
        }
    }

    private fun openHomeSettings() {
        if (!start(Intent(Settings.ACTION_HOME_SETTINGS))) {
            start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun start(intent: Intent): Boolean = try { startActivity(intent); true } catch (e: Exception) { false }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }
}
