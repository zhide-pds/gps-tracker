package com.gpstracker.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object BatteryUtils {
    /** Returns battery level as an integer 0–100, or -1 if unavailable. */
    fun getLevel(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        // API 21+: direct read
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level != null && level != Integer.MIN_VALUE) return level

        // Fallback: battery intent
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return -1
        val raw   = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (raw < 0 || scale <= 0) -1 else (raw * 100 / scale)
    }
}
