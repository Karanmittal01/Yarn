package app.yarn.ui.newchat

import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.yarn.AppContainer
import app.yarn.data.contacts.ContactPhone
import app.yarn.telephony.PhoneNumbers
import app.yarn.ui.common.Avatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Recipient(val name: String, val address: String)

@OptIn(FlowPreview::class)
class NewConversationViewModel(private val c: AppContainer, initialRecipients: List<String>) : ViewModel() {
    val query = MutableStateFlow("")
    private val _recipients = MutableStateFlow(initialRecipients.map { Recipient(c.contacts.lookup(it)?.name ?: it, it) })
    val recipients: StateFlow<List<Recipient>> = _recipients
    private val permissionTick = MutableStateFlow(0)

    // Contacts appear only once you start typing, like Google Messages.
    val results: StateFlow<List<ContactPhone>> = combine(query.debounce(150), permissionTick) { q, _ -> q }
        .map { q -> if (q.isBlank()) emptyList() else c.contacts.search(q) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun hasContacts() = c.contacts.hasPermission()
    fun onPermission() { c.contacts.start(); permissionTick.value++ }

    fun add(name: String, address: String) {
        if (_recipients.value.none { PhoneNumbers.matchKey(it.address) == PhoneNumbers.matchKey(address) }) {
            _recipients.value = _recipients.value + Recipient(name, address)
        }
        query.value = ""
    }

    fun remove(r: Recipient) { _recipients.value = _recipients.value - r }

    /** Typed text that looks like a number or email can be used directly. */
    fun typedAddress(): String? = addressFrom(query.value)

    companion object {
        fun addressFrom(text: String): String? = text.trim().takeIf { PhoneNumbers.isDialable(it) || PhoneNumbers.isEmail(it) }
    }

    fun start(onReady: (Long) -> Unit) {
        val typed = typedAddress()
        val all = _recipients.value.map { it.address } + listOfNotNull(typed)
        if (all.isEmpty()) return
        viewModelScope.launch {
            val id = c.sender.conversationFor(all, c.conversations)
            onReady(id)
        }
    }
}

@Composable
fun NewConversationScreen(vm: NewConversationViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val recipients by vm.recipients.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) vm.onPermission() }
    NewConversationContent(
        query = query,
        recipients = recipients,
        results = results,
        hasContacts = vm.hasContacts(),
        onQuery = { vm.query.value = it },
        onAdd = vm::add,
        onRemove = vm::remove,
        onStart = { vm.start(onOpen) },
        onRequestContacts = { permission.launch(Manifest.permission.READ_CONTACTS) },
        onBack = onBack,
    )
}

/** Stateless layout, also rendered by the screenshot tests. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NewConversationContent(
    query: String,
    recipients: List<Recipient>,
    results: List<ContactPhone>,
    hasContacts: Boolean,
    onQuery: (String) -> Unit,
    onAdd: (String, String) -> Unit,
    onRemove: (Recipient) -> Unit,
    onStart: () -> Unit,
    onRequestContacts: () -> Unit,
    onBack: () -> Unit,
    autoFocus: Boolean = true,
) {
    // Derived from the live text so the "Send to" row always shows (and uses) exactly what was typed.
    val typedAddress = NewConversationViewModel.addressFrom(query)
    var dialpad by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocus) runCatching { focus.requestFocus() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("New chat", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            if (recipients.size > 1 || (recipients.isNotEmpty() && query.isBlank())) {
                ExtendedFloatingActionButton(
                    onClick = onStart,
                    text = { Text(if (recipients.size > 1) "Start group" else "Next", style = MaterialTheme.typography.titleMedium) },
                    icon = {},
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (recipients.isNotEmpty()) {
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recipients.forEach { r ->
                        InputChip(
                            selected = true, onClick = { onRemove(r) }, label = { Text(r.name, style = MaterialTheme.typography.labelLarge) },
                            trailingIcon = { Icon(Icons.Outlined.Close, "Remove ${r.name}", Modifier.size(16.dp)) },
                            shape = androidx.compose.foundation.shape.CircleShape,
                        )
                    }
                }
            }
            // One clean search pill, like Google Messages.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(56.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(app.yarn.ui.inbox.inboxHeaderColor())
                    .padding(start = 20.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("To", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(14.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = if (dialpad) KeyboardType.Phone else KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { typedAddress?.let { onAdd(it, it) } }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    "Name, number or email",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            inner()
                        }
                    },
                )
                IconButton(onClick = { dialpad = !dialpad }) {
                    Icon(if (dialpad) Icons.Outlined.Keyboard else Icons.Outlined.Dialpad, if (dialpad) "Use keyboard" else "Use dial pad")
                }
            }
            if (query.isBlank() && recipients.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 72.dp, start = 40.dp, end = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.PersonSearch, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Start typing a name or number",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Add more people to start a group chat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)) {
                typedAddress?.let { typed ->
                    item(key = "typed") {
                        RecipientRow(
                            title = "Send to $typed",
                            subtitle = if (recipients.isEmpty()) "Tap to start" else "Tap to add",
                            avatar = { Avatar(typed, null, size = 48.dp, neutral = true) },
                            action = "Add",
                            onAction = { onAdd(typed, typed) },
                            onClick = { if (recipients.isEmpty()) onStart() else onAdd(typed, typed) },
                        )
                    }
                }
                if (!hasContacts && query.isNotBlank()) {
                    item {
                        RecipientRow(
                            title = "Search your contacts",
                            subtitle = "Allow access to find people by name. Contacts stay on your phone.",
                            avatar = { Avatar("?", null, size = 48.dp, neutral = true, icon = Icons.Outlined.PersonSearch) },
                            action = "Allow",
                            onAction = onRequestContacts,
                            onClick = onRequestContacts,
                        )
                    }
                }
                items(results, key = { "${it.contactId}-${it.number}" }) { contact ->
                    RecipientRow(
                        title = contact.name,
                        subtitle = contact.number,
                        avatar = { Avatar(contact.name, contact.photoUri, size = 48.dp) },
                        action = "Add",
                        onAction = { onAdd(contact.name, contact.number) },
                        onClick = {
                            // First pick opens the chat right away; later picks build a group.
                            onAdd(contact.name, contact.number)
                            if (recipients.isEmpty()) onStart()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipientRow(
    title: String,
    subtitle: String,
    avatar: @Composable () -> Unit,
    action: String,
    onAction: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatar()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        TextButton(onClick = onAction) { Text(action, style = MaterialTheme.typography.labelLarge) }
    }
}
