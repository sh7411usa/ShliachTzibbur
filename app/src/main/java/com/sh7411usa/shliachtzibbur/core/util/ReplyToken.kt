package com.sh7411usa.shliachtzibbur.core.util

/**
 * The Tzibbur service has no native "reply" concept, so a reply is encoded as a
 * marker at the very start of the message text: `RE:<seq> ` followed by the
 * actual message, e.g. `RE:118 I disagree`.
 *
 * Clients that understand the marker (this app) render a quoted preview of the
 * referenced message and hide the marker; other clients simply see the prefix,
 * which is still human-readable.
 */
object ReplyToken {

    /**
     * `RE:` + up to 18 digits (fits in a [Long]) + optionally whitespace and the
     * message body. Anchored to the whole string; the body may span lines.
     */
    private val PATTERN = Regex("""^RE:(\d{1,18})(?:[ \t]+([\s\S]*))?$""")

    /** A message text split into the [seq] it replies to and the remaining [body]. */
    data class Reply(val seq: Long, val body: String)

    /** Parses a leading reply marker off [text], or returns null if there is none. */
    fun parse(text: String): Reply? {
        val match = PATTERN.matchEntire(text) ?: return null
        val seq = match.groupValues[1].toLongOrNull() ?: return null
        return Reply(seq, match.groupValues[2])
    }

    /** Prepends the reply marker for [seq] to [body]. */
    fun format(seq: Long, body: String): String = "RE:$seq $body"

    /** [text] with any leading reply marker removed (used for previews). */
    fun strip(text: String): String = parse(text)?.body ?: text
}
