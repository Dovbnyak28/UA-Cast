package com.uacastplayer.data.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import com.uacastplayer.log.AppLog

private const val TAG = "CastWakeLocks"
private const val WAKE_LOCK_TAG = "UACastPlayer:proxySession"
private const val MAX_WAKE_LOCK_MILLIS = 10L * 3_600_000L

/** Held only while the local proxy session is actually serving the receiver; released the moment it ends. */
class CastWakeLocks(context: Context) {

    private val appContext = context.applicationContext
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    // WakeLock/WifiLock acquisition can fail for several unrelated reasons (permission revoked,
    // power service unavailable, OEM restrictions) - none of them should crash the cast session
    // that's just trying to keep the screen/network alive while streaming, only skip the lock.
    @Suppress("TooGenericExceptionCaught")
    fun acquire() {
        release()
        try {
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(MAX_WAKE_LOCK_MILLIS)
            }
            val wifiManager = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager.createWifiLock(wifiMode, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            AppLog.w(TAG) { "Failed to acquire proxy session locks: ${e.javaClass.simpleName}" }
        }
    }

    fun release() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }
}
