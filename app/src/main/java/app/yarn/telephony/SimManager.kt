package app.yarn.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SimInfo(
    val subId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String,
    val number: String?,
    val color: Int,
) {
    val label: String get() = "SIM ${slotIndex + 1} · $displayName"
}

/** Carrier MMS limits, read from the platform's carrier config with safe defaults. */
data class MmsConfig(
    val mmsEnabled: Boolean = true,
    val maxMessageSize: Int = 300 * 1024,
    val maxImageWidth: Int = 1280,
    val maxImageHeight: Int = 1280,
    val groupMmsEnabled: Boolean = true,
    /** Convert to MMS when an SMS needs more than this many parts; -1 = never. */
    val smsToMmsTextThreshold: Int = -1,
    val recipientLimit: Int = Int.MAX_VALUE,
    val deliveryReportsSupported: Boolean = true,
)

class SimManager(private val context: Context) {
    private val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
    private val _sims = MutableStateFlow<List<SimInfo>>(emptyList())
    val sims: StateFlow<List<SimInfo>> = _sims.asStateFlow()
    private var listening = false

    private val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
        override fun onSubscriptionsChanged() {
            _sims.value = loadSims()
        }
    }

    fun hasPhoneStatePermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    /** Starts tracking SIM insertions/removals. Safe to call repeatedly. */
    fun start() {
        _sims.value = loadSims()
        if (listening || !hasPhoneStatePermission()) return
        runCatching {
            subscriptionManager?.addOnSubscriptionsChangedListener(ContextCompat.getMainExecutor(context), listener)
            listening = true
        }.onFailure { Log.w(TAG, "Unable to observe subscriptions", it) }
    }

    @SuppressLint("MissingPermission")
    private fun loadSims(): List<SimInfo> {
        if (!hasPhoneStatePermission()) return emptyList()
        return try {
            subscriptionManager?.activeSubscriptionInfoList.orEmpty().map { it.toSimInfo() }.sortedBy { it.slotIndex }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    private fun SubscriptionInfo.toSimInfo(): SimInfo {
        val number = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) subscriptionManager?.getPhoneNumber(subscriptionId)
            else @Suppress("DEPRECATION") number
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return SimInfo(subscriptionId, simSlotIndex, displayName?.toString().orEmpty(), carrierName?.toString().orEmpty(), number, iconTint)
    }

    fun current(): List<SimInfo> = if (_sims.value.isEmpty()) loadSims().also { _sims.value = it } else _sims.value

    val isMultiSim: Boolean get() = current().size > 1

    fun defaultSmsSubId(): Int = SubscriptionManager.getDefaultSmsSubscriptionId()

    /** The preferred SIM if still inserted, else the system default, else any active SIM. */
    fun resolveSubId(preferred: Int): Int {
        val active = current()
        if (preferred >= 0 && active.any { it.subId == preferred }) return preferred
        val def = defaultSmsSubId()
        if (def >= 0 && (active.isEmpty() || active.any { it.subId == def })) return def
        return active.firstOrNull()?.subId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }

    fun smsManager(subId: Int): SmsManager {
        val base = context.getSystemService(SmsManager::class.java)
        return if (subId >= 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) base.createForSubscriptionId(subId)
            else @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(subId)
        } else {
            base ?: @Suppress("DEPRECATION") SmsManager.getDefault()
        }
    }

    /** Our own numbers, to exclude ourselves from group MMS participant lists. */
    fun ownNumbers(): Set<String> = current().mapNotNull { it.number }.toSet()

    @SuppressLint("MissingPermission")
    fun isRoaming(subId: Int): Boolean = runCatching {
        val tm = if (subId >= 0) telephonyManager?.createForSubscriptionId(subId) else telephonyManager
        tm?.isNetworkRoaming ?: false
    }.getOrDefault(false)

    fun mmsConfig(subId: Int): MmsConfig {
        val bundle = runCatching { smsManager(subId).carrierConfigValues }.getOrNull() ?: return MmsConfig()
        val d = MmsConfig()
        return MmsConfig(
            mmsEnabled = bundle.getBoolean(SmsManager.MMS_CONFIG_MMS_ENABLED, d.mmsEnabled),
            maxMessageSize = bundle.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, d.maxMessageSize).takeIf { it > 0 } ?: d.maxMessageSize,
            maxImageWidth = bundle.getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_WIDTH, d.maxImageWidth).takeIf { it > 0 } ?: d.maxImageWidth,
            maxImageHeight = bundle.getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_HEIGHT, d.maxImageHeight).takeIf { it > 0 } ?: d.maxImageHeight,
            groupMmsEnabled = bundle.getBoolean(SmsManager.MMS_CONFIG_GROUP_MMS_ENABLED, d.groupMmsEnabled),
            smsToMmsTextThreshold = bundle.getInt(SmsManager.MMS_CONFIG_SMS_TO_MMS_TEXT_THRESHOLD, d.smsToMmsTextThreshold),
            recipientLimit = bundle.getInt(SmsManager.MMS_CONFIG_RECIPIENT_LIMIT, d.recipientLimit).takeIf { it > 0 } ?: d.recipientLimit,
            deliveryReportsSupported = bundle.getBoolean(SmsManager.MMS_CONFIG_SMS_DELIVERY_REPORT_ENABLED, true),
        )
    }

    companion object {
        private const val TAG = "SimManager"
    }
}
