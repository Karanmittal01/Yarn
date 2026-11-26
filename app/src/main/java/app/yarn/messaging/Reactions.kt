package app.yarn.messaging

import android.content.Context
import app.yarn.R
import app.yarn.data.db.MessageDao
import app.yarn.data.db.ReactionText
import app.yarn.intelligence.SmsReactions

/**
 * Turns reactions that arrived as text ("Loved “see you at 8”") into emoji on the messages they're
 * about. The texts stay in the phone's message store untouched; Yarn only hides a reaction from the
 * chat once it has found the message it belongs to, so nothing is ever lost.
 */
object Reactions {
    /** One person's reaction on a message; [from] is the sender's address, or null for you. */
    data class Shown(val emoji: String, val from: String?)

    data class State(
        /** Reactions on each message, by message id, in the order they were added. */
        val byMessage: Map<Long, List<Shown>> = emptyMap(),
        /** Reaction texts that were matched to their message and needn't show as bubbles. */
        val hidden: Set<Long> = emptySet(),
    )

    suspend fun resolve(dao: MessageDao, conversationId: Long, texts: List<ReactionText>): State {
        if (texts.isEmpty()) return State()
        val parsed = texts.mapNotNull { t -> SmsReactions.parse(t.body)?.let { t to it } }
        if (parsed.isEmpty()) return State()
        val reactionIds = parsed.map { it.first.id }.toSet()
        val current = LinkedHashMap<Pair<Long, String>, String>()
        val hidden = HashSet<Long>()
        for ((text, reaction) in parsed) {
            val target = findTarget(dao, conversationId, text, reaction, reactionIds) ?: continue
            hidden += text.id
            val key = target to if (text.outgoing) ME else text.address
            if (reaction.removed) {
                // Whoever takes a reaction back removes theirs, whichever it was.
                current.remove(key)
            } else {
                // One reaction per person per message: a new one replaces the old.
                current.remove(key)
                current[key] = reaction.emoji
            }
        }
        val byMessage = LinkedHashMap<Long, MutableList<Shown>>()
        for ((key, emoji) in current) byMessage.getOrPut(key.first) { ArrayList() } += Shown(emoji, key.second.takeIf { it != ME })
        return State(byMessage, hidden)
    }

    private suspend fun findTarget(dao: MessageDao, conversationId: Long, text: ReactionText, reaction: SmsReactions.Reaction, reactionIds: Set<Long>): Long? {
        val quote = reaction.quote ?: return dao.latestAttachmentBefore(conversationId, text.date)?.id
        val prefix = SmsReactions.searchPrefix(quote)
        if (prefix.isEmpty()) return null
        val like = prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return dao.reactionTargets(conversationId, text.date, like)
            .firstOrNull { it.id != text.id && it.id !in reactionIds && SmsReactions.matches(quote, it.body) }?.id
    }

    /** "Reacted ❤️ to “see you at 8”", for the inbox and notifications; null when [body] isn't a reaction. */
    fun describe(context: Context, body: String): String? {
        val r = SmsReactions.parse(body) ?: return null
        val quote = r.quote?.let { if (it.length > 60) it.take(57).trimEnd() + "…" else it }
        return when {
            r.removed && quote != null -> context.getString(R.string.reaction_removed_from, r.emoji, quote)
            r.removed -> context.getString(R.string.reaction_removed_from_attachment, r.emoji)
            quote != null -> context.getString(R.string.reacted_to, r.emoji, quote)
            else -> context.getString(R.string.reacted_to_attachment, r.emoji)
        }
    }

    private const val ME = "\u0000me"
}
