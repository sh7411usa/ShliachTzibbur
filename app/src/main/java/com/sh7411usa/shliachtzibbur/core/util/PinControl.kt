package com.sh7411usa.shliachtzibbur.core.util

/**
 * Pin / unpin ride in-band as plain messages carrying a `$PIN:<seq>` /
 * `$UNPIN:<seq>` marker plus a human sentence (so non-Shliach-Tzibbur clients
 * read something and get the app link). Clients must verify the sender was a
 * group admin before honouring one.
 */
sealed interface PinControl {
    val targetSeq: Long

    data class Pin(override val targetSeq: Long) : PinControl
    data class Unpin(override val targetSeq: Long) : PinControl

    companion object {
        private const val GITHUB = "https://github.com/sh7411usa/ShliachTzibbur"
        private val MARKER = Regex("\\\$(PIN|UNPIN):(\\d{1,18})")

        fun parse(text: String): PinControl? {
            val m = MARKER.find(text) ?: return null
            val seq = m.groupValues[2].toLongOrNull() ?: return null
            return if (m.groupValues[1] == "PIN") Pin(seq) else Unpin(seq)
        }

        fun body(control: PinControl): String {
            val verb = if (control is Pin) "pinned" else "unpinned"
            return "📌 A message was $verb in Shliach Tzibbur. Get the app: $GITHUB " +
                "\$${if (control is Pin) "PIN" else "UNPIN"}:${control.targetSeq}"
        }
    }
}
