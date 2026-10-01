package app.yarn.telephony

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.BlockedNumberContract
import android.provider.Telephony
import android.util.Log

object DefaultSmsApp {
    fun isDefault(context: Context): Boolean {
        val rm = context.getSystemService(RoleManager::class.java)
        return if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS)) rm.isRoleHeld(RoleManager.ROLE_SMS)
        else Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }

    /** Intent that asks the user to make us the default SMS app, or null if unsupported. */
    fun requestIntent(context: Context): Intent? {
        val rm = context.getSystemService(RoleManager::class.java) ?: return null
        if (!rm.isRoleAvailable(RoleManager.ROLE_SMS)) return null
        return rm.createRequestRoleIntent(RoleManager.ROLE_SMS)
    }
}

/**
 * The system-wide block list (shared with the dialer and carrier services). Only the default SMS
 * app, default dialer or carrier apps may use it, so every call is guarded.
 */
object SystemBlockList {
    private const val TAG = "SystemBlockList"

    fun canUse(context: Context): Boolean =
        runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(context) }.getOrDefault(false) && DefaultSmsApp.isDefault(context)

    fun isBlocked(context: Context, number: String): Boolean =
        canUse(context) && runCatching { BlockedNumberContract.isBlocked(context, number) }.getOrDefault(false)

    fun block(context: Context, number: String): Boolean {
        if (!canUse(context)) return false
        return runCatching {
            val values = android.content.ContentValues().apply {
                put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
            }
            context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values) != null
        }.onFailure { Log.w(TAG, "Unable to add to system block list", it) }.getOrDefault(false)
    }

    fun unblock(context: Context, number: String) {
        if (!canUse(context)) return
        runCatching { BlockedNumberContract.unblock(context, number) }
            .onFailure { Log.w(TAG, "Unable to remove from system block list", it) }
    }
}
