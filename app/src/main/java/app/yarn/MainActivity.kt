package app.yarn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.yarn.data.prefs.AppSettings
import app.yarn.messaging.MediaTools
import app.yarn.ui.ExternalNav
import app.yarn.ui.YarnNavHost
import app.yarn.ui.conversation.SharePayload
import app.yarn.ui.onboarding.OnboardingScreen
import app.yarn.ui.theme.YarnTheme
import app.yarn.work.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource

class MainActivity : FragmentActivity() {
    private val container get() = (application as YarnApplication).container
    private val externalNav = MutableStateFlow<ExternalNav?>(null)
    private var locked by mutableStateOf(false)
    /** Nothing is drawn until Yarn knows whether to show the lock screen, so the inbox never flashes. */
    private var lockDecided by mutableStateOf(false)
    private lateinit var updates: InAppUpdates

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(app.yarn.i18n.AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        updates = InAppUpdates(this).also { it.register() }
        lifecycleScope.launch {
            settingsSnapshot = container.settings.current()
            // A new process (first launch, or Android closed Yarn in the background) always asks again.
            if (settingsSnapshot.appLock && !unlockedInThisProcess) locked = true else unlockedInThisProcess = true
            relockIfAway(settingsSnapshot)
            lockDecided = true
            applyLockToRecents(settingsSnapshot.appLock)
            // Kept current, so returning to Yarn can lock before its first frame.
            container.settings.settings.collect { settingsSnapshot = it; applyLockToRecents(it.appLock) }
        }
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val access by container.licence.access.collectAsStateWithLifecycle()
            val s = settings
            LaunchedEffect(s?.secureScreen) {
                if (s?.secureScreen == true) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            // Accent colours and chat wallpapers are Yarn Pro; without it Yarn keeps its own look.
            val pro = access.isPro
            YarnTheme(
                mode = s?.theme ?: app.yarn.data.prefs.ThemeMode.SYSTEM, dynamicColor = s?.dynamicColor ?: false, textScale = s?.textScale ?: 1f,
                accent = if (pro) app.yarn.ui.theme.Accent.of(s?.accent) else app.yarn.ui.theme.Accent.YARN,
                wallpaper = if (pro) app.yarn.ui.theme.Wallpaper.of(s?.chatWallpaper) else app.yarn.ui.theme.Wallpaper.DEFAULT,
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        s == null || !lockDecided -> Unit
                        !s.onboardingDone -> OnboardingScreen(onDone = { clean ->
                            lifecycleScope.launch {
                                container.settings.update { (clean?.applyTo(it) ?: it).copy(onboardingDone = true) }
                                SyncWorker.enqueue(this@MainActivity)
                            }
                        })
                        locked && s.appLock -> LockScreen()
                        else -> {
                            YarnNavHost(externalNav) { externalNav.value = null }
                            // Yarn Pro: asked for by a locked feature, or once when the 30-day trial ends.
                            val askPro by container.licence.askPro.collectAsStateWithLifecycle()
                            var downgradeSeen by androidx.compose.runtime.remember { mutableStateOf(container.licence.downgradeSeen) }
                            val trialJustEnded = access is app.yarn.billing.Access.Free && !downgradeSeen
                            if (askPro || trialJustEnded) {
                                val close = {
                                    container.licence.proShown()
                                    if (trialJustEnded) { container.licence.markDowngradeSeen(); downgradeSeen = true }
                                }
                                androidx.activity.compose.BackHandler(onBack = close)
                                app.yarn.ui.paywall.PaywallScreen(onBack = close)
                            }
                        }
                    }
                    val updateReady by updates.readyToInstall.collectAsStateWithLifecycle()
                    var updateLater by androidx.compose.runtime.remember { mutableStateOf(false) }
                    if (updateReady && !updateLater && s != null && s.onboardingDone && !(locked && s.appLock)) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = {},
                            title = { Text(stringResource(R.string.update_ready)) },
                            text = { Text(stringResource(R.string.update_ready_text)) },
                            confirmButton = { androidx.compose.material3.TextButton(onClick = { updates.completeUpdate() }) { Text(stringResource(R.string.restart)) } },
                            dismissButton = { androidx.compose.material3.TextButton(onClick = { updateLater = true }) { Text(stringResource(R.string.later)) } },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Decided right away from the settings already in memory, before Yarn draws again.
        relockIfAway(settingsSnapshot)
    }

    /** Locks again once Yarn has been in the background longer than the chosen time. */
    private fun relockIfAway(s: AppSettings) {
        if (s.appLock && backgroundedAt > 0 && SystemClock.elapsedRealtime() - backgroundedAt >= s.lockTimeoutSeconds * 1000L) {
            unlockedInThisProcess = false
            locked = true
        }
    }

    /** With app lock on, the recent-apps screen shows a blank card instead of your messages. */
    private fun applyLockToRecents(appLock: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!appLock)
    }

    override fun onResume() {
        super.onResume()
        container.refreshPlatformState()
        updates.check()
        // A purchase made or a code redeemed in the Play Store unlocks Yarn on return.
        lifecycleScope.launch { container.licence.refresh() }
        // Keep the local store in sync with anything that changed while we were away.
        if (settingsSnapshot.onboardingDone) SyncWorker.enqueue(this)
    }

    override fun onDestroy() {
        updates.unregister()
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) backgroundedAt = SystemClock.elapsedRealtime()
    }

    @androidx.compose.runtime.Composable
    private fun LockScreen() {
        LaunchedEffect(Unit) { authenticate() }
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.locked), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { authenticate() }) { Text(stringResource(R.string.unlock)) }
        }
    }

    private fun authenticate() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // No screen lock configured: app lock can't be enforced, so don't trap the user.
            locked = false
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                locked = false
                unlockedInThisProcess = true
                backgroundedAt = 0
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(this.getString(R.string.unlock_yarn))
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    /** Routes notification taps, sms:/smsto: links and share-sheet content. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val data = intent.data
        when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_SENDTO -> when (data?.scheme) {
                "yarn" -> {
                    val id = data.lastPathSegment?.toLongOrNull() ?: return
                    externalNav.value = ExternalNav.OpenChat(id, data.getQueryParameter("message")?.toLongOrNull() ?: -1)
                }
                "sms", "smsto", "mms", "mmsto" -> {
                    val recipients = Uri.decode(data.schemeSpecificPart.substringBefore('?'))
                        .split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
                    val body = if (data.isHierarchical) data.getQueryParameter("body") else bodyFromQuery(data)
                    val text = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT) ?: body
                    if (!text.isNullOrBlank()) container.shareHolder.pending = SharePayload(text)
                    if (recipients.isEmpty()) {
                        externalNav.value = ExternalNav.NewChat()
                    } else {
                        lifecycleScope.launch {
                            val id = runCatching { container.sender.conversationFor(recipients, container.conversations) }.getOrNull()
                            externalNav.value = if (id != null) ExternalNav.OpenChat(id) else ExternalNav.NewChat(recipients)
                        }
                    }
                }
            }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                val uris: List<Uri> = if (intent.action == Intent.ACTION_SEND) {
                    listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
                } else {
                    IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                }
                lifecycleScope.launch {
                    val drafts = withContext(Dispatchers.IO) { uris.mapNotNull { MediaTools.importAttachment(this@MainActivity, it) } }
                    container.shareHolder.pending = SharePayload(text, drafts)
                    externalNav.value = ExternalNav.NewChat()
                }
            }
        }
    }

    companion object {
        /** Unlocked since this process started: rotating the screen doesn't ask again, a restart does. */
        private var unlockedInThisProcess = false

        /** When Yarn last went to the background, for app lock's timeout (kept across activities). */
        private var backgroundedAt = 0L

        /** The latest settings, kept for the whole process so a reopened screen can lock at once. */
        private var settingsSnapshot = AppSettings()
    }

    /** sms:123?body=Hello is opaque, so parse the query by hand. */
    private fun bodyFromQuery(data: Uri): String? =
        data.schemeSpecificPart.substringAfter('?', "").split('&')
            .firstOrNull { it.startsWith("body=") }?.substringAfter("body=")?.let(Uri::decode)
}
