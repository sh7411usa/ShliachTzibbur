package com.sh7411usa.shliachtzibbur.model

import com.sh7411usa.shliachtzibbur.core.model.Message
import com.sh7411usa.shliachtzibbur.core.model.Role
import com.sh7411usa.shliachtzibbur.core.model.WhoCanPost
import com.sh7411usa.shliachtzibbur.core.util.PhoneNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {

    @Test
    fun `message splits server display-name prefix`() {
        val m = Message("id", "g", 1, "u", "Reb Yankel: gut shabbos", null, null)
        assertEquals("Reb Yankel", m.displayName)
        assertEquals("gut shabbos", m.text)
    }

    @Test
    fun `message without prefix keeps whole body`() {
        val m = Message("id", "g", 1, "u", "no colon here", null, null)
        assertNull(m.displayName)
        assertEquals("no colon here", m.text)
    }

    @Test
    fun `unknown enum tokens fall back safely`() {
        assertEquals(Role.MEMBER, Role.fromWire("something-new"))
        assertEquals(WhoCanPost.EVERYONE, WhoCanPost.fromWire(null))
    }

    @Test
    fun `phone assembly and validation`() {
        assertEquals("+15550100123", PhoneNumbers.toE164("+1", "555 010 0123"))
        assertEquals("+15550100123", PhoneNumbers.toE164("1", "(555) 010-0123"))
        assertEquals("+447911123456", PhoneNumbers.toE164("+99", "+44 7911 123456"))
        assertTrue(PhoneNumbers.looksValid("+15550100123"))
        assertFalse(PhoneNumbers.looksValid("5550100123"))
        assertFalse(PhoneNumbers.looksValid("+12"))
    }
}
