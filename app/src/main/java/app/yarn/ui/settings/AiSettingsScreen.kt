package app.yarn.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Quickreply
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yarn.intelligence.SpamSensitivity
import app.yarn.ui.common.ConfirmDialog
import app.yarn.work.ReanalyzeWorker
import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

/**
 * Deliberately simple: the engines behind these switches (rules, learning, ML Kit and Gemini Nano
 * where the phone has it) are chosen automatically, so people only decide *what* Yarn does.
 */
@Composable
fun AiSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    val on = s.aiEnabled

    SettingsScaffold("Smart features", onBack) {
        item {
            SettingsGroup {
                SwitchRow("Smart features", "Sorting, spam protection and suggestions", s.aiEnabled, icon = Icons.Outlined.AutoAwesome, tint = Color(0xFF1466F0)) { v ->
                    vm.update { it.copy(aiEnabled = v) }
                }
            }
        }
        item { SectionHeader("Organise") }
        item {
            SettingsGroup {
                SwitchRow("Smart sorting", "Personal, Transactions, Updates and Offers", s.autoCategorize, enabled = on, icon = Icons.Outlined.Category, tint = Color(0xFF7C5CFF)) { v ->
                    vm.update { it.copy(autoCategorize = v) }
                }
                SwitchRow("Spam protection", "Moves scams and spam to the Spam folder", s.spamFilter, enabled = on, icon = Icons.Outlined.GppGood, tint = Color(0xFF34C759)) { v ->
                    vm.update { it.copy(spamFilter = v) }
                }
                if (on && s.spamFilter) {
                    ChoiceRow(
                        "Filter strength",
                        listOf(SpamSensitivity.LOW to "Relaxed", SpamSensitivity.MEDIUM to "Balanced", SpamSensitivity.HIGH to "Strict"),
                        s.spamSensitivity,
                        icon = Icons.Outlined.Tune, tint = Color(0xFF8E8E93),
                    ) { v -> vm.update { it.copy(spamSensitivity = v) }; ReanalyzeWorker.enqueue(context) }
                }
            }
        }
        item { SectionHeader("Assist") }
        item {
            SettingsGroup {
                SwitchRow("Smart replies", "Quick reply suggestions in chats", s.smartReplies, enabled = on, icon = Icons.Outlined.Quickreply, tint = Color(0xFF0FA3B1)) { v ->
                    vm.update { it.copy(smartReplies = v) }
                }
                val langs = remember { TranslateLanguage.getAllLanguages().map { it to Locale.forLanguageTag(it).displayLanguage }.sortedBy { it.second } }
                ChoiceRow("Translate into", langs, s.translateTarget, icon = Icons.Outlined.Translate, tint = Color(0xFFFF9500)) { v -> vm.update { it.copy(translateTarget = v) } }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
        item {
            SettingsGroup {
                ClickRow("Forget what Yarn learned", destructive = true) { confirmReset = true }
            }
        }
        item {
            Text(
                "Everything runs privately on your phone. Yarn learns when you move a message or mark spam, and uses your phone's built-in AI when available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
            )
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            "Forget what Yarn learned?", "Your corrections and sender preferences will be cleared and messages re-sorted.", "Forget",
            onConfirm = { vm.launch { engine.resetLearning() }; ReanalyzeWorker.enqueue(context) }, onDismiss = { confirmReset = false }, destructive = true,
        )
    }
}
