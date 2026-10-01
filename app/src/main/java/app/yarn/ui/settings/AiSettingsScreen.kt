package app.yarn.ui.settings

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yarn.ai.EngineState
import app.yarn.ai.EngineStatus
import app.yarn.intelligence.Category
import app.yarn.intelligence.SpamSensitivity
import app.yarn.ui.common.CategoryUi
import app.yarn.ui.common.ConfirmDialog
import app.yarn.work.ReanalyzeWorker
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AiSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val c = vm.c
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<EngineStatus?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var nanoProgress by remember { mutableStateOf<Float?>(null) }
    var importing by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var selfHostedDialog by remember { mutableStateOf(false) }
    val corrections by c.db.learning().observeCorrections().collectAsState(initial = emptyList())
    val rules by c.db.learning().observeRules().collectAsState(initial = emptyList())

    LaunchedEffect(refresh, s.localLlmPath, s.selfHostedAiEnabled) { status = c.assistant.status() }

    val importModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "model.task"
            runCatching { c.localLlm.import(uri, name) }
                .onSuccess { f -> c.settings.update { it.copy(localLlmPath = f.absolutePath) } }
            importing = false
            refresh++
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("AI & learning") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Text(
                    "All smart features run on your phone by default. Nothing you write is sent to a server unless you set up your own AI server below.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp),
                )
            }
            item { SwitchRow("Smart features", "Master switch for everything on this page", s.aiEnabled) { v -> vm.update { it.copy(aiEnabled = v) } } }
            item { SectionHeader("Organise") }
            item { SwitchRow("Automatic categories", "Personal, OTP, banking, deliveries, promotions…", s.autoCategorize, enabled = s.aiEnabled) { v -> vm.update { it.copy(autoCategorize = v) } } }
            item { SwitchRow("Spam & scam protection", "Suspicious messages go to the Spam folder — never deleted silently", s.spamFilter, enabled = s.aiEnabled) { v -> vm.update { it.copy(spamFilter = v) } } }
            item {
                ChoiceRow(
                    "Spam sensitivity",
                    listOf(SpamSensitivity.LOW to "Low — only obvious spam", SpamSensitivity.MEDIUM to "Balanced", SpamSensitivity.HIGH to "High — catch more, may misfile"),
                    s.spamSensitivity,
                ) { v -> vm.update { it.copy(spamSensitivity = v) }; ReanalyzeWorker.enqueue(context) }
            }
            item { SwitchRow("Smart replies", "Suggested replies in personal chats (ML Kit, English)", s.smartReplies, enabled = s.aiEnabled) { v -> vm.update { it.copy(smartReplies = v) } } }
            item { SwitchRow("Enhanced detection", "Use ML Kit to find addresses and dates (one-time ~5 MB download on Wi-Fi)", s.mlEntityExtraction, enabled = s.aiEnabled) { v -> vm.update { it.copy(mlEntityExtraction = v) } } }
            item {
                val langs = remember { TranslateLanguage.getAllLanguages().map { it to Locale.forLanguageTag(it).displayLanguage }.sortedBy { it.second } }
                ChoiceRow("Translate messages into", langs, s.translateTarget) { v -> vm.update { it.copy(translateTarget = v) } }
            }

            item { SectionHeader("Language models") }
            item {
                val st = status
                val nano = st?.nanoSummarizer
                ListItem(
                    headlineContent = { Text("Gemini Nano (built into some phones)") },
                    supportingContent = {
                        Column {
                            Text(
                                when (nano) {
                                    null -> "Checking…"
                                    EngineState.AVAILABLE -> "Ready. Used for summaries and rewriting."
                                    EngineState.DOWNLOADABLE -> "Supported on this phone. Download the model (free) to enable."
                                    EngineState.DOWNLOADING -> "Downloading…"
                                    else -> "Not supported on this phone. Yarn uses other engines instead."
                                },
                            )
                            nanoProgress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) }
                        }
                    },
                    trailingContent = {
                        if (nano == EngineState.DOWNLOADABLE) TextButton(onClick = {
                            nanoProgress = 0f
                            scope.launch {
                                c.nano.download { done, total -> nanoProgress = if (total > 0) done.toFloat() / total else null }
                                nanoProgress = null
                                refresh++
                            }
                        }) { Text("Download") }
                    },
                )
            }
            item {
                val path = s.localLlmPath
                ListItem(
                    headlineContent = { Text("Your own on-device model") },
                    supportingContent = {
                        Text(
                            when {
                                importing -> "Importing… large models take a minute."
                                path != null && status?.localLlm == EngineState.AVAILABLE -> "Using ${path.substringAfterLast('/')}"
                                path != null -> "Model file missing — import it again."
                                else -> "Import a free open model (.task or .litertlm), e.g. Gemma 3 1B or Qwen 2.5 1.5B from Hugging Face / Kaggle. Runs offline with MediaPipe. Needs ~1–3 GB of free RAM."
                            },
                        )
                    },
                    trailingContent = {
                        if (path == null) TextButton(onClick = { importModel.launch(arrayOf("*/*")) }, enabled = !importing) { Text("Import") }
                        else TextButton(onClick = {
                            scope.launch {
                                c.localLlm.release()
                                java.io.File(path).delete()
                                c.settings.update { it.copy(localLlmPath = null) }
                                refresh++
                            }
                        }) { Text("Remove") }
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Self-hosted AI server (optional)") },
                    supportingContent = {
                        Text(
                            if (s.selfHostedAiEnabled) "On · ${s.selfHostedAiUrl} · ${s.selfHostedAiModel}\nOnly used when you tap Summarise or Rewrite and no on-device model is available."
                            else "Off. Connect your own OpenAI-compatible server (e.g. Ollama on your PC). Message text is sent to it only when you ask.",
                        )
                    },
                    trailingContent = { TextButton(onClick = { selfHostedDialog = true }) { Text(if (s.selfHostedAiEnabled) "Edit" else "Set up") } },
                )
            }

            item { SectionHeader("What Yarn has learned") }
            item {
                Text(
                    "Every time you move a message to another category or mark spam, Yarn learns locally. You can review and undo anything here.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (rules.isEmpty() && corrections.isEmpty()) item { ListItem(headlineContent = { Text("Nothing learned yet") }) }
            items(rules, key = { "r" + it.address }) { r ->
                ListItem(
                    headlineContent = { Text(r.displayAddress) },
                    supportingContent = {
                        Text(listOfNotNull(
                            Category.fromName(r.category)?.let { "Always ${CategoryUi.label(it)}" },
                            r.trusted?.let { if (it) "Trusted (never spam)" else "Always spam" },
                        ).joinToString(" · "))
                    },
                    trailingContent = { IconButton(onClick = { vm.launch { db.learning().deleteRule(r.address); engine.reload() } }) { Icon(Icons.Outlined.Delete, "Forget rule for ${r.displayAddress}") } },
                )
            }
            items(corrections.take(200), key = { "c" + it.id }) { corr ->
                ListItem(
                    headlineContent = { Text(corr.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            corr.category?.let { "→ ${Category.fromName(it)?.let(CategoryUi::label) ?: it}" }
                                ?: if (corr.isSpam == true) "→ Spam" else "→ Not spam",
                        )
                    },
                    trailingContent = { IconButton(onClick = { vm.launch { db.learning().deleteCorrection(corr.id); engine.reload() } }) { Icon(Icons.Outlined.Delete, "Forget this correction") } },
                )
            }
            item { ClickRow("Re-check all messages", "Re-run categories and spam checks with what Yarn knows now") { ReanalyzeWorker.enqueue(context) } }
            item { ClickRow("Reset learning", "Forget all corrections and sender rules") { confirmReset = true } }
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            "Reset learning?", "Yarn will forget every correction and sender rule. Categories return to the built-in defaults.", "Reset",
            onConfirm = { vm.launch { engine.resetLearning() }; ReanalyzeWorker.enqueue(context) }, onDismiss = { confirmReset = false }, destructive = true,
        )
    }
    if (selfHostedDialog) {
        var url by remember { mutableStateOf(s.selfHostedAiUrl.ifBlank { "http://192.168.1.10:11434" }) }
        var model by remember { mutableStateOf(s.selfHostedAiModel.ifBlank { "llama3.2" }) }
        AlertDialog(
            onDismissRequest = { selfHostedDialog = false },
            title = { Text("Self-hosted AI server") },
            text = {
                Column {
                    Text("When enabled, the text of the conversation you summarise or the draft you rewrite is sent to this address. Use a server you control.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(url, { url = it }, label = { Text("Server URL") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                    OutlinedTextField(model, { model = it }, label = { Text("Model name") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.update { it.copy(selfHostedAiEnabled = url.isNotBlank(), selfHostedAiUrl = url.trim(), selfHostedAiModel = model.trim()) }
                    selfHostedDialog = false
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { vm.update { it.copy(selfHostedAiEnabled = false) }; selfHostedDialog = false }) { Text("Turn off") }
            },
        )
    }
}
