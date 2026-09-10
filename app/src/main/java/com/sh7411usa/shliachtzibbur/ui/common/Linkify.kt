package com.sh7411usa.shliachtzibbur.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/**
 * Recognises URLs, e-mail addresses and phone numbers in plain text so they can
 * be made tappable. Kept free of `android.util.Patterns` so it is unit-testable
 * on the JVM.
 */
object Linkify {

    enum class Kind { URL, EMAIL, PHONE }

    data class Span(val start: Int, val end: Int, val kind: Kind) {
        fun text(source: String): String = source.substring(start, end)

        /** The `ACTION_VIEW` URI for this span. */
        fun uri(source: String): String = when (kind) {
            Kind.EMAIL -> "mailto:" + text(source)
            Kind.PHONE -> "tel:" + text(source).filter { it == '+' || it.isDigit() }
            Kind.URL -> text(source).let { if (it.startsWith("www.", ignoreCase = true)) "https://$it" else it }
        }
    }

    private val URL = Regex("(?i)\\b(?:https?://|geo:|www\\.)[^\\s<>()]+")
    private val EMAIL = Regex("\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\\b")
    private val PHONE = Regex("(?<![\\w.])\\+?\\d[\\d()\\-. ]{5,}\\d(?!\\w)")
    private val TRAILING = setOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'')

    /** Non-overlapping spans, left to right. */
    fun spans(text: String): List<Span> {
        val raw = buildList {
            EMAIL.findAll(text).forEach { add(Span(it.range.first, it.range.last + 1, Kind.EMAIL)) }
            URL.findAll(text).forEach { m ->
                var end = m.range.last + 1
                while (end > m.range.first + 1 && text[end - 1] in TRAILING) end--
                add(Span(m.range.first, end, Kind.URL))
            }
            PHONE.findAll(text).forEach { m ->
                if (m.value.count(Char::isDigit) >= 7) {
                    add(Span(m.range.first, m.range.last + 1, Kind.PHONE))
                }
            }
        }.sortedBy { it.start }

        val result = mutableListOf<Span>()
        var covered = 0
        for (span in raw) {
            if (span.start >= covered) {
                result += span
                covered = span.end
            }
        }
        return result
    }
}

/**
 * Appends [text] with any URLs / e-mails / phone numbers wrapped in a tappable
 * [LinkAnnotation.Url]. Links and phones are underlined; links and e-mails use
 * [linkColor].
 */
fun AnnotatedString.Builder.appendLinkified(text: String, linkColor: Color) {
    var cursor = 0
    for (span in Linkify.spans(text)) {
        if (span.start > cursor) append(text.substring(cursor, span.start))
        val style = when (span.kind) {
            Linkify.Kind.URL -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
            Linkify.Kind.EMAIL -> SpanStyle(color = linkColor)
            Linkify.Kind.PHONE -> SpanStyle(textDecoration = TextDecoration.Underline)
        }
        withLink(LinkAnnotation.Url(span.uri(text), TextLinkStyles(style))) {
            append(span.text(text))
        }
        cursor = span.end
    }
    if (cursor < text.length) append(text.substring(cursor))
}
