package com.smartsilentzone.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.smartsilentzone.data.PresenceStore
import com.smartsilentzone.work.ZoneWorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Does the minimum possible inside the ~10s receiver window: records presence
 * transitions and hands real work to WorkManager. Never touches the ringer directly.
 */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var presenceStore: PresenceStore
    @Inject lateinit var scheduler: ZoneWorkScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return

        if (event.hasError()) {
            Log.w(TAG, "Geofence error: ${GeofenceStatusCodes.getStatusCodeString(event.errorCode)}")
            // Services may have been reset (e.g. location toggled); reconcile state.
            scheduler.enqueueEvaluate()
            return
        }

        val zoneIds = event.triggeringGeofences.orEmpty().map { it.requestId }
        if (zoneIds.isEmpty()) return

        val transition = event.geofenceTransition
        val pending = goAsync()
        scope.launch {
            try {
                handle(transition, zoneIds)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed handling transition $transition", t)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(transition: Int, zoneIds: List<String>) {
        when (transition) {
            // Raw ENTER is only a candidate. It cancels a pending exit (anti-flapping).
            Geofence.GEOFENCE_TRANSITION_ENTER ->
                zoneIds.forEach { scheduler.cancelExitConfirm(it) }

            // DWELL = 30 s of sustained presence. This is the confirmed entry.
            Geofence.GEOFENCE_TRANSITION_DWELL -> {
                zoneIds.forEach {
                    scheduler.cancelExitConfirm(it)
                    presenceStore.markInside(it)
                }
                scheduler.enqueueEvaluate()
            }

            // Exit is also debounced by 30 s; the worker re-verifies before acting.
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                val confirmed = presenceStore.snapshotInside()
                zoneIds.filter { it in confirmed }.forEach { scheduler.scheduleExitConfirm(it) }
            }

            else -> Log.w(TAG, "Unknown transition $transition")
        }
    }

    private companion object { const val TAG = "GeofenceReceiver" }
}
