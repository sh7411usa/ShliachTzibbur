package com.sh7411usa.shliachtzibbur.net

import com.sh7411usa.shliachtzibbur.core.net.ws.WsEvent
import com.sh7411usa.shliachtzibbur.core.net.ws.WsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WsParserTest {

    @Test
    fun `parses hello with limits`() {
        val event = WsParser.parse(
            """{ "type": "hello", "protocolVersion": 1, "userId": "u1", "deviceId": "d1",
                "limits": { "heartbeatSeconds": 30, "maxFrameBytes": 16384 } }""",
        )
        event as WsEvent.Hello
        assertEquals("u1", event.userId)
        assertEquals(30, event.heartbeatSeconds)
    }

    @Test
    fun `parses messages frame and maps message bodies`() {
        val event = WsParser.parse(
            """{ "type": "messages", "groupId": "g1", "hasMore": false,
                "messages": [ { "id": "m1", "seq": 5, "senderId": "u2", "body": "Alice: hi" } ] }""",
        )
        event as WsEvent.Messages
        assertEquals("g1", event.groupId)
        assertEquals(1, event.messages.size)
        assertEquals("g1", event.messages.first().groupId)
        assertEquals("Alice", event.messages.first().displayName)
        assertEquals("hi", event.messages.first().text)
    }

    @Test
    fun `unknown type does not throw`() {
        assertTrue(WsParser.parse("""{ "type": "totally-new" }""") is WsEvent.Unknown)
        assertTrue(WsParser.parse("not json") is WsEvent.Unknown)
    }

    @Test
    fun `error frame flags upgrade codes`() {
        val event = WsParser.parse("""{ "type": "error", "code": "client_update_required" }""")
        event as WsEvent.ErrorFrame
        assertTrue(event.requiresUpgrade)
    }
}
