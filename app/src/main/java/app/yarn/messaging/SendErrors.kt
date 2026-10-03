package app.yarn.messaging

import android.content.Context
import android.telephony.SmsManager
import app.yarn.R

/** Classifies radio/MMS failures into what the queue should do next and what to tell the user. */
object SendErrors {
    enum class Action { WAIT_FOR_SERVICE, RETRY, FAIL }

    fun smsAction(resultCode: Int): Action = when (resultCode) {
        SmsManager.RESULT_ERROR_RADIO_OFF,
        SmsManager.RESULT_ERROR_NO_SERVICE,
        SmsManager.RESULT_RADIO_NOT_AVAILABLE,
        -> Action.WAIT_FOR_SERVICE
        SmsManager.RESULT_ERROR_GENERIC_FAILURE,
        SmsManager.RESULT_NETWORK_REJECT,
        SmsManager.RESULT_NETWORK_ERROR,
        SmsManager.RESULT_MODEM_ERROR,
        SmsManager.RESULT_SYSTEM_ERROR,
        SmsManager.RESULT_NO_RESOURCES,
        SmsManager.RESULT_NO_MEMORY,
        SmsManager.RESULT_ENCODING_ERROR,
        SmsManager.RESULT_INTERNAL_ERROR,
        SmsManager.RESULT_UNEXPECTED_EVENT_STOP_SENDING,
        -> Action.RETRY
        else -> Action.FAIL
    }

    fun smsMessage(context: Context, resultCode: Int): String = when (resultCode) {
        SmsManager.RESULT_ERROR_RADIO_OFF -> context.getString(R.string.err_radio_off)
        SmsManager.RESULT_ERROR_NO_SERVICE, SmsManager.RESULT_RADIO_NOT_AVAILABLE -> context.getString(R.string.err_no_service)
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> context.getString(R.string.err_limit)
        SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> context.getString(R.string.err_fdn)
        SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED, SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> context.getString(R.string.err_short_code)
        SmsManager.RESULT_INVALID_SMS_FORMAT, SmsManager.RESULT_INVALID_ARGUMENTS -> context.getString(R.string.err_invalid)
        SmsManager.RESULT_NETWORK_REJECT -> context.getString(R.string.err_network_reject)
        SmsManager.RESULT_INVALID_SMSC_ADDRESS -> context.getString(R.string.err_smsc)
        SmsManager.RESULT_OPERATION_NOT_ALLOWED -> context.getString(R.string.err_not_allowed)
        SmsManager.RESULT_CANCELLED -> context.getString(R.string.err_cancelled)
        else -> context.getString(R.string.err_sms_code, resultCode)
    }

    fun mmsAction(resultCode: Int): Action = when (resultCode) {
        SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS,
        SmsManager.MMS_ERROR_HTTP_FAILURE,
        SmsManager.MMS_ERROR_IO_ERROR,
        SmsManager.MMS_ERROR_RETRY,
        SmsManager.MMS_ERROR_UNSPECIFIED,
        -> Action.RETRY
        SmsManager.MMS_ERROR_NO_DATA_NETWORK -> Action.WAIT_FOR_SERVICE
        else -> Action.FAIL
    }

    fun mmsMessage(context: Context, resultCode: Int): String = when (resultCode) {
        SmsManager.MMS_ERROR_NO_DATA_NETWORK -> context.getString(R.string.err_mms_no_data)
        SmsManager.MMS_ERROR_DATA_DISABLED -> context.getString(R.string.err_mms_data_off)
        SmsManager.MMS_ERROR_INVALID_APN, SmsManager.MMS_ERROR_CONFIGURATION_ERROR -> context.getString(R.string.err_mms_apn)
        SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER -> context.getString(R.string.err_mms_disabled)
        MMS_TOO_LARGE -> context.getString(R.string.err_mms_too_large)
        SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION, SmsManager.MMS_ERROR_INVALID_SUBSCRIPTION_ID -> context.getString(R.string.err_mms_sim_inactive)
        SmsManager.MMS_ERROR_HTTP_FAILURE, SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS -> context.getString(R.string.err_mms_server)
        else -> context.getString(R.string.err_mms_code, resultCode)
    }

    /** Backoff for queue retries: 15s, 1m, 5m, 15m... */
    fun backoffMillis(attempt: Int): Long = when (attempt) {
        0, 1 -> 15_000L
        2 -> 60_000L
        3 -> 5 * 60_000L
        else -> 15 * 60_000L
    }

    /** App-level code: attachment exceeds the carrier MMS size limit. */
    const val MMS_TOO_LARGE = 10_001

    const val MAX_ATTEMPTS = 4
    /** Give up waiting for signal after this long and tell the user. */
    const val MAX_WAIT_FOR_SERVICE_MS = 72L * 60 * 60 * 1000
}
