package app.yarn.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import app.yarn.telephony.DefaultSmsApp

/**
 * Yarn asks for one permission itself: notifications (Android 13+). Becoming the default SMS app
 * already gives it SMS, contacts (for names) and SIM details; photos and files come through
 * Android's own pickers, which need no permission at all.
 */
object NotificationPermission {
    fun needed(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
}

/** Asks to become the default SMS app, then (once that's done) for notifications. [onDone] runs after both. */
@Composable
fun rememberSetDefaultApp(context: Context, onDone: () -> Unit): () -> Unit {
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (DefaultSmsApp.isDefault(context) && NotificationPermission.needed(context)) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onDone()
        }
    }
    return { DefaultSmsApp.requestIntent(context)?.let { role.launch(it) } }
}
