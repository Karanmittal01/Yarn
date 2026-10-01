package app.yarn.intelligence

import kotlin.math.exp

/**
 * Transparent keyword + sender-shape scoring. Every point awarded carries a reason, which the
 * UI surfaces in "Why is this here?" so users can understand and correct the model.
 */
object CategoryRules {

    private class Rule(val category: Category, val weight: Float, val pattern: Regex, val reason: String)

    private fun r(category: Category, weight: Float, reason: String, vararg words: String) =
        Rule(category, weight, Regex("(?i)\\b(?:${words.joinToString("|")})\\b"), reason)

    private val RULES = listOf(
        r(Category.OTP, 3.5f, "Mentions a one-time password", "otp", "one[- ]time (?:password|passcode|pin|code)", "verification code", "security code", "login code", "2fa", "passcode"),
        r(Category.OTP, 1.5f, "Asks you not to share a code", "do not share", "don't share", "never share", "valid for \\d+ ?(?:min|minutes|mins)"),

        r(Category.BANKING, 2.5f, "Mentions an account debit/credit", "debited", "credited", "a/c", "acct", "account balance", "avl bal", "available balance", "withdrawn", "atm", "neft", "imps", "rtgs", "ach", "wire transfer"),
        r(Category.BANKING, 1.8f, "Mentions a card or bank", "credit card", "debit card", "bank", "card ending", "net ?banking", "statement", "emi", "loan account", "cheque", "check deposit"),

        r(Category.PAYMENTS, 2.5f, "Mentions a payment", "upi", "paid to", "payment (?:of|received|successful|failed)", "sent to", "received from", "money request", "collect request", "wallet", "gpay", "phonepe", "paytm", "venmo", "zelle", "cash app", "paypal", "transaction id", "txn id", "ref no"),

        r(Category.BILLS, 2.5f, "Mentions a bill or due date", "bill (?:of|for|is|amount|generated)", "due date", "due on", "amount due", "overdue", "recharge", "postpaid", "electricity", "gas bill", "water bill", "broadband", "insurance premium", "premium due", "autopay", "auto-debit", "mandate", "subscription renew(?:s|al|ed)"),

        r(Category.DELIVERY, 2.8f, "Mentions a shipment", "shipped", "out for delivery", "delivered", "dispatched", "in transit", "tracking", "track your", "awb", "courier", "parcel", "package", "shipment", "delivery (?:partner|agent|executive|attempt|scheduled)", "arriving (?:today|tomorrow)"),

        r(Category.SHOPPING, 2.2f, "Mentions an order", "order (?:placed|confirmed|#|id|no)", "your order", "order has been", "thank you for (?:ordering|shopping|your order)", "gift card", "e-?gift", "cart", "invoice", "return (?:request|pickup)", "refund (?:initiated|processed)", "purchase"),

        r(Category.TRAVEL, 2.8f, "Mentions travel", "flight", "boarding", "pnr", "check-?in", "gate", "departure", "arrival", "train", "coach", "berth", "hotel", "reservation", "itinerary", "cab", "ride", "your driver", "uber", "ola", "lyft", "airport", "terminal", "e-ticket", "ticket booked"),

        r(Category.PROMOTIONS, 2.4f, "Looks like marketing", "sale", "offer", "discount", "\\d+% off", "flat \\d+", "coupon", "promo", "deal", "cashback offer", "limited time", "hurry", "shop now", "buy now", "exclusive", "free delivery", "use code", "voucher", "unsubscribe", "opt[- ]out", "reply stop", "txt stop", "sms stop"),
        r(Category.PROMOTIONS, 1.2f, "Invites you to an offer", "upgrade", "pre-?approved", "eligible for", "get up to", "win", "lucky", "special price"),

        r(Category.UPDATES, 1.8f, "Service notification", "appointment", "reminder", "scheduled", "your request", "ticket (?:no|number|id)", "complaint", "has been (?:updated|approved|activated|registered)", "successfully", "alert", "notification", "status", "results?", "report", "renewal", "kyc"),

        r(Category.WORK, 2.4f, "Mentions work topics", "meeting", "standup", "stand-up", "deadline", "client", "project", "office", "manager", "shift", "deploy", "invoice approved", "sprint", "presentation", "conference call", "team", "colleague", "payroll", "salary"),

        r(Category.PERSONAL, 1.6f, "Conversational language", "hi", "hey", "hello", "love", "miss you", "thanks", "thank you", "haha", "lol", "see you", "call me", "where are you", "how are you", "dinner", "lunch", "tonight", "weekend", "mom", "dad", "bro", "happy birthday", "congrats", "ok", "okay", "sure", "yes", "no worries"),
    )

    data class Scores(val probabilities: Map<Category, Float>, val reasons: Map<Category, List<String>>) {
        val top: Category get() = probabilities.maxByOrNull { it.value }!!.key
        val topProbability: Float get() = probabilities.getValue(top)
    }

    fun score(input: MessageInput, entities: List<Entity>): Scores {
        val text = input.text
        val raw = HashMap<Category, Float>()
        val reasons = HashMap<Category, MutableList<String>>()
        fun add(c: Category, w: Float, why: String) {
            raw[c] = (raw[c] ?: 0f) + w
            reasons.getOrPut(c) { mutableListOf() }.let { if (why !in it) it += why }
        }

        for (rule in RULES) {
            val hits = rule.pattern.findAll(text).take(3).count()
            if (hits > 0) add(rule.category, rule.weight * (1f + 0.35f * (hits - 1)), rule.reason)
        }

        entities.forEach { e ->
            when (e.type) {
                EntityType.OTP -> add(Category.OTP, 3f, "Contains a one-time code")
                EntityType.AMOUNT -> when (e.attributes["direction"]) {
                    "debit", "credit", "balance" -> add(Category.BANKING, 1.2f, "Contains a transaction amount")
                    "due" -> add(Category.BILLS, 1.2f, "Contains an amount due")
                    else -> add(Category.PAYMENTS, 0.4f, "Contains an amount")
                }
                EntityType.ACCOUNT_REF -> add(Category.BANKING, 1.5f, "References a masked account or card")
                EntityType.UPI_ID -> add(Category.PAYMENTS, 1.5f, "Contains a UPI ID")
                EntityType.TRACKING_NUMBER -> add(Category.DELIVERY, 2f, "Contains a tracking number")
                EntityType.ORDER_ID -> add(Category.SHOPPING, 1f, "Contains an order number")
                EntityType.BOOKING_REF, EntityType.FLIGHT -> add(Category.TRAVEL, 1.5f, "Contains a booking reference")
                else -> Unit
            }
        }

        val sender = input.sender
        when (sender.kind) {
            SenderKind.CONTACT -> add(Category.PERSONAL, 2.5f, "Sender is in your contacts")
            SenderKind.PHONE_NUMBER -> add(Category.PERSONAL, 1.2f, "Sent from a personal phone number")
            SenderKind.ALPHANUMERIC, SenderKind.SHORT_CODE -> {
                add(Category.UPDATES, 0.8f, "Sent by a business sender ID")
                raw[Category.PERSONAL] = (raw[Category.PERSONAL] ?: 0f) * 0.25f
                raw[Category.WORK] = (raw[Category.WORK] ?: 0f) * 0.5f
            }
            SenderKind.EMAIL -> Unit
        }
        SenderNames.brandCategory(sender.address)?.let { c ->
            add(c, 2.2f, "Sent by ${SenderNames.pretty(sender.address)}")
        }
        when (sender.commercialSuffix) {
            CommercialSuffix.PROMOTIONAL -> add(Category.PROMOTIONS, 3f, "Sender header is marked promotional (-P)")
            CommercialSuffix.TRANSACTIONAL -> {
                raw[Category.PROMOTIONS] = (raw[Category.PROMOTIONS] ?: 0f) * 0.3f
                add(Category.UPDATES, 0.5f, "Sender header is marked transactional (-T)")
            }
            CommercialSuffix.GOVERNMENT -> add(Category.UPDATES, 2f, "Sender header is marked government (-G)")
            CommercialSuffix.SERVICE, null -> Unit
        }
        // Transactional content beats marketing words that often appear in footers.
        val txn = maxOf(raw[Category.OTP] ?: 0f, raw[Category.BANKING] ?: 0f, raw[Category.PAYMENTS] ?: 0f)
        if (txn >= 3.5f) raw[Category.PROMOTIONS] = (raw[Category.PROMOTIONS] ?: 0f) * 0.5f
        if ((raw[Category.OTP] ?: 0f) >= 6f) {
            raw[Category.BANKING] = (raw[Category.BANKING] ?: 0f) * 0.4f
            raw[Category.PAYMENTS] = (raw[Category.PAYMENTS] ?: 0f) * 0.4f
        }

        // A small prior so short, keyword-free messages default sensibly.
        val prior = if (sender.isBusinessLike) Category.UPDATES else Category.PERSONAL
        raw[prior] = (raw[prior] ?: 0f) + 0.6f

        // Softmax over non-spam categories; spam is decided by SpamDetector.
        val cats = Category.entries.filter { it != Category.SPAM }
        val exps = cats.associateWith { exp(((raw[it] ?: 0f) * 0.9f).toDouble()).toFloat() }
        val total = exps.values.sum()
        return Scores(exps.mapValues { it.value / total }, reasons)
    }
}
