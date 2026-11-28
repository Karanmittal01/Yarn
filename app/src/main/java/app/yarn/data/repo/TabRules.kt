package app.yarn.data.repo

import app.yarn.data.prefs.CustomTab
import app.yarn.intelligence.Category
import app.yarn.intelligence.group

/**
 * Which tab each received message belongs to, so that, All aside, no message shows in two tabs.
 *
 * A message goes to the first of the user's own tabs, in the order they sit in the row, that wants
 * it; otherwise to its built-in tab (Personal, OTP, Updates, Offers). Making a tab takes its messages
 * out of the built-in tabs, and removing it gives them back: nothing is re-sorted or stored, the
 * tabs simply read the messages this way.
 *
 * Conditions are SQL on a received message `m` in chat `k`.
 */
object TabRules {
    /** Messages in [categories]; a chat the user moved to a category counts there as a whole. */
    fun inCategories(categories: Set<Category>): String {
        val names = sqlList(categories)
        return "((k.categoryLocked = 0 AND m.category IN ($names)) OR (k.categoryLocked = 1 AND k.category IN ($names)))"
    }

    /** The messages [tab] wants, before any earlier tab has taken its share. */
    fun wants(tab: CustomTab): String {
        tab.kind?.let { k -> return inCategories(Category.entries.filter { it.group == k }.toSet()) }
        if (tab.chats.isNotEmpty()) return "m.conversationId IN (${tab.chats.joinToString(",")})"
        val words = tab.words.map { it.trim() }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return "0"
        return words.joinToString(" OR ", "(", ")") { "m.body LIKE '${like(it)}' ESCAPE '\\'" }
    }

    /** What the tab showing [filter] lists, given the user's own [tabs] in row order. */
    fun condition(filter: InboxFilter, tabs: List<CustomTab>): String {
        val mine = filter.custom
        val (base, before) = if (mine != null) {
            val at = tabs.indexOfFirst { it.id == mine.id }
            wants(mine) to (if (at > 0) tabs.take(at) else emptyList())
        } else {
            inCategories(filter.categories) to tabs
        }
        return base + before.joinToString("") { " AND NOT " + wants(it) }
    }

    /** SQL giving each tab's [app.yarn.data.db.TabCount] (built-in tabs by name, the user's own by id); null for no tabs. */
    fun countsQuery(groups: List<InboxGroup>, tabs: List<CustomTab>): String? {
        val parts = groups.map { it.name to condition(it.filter, tabs) } + tabs.map { it.id to condition(it.filter, tabs) }
        if (parts.isEmpty()) return null
        return parts.joinToString(" UNION ALL ") { (key, cond) ->
            "SELECT '${key.replace("'", "''")}' AS tab, COUNT(DISTINCT m.conversationId) AS chats, " +
                "COUNT(DISTINCT CASE WHEN m.read = 0 THEN m.conversationId END) AS unread " +
                "FROM messages m JOIN conversations k ON k.id = m.conversationId " +
                "WHERE m.outgoing = 0 AND k.archived = 0 AND k.spam = 0 AND k.blocked = 0 AND $cond"
        }
    }

    /** Chats a tab takes whole, even before anything is received in them. */
    fun pickedChats(tabs: List<CustomTab>): List<Long> = tabs.flatMap { it.chats }.distinct()

    /** Categories that tabs of one kind (Bank & payments, Orders, Travel) take out of the built-in tabs. */
    fun claimedCategories(tabs: List<CustomTab>): Set<Category> =
        tabs.mapNotNull { it.kind }.flatMap { k -> Category.entries.filter { it.group == k } }.toSet()

    /** A LIKE pattern for [word] anywhere, as an SQL string body: quotes doubled, wildcards escaped. */
    private fun like(word: String): String =
        "%" + word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''") + "%"
}
