package com.sh7411usa.shliachtzibbur.sync

import com.sh7411usa.shliachtzibbur.core.model.Message
import com.sh7411usa.shliachtzibbur.core.model.ServiceMessage
import com.sh7411usa.shliachtzibbur.core.net.TzibburApi
import com.sh7411usa.shliachtzibbur.core.net.dto.PendingGroupDto
import com.sh7411usa.shliachtzibbur.core.net.dto.toDomain
import com.sh7411usa.shliachtzibbur.core.net.ws.TzibburWebSocket
import com.sh7411usa.shliachtzibbur.core.net.ws.WsEvent
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.core.util.Log
import com.sh7411usa.shliachtzibbur.data.local.dao.GroupDao
import com.sh7411usa.shliachtzibbur.data.prefs.SessionStore
import com.sh7411usa.shliachtzibbur.data.prefs.SettingsStore
import com.sh7411usa.shliachtzibbur.data.repo.GroupRepository
import com.sh7411usa.shliachtzibbur.data.repo.MessageRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

/**
 * The single place where incoming messages are turned into stored rows,
 * notifications, and delivery acknowledgements. Shared by [MessageSyncService]
 * (WebSocket) and [PendingSyncWorker] (REST poll).
 *
 * Order is always: **persist -> notify -> ack**.
 */
class SyncManager(
    private val api: TzibburApi,
    private val messageRepository: MessageRepository,
    private val groupRepository: GroupRepository,
    private val groupDao: GroupDao,
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore,
    private val notifications: NotificationHelper,
    private val webSocketFactory: () -> TzibburWebSocket,
) {
    /** Handle the full `GET /v1/pending` response. */
    suspend fun applyPending(groups: List<PendingGroupDto>) {
        for (pending in groups) {
            val messages = pending.messages.map { it.toDomain(pending.groupId) }
            ingest(pending.groupId, messages)
        }
    }

    /** Handle a WebSocket `messages` frame. */
    suspend fun applyWebSocketMessages(groupId: String, messages: List<Message>) {
        ingest(groupId, messages)
    }

    private suspend fun ingest(groupId: String, messages: List<Message>) {
        if (messages.isEmpty()) return

        // 1. Persist.
        messageRepository.applyIncoming(groupId, messages)

        val maxSeq = messages.maxOf { it.seq }

        // 2. Notify (unless muted, self-authored, or already on screen).
        val settings = settingsStore.settings.first()
        val selfId = sessionStore.session.first()?.userId
        val shouldNotify = settings.notificationsEnabled &&
            groupId !in settings.mutedGroupIds &&
            !AppForegroundState.suppressesNotificationFor(groupId)

        if (shouldNotify) {
            val groupName = groupDao.find(groupId)?.name ?: return ackQuietly(groupId, maxSeq)
            val notifiable = messages.filter { ServiceMessage.parse(it.text) == null }
            if (notifiable.isNotEmpty()) {
                notifications.notifyNewMessages(
                    groupId,
                    groupName,
                    messageRepository.decryptedForDisplay(groupId, notifiable),
                    selfId,
                )
            }
        }

        // 3. Ack (advances the device's deliveredSeq; enables live pushes).
        ackQuietly(groupId, maxSeq)
    }

    private suspend fun ackQuietly(groupId: String, seq: Long) {
        when (val result = messageRepository.ackDelivery(groupId, seq)) {
            is ApiResult.Failure -> Log.w("ack failed for $groupId@$seq: ${result.error.type}")
            is ApiResult.Success -> Unit
        }
    }

    /**
     * Run a WebSocket session until the caller's scope is cancelled. Emits are
     * dispatched on the same scope.
     */
    suspend fun runWebSocketSession() = coroutineScope {
        val socket = webSocketFactory()
        val collectorReady = CompletableDeferred<Unit>()
        launch {
            socket.events
                .onSubscription { collectorReady.complete(Unit) }
                .collect { event ->
                    when (event) {
                        is WsEvent.Hello ->
                            Log.i("ws hello user=${event.userId} device=${event.deviceId}")

                        is WsEvent.Messages -> {
                            applyWebSocketMessages(event.groupId, event.messages)
                            val maxSeq = event.messages.maxOfOrNull { it.seq }
                            if (maxSeq != null) {
                                // Ack on the socket so the server starts live pushes.
                                socket.ack(event.groupId, maxSeq)
                                Log.i("ws acked ${event.groupId}@$maxSeq (${event.messages.size} msgs)")
                            }
                        }

                        is WsEvent.GroupChanged -> groupRepository.onExternalGroupChange(event.groupId)
                        is WsEvent.ErrorFrame -> Log.w("ws error ${event.code}: ${event.detail}")
                        WsEvent.Pong -> Unit
                        is WsEvent.Unknown -> Log.d("ws unknown frame ${event.type}")
                    }
                }
        }
        // Don't start the socket until the collector is attached, or the
        // hello/backlog frames (replay = 0) are lost.
        collectorReady.await()
        socket.run()
    }

    /** One-shot REST sync, used by the worker and on app start. */
    suspend fun pollOnce(): ApiResult<Unit> {
        return when (val result = com.sh7411usa.shliachtzibbur.core.result.apiCatching { api.getPending() }) {
            is ApiResult.Success -> {
                applyPending(result.value)
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result
        }
    }
}
