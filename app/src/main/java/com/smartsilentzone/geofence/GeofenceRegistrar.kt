package com.smartsilentzone.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.location.*
import com.smartsilentzone.domain.Zone
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class GeofenceRegistrar @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val client: GeofencingClient
) {
    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(ctx, GeofenceBroadcastReceiver::class.java)
        // Geofence PendingIntents MUST be mutable on API 31+ (Play Services attaches extras).
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        PendingIntent.getBroadcast(ctx, 0, intent, flags)
    }

    /** Re-registers everything. Call on app start, after BOOT_COMPLETED and when zones change. */
    @SuppressLint("MissingPermission") // caller verifies permissions
    suspend fun register(zones: List<Zone>): Boolean {
        val enabled = zones.filter { it.enabled }.take(100) // platform cap: 100 per app
        if (enabled.isEmpty()) {
            unregisterAll()
            return true
        }

        val geofences = enabled.map {
            Geofence.Builder()
                .setRequestId(it.id)
                .setCircularRegion(it.latitude, it.longitude, it.radiusMeters)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_ENTER or
                    Geofence.GEOFENCE_TRANSITION_DWELL or
                    Geofence.GEOFENCE_TRANSITION_EXIT
                )
                .setLoiteringDelay(30_000) // the 30-second sustained-presence requirement
                .build()
        }
        val request = GeofencingRequest.Builder()
            // Fire ENTER/DWELL immediately if the user is already inside at registration.
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofences(geofences)
            .build()

        return suspendCancellableCoroutine { cont ->
            client.removeGeofences(pendingIntent).addOnCompleteListener {
                client.addGeofences(request, pendingIntent)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(true) }
                    .addOnFailureListener {
                        Log.w("GeofenceRegistrar", "addGeofences failed", it)
                        if (cont.isActive) cont.resume(false)
                    }
            }
        }
    }

    fun unregisterAll() { client.removeGeofences(pendingIntent) }
}
