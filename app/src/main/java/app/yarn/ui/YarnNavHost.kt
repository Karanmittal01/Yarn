package app.yarn.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.yarn.data.repo.InboxFilter
import app.yarn.data.repo.InboxView
import app.yarn.ui.common.yarnViewModel
import app.yarn.ui.conversation.ConversationScreen
import app.yarn.ui.conversation.ConversationViewModel
import app.yarn.ui.inbox.InboxDestination
import app.yarn.ui.inbox.InboxScreen
import app.yarn.ui.inbox.InboxViewModel
import app.yarn.ui.newchat.NewConversationScreen
import app.yarn.ui.newchat.NewConversationViewModel
import app.yarn.ui.search.SearchScreen
import app.yarn.ui.search.SearchViewModel
import app.yarn.ui.settings.AiSettingsScreen
import app.yarn.ui.settings.BackupScreen
import app.yarn.ui.settings.BlockedScreen
import app.yarn.ui.settings.SettingsPage
import app.yarn.ui.settings.SettingsScreen
import app.yarn.ui.settings.SettingsViewModel
import app.yarn.ui.settings.StarredScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable data class ChatRoute(val id: Long, val messageId: Long = -1)
@Serializable data class NewChatRoute(val recipients: String = "")
@Serializable object SearchRoute
@Serializable object SettingsRoute
@Serializable data class SettingsSubRoute(val page: String)
@Serializable data class FolderRoute(val view: String)
@Serializable object StarredRoute

/** Navigation requests coming from outside the UI (notifications, share sheet, other apps). */
sealed interface ExternalNav {
    data class OpenChat(val id: Long, val messageId: Long = -1) : ExternalNav
    data class NewChat(val recipients: List<String> = emptyList()) : ExternalNav
}

@Composable
fun YarnNavHost(external: StateFlow<ExternalNav?>, consume: () -> Unit) {
    val nav = rememberNavController()
    val pending by external.collectAsState()
    LaunchedEffect(pending) {
        when (val p = pending) {
            is ExternalNav.OpenChat -> nav.navigate(ChatRoute(p.id, p.messageId)) { launchSingleTop = true }
            is ExternalNav.NewChat -> nav.navigate(NewChatRoute(p.recipients.joinToString(","))) { launchSingleTop = true }
            null -> return@LaunchedEffect
        }
        consume()
    }

    NavHost(nav, startDestination = HomeRoute) {
        composable<HomeRoute> { HomePane(nav) }
        composable<ChatRoute> { entry ->
            val r = entry.toRoute<ChatRoute>()
            val vm = yarnViewModel(key = "chat-${r.id}") { ConversationViewModel(it, r.id) }
            ConversationScreen(
                vm = vm,
                highlightMessageId = r.messageId.takeIf { it > 0 },
                onBack = { nav.popBackStack() },
                onForward = { nav.navigate(NewChatRoute()) },
                onClosed = { nav.popBackStack() },
            )
        }
        composable<NewChatRoute> { entry ->
            val r = entry.toRoute<NewChatRoute>()
            val vm = yarnViewModel(key = "new-${r.recipients}") { NewConversationViewModel(it, r.recipients.split(',').filter { s -> s.isNotBlank() }) }
            NewConversationScreen(vm, onBack = { nav.popBackStack() }) { id ->
                nav.navigate(ChatRoute(id)) { popUpTo<NewChatRoute> { inclusive = true } }
            }
        }
        composable<SearchRoute> {
            val vm = yarnViewModel { SearchViewModel(it) }
            SearchScreen(vm, onBack = { nav.popBackStack() }) { id, msg -> nav.navigate(ChatRoute(id, msg ?: -1)) }
        }
        composable<SettingsRoute> {
            val vm = yarnViewModel { SettingsViewModel(it) }
            SettingsScreen(vm, onBack = { nav.popBackStack() }) { page -> nav.navigate(SettingsSubRoute(page.name)) }
        }
        composable<SettingsSubRoute> { entry ->
            val vm = yarnViewModel { SettingsViewModel(it) }
            when (SettingsPage.valueOf(entry.toRoute<SettingsSubRoute>().page)) {
                SettingsPage.AI -> AiSettingsScreen(vm, onBack = { nav.popBackStack() })
                SettingsPage.BLOCKED -> BlockedScreen(vm, onBack = { nav.popBackStack() }) { nav.navigate(FolderRoute(InboxView.BLOCKED.name)) }
                SettingsPage.BACKUP -> BackupScreen(vm, onBack = { nav.popBackStack() })
            }
        }
        composable<FolderRoute> { entry ->
            val view = InboxView.valueOf(entry.toRoute<FolderRoute>().view)
            val vm = yarnViewModel(key = "folder-$view") { InboxViewModel(it, InboxFilter(view)) }
            InboxScreen(
                vm = vm,
                title = when (view) {
                    InboxView.ARCHIVED -> "Archived"
                    InboxView.SPAM -> "Spam"
                    InboxView.BLOCKED -> "Blocked"
                    InboxView.STARRED -> "Starred conversations"
                    else -> "Messages"
                },
                selectedConversation = null,
                onOpen = { nav.navigate(ChatRoute(it)) },
                onNewMessage = null,
                onSearch = null,
                onNavigate = {},
                onBack = { nav.popBackStack() },
                showChips = false,
            )
        }
        composable<StarredRoute> {
            val vm = yarnViewModel { SettingsViewModel(it) }
            StarredScreen(vm, onBack = { nav.popBackStack() }) { id, msg -> nav.navigate(ChatRoute(id, msg)) }
        }
    }
}

/** Single pane on phones; list + conversation side by side on tablets, foldables and landscape. */
@Composable
private fun HomePane(nav: NavHostController) {
    val inboxVm = yarnViewModel(key = "inbox") { InboxViewModel(it, InboxFilter()) }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    val onNavigate: (InboxDestination) -> Unit = { d ->
        when (d) {
            InboxDestination.Starred -> nav.navigate(StarredRoute)
            InboxDestination.Archived -> nav.navigate(FolderRoute(InboxView.ARCHIVED.name))
            InboxDestination.Spam -> nav.navigate(FolderRoute(InboxView.SPAM.name))
            InboxDestination.Blocked -> nav.navigate(FolderRoute(InboxView.BLOCKED.name))
            InboxDestination.Settings -> nav.navigate(SettingsRoute)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val twoPane = width >= 720.dp
        if (!twoPane) {
            InboxScreen(
                vm = inboxVm, title = "Messages", selectedConversation = null,
                onOpen = { nav.navigate(ChatRoute(it)) }, onNewMessage = { nav.navigate(NewChatRoute()) },
                onSearch = { nav.navigate(SearchRoute) }, onNavigate = onNavigate,
            )
        } else {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width((width * 0.38f).coerceIn(320.dp, 440.dp)).fillMaxHeight()) {
                    InboxScreen(
                        vm = inboxVm, title = "Messages", selectedConversation = selected,
                        onOpen = { selected = it }, onNewMessage = { nav.navigate(NewChatRoute()) },
                        onSearch = { nav.navigate(SearchRoute) }, onNavigate = onNavigate,
                    )
                }
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val id = selected
                    if (id == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.AutoMirrored.Outlined.Chat, null, tint = MaterialTheme.colorScheme.outline)
                                Text("Select a conversation", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        val vm = yarnViewModel(key = "chat-$id") { ConversationViewModel(it, id) }
                        ConversationScreen(
                            vm = vm, highlightMessageId = null, onBack = null,
                            onForward = { nav.navigate(NewChatRoute()) }, onClosed = { selected = null },
                        )
                    }
                }
            }
        }
    }
}
