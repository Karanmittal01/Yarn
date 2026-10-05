package app.yarn.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import app.yarn.data.prefs.AppSettings
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
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.foundation.layout.padding
import app.yarn.R

/**
 * Deliberately simple: the engines behind these switches (rules, learning, ML Kit and Gemini Nano
 * where the phone has it) are chosen automatically, so people only decide *what* Yarn does.
 */
@Composable
fun AiSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    val job by vm.c.progress.job.collectAsStateWithLifecycle()
    val started = stringResource(R.string.resort_started)
    SettingsScaffold(stringResource(R.string.smart_features), onBack) {
        smartFeatureItems(
            s, update = { t -> vm.update(t) }, onRecheck = { ReanalyzeWorker.enqueue(context) }, onReset = { confirmReset = true },
            job = job,
            onResort = {
                // Fresh look at everything: the on-device model gets to re-check what it sorted before.
                vm.launch { db.messages().resetModelCategories() }
                ReanalyzeWorker.enqueue(context)
                android.widget.Toast.makeText(context, started, android.widget.Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            stringResource(R.string.forget_q), stringResource(R.string.forget_text), stringResource(R.string.forget),
            onConfirm = { vm.launch { engine.resetLearning() }; ReanalyzeWorker.enqueue(context) }, onDismiss = { confirmReset = false }, destructive = true,
        )
    }
}

/** The Smart features rows, split out so they can be rendered in screenshot tests. */
internal fun LazyListScope.smartFeatureItems(
    s: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
    onRecheck: () -> Unit,
    onReset: () -> Unit,
    job: app.yarn.work.JobProgress? = null,
    onResort: () -> Unit = {},
) {
    val on = s.aiEnabled
    item {
        SettingsGroup {
            SwitchRow(stringResource(R.string.smart_features), stringResource(R.string.smart_features_sub), s.aiEnabled, icon = Icons.Outlined.AutoAwesome, tint = Color(0xFF1466F0)) { v ->
                update { it.copy(aiEnabled = v) }
            }
        }
    }
    item { SectionHeader(stringResource(R.string.organise)) }
    item {
        SettingsGroup {
            SwitchRow(stringResource(R.string.smart_sorting), stringResource(R.string.smart_sorting_sub), s.autoCategorize, enabled = on, icon = Icons.Outlined.Category, tint = Color(0xFF7C5CFF)) { v ->
                update { it.copy(autoCategorize = v) }
            }
            SwitchRow(stringResource(R.string.spam_protection), stringResource(R.string.spam_protection_sub), s.spamFilter, enabled = on, icon = Icons.Outlined.GppGood, tint = Color(0xFF34C759)) { v ->
                update { it.copy(spamFilter = v) }
            }
            if (on && s.spamFilter) {
                ChoiceRow(
                    stringResource(R.string.filter_strength),
                    listOf(SpamSensitivity.LOW to stringResource(R.string.strength_relaxed), SpamSensitivity.MEDIUM to stringResource(R.string.strength_balanced), SpamSensitivity.HIGH to stringResource(R.string.strength_strict)),
                    s.spamSensitivity,
                    icon = Icons.Outlined.Tune, tint = Color(0xFF8E8E93),
                ) { v -> update { it.copy(spamSensitivity = v) }; onRecheck() }
            }
        }
    }
    item { SectionHeader(stringResource(R.string.assist)) }
    item {
        SettingsGroup {
            SwitchRow(stringResource(R.string.smart_replies), stringResource(R.string.smart_replies_sub), s.smartReplies, enabled = on, icon = Icons.Outlined.Quickreply, tint = Color(0xFF0FA3B1)) { v ->
                update { it.copy(smartReplies = v) }
            }
            val sameAsApp = stringResource(R.string.same_as_app_language)
            val uiLocale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
            val langs = remember(sameAsApp, uiLocale) {
                listOf("" to sameAsApp) + TranslateLanguage.getAllLanguages().map { it to Locale.forLanguageTag(it).getDisplayLanguage(uiLocale) }.sortedBy { it.second }
            }
            ChoiceRow(stringResource(R.string.translate_into), langs, s.translateTarget, icon = Icons.Outlined.Translate, tint = Color(0xFFFF9500)) { v -> update { it.copy(translateTarget = v) } }
        }
    }
    item { Spacer(Modifier.height(20.dp)) }
    item {
        SettingsGroup {
            if (job != null) {
                app.yarn.ui.common.JobProgressBar(job, Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
            } else {
                ClickRow(stringResource(R.string.resort_all), stringResource(R.string.resort_all_sub), icon = Icons.Outlined.Refresh, tint = Color(0xFF7C5CFF), onClick = onResort)
            }
        }
    }
    item { Spacer(Modifier.height(12.dp)) }
    item {
        SettingsGroup {
            ClickRow(stringResource(R.string.forget_learned), destructive = true) { onReset() }
        }
    }
    item {
        Text(
            stringResource(R.string.ai_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
        )
    }
}
