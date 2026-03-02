package com.gpstracker.mqtt

import android.content.Context
import android.util.Log
import com.gpstracker.utils.AppPreferences
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles all MQTT connection management and publishing.
 * Uses Eclipse Paho v3 client directly (no Android service layer needed).
 */
class MqttManager(private val context: Context) {

    private val tag = "MqttManager"
    private val prefs = AppPreferences(context)
    private var client: MqttClient? = null
    private val connecting = AtomicBoolean(false)

    var onStatusChange: ((String) -> Unit)? = null
    var onPublish: ((String) -> Unit)? = null

    // ── Connection ────────────────────────────────────────────────────────────

    fun connect(): Boolean {
        if (isConnected()) return true
        if (connecting.getAndSet(true)) return false

        return try {
            val clientId = prefs.deviceId
            client = MqttClient(prefs.mqttBrokerUri, clientId, MemoryPersistence())

            val opts = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 15
                keepAliveInterval = 60
                isAutomaticReconnect = true
                if (prefs.mqttUsername.isNotBlank()) {
                    userName = prefs.mqttUsername
                    password = prefs.mqttPassword.toCharArray()
                }
            }

            client?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.w(tag, "Connection lost: ${cause?.message}")
                    onStatusChange?.invoke("Disconnected: ${cause?.message ?: "unknown"}")
                }
                override fun messageArrived(topic: String?, message: MqttMessage?) {}
                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            client?.connect(opts)
            Log.i(tag, "Connected to ${prefs.mqttBrokerUri}")
            onStatusChange?.invoke("Connected to ${prefs.mqttHost}")
            true
        } catch (e: MqttException) {
            Log.e(tag, "Connect failed: ${e.message}")
            onStatusChange?.invoke("Connect failed: ${e.message}")
            false
        } finally {
            connecting.set(false)
        }
    }

    fun disconnect() {
        try {
            client?.takeIf { it.isConnected }?.disconnect()
            onStatusChange?.invoke("Disconnected")
        } catch (e: MqttException) {
            Log.e(tag, "Disconnect error: ${e.message}")
        }
    }

    fun isConnected(): Boolean = client?.isConnected == true

    // ── Publishing ────────────────────────────────────────────────────────────

    /**
     * Build JSON payload and publish to the configured topic.
     * @param lat       Latitude in decimal degrees
     * @param lon       Longitude in decimal degrees
     * @param battery   Battery level 0–100
     * @param trigger   What caused this publish: "interval" | "movement" | "manual"
     */
    fun publishLocation(lat: Double, lon: Double, battery: Int, trigger: String = "interval"): Boolean {
        if (!isConnected()) {
            Log.w(tag, "Not connected, attempting reconnect before publish")
            if (!connect()) return false
        }

        return try {
            val payload = JSONObject().apply {
                put("deviceId", prefs.deviceId)
                put("lat", lat)
                put("lon", lon)
                put("battery", battery)
                put("timestamp", System.currentTimeMillis() / 1000)
                put("trigger", trigger)
            }.toString()

            val message = MqttMessage(payload.toByteArray(Charsets.UTF_8)).apply {
                qos = 1
                isRetained = false
            }

            client?.publish(prefs.mqttTopic, message)
            Log.i(tag, "Published to ${prefs.mqttTopic}: $payload")
            onPublish?.invoke("Published → ${prefs.mqttTopic} [$trigger]")
            true
        } catch (e: MqttException) {
            Log.e(tag, "Publish failed: ${e.message}")
            onPublish?.invoke("Publish FAILED: ${e.message}")
            false
        }
    }
}
