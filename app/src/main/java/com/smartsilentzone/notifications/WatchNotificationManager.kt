package com.smartsilentzone.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notificationId = 1004

    fun postZoneHapticNotification(zoneName: String, isSilenced: Boolean) {
        val title = if (isSilenced) "Entered $zoneName" else "Left $zoneName"
        val message = if (isSilenced) "Phone silenced automatically." else "Ringer restored to Normal."

        val wearableExtender = NotificationCompat.WearableExtender()
            .setHintShowBackgroundOnly(false)

        val notification = NotificationCompat.Builder(context, NotificationChannels.SILENT_ZONE_EVENTS)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVibrate(longArrayOf(0, 300, 150, 300)) // Haptic vibration on paired watch
            .setAutoCancel(true)
            .extend(wearableExtender)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
        } catch (_: Exception) {}
    }
}
