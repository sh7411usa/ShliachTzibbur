package com.sh7411usa.shliachtzibbur.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sh7411usa.shliachtzibbur.ShliachTzibburApp
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import kotlinx.coroutines.flow.first

/** Flushes queued outgoing messages, e.g. after connectivity returns. */
class OutboxWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as ShliachTzibburApp).container
        if (container.sessionStore.session.first() == null) return Result.success()

        return when (val result = container.messageRepository.flushOutbox()) {
            is ApiResult.Success -> Result.success()
            is ApiResult.Failure -> if (result.error.isAuthError) Result.success() else Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "outbox-flush"
    }
}
