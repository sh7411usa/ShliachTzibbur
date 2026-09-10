package com.sh7411usa.shliachtzibbur.model

import com.sh7411usa.shliachtzibbur.core.model.ServiceMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceMessageTest {

    @Test
    fun `body and parse round-trip for every verb`() {
        ServiceMessage.entries.forEach { kind ->
            assertEquals(kind, ServiceMessage.parse(ServiceMessage.body(kind)))
        }
    }

    @Test
    fun `plain text is not a service message`() {
        assertNull(ServiceMessage.parse("let's turn on encryption tomorrow"))
        assertNull(ServiceMessage.parse("#ShliachTzibbur is a great app"))
    }

    @Test
    fun `bodies fit the limit and point at the repo`() {
        ServiceMessage.entries.forEach { kind ->
            val body = ServiceMessage.body(kind)
            assertTrue("under 1000 chars", body.length <= 1000)
            assertTrue(body.contains("github.com/sh7411usa/ShliachTzibbur"))
        }
    }
}
