package com.smartsilentzone.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    const val WORK = "zone_work"
    const val SILENT_ZONE_EVENTS = "silent_zone_events"

    fun create(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(WORK, "Zone checks", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shown briefly while the app verifies your location"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(SILENT_ZONE_EVENTS, "Zone State Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts posted on entering/leaving silent zones, mirrored to smartwatch with haptic vibration"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            }
        )
    }
}
