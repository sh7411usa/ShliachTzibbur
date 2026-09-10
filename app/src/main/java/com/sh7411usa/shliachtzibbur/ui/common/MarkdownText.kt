package com.sh7411usa.shliachtzibbur.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Minimal CommonMark renderer for the subset used by the legal documents:
 * ATX headings, `**bold**`, `*italic*` / `_italic_`, `` `code` ``, `[text](url)`
 * links, `-`/`*`/`1.` lists, `---` rules, and blank-line paragraphs. Deliberately
 * dependency-free — a full Markdown library isn't warranted for static policy text.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    Column(modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = renderInline(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )

                is MdBlock.Paragraph -> Text(
                    text = renderInline(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                is MdBlock.ListItem -> Row(Modifier.padding(vertical = 2.dp)) {
                    Text(block.marker, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = renderInline(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 12.dp))
            }
        }
    }
}

internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ListItem(val marker: String, val text: String) : MdBlock
    data object Rule : MdBlock
}

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET = Regex("^\\s*[-*+]\\s+(.*)$")
private val NUMBERED = Regex("^\\s*(\\d+)[.)]\\s+(.*)$")
private val RULE = Regex("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$")

internal fun parseBlocks(md: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) out += MdBlock.Paragraph(paragraph.trim().toString())
        paragraph.setLength(0)
    }

    for (rawLine in md.replace("\r\n", "\n").split("\n")) {
        val line = rawLine.trimEnd()
        when {
            line.isBlank() -> flushParagraph()
            RULE.matches(line) -> {
                flushParagraph()
                out += MdBlock.Rule
            }
            HEADING.matchEntire(line) != null -> {
                flushParagraph()
                val m = HEADING.matchEntire(line)!!
                out += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2])
            }
            BULLET.matchEntire(line) != null -> {
                flushParagraph()
                out += MdBlock.ListItem("•", BULLET.matchEntire(line)!!.groupValues[1])
            }
            NUMBERED.matchEntire(line) != null -> {
                flushParagraph()
                val m = NUMBERED.matchEntire(line)!!
                out += MdBlock.ListItem("${m.groupValues[1]}.", m.groupValues[2])
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line.trim())
            }
        }
    }
    flushParagraph()
    return out
}

private val INLINE = Regex(
    "`([^`]+)`" +                       // code
        "|\\*\\*([^*]+)\\*\\*" +        // bold
        "|(?<![*\\w])[*_]([^*_]+)[*_]" + // italic
        "|\\[([^\\]]+)]\\(([^)]+)\\)",   // link
)

/**
 * Markdown inline spans (`**bold**`, `*italic*`, `` `code` ``, `[t](u)`) plus
 * automatic linkification of bare URLs / e-mails / phone numbers.
 */
@Composable
internal fun renderInline(text: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        var cursor = 0
        for (match in INLINE.findAll(text)) {
            if (match.range.first > cursor) appendLinkified(text.substring(cursor, match.range.first), linkColor)
            val (code, bold, italic, linkText, linkUrl) = match.destructured
            when {
                code.isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }
                bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
                italic.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
                linkText.isNotEmpty() -> withLink(LinkAnnotation.Url(linkUrl)) {
                    withStyle(SpanStyle(color = linkColor)) { append(linkText) }
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) appendLinkified(text.substring(cursor), linkColor)
    }
}
