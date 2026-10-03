package app.yarn.ui.onboarding

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yarn.data.prefs.AppSettings
import app.yarn.i18n.AppLanguage
import app.yarn.telephony.DefaultSmsApp
import app.yarn.ui.common.YarnLogo
import app.yarn.ui.common.container
import app.yarn.ui.theme.LocalDarkTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import app.yarn.R

/** What the user picked on the stringResource(R.string.ob_clean) step. */
data class AutoCleanChoice(val codes: Boolean = true, val offers: Boolean = true, val spam: Boolean = true) {
    fun applyTo(s: AppSettings) = s.copy(
        otpAutoDeleteMinutes = if (codes) 24 * 60 else 0,
        promoAutoDeleteDays = if (offers) 30 else 0,
        spamAutoDeleteDays = if (spam) 30 else 0,
    )
}

/** Welcome → default SMS app → permissions → auto-clean. */
@Composable
fun OnboardingScreen(onDone: (AutoCleanChoice?) -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(0) }
    var isDefault by remember { mutableStateOf(DefaultSmsApp.isDefault(context)) }
    var clean by remember { mutableStateOf(AutoCleanChoice()) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = DefaultSmsApp.isDefault(context)
        context.container.refreshPlatformState()
        if (isDefault) step = 2
    }
    val permissions = buildList {
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_PHONE_NUMBERS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        if (!isDefault) add(Manifest.permission.READ_SMS)
    }.toTypedArray()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        context.container.refreshPlatformState()
        step = 3
    }
    OnboardingContent(
        step = step,
        isDefault = isDefault,
        language = AppLanguage.selected(context),
        onLanguage = { tag -> (context as? Activity)?.let { AppLanguage.select(it, tag) } },
        clean = clean,
        onClean = { clean = it },
        onNext = { step = it },
        onSetDefault = { DefaultSmsApp.requestIntent(context)?.let { roleLauncher.launch(it) } },
        onPermissions = { permissionLauncher.launch(permissions) },
        onFinish = onDone,
    )
}

@Composable
fun OnboardingContent(
    step: Int,
    isDefault: Boolean,
    language: String,
    onLanguage: (String) -> Unit,
    clean: AutoCleanChoice,
    onClean: (AutoCleanChoice) -> Unit,
    onNext: (Int) -> Unit,
    onSetDefault: () -> Unit,
    onPermissions: () -> Unit,
    onFinish: (AutoCleanChoice?) -> Unit,
) {
    val dark = LocalDarkTheme.current
    val bg = if (dark) Brush.verticalGradient(listOf(Color(0xFF0B1B3F), Color(0xFF000000)))
    else Brush.verticalGradient(listOf(Color(0xFFDCE8FF), Color(0xFFFFFFFF), Color(0xFFFFFFFF)))
    Box(Modifier.fillMaxSize().background(bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            StepDots(step, total = 4, modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally))
            AnimatedContent(
                step,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(320)) { it / 4 * dir } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(240)) { -it / 4 * dir } + fadeOut(tween(200)))
                },
                modifier = Modifier.weight(1f),
                label = "onboarding",
            ) { s ->
                when (s) {
                    0 -> Page(
                        hero = { YarnLogo(size = 96.dp) },
                        title = stringResource(R.string.ob_welcome),
                        body = stringResource(R.string.ob_welcome_body),
                        primary = stringResource(R.string.get_started) to { onNext(1) },
                    ) {
                        LanguagePicker(language, onLanguage)
                        Spacer(Modifier.height(18.dp))
                        Feature(Icons.Outlined.AutoAwesome, Color(0xFF1A73E8), stringResource(R.string.ob_sorted), stringResource(R.string.ob_sorted_body))
                        Feature(Icons.Outlined.GppGood, Color(0xFF188038), stringResource(R.string.ob_scam), stringResource(R.string.ob_scam_body))
                        Feature(Icons.Outlined.Lock, Color(0xFF7C5CFF), stringResource(R.string.ob_private), stringResource(R.string.ob_private_body))
                    }
                    1 -> Page(
                        hero = { HeroIcon(Icons.Outlined.CheckCircle, Color(0xFF1A73E8)) },
                        title = stringResource(R.string.make_default_title),
                        body = stringResource(R.string.ob_default_body),
                        primary = if (isDefault) stringResource(R.string.continue_) to { onNext(2) } else stringResource(R.string.set_default_sms) to onSetDefault,
                        secondary = if (isDefault) null else stringResource(R.string.not_now) to { onNext(2) },
                    ) {
                        if (isDefault) {
                            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF188038))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.ob_is_default), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    2 -> Page(
                        hero = { HeroIcon(Icons.Outlined.Contacts, Color(0xFF00A3A3)) },
                        title = stringResource(R.string.ob_permissions),
                        body = stringResource(R.string.ob_permissions_body),
                        primary = stringResource(R.string.continue_) to onPermissions,
                        secondary = stringResource(R.string.skip) to { onNext(3) },
                    ) {
                        Feature(Icons.Outlined.Contacts, Color(0xFF00A3A3), stringResource(R.string.contacts), stringResource(R.string.ob_contacts_body))
                        Feature(Icons.Outlined.SimCard, Color(0xFFE8710A), stringResource(R.string.phone), stringResource(R.string.ob_phone_body))
                        Feature(Icons.Outlined.Notifications, Color(0xFFF2453D), stringResource(R.string.notifications), stringResource(R.string.ob_notif_body))
                    }
                    else -> Page(
                        hero = { HeroIcon(Icons.Outlined.CleaningServices, Color(0xFF00A86B)) },
                        title = stringResource(R.string.ob_clean),
                        body = stringResource(R.string.ob_clean_body),
                        primary = stringResource(R.string.turn_on_auto_clean) to { onFinish(clean) },
                        secondary = stringResource(R.string.not_now) to { onFinish(null) },
                    ) {
                        CleanToggle(Icons.Outlined.Password, Color(0xFF7C5CFF), stringResource(R.string.junk_codes), pluralStringResource(R.plurals.deleted_after_days, 1, 1), clean.codes) { onClean(clean.copy(codes = it)) }
                        CleanToggle(Icons.Outlined.LocalOffer, Color(0xFFFF9500), stringResource(R.string.offers_promotions), pluralStringResource(R.plurals.deleted_after_days, 30, 30), clean.offers) { onClean(clean.copy(offers = it)) }
                        CleanToggle(Icons.Outlined.Report, Color(0xFFF2453D), stringResource(R.string.verdict_spam), pluralStringResource(R.plurals.deleted_after_days, 30, 30), clean.spam) { onClean(clean.copy(spam = it)) }
                        Text(
                            stringResource(R.string.ob_clean_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Page(
    hero: @Composable () -> Unit,
    title: String,
    body: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(36.dp))
            hero()
            Spacer(Modifier.height(24.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp),
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(10.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(26.dp))
            Column(Modifier.fillMaxWidth(), content = content)
            Spacer(Modifier.height(16.dp))
        }
        Button(onClick = primary.second, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp)) {
            Text(primary.first, style = MaterialTheme.typography.titleMedium)
        }
        if (secondary != null) {
            TextButton(onClick = secondary.second, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(48.dp)) {
                Text(secondary.first, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Spacer(Modifier.height(52.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StepDots(step: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            val w by animateDpAsState(if (i == step) 22.dp else 7.dp, label = "dot")
            Box(
                Modifier.height(7.dp).width(w).clip(CircleShape)
                    .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun HeroIcon(icon: ImageVector, tint: Color) {
    Box(Modifier.size(120.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(42.dp))
        }
    }
}

/** English / हिन्दी, written in their own script so anyone can find theirs. */
@Composable
private fun LanguagePicker(selected: String, onSelect: (String) -> Unit) {
    val current = selected.ifEmpty { java.util.Locale.getDefault().language.takeIf { it in AppLanguage.supported } ?: "en" }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
            Icon(Icons.Outlined.Language, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text("Language · भाषा", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppLanguage.supported.forEach { tag ->
                val on = tag == current
                val shape = RoundedCornerShape(18.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(shape)
                        .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(if (on) 2.dp else 0.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
                        .selectable(selected = on, role = Role.RadioButton) { onSelect(tag) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        AppLanguage.nativeName(tag),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, tint: Color, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = tint)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CleanToggle(icon: ImageVector, tint: Color, title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val card = if (LocalDarkTheme.current) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow
    Surface(shape = RoundedCornerShape(18.dp), color = card, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(tint), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = null, modifier = Modifier.scale(0.85f))
        }
    }
}
