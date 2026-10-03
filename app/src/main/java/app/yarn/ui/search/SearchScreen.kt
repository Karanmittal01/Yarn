package app.yarn.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.yarn.AppContainer
import app.yarn.data.repo.ConversationRepository
import app.yarn.intelligence.Category
import app.yarn.ui.common.Avatar
import app.yarn.ui.common.CategoryUi
import app.yarn.ui.common.Format
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import androidx.compose.ui.res.stringResource
import app.yarn.R

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val category = MutableStateFlow<Category?>(null)
    val starredOnly = MutableStateFlow(false)

    val results: StateFlow<ConversationRepository.SearchResults?> = combine(query.debounce(200), category, starredOnly) { q, cat, starred -> Triple(q, cat, starred) }
        .mapLatest { (q, cat, starred) -> if (q.isBlank()) null else c.conversations.search(q, setOfNotNull(cat), starred) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun SearchScreen(vm: SearchViewModel, onBack: () -> Unit, onOpen: (conversationId: Long, messageId: Long?) -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val starred by vm.starredOnly.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val context = LocalContext.current
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = {
                    TextField(
                        value = query, onValueChange = { vm.query.value = it }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.clear)) } },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = starred, onClick = { vm.starredOnly.value = !starred }, label = { Text(stringResource(R.string.starred)) }) }
                items(Category.entries.filter { it != Category.SPAM }) { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { vm.category.value = if (category == c) null else c },
                        label = { Text(CategoryUi.label(c)) },
                    )
                }
            }
            val r = results
            LazyColumn(Modifier.fillMaxSize()) {
                if (r == null) {
                    item { Text(stringResource(R.string.search_private), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    return@LazyColumn
                }
                if (r.conversations.isEmpty() && r.messages.isEmpty()) {
                    item { Text(stringResource(R.string.no_results, query), Modifier.padding(24.dp)) }
                }
                if (r.conversations.isNotEmpty()) {
                    item { Header(stringResource(R.string.conversations)) }
                    items(r.conversations, key = { "c${it.id}" }) { c ->
                        ListItem(
                            headlineContent = { Text(c.title) },
                            supportingContent = { Text(c.snippet, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Avatar(c.title, null, size = 40.dp, isGroup = c.isGroup) },
                            modifier = Modifier.clickable { onOpen(c.id, null) },
                        )
                    }
                }
                if (r.messages.isNotEmpty()) {
                    item { Header(stringResource(R.string.messages_n, r.messages.size)) }
                    items(r.messages, key = { "m${it.message.id}" }) { res ->
                        ListItem(
                            overlineContent = { Text("${res.conversationTitle} · ${Format.listTime(context, res.message.date)}") },
                            headlineContent = { Text(highlight(res.snippet), maxLines = 3, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.clickable { onOpen(res.message.conversationId, res.message.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) = Text(
    text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
)

/** The FTS snippet marks matches with \u0002 … \u0003; render them bold. */
private fun highlight(snippet: String): AnnotatedString = buildAnnotatedString {
    var bold = false
    var start = 0
    snippet.forEach { ch ->
        when (ch) {
            '\u0002' -> { bold = true; start = length }
            '\u0003' -> { if (bold) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length); bold = false }
            else -> append(ch)
        }
    }
}
