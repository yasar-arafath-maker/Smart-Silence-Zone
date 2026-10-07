package com.smartsilentzone.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.smartsilentzone.data.PresenceStore
import com.smartsilentzone.domain.*
import com.smartsilentzone.geofence.PresenceReconciler
import com.smartsilentzone.notifications.NotificationChannels
import com.smartsilentzone.ringer.RingerModeManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

@HiltWorker
class ZoneEvaluationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val zoneRepository: ZoneRepository,
    private val presenceStore: PresenceStore,
    private val evaluator: ZoneEvaluator,
    private val ringer: RingerModeManager,
    private val fused: FusedLocationProviderClient,
    private val reconciler: PresenceReconciler
) : CoroutineWorker(appContext, params) {

    /**
     * REQUIRED for expedited work on Android 11 and below (API < 31). Without it WorkManager
     * throws IllegalStateException when the expedited request runs. API 31+ ignores it.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val n = NotificationCompat.Builder(appContext, NotificationChannels.WORK)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setContentTitle("Checking your zone")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        return ForegroundInfo(FOREGROUND_ID, n)
    }

    override suspend fun doWork(): Result {
        return try {
            if (inputData.getBoolean(ZoneWorkScheduler.KEY_RECONCILE, false)) {
                reconciler.reconcile()
            }

            val zones = zoneRepository.getZones()

            // Exit-confirmation path: verify with one low-power fix before accepting the exit.
            val exitId = inputData.getString(ZoneWorkScheduler.KEY_ZONE_ID)
            if (exitId != null) {
                val zone = zones.firstOrNull { it.id == exitId }
                when {
                    zone == null -> presenceStore.markOutside(exitId) // zone was deleted
                    stillInside(zone) -> Log.d(TAG, "Exit rejected: still inside ${zone.name}")
                    else -> presenceStore.markOutside(exitId)
                }
            }

            evaluateAndApply(zones)
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Evaluation failed", t)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun evaluateAndApply(zones: List<Zone>) {
        val inside = presenceStore.snapshotInside()
            .filter { id -> zones.any { it.id == id && it.enabled } }.toSet()

        when (val d = evaluator.decide(zones, inside)) {
            Decision.NoChange -> Unit
            is Decision.Apply -> {
                // Only act on a change in *decision*; don't fight manual tweaks made within a zone.
                if (presenceStore.lastAppliedOnce() == d.action && ringer.currentAction() == d.action) return
                when (val r = ringer.apply(d.action)) {
                    is RingerModeManager.Result.Applied,
                    is RingerModeManager.Result.AlreadyActive -> {
                        presenceStore.setLastApplied(d.action)
                        Log.i(TAG, "${d.reason} -> ${d.action}")
                    }
                    RingerModeManager.Result.PolicyAccessMissing ->
                        Log.w(TAG, "Policy access missing; ringer not changed")
                    is RingerModeManager.Result.Failed ->
                        Log.w(TAG, "Ringer change failed: $r") // lastApplied untouched, so the next event retries
                }
            }
        }
    }

    private suspend fun stillInside(zone: Zone): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false

        val token = CancellationTokenSource()
        val loc: Location = withTimeoutOrNull(10_000) {
            @Suppress("MissingPermission")
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token).await()
        } ?: run { token.cancel(); return false } // no fix: trust the geofence EXIT

        val out = FloatArray(1)
        Location.distanceBetween(loc.latitude, loc.longitude, zone.latitude, zone.longitude, out)
        // Small buffer so GPS jitter on the boundary doesn't cancel a legitimate exit.
        return out[0] < zone.radiusMeters - 5f
    }

    private companion object {
        const val TAG = "ZoneEvalWorker"
        const val FOREGROUND_ID = 4201
    }
}
