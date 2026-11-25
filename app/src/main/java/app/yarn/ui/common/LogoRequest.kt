package app.yarn.ui.common

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import app.yarn.BuildConfig
import app.yarn.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Asking for a missing logo in one tap: Yarn texts the sender's name and ID to its developer from
 * the phone's own SIM. Only those two are sent, never a message. Each sender is asked for once.
 */
object LogoRequests {
    enum class Outcome { SENT, ALREADY, LIMIT, FAILED }

    /** A day's requests at most, so a slip of the finger never sends a pile of texts. */
    private const val DAILY_LIMIT = 10
    private const val DAY_MS = 86_400_000L

    private fun prefs(context: Context) = context.getSharedPreferences("logo_requests", Context.MODE_PRIVATE)

    /** "CP-APLPHR-S" and "APLPHR" are the same sender: the six letters in the middle. */
    fun key(address: String): String {
        val parts = address.trim().uppercase().split('-').filter { it.isNotBlank() }
        return (if (parts.size >= 2) parts[1] else parts.firstOrNull()).orEmpty()
    }

    fun wasRequested(context: Context, address: String): Boolean =
        key(address) in prefs(context).getStringSet(K_SENT, emptySet()).orEmpty()

    /** The text that is sent: the sender and Yarn's version, in plain letters so it fits one SMS. */
    fun text(name: String, address: String): String {
        val id = address.trim().uppercase()
        val who = if (name.isBlank() || name.equals(id, ignoreCase = true) || name.equals(key(address), ignoreCase = true)) id else "$name ($id)"
        return "Yarn logo request: $who. Yarn ${BuildConfig.VERSION_CODE}".take(160)
    }

    suspend fun send(context: Context, name: String, address: String): Outcome = withContext(Dispatchers.IO) {
        val prefs = prefs(context)
        val sent = prefs.getStringSet(K_SENT, emptySet()).orEmpty()
        val key = key(address)
        if (key in sent) return@withContext Outcome.ALREADY
        val now = System.currentTimeMillis()
        val today = prefs.getStringSet(K_TIMES, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.filter { now - it < DAY_MS }
        if (today.size >= DAILY_LIMIT) return@withContext Outcome.LIMIT
        val number = Feedback.whatsAppNumber(context) ?: return@withContext Outcome.FAILED
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext Outcome.FAILED
        }
        // Counted as sent only once the phone confirms it went out (no signal, no SIM or no balance
        // all fail here), so a request that didn't go can be tried again.
        val ok = withTimeoutOrNull(SENT_TIMEOUT_MS) { sendAndConfirm(context, "+$number", text(name, address)) } ?: false
        if (!ok) return@withContext Outcome.FAILED
        prefs.edit {
            putStringSet(K_SENT, sent + key)
            putStringSet(K_TIMES, (today + now).map { it.toString() }.toSet())
        }
        Outcome.SENT
    }

    /** Sends one text and waits for the radio's answer for every part: true when all went out. */
    private suspend fun sendAndConfirm(context: Context, to: String, text: String): Boolean = suspendCancellableCoroutine { cont ->
        val app = context.applicationContext
        val action = "${app.packageName}.LOGO_REQUEST_SENT.${System.nanoTime()}"
        var remaining = 0
        var failed = false
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (resultCode != Activity.RESULT_OK) failed = true
                if (--remaining <= 0) {
                    runCatching { app.unregisterReceiver(this) }
                    if (cont.isActive) cont.resume(!failed)
                }
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        cont.invokeOnCancellation { runCatching { app.unregisterReceiver(receiver) } }
        try {
            val sims = app.container.sims
            val sms = sims.smsManager(sims.resolveSubId(-1))
            val parts = sms.divideMessage(text)
            remaining = parts.size
            val sent = ArrayList(parts.indices.map { i ->
                PendingIntent.getBroadcast(
                    app, i, Intent(action).setPackage(app.packageName),
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE,
                )
            })
            if (parts.size == 1) sms.sendTextMessage(to, null, parts[0], sent[0], null)
            else sms.sendMultipartTextMessage(to, null, parts, sent, null)
        } catch (e: Exception) {
            runCatching { app.unregisterReceiver(receiver) }
            if (cont.isActive) cont.resume(false)
        }
    }

    private const val SENT_TIMEOUT_MS = 60_000L
    private const val K_SENT = "sent"
    private const val K_TIMES = "times"
}

/**
 * Confirms, then sends the request for [address]'s logo. Shows nothing if it was already asked for,
 * just a note saying so.
 */
@Composable
fun LogoRequestDialog(name: String, address: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    if (LogoRequests.wasRequested(context, address)) {
        androidx.compose.runtime.LaunchedEffect(address) {
            Toast.makeText(context, R.string.logo_already_requested, Toast.LENGTH_SHORT).show()
            onDismiss()
        }
        return
    }
    val shown = name.ifBlank { address }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.logo_request_title, shown)) },
        text = { Text(stringResource(R.string.logo_request_body, address.trim().uppercase())) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                // Yarn's own scope: the dialog is already gone when the text goes out.
                val app = context.applicationContext
                Toast.makeText(app, R.string.logo_request_sending, Toast.LENGTH_SHORT).show()
                app.container.scope.launch(Dispatchers.Main) {
                    val note = when (LogoRequests.send(app, name, address)) {
                        LogoRequests.Outcome.SENT -> R.string.logo_requested
                        LogoRequests.Outcome.ALREADY -> R.string.logo_already_requested
                        LogoRequests.Outcome.LIMIT -> R.string.logo_request_limit
                        LogoRequests.Outcome.FAILED -> R.string.logo_request_failed
                    }
                    Toast.makeText(app, note, Toast.LENGTH_SHORT).show()
                }
            }) { Text(stringResource(R.string.logo_request_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
