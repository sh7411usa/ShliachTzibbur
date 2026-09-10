package com.sh7411usa.shliachtzibbur.util

import com.sh7411usa.shliachtzibbur.core.model.Message
import com.sh7411usa.shliachtzibbur.core.util.PinControl
import com.sh7411usa.shliachtzibbur.core.util.PollSpec
import com.sh7411usa.shliachtzibbur.core.util.PollToken
import com.sh7411usa.shliachtzibbur.core.util.Polls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun msg(seq: Long, sender: String?, text: String, createdAt: String? = "2025-01-01T00:00:00Z") =
    Message("m$seq", "g", seq, sender, if (sender == null) text else "$sender: $text", null, createdAt)

class PollSpecTest {

    @Test
    fun `parses question and numbered options`() {
        val poll = PollSpec.parse("\$POLL: Dinner?\n1. Pizza\n2. Sushi\n3) Tacos\nVote in the app")
        assertEquals("Dinner?", poll!!.question)
        assertEquals(listOf("Pizza", "Sushi", "Tacos"), poll.options)
    }

    @Test
    fun `needs at least two options`() {
        assertNull(PollSpec.parse("\$POLL: One?\n1. Only"))
        assertNull(PollSpec.parse("not a poll"))
    }

    @Test
    fun `format round-trips`() {
        val body = PollSpec.format("Colour?", listOf("Red", "Blue"))
        assertTrue(PollSpec.isPoll(body))
        val poll = PollSpec.parse(body)!!
        assertEquals("Colour?", poll.question)
        assertEquals(listOf("Red", "Blue"), poll.options)
        assertTrue(body.contains("github.com/sh7411usa/ShliachTzibbur"))
    }
}

class PollTokenTest {

    @Test
    fun `parses numeric votes and END`() {
        assertEquals(PollToken.Vote(114, 2, false), PollToken.parse("RE:114:2"))
        assertEquals(PollToken.Vote(114, null, true), PollToken.parse("RE:114:END"))
        assertNull(PollToken.parse("RE:114 reply text"))
        assertNull(PollToken.parse("RE:114:2 extra"))
    }

    @Test
    fun `format matches parse`() {
        assertEquals("RE:9:3", PollToken.formatVote(9, 3))
        assertEquals("RE:9:END", PollToken.formatEnd(9))
    }
}

class PollsAggregateTest {

    private val poll = msg(10, "author", "\$POLL: Q?\n1. A\n2. B\n3. C")
    private val startMs = 1_735_689_600_000L // 2025-01-01T00:00:00Z (matches msg() createdAt)
    private val now = startMs + 24 * 3600 * 1000L // one day later — before the +1 week auto-end

    private fun state(thread: List<Message>, self: String?, nowMs: Long = now) =
        Polls.aggregate(poll, thread + poll, self, nowMs)!!

    @Test
    fun `results hidden until the viewer votes`() {
        val s = state(listOf(msg(11, "bob", "RE:10:1")), self = "carol")
        assertFalse(s.showResults)
        assertEquals(1, s.totalVotes)
    }

    @Test
    fun `own vote reveals results and only the first counts`() {
        val s = state(listOf(msg(11, "carol", "RE:10:1"), msg(12, "carol", "RE:10:2")), self = "carol")
        assertTrue(s.showResults)
        assertEquals(1, s.myChoice)
        assertEquals(listOf(1, 0, 0), s.counts)
        assertEquals(1, s.totalVotes)
    }

    @Test
    fun `author END closes the poll and later votes are ignored`() {
        val s = state(
            listOf(msg(11, "bob", "RE:10:1"), msg(12, "author", "RE:10:END"), msg(13, "dave", "RE:10:2")),
            self = "zoe",
        )
        assertTrue(s.ended)
        assertTrue(s.endedManually)
        assertTrue(s.showResults)
        assertEquals(listOf(1, 0, 0), s.counts)
        assertEquals(12L, s.summaryAnchorSeq)
    }

    @Test
    fun `END from a non-author is ignored`() {
        val s = state(listOf(msg(11, "bob", "RE:10:END")), self = "zoe")
        assertFalse(s.ended)
    }

    @Test
    fun `poll auto-ends a week later`() {
        val weekLater = startMs + 8L * 24 * 3600 * 1000
        val s = Polls.aggregate(poll, listOf(poll, msg(11, "bob", "RE:10:1")), "zoe", weekLater)!!
        assertTrue(s.ended)
        assertFalse(s.endedManually)
    }
}

class PinControlTest {

    @Test
    fun `parses pin and unpin markers`() {
        assertEquals(PinControl.Pin(116), PinControl.parse("some text \$PIN:116"))
        assertEquals(PinControl.Unpin(116), PinControl.parse("\$UNPIN:116 trailing"))
        assertNull(PinControl.parse("no marker here"))
    }

    @Test
    fun `body carries the marker and the app link, under the limit`() {
        val body = PinControl.body(PinControl.Pin(42))
        assertEquals(PinControl.Pin(42), PinControl.parse(body))
        assertTrue(body.length <= 1000)
        assertTrue(body.contains("github.com/sh7411usa/ShliachTzibbur"))
    }
}
