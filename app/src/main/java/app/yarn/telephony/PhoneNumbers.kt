package app.yarn.telephony

import android.content.Context
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

object PhoneNumbers {
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun countryIso(context: Context): String {
        val tm = context.getSystemService(TelephonyManager::class.java)
        return (tm?.simCountryIso?.takeIf { it.isNotBlank() }
            ?: tm?.networkCountryIso?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().country).uppercase()
    }

    fun isEmail(address: String) = EMAIL.matches(address.trim())

    fun isDialable(address: String): Boolean {
        val a = address.trim()
        return a.isNotEmpty() && a.none { it.isLetter() } && a.count { it.isDigit() } >= 3
    }

    /** Canonical form used for sending and matching: E.164 when parseable, otherwise trimmed. */
    fun normalize(address: String, iso: String): String {
        val a = address.trim()
        if (a.isEmpty() || isEmail(a) || a.any { it.isLetter() }) return a
        PhoneNumberUtils.formatNumberToE164(a, iso)?.let { return it }
        val digits = a.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        return digits.ifEmpty { a }
    }

    fun same(a: String, b: String, iso: String): Boolean {
        if (a.equals(b, ignoreCase = true)) return true
        if (!isDialable(a) || !isDialable(b)) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PhoneNumberUtils.areSamePhoneNumber(a, b, iso.lowercase())
        } else {
            @Suppress("DEPRECATION")
            PhoneNumberUtils.compare(a, b)
        }
    }

    fun format(address: String, iso: String): String {
        if (!isDialable(address)) return address
        return PhoneNumberUtils.formatNumber(address, iso) ?: address
    }

    /** Short key for matching the same person across formats (last 10 digits). */
    fun matchKey(address: String): String {
        val a = address.trim()
        if (a.any { it.isLetter() }) return a.lowercase()
        val digits = a.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }
}
