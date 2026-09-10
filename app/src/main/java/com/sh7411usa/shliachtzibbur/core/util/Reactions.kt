package com.sh7411usa.shliachtzibbur.core.util

/**
 * Emoji reactions ride on top of [ReplyToken]: a reaction is a reply whose body
 * is nothing but emoji (e.g. `RE:118 👍`). Clients that understand it
 * render the emoji as a badge on the referenced message instead of a new bubble;
 * other clients just see a short reply.
 */
object Reactions {

    /** The five one-tap reactions offered directly in the message menu. */
    val QUICK: List<String> = listOf(
        "👍", // 👍 thumbs up
        "❤️", // ❤️ red heart
        "😂", // 😂 tears of joy
        "😮", // 😮 open mouth
        "😢", // 😢 crying
    )

    /** A wider palette for the "more" chooser (kept small and dependency-free). */
    val PALETTE: List<String> = listOf(
        "👍", "👎", "❤️", "🔥", "🎉", "👏",
        "🙏", "💯", "✅", "❌", "❗", "❓",
        "😂", "🤣", "😊", "😍", "😘", "😉",
        "😎", "🤔", "😢", "😭", "😡", "😠",
        "😱", "😨", "😔", "🙄", "😐", "😬",
        "🙌", "👌", "🤝", "💪", "🙋", "👀",
        "🎈", "🎂", "☕", "🍻", "🍕", "🚀",
        "⭐", "🌟", "🌈", "⚡", "💡", "📣",
    )

    /**
     * The emoji carried by [messageText] if it is a reaction — a [ReplyToken]
     * reply whose body is only emoji — otherwise null.
     */
    fun of(messageText: String): String? =
        ReplyToken.parse(messageText)?.body?.takeIf { isEmojiOnly(it) }?.trim()

    /** The message sequence [messageText] reacts to, or null if it isn't a reaction. */
    fun targetOf(messageText: String): Long? {
        val reply = ReplyToken.parse(messageText) ?: return null
        return if (isEmojiOnly(reply.body)) reply.seq else null
    }

    private const val ZWJ = 0x200D
    private const val VS15 = 0xFE0E
    private const val VS16 = 0xFE0F
    private const val KEYCAP = 0x20E3
    private val SKIN_TONES = 0x1F3FB..0x1F3FF

    /**
     * True when [text] (after trimming) is made up solely of emoji — one to a
     * handful of emoji code points plus the joiners / modifiers that bind them,
     * and nothing else. Letters, digits and ordinary punctuation disqualify it,
     * so a real one-word reply ("ok", ":)") is never mistaken for a reaction.
     */
    fun isEmojiOnly(text: String): Boolean {
        val s = text.trim()
        if (s.isEmpty()) return false
        var index = 0
        var emojiCount = 0
        while (index < s.length) {
            val cp = s.codePointAt(index)
            index += Character.charCount(cp)
            when {
                isEmojiScalar(cp) -> {
                    emojiCount++
                    if (emojiCount > 12) return false
                }
                cp == ZWJ || cp == VS15 || cp == VS16 || cp == KEYCAP || cp in SKIN_TONES -> Unit
                else -> return false
            }
        }
        return emojiCount > 0
    }

    /**
     * The emoji if [text] is a short emoji-only message (1–3 emoji, no other
     * characters) — rendered as a large sticker instead of a bubble — else null.
     */
    fun singleEmojiOrNull(text: String): String? {
        val s = text.trim()
        if (s.isEmpty() || !isEmojiOnly(s)) return null
        var index = 0
        var scalars = 0
        while (index < s.length) {
            val cp = s.codePointAt(index)
            index += Character.charCount(cp)
            if (isEmojiScalar(cp)) scalars++
        }
        return if (scalars in 1..3) s else null
    }

    /** Deliberately generous: reaction text only ever comes from our own picker. */
    private fun isEmojiScalar(cp: Int): Boolean =
        cp in 0x1F000..0x1FFFF ||   // supplementary symbol / pictograph planes
            cp in 0x2600..0x27BF || // Misc Symbols + Dingbats
            cp in 0x2B00..0x2BFF || // Misc Symbols and Arrows
            cp in 0x2190..0x21FF || // Arrows
            cp in 0x2300..0x23FF || // Misc Technical (⌚ ⏰ …)
            cp in 0x25A0..0x25FF || // Geometric Shapes
            cp in 0x2900..0x297F || // Supplemental Arrows-B
            cp == 0x00A9 || cp == 0x00AE || cp == 0x2122 || cp == 0x2139 ||
            cp == 0x203C || cp == 0x2049 || cp == 0x3030 || cp == 0x303D ||
            cp == 0x3297 || cp == 0x3299
}
