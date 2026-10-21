package com.g41us.carvibe

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File

/**
 * Updates itself from the GitHub repo. Every push builds on GitHub Actions and
 * publishes CarVibe.apk plus version.json to the "latest" release; the app
 * compares version numbers, downloads the new APK and hands it to Android's
 * installer. The same signing key means it installs over the old one.
 */
object Updater {
    const val REPO = "G41US/car-vibe"
    private const val BASE = "https://github.com/$REPO/releases/latest/download"
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

    data class Release(val code: Int, val name: String, val notes: String)

    enum class State { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, FAILED }

    fun interface Listener { fun onChanged() }

    private val listeners = LinkedHashSet<Listener>()
    fun add(l: Listener) { listeners.add(l) }
    fun remove(l: Listener) { listeners.remove(l) }
    private fun changed() { for (l in listeners.toList()) l.onChanged() }

    var state = State.IDLE
        private set
    var latest: Release? = null
        private set
    var progress = -1
        private set
    var error: String? = null
        private set
    private var lastCheck = 0L

    fun currentCode(ctx: Context): Int = try {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
    } catch (e: Exception) { 0 }

    fun currentName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (e: Exception) { "" }

    private fun apkFile(ctx: Context) = File(File(ctx.cacheDir, "updates"), "CarVibe.apk")

    /** Quiet check from the home screen, at most every six hours. */
    fun checkIfDue(ctx: Context) {
        if (System.currentTimeMillis() - lastCheck > CHECK_EVERY_MS) check(ctx)
    }

    fun check(ctx: Context) {
        if (state == State.CHECKING || state == State.DOWNLOADING) return
        val app = ctx.applicationContext
        lastCheck = System.currentTimeMillis()
        state = State.CHECKING
        error = null
        changed()
        Net.async({
            val json = JSONObject(Net.get("$BASE/version.json"))
            Release(json.getInt("versionCode"), json.optString("versionName"), json.optString("notes"))
        }) { release ->
            if (release == null) {
                state = State.FAILED
                error = "Couldn't reach GitHub. Check the internet connection."
            } else {
                latest = release
                state = if (release.code > currentCode(app)) State.AVAILABLE else State.UP_TO_DATE
                if (state == State.AVAILABLE && apkFile(app).exists() && downloadedIsNewer(app)) state = State.READY
            }
            changed()
        }
    }

    fun download(ctx: Context) {
        if (state == State.DOWNLOADING) return
        val app = ctx.applicationContext
        state = State.DOWNLOADING
        progress = 0
        changed()
        val dest = apkFile(app)
        Net.async({
            dest.delete()
            Net.download("$BASE/CarVibe.apk", dest) { pct -> progress = pct; changed() }
            downloadedIsNewer(app)
        }) { ok ->
            if (ok == true) {
                state = State.READY
            } else {
                state = State.FAILED
                error = if (ok == false) "The download wasn't a newer Car Vibe." else "Download failed. Try again."
                dest.delete()
            }
            changed()
        }
    }

    /** Checks the downloaded file really is Car Vibe, and newer than this one. */
    private fun downloadedIsNewer(ctx: Context): Boolean {
        val f = apkFile(ctx)
        if (!f.exists()) return false
        @Suppress("DEPRECATION")
        val info = ctx.packageManager.getPackageArchiveInfo(f.path, 0) ?: return false
        @Suppress("DEPRECATION")
        return info.packageName == ctx.packageName && info.versionCode > currentCode(ctx)
    }

    /** Opens Android's installer. On first use, Android asks to allow installs from Car Vibe. */
    fun install(activity: Activity) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(activity, "Allow Car Vibe to install updates, then tap Install again", Toast.LENGTH_LONG).show()
            try {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            } catch (e: Exception) { }
            return
        }
        val uri = Uri.parse("content://${activity.packageName}.apk/CarVibe.apk")
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false)
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "Couldn't open the installer", Toast.LENGTH_LONG).show()
        }
    }

    /** One line for the Settings row. */
    fun summary(ctx: Context): String {
        val current = "You have version ${currentName(ctx)}."
        return when (state) {
            State.IDLE -> current
            State.CHECKING -> "Checking for updates…"
            State.UP_TO_DATE -> "$current It's the latest."
            State.AVAILABLE -> "Version ${latest?.name} is ready to download." + notes()
            State.DOWNLOADING -> if (progress >= 0) "Downloading… $progress%" else "Downloading…"
            State.READY -> "Version ${latest?.name} downloaded. Tap Install." + notes()
            State.FAILED -> error ?: "Something went wrong."
        }
    }

    private fun notes(): String = latest?.notes?.takeIf { it.isNotBlank() }?.let { " What's new: $it" } ?: ""
}

/** Serves only the downloaded update file, read-only, to the system installer. */
class ApkProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: return null
        if (uri.lastPathSegment != "CarVibe.apk") return null
        val file = File(File(ctx.cacheDir, "updates"), "CarVibe.apk")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "application/vnd.android.package-archive"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
