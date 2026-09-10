package com.sh7411usa.shliachtzibbur.ui.messages

import com.sh7411usa.shliachtzibbur.core.model.ConversationItem
import com.sh7411usa.shliachtzibbur.core.model.Message
import com.sh7411usa.shliachtzibbur.core.model.OutboxMessage
import com.sh7411usa.shliachtzibbur.core.model.ServiceMessage
import com.sh7411usa.shliachtzibbur.core.util.PinControl
import com.sh7411usa.shliachtzibbur.core.util.PollSpec
import com.sh7411usa.shliachtzibbur.core.util.PollState
import com.sh7411usa.shliachtzibbur.core.util.PollToken
import com.sh7411usa.shliachtzibbur.core.util.Polls
import com.sh7411usa.shliachtzibbur.core.util.Reactions

/** One emoji reaction shown on the message it targets. */
data class Reaction(
    val emoji: String,
    /** The reactor's display name; null while the send is still pending. */
    val reactor: String?,
    /** The reactor's user id, used to keep only their most recent reaction. */
    val reactorId: String?,
    /** Reaction-message seq; [Long.MAX_VALUE] while pending. */
    val seq: Long,
    val pending: Boolean,
)

/** The non-message tags the conversation can show. */
enum class ControlKind { EncOn, EncOff, KeyChanged, Pinned, Unpinned, PollEnded, EncryptionPending }

/** A row rendered by [com.sh7411usa.shliachtzibbur.ui.messages.MessagesScreen]. */
sealed interface ConvRow {
    val key: String
    val orderSeq: Long
    val orderTie: Int

    data class Msg(
        val item: ConversationItem.Delivered,
        val text: String,
        val sticker: Boolean,
        val poll: PollState?,
    ) : ConvRow {
        override val key get() = "d-${item.message.id}"
        override val orderSeq get() = item.message.seq
        override val orderTie get() = 0
    }

    data class Control(
        val kind: ControlKind,
        val actor: String?,
        val targetSeq: Long?,
        val atSeq: Long,
    ) : ConvRow {
        override val key get() = "c-$atSeq-$kind"
        override val orderSeq get() = atSeq
        override val orderTie get() = 0
    }

    data class Summary(val state: PollState) : ConvRow {
        override val key get() = "poll-summary-${state.pollSeq}"
        override val orderSeq get() = state.summaryAnchorSeq
        override val orderTie get() = 1
    }

    data class Pending(val outbox: OutboxMessage) : ConvRow {
        override val key get() = "p-${outbox.clientMessageId}"
        override val orderSeq get() = Long.MAX_VALUE
        override val orderTie get() = 2
    }
}

data class Derived(
    val rows: List<ConvRow>,
    val reactionsBySeq: Map<Long, List<Reaction>>,
    val pinnedSeq: Long?,
)

/**
 * Turns the raw (already decrypted) conversation into renderable rows: reactions
 * hang on their target, poll votes / pin-control / service messages become tags
 * or are dropped, a `$POLL:` message carries its aggregated [PollState], and a
 * closed poll gets a summary row at its close position.
 */
fun deriveConversation(
    items: List<ConversationItem>,
    selfId: String?,
    adminIds: Set<String>,
    nowMillis: Long,
): Derived {
    val delivered = items.filterIsInstance<ConversationItem.Delivered>()
    val pending = items.filterIsInstance<ConversationItem.Pending>()

    // A decrypted "view" of every delivered message so marker parsing sees the
    // real body even in an encrypted group.
    val view = delivered.map { d ->
        d to d.message.copy(
            body = d.message.displayName?.let { "$it: ${d.displayText}" } ?: d.displayText,
        )
    }
    val decrypted = view.map { it.second }

    val reactionsBySeq = collectReactions(view, pending)

    // Pins: replay admin-authored $PIN / $UNPIN, latest wins.
    var pinnedSeq: Long? = null
    decrypted.sortedBy { it.seq }.forEach { m ->
        val pc = PinControl.parse(m.text) ?: return@forEach
        if (m.senderId == null || m.senderId !in adminIds) return@forEach
        pinnedSeq = when (pc) {
            is PinControl.Pin -> pc.targetSeq
            is PinControl.Unpin -> if (pinnedSeq == pc.targetSeq) null else pinnedSeq
        }
    }

    // Poll aggregates keyed by the poll message seq.
    val polls = HashMap<Long, PollState>()
    view.forEach { (_, m) ->
        if (PollSpec.isPoll(m.text)) {
            Polls.aggregate(m, decrypted, selfId, nowMillis)?.let { polls[m.seq] = it }
        }
    }

    val rows = ArrayList<ConvRow>(view.size + pending.size + polls.size)
    view.forEach { (d, m) ->
        val text = m.text
        val vote = PollToken.parse(text)
        when {
            Reactions.targetOf(text) != null -> Unit
            ServiceMessage.parse(text) != null ->
                rows += ConvRow.Control(serviceKind(ServiceMessage.parse(text)!!), m.displayName, null, m.seq)
            PinControl.parse(text) != null -> {
                val pc = PinControl.parse(text)!!
                if (m.senderId != null && m.senderId in adminIds) {
                    val kind = if (pc is PinControl.Pin) ControlKind.Pinned else ControlKind.Unpinned
                    rows += ConvRow.Control(kind, m.displayName, pc.targetSeq, m.seq)
                }
            }
            vote?.end == true -> {
                val poll = polls[vote.pollSeq]
                if (poll != null && m.senderId != null && m.senderId == poll.authorId) {
                    rows += ConvRow.Control(ControlKind.PollEnded, m.displayName, vote.pollSeq, m.seq)
                }
            }
            vote != null -> Unit // numeric vote — hidden
            PollSpec.isPoll(text) -> rows += ConvRow.Msg(d, text, sticker = false, poll = polls[m.seq])
            else -> rows += ConvRow.Msg(d, text, sticker = Reactions.singleEmojiOrNull(text) != null, poll = null)
        }
    }
    polls.values.filter { it.ended }.forEach { rows += ConvRow.Summary(it) }
    pending.forEach { rows += ConvRow.Pending(it.outbox) }

    return Derived(
        rows = rows.sortedWith(compareBy({ it.orderSeq }, { it.orderTie })),
        reactionsBySeq = reactionsBySeq,
        pinnedSeq = pinnedSeq,
    )
}

private fun serviceKind(s: ServiceMessage): ControlKind = when (s) {
    ServiceMessage.EncryptionOn -> ControlKind.EncOn
    ServiceMessage.EncryptionOff -> ControlKind.EncOff
    ServiceMessage.KeyChanged -> ControlKind.KeyChanged
}

private fun collectReactions(
    view: List<Pair<ConversationItem.Delivered, Message>>,
    pending: List<ConversationItem.Pending>,
): Map<Long, List<Reaction>> {
    val byTarget = LinkedHashMap<Long, MutableList<Reaction>>()
    fun add(target: Long, r: Reaction) = byTarget.getOrPut(target) { mutableListOf() }.add(r)

    view.forEach { (_, m) ->
        val target = Reactions.targetOf(m.text)
        val emoji = Reactions.of(m.text)
        if (target != null && emoji != null) {
            add(target, Reaction(emoji, m.displayName, m.senderId, m.seq, pending = false))
        }
    }
    pending.forEach { p ->
        val target = Reactions.targetOf(p.outbox.text)
        val emoji = Reactions.of(p.outbox.text)
        if (target != null && emoji != null) {
            add(target, Reaction(emoji, null, null, Long.MAX_VALUE, pending = true))
        }
    }

    return byTarget.mapValues { (_, list) ->
        list.groupBy { it.reactorId ?: "pending:${it.emoji}" }
            .map { (_, perPerson) -> perPerson.maxByOrNull { it.seq }!! }
            .sortedBy { it.seq }
    }
}
