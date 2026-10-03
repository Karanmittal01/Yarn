package app.yarn.ui.common

import android.content.Context
import app.yarn.R

/**
 * The analyser explains its decisions in English (it's a plain Kotlin module and the text is
 * stored with each message). This shows those explanations in Yarn's current language.
 */
object ReasonText {
    private val exact: Map<String, Int> = mapOf(
        "About your order" to R.string.reason_1,
        "Advertises a loan, card or plan" to R.string.reason_2,
        "Anything from the bank" to R.string.reason_3,
        "Asks you not to share a code" to R.string.reason_4,
        "Asks you to buy or sign up" to R.string.reason_5,
        "Contains a UPI ID" to R.string.reason_6,
        "Contains a booking reference" to R.string.reason_7,
        "Contains a one-time code" to R.string.reason_8,
        "Contains a tracking number" to R.string.reason_9,
        "Contains a transaction amount" to R.string.reason_10,
        "Contains an amount due" to R.string.reason_11,
        "Contains an amount" to R.string.reason_12,
        "Contains an order number" to R.string.reason_13,
        "Conversational language" to R.string.reason_14,
        "Creates urgency to buy" to R.string.reason_15,
        "Has an opt-out footer" to R.string.reason_16,
        "Looks like a personal message" to R.string.reason_17,
        "Looks like marketing" to R.string.reason_18,
        "Mentions a bill or due date" to R.string.reason_19,
        "Mentions a card or bank account" to R.string.reason_20,
        "Mentions a one-time password" to R.string.reason_21,
        "Mentions a payment" to R.string.reason_22,
        "Mentions a shipment" to R.string.reason_23,
        "Mentions an account debit/credit" to R.string.reason_24,
        "Mentions investments" to R.string.reason_25,
        "Mentions travel" to R.string.reason_26,
        "Mentions work topics" to R.string.reason_27,
        "Money moved" to R.string.reason_28,
        "References a masked account or card" to R.string.reason_29,
        "Sender header is marked government (-G)" to R.string.reason_30,
        "Sender header is marked promotional (-P)" to R.string.reason_31,
        "Sender header is marked transactional (-T)" to R.string.reason_32,
        "Sender is in your contacts" to R.string.reason_33,
        "Sent by a business sender ID" to R.string.reason_34,
        "Sent from a personal phone number" to R.string.reason_35,
        "Service notification" to R.string.reason_36,
        "Talks about your order" to R.string.reason_37,
        "Threatens account suspension or blocking" to R.string.reason_38,
        "Asks you to verify identity or update KYC via a link" to R.string.reason_39,
        "Asks for a code, PIN or password" to R.string.reason_40,
        "Claims unusual or unauthorised activity" to R.string.reason_41,
        "Prize or lottery claim" to R.string.reason_42,
        "Undelivered-parcel or fee scam wording" to R.string.reason_43,
        "Unpaid toll/fine or tax refund wording" to R.string.reason_44,
        "Job or investment lure" to R.string.reason_45,
        "Electricity/utility disconnection threat" to R.string.reason_46,
        "Urgent pressure language" to R.string.reason_47,
        "Pushes you to click a link" to R.string.reason_48,
        "Free-gift or loan bait" to R.string.reason_49,
        "Adult or gambling content" to R.string.reason_50,
        "Wrong-number conversation opener" to R.string.reason_51,
        "Claims to be a company but comes from a personal phone number with a link" to R.string.reason_52,
        "Claims to be a company but comes from an email address with a link" to R.string.reason_53,
        "Unknown number sent a link" to R.string.reason_54,
        "Uses lookalike characters to disguise words" to R.string.reason_55,
        "Contains hidden characters" to R.string.reason_56,
        "Written mostly in capitals" to R.string.reason_57,
        "Malformed link" to R.string.reason_58,
        "Link points to a raw IP address" to R.string.reason_59,
        "Link uses an internationalised lookalike domain" to R.string.reason_60,
        "Link is shortened and hides its destination" to R.string.reason_61,
        "Link hides its real destination with '@'" to R.string.reason_62,
        "Link has an unusually deep subdomain" to R.string.reason_63,
        "Domain contains several hyphens" to R.string.reason_64,
        "You marked this sender as not spam" to R.string.reason_65,
        "You marked this sender as spam" to R.string.reason_66,
        "You marked a similar message as spam" to R.string.reason_67,
        "You marked a similar message as not spam" to R.string.reason_68,
        "Resembles messages you marked as spam" to R.string.reason_69,
        "Resembles messages you marked as not spam" to R.string.reason_70,
        "You chose this category" to R.string.reason_71,
        "Sorted by Gemini Nano on your phone" to R.string.reason_72,
        "Matched one of your block filters" to R.string.reason_73,
    )
    private val marketingFrom = Regex("^Marketing from (.+)$")
    private val sentBy = Regex("^Sent by (.+)$")
    private val riskyTld = Regex("^Link uses a domain ending often abused by scammers \\((.+)\\)$")
    private val imitates = Regex("^Domain imitates (.+) but is not an official (.+) domain$")

    fun localize(context: Context, reason: String): String {
        val r = reason.trim()
        exact[r]?.let { return context.getString(it) }
        marketingFrom.matchEntire(r)?.let { return context.getString(R.string.reason_marketing_from, it.groupValues[1]) }
        riskyTld.matchEntire(r)?.let { return context.getString(R.string.reason_risky_tld, it.groupValues[1]) }
        imitates.matchEntire(r)?.let { return context.getString(R.string.reason_imitates, it.groupValues[1], it.groupValues[2]) }
        sentBy.matchEntire(r)?.let { return context.getString(R.string.reason_sent_by, it.groupValues[1]) }
        return r
    }

    /** Splits a stored multi-line explanation into localized, non-empty lines. */
    fun lines(context: Context, stored: String): List<String> = stored.lines().filter { it.isNotBlank() }.map { localize(context, it) }
}
