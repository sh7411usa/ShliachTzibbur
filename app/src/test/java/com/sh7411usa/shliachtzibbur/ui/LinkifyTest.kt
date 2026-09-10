package com.sh7411usa.shliachtzibbur.ui

import com.sh7411usa.shliachtzibbur.ui.common.Linkify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkifyTest {

    private fun kinds(text: String) = Linkify.spans(text).map { it.kind }
    private fun texts(text: String) = Linkify.spans(text).map { it.text(text) }

    @Test
    fun `detects url email and phone`() {
        val t = "see https://tzibbur.me or mail me at a.b@example.com or call +1 (555) 010-0123"
        assertEquals(
            listOf(Linkify.Kind.URL, Linkify.Kind.EMAIL, Linkify.Kind.PHONE),
            kinds(t),
        )
    }

    @Test
    fun `strips trailing punctuation from a url`() {
        val t = "go to https://tzibbur.me/help."
        assertEquals(listOf("https://tzibbur.me/help"), texts(t))
    }

    @Test
    fun `builds tel and mailto uris`() {
        val t = "call 555-123-4567 or write to x@y.co"
        val spans = Linkify.spans(t)
        assertEquals("tel:5551234567", spans.first { it.kind == Linkify.Kind.PHONE }.uri(t))
        assertEquals("mailto:x@y.co", spans.first { it.kind == Linkify.Kind.EMAIL }.uri(t))
    }

    @Test
    fun `www gets https prefix`() {
        val t = "visit www.tzibbur.me now"
        assertEquals("https://www.tzibbur.me", Linkify.spans(t).first().uri(t))
    }

    @Test
    fun `short number runs are not phones`() {
        assertTrue(Linkify.spans("room 42 at 3pm").none { it.kind == Linkify.Kind.PHONE })
    }

    @Test
    fun `geo uri is treated as a link`() {
        val t = "here: geo:37.4,-122.0?q=37.4,-122.0"
        val span = Linkify.spans(t).single()
        assertEquals(Linkify.Kind.URL, span.kind)
        assertTrue(span.uri(t).startsWith("geo:"))
    }
}
