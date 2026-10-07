package com.smartsilentzone.domain

import com.smartsilentzone.geofence.LiveCampusLocationState
import com.smartsilentzone.notifications.WatchNotificationManager
import com.smartsilentzone.ringer.RingerModeManager
import com.smartsilentzone.sensor.AcousticCrowdEngine
import com.smartsilentzone.sensor.AcousticState
import com.smartsilentzone.sensor.BreakGlassSmsManager
import com.smartsilentzone.sensor.FlipSensorState
import com.smartsilentzone.sensor.WifiFloorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class ActiveSystemPriority {
    PRIORITY_1_VIP_EMERGENCY,
    PRIORITY_2_MANUAL_OVERRIDE,
    PRIORITY_3_CONTEXTUAL_MATRIX,
    IDLE_NORMAL
}

data class CentralSystemState(
    val priority: ActiveSystemPriority = ActiveSystemPriority.IDLE_NORMAL,
    val effectiveRingerAction: RingerAction = RingerAction.NORMAL,
    val reason: String = "Monitoring context",
    val isEmergencyActive: Boolean = false,
    val isManualOverrideActive: Boolean = false,
    val isFlipToShhhActive: Boolean = false,
    val activeZone: Zone? = null,
    val liveDecibels: Double = 35.0,
    val wifiFloorZoneName: String? = null,
    val lastTransitionTimestamp: Long = System.currentTimeMillis()
)

@Singleton
class CentralActionStateMachine @Inject constructor(
    private val ringerModeManager: RingerModeManager,
    private val breakGlassSmsManager: BreakGlassSmsManager,
    private val acousticCrowdEngine: AcousticCrowdEngine,
    private val watchNotificationManager: WatchNotificationManager
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _systemState = MutableStateFlow(CentralSystemState())
    val systemState: StateFlow<CentralSystemState> = _systemState.asStateFlow()

    private var manualOverrideMode: RingerAction? = null

    fun setManualOverride(enabled: Boolean, overrideAction: RingerAction = RingerAction.NORMAL) {
        manualOverrideMode = if (enabled) overrideAction else null
        evaluateSystemState(
            locationState = null,
            wifiState = null,
            flipState = null,
            acousticState = null
        )
    }

    fun dismissEmergencyOverride() {
        breakGlassSmsManager.dismissEmergencyAlarm()
        evaluateSystemState(
            locationState = null,
            wifiState = null,
            flipState = null,
            acousticState = null
        )
    }

    /**
     * Centralized Evaluation Engine:
     * Evaluates Priority 1 -> Priority 2 -> Priority 3.
     */
    fun evaluateSystemState(
        locationState: LiveCampusLocationState?,
        wifiState: WifiFloorState?,
        flipState: FlipSensorState?,
        acousticState: AcousticState?
    ) {
        scope.launch {
            // PRIORITY 1: VIP Emergency Alarm Override
            if (breakGlassSmsManager.isEmergencyAlarmPlaying) {
                applyRingerNonBlocking(RingerAction.NORMAL)
                _systemState.value = _systemState.value.copy(
                    priority = ActiveSystemPriority.PRIORITY_1_VIP_EMERGENCY,
                    effectiveRingerAction = RingerAction.NORMAL,
                    reason = "🚨 VIP Emergency Override Triggered • Loud Alarm Sounding",
                    isEmergencyActive = true,
                    lastTransitionTimestamp = System.currentTimeMillis()
                )
                return@launch
            }

            // PRIORITY 2: User Manual Override / Break-Glass
            manualOverrideMode?.let { manualAction ->
                applyRingerNonBlocking(manualAction)
                _systemState.value = _systemState.value.copy(
                    priority = ActiveSystemPriority.PRIORITY_2_MANUAL_OVERRIDE,
                    effectiveRingerAction = manualAction,
                    reason = "User Manual Override Active ($manualAction)",
                    isManualOverrideActive = true,
                    isEmergencyActive = false,
                    lastTransitionTimestamp = System.currentTimeMillis()
                )
                return@launch
            }

            // PRIORITY 3: Contextual Multi-Sensor Fusion Matrix
            // Factor 3A: Flip-to-Shhh Face-Down condition
            if (flipState?.isSilenceApplied == true) {
                applyRingerNonBlocking(RingerAction.SILENT)
                _systemState.value = _systemState.value.copy(
                    priority = ActiveSystemPriority.PRIORITY_3_CONTEXTUAL_MATRIX,
                    effectiveRingerAction = RingerAction.SILENT,
                    reason = "Flip-to-Shhh Engaged (Phone Face Down) • Auto-Silenced",
                    isFlipToShhhActive = true,
                    isManualOverrideActive = false,
                    isEmergencyActive = false,
                    lastTransitionTimestamp = System.currentTimeMillis()
                )
                return@launch
            }

            // Factor 3B: Wi-Fi Floor & GPS Zone + Acoustic Sound Fusion
            val currentInsideZone = locationState?.activeInsideZones?.maxByOrNull { it.type.priority }
            val wifiPolicy = wifiState?.matchedZone?.silentPolicy
            val decibels = acousticState?.currentDecibels ?: _systemState.value.liveDecibels

            val (acousticDecisionAction, acousticReason) = acousticCrowdEngine.evaluateAcousticPolicy(
                activeZone = currentInsideZone,
                wifiPolicy = wifiPolicy,
                currentDb = decibels
            )

            // Check if approaching zone with predictive vector
            val approachingEvent = locationState?.predictiveEvents?.firstOrNull { it.isApproaching && (it.etaSeconds ?: 10f) < 6f }
            val finalReason = if (approachingEvent != null && currentInsideZone == null) {
                "Approaching ${approachingEvent.zone.name} (ETA ~${approachingEvent.etaSeconds?.toInt()}s) • Pre-Arming Audio Policy"
            } else {
                acousticReason
            }

            val previousAction = _systemState.value.effectiveRingerAction
            applyRingerNonBlocking(acousticDecisionAction)

            // Notify smartwatch if entering/leaving silent profile
            if (acousticDecisionAction != previousAction) {
                val isSilenced = acousticDecisionAction == RingerAction.SILENT || acousticDecisionAction == RingerAction.DND
                val zoneName = currentInsideZone?.name ?: wifiState?.matchedZone?.zoneName ?: "Campus Block"
                watchNotificationManager.postZoneHapticNotification(zoneName, isSilenced)
            }

            _systemState.value = CentralSystemState(
                priority = ActiveSystemPriority.PRIORITY_3_CONTEXTUAL_MATRIX,
                effectiveRingerAction = acousticDecisionAction,
                reason = finalReason,
                isEmergencyActive = false,
                isManualOverrideActive = false,
                isFlipToShhhActive = false,
                activeZone = currentInsideZone,
                liveDecibels = decibels,
                wifiFloorZoneName = wifiState?.matchedZone?.zoneName,
                lastTransitionTimestamp = System.currentTimeMillis()
            )
        }
    }

    private suspend fun applyRingerNonBlocking(action: RingerAction) {
        ringerModeManager.apply(action)
    }
}
