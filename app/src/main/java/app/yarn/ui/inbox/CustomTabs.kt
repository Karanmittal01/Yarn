package app.yarn.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yarn.R
import app.yarn.data.db.ConversationEntity
import app.yarn.data.prefs.CustomTab
import app.yarn.intelligence.CategoryGroup
import app.yarn.ui.common.CategoryUi
import java.util.UUID

private enum class Step { CHOOSE, CHATS, WORDS }

/**
 * Makes a tab of the user's own (Yarn Pro) from a few choices Yarn can sort reliably: one kind of
 * message, chats they pick, or messages that contain a word.
 */
@Composable
fun NewTabDialog(loadChats: suspend () -> List<ConversationEntity>, onCreate: (CustomTab) -> Unit, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(Step.CHOOSE) }
    var name by remember { mutableStateOf("") }
    var words by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<Long>() }
    var chats by remember { mutableStateOf<List<ConversationEntity>?>(null) }
    LaunchedEffect(step) { if (step == Step.CHATS && chats == null) chats = loadChats() }
    val kindNames = mapOf(
        CategoryGroup.TRANSACTIONS to stringResource(R.string.kind_money),
        CategoryGroup.SHOPPING to stringResource(R.string.kind_orders),
        CategoryGroup.TRAVEL to stringResource(R.string.group_travel),
    )
    val wordList = words.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (step == Step.CHATS) R.string.custom_tab_pick_chats else R.string.custom_tab_title)) },
        text = {
            when (step) {
                Step.CHOOSE -> NewTabChoices(
                    onKind = { kind -> onCreate(CustomTab(UUID.randomUUID().toString(), kindNames.getValue(kind), kind = kind)); onDismiss() },
                    onChats = { step = Step.CHATS },
                    onWords = { step = Step.WORDS },
                )
                Step.CHATS -> Column {
                    OutlinedTextField(name, { name = it.take(24) }, label = { Text(stringResource(R.string.custom_tab_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(chats.orEmpty(), key = { it.id }) { c ->
                            Row(
                                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                                    .clickable { if (c.id in picked) picked.remove(c.id) else picked.add(c.id) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(c.id in picked, onCheckedChange = { if (it) picked.add(c.id) else picked.remove(c.id) })
                                Text(c.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Step.WORDS -> NewTabWords(words, { words = it.take(120) }, name, { name = it.take(24) })
            }
        },
        confirmButton = {
            when (step) {
                Step.CHOOSE -> Unit
                Step.CHATS -> TextButton(
                    enabled = picked.isNotEmpty(),
                    onClick = {
                        val title = name.trim().ifEmpty { chats.orEmpty().firstOrNull { it.id == picked.first() }?.title.orEmpty().take(24) }
                        onCreate(CustomTab(UUID.randomUUID().toString(), title, chats = picked.toList())); onDismiss()
                    },
                ) { Text(stringResource(R.string.custom_tab_add)) }
                Step.WORDS -> TextButton(
                    enabled = wordList.isNotEmpty(),
                    onClick = {
                        val title = name.trim().ifEmpty { wordList.first().replaceFirstChar(Char::uppercaseChar).take(24) }
                        onCreate(CustomTab(UUID.randomUUID().toString(), title, words = wordList)); onDismiss()
                    },
                ) { Text(stringResource(R.string.custom_tab_add)) }
            }
        },
        dismissButton = { TextButton(onClick = { if (step == Step.CHOOSE) onDismiss() else step = Step.CHOOSE }) { Text(stringResource(if (step == Step.CHOOSE) R.string.cancel else R.string.back)) } },
    )
}

/** The tab maker's first step: what the tab shows. */
@Composable
internal fun NewTabChoices(onKind: (CategoryGroup) -> Unit, onChats: () -> Unit, onWords: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.custom_tab_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        ChoiceRow(CategoryUi.kindIcon(CategoryGroup.TRANSACTIONS), stringResource(R.string.kind_money), stringResource(R.string.custom_tab_money_desc)) { onKind(CategoryGroup.TRANSACTIONS) }
        ChoiceRow(CategoryUi.kindIcon(CategoryGroup.SHOPPING), stringResource(R.string.kind_orders), stringResource(R.string.custom_tab_orders_desc)) { onKind(CategoryGroup.SHOPPING) }
        ChoiceRow(CategoryUi.kindIcon(CategoryGroup.TRAVEL), stringResource(R.string.group_travel), stringResource(R.string.custom_tab_travel_desc)) { onKind(CategoryGroup.TRAVEL) }
        ChoiceRow(Icons.Outlined.Forum, stringResource(R.string.custom_tab_chats), stringResource(R.string.custom_tab_chats_desc), onChats)
        ChoiceRow(Icons.Outlined.TextFields, stringResource(R.string.custom_tab_words), stringResource(R.string.custom_tab_words_desc), onWords)
    }
}

/** The words a tab looks for, and its name. */
@Composable
internal fun NewTabWords(words: String, onWords: (String) -> Unit, name: String, onName: (String) -> Unit) {
    Column {
        OutlinedTextField(
            words, onWords, label = { Text(stringResource(R.string.custom_tab_words_field)) },
            supportingText = { Text(stringResource(R.string.custom_tab_words_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(name, onName, label = { Text(stringResource(R.string.custom_tab_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ChoiceRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Confirms removing a tab the user made; their messages aren't touched. */
@Composable
fun RemoveTabDialog(tab: CustomTab, onRemove: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_tab_remove_title, tab.name)) },
        text = { Text(stringResource(R.string.custom_tab_remove_text)) },
        confirmButton = { TextButton(onClick = { onRemove(); onDismiss() }) { Text(stringResource(R.string.custom_tab_remove)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * The "+" at the end of the tabs: every tab with a switch to show or hide it (tabs the user made
 * are removed when switched off), and the way to make a new one.
 */
@Composable
fun TabsDialog(
    builtIn: List<BuiltInTab>,
    custom: List<CustomTab>,
    onShow: (String, Boolean) -> Unit,
    onRemove: (CustomTab) -> Unit,
    onMake: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tabs_title)) },
        text = { TabsList(builtIn, custom, onShow, onRemove) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        dismissButton = {
            if (custom.size < CustomTab.MAX) TextButton(onClick = { onDismiss(); onMake() }) { Text(stringResource(R.string.tabs_make)) }
        },
    )
}

/** The tab editor's list, also drawn in place for screenshots. */
@Composable
internal fun TabsList(builtIn: List<BuiltInTab>, custom: List<CustomTab>, onShow: (String, Boolean) -> Unit, onRemove: (CustomTab) -> Unit) {
    LazyColumn(Modifier.heightIn(max = 420.dp)) {
        item { Text(stringResource(R.string.tabs_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)) }
        items(builtIn, key = { it.key }) { t ->
            val icon = app.yarn.data.repo.InboxGroup.entries.firstOrNull { it.name == t.key }?.let(CategoryUi::groupIcon) ?: Icons.Outlined.Report
            SwitchLine(icon, stringResource(t.label), t.shown) { onShow(t.key, it) }
        }
        items(custom, key = { it.id }) { t ->
            val icon = t.kind?.let(CategoryUi::kindIcon) ?: if (t.chats.isNotEmpty()) Icons.Outlined.Forum else Icons.Outlined.TextFields
            SwitchLine(icon, t.name, true) { if (!it) onRemove(t) }
        }
        if (custom.size >= CustomTab.MAX) item {
            Text(stringResource(R.string.tabs_limit, CustomTab.MAX), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun SwitchLine(icon: ImageVector, title: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onChange(!on) }.padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        androidx.compose.material3.Switch(on, onCheckedChange = onChange)
    }
}

/** Long-pressing a built-in tab: hide it (its messages stay in All; "+" brings it back). */
@Composable
fun HideTabDialog(name: String, onHide: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hide_tab_title, name)) },
        text = { Text(stringResource(R.string.hide_tab_text)) },
        confirmButton = { TextButton(onClick = { onHide(); onDismiss() }) { Text(stringResource(R.string.hide_tab)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
