package com.gpstracker.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.util.UUID

/**
 * Central store for all app configuration.
 * All settings are persisted in SharedPreferences and survive app restarts.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    // ── Device Identity ──────────────────────────────────────────────────────
    val deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id == null) {
                // Generate a stable unique ID once, persist it forever
                id = "TRACKER-" + UUID.randomUUID().toString().uppercase().take(9)
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }

    // ── MQTT Broker Settings ──────────────────────────────────────────────────
    var mqttHost: String
        get() = prefs.getString(KEY_MQTT_HOST, "broker.hivemq.com") ?: "broker.hivemq.com"
        set(v) = prefs.edit().putString(KEY_MQTT_HOST, v).apply()

    var mqttPort: Int
        get() = prefs.getString(KEY_MQTT_PORT, "1883")?.toIntOrNull() ?: 1883
        set(v) = prefs.edit().putString(KEY_MQTT_PORT, v.toString()).apply()

    var mqttTopic: String
        get() = prefs.getString(KEY_MQTT_TOPIC, "tracker/devices") ?: "tracker/devices"
        set(v) = prefs.edit().putString(KEY_MQTT_TOPIC, v).apply()

    var mqttUsername: String
        get() = prefs.getString(KEY_MQTT_USER, "") ?: ""
        set(v) = prefs.edit().putString(KEY_MQTT_USER, v).apply()

    var mqttPassword: String
        get() = prefs.getString(KEY_MQTT_PASS, "") ?: ""
        set(v) = prefs.edit().putString(KEY_MQTT_PASS, v).apply()

    var mqttUseTls: Boolean
        get() = prefs.getBoolean(KEY_MQTT_TLS, false)
        set(v) = prefs.edit().putBoolean(KEY_MQTT_TLS, v).apply()

    val mqttBrokerUri: String
        get() {
            val scheme = if (mqttUseTls) "ssl" else "tcp"
            return "$scheme://$mqttHost:$mqttPort"
        }

    // ── Publish Modes ─────────────────────────────────────────────────────────
    var intervalModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_INTERVAL_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_INTERVAL_ENABLED, v).apply()

    /** Interval in seconds between publishes */
    var publishIntervalSeconds: Int
        get() = prefs.getString(KEY_INTERVAL_SECS, "30")?.toIntOrNull() ?: 30
        set(v) = prefs.edit().putString(KEY_INTERVAL_SECS, v.toString()).apply()

    var movementModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_MOVEMENT_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_MOVEMENT_ENABLED, v).apply()

    /** Minimum distance change in metres to trigger a publish */
    var movementThresholdMetres: Float
        get() = prefs.getString(KEY_MOVEMENT_METRES, "50")?.toFloatOrNull() ?: 50f
        set(v) = prefs.edit().putString(KEY_MOVEMENT_METRES, v.toString()).apply()

    // ── Background / Boot ────────────────────────────────────────────────────
    var startOnBoot: Boolean
        get() = prefs.getBoolean(KEY_START_ON_BOOT, false)
        set(v) = prefs.edit().putBoolean(KEY_START_ON_BOOT, v).apply()

    companion object {
        const val KEY_DEVICE_ID       = "device_id"
        const val KEY_MQTT_HOST       = "mqtt_host"
        const val KEY_MQTT_PORT       = "mqtt_port"
        const val KEY_MQTT_TOPIC      = "mqtt_topic"
        const val KEY_MQTT_USER       = "mqtt_username"
        const val KEY_MQTT_PASS       = "mqtt_password"
        const val KEY_MQTT_TLS        = "mqtt_tls"
        const val KEY_INTERVAL_ENABLED= "interval_enabled"
        const val KEY_INTERVAL_SECS   = "publish_interval_secs"
        const val KEY_MOVEMENT_ENABLED= "movement_enabled"
        const val KEY_MOVEMENT_METRES = "movement_threshold_metres"
        const val KEY_START_ON_BOOT   = "start_on_boot"
    }
}
