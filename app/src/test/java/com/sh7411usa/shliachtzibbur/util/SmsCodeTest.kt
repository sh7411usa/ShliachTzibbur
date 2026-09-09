package com.sh7411usa.shliachtzibbur.util

import com.sh7411usa.shliachtzibbur.core.util.SmsCodeReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsCodeTest {

    @Test
    fun `extracts the exact Tzibbur format`() {
        assertEquals("123456", SmsCodeReceiver.extractCode("Your tzibbur code: 123456"))
    }

    @Test
    fun `prefers digits right after the word code`() {
        assertEquals(
            "998877",
            SmsCodeReceiver.extractCode("Tzibbur: do not share. code 998877. ref 12"),
        )
        assertEquals(
            "482913",
            SmsCodeReceiver.extractCode("Tzibbur 2026: your code is 482913. Valid 10 min."),
        )
    }

    @Test
    fun `finds the code inside a multi-line inbox body`() {
        val body = "Promo: 50 off!\nYour tzibbur code: 445566\nReply STOP to opt out"
        assertEquals("445566", SmsCodeReceiver.extractCode(body))
    }

    @Test
    fun `falls back to a digit run when the word code is absent`() {
        assertEquals("5821", SmsCodeReceiver.extractCode("PIN 5821"))
    }

    @Test
    fun `returns null when there is nothing code-like`() {
        assertNull(SmsCodeReceiver.extractCode("Welcome to Tzibbur!"))
        assertNull(SmsCodeReceiver.extractCode("call 12"))
    }
}
