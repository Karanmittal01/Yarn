package app.yarn.messaging

import android.telephony.SmsManager

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

    fun smsMessage(resultCode: Int): String = when (resultCode) {
        SmsManager.RESULT_ERROR_RADIO_OFF -> "Airplane mode or radio is off. It will send when you're back online."
        SmsManager.RESULT_ERROR_NO_SERVICE, SmsManager.RESULT_RADIO_NOT_AVAILABLE -> "No mobile signal. It will send automatically when service returns."
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "Android's SMS sending limit was reached. Try again later."
        SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> "Blocked by Fixed Dialing Numbers on your SIM."
        SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED, SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "Sending to this short code isn't allowed."
        SmsManager.RESULT_INVALID_SMS_FORMAT, SmsManager.RESULT_INVALID_ARGUMENTS -> "The message or number is invalid."
        SmsManager.RESULT_NETWORK_REJECT -> "The network rejected the message."
        SmsManager.RESULT_INVALID_SMSC_ADDRESS -> "Your SIM's message center number is invalid."
        SmsManager.RESULT_OPERATION_NOT_ALLOWED -> "Your carrier doesn't allow this message."
        SmsManager.RESULT_CANCELLED -> "Sending was cancelled."
        else -> "Couldn't send (error $resultCode)."
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

    fun mmsMessage(resultCode: Int): String = when (resultCode) {
        SmsManager.MMS_ERROR_NO_DATA_NETWORK -> "MMS needs mobile data. It will retry when mobile data is available."
        SmsManager.MMS_ERROR_DATA_DISABLED -> "Mobile data is off. Turn it on to send or receive MMS."
        SmsManager.MMS_ERROR_INVALID_APN, SmsManager.MMS_ERROR_CONFIGURATION_ERROR -> "Your carrier's MMS settings (APN) look wrong."
        SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER -> "Your carrier has disabled MMS for this SIM."
        MMS_TOO_LARGE -> "The attachment is too large for your carrier."
        SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION, SmsManager.MMS_ERROR_INVALID_SUBSCRIPTION_ID -> "The selected SIM isn't active."
        SmsManager.MMS_ERROR_HTTP_FAILURE, SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS -> "Couldn't reach your carrier's MMS server."
        else -> "Couldn't send multimedia message (error $resultCode)."
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
