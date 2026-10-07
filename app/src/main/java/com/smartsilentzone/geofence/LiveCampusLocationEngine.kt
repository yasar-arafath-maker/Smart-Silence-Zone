package com.smartsilentzone.geofence

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import com.smartsilentzone.domain.Zone
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

data class PredictiveZoneEvent(
    val zone: Zone,
    val distanceToBoundaryMeters: Float,
    val etaSeconds: Float?,
    val isApproaching: Boolean
)

data class LiveCampusLocationState(
    val location: Location? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Float = 0f,
    val speedMps: Float = 0f,
    val bearingDegrees: Float = 0f,
    val nearestZone: Zone? = null,
    val distanceToNearestBoundaryMeters: Float = Float.MAX_VALUE,
    val predictiveEvents: List<PredictiveZoneEvent> = emptyList(),
    val isInsideAnyZone: Boolean = false,
    val activeInsideZones: List<Zone> = emptyList()
)

@Singleton
class LiveCampusLocationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    companion object {
        private const val GPS_INTERVAL_MS = 2500L
        private const val FASTEST_INTERVAL_MS = 1000L
        private const val PREDICTION_HORIZON_SECONDS = 8.0f
    }

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        GPS_INTERVAL_MS
    ).apply {
        setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
        setMinUpdateDistanceMeters(0.8f) // High-precision campus tracking
        setWaitForAccurateLocation(false)
    }.build()

    /**
     * Cold stream that registers FusedLocationProviderClient and yields live Location updates.
     */
    @SuppressLint("MissingPermission")
    fun observeLiveLocation(): Flow<Location> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            close(e)
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    /**
     * Fuses live GPS location with campus zones to calculate exact boundary distances
     * and predictive trajectory vectors (anticipating zone entry before threshold crossing).
     */
    fun observeCampusTracking(zonesFlow: Flow<List<Zone>>): Flow<LiveCampusLocationState> {
        return combine(observeLiveLocation(), zonesFlow) { loc, zones ->
            evaluateLocationWithPredictions(loc, zones)
        }
    }

    private fun evaluateLocationWithPredictions(
        location: Location,
        zones: List<Zone>
    ): LiveCampusLocationState {
        val userLat = location.latitude
        val userLng = location.longitude
        val speed = if (location.hasSpeed()) location.speed else 0f
        val bearing = if (location.hasBearing()) location.bearing else 0f

        // Predicted position in T seconds based on vector (speed * time)
        val predictedDistance = speed * PREDICTION_HORIZON_SECONDS
        val predictedCoords = calculatePredictedCoordinate(userLat, userLng, predictedDistance, bearing)

        val predictiveEvents = mutableListOf<PredictiveZoneEvent>()
        val activeInsideZones = mutableListOf<Zone>()
        var nearestZone: Zone? = null
        var minBoundaryDist = Float.MAX_VALUE

        for (zone in zones.filter { it.enabled }) {
            val distToCenter = FloatArray(1).also {
                Location.distanceBetween(userLat, userLng, zone.latitude, zone.longitude, it)
            }[0]

            val distToBoundary = distToCenter - zone.radiusMeters

            if (distToBoundary <= 0f) {
                // Inside zone
                activeInsideZones.add(zone)
            }

            if (abs(distToBoundary) < abs(minBoundaryDist)) {
                minBoundaryDist = distToBoundary
                nearestZone = zone
            }

            // Check predictive trajectory
            if (predictedDistance > 2f) {
                val predictedDistToCenter = FloatArray(1).also {
                    Location.distanceBetween(
                        predictedCoords.first,
                        predictedCoords.second,
                        zone.latitude,
                        zone.longitude,
                        it
                    )
                }[0]

                val predictedDistToBoundary = predictedDistToCenter - zone.radiusMeters
                val isApproaching = predictedDistToBoundary < distToBoundary

                val etaSeconds = if (isApproaching && speed > 0.5f && distToBoundary > 0f) {
                    distToBoundary / speed
                } else null

                if (isApproaching || distToBoundary <= 0f) {
                    predictiveEvents.add(
                        PredictiveZoneEvent(
                            zone = zone,
                            distanceToBoundaryMeters = distToBoundary,
                            etaSeconds = etaSeconds,
                            isApproaching = isApproaching
                        )
                    )
                }
            }
        }

        return LiveCampusLocationState(
            location = location,
            latitude = userLat,
            longitude = userLng,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else 10f,
            speedMps = speed,
            bearingDegrees = bearing,
            nearestZone = nearestZone,
            distanceToNearestBoundaryMeters = minBoundaryDist,
            predictiveEvents = predictiveEvents,
            isInsideAnyZone = activeInsideZones.isNotEmpty(),
            activeInsideZones = activeInsideZones
        )
    }

    /**
     * Extrapolates future Lat/Lng using Great Circle forward destination formula.
     */
    private fun calculatePredictedCoordinate(
        lat: Double,
        lng: Double,
        distanceMeters: Float,
        bearingDegrees: Float
    ): Pair<Double, Double> {
        val earthRadius = 6371000.0 // meters
        val angularDistance = distanceMeters / earthRadius
        val bearingRad = Math.toRadians(bearingDegrees.toDouble())
        val latRad = Math.toRadians(lat)
        val lngRad = Math.toRadians(lng)

        val predLatRad = asin(
            sin(latRad) * cos(angularDistance) +
                    cos(latRad) * sin(angularDistance) * cos(bearingRad)
        )
        val predLngRad = lngRad + atan2(
            sin(bearingRad) * sin(angularDistance) * cos(latRad),
            cos(angularDistance) - sin(latRad) * sin(predLatRad)
        )

        return Pair(Math.toDegrees(predLatRad), Math.toDegrees(predLngRad))
    }
}
