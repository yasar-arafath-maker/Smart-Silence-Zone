package com.smartsilentzone.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.smartsilentzone.data.MutableZoneRepository
import com.smartsilentzone.data.PresenceStore
import com.smartsilentzone.domain.*
import com.smartsilentzone.geofence.GeofenceRegistrar
import com.smartsilentzone.ringer.RingerModeManager
import com.smartsilentzone.work.ZoneWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject

data class PermissionState(
    val fineLocation: Boolean,
    val backgroundLocation: Boolean,
    val notifications: Boolean,
    val policyAccess: Boolean,
    val gpsEnabled: Boolean
) {
    val readyForAutomation get() = fineLocation && backgroundLocation && policyAccess && gpsEnabled
}

enum class NextPermissionStep { FINE_LOCATION, BACKGROUND_LOCATION, NOTIFICATIONS, POLICY_ACCESS, ENABLE_GPS, NONE }

data class ZoneUiState(
    val zones: List<Zone> = emptyList(),
    val insideZones: List<Zone> = emptyList(),
    val activeZone: Zone? = null,
    val targetAction: RingerAction? = null,
    val targetReason: String = "",
    val deviceAction: RingerAction = RingerAction.NORMAL,
    val permissions: PermissionState? = null,
    val nextStep: NextPermissionStep = NextPermissionStep.NONE,
    val registrationFailed: Boolean = false,
    val isSyncing: Boolean = false,
    val userMessage: String? = null
)

sealed interface UiEvent {
    data class OpenSettings(val intent: Intent) : UiEvent
    data class RequestPermission(val step: NextPermissionStep) : UiEvent
    data class ShowToast(val message: String) : UiEvent
}

@HiltViewModel
class ZoneEvaluationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val zoneRepository: MutableZoneRepository,
    private val presenceStore: PresenceStore,
    private val evaluator: ZoneEvaluator,
    val ringer: RingerModeManager,
    private val registrar: GeofenceRegistrar,
    private val scheduler: ZoneWorkScheduler,
    private val fused: FusedLocationProviderClient,
    val wifiFloorDetector: com.smartsilentzone.sensor.WifiFloorDetector,
    val wifiApDao: com.smartsilentzone.data.db.WifiApDao,
    val breakGlassSmsManager: com.smartsilentzone.sensor.BreakGlassSmsManager,
    val locationEngine: com.smartsilentzone.geofence.LiveCampusLocationEngine,
    val acousticEngine: com.smartsilentzone.sensor.AcousticCrowdEngine,
    val centralStateMachine: com.smartsilentzone.domain.CentralActionStateMachine,
    val flipDetector: com.smartsilentzone.sensor.FlipToShhhDetector
) : ViewModel() {

    val campusLocationState: StateFlow<com.smartsilentzone.geofence.LiveCampusLocationState> =
        locationEngine.observeCampusTracking(zoneRepository.zones)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.smartsilentzone.geofence.LiveCampusLocationState())

    init {
        // Continuous evaluation feeding live GPS + Wi-Fi + Acoustic + Flip into Central Action State Machine
        viewModelScope.launch {
            combine(
                campusLocationState,
                wifiFloorDetector.floorState,
                flipDetector.sensorState,
                acousticEngine.acousticState
            ) { loc, wifi, flip, acoustic ->
                centralStateMachine.evaluateSystemState(loc, wifi, flip, acoustic)
            }.collect()
        }
    }

    private val systemTick = MutableStateFlow(0)
    private val registrationFailed = MutableStateFlow(false)
    private val isSyncing = MutableStateFlow(false)

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val uiState: StateFlow<ZoneUiState> = combine(
        zoneRepository.zones,
        presenceStore.confirmedInside,
        systemTick,
        registrationFailed,
        isSyncing
    ) { zones, insideIds, _, regFailed, syncing ->
        val perms = readPermissions()
        val insideZones = zones.filter { it.enabled && it.id in insideIds }
        val decision = evaluator.decide(zones, insideIds)
        ZoneUiState(
            zones = zones,
            insideZones = insideZones,
            activeZone = (decision as? Decision.Apply)?.cause,
            targetAction = (decision as? Decision.Apply)?.action,
            targetReason = (decision as? Decision.Apply)?.reason.orEmpty(),
            deviceAction = ringer.currentAction(),
            permissions = perms,
            nextStep = nextStep(perms),
            registrationFailed = regFailed,
            isSyncing = syncing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ZoneUiState())

    fun refreshSystemState() {
        systemTick.update { it + 1 }
        viewModelScope.launch { ensureGeofencesRegistered() }
    }

    fun onGrantClicked() {
        when (val step = nextStep(readPermissions())) {
            NextPermissionStep.POLICY_ACCESS -> _events.tryEmit(UiEvent.OpenSettings(ringer.policyAccessIntent()))
            NextPermissionStep.ENABLE_GPS -> _events.tryEmit(
                UiEvent.OpenSettings(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            )
            NextPermissionStep.NONE -> Unit
            else -> _events.tryEmit(UiEvent.RequestPermission(step))
        }
    }

    fun forceEvaluate() {
        viewModelScope.launch {
            isSyncing.value = true
            try {
                ensureGeofencesRegistered()
                scheduler.enqueueEvaluate()
                _events.tryEmit(UiEvent.ShowToast("Zones re-evaluated successfully"))
            } finally {
                isSyncing.value = false
            }
        }
    }

    fun saveZone(zone: Zone) {
        viewModelScope.launch {
            try {
                zoneRepository.upsert(zone)
                ensureGeofencesRegistered()
                scheduler.enqueueEvaluate()
                _events.tryEmit(UiEvent.ShowToast("Saved zone: ${zone.name}"))
            } catch (e: Exception) {
                _events.tryEmit(UiEvent.ShowToast("Error saving: ${e.message}"))
            }
        }
    }

    fun deleteZone(id: String) {
        viewModelScope.launch {
            zoneRepository.delete(id)
            presenceStore.markOutside(id)
            ensureGeofencesRegistered()
            scheduler.enqueueEvaluate()
            _events.tryEmit(UiEvent.ShowToast("Zone deleted"))
        }
    }

    fun toggleZoneEnabled(zone: Zone) {
        viewModelScope.launch {
            val updated = zone.copy(enabled = !zone.enabled)
            zoneRepository.upsert(updated)
            if (!updated.enabled) {
                presenceStore.markOutside(zone.id)
            }
            ensureGeofencesRegistered()
            scheduler.enqueueEvaluate()
        }
    }

    suspend fun getCurrentLocation(): Location? {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null

        val token = CancellationTokenSource()
        return withTimeoutOrNull(8_000) {
            @Suppress("MissingPermission")
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
        }
    }

    fun seedCampusTemplates(centerLat: Double, centerLng: Double) {
        viewModelScope.launch {
            val college = Zone(
                id = UUID.randomUUID().toString(),
                name = "College Campus",
                type = ZoneType.COLLEGE,
                latitude = centerLat,
                longitude = centerLng,
                radiusMeters = 350f
            )
            val library = Zone(
                id = UUID.randomUUID().toString(),
                name = "Central Library",
                type = ZoneType.LIBRARY,
                latitude = centerLat + 0.0004,
                longitude = centerLng + 0.0004,
                radiusMeters = 60f
            )
            val classroom = Zone(
                id = UUID.randomUUID().toString(),
                name = "Lecture Complex",
                type = ZoneType.CLASSROOM,
                latitude = centerLat - 0.0003,
                longitude = centerLng + 0.0003,
                radiusMeters = 70f
            )
            val examHall = Zone(
                id = UUID.randomUUID().toString(),
                name = "Main Exam Hall",
                type = ZoneType.EXAM_HALL,
                latitude = centerLat + 0.0002,
                longitude = centerLng - 0.0004,
                radiusMeters = 50f
            )
            val canteen = Zone(
                id = UUID.randomUUID().toString(),
                name = "Campus Canteen",
                type = ZoneType.CANTEEN,
                latitude = centerLat - 0.0005,
                longitude = centerLng - 0.0005,
                radiusMeters = 50f
            )

            listOf(college, library, classroom, examHall, canteen).forEach { zoneRepository.upsert(it) }
            ensureGeofencesRegistered()
            scheduler.enqueueEvaluate()
            _events.tryEmit(UiEvent.ShowToast("Added 5 College Preset Zones!"))
        }
    }

    private suspend fun ensureGeofencesRegistered() {
        val p = readPermissions()
        if (!p.fineLocation || !p.backgroundLocation) return
        registrationFailed.value = !registrar.register(zoneRepository.getZones())
    }

    private fun readPermissions() = PermissionState(
        fineLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION),
        backgroundLocation = Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        notifications = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
        policyAccess = ringer.hasPolicyAccess(),
        gpsEnabled = (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled
    )

    private fun nextStep(p: PermissionState) = when {
        !p.fineLocation -> NextPermissionStep.FINE_LOCATION
        !p.backgroundLocation -> NextPermissionStep.BACKGROUND_LOCATION
        !p.notifications -> NextPermissionStep.NOTIFICATIONS
        !p.policyAccess -> NextPermissionStep.POLICY_ACCESS
        !p.gpsEnabled -> NextPermissionStep.ENABLE_GPS
        else -> NextPermissionStep.NONE
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
