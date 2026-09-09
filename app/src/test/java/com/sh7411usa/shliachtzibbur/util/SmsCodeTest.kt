package com.sh7411usa.shliachtzibbur.util

import com.sh7411usa.shliachtzibbur.core.util.SmsCodeReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsCodeTest {

    @Test
    fun `extracts a plain six digit code`() {
        assertEquals("123456", SmsCodeReceiver.extractCode("Your Tzibbur code is 123456"))
    }

    @Test
    fun `prefers the six digit run over other numbers`() {
        assertEquals("482913", SmsCodeReceiver.extractCode("Tzibbur 2026: your code is 482913. Valid 10 min."))
    }

    @Test
    fun `falls back to the last digit run when no six digit code`() {
        assertEquals("5821", SmsCodeReceiver.extractCode("Code: 5821"))
    }

    @Test
    fun `returns null when there is nothing code-like`() {
        assertNull(SmsCodeReceiver.extractCode("Welcome to Tzibbur!"))
        assertNull(SmsCodeReceiver.extractCode("call 12"))
    }
}
