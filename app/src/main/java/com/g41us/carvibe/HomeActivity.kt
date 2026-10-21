package com.g41us.carvibe

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeActivity : BaseActivity() {

    private lateinit var strip: LinearLayout
    private lateinit var badgeRing: FrameLayout
    private lateinit var badgeInner: FrameLayout
    private lateinit var badgeLogo: ImageView
    private lateinit var badgeHint: TextView
    private lateinit var carModel: TextView
    private lateinit var stripLine: View
    private lateinit var weatherIcon: ImageView
    private lateinit var weatherTemp: TextView
    private lateinit var weatherCity: TextView
    private lateinit var greeting: TextView
    private lateinit var date: TextView
    private lateinit var gpsIcon: ImageView
    private lateinit var gpsText: TextView
    private lateinit var gauge: SpeedGaugeView
    private lateinit var speed: TextView
    private lateinit var phoneCard: LinearLayout
    private lateinit var phoneGlow: PhoneGlowView
    private lateinit var phoneStatus: TextView
    private lateinit var phoneName: TextView
    private lateinit var phoneHint: TextView
    private lateinit var mediaArt: ImageView
    private lateinit var mediaLabel: TextView
    private lateinit var mediaTitle: TextView
    private lateinit var mediaArtist: TextView
    private lateinit var mediaFill: View
    private lateinit var mediaPlay: ImageButton
    private lateinit var mapHolder: FrameLayout
    private lateinit var dock: LinearLayout

    private lateinit var phone: PhoneMonitor
    private var phoneState = PhoneState(false, null)
    private var map: MapView? = null
    private var carDot: CarDotOverlay? = null
    private var mapWaiting: TextView? = null
    private var mapCentered = false
    private var weatherAt = 0L
    private var weatherLoc: Location? = null
    private var askedLocation = false
    private var logoStamp = -1L
    private var lastMinute = -1
    private var lastSpeedText = ""

    private val locationListener = LocationHub.Listener { onLocation(it) }
    private val mediaListener = MediaHub.Listener { renderMedia() }
    private val updateListener = Updater.Listener { renderUpdate() }
    private val ticker = object : Runnable {
        override fun run() {
            tick()
            Net.main.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        bind()
        setupClicks()
        setupMap()
        phone = PhoneMonitor(this) { state ->
            phoneState = state
            renderPhone()
        }
        if (!prefs.setupDone) startActivity(Intent(this, SetupActivity::class.java))
    }

    private fun bind() {
        strip = findViewById(R.id.strip)
        badgeRing = findViewById(R.id.badgeRing)
        badgeInner = findViewById(R.id.badgeInner)
        badgeLogo = findViewById(R.id.badgeLogo)
        badgeHint = findViewById(R.id.badgeHint)
        carModel = findViewById(R.id.carModel)
        stripLine = findViewById(R.id.stripLine)
        weatherIcon = findViewById(R.id.weatherIcon)
        weatherTemp = findViewById(R.id.weatherTemp)
        weatherCity = findViewById(R.id.weatherCity)
        greeting = findViewById(R.id.greeting)
        date = findViewById(R.id.date)
        gpsIcon = findViewById(R.id.gpsIcon)
        gpsText = findViewById(R.id.gpsText)
        gauge = findViewById(R.id.gauge)
        speed = findViewById(R.id.speed)
        phoneCard = findViewById(R.id.phoneCard)
        phoneGlow = findViewById(R.id.phoneGlow)
        phoneStatus = findViewById(R.id.phoneStatus)
        phoneName = findViewById(R.id.phoneName)
        phoneHint = findViewById(R.id.phoneHint)
        mediaArt = findViewById(R.id.mediaArt)
        mediaLabel = findViewById(R.id.mediaLabel)
        mediaTitle = findViewById(R.id.mediaTitle)
        mediaArtist = findViewById(R.id.mediaArtist)
        mediaFill = findViewById(R.id.mediaFill)
        mediaPlay = findViewById(R.id.mediaPlay)
        mapHolder = findViewById(R.id.mapHolder)
        dock = findViewById(R.id.dock)

        mediaFill.layoutParams = mediaFill.layoutParams.apply { width = ViewGroup.LayoutParams.MATCH_PARENT }
        mediaFill.pivotX = 0f
        mediaFill.scaleX = 0f
        mediaArt.clipToOutline = true
        findViewById<View>(R.id.mapsCard).clipToOutline = true
    }

    override fun onResume() {
        super.onResume()
        applyLook()
        updateClock(force = true)
        LocationHub.add(locationListener)
        if (!LocationHub.hasPermission(this) && !askedLocation && prefs.setupDone) {
            askedLocation = true
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
        }
        LocationHub.start(this)
        onLocation(LocationHub.last)
        MediaHub.add(mediaListener)
        MediaHub.attach(this)
        renderMedia()
        phone.start()
        Updater.add(updateListener)
        Updater.checkIfDue(this)
        renderUpdate()
        map?.onResume()
        Net.main.removeCallbacks(ticker)
        Net.main.post(ticker)
    }

    override fun onPause() {
        Net.main.removeCallbacks(ticker)
        LocationHub.remove(locationListener)
        LocationHub.stop()
        MediaHub.remove(mediaListener)
        phone.stop()
        Updater.remove(updateListener)
        map?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        map?.onDetach()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) LocationHub.start(this)
    }

    @Deprecated("Home screen: Back does nothing")
    override fun onBackPressed() { /* this is the home screen */ }

    /* ------------------------------------------------------------ look */

    private fun applyLook() {
        val accent = prefs.accent
        val card = getColor(R.color.card)
        val edge = getColor(R.color.card_edge)
        val one = dpi(1f)

        strip.layoutParams = strip.layoutParams.apply { width = dpi(prefs.deadZone.toFloat()) }

        listOf(R.id.clockCard, R.id.speedCard, R.id.mediaCard, R.id.mapsCard).forEach {
            findViewById<View>(it).background = Ui.round(card, dp(22f), edge, one)
        }
        phoneCard.background = Ui.pressable(Ui.round(getColor(R.color.hero), dp(28f), getColor(R.color.hero_edge), one))
        dock.background = Ui.round(getColor(R.color.dock), dp(22f), edge, one)
        mapHolder.setBackgroundColor(Color.parseColor("#090D14"))

        findViewById<View>(R.id.bgGlow).background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(Ui.withAlpha(accent, 0x22), Ui.withAlpha(accent, 0x00))
            gradientRadius = dp(250f)
            setGradientCenter(0.5f, 0.05f)
        }
        val glowLine = findViewById<View>(R.id.glowLine)
        glowLine.visibility = if (prefs.glowLine) View.VISIBLE else View.GONE
        glowLine.alpha = 0.8f
        glowLine.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.TRANSPARENT, accent, Color.TRANSPARENT))
        stripLine.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, Ui.withAlpha(accent, 0x40), Color.TRANSPARENT))

        badgeRing.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
            Color.parseColor("#E6EBF2"), Color.parseColor("#5E6878"),
            Color.parseColor("#C9D0DA"), Color.parseColor("#4A5262"))).apply { shape = GradientDrawable.OVAL }
        badgeInner.background = Ui.oval(Color.parseColor("#0A0E16"))
        badgeInner.clipToOutline = true
        renderBadge()
        carModel.text = prefs.carModel.ifBlank { prefs.brandName }

        gauge.accent = accent
        phoneGlow.accent = accent
        phoneGlow.pulse = prefs.phonePulse
        mediaLabel.setTextColor(accent)
        mediaFill.background = Ui.round(accent, dp(2f))
        findViewById<View>(R.id.mediaTrack).background = Ui.round(getColor(R.color.track), dp(2f))
        mediaPlay.background = Ui.pressable(Ui.oval(getColor(R.color.text)), 0x33000000)
        mediaArt.background = Ui.round(Color.parseColor("#121B2C"), dp(14f), Color.parseColor("#1F2A40"), one)
        carDot?.accent = accent
        map?.invalidate()

        buildDock()
        renderPhone()
    }

    private fun renderBadge() {
        val file = Logo.file(this)
        val stamp = if (file.exists()) file.lastModified() + prefs.logoStyle else 0L
        if (stamp == logoStamp) return
        logoStamp = stamp
        val bmp: Bitmap? = Logo.load(this)
        if (bmp == null) {
            badgeLogo.setImageDrawable(null)
            badgeHint.visibility = View.VISIBLE
            return
        }
        badgeHint.visibility = View.GONE
        badgeLogo.setImageBitmap(bmp)
        if (prefs.logoStyle == 0 && Logo.hasTransparency(bmp)) {
            badgeLogo.setColorFilter(Color.parseColor("#E6EBF2"), PorterDuff.Mode.SRC_IN)
        } else {
            badgeLogo.clearColorFilter()
        }
    }

    /* ------------------------------------------------------------ dock */

    private data class DockItem(val key: String, val label: String, val icon: Int)

    private val dockItems = listOf(
        DockItem("home", "Home", R.drawable.ic_home),
        DockItem("music", "Music", R.drawable.ic_music),
        DockItem("radio", "Radio", R.drawable.ic_radio),
        DockItem("camera", "Camera", R.drawable.ic_camera),
        DockItem("bluetooth", "Bluetooth", R.drawable.ic_bluetooth),
        DockItem("apps", "Apps", R.drawable.ic_apps),
        DockItem("settings", "Settings", R.drawable.ic_settings),
    )

    private fun buildDock() {
        dock.removeAllViews()
        val accent = prefs.accent
        for (item in dockItems) {
            val active = item.key == "home"
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                contentDescription = item.label
                val bg = if (active) Ui.round(Color.parseColor("#111A29"), dp(17f)) else Ui.round(Color.TRANSPARENT, dp(17f))
                Ui.clickable(this, bg)
            }
            val icon = ImageView(this).apply {
                setImageResource(item.icon)
                setColorFilter(if (active) accent else getColor(R.color.text_soft))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val label = TextView(this).apply {
                text = item.label
                textSize = 12f
                setTextColor(if (active) accent else getColor(R.color.text_soft))
                typeface = resources.getFont(if (active) R.font.manrope_semibold else R.font.manrope_medium)
                gravity = Gravity.CENTER
                maxLines = 1
            }
            cell.addView(icon, LinearLayout.LayoutParams(dpi(21f), dpi(21f)))
            cell.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpi(3f)
            })
            dock.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dpi(2f)
                marginEnd = dpi(2f)
            })
            cell.setOnClickListener { onDock(item) }
            if (item.key in Apps.SHORTCUTS) {
                cell.setOnLongClickListener { chooseShortcut(item); true }
            }
        }
    }

    private fun onDock(item: DockItem) {
        when (item.key) {
            "home" -> Unit
            "apps" -> startActivity(Intent(this, AppsActivity::class.java))
            "settings" -> startActivity(Intent(this, SettingsActivity::class.java))
            else -> openShortcut(item)
        }
    }

    private fun openShortcut(item: DockItem) {
        if (!Apps.launch(this, Apps.shortcut(this, item.key))) chooseShortcut(item)
    }

    private fun chooseShortcut(item: DockItem) {
        Apps.pick(this, "Which app should ${item.label} open?") {
            prefs.setShortcut(item.key, it.pkg)
            Apps.launch(this, it)
        }
    }

    /* ------------------------------------------------------------ clicks */

    private fun setupClicks() {
        phoneCard.setOnClickListener { openPhoneApp() }
        phoneCard.setOnLongClickListener {
            safeStart(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); true
        }
        findViewById<View>(R.id.mapsClick).setOnClickListener { openMaps() }
        findViewById<View>(R.id.mediaInfo).setOnClickListener { openMediaApp() }
        mediaPlay.setOnClickListener {
            if (MediaHub.nowPlaying != null) MediaHub.playPause() else openMediaApp()
        }
        findViewById<View>(R.id.mediaPrev).setOnClickListener { MediaHub.previous() }
        findViewById<View>(R.id.mediaNext).setOnClickListener { MediaHub.next() }
        val gpsClick = View.OnClickListener {
            when {
                !LocationHub.hasPermission(this) -> requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
                !LocationHub.gpsEnabled(this) -> safeStart(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }
        gpsIcon.setOnClickListener(gpsClick)
        gpsText.setOnClickListener(gpsClick)
    }

    private fun openPhoneApp() {
        if (Apps.launch(this, prefs.phoneApp)) return
        Apps.pick(this, "Which app runs CarPlay / Android Auto?") {
            prefs.phoneApp = it.pkg
            Apps.launch(this, it)
        }
    }

    /** With a phone connected, maps means the phone's Google Maps inside RLink. */
    private fun openMaps() {
        if (phoneState.connected && Apps.launch(this, prefs.phoneApp)) return
        if (Apps.launch(this, Prefs.GOOGLE_MAPS)) return
        if (safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")))) return
        Toast.makeText(this, "Install Google Maps from the Play Store", Toast.LENGTH_LONG).show()
    }

    private fun openMediaApp() {
        if (!MediaHub.accessGranted(this)) {
            if (!safeStart(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))) {
                Toast.makeText(this, "Allow Car Vibe under Settings › Apps › Special access › Notification access", Toast.LENGTH_LONG).show()
            }
            return
        }
        val pkg = MediaHub.nowPlaying?.packageName
        if (!Apps.launch(this, pkg)) openShortcut(dockItems.first { it.key == "music" })
    }

    private fun safeStart(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { false }

    /* ------------------------------------------------------------ updates */

    private fun renderUpdate() {
        val chip = findViewById<TextView>(R.id.updateChip)
        val show = Updater.state == Updater.State.AVAILABLE || Updater.state == Updater.State.READY
        chip.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        val accent = prefs.accent
        chip.setTextColor(accent)
        chip.text = if (Updater.state == Updater.State.READY) "Update ready · Install" else "Update available"
        Ui.clickable(chip, Ui.round(Ui.withAlpha(accent, 0x22), dp(15f), Ui.withAlpha(accent, 0x66), dpi(1f)))
        chip.setOnClickListener {
            if (Updater.state == Updater.State.READY) Updater.install(this)
            else startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    /* ------------------------------------------------------------ phone */

    private fun renderPhone() {
        val s = phoneState
        val accent = prefs.accent
        phoneGlow.connected = s.connected
        if (s.connected) {
            val dot = Ui.oval(accent).apply { setBounds(0, 0, dpi(8f), dpi(8f)) }
            phoneStatus.setCompoundDrawables(dot, null, null, null)
            phoneStatus.text = if (s.battery in 0..100) "CONNECTED · ${s.battery}%" else "CONNECTED"
            phoneStatus.setTextColor(accent)
            phoneName.text = s.name ?: "Your phone"
            phoneHint.text = "Tap to open CarPlay · Android Auto"
        } else {
            phoneStatus.setCompoundDrawables(null, null, null, null)
            phoneStatus.text = "NOT CONNECTED"
            phoneStatus.setTextColor(getColor(R.color.text_mute))
            phoneName.text = "Connect your phone"
            phoneHint.text = "Turn on Bluetooth and Wi-Fi on your phone"
        }
    }

    /* ------------------------------------------------------------ media */

    private fun renderMedia() {
        val np = MediaHub.nowPlaying
        when {
            !MediaHub.accessGranted(this) -> {
                mediaLabel.text = "MEDIA"
                mediaTitle.text = "Allow access"
                mediaArtist.text = "Tap to show music here"
            }
            np == null -> {
                mediaLabel.text = "MEDIA"
                mediaTitle.text = "Nothing playing"
                mediaArtist.text = "Tap to open music"
            }
            else -> {
                mediaLabel.text = if (np.playing) "NOW PLAYING" else "PAUSED"
                mediaTitle.text = np.title ?: "Unknown track"
                mediaArtist.text = np.artist ?: ""
            }
        }
        if (np?.art != null) {
            mediaArt.scaleType = ImageView.ScaleType.CENTER_CROP
            mediaArt.clearColorFilter()
            mediaArt.setImageBitmap(np.art)
        } else {
            mediaArt.scaleType = ImageView.ScaleType.CENTER
            mediaArt.setImageResource(R.drawable.ic_music)
            mediaArt.setColorFilter(Color.parseColor("#7E8BA3"))
        }
        mediaPlay.setImageResource(if (np?.playing == true) R.drawable.ic_pause else R.drawable.ic_play)
        mediaPlay.contentDescription = if (np?.playing == true) "Pause" else "Play"
        updateProgress()
    }

    private fun updateProgress() {
        val np = MediaHub.nowPlaying
        val frac = if (np != null && np.durationMs > 0) np.currentPosition().toFloat() / np.durationMs else 0f
        mediaFill.scaleX = frac.coerceIn(0f, 1f)
    }

    /* ------------------------------------------------------------ location */

    private fun onLocation(loc: Location?) {
        updateSpeed()
        if (loc == null) return
        val point = GeoPoint(loc.latitude, loc.longitude)
        map?.let { m ->
            carDot?.point = point
            if (!mapCentered) {
                m.controller.setZoom(15.5)
                m.controller.setCenter(point)
                mapCentered = true
            } else {
                m.controller.setCenter(point)
            }
            mapWaiting?.visibility = View.GONE
            m.invalidate()
        }
        val now = System.currentTimeMillis()
        val moved = weatherLoc?.distanceTo(loc) ?: Float.MAX_VALUE
        if (now - weatherAt > 30 * 60_000L || moved > 10_000f) {
            weatherAt = now
            weatherLoc = loc
            Weather.fetch(this, loc.latitude, loc.longitude) { w ->
                // On failure, try again in about five minutes.
                if (w == null) { weatherAt = System.currentTimeMillis() - 25 * 60_000L; return@fetch }
                weatherTemp.text = "${w.tempC}°"
                weatherIcon.setImageResource(Weather.icon(w.code))
                weatherCity.text = w.city ?: ""
            }
        }
    }

    private fun updateSpeed() {
        val kmh = if (LocationHub.hasFix()) LocationHub.speedKmh else null
        val text = kmh?.let { Math.round(it).toString() } ?: "--"
        if (text != lastSpeedText) {
            lastSpeedText = text
            speed.text = text
            gauge.setSpeed(kmh ?: 0f)
        }
        val accent = prefs.accent
        when {
            !LocationHub.hasPermission(this) -> {
                gpsText.text = "Location off"; gpsText.setTextColor(getColor(R.color.text_mute)); gpsIcon.setColorFilter(getColor(R.color.text_mute))
            }
            LocationHub.hasFix() -> {
                gpsText.text = "GPS on"; gpsText.setTextColor(accent); gpsIcon.setColorFilter(accent)
            }
            else -> {
                gpsText.text = "GPS searching"; gpsText.setTextColor(getColor(R.color.text_mute)); gpsIcon.setColorFilter(getColor(R.color.text_mute))
            }
        }
    }

    /* ------------------------------------------------------------ clock */

    private fun tick() {
        updateClock(force = false)
        updateSpeed()
        updateProgress()
    }

    private fun updateClock(force: Boolean) {
        val cal = Calendar.getInstance()
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        if (!force && minute == lastMinute) return
        lastMinute = minute
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        greeting.text = when {
            hour < 5 -> "Good night"
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        date.text = SimpleDateFormat("EEEE · d MMMM", Locale.getDefault()).format(Date())
    }

    /* ------------------------------------------------------------ map */

    private fun setupMap() {
        val waiting = TextView(this).apply {
            text = "Waiting for GPS"
            textSize = 12f
            setTextColor(getColor(R.color.text_mute))
            gravity = Gravity.CENTER
        }
        try {
            val cfg = org.osmdroid.config.Configuration.getInstance()
            cfg.userAgentValue = packageName
            cfg.osmdroidBasePath = File(cacheDir, "osmdroid")
            cfg.osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
            val m = MapView(this)
            m.setTileSource(TileSourceFactory.MAPNIK)
            m.setMultiTouchControls(false)
            m.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            m.isTilesScaledToDpi = true
            m.isHorizontalMapRepetitionEnabled = false
            m.overlayManager.tilesOverlay.setColorFilter(darkMapFilter())
            // Transparent while tiles load: the dark-map filter would turn a dark colour light.
            m.overlayManager.tilesOverlay.loadingBackgroundColor = Color.TRANSPARENT
            m.overlayManager.tilesOverlay.loadingLineColor = Color.TRANSPARENT
            m.setBackgroundColor(Color.TRANSPARENT)
            m.controller.setZoom(15.5)
            val dot = CarDotOverlay(resources.displayMetrics.density)
            dot.accent = prefs.accent
            m.overlays.add(dot)
            carDot = dot
            mapHolder.addView(m, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            // OpenStreetMap's tile policy asks for visible credit.
            mapHolder.addView(TextView(this).apply {
                text = "© OpenStreetMap"
                textSize = 8f
                setTextColor(Ui.withAlpha(getColor(R.color.text_dim), 0xB0))
                setPadding(dpi(6f), dpi(2f), dpi(8f), dpi(3f))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END))
            map = m
        } catch (e: Throwable) {
            map = null
            waiting.text = "Map unavailable"
        }
        mapHolder.addView(waiting, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        mapWaiting = waiting
    }

    /** Turns the standard light map into a dark, blue-tinted one to match the dashboard. */
    private fun darkMapFilter(): ColorMatrixColorFilter {
        val m = ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ))
        m.postConcat(ColorMatrix().apply { setSaturation(0.2f) })
        m.postConcat(ColorMatrix().apply { setScale(0.62f, 0.7f, 0.86f, 1f) })
        return ColorMatrixColorFilter(m)
    }

    companion object {
        private const val REQ_LOCATION = 7
    }
}

/** The car's position on the mini map: an accent dot with a soft halo. */
class CarDotOverlay(private val density: Float) : Overlay() {
    var point: GeoPoint? = null
    var accent: Int = Prefs.DEFAULT_ACCENT
    private val pt = Point()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val p = point ?: return
        mapView.projection.toPixels(p, pt)
        val x = pt.x.toFloat()
        val y = pt.y.toFloat()
        paint.color = Ui.withAlpha(accent, 0x40)
        canvas.drawCircle(x, y, 15f * density, paint)
        paint.color = Color.parseColor("#04060A")
        canvas.drawCircle(x, y, 7.5f * density, paint)
        paint.color = accent
        canvas.drawCircle(x, y, 5.5f * density, paint)
    }
}
