package app.yarn

import app.yarn.data.prefs.sortingOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import app.yarn.ai.SmartSorter
import app.yarn.backup.GoogleDrive
import app.yarn.work.GoogleBackupWorker
import app.yarn.billing.Licence
import app.yarn.ui.common.BrandLogos
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
import app.yarn.ai.TranslationService
import app.yarn.backup.BackupManager
import app.yarn.data.contacts.ContactsRepository
import app.yarn.data.db.YarnDatabase
import app.yarn.data.prefs.SettingsRepository
import app.yarn.data.repo.Cleaner
import app.yarn.data.repo.ConversationRepository
import app.yarn.data.repo.RecycleBin
import app.yarn.data.repo.TelephonySync
import app.yarn.messaging.IncomingProcessor
import app.yarn.messaging.MessageSender
import app.yarn.notifications.Notifier
import app.yarn.telephony.DefaultSmsApp
import app.yarn.telephony.SimManager
import app.yarn.telephony.TelephonyStore
import app.yarn.ui.conversation.ShareHolder
import app.yarn.work.MaintenanceWorker
import app.yarn.work.ProgressTracker
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
    /** App-wide background work; a failing job is recorded instead of closing the app. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CrashLog.handler)
    /** Serialises every write to the system SMS/MMS provider against imports. */
    private val providerLock = Mutex()

    val db: YarnDatabase by lazy { YarnDatabase.open(app) }
    val settings = SettingsRepository(app)
    val contacts = ContactsRepository(app)
    val sims = SimManager(app)
    val store = TelephonyStore(app)

    val engine by lazy { IntelligenceEngine(db, settings, contacts, scope, pro = { licence.isPro }) }
    val notifier by lazy { Notifier(app, db, contacts, settings, sims) { licence.isPro } }
    val bin by lazy { RecycleBin(app, db, settings) { licence.isPro } }
    val conversations by lazy { ConversationRepository(app, db, store, contacts, engine, notifier, providerLock, scope, bin) }
    val sender by lazy { MessageSender(app, db, store, sims, settings, engine, notifier, providerLock, scope) }
    val language by lazy { LanguageService() }
    val translation by lazy { TranslationService(app, language) }
    val mlEntities by lazy { MlEntityService() }
    val smartSorter by lazy { SmartSorter(db, nano, settings, engine) { licence.isPro } }
    val licence by lazy { Licence(app, scope) }
    val incoming by lazy { IncomingProcessor(app, db, store, sims, settings, engine, mlEntities, smartSorter, conversations, sender, notifier, providerLock, scope) }
    val sync by lazy { TelephonySync(app, db, store, conversations, providerLock) }
    val nano by lazy { GeminiNanoService(app) }
    val localLlm by lazy { LocalLlmService(app) }
    val assistant by lazy { AssistantService(settings, nano, localLlm, SelfHostedAiService(), language, mlEntities) }
    val backup by lazy { BackupManager(app, db, store, sync, providerLock, settings) }
    val drive by lazy { GoogleDrive(app) }
    val cleaner by lazy { Cleaner(db, conversations, settings) }
    val shareHolder = ShareHolder()
    /** Progress of sorting / re-sorting, shown as bars in the inbox and settings. */
    val progress = ProgressTracker()

    private val _isDefaultSmsApp = MutableStateFlow(false)
    val isDefaultSmsApp: StateFlow<Boolean> = _isDefaultSmsApp.asStateFlow()

    private val _airplaneMode = MutableStateFlow(false)
    val airplaneMode: StateFlow<Boolean> = _airplaneMode.asStateFlow()

    private var providerObserverRegistered = false
    private var syncJob: Job? = null

    fun start() {
        notifier.createChannels()
        BrandLogos.enabled = licence.isPro
        licence.start()
        // Pro started or ended (even while Yarn was closed): spam filtering changes, so file
        // everything again. Logos follow at once.
        scope.launch {
            val prefs = app.getSharedPreferences("licence", android.content.Context.MODE_PRIVATE)
            licence.access.map { it.isPro }.distinctUntilChanged().collect { pro ->
                BrandLogos.enabled = pro
                val filedAs = if (prefs.contains(K_FILED_AS_PRO)) prefs.getBoolean(K_FILED_AS_PRO, true) else null
                if (filedAs != null && filedAs != pro) ReanalyzeWorker.enqueue(app)
                prefs.edit().putBoolean(K_FILED_AS_PRO, pro).apply()
            }
        }
        // Sorting turned back on: file everything again, including what arrived while it was off.
        scope.launch {
            var was: Boolean? = null
            settings.settings.map { it.sortingOn }.distinctUntilChanged().collect { on ->
                if (was == false && on) ReanalyzeWorker.enqueue(app)
                was = on
            }
        }
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
        // Once: business chats (RCS cards) imported before Yarn could read them get their words back.
        scope.launch {
            val repairs = app.getSharedPreferences("repairs", android.content.Context.MODE_PRIVATE)
            if (!repairs.getBoolean(K_RICH_CARDS, false) && settings.current().initialImportDone) {
                val fixed = runCatching { sync.repairRichCards() }.getOrDefault(-1)
                if (fixed >= 0) repairs.edit().putBoolean(K_RICH_CARDS, true).apply()
                if (fixed > 0) SyncWorker.enqueue(app)
            }
        }
        // Refresh cached names once per launch (contact edits, better sender-name mapping in updates).
        scope.launch { runCatching { conversations.refreshDisplayNames() } }
        scope.launch { runCatching { bin.emptyExpired() } }
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
        CrashLog.install(this)
        NativeGuard.install(this)
        app.yarn.i18n.AppLanguage.applyTo(this)
        container = AppContainer(this)
        container.start()
        // Crashes the handler couldn't catch (native code, out of memory) are noted by Android; read them now.
        container.scope.launch { CrashLog.collectExitReasons(this@YarnApplication) }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Android 10–12: keep Yarn's chosen language when the phone's configuration changes.
        app.yarn.i18n.AppLanguage.applyTo(this)
    }

    companion object {
        /** Bump when categorisation changes enough that existing messages should be re-sorted. */
        const val RULES_VERSION = 12
    }
}

/** Whether messages were last filed with Pro's spam filtering, so a change while Yarn was closed is caught. */
private const val K_FILED_AS_PRO = "filed_as_pro"

/** Business chats' cards were turned into text (see TelephonySync.repairRichCards). */
private const val K_RICH_CARDS = "rich_cards_v1"
