package com.smartsilentzone.work

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZoneWorkScheduler @Inject constructor(@ApplicationContext ctx: Context) {
    private val wm = WorkManager.getInstance(ctx)

    companion object {
        const val HYSTERESIS_SECONDS = 30L
        const val KEY_ZONE_ID = "zone_id"
        const val KEY_RECONCILE = "reconcile"
        private fun exitName(id: String) = "exit_confirm_$id"
        private const val EVALUATE = "evaluate_zones"
        private const val RECONCILE = "reconcile_presence"
    }

    /** Evaluate now. Expedited so it runs promptly even in the background. */
    fun enqueueEvaluate() {
        val req = OneTimeWorkRequestBuilder<ZoneEvaluationWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        wm.enqueueUniqueWork(EVALUATE, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    /** Rebuild presence from a fresh fix (after reboot), then evaluate. */
    fun enqueueReconcile() {
        val req = OneTimeWorkRequestBuilder<ZoneEvaluationWorker>()
            .setInputData(workDataOf(KEY_RECONCILE to true))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        wm.enqueueUniqueWork(RECONCILE, ExistingWorkPolicy.REPLACE, req)
    }

    /** Hysteresis for leaving: confirm 30s later; ENTER/DWELL cancels. */
    fun scheduleExitConfirm(zoneId: String) {
        val req = OneTimeWorkRequestBuilder<ZoneEvaluationWorker>()
            .setInitialDelay(HYSTERESIS_SECONDS, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_ZONE_ID to zoneId))
            .build()
        wm.enqueueUniqueWork(exitName(zoneId), ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelExitConfirm(zoneId: String) = wm.cancelUniqueWork(exitName(zoneId))
}
