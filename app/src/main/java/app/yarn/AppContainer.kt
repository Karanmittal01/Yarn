package app.yarn

import app.yarn.ai.SmartSorter
import app.yarn.backup.GoogleDrive
import app.yarn.work.GoogleBackupWorker
import app.yarn.work.ReanalyzeWorker
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import app.yarn.ai.AssistantService
import app.yarn.ai.GeminiNanoService
import app.yarn.ai.IntelligenceEngine
import app.yarn.ai.LanguageService
import app.yarn.ai.LocalLlmService
import app.yarn.ai.MlEntityService
import app.yarn.ai.SelfHostedAiService
import app.yarn.ai.SmartReplyService
import app.yarn.ai.TranslationService
import app.yarn.backup.BackupManager
import app.yarn.data.contacts.ContactsRepository
import app.yarn.data.db.YarnDatabase
import app.yarn.data.prefs.SettingsRepository
import app.yarn.data.repo.ConversationRepository
import app.yarn.data.repo.TelephonySync
import app.yarn.messaging.IncomingProcessor
import app.yarn.messaging.MessageSender
import app.yarn.notifications.Notifier
import app.yarn.telephony.DefaultSmsApp
import app.yarn.telephony.SimManager
import app.yarn.telephony.TelephonyStore
import app.yarn.ui.conversation.ShareHolder
import app.yarn.work.MaintenanceWorker
import app.yarn.work.SendWorker
import app.yarn.work.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/** Hand-rolled dependency graph: explicit, fast to start, no reflection or codegen. */
class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Serialises every write to the system SMS/MMS provider against imports. */
    private val providerLock = Mutex()

    val db: YarnDatabase by lazy { YarnDatabase.open(app) }
    val settings = SettingsRepository(app)
    val contacts = ContactsRepository(app)
    val sims = SimManager(app)
    val store = TelephonyStore(app)

    val engine by lazy { IntelligenceEngine(db, settings, contacts, scope) }
    val notifier by lazy { Notifier(app, db, contacts, settings) }
    val conversations by lazy { ConversationRepository(app, db, store, contacts, engine, notifier, providerLock, scope) }
    val sender by lazy { MessageSender(app, db, store, sims, settings, engine, notifier, providerLock, scope) }
    val language by lazy { LanguageService() }
    val translation by lazy { TranslationService(language) }
    val mlEntities by lazy { MlEntityService() }
    val smartSorter by lazy { SmartSorter(db, nano, settings) }
    val incoming by lazy { IncomingProcessor(app, db, store, sims, settings, engine, mlEntities, smartSorter, conversations, sender, notifier, providerLock, scope) }
    val sync by lazy { TelephonySync(app, db, store, conversations, providerLock) }
    val nano by lazy { GeminiNanoService(app) }
    val localLlm by lazy { LocalLlmService(app) }
    val assistant by lazy { AssistantService(settings, nano, localLlm, SelfHostedAiService(), SmartReplyService(), language, mlEntities) }
    val backup by lazy { BackupManager(app, db, store, sync, providerLock) }
    val drive by lazy { GoogleDrive(app) }
    val shareHolder = ShareHolder()

    private val _isDefaultSmsApp = MutableStateFlow(false)
    val isDefaultSmsApp: StateFlow<Boolean> = _isDefaultSmsApp.asStateFlow()

    private val _airplaneMode = MutableStateFlow(false)
    val airplaneMode: StateFlow<Boolean> = _airplaneMode.asStateFlow()

    private var providerObserverRegistered = false
    private var syncJob: Job? = null

    fun start() {
        notifier.createChannels()
        refreshPlatformState()
        MaintenanceWorker.schedule(app)
        scope.launch {
            val s = settings.current()
            GoogleBackupWorker.schedule(app, s.googleAccount != null && s.autoBackup)
            // Sorting rules improved since these messages were filed: re-sort once.
            if (s.initialImportDone && s.rulesVersion < YarnApplication.RULES_VERSION) {
                db.messages().resetModelCategories()
                ReanalyzeWorker.enqueue(app)
            }
            if (s.rulesVersion < YarnApplication.RULES_VERSION) settings.update { it.copy(rulesVersion = YarnApplication.RULES_VERSION) }
        }
        // Prepare the free on-device models in the background (no-ops when unsupported).
        scope.launch {
            delay(5_000)
            if (settings.current().aiEnabled) {
                runCatching { nano.ensureDownloaded() }
                runCatching { mlEntities.ensureModel(allowMobileData = false) }
            }
        }
        // Refresh cached names once per launch (contact edits, better sender-name mapping in updates).
        scope.launch { runCatching { conversations.refreshDisplayNames() } }
        scope.launch {
            // Rename conversations when the address book changes.
            contacts.version.drop(1).collect { conversations.refreshDisplayNames() }
        }
        val airplaneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                _airplaneMode.value = readAirplaneMode()
                // Leaving airplane mode: retry anything waiting for signal right away.
                if (!_airplaneMode.value) SendWorker.enqueue(app)
            }
        }
        ContextCompat.registerReceiver(app, airplaneReceiver, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Re-evaluates role, permissions and dependent services; call on every app resume. */
    fun refreshPlatformState() {
        val isDefault = DefaultSmsApp.isDefault(app)
        _isDefaultSmsApp.value = isDefault
        _airplaneMode.value = readAirplaneMode()
        sims.start()
        contacts.start()
        registerProviderObserver()
        if (isDefault) {
            SendWorker.enqueue(app)
            scope.launch { sender.scheduleAlarm() }
        }
    }

    private fun readAirplaneMode() = Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    /**
     * Picks up messages written by other apps (restore tools, or carriers' system apps). Debounced
     * so bursts of changes trigger one incremental import.
     */
    private fun registerProviderObserver() {
        if (providerObserverRegistered) return
        if (ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                syncJob?.cancel()
                syncJob = scope.launch { delay(2_000); SyncWorker.enqueue(app) }
            }
        }
        runCatching {
            app.contentResolver.registerContentObserver(Uri.parse("content://mms-sms/"), true, observer)
            providerObserverRegistered = true
        }
    }
}

class YarnApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }

    companion object {
        /** Bump when categorisation changes enough that existing messages should be re-sorted. */
        const val RULES_VERSION = 4
    }
}
