package com.sh7411usa.shliachtzibbur.core.net.ws

import com.sh7411usa.shliachtzibbur.core.model.Message
import com.sh7411usa.shliachtzibbur.core.net.NetJson
import com.sh7411usa.shliachtzibbur.core.net.dto.MessageDto
import com.sh7411usa.shliachtzibbur.core.net.dto.toDomain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Parsed server -> client WebSocket frame. */
sealed interface WsEvent {
    data class Hello(
        val userId: String,
        val deviceId: String,
        val heartbeatSeconds: Int,
        val maxFrameBytes: Int,
    ) : WsEvent

    data class Messages(
        val groupId: String,
        val messages: List<Message>,
        val hasMore: Boolean,
    ) : WsEvent

    /** A `group` frame. [payload] is passed through untyped; the exact shapes are not fully documented. */
    data class GroupChanged(
        val event: String,
        val groupId: String?,
        val payload: JsonObject,
    ) : WsEvent

    data object Pong : WsEvent

    data class ErrorFrame(val code: String, val detail: String?, val requiresUpgrade: Boolean) : WsEvent

    data class Unknown(val type: String) : WsEvent
}

/** Client -> server frames. */
object WsOutbound {
    fun ping(): String = buildJsonObject { put("type", "ping") }.toString()

    fun ack(groupId: String, seq: Long): String = buildJsonObject {
        put("type", "ack")
        put("groupId", groupId)
        put("seq", seq)
    }.toString()
}

object WsParser {

    fun parse(raw: String): WsEvent {
        val obj = runCatching { NetJson.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return WsEvent.Unknown("malformed")
        return when (val type = obj["type"]?.jsonPrimitive?.contentOrNull) {
            "hello" -> parseHello(obj)
            "messages" -> parseMessages(obj)
            "group" -> parseGroup(obj)
            "pong" -> WsEvent.Pong
            "error" -> parseError(obj)
            else -> WsEvent.Unknown(type ?: "null")
        }
    }

    private fun parseHello(obj: JsonObject): WsEvent {
        val limits = obj["limits"]?.jsonObject
        return WsEvent.Hello(
            userId = obj["userId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            deviceId = obj["deviceId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            heartbeatSeconds = limits?.get("heartbeatSeconds")?.jsonPrimitive?.intOrNull ?: 30,
            maxFrameBytes = limits?.get("maxFrameBytes")?.jsonPrimitive?.intOrNull ?: 16384,
        )
    }

    private fun parseMessages(obj: JsonObject): WsEvent {
        val groupId = obj["groupId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val messages = obj["messages"]?.jsonArray?.mapNotNull { element ->
            runCatching {
                NetJson.decodeFromJsonElement(MessageDto.serializer(), element).toDomain(groupId)
            }.getOrNull()
        }.orEmpty()
        val hasMore = obj["hasMore"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
        return WsEvent.Messages(groupId, messages, hasMore)
    }

    private fun parseGroup(obj: JsonObject): WsEvent {
        val event = obj["event"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val payload = obj["payload"]?.jsonObject ?: JsonObject(emptyMap())
        val groupId = payload["groupId"]?.jsonPrimitive?.contentOrNull
        return WsEvent.GroupChanged(event, groupId, payload)
    }

    private fun parseError(obj: JsonObject): WsEvent {
        val code = obj["code"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val detail = obj["detail"]?.jsonPrimitive?.contentOrNull
        val upgrade = code.contains("version", ignoreCase = true) || code.contains("update", ignoreCase = true)
        return WsEvent.ErrorFrame(code, detail, upgrade)
    }
}
