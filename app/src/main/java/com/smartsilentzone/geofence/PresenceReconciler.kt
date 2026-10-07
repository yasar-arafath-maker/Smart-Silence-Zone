package com.smartsilentzone.geofence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.smartsilentzone.data.PresenceStore
import com.smartsilentzone.domain.ZoneRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * After a reboot, Play Services forgets geofence state, so a persisted "inside" zone can be
 * stale (the user may have left while the phone was off, and no EXIT will ever fire).
 * One low-power fix rebuilds the inside-set from reality. This deliberately skips the 30 s
 * hysteresis: it is state recovery, not a border crossing.
 */
@Singleton
class PresenceReconciler @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val fused: FusedLocationProviderClient,
    private val zoneRepository: ZoneRepository,
    private val presenceStore: PresenceStore
) {
    /** @return true if presence was rebuilt, false if no trustworthy fix was available. */
    suspend fun reconcile(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false

        val token = CancellationTokenSource()
        val loc = withTimeoutOrNull(15_000) {
            @Suppress("MissingPermission")
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token).await()
        }
        if (loc == null) { token.cancel(); return false }
        if (loc.hasAccuracy() && loc.accuracy > 150f) {
            Log.d(TAG, "Fix too coarse (${loc.accuracy} m); keeping stored presence")
            return false
        }

        val inside = zoneRepository.getZones().filter { z ->
            val out = FloatArray(1)
            Location.distanceBetween(loc.latitude, loc.longitude, z.latitude, z.longitude, out)
            z.enabled && out[0] <= z.radiusMeters
        }.map { it.id }.toSet()

        presenceStore.setInside(inside)
        Log.i(TAG, "Reconciled presence: ${inside.size} zone(s)")
        return true
    }

    private companion object { const val TAG = "PresenceReconciler" }
}
