package com.gpstracker.ui

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gpstracker.R
import com.gpstracker.databinding.ActivityMainBinding
import com.gpstracker.service.TrackerService
import com.gpstracker.utils.AppPreferences
import com.gpstracker.utils.BatteryUtils
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences
    private var isTracking = false
    private val logEntries = mutableListOf<String>()
    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // ── Broadcast receiver: updates from TrackerService ────────────────────
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                TrackerService.BROADCAST_STATUS -> {
                    val msg = intent.getStringExtra(TrackerService.EXTRA_STATUS_MSG) ?: return
                    addLog(msg)
                }
                TrackerService.BROADCAST_LOCATION -> {
                    val lat = intent.getDoubleExtra(TrackerService.EXTRA_LAT, 0.0)
                    val lon = intent.getDoubleExtra(TrackerService.EXTRA_LON, 0.0)
                    val bat = intent.getIntExtra(TrackerService.EXTRA_BATTERY, -1)
                    updateDashboard(lat, lon, bat)
                }
            }
        }
    }

    // ── Permission launcher ────────────────────────────────────────────────
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (allGranted) {
            startTracker()
        } else {
            Toast.makeText(this, "Location permission required for tracking", Toast.LENGTH_LONG).show()
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        prefs = AppPreferences(this)

        binding.tvDeviceId.text = prefs.deviceId
        binding.tvBattery.text  = "${BatteryUtils.getLevel(this)}%"

        binding.fabToggle.setOnClickListener {
            if (isTracking) stopTracker() else requestPermissionsAndStart()
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(TrackerService.BROADCAST_STATUS)
            addAction(TrackerService.BROADCAST_LOCATION)
        }
        registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statusReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // ── Tracker control ────────────────────────────────────────────────────

    private fun requestPermissionsAndStart() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        // Background location needs a separate request on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            needed.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startTracker() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun startTracker() {
        ContextCompat.startForegroundService(this, TrackerService.startIntent(this))
        isTracking = true
        updateTrackingUI()
        addLog("Tracker started")
    }

    private fun stopTracker() {
        startService(TrackerService.stopIntent(this))
        isTracking = false
        updateTrackingUI()
        addLog("Tracker stopped")
    }

    // ── UI helpers ─────────────────────────────────────────────────────────

    private fun updateTrackingUI() {
        binding.fabToggle.text         = if (isTracking) "STOP" else "START"
        binding.chipStatus.text        = if (isTracking) "LIVE" else "IDLE"
        binding.chipStatus.isSelected  = isTracking
    }

    private fun updateDashboard(lat: Double, lon: Double, battery: Int) {
        binding.tvLat.text     = String.format("%.6f°", lat)
        binding.tvLon.text     = String.format("%.6f°", lon)
        binding.tvBattery.text = if (battery >= 0) "$battery%" else "—"
    }

    private fun addLog(msg: String) {
        val entry = "[${sdf.format(Date())}] $msg"
        logEntries.add(0, entry)
        if (logEntries.size > 100) logEntries.removeLast()
        binding.tvLog.text = logEntries.joinToString("\n")
    }
}
