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
            "login code", "auth(?:entication)? code", "2fa", "passcode", "(?:delivery |secret |security |ride )?pin is", "code to verify", "is your (?:\\w+ )?code", "code is", "code for (?:login|logging in|verification|sign[- ]?in)", "ओटीपी", "सत्यापन कोड"),
        r(Category.OTP, 1.5f, "Asks you not to share a code", "do not share", "don't share", "dont share", "never share", "valid (?:for|till) \\d+ ?(?:min|minutes|mins|seconds)"),

        // ---- Banks, cards, investments ----------------------------------------------------------
        r(Category.BANKING, 2.5f, "Mentions an account debit/credit", "debited", "credited", "a/c", "acct", "account balance", "avl bal", "avl\\.? bal", "available balance",
            "avl limit", "available limit", "withdrawn", "withdrawal", "atm", "neft", "imps", "rtgs", "ach", "wire transfer", "spent on", "spent at",
            "(?:txn|trxn|transaction|purchase) (?:of|for) $MONEY", "डेबिट", "क्रेडिट", "जमा", "निकासी", "खाते"),
        r(Category.BANKING, 1.8f, "Mentions a card or bank account", "credit card", "debit card", "card ending", "card xx\\d+", "savings account", "current account", "net ?banking",
            "statement", "emi", "loan account", "cheque", "kyc", "home branch", "card (?:has been |is )?(?:blocked|activated|dispatched|delivered)", "bank"),
        r(Category.BANKING, 2.2f, "Mentions investments", "demat", "ipo", "fpo", "sip", "mutual fund", "folio", "nav", "dividend", "allotment", "allotted",
            "securities", "shares", "trading account", "order to (?:buy|sell)", "(?:buy|sell) order", "executed", "traded", "funds (?:of $MONEY )?(?:added|withdrawn|credited)", "margin"),

        r(Category.PAYMENTS, 2.5f, "Mentions a payment", "upi", "paid to", "paid $MONEY", "payment (?:of|received|successful|failed|is successful|done)", "sent to",
            "received from", "money request", "collect request", "wallet", "vpa", "transaction id", "txn id", "ref no", "upi ref", "utr", "autopay",
            "$MONEY (?:has been |is |was )?(?:debited|credited|deducted|paid|received|added|refunded|transferred)",
            "(?:debited|credited|deducted|added|refunded) (?:from|to|in|into) your \\w+ (?:money|wallet|pay|balance|account)",
            "\\w+ (?:money|pay|wallet) (?:balance|is credited|has been credited|credited|debited)", "(?:money|pay) balance",
            "refund (?:of|for|has been|is|was|amount)", "refunded", "cashback (?:of|has been|is|credited|received|added)", "cashback credited",
            "gift card (?:of|worth|has been|is|was|added|credited|redeemed|balance)", "gift card has", "added to your (?:\\w+ )?(?:account|wallet|balance)",
            "balance (?:is|of)", "recharge (?:of|successful|is successful|done)", "fastag",
            "(?:has been |was |is )?charged (?:to|on) your", "charged $MONEY", "$MONEY (?:has been |was )?charged"),

        r(Category.BILLS, 2.5f, "Mentions a bill or due date", "bill (?:of|for|is|amount|generated)", "due date", "due on", "is due", "amount due", "amt due", "overdue",
            "postpaid", "electricity bill", "gas bill", "water bill", "broadband", "premium (?:of $MONEY )?(?:for|is|due)", "insurance premium", "autopay", "auto-debit",
            "mandate", "subscription renew(?:s|al|ed)", "plan expires", "validity (?:ends|expires)",
            "emi (?:of|for|is|was)", "bounced", "pay immediately"),

        // ---- Orders, food, groceries, deliveries ------------------------------------------------
        r(Category.SHOPPING, 3f, "Talks about your order", "your order(?!s| now| to (?:buy|sell))", "your (?!next |first )(?:\\w+ ){1,2}order(?!s| now| to (?:buy|sell))", "order (?:#|no\\.?|id|number) ?[a-z]*\\d[\\w-]*",
            "order (?:has been |is |was )?(?:placed|confirmed|received|packed|ready|cancelled|canceled|returned|refunded|on (?:its|the) way)",
            "thank you for (?:ordering|shopping|your order|your purchase)", "thanks for (?:ordering|shopping)", "items? (?:in|from) your order",
            "order total", "order value", "invoice", "return (?:request|pickup|initiated|of)", "exchange (?:request|pickup)",
            "replacement", "rate your (?:order|delivery|experience|meal|purchase)", "enjoy your (?:meal|food|order)", "being prepared", "is preparing your"),
        r(Category.DELIVERY, 2.8f, "Mentions a shipment", "shipped", "out for delivery", "delivered", "dispatched", "in transit", "tracking", "track (?:your|it)",
            "awb", "courier", "parcel", "package", "shipment", "delivery (?:partner|agent|executive|associate|attempt|scheduled|slot|boy)",
            "arriving (?:today|tomorrow|by|in)", "will (?:arrive|reach you)", "scheduled for delivery", "picked up",
            "unable to deliver", "could not deliver", "डिलीवर", "ऑर्डर", "couldn't deliver", "delivery (?:failed|attempted|was attempted|rescheduled)", "attempted delivery",
            "on (?:its|the) way", "reached your location", "near your location", "valet"),

        // ---- Travel ---------------------------------------------------------------------------
        r(Category.TRAVEL, 2.8f, "Mentions travel", "flight", "boarding", "pnr", "check-?in", "gate", "departure", "arrival", "train", "coach", "berth", "hotel",
            "itinerary", "cab", "ride", "captain", "bike [a-z]{2}\\d", "your driver", "driver \\w+", "uber", "ola", "rapido", "airport", "terminal", "e-ticket", "ticket booked", "bus ticket"),

        // ---- Marketing ------------------------------------------------------------------------
        r(Category.PROMOTIONS, 2.6f, "Looks like marketing", "sale", "sale is live", "offer", "offers", "discount", "discounts", "\\d+ ?% ?off", "up ?to \\d+ ?%",
            "flat (?:$MONEY|\\d+ ?%)", "$MONEY off", "coupon", "promo ?code", "use code", "deal", "deals", "loot", "lowest price", "best price", "starting (?:at|from|@)",
            "cashback offer", "(?:get|earn|win|assured|extra) (?:up ?to )?(?:$MONEY |\\d+ ?% )?cashback", "cashback (?:of )?up ?to", "get \\d+ ?% back", "voucher", "freebie", "free (?:delivery|shipping|gift|trial)",
            "bogo", "buy (?:1|one) get (?:1|one)", "combo", "chhoot", "bumper", "bonanza", "big billion", "great indian festival", "end of (?:season|reason)", "eoss",
            "mega", "festive", "diwali (?:sale|offer|deal)s?"),
        r(Category.PROMOTIONS, 1.8f, "Asks you to buy or sign up", "shop now", "buy now", "order now", "book now", "apply now", "grab (?:now|it|yours)?", "explore now",
            "download (?:the )?app", "install now", "click to apply", "open now", "recharge now", "subscribe now", "visit (?:our|your nearest)", "don't miss", "dont miss",
            "abhi order", "t&c", "t&cs", "tnc", "\\*t&c apply", "pay now", "add money", "try now", "check it out", "claim now"),
        r(Category.PROMOTIONS, 1.6f, "Creates urgency to buy", "hurry", "limited (?:time|period|stock|offer)", "ends (?:today|tonight|soon|at midnight|in)", "last (?:day|chance|few hours)",
            "today only", "only today", "sirf aaj", "this (?:diwali|festive season|weekend|holi|navratri)", "missing out", "your cart misses you", "left in your cart", "back in stock", "new arrivals?", "just launched",
            "craving", "hungry\\?", "new on \\w+", "delivered in \\d+ ?(?:min|mins|minutes)", "in just \\d+ ?(?:min|mins|minutes)"),
        r(Category.PROMOTIONS, 1.6f, "Advertises a loan, card or plan", "pre-?approved", "eligible for", "instant (?:loan|personal loan|credit)", "loan (?:of )?up ?to",
            "lifetime free", "get (?:an? )?(?:loan|credit card|card)", "upgrade to", "interest rates? (?:starting|as low)", "unlimited (?:5g|data|calls)",
            "5g unlimited", "with $MONEY plan", "$MONEY plan", "recharge (?:before|with)",
            "(?:\\d+ )?months? (?:of )?\\w+ free", "earn \\d+ ?% cashback"),
        r(Category.PROMOTIONS, 2.4f, "Advertises a financial product or perk", "reward points? (?:are|is) waiting", "redeem (?:them|it|now|your)", "coins (?:are|is) expiring",
            "complimentary", "exclusive (?:for you|benefits?|offers?|dining|deals?)", "unlock (?:exclusive|special)", "zero (?:joining|annual|processing|forex)",
            "lounge (?:access|voucher)", "claim (?:it|now|your|before|by)", "plan your", "attractive (?:rates?|interest)", "earn up ?to", "open an? (?:\\w+ )?account (?:in|online|now|today)",
            "know more", "avail (?:now|it|today)", "no (?:documents?|income proof|paperwork)(?: (?:needed|required))?", "home loans?", "at \\d+(?:\\.\\d+)?% p\\.?a",
            "apply (?:today|online)", "test ride", "unbelievable price", "best prices?", "lowest prices?", "save up ?to", "compare plans", "for less",
            "get it in minutes", "order from top", "metal card", "\\dx rewards", "welcome (?:rewards|benefits|bonus)", "convert (?:your|it|now|to)", "easy emis?", "low interest", "book a test ride",
            "refer (?:a friend|now|and earn)", "earn [\\d,]+ (?:\\w+ ){0,2}(?:points|coins)", "invest now", "\\bnfo\\b", "start (?:a|your) sip", "starting $MONEY",
            "get a quote", "insure your", "bonus (?:\\w+ )?points on joining", "is waiting for you", "upgrade",
            "(?:now |starting )?at (?:just |only )?$MONEY", "$MONEY only", "only $MONEY", "subscribe (?:now|today)", "free (?:delivery|movie|lounge)",
            "ऑफ़र", "ऑफर", "छूट", "सेल", "पाएं", "अभी रिचार्ज", "धमाका"),
        r(Category.PROMOTIONS, 1.2f, "Has an opt-out footer", "unsubscribe", "opt[- ]?out", "reply stop", "txt stop", "sms stop", "to stop (?:receiving|these)"),

        // ---- Service notices ------------------------------------------------------------------
        r(Category.UPDATES, 1.8f, "Service notification", "appointment", "reminder", "scheduled", "your request", "ticket (?:no|number|id)", "complaint",
            "has been (?:updated|approved|activated|registered|processed)", "successfully", "alert", "notification", "status", "results?", "report", "renewal",
            "booking (?:id|is confirmed|confirmed)", "is confirmed"),

        // Safety advice ("We never ask for your OTP") is a notice, not a transaction or a code.
        r(Category.UPDATES, 4f, "Safety advice from the sender", "never (?:asks?|calls?|call you|ask you|requests?|send)", "will never (?:ask|call|send|request)",
            "beware", "fraud(?:sters?| alerts?| calls?)", "cyber ?(?:crime|fraud)", "report (?:cyber )?fraud", "stay (?:safe|alert)", "be (?:aware|alert|cautious)",
            "do not (?:disclose|respond|click|install|scan)", "don't click", "cautions?", "fake (?:customer care|calls?|links?|numbers?)", "only for making payments",
            "never need to enter", "kehta hai", "सावधान", "धोखेबाज़", "कभी भी"),
        // Account and service changes where no money moved.
        r(Category.UPDATES, 2.6f, "Account or service change", "logged in", "login (?:alert|detected)", "new device",
            "(?:has been|have been|was) (?:blocked|unblocked|enabled|disabled|linked|delinked|changed|registered|submitted|closed|issued|activated|deactivated|completed|updated|approved)",
            "successfully (?:linked|registered|updated|changed|submitted|completed|closed|logged)", "will be unavailable", "scheduled maintenance", "as requested",
            "supply will be (?:disrupted|interrupted|affected)", "power (?:cut|outage|shutdown)", "for maintenance",
            "statement (?:for [\\w ]+? )?(?:has been (?:emailed|sent|mailed)|is (?:now )?available)", "will expire (?:on|in)", "new card will be", "renewal card",
            "unlocked", "kyc is due", "complete (?:your |video )?kyc", "on its way", "(?:card|cheque ?book) (?:has been |is )?(?:dispatched|delivered|shipped)", "dispatched via",
            "nominee", "\\bnoc\\b", "under (?:police )?verification", "policy (?:has been )?issued", "will mature", "auto-?renewed", "kyc (?:has been|is) (?:completed|updated|verified)"),
        r(Category.WORK, 2.4f, "Mentions work topics", "meeting", "standup", "stand-up", "deadline", "client", "project", "office", "manager", "shift", "deploy",
            "invoice approved", "sprint", "presentation", "conference call", "team", "colleague", "payroll", "salary"),

        r(Category.PERSONAL, 1.6f, "Conversational language", "hi", "hey", "hello", "love", "miss you", "thanks", "thank you", "haha", "lol", "see you", "call me",
            "where are you", "how are you", "dinner", "lunch", "tonight", "weekend", "mom", "dad", "bro", "bhai", "yaar", "happy birthday", "congrats", "ok", "okay",
            "sure", "yes", "no worries", "kal", "aaj", "kya", "hai", "bhej", "check"),
    )

    private val CATEGORIES = Category.entries.filter { it != Category.SPAM }

    // Money actually moved (not just a price in an advert).
    private val MONEY_LEAD = Regex(
        "(?i)(?:debited|credited|deducted|refunded|refund (?:of|for|has|is|amount)|\\bpaid\\b|payment (?:of|received|successful|is successful|done|failed)|" +
            "\\bspent\\b|charged|transferred|withdrawn|gift card (?:has|of|worth|is)|\\b\\w+ money (?:is|has)|cashback (?:of|has been|credited|is credited)|" +
            "added to your \\w* ?(?:balance|wallet|account)|has been executed|units allotted|\\bsip of|recharge (?:of (?:rs|inr|₹)|successful|is successful)|emi (?:of|for) (?:rs|inr|₹)|bounced|declined|levied|charges of (?:rs|inr|₹)|डेबिट|जमा)",
    )
    private val ORDER_LEAD = Regex(
        "(?i)(?:your (?!next |first )(?:\\w+ ){0,2}order(?!s| now| to (?:buy|sell))|order (?:#|no\\.|id|number|is|has|was|\\d)|shipped|shipment|dispatched|packed|" +
            "out for delivery|return pickup|parcel|package|courier|unable to deliver|(?:has been|was|is|got) delivered|delivered (?:to|at|today|successfully)|order delivered)",
    )
    private val DISCOUNT = Regex("(?i)\\d+ ?% ?(?:off|discount|cashback)|use (?:code|coupon)|promo ?code|flat $MONEY off|up ?to $MONEY off")
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

        // Did money actually move or fall due? Mentions of "account" or "card" alone don't count.
        val hasAmount = entities.any { it.type == EntityType.AMOUNT }
        val moneyEvidence = MONEY_LEAD.containsMatchIn(text) || (get(Category.BILLS) >= 2.5f && hasAmount) ||
            entities.any { it.type == EntityType.AMOUNT && it.attributes["direction"] in setOf("debit", "credit", "due", "balance") }
        if (otp == null && !moneyEvidence) { scale(Category.BANKING, 0.4f); scale(Category.PAYMENTS, 0.5f) }

        // A discount or promo code is an advert, unless money moved or an order is under way.
        if (otp == null && !moneyEvidence && DISCOUNT.containsMatchIn(text) && !ORDER_LEAD.containsMatchIn(text)) add(Category.PROMOTIONS, 3f, "Offers a discount")

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
                    // A bank's name only tips money messages into Transactions; banks also send
                    // safety advice, service notices and adverts, which are filed by what they say.
                    add(brand, if (moneyEvidence) 3f else 0.6f, "Sent by $name")
                }
                else -> add(brand, 1.2f, "Sent by $name")
            }
        }

        when (sender.commercialSuffix) {
            CommercialSuffix.PROMOTIONAL -> {
                // Operators only allow advertising on -P headers, so this is always an offer.
                add(Category.PROMOTIONS, 40f, "Sender header is marked promotional (-P)")
            }
            CommercialSuffix.TRANSACTIONAL -> {
                scale(Category.PROMOTIONS, 0.2f)
                add(if (otp != null) Category.OTP else Category.BANKING, 1f, "Sender header is marked transactional (-T)")
            }
            CommercialSuffix.GOVERNMENT -> add(Category.UPDATES, 2f, "Sender header is marked government (-G)")
            CommercialSuffix.SERVICE, null -> Unit
        }

        // When a message talks about both money and an order, its opening line says what it is
        // ("Payment of Rs 185 from Zomato Money…" is a transaction; "Your order is on the way…" is shopping).
        // Only for businesses (people mention money and orders casually) and never over a real code.
        // Banks don't ship orders: "your new card is on its way" is a service notice.
        val bankSender = brand == Category.BANKING || brand == Category.PAYMENTS
        if (bankSender && otp == null) { scale(Category.DELIVERY, 0.3f); scale(Category.SHOPPING, 0.3f) }
        if (sender.isBusinessLike && otp == null) {
            val moneyAt = MONEY_LEAD.find(text)?.range?.first
            val orderAt = ORDER_LEAD.find(text)?.range?.first
            if (moneyAt != null && (orderAt == null || moneyAt < orderAt)) add(Category.PAYMENTS, 5f, "Money moved")
            if (orderAt != null && !bankSender && (moneyAt == null || orderAt < moneyAt)) add(Category.SHOPPING, 5f, "About your order")
        }

        // Real activity beats marketing lines in footers ("Get 10% cashback on your next order").
        val txn = maxOf(get(Category.OTP), get(Category.BANKING), get(Category.PAYMENTS))
        val order = maxOf(get(Category.SHOPPING), get(Category.DELIVERY))
        val strongEvidence = (entities.any { it.type == EntityType.AMOUNT && it.attributes["direction"] in setOf("debit", "credit") }) ||
            (moneyEvidence && entities.any { it.type == EntityType.ACCOUNT_REF }) || order >= 5f
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
