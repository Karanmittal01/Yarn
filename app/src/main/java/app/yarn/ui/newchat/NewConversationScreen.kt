package app.yarn.ui.newchat

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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NewConversationScreen(vm: NewConversationViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val recipients by vm.recipients.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    // Derived from the live text so the "Send to" row always shows (and uses) exactly what was typed.
    val typedAddress = NewConversationViewModel.addressFrom(query)
    var dialpad by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) vm.onPermission() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New conversation") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            if (recipients.size > 1 || (recipients.isNotEmpty() && query.isBlank())) {
                ExtendedFloatingActionButton(onClick = { vm.start(onOpen) }, text = { Text(if (recipients.size > 1) "Start group" else "Next") }, icon = {})
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                recipients.forEach { r ->
                    InputChip(
                        selected = true, onClick = { vm.remove(r) }, label = { Text(r.name) },
                        trailingIcon = { Icon(Icons.Outlined.Close, "Remove ${r.name}") },
                    )
                }
            }
            TextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).focusRequester(focus),
                placeholder = { Text("Type a name, phone number or email") },
                singleLine = true,
                leadingIcon = { Text("To", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge) },
                trailingIcon = {
                    IconButton(onClick = { dialpad = !dialpad }) {
                        Icon(if (dialpad) Icons.Outlined.Keyboard else Icons.Outlined.Dialpad, if (dialpad) "Use keyboard" else "Use dial pad")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = if (dialpad) KeyboardType.Phone else KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.typedAddress()?.let { vm.add(it, it) } }),
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                typedAddress?.let { typed ->
                    item(key = "typed") {
                        ListItem(
                            headlineContent = { Text("Send to $typed") },
                            supportingContent = { Text("Tap to start, or add more people") },
                            leadingContent = { Avatar(typed, null, size = 40.dp) },
                            trailingContent = { TextButton(onClick = { vm.add(typed, typed) }) { Text("Add") } },
                            modifier = Modifier.clickable { if (recipients.isEmpty()) vm.start(onOpen) else vm.add(typed, typed) },
                        )
                    }
                }
                if (!vm.hasContacts() && query.isNotBlank()) {
                    item {
                        ListItem(
                            headlineContent = { Text("Show your contacts") },
                            supportingContent = { Text("Allow contacts access to search by name. Contacts stay on your phone.") },
                            trailingContent = { TextButton(onClick = { permission.launch(Manifest.permission.READ_CONTACTS) }) { Text("Allow") } },
                        )
                    }
                }
                items(results, key = { "${it.contactId}-${it.number}" }) { contact ->
                    ListItem(
                        headlineContent = { Text(contact.name) },
                        supportingContent = { Text(contact.number) },
                        leadingContent = { Avatar(contact.name, contact.photoUri, size = 40.dp) },
                        trailingContent = { TextButton(onClick = { vm.add(contact.name, contact.number) }) { Text("Add to group") } },
                        modifier = Modifier.clickable {
                            // First pick opens the chat right away; later picks build a group.
                            vm.add(contact.name, contact.number)
                            if (recipients.isEmpty()) vm.start(onOpen)
                        },
                    )
                }
            }
        }
    }
}
