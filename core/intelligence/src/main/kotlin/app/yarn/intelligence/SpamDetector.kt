package app.yarn.intelligence

import kotlin.math.pow

/** How aggressively to filter. Users choose this in settings. */
enum class SpamSensitivity(val spamThreshold: Float, val scamThreshold: Float) {
    LOW(0.65f, 0.8f),
    MEDIUM(0.55f, 0.7f),
    HIGH(0.4f, 0.55f),
}

/**
 * Heuristic spam, scam and phishing scoring. Signals are combined with a noisy-OR so several
 * weak hints add up while no single weak hint can condemn a message. Messages from saved
 * contacts are never auto-filtered, only warned about when they carry a dangerous link.
 */
class SpamDetector(private val sensitivity: SpamSensitivity = SpamSensitivity.MEDIUM) {

    private class Signal(val pattern: Regex, val weight: Float, val reason: String, val scam: Boolean, val needsLink: Boolean = false)

    private fun s(weight: Float, reason: String, scam: Boolean, vararg phrases: String) =
        Signal(Regex("(?i)\\b(?:${phrases.joinToString("|")})"), weight, reason, scam)

    /**
     * Wording that's only a scam with a way to act on it, a link or a mobile number: "update your
     * payment method in the app" or "KYC is pending, visit your branch" is routine.
     */
    private fun withLink(weight: Float, reason: String, scam: Boolean, vararg phrases: String) =
        Signal(Regex("(?i)\\b(?:${phrases.joinToString("|")})"), weight, reason, scam, needsLink = true)

    /** Hindi wording: Java's \\b only knows Latin letters, so these phrases match anywhere. */
    private fun hindi(weight: Float, reason: String, scam: Boolean, vararg phrases: String) =
        Signal(Regex("(?:${phrases.joinToString("|")})"), weight, reason, scam)

    private val signals = listOf(
        s(0.45f, "Threatens account suspension or blocking", true,
            "(?:account|card|wallet|sim|number|service)s? (?:will be |has been |is |are )?(?:suspended|blocked|locked|deactivated|disabled|terminated|closed)",
            "(?:suspend|block|deactivate)(?:ed)? (?:your|ur) (?:account|card|sim)"),
        withLink(0.45f, "Asks you to verify identity or update KYC via a link", true,
            "(?:update|complete|verify|re-?verify) (?:your |ur )?(?:kyc|pan|aadhaar|identity|account details|billing|payment (?:info|details|method))",
            "kyc (?:is |has )?(?:expired|expiring|pending|update|suspended|failed|blocked)", "verify (?:your )?(?:vehicle|account|card|kyc|bank) details",
            "requires? re-?verification"),
        s(0.55f, "Asks for a code, PIN or password", true,
            "(?:share|send|tell|forward|reply with) (?:me |us )?(?:the |your |ur )?(?:otp|code|pin|password|cvv|card number)",
            "enter (?:your )?(?:upi )?pin to (?:receive|get|claim)", "(?:share|give) (?:the )?otp \\d* ?with (?:our|the) "),
        s(0.55f, "Asks for your ID or card details", true,
            "(?:send|submit|share|update|provide) (?:your )?(?:aadhaa?r|pan|card|bank|kyc)(?:,? (?:and |& )?(?:aadhaa?r|pan|card|bank|kyc))* (?:details|number|copy|card)",
            "(?:send|submit|share) (?:your )?(?:aadhaa?r|pan)\\b"),
        s(0.35f, "Claims unusual or unauthorised activity", true,
            "unusual (?:activity|sign-?in|login)", "unauthori[sz]ed (?:transaction|access|login|attempt)", "suspicious (?:activity|login|transaction)"),
        s(0.4f, "Prize or lottery claim", true,
            "(?:you(?:'ve| have)? |u |your (?:mobile |phone )?number (?:has )?)(?:won|been selected)", "lottery", "lucky draw", "lucky winner",
            "claim (?:your |ur )?(?:prize|reward|gift|cash)", "jackpot", "winner", "kbc", "kaun banega crorepati"),
        s(0.4f, "Undelivered-parcel or fee scam wording", true,
            "(?:package|parcel|shipment) (?:is |was |has been )?(?:on hold|held|could not be delivered|undeliverable|returned)",
            "(?:redelivery|customs|shipping|clearance) fee", "incomplete (?:address|shipping)", "update (?:your )?(?:delivery )?address"),
        s(0.4f, "Unpaid toll/fine or tax refund wording", true,
            "unpaid toll", "toll (?:balance|due|violation)", "outstanding (?:toll|fine|penalty)", "tax refund", "refund (?:is )?pending", "irs", "hmrc", "income tax refund",
            "(?:pending|unpaid|outstanding|overdue) (?:traffic )?e-?challan", "avoid (?:legal action|court|penalty|arrest|fir)",
            "e-?challan (?:[\\w.]+ ){0,6}(?:pending|due|unpaid|generated)", "(?:face|or) (?:court|legal) action"),
        s(0.6f, "Promises a big prize", true,
            "won (?:rs\\.? ?|₹ ?|inr ?)?[\\d,.]+ ?(?:lakh|lakhs|lac|crore|cr)\\b", "(?:lucky draw|lottery|jackpot)[^.]{0,40}(?:won|winner|claim)",
            "(?:won|winner)[^.]{0,40}(?:lucky draw|lottery|kbc|jackpot)"),
        s(0.45f, "Promises money from a government scheme", true,
            "(?:pm|pradhan mantri|modi|sarkari|government|govt) (?:\\w+ ){0,2}yojana", "free (?:laptop|scooty|mobile|tablet|smartphone|recharge) yojana",
            "yojana[^.]{0,60}(?:claim|register|apply now|click)"),
        s(0.6f, "Sells phone numbers", false,
            "vip (?:mobile )?(?:no|nos|number|numbers)\\b", "fancy (?:mobile )?(?:no|nos|number|numbers)\\b", "similar (?:&|and) vip", "want similar",
            "(?:golden|platinum|diamond|vip) numbers?", "numbers? (?:available|for sale)"),
        s(0.5f, "Adult or dating spam", false,
            "(?:want|wanna|like) to (?:be|make) friends?", "make friends", "saw your profile", "(?:hot|beautiful|sexy) (?:and \\w+ )?(?:girls?|ladies|women|bhabhi)",
            "girls? available", "call girls?", "escort", "massage (?:service|parlour|parlor)"),
        s(0.5f, "Pretends to be family on a new number", true,
            "(?:this is my|mera|my) new number[\\s\\S]{0,100}?(?:send|bhej|transfer|upi|urgent|zarurat|jaldi)", "naya number[\\s\\S]{0,100}?(?:bhej|zarurat|jaldi|upi|paise)",
            "(?:phone|mobile) (?:broke|is broken|got broken|kharab|kho gaya|lost)[\\s\\S]{0,100}?(?:send|bhej|transfer)"),
        s(0.5f, "Impersonates police, customs or a government officer", true,
            "illegal (?:items|drugs|goods|activity|activities|parcel)", "(?:drugs|narcotics) (?:found|in your)", "held by customs", "seized by",
            "(?:cbi|ncb|ed|cyber ?cell|crime branch|police|customs|narcotics|trai|rbi) officer", "digital arrest", "(?:will|could) be arrested",
            "arrest warrant", "money laundering", "press 1 (?:to|or)", "police case", "case (?:has been )?registered against",
            "download (?:the )?(?:attached )?(?:\\w+ )?(?:verification|kyc|support|security) app",
            "(?:son|daughter|husband|wife|brother|sister|father|mother) (?:has been|is|was|got) (?:arrested|detained|caught)", "in police custody"),
        s(0.45f, "Asks you to return money \"sent by mistake\"", true,
            "(?:sent|transferred|credited) (?:rs\\.? ?|₹ ?)?[\\d,]+ (?:to your (?:account|number|upi|wallet) )?by mistake",
            "by mistake[^.]{0,60}(?:return|send (?:it )?back|refund)", "return (?:it|my money|the money) (?:to|on) my",
            "galti se[^.]{0,80}(?:wapas|lauta|return|bhej)", "wapas (?:bhej|kar|de) (?:do|dijiye|dena)"),
        s(0.45f, "Asks you to install an app or share your screen", true,
            "(?:install|download) (?:the |this )?(?:app|apk)[^.]{0,40}(?:redeem|kyc|reward|points|refund|update)", "anydesk", "teamviewer", "quick ?support", "screen ?shar(?:e|ing) app"),
        s(0.5f, "Asks for a fee before paying out a loan or prize", true,
            "(?:refundable |processing |clearance |release |registration |verification )fee to (?:release|receive|claim|unlock|disburse)",
            "pay (?:a |the )?(?:\\w+ )?fee (?:to|before) (?:release|receive|claim|unlock|disburse|get)"),
        s(0.5f, "Promises guaranteed investment returns", true,
            "guaranteed (?:\\d+ ?% )?(?:daily |weekly |monthly )?(?:returns?|profits?)", "\\d+ ?% (?:daily|weekly) (?:returns?|profits?)"),
        s(0.4f, "Job or investment lure", true,
            "work from home", "part[- ]time (?:job|work)", "registration fee", "join our (?:private|vip|exclusive) group", "no experience required",
            "(?:refundable |processing |clearance |release )fee to (?:release|receive|claim|unlock)", "pay (?:a )?(?:\\w+ )?fee to (?:release|receive|claim|unlock)", "direct joining", "no exam", "job vacancy", "earn (?:rs\\.?|₹|\\$)?\\s?\\d", "daily (?:income|earning|profit)", "guaranteed (?:returns?|profit)",
            "double your", "crypto(?:currency)? (?:investment|trading|profit)", "telegram(?!:? (?:code|login|account|app\\b))", "task[- ]based", "stock tips", "trading signals",
            "vip (?:group|tips|signals|members?)", "(?:daily|weekly|monthly) \\d+ ?% (?:profit|returns?)", "\\d+ ?% (?:daily|weekly|monthly) (?:profit|returns?)",
            "(?:per|each) (?:review|like|task|rating)", "rate (?:hotels|restaurants|products) (?:on google )?(?:and|&) earn", "prepaid task"),
        s(0.4f, "Electricity/utility disconnection threat", true,
            "(?:electricity|power|gas|connection) (?:will be |is )?(?:disconnected|cut)", "disconnection (?:tonight|today|notice)",
            "supply will (?:stop|be cut|be disconnected)",
            "bijli (?:\\w+ ){0,3}(?:kaat|kat|cut)"),
        // Power companies give days of notice; "tonight" with a link to pay is the scam.
        withLink(0.3f, "Threatens to cut your power tonight", true,
            "(?:disconnect(?:ed|ion)?|cut|stop|switched off) (?:\\w+ ){0,2}tonight", "tonight at \\d"),
        hindi(0.4f, "Electricity/utility disconnection threat", true, "बिजली (?:\\S+ ){0,3}(?:काट|कट)", "कनेक्शन (?:\\S+ ){0,3}(?:काट|कट)"),
        hindi(0.45f, "Asks you to verify identity or update KYC via a link", true, "केवाईसी (?:\\S+ ){0,3}(?:अपडेट|पूरा|समाप्त)"),
        hindi(0.4f, "Prize or lottery claim", true, "लॉटरी", "इनाम", "जीती है", "जीत लिया", "लकी ड्रा"),
        s(0.5f, "Threatens to shame you to your contacts", true,
            "(?:send|share|forward) (?:your )?(?:photos?|pictures?|images?|morphed) (?:\\w+ ){0,2}to (?:all )?(?:your )?(?:contacts|family|relatives)",
            "(?:all )?your contacts will (?:be informed|know|receive)"),
        s(0.35f, "Urgent pressure language", false,
            "urgent", "immediately", "act now", "call now", "call immediately", "final (?:notice|warning|reminder)", "within 24 ?h(?:ou)?rs", "expires? today", "last chance", "respond now"),
        s(0.3f, "Pushes you to click a link", false,
            "click (?:here|the link|below|on the link|now)", "tap (?:here|the link)", "visit (?:the )?link", "open (?:the )?link", "log ?in (?:here|now|at)"),
        s(0.25f, "Free-gift or loan bait", false,
            "free (?:gift|iphone|recharge|voucher|money)", "gift card", "loan (?:approved|sanctioned|offer)", "pre-?approved loan", "instant loan",
            "(?:check|improve|boost|increase|fix) (?:your )?credit score", "free credit (?:score|report)"),
        s(0.3f, "Adult or gambling content", false,
            "casino", "betting", "bet now", "rummy", "teen patti", "hot singles", "dating site", "xxx", "win real cash", "real money games?",
            "(?:first|welcome) deposit", "deposit bonus", "aviator", "satta", "matka"),
        s(0.2f, "Wrong-number conversation opener", false,
            "is this \\w+\\?", "sorry,? wrong number", "hi,? are you (?:free|there)", "long time no see"),
    )

    private val safeHints = Regex("(?i)\\b(?:do not share|don't share|never share|never ask|will never ask|not requested by you|if not you|if this wasn't you)\\b")
    private val brandMention = Regex("(?i)\\b(?:paypal|amazon|apple|icloud|netflix|usps|fedex|dhl|ups|irs|hdfc|icici|sbi|axis|kotak|paytm|phonepe|flipkart|chase|wells ?fargo|bank of america|whatsapp|e-?zpass|india post|royal mail|hmrc|jio|airtel|aadhaar|uidai|income ?tax|epfo|irctc|rbi|trai|npci)\\b")
    private val bankMention = Regex("(?i)\\b(?:bank|a/c|account|kyc|credit card|debit card)\\b")

    /** "Call 98765 43210", "contact officer on +91 9876543210", "सम्पर्क करें 9876543210". */
    private val CALL_MOBILE = Regex("(?i)(?:call|contact|whatsapp|sampark|संपर्क|सम्पर्क|फोन)(?:\\S*\\s+){0,5}?(?:on |at |no\\.? ?)?(?:\\+?91[- ]?)?[6-9]\\d{4} ?\\d{5}\\b|(?:\\+?91[- ]?)?[6-9]\\d{9}\\s*(?:पर )?(?:call|कॉल|सम्पर्क|संपर्क)")
    private val MONEY_OR_ACCOUNT = Regex("(?i)debited|deducted|charged|suspicious|deactivat|flagged|auto-?renew|cancel|refund|blocked|deactivat|suspend|kyc|prize|lottery|claim|officer|adhikari|अधिकारी|बिजली|bijli|electricity|arrest|customs|parcel|courier|loan|log-?in|helpline|customer care|won|winner|kbc|लॉटरी|इनाम")

    private val ACCOUNT_THREAT = Regex("(?i)suspicious|flagged|blocked|deactivat|suspend|frozen|\\bkyc\\b|prize|lottery|\\bwon\\b|winner|arrest|customs|disconnect")
    private val CARD_POINTS = Regex("(?i)(?:credit|debit) card (?:reward )?points|card reward")
    private val REWARD_EXPIRY = Regex("(?i)(?:reward|loyalty)? ?points?[^.]{0,40}(?:expir|lapse)[\\s\\S]{0,70}?(?:redeem|claim|click|using|here)")
    private val MOBILE = Regex("(?<![\\dA-Za-z])(?:\\+?91[- ]?)?[6-9]\\d{4} ?\\d{5}(?!\\d)")
    private val PHONE_LIKE = Regex("(?<!\\d)(?:[6-9]\\d[\\d -]{7,12}\\d)(?!\\d)")

    fun assess(input: MessageInput, entities: List<Entity>): SpamAssessment {
        if (!input.isIncoming) return SpamAssessment(SpamVerdict.CLEAN, 0f, emptyList())
        val text = input.text
        val sender = input.sender
        val reasons = mutableListOf<String>()
        var notSpam = 1f
        var notScam = 1f
        fun hit(weight: Float, reason: String, scam: Boolean) {
            notSpam *= (1f - weight)
            if (scam) notScam *= (1f - weight)
            if (reason !in reasons) reasons += reason
        }

        // Each signal counts once, a little more when several of its phrases appear ("work from home",
        // "earn 5000 daily", "Telegram").
        var scamWords = false
        var lure = false
        val linked = entities.any { it.type == EntityType.URL } || MOBILE.containsMatchIn(text)
        signals.forEach { sig ->
            if (sig.needsLink && !linked) return@forEach
            val n = sig.pattern.findAll(text).map { it.value.lowercase() }.distinct().take(3).count()
            if (n > 0) {
                // Registered businesses repeat their own wording ("could not be delivered… update address");
                // only unregistered senders get the extra weight.
                val k = if (sender.commercialSuffix == null) n else 1
                hit(1f - (1f - sig.weight).pow(1f + 0.5f * (k - 1)), sig.reason, sig.scam)
                if (sig.scam || sig.reason.startsWith("Adult")) scamWords = true
                if (sig.reason.startsWith("Pushes") || sig.reason.startsWith("Free-gift")) lure = true
            }
        }

        // A well-known brand's own registered sender ID (India's DLT headers can't be borrowed):
        // its alerts are never filtered, whatever their wording. Headers from before 2025 carry no
        // -S/-T/-P/-G suffix ("VM-HDFCBK"), so a missing suffix proves nothing.
        val brand = if (sender.kind == SenderKind.ALPHANUMERIC || sender.kind == SenderKind.SHORT_CODE) SenderNames.brandKey(sender.address) else null
        val registered = sender.commercialSuffix == CommercialSuffix.SERVICE || sender.commercialSuffix == CommercialSuffix.TRANSACTIONAL ||
            sender.commercialSuffix == CommercialSuffix.GOVERNMENT

        val urls = entities.filter { it.type == EntityType.URL }
        val risky = HashSet<String>()
        urls.forEach { e ->
            val risk = UrlTools.assess(e.value, brand, registered)
            if (risk.score >= 0.3f) {
                risky += e.value
                risk.reasons.forEach { hit(minOf(risk.score, 0.7f), it, scam = risk.score >= 0.5f) }
            }
        }

        // A named company, or "your bank account" alongside other warning signs ("bank ka kaam ho gaya"
        // from a friend is just conversation).
        val mentionsBrand = brandMention.containsMatchIn(text) || bankMention.containsMatchIn(text) && reasons.isNotEmpty()
        val hasUrl = urls.isNotEmpty()
        // People share real shop links ("this phone is on sale, amzn.in/d/…"): only a link that isn't
        // the brand's own makes a phone number pretending to be a company.
        val foreignLink = urls.any { !UrlTools.isOfficialBrandDomain(it.value) }
        if (mentionsBrand && sender.kind == SenderKind.PHONE_NUMBER && foreignLink) {
            hit(0.55f, "Claims to be a company but comes from a personal phone number with a link", true)
        }
        if (mentionsBrand && sender.kind == SenderKind.EMAIL && foreignLink) {
            hit(0.5f, "Claims to be a company but comes from an email address with a link", true)
        }
        // Scam wording from a stranger's number: businesses in India send from registered sender IDs,
        // so a 10-digit number talking prizes, KYC, customs or "sent by mistake" is almost never genuine.
        if (sender.kind == SenderKind.PHONE_NUMBER && CALL_MOBILE.containsMatchIn(text) && MONEY_OR_ACCOUNT.containsMatchIn(text) ||
            // Businesses give 1800 numbers or landlines; "account blocked, call 98765 43210" is a scam from anyone but the brand.
            sender.kind == SenderKind.ALPHANUMERIC && brand == null && CALL_MOBILE.containsMatchIn(text) && ACCOUNT_THREAT.containsMatchIn(text)
        ) {
            hit(0.45f, "Asks you to call a mobile number about your money or account", true)
            scamWords = true
        }
        val stranger = sender.kind == SenderKind.PHONE_NUMBER || sender.kind == SenderKind.EMAIL
        // "Your reward points expire today, redeem using <link>": the classic points phish.
        // Card points belong to a bank, so from any other sender ID with an outside link they're the same phish.
        if ((stranger || sender.kind == SenderKind.ALPHANUMERIC && brand == null && CARD_POINTS.containsMatchIn(text)) && foreignLink && REWARD_EXPIRY.containsMatchIn(text)) {
            hit(0.5f, "Reward points with a link to redeem", true)
            scamWords = true
        }
        // A list of phone numbers from a stranger is a number seller.
        if (stranger && PHONE_LIKE.findAll(text).count() >= 4) hit(0.5f, "Sells phone numbers", false)
        if (stranger && (scamWords || lure && foreignLink)) {
            hit(0.4f, "Comes from an unknown personal number", true)
        }
        if (sender.kind == SenderKind.PHONE_NUMBER && hasUrl && urls.none { UrlTools.isOfficialBrandDomain(it.value) } && reasons.isNotEmpty()) {
            hit(0.2f, "Unknown number sent a link", false)
        }
        if (TextNormalizer.mixedScriptWordCount(text) > 0) hit(0.45f, "Uses lookalike characters to disguise words", true)
        if (TextNormalizer.containsZeroWidth(text)) hit(0.3f, "Contains hidden characters", false)
        val letters = text.filter { it.isLetter() }
        if (letters.length > 30 && letters.count { it.isUpperCase() } > letters.length * 0.7) hit(0.15f, "Written mostly in capitals", false)

        // Legit OTP/bank alerts carry "do not share" warnings; dampen unless a dangerous link exists.
        if (safeHints.containsMatchIn(text) && risky.isEmpty() && sender.kind != SenderKind.PHONE_NUMBER && sender.kind != SenderKind.EMAIL) {
            notSpam = 1f - (1f - notSpam) * 0.4f
            notScam = 1f - (1f - notScam) * 0.3f
        }
        if (sender.commercialSuffix == CommercialSuffix.TRANSACTIONAL || sender.commercialSuffix == CommercialSuffix.GOVERNMENT) {
            if (risky.isEmpty()) { notSpam = 1f - (1f - notSpam) * 0.5f; notScam = 1f - (1f - notScam) * 0.5f }
        } else if (sender.commercialSuffix != null && risky.isEmpty()) {
            // A DLT-registered header (-P/-S) is a verified business: marketing pressure ("apply now",
            // "pre-approved loan") makes it an offer, not junk. Scam wording still counts in full.
            notSpam = 1f - (1f - notSpam) * 0.5f
        }

        var spamScore = 1f - notSpam
        var scamScore = 1f - notScam
        if (sender.kind == SenderKind.CONTACT || sender.isStarredContact) {
            // Never auto-filter people the user knows; keep the warning for dangerous links only.
            spamScore *= 0.3f
            scamScore = if (risky.isNotEmpty()) scamScore * 0.6f else scamScore * 0.2f
        }

        val promo = sender.commercialSuffix == CommercialSuffix.PROMOTIONAL ||
            Regex("(?i)\\b(?:unsubscribe|opt[- ]out|reply stop|txt stop|sms stop|\\d+% off|sale|coupon|offer)\\b").containsMatchIn(text)

        val verdict = when {
            brand != null -> if (promo) SpamVerdict.PROMOTIONAL else SpamVerdict.CLEAN
            scamScore >= sensitivity.scamThreshold -> SpamVerdict.SCAM
            spamScore >= sensitivity.spamThreshold -> SpamVerdict.SPAM
            promo -> SpamVerdict.PROMOTIONAL
            else -> SpamVerdict.CLEAN
        }
        val score = maxOf(spamScore, scamScore)
        return SpamAssessment(verdict, score, if (verdict == SpamVerdict.CLEAN && score < 0.2f) emptyList() else reasons, risky)
    }
}
