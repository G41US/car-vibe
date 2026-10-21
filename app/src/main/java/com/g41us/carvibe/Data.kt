package com.g41us.carvibe

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.Locale

/* ---------------------------------------------------------------- weather */

data class WeatherNow(val tempC: Int, val code: Int, val city: String?)

object Weather {
    /** Free Open-Meteo forecast (no key) plus the phone-less Android geocoder for the city name. */
    fun fetch(ctx: Context, lat: Double, lon: Double, done: (WeatherNow?) -> Unit) {
        val app = ctx.applicationContext
        Net.async({
            val url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&timezone=auto",
                lat, lon
            )
            val cur = JSONObject(Net.get(url)).getJSONObject("current")
            val city = try {
                @Suppress("DEPRECATION")
                Geocoder(app, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()?.let {
                    it.locality ?: it.subAdminArea ?: it.adminArea
                }
            } catch (e: Exception) { null }
            WeatherNow(Math.round(cur.getDouble("temperature_2m")).toInt(), cur.optInt("weather_code", 0), city)
        }, done)
    }

    fun icon(code: Int): Int = when (code) {
        0, 1 -> R.drawable.ic_sun
        in 51..67, in 80..82 -> R.drawable.ic_rain
        in 95..99 -> R.drawable.ic_storm
        else -> R.drawable.ic_cloud
    }
}

/* ---------------------------------------------------------- brand logos */

data class Brand(val id: String, val label: String, val description: String, val logoFile: String) {
    fun logoUrl(width: Int): String {
        val name = URLEncoder.encode(logoFile.replace(' ', '_'), "UTF-8").replace("+", "%20")
        return "https://commons.wikimedia.org/wiki/Special:FilePath/$name?width=$width"
    }
}

/** Looks a car brand up on Wikidata and keeps only results that have a logo (property P154). */
object BrandSearch {
    private val carWords = listOf("car", "automobile", "automotive", "motor", "vehicle", "truck", "marque", "manufacturer")

    fun search(query: String): List<Brand> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val found = JSONObject(Net.get(
            "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=$q&language=en&uselang=en&type=item&limit=20&format=json"
        )).optJSONArray("search") ?: return emptyList()
        val ids = (0 until found.length()).map { found.getJSONObject(it).getString("id") }
        if (ids.isEmpty()) return emptyList()

        val entities = JSONObject(Net.get(
            "https://www.wikidata.org/w/api.php?action=wbgetentities&ids=${ids.joinToString("%7C")}&props=claims%7Clabels%7Cdescriptions&languages=en&format=json"
        )).optJSONObject("entities") ?: return emptyList()

        val brands = ids.mapNotNull { id ->
            val e = entities.optJSONObject(id) ?: return@mapNotNull null
            val logo = e.optJSONObject("claims")?.optJSONArray("P154")?.optJSONObject(0)
                ?.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optString("value")
            if (logo.isNullOrBlank()) return@mapNotNull null
            val label = e.optJSONObject("labels")?.optJSONObject("en")?.optString("value") ?: return@mapNotNull null
            val desc = e.optJSONObject("descriptions")?.optJSONObject("en")?.optString("value") ?: ""
            Brand(id, label, desc, logo)
        }
        // Car makers first, keeping Wikidata's relevance order otherwise.
        return brands.sortedByDescending { b -> carWords.any { b.description.lowercase().contains(it) } }.take(8)
    }

    fun downloadLogo(brand: Brand, width: Int): Bitmap? {
        val bytes = Net.getBytes(brand.logoUrl(width))
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}

object Logo {
    fun file(ctx: Context) = File(ctx.filesDir, "brand_logo.png")

    fun save(ctx: Context, bmp: Bitmap) {
        file(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun load(ctx: Context): Bitmap? {
        val f = file(ctx)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    /** Logos with a transparent background can be shown in silver; photos can't. */
    fun hasTransparency(bmp: Bitmap): Boolean {
        if (!bmp.hasAlpha()) return false
        val w = bmp.width
        val h = bmp.height
        val points = listOf(0 to 0, w - 1 to 0, 0 to h - 1, w - 1 to h - 1, w / 2 to 0, 0 to h / 2)
        return points.any { (x, y) -> (bmp.getPixel(x, y) ushr 24) < 0x40 }
    }

    fun decodeScaled(ctx: Context, uri: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}

/* -------------------------------------------------------------- apps */

data class AppEntry(val label: String, val pkg: String, val cls: String, val system: Boolean) {
    fun icon(ctx: Context): Drawable? = try {
        ctx.packageManager.getActivityIcon(android.content.ComponentName(pkg, cls))
    } catch (e: Exception) { null }
}

object Apps {
    /** Dock shortcuts that point at the head unit's own apps, found by name. */
    val SHORTCUTS = linkedMapOf(
        "music" to listOf("music", "media", "player", "spotify", "audio"),
        "radio" to listOf("radio", "fm", "tuner"),
        "camera" to listOf("reverse", "camera", "avm", "360", "rear", "dvr"),
        "bluetooth" to listOf("bluetooth", "bt music", "bt", "phone", "dial"),
    )

    fun list(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map {
                AppEntry(
                    it.loadLabel(pm).toString(),
                    it.activityInfo.packageName,
                    it.activityInfo.name,
                    (it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .distinctBy { it.pkg + "/" + it.cls }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    fun installed(ctx: Context, pkg: String): Boolean =
        ctx.packageManager.getLaunchIntentForPackage(pkg) != null

    fun launch(ctx: Context, pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(intent); true } catch (e: Exception) { false }
    }

    fun launch(ctx: Context, entry: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(entry.pkg, entry.cls)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try { ctx.startActivity(intent) } catch (e: Exception) {
            Toast.makeText(ctx, "Couldn't open ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }

    /** The app chosen for a dock shortcut, or the best name match on this head unit. */
    fun shortcut(ctx: Context, key: String): String? {
        val prefs = Prefs(ctx)
        prefs.shortcut(key)?.let { if (installed(ctx, it)) return it }
        val words = SHORTCUTS[key] ?: return null
        val apps = list(ctx).filter { it.pkg != prefs.phoneApp }
        for (word in words) {
            val match = apps.filter { a ->
                val l = a.label.lowercase(Locale.getDefault())
                if (word.length <= 3) l == word || l.startsWith("$word ") || l.endsWith(" $word") else l.contains(word)
            }
            match.firstOrNull { it.system }?.let { return it.pkg }
            match.firstOrNull()?.let { return it.pkg }
        }
        return null
    }

    /** A simple list of every app, used to choose what a shortcut opens. */
    fun pick(activity: BaseActivity, title: String, onPicked: (AppEntry) -> Unit) {
        val apps = list(activity)
        val d = activity.resources.displayMetrics.density
        val adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(position: Int) = apps[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = (convertView as? LinearLayout) ?: LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((20 * d).toInt(), (10 * d).toInt(), (20 * d).toInt(), (10 * d).toInt())
                    addView(ImageView(activity), LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt()))
                    addView(TextView(activity).apply {
                        textSize = 17f
                        setTextColor(activity.getColor(R.color.text))
                        setPadding((16 * d).toInt(), 0, 0, 0)
                    })
                }
                val app = apps[position]
                (row.getChildAt(0) as ImageView).setImageDrawable(app.icon(activity))
                (row.getChildAt(1) as TextView).text = app.label
                return row
            }
        }
        AlertDialog.Builder(activity, R.style.Dialog_CarVibe)
            .setTitle(title)
            .setAdapter(adapter) { _, which -> onPicked(apps[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
