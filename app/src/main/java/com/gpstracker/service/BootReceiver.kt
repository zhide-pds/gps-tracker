package com.gpstracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.gpstracker.utils.AppPreferences

/**
 * Receives BOOT_COMPLETED and starts the tracker service if "start on boot" is enabled.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        val prefs = AppPreferences(context)
        if (prefs.startOnBoot) {
            Log.i("BootReceiver", "Boot received — starting TrackerService")
            ContextCompat.startForegroundService(context, TrackerService.startIntent(context))
        }
    }
}
