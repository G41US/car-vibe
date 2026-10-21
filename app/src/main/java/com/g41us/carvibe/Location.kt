package com.g41us.carvibe

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock

/** One shared GPS listener for the speedometer, the mini map and the weather. */
object LocationHub : LocationListener {

    fun interface Listener {
        fun onLocation(location: Location?)
    }

    private val listeners = LinkedHashSet<Listener>()
    private var manager: LocationManager? = null
    private var running = false

    var last: Location? = null
        private set
    private var lastGpsAt = 0L
    private var previous: Location? = null

    /** Speed in km/h from the latest GPS fix, or null if there's no recent fix. */
    var speedKmh: Float? = null
        private set

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun gpsEnabled(ctx: Context): Boolean {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (e: Exception) { false }
    }

    /** True when a GPS fix arrived in the last few seconds. */
    fun hasFix(): Boolean = SystemClock.elapsedRealtime() - lastGpsAt < 5_000

    fun add(l: Listener) { listeners.add(l) }
    fun remove(l: Listener) { listeners.remove(l) }

    @SuppressLint("MissingPermission")
    fun start(ctx: Context) {
        if (running || !hasPermission(ctx)) return
        val lm = ctx.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        manager = lm
        try {
            if (lm.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            }
            if (lm.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30_000L, 50f, this, Looper.getMainLooper())
            }
            running = true
            val known = listOfNotNull(
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER),
                lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER),
                lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER),
            ).maxByOrNull { it.time }
            if (known != null && last == null) {
                last = known
                dispatch()
            }
        } catch (e: Exception) {
            running = false
        }
    }

    fun stop() {
        try { manager?.removeUpdates(this) } catch (e: Exception) { }
        running = false
    }

    private fun dispatch() {
        for (l in listeners.toList()) l.onLocation(last)
    }

    override fun onLocationChanged(location: Location) {
        val isGps = location.provider == LocationManager.GPS_PROVIDER
        if (isGps) {
            lastGpsAt = SystemClock.elapsedRealtime()
            val prev = previous
            speedKmh = when {
                location.hasSpeed() -> location.speed * 3.6f
                prev != null -> {
                    val dt = (location.time - prev.time) / 1000f
                    if (dt > 0.2f) prev.distanceTo(location) / dt * 3.6f else speedKmh
                }
                else -> 0f
            }
            // Ignore jitter while parked.
            if ((speedKmh ?: 0f) < 2f) speedKmh = 0f
            previous = location
            last = location
        } else if (!hasFix()) {
            // Network fixes only help until GPS locks on.
            last = location
        }
        dispatch()
    }

    // These three must be implemented: the head unit runs Android 8.1, where
    // LocationListener has no default methods.
    @Deprecated("Required on Android 8.1")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) { dispatch() }
    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) { lastGpsAt = 0L; speedKmh = null }
        dispatch()
    }
}
