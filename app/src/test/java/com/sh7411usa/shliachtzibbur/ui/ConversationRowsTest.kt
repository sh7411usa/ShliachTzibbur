package com.sh7411usa.shliachtzibbur.ui

import com.sh7411usa.shliachtzibbur.core.model.ConversationItem
import com.sh7411usa.shliachtzibbur.core.model.Message
import com.sh7411usa.shliachtzibbur.core.model.ServiceMessage
import com.sh7411usa.shliachtzibbur.ui.messages.ControlKind
import com.sh7411usa.shliachtzibbur.ui.messages.ConvRow
import com.sh7411usa.shliachtzibbur.ui.messages.deriveConversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRowsTest {

    private fun delivered(seq: Long, sender: String?, text: String, createdAt: String? = "2025-01-01T00:00:00Z") =
        ConversationItem.Delivered(
            Message("m$seq", "g", seq, sender, if (sender == null) text else "$sender: $text", null, createdAt),
        )

    private fun derive(items: List<ConversationItem>, self: String? = "me", admins: Set<String> = setOf("admin")) =
        deriveConversation(items, self, admins, nowMillis = 1_735_776_000_000L)

    @Test
    fun `plain messages become bubble rows, reactions are pulled out`() {
        val d = derive(
            listOf(
                delivered(1, "bob", "hello"),
                delivered(2, "carol", "RE:1 👍"),
            ),
        )
        assertEquals(1, d.rows.size)
        assertTrue(d.rows.single() is ConvRow.Msg)
        assertEquals(1, d.reactionsBySeq[1L]?.size)
    }

    @Test
    fun `a single emoji message is a sticker`() {
        val d = derive(listOf(delivered(1, "bob", "🎉")))
        assertTrue((d.rows.single() as ConvRow.Msg).sticker)
    }

    @Test
    fun `poll produces a card and, once ended, a summary at the close seq`() {
        val d = derive(
            listOf(
                delivered(5, "author", "\$POLL: Q?\n1. A\n2. B"),
                delivered(6, "bob", "RE:5:1"),
                delivered(7, "author", "RE:5:END"),
                delivered(8, "carol", "later message"),
            ),
        )
        val card = d.rows.filterIsInstance<ConvRow.Msg>().first { it.poll != null }
        assertEquals(5L, card.item.message.seq)
        assertTrue(card.poll!!.ended)
        val summary = d.rows.filterIsInstance<ConvRow.Summary>().single()
        assertEquals(7L, summary.orderSeq)
        // The vote message is hidden; the END shows as a tag.
        assertNull(d.rows.filterIsInstance<ConvRow.Msg>().firstOrNull { it.item.message.seq == 6L })
        assertTrue(d.rows.any { it is ConvRow.Control && it.kind == ControlKind.PollEnded })
    }

    @Test
    fun `encryption service tags show only from an admin`() {
        val fromMember = derive(
            listOf(delivered(1, "bob", ServiceMessage.body(ServiceMessage.EncryptionOn))),
        )
        assertTrue(fromMember.rows.none { it is ConvRow.Control })

        val fromAdmin = derive(
            listOf(delivered(1, "admin", ServiceMessage.body(ServiceMessage.EncryptionOn))),
        )
        assertTrue(fromAdmin.rows.any { it is ConvRow.Control && it.kind == ControlKind.EncOn })
    }

    @Test
    fun `pins are honoured only from admins and latest wins`() {
        val fromMember = derive(listOf(delivered(1, "bob", "hi"), delivered(2, "bob", "pin \$PIN:1")))
        assertNull(fromMember.pinnedSeq)

        val unpinned = derive(
            listOf(
                delivered(1, "bob", "hi"),
                delivered(2, "admin", "\$PIN:1"),
                delivered(3, "admin", "\$UNPIN:1"),
            ),
        )
        assertNull(unpinned.pinnedSeq)

        val pinned = derive(listOf(delivered(1, "bob", "hi"), delivered(2, "admin", "\$PIN:1")))
        assertEquals(1L, pinned.pinnedSeq)
        assertNotNull(pinned.rows.firstOrNull { it is ConvRow.Control && it.kind == ControlKind.Pinned })
    }
}
