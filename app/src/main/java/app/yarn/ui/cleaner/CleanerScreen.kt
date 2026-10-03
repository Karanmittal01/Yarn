package app.yarn.ui.cleaner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.yarn.AppContainer
import app.yarn.data.prefs.AppSettings
import app.yarn.data.repo.JunkKind
import app.yarn.telephony.DefaultSmsApp
import app.yarn.ui.common.ConfirmDialog
import app.yarn.ui.common.container
import app.yarn.ui.settings.ChoiceRow
import app.yarn.ui.settings.SectionHeader
import app.yarn.ui.settings.SettingsGroup
import app.yarn.ui.settings.SettingsRow
import app.yarn.ui.settings.settingsPageColor
import app.yarn.ui.theme.LocalDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import app.yarn.R

data class CleanerState(
    val scanning: Boolean = true,
    val found: Map<JunkKind, Int> = emptyMap(),
    val selected: Set<JunkKind> = JunkKind.entries.filter { it.selectedByDefault }.toSet(),
    val cleaning: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    /** Messages cleared by the last clean, shown as the "All clean" result. */
    val cleaned: Int? = null,
) {
    val selectedCount: Int get() = selected.sumOf { found[it] ?: 0 }
    val foundCount: Int get() = found.values.sum()
}

class CleanerViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(CleanerState())
    val state: StateFlow<CleanerState> = _state
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val isDefault = c.isDefaultSmsApp
    private var ids: Map<JunkKind, List<Long>> = emptyMap()

    init { scan() }

    fun scan() = viewModelScope.launch {
        _state.update { it.copy(scanning = true) }
        ids = c.cleaner.scan()
        _state.update { it.copy(scanning = false, found = ids.mapValues { e -> e.value.size }) }
    }

    fun toggle(kind: JunkKind) = _state.update { s -> s.copy(selected = if (kind in s.selected) s.selected - kind else s.selected + kind) }

    fun clean() {
        val chosen = _state.value.selected.flatMap { ids[it].orEmpty() }.distinct()
        if (chosen.isEmpty()) return
        // App scope: leaving the screen mustn't stop a clean half-way.
        c.scope.launch {
            _state.update { it.copy(cleaning = true, done = 0, total = chosen.size) }
            val n = c.cleaner.clean(chosen) { done -> _state.update { it.copy(done = done) } }
            ids = c.cleaner.scan()
            _state.update { it.copy(cleaning = false, cleaned = n, found = ids.mapValues { e -> e.value.size }) }
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { c.settings.update(transform) }
}

@Composable
fun CleanerScreen(vm: CleanerViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isDefault by vm.isDefault.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { context.container.refreshPlatformState() }
    var confirm by remember { mutableStateOf(false) }
    CleanerContent(
        state = state, settings = settings, isDefault = isDefault,
        onToggle = vm::toggle,
        onClean = { confirm = true },
        onSetDefault = { DefaultSmsApp.requestIntent(context)?.let { roleLauncher.launch(it) } },
        onSettings = { vm.update(it) },
        onBack = onBack,
    )
    if (confirm) {
        val n = state.selectedCount
        ConfirmDialog(
            title = pluralStringResource(R.plurals.clean_delete_q, n, n),
            text = stringResource(R.string.clean_delete_text),
            confirm = stringResource(R.string.clean_up),
            destructive = true,
            onConfirm = { vm.clean() },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
fun CleanerContent(
    state: CleanerState,
    settings: AppSettings,
    isDefault: Boolean,
    onToggle: (JunkKind) -> Unit,
    onClean: () -> Unit,
    onSetDefault: () -> Unit,
    onSettings: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    val page = settingsPageColor()
    Scaffold(
        containerColor = page,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.clean_up)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = page),
            )
        },
        bottomBar = { CleanBar(state, isDefault, onClean, onSetDefault) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Hero(state) }
            item { SectionHeader(stringResource(R.string.found_on_phone)) }
            item {
                SettingsGroup {
                    JunkKind.entries.forEach { kind ->
                        JunkRow(kind, state.found[kind], kind in state.selected, enabled = !state.cleaning) { onToggle(kind) }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.clean_automatically)) }
            item {
                SettingsGroup {
                    ChoiceRow(
                        stringResource(R.string.verification_codes), listOf(0 to stringResource(R.string.keep), 24 to pluralStringResource(R.plurals.delete_after_days, 1, 1), 168 to pluralStringResource(R.plurals.delete_after_days, 7, 7), 720 to pluralStringResource(R.plurals.delete_after_days, 30, 30)),
                        settings.otpAutoDeleteHours, icon = Icons.Outlined.Password, tint = Color(0xFF7C5CFF),
                    ) { v -> onSettings { it.copy(otpAutoDeleteHours = v) } }
                    ChoiceRow(
                        stringResource(R.string.offers_promotions), listOf(0 to stringResource(R.string.keep), 7 to pluralStringResource(R.plurals.delete_after_days, 7, 7), 30 to pluralStringResource(R.plurals.delete_after_days, 30, 30), 90 to pluralStringResource(R.plurals.delete_after_days, 90, 90)),
                        settings.promoAutoDeleteDays, icon = Icons.Outlined.LocalOffer, tint = Color(0xFFFF9500),
                    ) { v -> onSettings { it.copy(promoAutoDeleteDays = v) } }
                    ChoiceRow(
                        stringResource(R.string.verdict_spam), listOf(0 to stringResource(R.string.keep), 7 to pluralStringResource(R.plurals.delete_after_days, 7, 7), 30 to pluralStringResource(R.plurals.delete_after_days, 30, 30), 90 to pluralStringResource(R.plurals.delete_after_days, 90, 90)),
                        settings.spamAutoDeleteDays, icon = Icons.Outlined.Report, tint = Color(0xFFF2453D),
                    ) { v -> onSettings { it.copy(spamAutoDeleteDays = v) } }
                }
            }
            item {
                Text(
                    stringResource(R.string.clean_never_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
                )
            }
        }
    }
}

private val JunkKind.icon: ImageVector get() = when (this) {
    JunkKind.CODES -> Icons.Outlined.Password
    JunkKind.OFFERS -> Icons.Outlined.LocalOffer
    JunkKind.SPAM -> Icons.Outlined.Report
    JunkKind.ORDERS -> Icons.Outlined.LocalShipping
}

private val JunkKind.tint: Color get() = when (this) {
    JunkKind.CODES -> Color(0xFF7C5CFF)
    JunkKind.OFFERS -> Color(0xFFFF9500)
    JunkKind.SPAM -> Color(0xFFF2453D)
    JunkKind.ORDERS -> Color(0xFF00A3A3)
}

private val JunkKind.title: Int get() = when (this) {
    JunkKind.CODES -> R.string.junk_codes
    JunkKind.OFFERS -> R.string.junk_offers
    JunkKind.SPAM -> R.string.verdict_spam
    JunkKind.ORDERS -> R.string.junk_orders
}

private val JunkKind.summary: Int get() = when (this) {
    JunkKind.CODES -> R.string.junk_codes_sub
    JunkKind.OFFERS -> R.string.junk_offers_sub
    JunkKind.SPAM -> R.string.junk_spam_sub
    JunkKind.ORDERS -> R.string.junk_orders_sub
}

@Composable
private fun JunkRow(kind: JunkKind, count: Int?, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val has = (count ?: 0) > 0
    SettingsRow(
        stringResource(kind.title),
        Modifier.toggleable(value = checked && has, enabled = enabled && has, role = Role.Checkbox) { onToggle() },
        summary = stringResource(if (count == 0) R.string.nothing_to_clear else kind.summary),
        icon = kind.icon, tint = kind.tint, enabled = has || count == null,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (count != null && has) {
                    Text(formatCount(count), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                Checkbox(checked = checked && has, onCheckedChange = null, enabled = enabled && has, modifier = Modifier.padding(start = 4.dp))
            }
        },
    )
}

/** Big ring with the amount of junk found; turns into a green tick once everything is clean. */
@Composable
private fun Hero(state: CleanerState) {
    val dark = LocalDarkTheme.current
    val accent = Color(0xFF00A86B)
    val bg = if (dark) Brush.verticalGradient(listOf(Color(0xFF0E2A22), Color(0xFF16181C)))
    else Brush.verticalGradient(listOf(Color(0xFFDDF6EC), Color(0xFFF5FBF8)))
    val sweep by animateFloatAsState(
        when {
            state.cleaning && state.total > 0 -> state.done / state.total.toFloat()
            state.scanning -> 0.15f
            else -> 1f
        },
        tween(600, easing = FastOutSlowInEasing), label = "ring",
    )
    val allClean = !state.scanning && !state.cleaning && state.foundCount == 0
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .padding(vertical = 26.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            val track = accent.copy(alpha = 0.16f)
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 10.dp.toPx()
                val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                drawArc(accent, -90f, 360f * sweep, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            AnimatedContent(allClean || state.cleaned != null && state.foundCount == 0, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith fadeOut() }, label = "heroIcon") { clean ->
                Box(Modifier.size(84.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Icon(if (clean) Icons.Outlined.CheckCircle else Icons.Outlined.CleaningServices, null, tint = Color.White, modifier = Modifier.size(44.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        val (headline, line) = when {
            state.scanning -> stringResource(R.string.hero_scanning) to stringResource(R.string.hero_scanning_sub)
            state.cleaning -> stringResource(R.string.hero_cleaning) to stringResource(R.string.hero_cleaning_sub, formatCount(state.done), formatCount(state.total))
            state.cleaned != null && state.foundCount == 0 -> stringResource(R.string.hero_all_clean) to pluralStringResource(R.plurals.hero_cleared_sub, state.cleaned, formatCount(state.cleaned))
            allClean -> stringResource(R.string.hero_all_clean) to stringResource(R.string.hero_nothing_now)
            state.cleaned != null -> stringResource(R.string.hero_cleared, formatCount(state.cleaned)) to stringResource(R.string.hero_more, formatCount(state.foundCount))
            else -> formatCount(state.foundCount) to stringResource(R.string.hero_found_sub)
        }
        Text(headline, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun CleanBar(state: CleanerState, isDefault: Boolean, onClean: () -> Unit, onSetDefault: () -> Unit) {
    Surface(color = settingsPageColor()) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                state.cleaning -> LinearProgressIndicator(
                    progress = { if (state.total > 0) state.done / state.total.toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                )
                !isDefault -> {
                    Text(stringResource(R.string.only_default_delete), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onSetDefault, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp)) {
                        Text(stringResource(R.string.set_yarn_default), style = MaterialTheme.typography.titleMedium)
                    }
                }
                else -> {
                    val n = state.selectedCount
                    Button(
                        onClick = onClean, enabled = n > 0 && !state.scanning,
                        modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp),
                    ) {
                        Icon(Icons.Outlined.CleaningServices, null, Modifier.size(20.dp))
                        Spacer(Modifier.size(10.dp))
                        Text(if (n > 0) pluralStringResource(R.plurals.clean_n, n, formatCount(n)) else stringResource(R.string.nothing_selected), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

private fun formatCount(n: Int): String = java.text.NumberFormat.getIntegerInstance().format(n)
