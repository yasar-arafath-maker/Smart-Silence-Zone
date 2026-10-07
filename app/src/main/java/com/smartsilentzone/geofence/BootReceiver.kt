package com.smartsilentzone.geofence

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.smartsilentzone.domain.ZoneRepository
import com.smartsilentzone.work.ZoneWorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Geofences do not survive a reboot or an app update. Re-register them (fast, fits the
 * receiver window) and hand the slower location check to WorkManager.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var registrar: GeofenceRegistrar
    @Inject lateinit var zoneRepository: ZoneRepository
    @Inject lateinit var scheduler: ZoneWorkScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val fine = has(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val bg = Build.VERSION.SDK_INT < 29 || has(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        if (!fine || !bg) {
            Log.i(TAG, "Location permissions missing; nothing to restore")
            return
        }

        val pending = goAsync()
        scope.launch {
            try {
                val ok = registrar.register(zoneRepository.getZones())
                Log.i(TAG, "Geofences re-registered: $ok")
                scheduler.enqueueReconcile()
            } catch (t: Throwable) {
                Log.e(TAG, "Restore failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private fun has(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    private companion object { const val TAG = "BootReceiver" }
}
