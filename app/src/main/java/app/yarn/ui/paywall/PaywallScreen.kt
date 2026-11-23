package app.yarn.ui.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yarn.R
import app.yarn.billing.Access
import app.yarn.billing.Licence
import app.yarn.billing.ProPlan
import app.yarn.ui.common.YarnLogo
import app.yarn.ui.common.container
import kotlinx.coroutines.launch

/**
 * Yarn Pro: the 30-day trial, the free plan after it, and buying Pro yearly or for life. Opened from
 * the profile page or a locked feature, and shown once when the trial ends. Yarn never locks:
 * without Pro it keeps working as Yarn Free.
 */
@Composable
fun PaywallScreen(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val licence = remember { context.container.licence }
    val access by licence.access.collectAsStateWithLifecycle()
    val prices by licence.prices.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var plan by rememberSaveable { mutableStateOf(ProPlan.YEARLY) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<Int?>(null) }
    var redeemHelp by remember { mutableStateOf(false) }
    LaunchedEffect(licence) {
        licence.events.collect { busy = false; note = R.string.plan_failed }
    }
    LaunchedEffect(Unit) { licence.refresh() }
    fun open(intent: Intent) = runCatching { context.startActivity(intent) }.isSuccess
    PaywallContent(
        access = access, prices = prices, plan = plan, onPlan = { plan = it }, busy = busy, note = note?.let { stringResource(it) },
        onBuy = {
            val activity = context.findActivity() ?: return@PaywallContent
            scope.launch {
                busy = true
                note = null
                val started = licence.buy(activity, plan)
                busy = false
                if (started != Licence.Result.STARTED) note = R.string.plan_unavailable
            }
        },
        onRestore = {
            scope.launch {
                busy = true
                note = null
                val reached = licence.refresh()
                busy = false
                val a = licence.access.value
                note = when {
                    !reached -> R.string.plan_unavailable
                    a is Access.Pro || (a as? Access.Free)?.pending == true || (a as? Access.Trial)?.pending == true -> null
                    else -> R.string.plan_nothing_found
                }
            }
        },
        onRedeem = { redeemHelp = true },
        onManage = { open(Licence.manageIntent(context)) },
        onBack = onBack,
    )
    if (redeemHelp) AlertDialog(
        onDismissRequest = { redeemHelp = false },
        title = { Text(stringResource(R.string.plan_redeem)) },
        text = { Text(stringResource(R.string.plan_redeem_steps)) },
        confirmButton = {
            TextButton(onClick = {
                redeemHelp = false
                val activity = context.findActivity() ?: return@TextButton
                scope.launch {
                    note = null
                    // Google's sheet is the sure way; the Play Store's own page is the fallback.
                    if (licence.redeem(activity) != Licence.Result.STARTED && !open(Licence.redeemIntent())) open(Licence.redeemOnWeb())
                }
            }) { Text(stringResource(R.string.continue_)) }
        },
        dismissButton = { TextButton(onClick = { redeemHelp = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun PaywallContent(
    access: Access,
    prices: Licence.Prices,
    plan: ProPlan,
    onPlan: (ProPlan) -> Unit,
    busy: Boolean,
    note: String?,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onRedeem: () -> Unit,
    onManage: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val subtle = MaterialTheme.colorScheme.onSurfaceVariant
    val pending = (access as? Access.Free)?.pending == true || (access as? Access.Trial)?.pending == true
    Surface(Modifier.fillMaxSize(), color = app.yarn.ui.settings.settingsPageColor(), contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                if (onBack != null) IconButton(onClick = onBack, Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            }
            Box {
                YarnLogo(64.dp)
                ProBadge(Modifier.align(Alignment.BottomEnd))
            }
            Spacer(Modifier.height(14.dp))
            val (title, subtitle) = words(access, pending)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = subtle, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(18.dp))

            if (access !is Access.Pro) {
                if (prices.yearly?.regular != null || prices.lifetime?.regular != null) {
                    Text(
                        stringResource(R.string.plan_early_bird),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                }
                PlanCard(
                    selected = plan == ProPlan.YEARLY, title = stringResource(R.string.pro_yearly), price = prices.yearly,
                    unit = stringResource(R.string.plan_per_year), note = stringResource(R.string.pro_yearly_note),
                ) { onPlan(ProPlan.YEARLY) }
                Spacer(Modifier.height(10.dp))
                PlanCard(
                    selected = plan == ProPlan.LIFETIME, title = stringResource(R.string.pro_lifetime), price = prices.lifetime,
                    unit = null, note = stringResource(R.string.pro_lifetime_note),
                ) { onPlan(ProPlan.LIFETIME) }
                Spacer(Modifier.height(16.dp))
            }

            Feature(Icons.Outlined.Category, stringResource(R.string.pro_f_tabs))
            Feature(Icons.Outlined.GppGood, stringResource(R.string.pro_f_spam))
            Feature(Icons.Outlined.Storefront, stringResource(R.string.pro_f_logos))
            Feature(Icons.Outlined.Backup, stringResource(R.string.pro_f_backup))
            Feature(Icons.Outlined.AutoAwesome, stringResource(R.string.pro_f_ai))
            Feature(Icons.Outlined.CleaningServices, stringResource(R.string.pro_f_clean))
            Feature(Icons.Outlined.NotificationsActive, stringResource(R.string.pro_f_notify))
            Feature(Icons.Outlined.Schedule, stringResource(R.string.pro_f_schedule))
            Feature(Icons.Outlined.DeleteOutline, stringResource(R.string.pro_f_bin))
            Feature(Icons.Outlined.Palette, stringResource(R.string.pro_f_look))
            Text(
                stringResource(R.string.pro_free_always),
                style = MaterialTheme.typography.bodySmall, color = subtle, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, bottom = 18.dp),
            )
        }
        // The button stays in reach under the list, with restoring and redeeming beside it.
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                access is Access.Pro && !access.lifetime -> Button(onClick = onManage, Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.plan_manage))
                }
                access is Access.Pro -> Unit
                pending -> Button(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    ButtonText(busy, stringResource(R.string.plan_check_again))
                }
                else -> Button(onClick = onBuy, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    val price = if (plan == ProPlan.YEARLY) prices.yearly else prices.lifetime
                    ButtonText(
                        busy,
                        when {
                            price == null -> stringResource(R.string.pro_get)
                            plan == ProPlan.YEARLY -> stringResource(R.string.pro_get_yearly, price.formatted)
                            else -> stringResource(R.string.pro_get_lifetime, price.formatted)
                        },
                    )
                }
            }
            note?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            }
            if (access !is Access.Pro) {
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onRestore, enabled = !busy) { Text(stringResource(R.string.plan_restore)) }
                    TextButton(onClick = onRedeem) { Text(stringResource(R.string.plan_redeem)) }
                }
            }
        }
        }
    }
}

@Composable
private fun words(access: Access, pending: Boolean): Pair<String, String> = when (access) {
    is Access.Trial -> stringResource(R.string.pro_trial_title) to
        if (pending) stringResource(R.string.plan_pending_sub)
        else pluralStringResource(R.plurals.pro_trial_left, access.daysLeft, access.daysLeft)
    is Access.Pro -> if (access.lifetime) stringResource(R.string.pro_life_title) to stringResource(R.string.plan_lifetime_sub)
        else stringResource(R.string.pro_yours_title) to stringResource(R.string.plan_subscribed_sub)
    is Access.Free -> if (pending) stringResource(R.string.plan_pending_title) to stringResource(R.string.plan_pending_sub)
        else stringResource(R.string.pro_free_title) to stringResource(R.string.pro_free_sub)
}

/** One way to buy Pro: its price, the early-bird discount, and how it's charged. */
@Composable
private fun PlanCard(selected: Boolean, title: String, price: Licence.Price?, unit: String?, note: String, onClick: () -> Unit) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(app.yarn.ui.settings.settingsCardColor())
            .border(if (selected) 2.dp else 1.dp, outline, RoundedCornerShape(20.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (price != null) Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(price.formatted, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (unit != null) Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
            }
            price.regular?.let {
                Text(
                    it, style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The small "PRO" tag used on locked features and on the Pro page. */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.pro_badge),
        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ButtonText(busy: Boolean, text: String) {
    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onPrimary)
    else Text(text, style = MaterialTheme.typography.titleMedium)
}

private fun Context.findActivity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
