package com.uacastplayer.ui.epg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import java.time.ZoneId

/** Device-local calendar policy, observed only while an open guide is visible. */
@Composable
internal fun rememberEpgTimeZone(): ZoneId {
    val context = LocalContext.current.applicationContext
    var zone by remember(context) { mutableStateOf(ZoneId.systemDefault()) }
    LifecycleStartEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_TIMEZONE_CHANGED) zone = ZoneId.systemDefault()
            }
        }
        // This protected system action has no application commands/payloads. Read the actual
        // device zone, never the broadcast's extras. Compatibility registration covers API 24+.
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED), ContextCompat.RECEIVER_EXPORTED,
        )
        // A stopped guide may have missed the system event; resuming must reread the setting.
        zone = ZoneId.systemDefault()
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
    return zone
}
