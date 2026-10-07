package com.smartsilentzone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.smartsilentzone.sensor.FlipToShhhDetector
import com.smartsilentzone.sensor.WifiFloorDetector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmartZoneForegroundService : Service() {

    @Inject
    lateinit var wifiFloorDetector: WifiFloorDetector

    @Inject
    lateinit var flipDetector: FlipToShhhDetector

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "smart_silent_zone_foreground"

        fun start(context: Context) {
            val intent = Intent(context, SmartZoneForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SmartZoneForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundWithTypes()

        // Acquire partial wakelock with 10-second safety timeout for initialization transition
        acquireTransitionWakeLock(10_000L)

        // Start sensors & listeners
        wifiFloorDetector.startDetection()
        flipDetector.startListening()

        // Observe floor state changes
        serviceScope.launch {
            wifiFloorDetector.floorState.collect { state ->
                val title = state.matchedZone?.let { "${it.zoneName} (${it.floor})" } ?: "Monitoring Campus Zones"
                val subtitle = "Current Profile: ${state.matchedZone?.silentPolicy ?: "Normal"}"
                updateNotification(title, subtitle)
            }
        }
    }

    private fun startForegroundWithTypes() {
        val notification = buildNotification("Smart Silent Zone Active", "Guarding classroom audio profiles")

        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ (API 34/35) exact types compliance
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            foregroundServiceType
        )
    }

    private fun acquireTransitionWakeLock(timeoutMs: Long) {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartSilentZone::TransitionLock").apply {
                setReferenceCounted(false)
                acquire(timeoutMs) // Safe timeout prevents battery drain
            }
        } catch (_: Exception) {}
    }

    private fun buildNotification(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Smart Silent Zone Background Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps context-aware silent zone detection active"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wifiFloorDetector.stopDetection()
        flipDetector.stopListening()
        serviceScope.cancel()

        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
