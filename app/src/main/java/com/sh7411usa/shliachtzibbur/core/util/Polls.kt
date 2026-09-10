package com.sh7411usa.shliachtzibbur.core.util

import com.sh7411usa.shliachtzibbur.core.model.Message

/**
 * Polls ride on plain messages, so non-Shliach-Tzibbur clients still read them:
 *
 *  - the poll itself is a message whose body starts with `$POLL:` — the question
 *    on the first line, then one numbered option per line.
 *  - a vote is a message whose whole body is `RE:<pollSeq>:<n>` (choose option n)
 *    or `RE:<pollSeq>:END` (the author closes the poll).
 *
 * Shliach Tzibbur renders an interactive card; the raw text is the fallback.
 */
object PollSpec {

    private const val PREFIX = "\$POLL:"
    private const val GITHUB = "https://github.com/sh7411usa/ShliachTzibbur"
    private val OPTION = Regex("^\\s*\\d{1,3}[.)]\\s*(.+)$")

    /** A parsed poll body: [question] plus 2+ [options], plug/blank lines ignored. */
    data class Poll(val question: String, val options: List<String>)

    fun isPoll(text: String): Boolean = text.trimStart().startsWith(PREFIX)

    fun parse(text: String): Poll? {
        if (!isPoll(text)) return null
        val lines = text.trim().removePrefix(PREFIX).trim().split("\n")
        val question = lines.firstOrNull()?.trim().orEmpty()
        if (question.isEmpty()) return null
        val options = lines.drop(1).mapNotNull { line -> OPTION.matchEntire(line.trim())?.groupValues?.get(1)?.trim() }
            .filter { it.isNotEmpty() }
        return if (options.size >= 2) Poll(question, options) else null
    }

    /** Builds the poll body. Caller must keep the result within the message limit. */
    fun format(question: String, options: List<String>): String = buildString {
        append(PREFIX).append(' ').append(question.trim())
        options.forEachIndexed { i, opt -> append('\n').append(i + 1).append(". ").append(opt.trim()) }
        append("\nVote in Shliach Tzibbur: ").append(GITHUB)
    }
}

/** Parser/formatter for poll-vote messages. */
object PollToken {

    private val PATTERN = Regex("^RE:(\\d{1,18}):(\\d{1,3}|END)$")

    /** [choice] is 1-based; [end] means the author closed the poll. */
    data class Vote(val pollSeq: Long, val choice: Int?, val end: Boolean)

    fun parse(text: String): Vote? {
        val m = PATTERN.matchEntire(text.trim()) ?: return null
        val seq = m.groupValues[1].toLongOrNull() ?: return null
        val raw = m.groupValues[2]
        return if (raw == "END") Vote(seq, null, end = true) else Vote(seq, raw.toInt(), end = false)
    }

    fun formatVote(pollSeq: Long, choice: Int): String = "RE:$pollSeq:$choice"

    fun formatEnd(pollSeq: Long): String = "RE:$pollSeq:END"
}

/** Aggregated live state of a poll, derived from the message log. */
data class PollState(
    val pollSeq: Long,
    val question: String,
    val options: List<String>,
    val authorId: String?,
    /** Votes per option, index 0 = option 1. */
    val counts: List<Int>,
    val totalVotes: Int,
    /** The current user's 1-based choice, or null if they haven't voted. */
    val myChoice: Int?,
    val ended: Boolean,
    val endedManually: Boolean,
    /** Seq where the results summary belongs in the feed. */
    val summaryAnchorSeq: Long,
) {
    /** Results stay hidden until the user votes or the poll closes. */
    val showResults: Boolean get() = ended || myChoice != null
}

object Polls {

    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * @param poll the `$POLL:` message
     * @param thread every delivered message in the group (any order)
     */
    fun aggregate(poll: Message, thread: List<Message>, selfId: String?, nowMillis: Long): PollState? {
        val spec = PollSpec.parse(poll.text) ?: return null
        val startMs = Timestamps.epochMillisOrNull(poll.createdAt) ?: nowMillis
        val autoEndMs = startMs + WEEK_MS

        val endMsg = thread
            .asSequence()
            .filter { it.seq > poll.seq }
            .sortedBy { it.seq }
            .firstOrNull { m ->
                PollToken.parse(m.text)?.let { it.pollSeq == poll.seq && it.end } == true &&
                    m.senderId != null && m.senderId == poll.senderId
            }
        val endBySeq = endMsg?.seq
        val ended = endBySeq != null || nowMillis >= autoEndMs
        val cutoff = endBySeq ?: Long.MAX_VALUE

        val firstVote = LinkedHashMap<String, Int>()
        thread.sortedBy { it.seq }.forEach { m ->
            val v = PollToken.parse(m.text) ?: return@forEach
            if (v.pollSeq != poll.seq || v.choice == null) return@forEach
            if (m.seq <= poll.seq || m.seq >= cutoff) return@forEach
            val uid = m.senderId ?: return@forEach
            if (uid !in firstVote && v.choice in 1..spec.options.size) firstVote[uid] = v.choice
        }

        val counts = IntArray(spec.options.size)
        firstVote.values.forEach { counts[it - 1]++ }

        val anchor = when {
            endBySeq != null -> endBySeq
            ended -> thread.filter { (Timestamps.epochMillisOrNull(it.createdAt) ?: 0) > autoEndMs }
                .minByOrNull { it.seq }?.seq
                ?: (thread.maxOfOrNull { it.seq } ?: poll.seq)
            else -> poll.seq
        }

        return PollState(
            pollSeq = poll.seq,
            question = spec.question,
            options = spec.options,
            authorId = poll.senderId,
            counts = counts.toList(),
            totalVotes = firstVote.size,
            myChoice = selfId?.let { firstVote[it] },
            ended = ended,
            endedManually = endBySeq != null,
            summaryAnchorSeq = anchor,
        )
    }
}
