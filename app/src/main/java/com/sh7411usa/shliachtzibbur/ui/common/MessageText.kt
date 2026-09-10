package com.sh7411usa.shliachtzibbur.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Renders a chat message body. Auto-links URLs / e-mails / phone numbers always;
 * interprets Markdown when [markdown] is true (otherwise the raw text, newlines
 * preserved, is shown as one block).
 */
@Composable
fun MessageText(
    text: String,
    markdown: Boolean,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    val blocks = remember(text, markdown) {
        if (markdown) parseBlocks(text) else listOf(MdBlock.Paragraph(text))
    }
    Column(modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = renderInline(block.text),
                    color = color,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleMedium
                        2 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.bodyLarge
                    },
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )

                is MdBlock.Paragraph -> Text(
                    text = renderInline(block.text),
                    color = color,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 1.dp),
                )

                is MdBlock.ListItem -> Row(Modifier.padding(vertical = 1.dp)) {
                    Text(block.marker, color = color, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = renderInline(block.text),
                        color = color,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 6.dp))
            }
        }
    }
}
