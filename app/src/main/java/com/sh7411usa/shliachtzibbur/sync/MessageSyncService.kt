package com.sh7411usa.shliachtzibbur.sync

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.sh7411usa.shliachtzibbur.ShliachTzibburApp
import com.sh7411usa.shliachtzibbur.core.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps a WebSocket connection open so messages arrive
 * promptly without Google Play Services. Optional — controlled by the
 * "keep sync running" setting. When disabled, [PendingSyncWorker] is the delivery
 * path instead.
 */
class MessageSyncService : LifecycleService() {

    private var sessionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val notifications = container().notificationHelper
        notifications.ensureChannels()
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (sessionJob?.isActive != true) {
            sessionJob = lifecycleScope.launch {
                container().sessionStore.session.collect { current ->
                    if (current == null) {
                        Log.d("Sync service stopping: signed out")
                        stopSelf()
                    }
                }
            }
            lifecycleScope.launch {
                runCatching { container().syncManager.runWebSocketSession() }
                    .onFailure { Log.w("Sync session ended: ${it.message}") }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun startForegroundCompat() {
        val notification = container().notificationHelper.foregroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NotificationHelper.FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun container() = (application as ShliachTzibburApp).container

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, MessageSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MessageSyncService::class.java))
        }
    }
}
