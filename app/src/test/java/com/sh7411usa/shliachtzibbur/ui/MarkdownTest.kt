package com.sh7411usa.shliachtzibbur.ui

import com.sh7411usa.shliachtzibbur.ui.common.MdBlock
import com.sh7411usa.shliachtzibbur.ui.common.parseBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    @Test
    fun `headings, paragraphs, lists and rules`() {
        val md = """
            # Privacy Policy

            We respect your privacy. This document explains
            what we collect.

            ## Data we collect

            - Your phone number
            - Your display name

            ---

            1. First point
            2. Second point
        """.trimIndent()

        val blocks = parseBlocks(md)

        assertEquals(MdBlock.Heading(1, "Privacy Policy"), blocks[0])
        assertEquals(
            MdBlock.Paragraph("We respect your privacy. This document explains what we collect."),
            blocks[1],
        )
        assertEquals(MdBlock.Heading(2, "Data we collect"), blocks[2])
        assertEquals(MdBlock.ListItem("•", "Your phone number"), blocks[3])
        assertEquals(MdBlock.ListItem("•", "Your display name"), blocks[4])
        assertEquals(MdBlock.Rule, blocks[5])
        assertEquals(MdBlock.ListItem("1.", "First point"), blocks[6])
        assertEquals(MdBlock.ListItem("2.", "Second point"), blocks[7])
    }

    @Test
    fun `plain text with no markup is one paragraph`() {
        val blocks = parseBlocks("Just some plain terms of service text.")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MdBlock.Paragraph)
    }
}
