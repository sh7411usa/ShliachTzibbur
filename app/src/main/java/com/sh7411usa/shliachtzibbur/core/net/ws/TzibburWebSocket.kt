package com.sh7411usa.shliachtzibbur.core.net.ws

import com.sh7411usa.shliachtzibbur.core.net.TokenProvider
import com.sh7411usa.shliachtzibbur.core.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

/**
 * A resilient client for `wss://api.tzibbur.me/v1/ws`.
 *
 * Call [run] from a long-lived coroutine (the sync service, or a screen scope).
 * It connects, emits every server frame on [events], keeps the connection warm
 * with `ping` frames, and reconnects with exponential backoff until the coroutine
 * is cancelled. Send delivery acknowledgements with [ack].
 */
class TzibburWebSocket(
    private val wsUrl: String,
    baseClient: OkHttpClient,
    private val tokens: TokenProvider,
) {
    private val client: OkHttpClient = baseClient.newBuilder()
        .pingInterval(0, TimeUnit.SECONDS) // application-level ping instead
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private val _events = MutableSharedFlow<WsEvent>(
        replay = 0,
        extraBufferCapacity = 128,
    )
    val events: SharedFlow<WsEvent> = _events

    private data class Ack(val groupId: String, val seq: Long)

    private val ackQueue = Channel<Ack>(Channel.UNLIMITED)

    /** Queue a delivery acknowledgement; delivered on the current connection or the next one. */
    fun ack(groupId: String, seq: Long) {
        ackQueue.trySend(Ack(groupId, seq))
    }

    suspend fun run() {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            val token = tokens.currentToken()
            if (token.isNullOrBlank()) {
                delay(2_000)
                continue
            }
            try {
                connectOnce(token)
                attempt = 0 // clean disconnect -> reset backoff
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("WebSocket session ended: ${e.message}")
                attempt++
            }
            if (!currentCoroutineContext().isActive) break
            val backoff = min(30_000.0, 1_000.0 * 2.0.pow(min(attempt, 5))).toLong()
            delay(backoff)
        }
    }

    private suspend fun connectOnce(token: String) = coroutineScope {
        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Bearer $token")
            .build()

        val closed = CompletableDeferred<Unit>()
        val incoming = Channel<String>(Channel.UNLIMITED)

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                incoming.trySend(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket closing: $code $reason")
                webSocket.close(NORMAL_CLOSE, null)
                if (code == TOO_MANY_CONNECTIONS) {
                    closed.completeExceptionally(IllegalStateException("too many connections ($code)"))
                } else {
                    closed.complete(Unit)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!closed.isCompleted) closed.completeExceptionally(t)
            }
        }

        val socket = client.newWebSocket(request, listener)

        // Pump queued acks to the socket.
        val ackJob = launch {
            while (isActive) {
                val ack = ackQueue.receive()
                if (!socket.send(WsOutbound.ack(ack.groupId, ack.seq))) {
                    // Socket gone; re-queue and stop pumping for this connection.
                    ackQueue.trySend(ack)
                    break
                }
            }
        }

        // Application-level heartbeat.
        val pingJob = launch {
            while (isActive) {
                delay(25_000)
                if (!socket.send(WsOutbound.ping())) break
            }
        }

        try {
            while (isActive) {
                val done = select<Boolean> {
                    incoming.onReceive { text ->
                        _events.emit(WsParser.parse(text))
                        false
                    }
                    closed.onAwait { true }
                }
                if (done) break
            }
        } finally {
            ackJob.cancel()
            pingJob.cancel()
            runCatching { socket.close(NORMAL_CLOSE, null) }
            runCatching { socket.cancel() }
            incoming.close()
        }
        // An abnormal close completes [closed] exceptionally; awaiting it here
        // rethrows so [run] applies backoff. A normal close just returns.
        closed.await()
    }

    private companion object {
        const val NORMAL_CLOSE = 1000
        const val TOO_MANY_CONNECTIONS = 4029
    }
}
