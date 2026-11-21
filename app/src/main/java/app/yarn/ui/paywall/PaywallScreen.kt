package app.yarn.ui.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yarn.R
import app.yarn.billing.Access
import app.yarn.billing.Licence
import app.yarn.ui.common.YarnLogo
import app.yarn.ui.common.container
import kotlinx.coroutines.launch

/**
 * Yarn's plan: the free trial, the yearly subscription, and what happens after the trial. Shown on
 * its own once the trial ends without a purchase, or opened from the profile page.
 */
@Composable
fun PaywallScreen(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val licence = remember { context.container.licence }
    val access by licence.access.collectAsStateWithLifecycle()
    val price by licence.price.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<Int?>(null) }
    var redeemHelp by remember { mutableStateOf(false) }
    LaunchedEffect(licence) {
        licence.events.collect { busy = false; note = R.string.plan_failed }
    }
    fun open(intent: Intent) = runCatching { context.startActivity(intent) }.isSuccess
    PaywallContent(
        access = access, price = price, busy = busy, note = note?.let { stringResource(it) },
        onSubscribe = {
            val activity = context.findActivity() ?: return@PaywallContent
            scope.launch {
                busy = true
                note = null
                val started = licence.buy(activity)
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
                    a is Access.Unlocked || a == Access.Pending || (a is Access.Trial && a.pending) -> null
                    else -> R.string.plan_nothing_found
                }
            }
        },
        onRedeem = { redeemHelp = true },
        onManage = { open(Licence.manageIntent(context)) },
        onOtherApp = { open(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) },
        onBack = onBack,
    )
    if (redeemHelp) androidx.compose.material3.AlertDialog(
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
    price: Licence.Price?,
    busy: Boolean,
    note: String?,
    onSubscribe: () -> Unit,
    onRestore: () -> Unit,
    onRedeem: () -> Unit,
    onManage: () -> Unit,
    onOtherApp: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val subtle = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(Modifier.fillMaxSize(), color = app.yarn.ui.settings.settingsPageColor(), contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                if (onBack != null) IconButton(onClick = onBack, Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            }
            YarnLogo(64.dp)
            Spacer(Modifier.height(14.dp))
            val (title, subtitle) = words(access)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = subtle, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(18.dp))

            if (access !is Access.Unlocked) {
                PriceCard(price)
                Spacer(Modifier.height(16.dp))
                Feature(Icons.Outlined.Category, stringResource(R.string.plan_feature_tabs))
                Feature(Icons.Outlined.Storefront, stringResource(R.string.plan_feature_logos))
                Feature(Icons.Outlined.GppGood, stringResource(R.string.plan_feature_scams))
                Feature(Icons.Outlined.AutoAwesome, stringResource(R.string.plan_feature_ai))
                Feature(Icons.Outlined.Backup, stringResource(R.string.plan_feature_backup))
                Spacer(Modifier.height(18.dp))
            }

            val pending = access == Access.Pending || (access as? Access.Trial)?.pending == true
            when {
                access is Access.Unlocked && !access.lifetime -> Button(onClick = onManage, Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.plan_manage))
                }
                access is Access.Unlocked -> Unit
                pending -> Button(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    ButtonText(busy, stringResource(R.string.plan_check_again))
                }
                else -> Button(onClick = onSubscribe, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    ButtonText(busy, price?.let { stringResource(R.string.plan_subscribe_for, it.formatted) } ?: stringResource(R.string.plan_subscribe))
                }
            }
            note?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            }
            if (access !is Access.Unlocked) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onRestore, enabled = !busy) { Text(stringResource(R.string.plan_restore)) }
                    TextButton(onClick = onRedeem) { Text(stringResource(R.string.plan_redeem)) }
                }
            }
            if (!access.usable) {
                TextButton(onClick = onOtherApp) { Text(stringResource(R.string.plan_other_app)) }
                Text(stringResource(R.string.plan_messages_safe), style = MaterialTheme.typography.bodySmall, color = subtle, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun words(access: Access): Pair<String, String> = when (access) {
    is Access.Trial -> stringResource(R.string.plan_trial_title) to
        if (access.pending) stringResource(R.string.plan_pending_sub)
        else pluralStringResource(R.plurals.plan_trial_left, access.daysLeft, access.daysLeft)
    is Access.Unlocked -> if (access.lifetime) stringResource(R.string.plan_lifetime_title) to stringResource(R.string.plan_lifetime_sub)
        else stringResource(R.string.plan_subscribed_title) to stringResource(R.string.plan_subscribed_sub)
    Access.Pending -> stringResource(R.string.plan_pending_title) to stringResource(R.string.plan_pending_sub)
    Access.Expired -> stringResource(R.string.plan_expired_title) to stringResource(R.string.plan_expired_sub)
}

/** The yearly price, its early-bird discount, and how renewing and cancelling work. */
@Composable
private fun PriceCard(price: Licence.Price?) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(app.yarn.ui.settings.settingsCardColor()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val regular = price?.regular
        if (regular != null) {
            Text(
                stringResource(R.string.plan_early_bird),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
        }
        if (price != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(price.formatted, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.plan_per_year), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                if (regular != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        regular + stringResource(R.string.plan_per_year),
                        style = MaterialTheme.typography.titleMedium.copy(textDecoration = TextDecoration.LineThrough),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
        }
        Text(
            stringResource(R.string.plan_terms),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
