package com.g41us.carvibe

import android.app.Notification
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.os.Build
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A small rolling log of things worth knowing when fixing problems remotely. */
object Diag {
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun log(msg: String) {
        lines.addLast("${fmt.format(Date())} $msg")
        while (lines.size > 200) lines.removeFirst()
    }

    @Synchronized
    fun dump(): String = lines.joinToString("\n")
}

/**
 * Builds a report about this head unit (model, audio, Bluetooth, the vendor's
 * system apps and how they're wired up) plus copies of the vendor apps, and
 * uploads it to temp.sh for three days. The short code it shows lets Claude
 * download and read it, to work out things like the unit's own volume system.
 * Nothing personal is included: no contacts, photos, location or device names.
 */
object Diagnostics {
    private const val MAX_APK_BYTES = 80L * 1024 * 1024

    fun run(ctx: Context, status: (String) -> Unit, done: (String?) -> Unit) {
        val app = ctx.applicationContext
        Net.async({
            Net.main.post { status("Collecting system info…") }
            val zip = build(app)
            Net.main.post { status("Uploading ${zip.length() / (1024 * 1024)} MB…") }
            val url = upload(zip) { pct -> Net.main.post { status("Uploading… $pct%") } }
            zip.delete()
            url
        }, done)
    }

    private fun build(ctx: Context): File {
        val pm = ctx.packageManager
        val report = StringBuilder()
        fun line(s: String = "") { report.append(s).append('\n') }

        line("Car Vibe diagnostics ${Updater.currentName(ctx)} (${Updater.currentCode(ctx)})")
        line("Made ${Date()}")
        line()
        line("== Device")
        line("model=${Build.MODEL} manufacturer=${Build.MANUFACTURER} brand=${Build.BRAND} device=${Build.DEVICE} product=${Build.PRODUCT}")
        line("hardware=${Build.HARDWARE} board=${Build.BOARD} sdk=${Build.VERSION.SDK_INT} release=${Build.VERSION.RELEASE}")
        line("display=${Build.DISPLAY}")
        line("fingerprint=${Build.FINGERPRINT}")
        val dm = ctx.resources.displayMetrics
        line("screen=${dm.widthPixels}x${dm.heightPixels} densityDpi=${dm.densityDpi}")
        line()

        line("== Audio")
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val streams = listOf("VOICE_CALL" to 0, "SYSTEM" to 1, "RING" to 2, "MUSIC" to 3, "ALARM" to 4, "NOTIFICATION" to 5, "BT_SCO" to 6, "DTMF" to 8, "ACCESSIBILITY" to 10)
        for ((name, s) in streams) {
            try { line("$name: ${am.getStreamVolume(s)}/${am.getStreamMaxVolume(s)} muted=${am.isStreamMute(s)} fixed=${am.isVolumeFixed}") } catch (e: Exception) { line("$name: ${e.message}") }
        }
        line("mode=${am.mode} musicActive=${am.isMusicActive}")
        try {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).forEach { d: AudioDeviceInfo ->
                line("output type=${d.type} name=${d.productName}")
            }
        } catch (e: Exception) { }
        line()

        line("== Bluetooth")
        try {
            val bt = BluetoothAdapter.getDefaultAdapter()
            if (bt == null) line("no Android Bluetooth adapter") else {
                line("enabled=${bt.isEnabled} bonded=${bt.bondedDevices?.size}")
                line("headset=${bt.getProfileConnectionState(BluetoothProfile.HEADSET)} a2dp=${bt.getProfileConnectionState(BluetoothProfile.A2DP)}")
            }
        } catch (e: Exception) { line("bluetooth error: ${e.message}") }
        line()

        line("== Location")
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            line("providers=${lm.allProviders} enabled=${lm.getProviders(true)} permission=${LocationHub.hasPermission(ctx)}")
        } catch (e: Exception) { line("location error: ${e.message}") }
        line()

        line("== Media sessions and phone app")
        line("mediaAccess=${MediaHub.accessGranted(ctx)} phoneApp=${Prefs(ctx).phoneApp}")
        try {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm.getActiveSessions(ComponentName(ctx, MediaListenerService::class.java)).forEach {
                line("session ${it.packageName} state=${it.playbackState?.state} title=${it.metadata?.description?.title}")
            }
        } catch (e: Exception) { line("sessions: ${e.message}") }
        try {
            val phonePkg = Prefs(ctx).phoneApp
            MediaListenerService.instance?.activeNotifications?.forEach { n ->
                if (n.packageName == phonePkg) {
                    val ex = n.notification.extras
                    line("phone-app notification ongoing=${n.isOngoing} title=${ex.getCharSequence(Notification.EXTRA_TITLE)} text=${ex.getCharSequence(Notification.EXTRA_TEXT)}")
                } else {
                    line("notification from ${n.packageName}")
                }
            }
        } catch (e: Exception) { line("notifications: ${e.message}") }
        line()

        line("== Internet checks")
        for ((name, url) in listOf(
            "weather" to "https://api.open-meteo.com/v1/forecast?latitude=6.45&longitude=3.39&current=temperature_2m",
            "wikidata" to "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=toyota&language=en&format=json&limit=1",
            "github" to "https://github.com/${Updater.REPO}/releases/latest/download/version.json",
        )) {
            line("$name: " + try { "ok ${Net.get(url).take(80).replace('\n', ' ')}" } catch (e: Exception) { "FAILED ${e.javaClass.simpleName}: ${e.message}" })
        }
        line()

        line("== Recent log")
        line(Diag.dump())
        line()

        // Every package, and the wiring of the non-Google ones.
        line("== Packages")
        @Suppress("DEPRECATION")
        val all = pm.getInstalledPackages(0)
        val vendor = mutableListOf<Pair<String, ApplicationInfo>>()
        for (p in all.sortedBy { it.packageName }) {
            val ai = p.applicationInfo ?: continue
            val system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            line("${p.packageName} v${p.versionName} system=$system path=${ai.sourceDir}")
            val name = p.packageName
            val isVendor = name.contains("reglink") || name == Prefs(ctx).phoneApp ||
                (system && !name.startsWith("com.google") && !name.startsWith("com.android") && !name.startsWith("android") &&
                    !name.startsWith("com.qualcomm") && name != ctx.packageName)
            if (isVendor) vendor += name to ai
        }
        line()
        line("== Vendor app details")
        for ((name, _) in vendor) {
            try {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(name, PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS)
                line("-- $name")
                info.activities?.forEach { line("  activity ${it.name} exported=${it.exported}") }
                info.services?.forEach { line("  service ${it.name} exported=${it.exported} perm=${it.permission}") }
                info.receivers?.forEach { line("  receiver ${it.name} exported=${it.exported}") }
                info.providers?.forEach { line("  provider ${it.name} authority=${it.authority} exported=${it.exported}") }
                info.requestedPermissions?.forEach { line("  uses ${it}") }
                info.permissions?.forEach { line("  defines ${it.name}") }
            } catch (e: Exception) { line("-- $name: ${e.message}") }
        }

        // The vendor apps themselves, most useful first, within a size cap.
        val apks = vendor.map { it.second }.distinctBy { it.sourceDir }.sortedWith(compareBy<ApplicationInfo>(
            { if (it.packageName.contains("reglink")) 0 else if (it.packageName == Prefs(ctx).phoneApp) 1 else 2 },
            { File(it.sourceDir).length() }
        ))
        val out = File(ctx.cacheDir, "carvibe-diag.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)
            zip.putNextEntry(ZipEntry("report.txt"))
            zip.write(report.toString().toByteArray())
            zip.closeEntry()
            var total = 0L
            for (ai in apks) {
                val f = File(ai.sourceDir)
                if (!f.canRead() || total + f.length() > MAX_APK_BYTES) continue
                total += f.length()
                zip.putNextEntry(ZipEntry("apks/${ai.packageName}.apk"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return out
    }

    private fun upload(file: File, progress: (Int) -> Unit): String {
        val boundary = "CarVibe" + System.currentTimeMillis()
        val head = ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"carvibe-diag.zip\"\r\n" +
            "Content-Type: application/zip\r\n\r\n").toByteArray()
        val tail = "\r\n--$boundary--\r\n".toByteArray()
        val c = URL("https://temp.sh/upload").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 15_000
            c.readTimeout = 120_000
            c.setRequestProperty("User-Agent", Net.UA)
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            c.setFixedLengthStreamingMode(head.size + file.length() + tail.size)
            DataOutputStream(c.outputStream).use { out ->
                out.write(head)
                val buf = ByteArray(64 * 1024)
                var sent = 0L
                var last = -1
                file.inputStream().use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        sent += n
                        val pct = (sent * 100 / file.length().coerceAtLeast(1)).toInt()
                        if (pct != last) { last = pct; progress(pct) }
                    }
                }
                out.write(tail)
            }
            if (c.responseCode !in 200..299) throw java.io.IOException("Upload failed: HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().readText().trim()
        } finally {
            c.disconnect()
        }
    }

    /** "https://temp.sh/AbC12/carvibe-diag.zip" -> "AbC12" */
    fun codeOf(url: String): String = url.removePrefix("https://").removePrefix("http://").split('/').getOrNull(1) ?: url
}
