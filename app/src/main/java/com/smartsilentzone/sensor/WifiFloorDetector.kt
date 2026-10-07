package com.smartsilentzone.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.smartsilentzone.data.db.WifiApDao
import com.smartsilentzone.data.db.WifiApEntity
import com.smartsilentzone.ringer.RingerModeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class WifiFloorState(
    val bssid: String? = null,
    val rssiDbm: Int? = null,
    val matchedZone: WifiApEntity? = null,
    val isScanning: Boolean = false,
    val hasPermission: Boolean = false,
    val lastResolvedAt: Long = 0L
)

@Singleton
class WifiFloorDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wifiApDao: WifiApDao,
    private val ringerModeManager: RingerModeManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _floorState = MutableStateFlow(WifiFloorState())
    val floorState: StateFlow<WifiFloorState> = _floorState.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun hasRequiredPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val nearbyWifi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return fineLocation && nearbyWifi
    }

    @SuppressLint("MissingPermission")
    fun startDetection() {
        val hasPerms = hasRequiredPermissions()
        _floorState.value = _floorState.value.copy(hasPermission = hasPerms, isScanning = true)

        if (!hasPerms) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+): ConnectivityManager NetworkCallback with FLAG_INCLUDE_LOCATION_INFO
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback(
                FLAG_INCLUDE_LOCATION_INFO
            ) {
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    val wifiInfo = networkCapabilities.transportInfo as? WifiInfo
                    val bssid = wifiInfo?.bssid
                    val rssi = wifiInfo?.rssi
                    handleBssidDetected(bssid, rssi)
                }

                override fun onLost(network: Network) {
                    handleBssidDetected(null, null)
                }
            }

            try {
                connectivityManager.registerNetworkCallback(request, networkCallback!!)
            } catch (e: Exception) {
                // Fallback if needed
                pollLegacyWifi()
            }
        } else {
            // Android 8.1 - 11 fallback using WifiManager
            pollLegacyWifi()
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    fun pollLegacyWifi() {
        if (!hasRequiredPermissions()) return
        try {
            val info = wifiManager.connectionInfo
            val bssid = info?.bssid
            val rssi = info?.rssi
            handleBssidDetected(bssid, rssi)
        } catch (_: Exception) {}
    }

    private fun handleBssidDetected(rawBssid: String?, rssi: Int?) {
        val normalizedBssid = rawBssid?.lowercase()?.takeIf { it != "02:00:00:00:00:00" && it.isNotBlank() }
        scope.launch {
            if (normalizedBssid == null) {
                _floorState.value = _floorState.value.copy(
                    bssid = null,
                    rssiDbm = null,
                    matchedZone = null,
                    lastResolvedAt = System.currentTimeMillis()
                )
                return@launch
            }

            val match = wifiApDao.getByBssid(normalizedBssid)
            _floorState.value = _floorState.value.copy(
                bssid = normalizedBssid,
                rssiDbm = rssi,
                matchedZone = match,
                lastResolvedAt = System.currentTimeMillis()
            )

            // Auto-apply zone's silent policy if matched
            match?.let { ap ->
                when (ap.silentPolicy.uppercase()) {
                    "SILENT" -> ringerModeManager.setSilent()
                    "VIBRATE" -> ringerModeManager.setVibrate()
                    "NORMAL" -> ringerModeManager.setNormal()
                }
            }
        }
    }

    suspend fun saveCurrentApSurvey(zoneName: String, floor: String, silentPolicy: String): Boolean {
        val currentBssid = _floorState.value.bssid ?: return false
        val entity = WifiApEntity(
            bssid = currentBssid.lowercase(),
            zoneName = zoneName,
            floor = floor,
            silentPolicy = silentPolicy
        )
        wifiApDao.insertOrUpdate(entity)
        handleBssidDetected(currentBssid)
        return true
    }

    fun stopDetection() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
            networkCallback = null
        }
        _floorState.value = _floorState.value.copy(isScanning = false)
    }
}
