package com.sh7411usa.shliachtzibbur.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Builds a one-line preview of [text] centred on the first case-insensitive
 * match of [query], with the match highlighted and `…` where the text is
 * clipped. The window slides so the match stays visible even deep in a long
 * message.
 */
object SearchSnippet {

    /** The clipped substring plus where the match sits inside it. */
    data class Window(val text: String, val matchStart: Int, val matchEnd: Int)

    fun window(text: String, query: String, radius: Int = 48): Window {
        val q = query.trim()
        if (q.isEmpty()) {
            val head = text.take(radius * 2)
            return Window(if (head.length < text.length) "$head…" else head, 0, 0)
        }
        val hit = text.indexOf(q, ignoreCase = true)
        if (hit < 0) {
            val head = text.take(radius * 2)
            return Window(if (head.length < text.length) "$head…" else head, 0, 0)
        }
        var start = (hit - radius).coerceAtLeast(0)
        var end = (hit + q.length + radius).coerceAtMost(text.length)
        // Snap to word boundaries where it's cheap, so we don't cut mid-word.
        while (start > 0 && !text[start - 1].isWhitespace() && hit - start < radius + 12) start--
        while (end < text.length && !text[end].isWhitespace() && end - (hit + q.length) < radius + 12) end++
        val core = text.substring(start, end)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return Window(prefix + core + suffix, prefix.length + (hit - start), prefix.length + (hit - start) + q.length)
    }

    fun highlighted(text: String, query: String, highlightColor: Color, radius: Int = 48): AnnotatedString {
        val w = window(text, query, radius)
        return buildAnnotatedString {
            append(w.text.substring(0, w.matchStart))
            withStyle(SpanStyle(background = highlightColor, fontWeight = FontWeight.Bold)) {
                append(w.text.substring(w.matchStart, w.matchEnd))
            }
            append(w.text.substring(w.matchEnd))
        }
    }
}
