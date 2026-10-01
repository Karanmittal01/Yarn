package app.yarn.intelligence

import kotlin.math.exp

/**
 * Transparent keyword + sender scoring. Every point awarded carries a reason, which the UI surfaces
 * in "Why is this here?" so users can understand and correct the model.
 *
 * The order of evidence, strongest first:
 *  1. The Indian DLT header suffix (-P promotional, -T transactional, -S service, -G government).
 *     Operators enforce it per template, so a -P header cannot carry an OTP or an order update.
 *  2. What the message *says*: a one-time code, a debit/credit, an order's progress, or marketing.
 *  3. Who sent it (a known bank, shop or courier) — only a tie-breaker, so "Sale is live" from
 *     Zepto is an offer while "Your order is on the way" from Zepto is shopping.
 */
object CategoryRules {

    private class Rule(val category: Category, val weight: Float, val pattern: Regex, val reason: String)

    private fun r(category: Category, weight: Float, reason: String, vararg words: String) =
        Rule(category, weight, Regex("(?i)(?<![\\p{L}\\p{N}])(?:${words.joinToString("|")})(?![\\p{L}\\p{N}])"), reason)

    private const val MONEY = "(?:rs\\.?|inr|₹)\\s?[\\d,]+(?:\\.\\d+)?"

    private val RULES = listOf(
        // ---- One-time codes ---------------------------------------------------------------------
        r(Category.OTP, 3.5f, "Mentions a one-time password", "otp", "one[- ]time (?:password|passcode|pin|code)", "verification code", "security code",
            "login code", "auth(?:entication)? code", "2fa", "passcode", "is your (?:\\w+ )?code", "code is", "code for (?:login|logging in|verification|sign[- ]?in)"),
        r(Category.OTP, 1.5f, "Asks you not to share a code", "do not share", "don't share", "dont share", "never share", "valid (?:for|till) \\d+ ?(?:min|minutes|mins|seconds)"),

        // ---- Banks, cards, investments ----------------------------------------------------------
        r(Category.BANKING, 2.5f, "Mentions an account debit/credit", "debited", "credited", "a/c", "acct", "account balance", "avl bal", "avl\\.? bal", "available balance",
            "avl limit", "available limit", "withdrawn", "withdrawal", "atm", "neft", "imps", "rtgs", "ach", "wire transfer", "spent on", "spent at",
            "(?:txn|trxn|transaction|purchase) (?:of|for) $MONEY"),
        r(Category.BANKING, 1.8f, "Mentions a card or bank account", "credit card", "debit card", "card ending", "card xx\\d+", "savings account", "current account", "net ?banking",
            "statement", "emi", "loan account", "cheque", "kyc", "home branch", "card (?:has been |is )?(?:blocked|activated|dispatched|delivered)", "bank"),
        r(Category.BANKING, 2.2f, "Mentions investments", "demat", "ipo", "fpo", "sip", "mutual fund", "folio", "nav", "dividend", "allotment", "allotted",
            "securities", "shares", "trading account", "funds (?:of $MONEY )?(?:added|withdrawn|credited)", "margin"),

        r(Category.PAYMENTS, 2.5f, "Mentions a payment", "upi", "paid to", "paid $MONEY to", "payment (?:of|received|successful|failed|is successful)", "sent to",
            "received from", "money request", "collect request", "wallet", "vpa", "transaction id", "txn id", "ref no", "upi ref", "utr"),

        r(Category.BILLS, 2.5f, "Mentions a bill or due date", "bill (?:of|for|is|amount|generated)", "due date", "due on", "is due", "amount due", "amt due", "overdue",
            "postpaid", "electricity", "gas bill", "water bill", "broadband", "premium (?:of $MONEY )?(?:for|is|due)", "insurance premium", "autopay", "auto-debit",
            "mandate", "subscription renew(?:s|al|ed)", "plan expires", "validity (?:ends|expires)"),

        // ---- Orders, food, groceries, deliveries ------------------------------------------------
        r(Category.SHOPPING, 3f, "Talks about your order", "your order", "your (?:\\w+ ){1,2}order", "order (?:#|no\\.?|id) ?\\w+",
            "order (?:has been |is |was )?(?:placed|confirmed|received|packed|ready|cancelled|canceled|returned|refunded|on (?:its|the) way)",
            "thank you for (?:ordering|shopping|your order|your purchase)", "thanks for (?:ordering|shopping)", "items? (?:in|from) your order",
            "order total", "order value", "invoice", "refund (?:of|for|initiated|processed|has been|is|credited)", "return (?:request|pickup|initiated|of)",
            "replacement", "rate your (?:order|delivery|experience|meal|purchase)", "enjoy your (?:meal|food|order)", "gift card"),
        r(Category.DELIVERY, 2.8f, "Mentions a shipment", "shipped", "out for delivery", "delivered", "dispatched", "in transit", "tracking", "track (?:your|it)",
            "awb", "courier", "parcel", "package", "shipment", "delivery (?:partner|agent|executive|associate|attempt|scheduled|slot|boy)",
            "arriving (?:today|tomorrow|by|in)", "will (?:arrive|reach you)", "scheduled for delivery", "picked up"),

        // ---- Travel ---------------------------------------------------------------------------
        r(Category.TRAVEL, 2.8f, "Mentions travel", "flight", "boarding", "pnr", "check-?in", "gate", "departure", "arrival", "train", "coach", "berth", "hotel",
            "itinerary", "cab", "ride", "your driver", "driver \\w+", "uber", "ola", "rapido", "airport", "terminal", "e-ticket", "ticket booked", "bus ticket"),

        // ---- Marketing ------------------------------------------------------------------------
        r(Category.PROMOTIONS, 2.6f, "Looks like marketing", "sale", "sale is live", "offer", "offers", "discount", "discounts", "\\d+ ?% ?off", "up ?to \\d+ ?%",
            "flat (?:$MONEY|\\d+ ?%)", "$MONEY off", "coupon", "promo ?code", "use code", "deal", "deals", "loot", "lowest price", "best price", "starting (?:at|from|@)",
            "cashback offer", "(?:get|earn|win|assured|extra) (?:up ?to )?(?:\\d+ ?% )?cashback", "voucher", "freebie", "free (?:delivery|shipping|gift|trial)",
            "bogo", "buy (?:1|one) get (?:1|one)", "combo", "chhoot", "bumper", "bonanza", "big billion", "great indian festival", "end of (?:season|reason)", "eoss",
            "mega", "festive", "diwali (?:sale|offer|deal)s?"),
        r(Category.PROMOTIONS, 1.8f, "Asks you to buy or sign up", "shop now", "buy now", "order now", "book now", "apply now", "grab (?:now|it|yours)?", "explore now",
            "download (?:the )?app", "install now", "click to apply", "open now", "recharge now", "subscribe now", "visit (?:our|your nearest)", "don't miss", "dont miss",
            "abhi order", "t&c", "t&cs", "tnc", "\\*t&c apply"),
        r(Category.PROMOTIONS, 1.6f, "Creates urgency to buy", "hurry", "limited (?:time|period|stock|offer)", "ends (?:today|tonight|soon|at midnight|in)", "last (?:day|chance|few hours)",
            "today only", "only today", "sirf aaj", "missing out", "your cart misses you", "left in your cart", "back in stock", "new arrivals?", "just launched",
            "craving", "hungry\\?"),
        r(Category.PROMOTIONS, 1.6f, "Advertises a loan, card or plan", "pre-?approved", "eligible for", "instant (?:loan|personal loan|credit)", "loan (?:of )?up ?to",
            "lifetime free", "get (?:an? )?(?:loan|credit card|card)", "upgrade to", "interest rates? (?:starting|as low)", "unlimited (?:5g|data|calls)",
            "(?:\\d+ )?months? (?:of )?\\w+ free", "earn \\d+ ?% cashback"),
        r(Category.PROMOTIONS, 1.2f, "Has an opt-out footer", "unsubscribe", "opt[- ]?out", "reply stop", "txt stop", "sms stop", "to stop (?:receiving|these)"),

        // ---- Service notices ------------------------------------------------------------------
        r(Category.UPDATES, 1.8f, "Service notification", "appointment", "reminder", "scheduled", "your request", "ticket (?:no|number|id)", "complaint",
            "has been (?:updated|approved|activated|registered|processed)", "successfully", "alert", "notification", "status", "results?", "report", "renewal",
            "booking (?:id|is confirmed|confirmed)", "is confirmed"),

        r(Category.WORK, 2.4f, "Mentions work topics", "meeting", "standup", "stand-up", "deadline", "client", "project", "office", "manager", "shift", "deploy",
            "invoice approved", "sprint", "presentation", "conference call", "team", "colleague", "payroll", "salary"),

        r(Category.PERSONAL, 1.6f, "Conversational language", "hi", "hey", "hello", "love", "miss you", "thanks", "thank you", "haha", "lol", "see you", "call me",
            "where are you", "how are you", "dinner", "lunch", "tonight", "weekend", "mom", "dad", "bro", "bhai", "yaar", "happy birthday", "congrats", "ok", "okay",
            "sure", "yes", "no worries", "kal", "aaj", "kya", "hai", "bhej", "check"),
    )

    private val CATEGORIES = Category.entries.filter { it != Category.SPAM }
    private val SERVICE = listOf(Category.OTP, Category.BANKING, Category.PAYMENTS, Category.BILLS, Category.SHOPPING, Category.DELIVERY, Category.TRAVEL)

    data class Scores(val probabilities: Map<Category, Float>, val reasons: Map<Category, List<String>>) {
        val top: Category get() = probabilities.maxByOrNull { it.value }!!.key
        val topProbability: Float get() = probabilities.getValue(top)
    }

    fun score(input: MessageInput, entities: List<Entity>): Scores {
        val text = input.text
        val raw = HashMap<Category, Float>()
        val reasons = HashMap<Category, MutableList<String>>()
        fun get(c: Category) = raw[c] ?: 0f
        fun add(c: Category, w: Float, why: String) {
            raw[c] = get(c) + w
            reasons.getOrPut(c) { mutableListOf() }.let { if (why !in it) it += why }
        }
        fun scale(c: Category, f: Float) { raw[c] = get(c) * f }

        for (rule in RULES) {
            val hits = rule.pattern.findAll(text).take(3).count()
            if (hits > 0) add(rule.category, rule.weight * (1f + 0.35f * (hits - 1)), rule.reason)
        }

        val otp = entities.firstOrNull { it.type == EntityType.OTP }
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

        // A message is only an OTP when it actually carries a code. "Never share your OTP" is a
        // standard footer on bank alerts and must not drag them out of Transactions.
        if (otp == null) scale(Category.OTP, 0.1f) else {
            add(Category.OTP, 2f, "Contains a one-time code")
            scale(Category.BANKING, 0.35f); scale(Category.PAYMENTS, 0.35f); scale(Category.SHOPPING, 0.4f); scale(Category.DELIVERY, 0.4f)
            scale(Category.PROMOTIONS, 0.2f)
        }

        val sender = input.sender
        when (sender.kind) {
            SenderKind.CONTACT -> add(Category.PERSONAL, 3f, "Sender is in your contacts")
            SenderKind.PHONE_NUMBER -> add(Category.PERSONAL, 2.2f, "Sent from a personal phone number")
            SenderKind.ALPHANUMERIC, SenderKind.SHORT_CODE -> {
                add(Category.UPDATES, 0.8f, "Sent by a business sender ID")
                scale(Category.PERSONAL, 0.15f)
                scale(Category.WORK, 0.4f)
            }
            SenderKind.EMAIL -> Unit
        }
        if (sender.kind == SenderKind.CONTACT || sender.kind == SenderKind.PHONE_NUMBER) {
            // People mention money and links too; only overwhelming evidence should move them.
            for (c in SERVICE + Category.PROMOTIONS + Category.UPDATES) if (c != Category.OTP) scale(c, 0.5f)
        }

        // What the message is about, before we look at who sent it.
        val promo = get(Category.PROMOTIONS)
        val service = SERVICE.maxOf { get(it) }

        val brand = SenderNames.brandCategory(sender.address)
        if (brand != null) {
            val name = SenderNames.pretty(sender.address)
            val marketing = promo >= 2.6f && promo > service
            when {
                marketing -> add(Category.PROMOTIONS, 1f, "Marketing from $name")
                brand == Category.BANKING || brand == Category.PAYMENTS -> {
                    // "Anything from the bank" is a transaction unless it is clearly an advert.
                    add(brand, 3f, "Sent by $name")
                }
                else -> add(brand, 2.2f, "Sent by $name")
            }
            if (brand == Category.SHOPPING || brand == Category.DELIVERY) {
                // Refunds to Swiggy Money or a shop wallet belong with the order, not the bank.
                scale(Category.BANKING, 0.4f); scale(Category.PAYMENTS, 0.4f)
            }
        }

        when (sender.commercialSuffix) {
            CommercialSuffix.PROMOTIONAL -> {
                add(Category.PROMOTIONS, 6f, "Sender header is marked promotional (-P)")
                for (c in SERVICE) if (c != Category.OTP) scale(c, 0.25f)
            }
            CommercialSuffix.TRANSACTIONAL -> {
                scale(Category.PROMOTIONS, 0.2f)
                add(if (otp != null) Category.OTP else Category.BANKING, 1f, "Sender header is marked transactional (-T)")
            }
            CommercialSuffix.GOVERNMENT -> add(Category.UPDATES, 2f, "Sender header is marked government (-G)")
            CommercialSuffix.SERVICE, null -> Unit
        }

        // Real activity beats marketing lines in footers ("Get 10% cashback on your next order").
        val txn = maxOf(get(Category.OTP), get(Category.BANKING), get(Category.PAYMENTS))
        val order = maxOf(get(Category.SHOPPING), get(Category.DELIVERY))
        val strongEvidence = (entities.any { it.type == EntityType.AMOUNT && it.attributes["direction"] in setOf("debit", "credit") }) ||
            entities.any { it.type == EntityType.ACCOUNT_REF } || order >= 5f
        if (sender.commercialSuffix != CommercialSuffix.PROMOTIONAL && strongEvidence && maxOf(txn, order) >= 3.5f) scale(Category.PROMOTIONS, 0.35f)

        // A small prior so short, keyword-free messages default sensibly.
        val prior = if (sender.isBusinessLike) Category.UPDATES else Category.PERSONAL
        add(prior, 0.6f, if (prior == Category.PERSONAL) "Looks like a personal message" else "Sent by a business sender ID")

        // Softmax over non-spam categories; spam is decided by SpamDetector.
        val exps = CATEGORIES.associateWith { exp((get(it) * 0.9f).toDouble()).toFloat() }
        val total = exps.values.sum()
        return Scores(exps.mapValues { it.value / total }, reasons)
    }
}
