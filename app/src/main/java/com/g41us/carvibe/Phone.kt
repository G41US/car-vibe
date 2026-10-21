package com.g41us.carvibe

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

data class PhoneState(
    val connected: Boolean,
    val name: String?,
    /** 0-100, or -1 when the phone doesn't report it. */
    val battery: Int = -1,
    /** True when the phone app (RLink) itself reports an active session. */
    val projecting: Boolean = false,
)

/**
 * Works out whether a phone is connected, and its name, from two sources:
 * 1. Android's Bluetooth (hands-free / audio profiles).
 * 2. The phone app's (RLink's) own notifications, via [MediaHub], in case this
 *    head unit runs its phone link outside Android's Bluetooth stack.
 */
class PhoneMonitor(private val ctx: Context, private val onChange: (PhoneState) -> Unit) {

    private val adapter: BluetoothAdapter? = try { BluetoothAdapter.getDefaultAdapter() } catch (e: Exception) { null }
    private var headset: BluetoothProfile? = null
    private var a2dp: BluetoothProfile? = null
    private var aclDevice: BluetoothDevice? = null
    private var batteryLevel = -1
    private var registered = false

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                BluetoothProfile.HEADSET -> headset = proxy
                BluetoothProfile.A2DP -> a2dp = proxy
            }
            refresh()
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.HEADSET -> headset = null
                BluetoothProfile.A2DP -> a2dp = null
            }
            refresh()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val device: BluetoothDevice? = try {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } catch (e: Exception) { null }
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> aclDevice = device
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (device == aclDevice) { aclDevice = null; batteryLevel = -1 }
                ACTION_BATTERY_LEVEL_CHANGED -> batteryLevel = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                BluetoothAdapter.ACTION_STATE_CHANGED -> if (adapter?.isEnabled != true) { aclDevice = null; batteryLevel = -1 }
            }
            refresh()
        }
    }

    private val hubListener = MediaHub.Listener { refresh() }

    fun start() {
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(ACTION_BATTERY_LEVEL_CHANGED)
            }
            try { ctx.registerReceiver(receiver, filter) } catch (e: Exception) { }
            registered = true
            try {
                adapter?.getProfileProxy(ctx, profileListener, BluetoothProfile.HEADSET)
                adapter?.getProfileProxy(ctx, profileListener, BluetoothProfile.A2DP)
            } catch (e: Exception) { }
            MediaHub.add(hubListener)
        }
        refresh()
    }

    fun stop() {
        if (!registered) return
        try { ctx.unregisterReceiver(receiver) } catch (e: Exception) { }
        try {
            headset?.let { adapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
            a2dp?.let { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
        } catch (e: Exception) { }
        headset = null
        a2dp = null
        MediaHub.remove(hubListener)
        registered = false
    }

    fun refresh() {
        onChange(current())
    }

    fun current(): PhoneState {
        val devices = LinkedHashSet<BluetoothDevice>()
        try { headset?.connectedDevices?.let { devices.addAll(it) } } catch (e: Exception) { }
        try { a2dp?.connectedDevices?.let { devices.addAll(it) } } catch (e: Exception) { }
        val device = devices.firstOrNull() ?: aclDevice?.takeIf { adapter?.isEnabled == true }

        val projecting = MediaHub.phoneAppActive
        if (device != null) {
            val battery = if (batteryLevel >= 0) batteryLevel else readBattery(device)
            return PhoneState(true, nameOf(device), battery, projecting)
        }
        if (projecting) {
            return PhoneState(true, MediaHub.phoneAppHint, -1, true)
        }
        return PhoneState(false, null)
    }

    private fun nameOf(device: BluetoothDevice): String? {
        // The alias is what the user renamed the phone to in Bluetooth settings.
        val alias = try {
            device.javaClass.getMethod("getAliasName").invoke(device) as? String
        } catch (e: Exception) {
            try { device.javaClass.getMethod("getAlias").invoke(device) as? String } catch (e2: Exception) { null }
        }
        return alias?.takeIf { it.isNotBlank() } ?: try { device.name } catch (e: SecurityException) { null }
    }

    private fun readBattery(device: BluetoothDevice): Int = try {
        // Hidden API on Android 8.1; reachable by reflection there.
        device.javaClass.getMethod("getBatteryLevel").invoke(device) as? Int ?: -1
    } catch (e: Exception) { -1 }

    companion object {
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    }
}
