package com.sh7411usa.shliachtzibbur.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sh7411usa.shliachtzibbur.MainActivity
import com.sh7411usa.shliachtzibbur.R
import com.sh7411usa.shliachtzibbur.core.model.Message

/**
 * Owns notification channels and posts per-group message notifications.
 *
 * Channels:
 *  - [CHANNEL_MESSAGES] — default importance, one notification id per group,
 *    messages stacked with [NotificationCompat.MessagingStyle].
 *  - [CHANNEL_SYNC] — low importance, the ongoing foreground-service notice.
 */
class NotificationHelper(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES,
                context.getString(R.string.notif_channel_messages),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_messages_desc) },
        )
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SYNC,
                context.getString(R.string.notif_channel_sync),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notif_channel_sync_desc) },
        )
    }

    /** The persistent notification shown while [MessageSyncService] runs. */
    fun foregroundNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setContentTitle(context.getString(R.string.notif_sync_title))
            .setContentText(context.getString(R.string.notif_sync_text))
            .setSmallIcon(R.drawable.ic_stat_tzibbur)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent(null))
            .build()

    fun notifyNewMessages(
        groupId: String,
        groupName: String,
        messages: List<Message>,
        selfUserId: String?,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !manager.areNotificationsEnabled()
        ) {
            return
        }
        val relevant = messages.filter { it.senderId != selfUserId }
        if (relevant.isEmpty()) return

        val style = NotificationCompat.MessagingStyle(
            androidx.core.app.Person.Builder().setName(context.getString(R.string.notif_you)).build(),
        ).setConversationTitle(groupName).setGroupConversation(true)

        relevant.takeLast(6).forEach { message ->
            val sender = androidx.core.app.Person.Builder()
                .setName(message.displayName ?: context.getString(R.string.notif_someone))
                .build()
            style.addMessage(message.text, timestampMillis(message), sender)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_tzibbur)
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(groupId))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        runCatching { manager.notify(groupId.hashCode(), notification) }
    }

    fun clearGroup(groupId: String) {
        manager.cancel(groupId.hashCode())
    }

    private fun timestampMillis(message: Message): Long =
        com.sh7411usa.shliachtzibbur.core.util.Timestamps.epochMillis(message.createdAt)

    private fun openAppIntent(groupId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (groupId != null) putExtra(MainActivity.EXTRA_GROUP_ID, groupId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getActivity(context, groupId?.hashCode() ?: 0, intent, flags)
    }

    companion object {
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_SYNC = "sync"
        const val FOREGROUND_NOTIFICATION_ID = 42
    }
}
