package com.sh7411usa.shliachtzibbur.ui

import com.sh7411usa.shliachtzibbur.core.util.Reactions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionsTest {

    @Test
    fun `every quick reaction is recognised as emoji-only`() {
        Reactions.QUICK.forEach { assertTrue(it, Reactions.isEmojiOnly(it)) }
    }

    @Test
    fun `every palette entry is recognised as emoji-only`() {
        Reactions.PALETTE.forEach { assertTrue(it, Reactions.isEmojiOnly(it)) }
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertTrue(Reactions.isEmojiOnly("  👍 "))
    }

    @Test
    fun `a couple of stacked emoji still count`() {
        assertTrue(Reactions.isEmojiOnly("👍👍❤️"))
    }

    @Test
    fun `ordinary short replies are not reactions`() {
        listOf("ok", "lol", ":)", "no", "👍 thanks", "RE:5", "100", "-").forEach {
            assertFalse(it, Reactions.isEmojiOnly(it))
        }
    }

    @Test
    fun `empty text is not a reaction`() {
        assertFalse(Reactions.isEmojiOnly(""))
        assertFalse(Reactions.isEmojiOnly("   "))
    }

    @Test
    fun `singleEmojiOrNull accepts one to three emoji, rejects text and long runs`() {
        assertEquals("👍", Reactions.singleEmojiOrNull("👍"))
        assertEquals("🎉🎉", Reactions.singleEmojiOrNull(" 🎉🎉 "))
        assertNull(Reactions.singleEmojiOrNull("👍👍👍👍"))
        assertNull(Reactions.singleEmojiOrNull("hi"))
        assertNull(Reactions.singleEmojiOrNull("👍 nice"))
    }
}
