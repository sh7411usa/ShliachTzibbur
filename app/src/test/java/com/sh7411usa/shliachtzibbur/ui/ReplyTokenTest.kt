package com.sh7411usa.shliachtzibbur.ui

import com.sh7411usa.shliachtzibbur.core.util.ReplyToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplyTokenTest {

    @Test
    fun `parses a reply marker and body`() {
        val parsed = ReplyToken.parse("RE:118 I disagree")
        assertEquals(ReplyToken.Reply(118L, "I disagree"), parsed)
    }

    @Test
    fun `keeps a multi-line body intact`() {
        val parsed = ReplyToken.parse("RE:7 first line\nsecond line")
        assertEquals(ReplyToken.Reply(7L, "first line\nsecond line"), parsed)
    }

    @Test
    fun `marker with no body parses to an empty body`() {
        assertEquals(ReplyToken.Reply(42L, ""), ReplyToken.parse("RE:42"))
    }

    @Test
    fun `plain text is not a reply`() {
        assertNull(ReplyToken.parse("just a normal message"))
        assertNull(ReplyToken.parse("RE: not numbered"))
        assertNull(ReplyToken.parse("see RE:5 in the middle"))
    }

    @Test
    fun `overlong number is rejected rather than overflowing`() {
        assertNull(ReplyToken.parse("RE:99999999999999999999 hi"))
    }

    @Test
    fun `format then parse round-trips`() {
        val text = ReplyToken.format(256L, "sounds good")
        assertEquals("RE:256 sounds good", text)
        assertEquals(ReplyToken.Reply(256L, "sounds good"), ReplyToken.parse(text))
    }

    @Test
    fun `strip removes the marker and leaves other text untouched`() {
        assertEquals("hello", ReplyToken.strip("RE:1 hello"))
        assertEquals("no marker here", ReplyToken.strip("no marker here"))
    }
}
