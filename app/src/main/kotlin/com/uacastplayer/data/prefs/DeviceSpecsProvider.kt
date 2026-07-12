package com.uacastplayer.data.prefs

import android.app.ActivityManager
import android.content.Context
import android.os.Build

data class DeviceSpecs(val totalRamBytes: Long, val cpuCoreCount: Int, val sdkInt: Int)

object DeviceSpecsProvider {
    fun current(context: Context): DeviceSpecs {
        val activityManager = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return DeviceSpecs(
            totalRamBytes = memoryInfo.totalMem,
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            sdkInt = Build.VERSION.SDK_INT,
        )
    }
}
