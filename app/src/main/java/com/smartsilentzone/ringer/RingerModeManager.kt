package com.smartsilentzone.ringer

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import com.smartsilentzone.domain.RingerAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RingerModeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioManager: AudioManager,
    private val notificationManager: NotificationManager
) {
    sealed interface Result {
        data class Applied(val action: RingerAction) : Result
        data class AlreadyActive(val action: RingerAction) : Result
        data object PolicyAccessMissing : Result
        data class Failed(val requested: RingerAction) : Result
    }

    fun hasPolicyAccess(): Boolean = notificationManager.isNotificationPolicyAccessGranted

    /** Opens the system screen where the user grants Do Not Disturb access. */
    fun policyAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** What the device is really doing right now (not what we last asked for). */
    fun currentAction(): RingerAction {
        when (notificationManager.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            NotificationManager.INTERRUPTION_FILTER_ALARMS,
            NotificationManager.INTERRUPTION_FILTER_NONE -> return RingerAction.DND
        }
        return when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> RingerAction.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> RingerAction.VIBRATE
            else -> RingerAction.NORMAL
        }
    }

    /**
     * Applies [action] and VERIFIES it by reading the system state back.
     * Never reports success unless the device actually changed.
     */
    suspend fun apply(action: RingerAction): Result {
        if (!hasPolicyAccess()) return Result.PolicyAccessMissing
        if (currentAction() == action) return Result.AlreadyActive(action)

        try {
            when (action) {
                RingerAction.NORMAL -> {
                    clearDnd()
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                }
                RingerAction.VIBRATE -> {
                    clearDnd()
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
                RingerAction.SILENT -> {
                    clearDnd()
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                }
                RingerAction.DND -> {
                    // PRIORITY keeps the user's own "starred contacts / repeat callers" exceptions.
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Policy access revoked", e)
            return Result.PolicyAccessMissing
        } catch (e: Exception) {
            Log.e(TAG, "Ringer change threw", e)
            return Result.Failed(action)
        }

        return if (awaitState(action)) Result.Applied(action) else Result.Failed(action)
    }

    private suspend fun clearDnd() {
        val f = notificationManager.currentInterruptionFilter
        if (f != NotificationManager.INTERRUPTION_FILTER_ALL &&
            f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            // The filter change is async; wait briefly so the following ringerMode write sticks.
            repeat(10) {
                if (notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) return
                delay(50)
            }
        }
    }

    private suspend fun awaitState(expected: RingerAction): Boolean {
        repeat(10) {
            if (currentAction() == expected) return true
            delay(100)
        }
        return false
    }

    private companion object { const val TAG = "RingerModeManager" }
}
