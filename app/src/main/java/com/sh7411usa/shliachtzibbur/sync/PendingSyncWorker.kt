package com.sh7411usa.shliachtzibbur.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sh7411usa.shliachtzibbur.ShliachTzibburApp
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.core.util.Log
import kotlinx.coroutines.flow.first

/**
 * Periodic fallback delivery: polls `GET /v1/pending`, stores new messages, posts
 * notifications, and acks. Scheduled whenever the user is signed in; it is the
 * only delivery path when the foreground sync service is disabled.
 */
class PendingSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as ShliachTzibburApp).container
        if (container.sessionStore.session.first() == null) return Result.success()

        container.notificationHelper.ensureChannels()
        return when (val result = container.syncManager.pollOnce()) {
            is ApiResult.Success -> Result.success()
            is ApiResult.Failure -> {
                if (result.error.isAuthError) {
                    Log.w("Pending sync unauthorized; giving up")
                    Result.success()
                } else {
                    Log.w("Pending sync failed: ${result.error.type}")
                    Result.retry()
                }
            }
        }
    }

    companion object {
        const val UNIQUE_NAME = "pending-sync"
    }
}
