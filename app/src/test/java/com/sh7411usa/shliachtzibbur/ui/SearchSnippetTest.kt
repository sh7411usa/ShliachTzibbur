package com.sh7411usa.shliachtzibbur.ui

import com.sh7411usa.shliachtzibbur.ui.common.SearchSnippet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSnippetTest {

    @Test
    fun `match near the start keeps the head`() {
        val w = SearchSnippet.window("hello world this is a long message", "hello", radius = 8)
        assertEquals(0, w.matchStart)
        assertEquals("hello", w.text.substring(w.matchStart, w.matchEnd))
        assertTrue(w.text.startsWith("hello"))
        assertTrue(w.text.endsWith("…"))
    }

    @Test
    fun `match deep in the text slides the window`() {
        val text = "a".repeat(200) + " needle " + "b".repeat(200)
        val w = SearchSnippet.window(text, "needle", radius = 20)
        assertEquals("needle", w.text.substring(w.matchStart, w.matchEnd))
        assertTrue(w.text.startsWith("…"))
        assertTrue(w.text.endsWith("…"))
        assertTrue(w.text.length < 80)
    }

    @Test
    fun `no match returns a head snippet`() {
        val w = SearchSnippet.window("some text without it", "xyz", radius = 4)
        assertEquals(0, w.matchStart)
        assertEquals(0, w.matchEnd)
    }
}
