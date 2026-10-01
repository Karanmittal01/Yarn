package app.yarn.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yarn.telephony.DefaultSmsApp
import app.yarn.ui.common.container

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var isDefault by remember { mutableStateOf(DefaultSmsApp.isDefault(context)) }
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
        onDone()
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            when (step) {
                0 -> {
                    Text("Welcome to Yarn", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("Text messaging that organises itself — privately, on your phone.", style = MaterialTheme.typography.titleMedium)
                    Feature(Icons.Outlined.CloudOff, "Works without internet", "SMS and MMS go over your mobile network. No account, no servers.")
                    Feature(Icons.Outlined.AutoAwesome, "On-device intelligence", "Automatic categories, OTP copy, summaries and smart replies run on your phone.")
                    Feature(Icons.Outlined.Shield, "Scam protection", "Phishing links and impersonation attempts are flagged before you tap.")
                    Feature(Icons.Outlined.Lock, "Encrypted storage", "Yarn's database is encrypted with a key that never leaves this device.")
                    Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
                }
                1 -> {
                    Text("Make Yarn your SMS app", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Android only lets the default messaging app send, receive and organise texts. " +
                            "You can switch back at any time in system settings — your messages stay in the system store.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (isDefault) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Text("  Yarn is your default SMS app")
                        }
                        Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
                    } else {
                        Button(onClick = { DefaultSmsApp.requestIntent(context)?.let { roleLauncher.launch(it) } }, modifier = Modifier.fillMaxWidth()) {
                            Text("Set as default SMS app")
                        }
                        TextButton(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Not now (read-only)") }
                    }
                }
                else -> {
                    Text("A few permissions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Feature(Icons.Outlined.CheckCircle, "Contacts", "Show names and photos instead of numbers. Contacts never leave your phone.")
                    Feature(Icons.Outlined.CheckCircle, "Phone", "Choose the right SIM on dual-SIM phones and handle roaming for MMS.")
                    Feature(Icons.Outlined.CheckCircle, "Notifications", "Alert you to new messages, with reply and copy-code buttons.")
                    Button(onClick = { permissionLauncher.launch(permissions) }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
                    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
                }
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
