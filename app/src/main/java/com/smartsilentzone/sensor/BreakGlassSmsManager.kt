package com.smartsilentzone.sensor

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.telephony.SmsManager
import com.smartsilentzone.data.PresenceStore
import com.smartsilentzone.data.db.ContactDao
import com.smartsilentzone.ringer.RingerModeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BreakGlassSmsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactDao: ContactDao,
    private val presenceStore: PresenceStore,
    private val ringerModeManager: RingerModeManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lastRepliedMap = ConcurrentHashMap<String, Long>()
    private val rateLimitMillis = 30 * 60 * 1000L // 30 minutes

    private var emergencyPlayer: MediaPlayer? = null
    var isEmergencyAlarmPlaying: Boolean = false
        private set

    fun handleMissedCall(incomingNumber: String) {
        scope.launch {
            // Check if caller is in VIP contact whitelist
            val vipContacts = contactDao.getAllContacts().first()
            val cleanIncoming = incomingNumber.replace("[^0-9+]".toRegex(), "")
            val matchedVip = vipContacts.find {
                it.phoneNumber.replace("[^0-9+]".toRegex()) == cleanIncoming
            } ?: return@launch

            // Check if phone is in silent zone
            val currentMode = ringerModeManager.getCurrentRingerMode()
            val isSilent = currentMode == AudioManager.RINGER_MODE_SILENT || currentMode == AudioManager.RINGER_MODE_VIBRATE
            if (!isSilent) return@launch

            // Check 30-min rate limit per contact
            val lastTime = lastRepliedMap[cleanIncoming] ?: 0L
            val now = System.currentTimeMillis()
            if (now - lastTime < rateLimitMillis) {
                return@launch
            }

            // Send Break-Glass SMS
            val activeZone = presenceStore.getActiveZoneName() ?: "Silent Zone"
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val expectedFreeTime = timeFormat.format(Date(now + 60 * 60 * 1000L))

            val message = "I'm in $activeZone. My phone is silenced until $expectedFreeTime. For an emergency, reply EMERGENCY."

            sendSms(incomingNumber, message)
            lastRepliedMap[cleanIncoming] = now
        }
    }

    private fun sendSms(destinationNumber: String, message: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(destinationNumber, null, message, null, null)
        } catch (_: Exception) {}
    }

    fun handleIncomingSms(senderNumber: String, messageBody: String) {
        scope.launch {
            if (messageBody.trim().equals("EMERGENCY", ignoreCase = true)) {
                val vipContacts = contactDao.getAllContacts().first()
                val cleanSender = senderNumber.replace("[^0-9+]".toRegex(), "")
                val isVip = vipContacts.any {
                    it.phoneNumber.replace("[^0-9+]".toRegex()) == cleanSender
                }

                if (isVip) {
                    playEmergencyAlarm()
                }
            }
        }
    }

    fun playEmergencyAlarm() {
        if (isEmergencyAlarmPlaying) return
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVol, 0)

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            emergencyPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            isEmergencyAlarmPlaying = true
        } catch (_: Exception) {}
    }

    fun dismissEmergencyAlarm() {
        emergencyPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        emergencyPlayer = null
        isEmergencyAlarmPlaying = false
    }
}
