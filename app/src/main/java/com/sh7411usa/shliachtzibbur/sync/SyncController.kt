package com.sh7411usa.shliachtzibbur.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sh7411usa.shliachtzibbur.core.util.Log
import java.util.concurrent.TimeUnit

/**
 * Central control for background delivery. Reconciles the running state of the
 * periodic poll and the foreground service against the current session and the
 * user's "keep sync running" preference.
 */
class SyncController(private val appContext: Context) {

    private val workManager get() = WorkManager.getInstance(appContext)

    /** Call whenever the session or the sync-service setting changes. */
    fun apply(signedIn: Boolean, serviceEnabled: Boolean) {
        if (signedIn) {
            schedulePeriodicPoll()
            if (serviceEnabled) MessageSyncService.start(appContext) else MessageSyncService.stop(appContext)
        } else {
            cancelAll()
            MessageSyncService.stop(appContext)
        }
    }

    fun requestOutboxFlush() {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(OutboxWorker.UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun requestImmediatePoll() {
        val request = OneTimeWorkRequestBuilder<PendingSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(
            "${PendingSyncWorker.UNIQUE_NAME}-now",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun schedulePeriodicPoll() {
        val request = PeriodicWorkRequestBuilder<PendingSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PendingSyncWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        Log.d("Periodic pending-sync scheduled")
    }

    private fun cancelAll() {
        workManager.cancelUniqueWork(PendingSyncWorker.UNIQUE_NAME)
        workManager.cancelUniqueWork(OutboxWorker.UNIQUE_NAME)
    }
}
