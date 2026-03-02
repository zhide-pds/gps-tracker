package com.gpstracker.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.gpstracker.R
import com.gpstracker.mqtt.MqttManager
import com.gpstracker.ui.MainActivity
import com.gpstracker.utils.AppPreferences
import com.gpstracker.utils.BatteryUtils
import kotlinx.coroutines.*

/**
 * Foreground service that keeps GPS tracking alive when the app is in the background.
 *
 * Supports two publish triggers:
 *  1. Interval — fires every N seconds regardless of movement
 *  2. Movement — fires when the device moves more than M metres from the last published point
 */
class TrackerService : Service() {

    companion object {
        const val TAG = "TrackerService"
        const val CHANNEL_ID = "tracker_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.gpstracker.START"
        const val ACTION_STOP  = "com.gpstracker.STOP"

        // Broadcast events (for updating the UI)
        const val BROADCAST_STATUS  = "com.gpstracker.STATUS"
        const val BROADCAST_LOCATION= "com.gpstracker.LOCATION"
        const val EXTRA_STATUS_MSG  = "status_msg"
        const val EXTRA_LAT         = "lat"
        const val EXTRA_LON         = "lon"
        const val EXTRA_BATTERY     = "battery"
        const val EXTRA_TRIGGER     = "trigger"

        fun startIntent(ctx: Context)  = Intent(ctx, TrackerService::class.java).setAction(ACTION_START)
        fun stopIntent(ctx: Context)   = Intent(ctx, TrackerService::class.java).setAction(ACTION_STOP)
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefs: AppPreferences
    private lateinit var mqttManager: MqttManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var intervalJob: Job? = null
    private var lastPublishedLocation: Location? = null
    private var currentLocation: Location? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        mqttManager = MqttManager(this).apply {
            onStatusChange = { msg -> broadcastStatus(msg) }
            onPublish      = { msg -> broadcastStatus(msg) }
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopTracking(); return START_NOT_STICKY }
            else        -> startTracking()
        }
        return START_STICKY  // Restart if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
        serviceScope.cancel()
    }

    // ── Start / Stop ──────────────────────────────────────────────────────────

    private fun startTracking() {
        Log.i(TAG, "Starting tracker service")
        startForeground(NOTIFICATION_ID, buildNotification("Connecting…"))

        serviceScope.launch {
            mqttManager.connect()
            startLocationUpdates()
            if (prefs.intervalModeEnabled) startIntervalPublisher()
        }
    }

    private fun stopTracking() {
        Log.i(TAG, "Stopping tracker service")
        intervalJob?.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        mqttManager.disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Location ──────────────────────────────────────────────────────────────

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            currentLocation = location
            broadcastLocation(location)
            updateNotification("Tracking · ${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}")

            // Movement-based trigger
            if (prefs.movementModeEnabled) {
                val last = lastPublishedLocation
                if (last == null || location.distanceTo(last) >= prefs.movementThresholdMetres) {
                    serviceScope.launch {
                        publish(location, "movement")
                        lastPublishedLocation = location
                    }
                }
            }
        }
    }

    @Suppress("MissingPermission")  // Permission is checked before starting the service
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5_000L  // 5-second location poll interval
        ).apply {
            setMinUpdateDistanceMeters(0f)
            setWaitForAccurateLocation(false)
        }.build()

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    // ── Interval Publisher ────────────────────────────────────────────────────

    private fun startIntervalPublisher() {
        intervalJob?.cancel()
        intervalJob = serviceScope.launch {
            while (isActive) {
                delay(prefs.publishIntervalSeconds * 1000L)
                currentLocation?.let { publish(it, "interval") }
            }
        }
    }

    // ── Publish ───────────────────────────────────────────────────────────────

    private suspend fun publish(location: Location, trigger: String) = withContext(Dispatchers.IO) {
        val battery = BatteryUtils.getLevel(this@TrackerService)
        mqttManager.publishLocation(location.latitude, location.longitude, battery, trigger)
        broadcastLocation(location, trigger)
    }

    // ── Broadcasts (UI updates) ───────────────────────────────────────────────

    private fun broadcastStatus(msg: String) {
        sendBroadcast(Intent(BROADCAST_STATUS).putExtra(EXTRA_STATUS_MSG, msg))
    }

    private fun broadcastLocation(location: Location, trigger: String = "gps") {
        val battery = BatteryUtils.getLevel(this)
        sendBroadcast(Intent(BROADCAST_LOCATION).apply {
            putExtra(EXTRA_LAT, location.latitude)
            putExtra(EXTRA_LON, location.longitude)
            putExtra(EXTRA_BATTERY, battery)
            putExtra(EXTRA_TRIGGER, trigger)
        })
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GPS Tracker",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Background location tracking" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 0,
            stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS Tracker Active")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_location)
            .setContentIntent(openApp)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
