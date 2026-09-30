package app.yarn.intelligence

/** How aggressively to filter. Users choose this in settings. */
enum class SpamSensitivity(val spamThreshold: Float, val scamThreshold: Float) {
    LOW(0.75f, 0.85f),
    MEDIUM(0.55f, 0.7f),
    HIGH(0.4f, 0.55f),
}

/**
 * Heuristic spam, scam and phishing scoring. Signals are combined with a noisy-OR so several
 * weak hints add up while no single weak hint can condemn a message. Messages from saved
 * contacts are never auto-filtered, only warned about when they carry a dangerous link.
 */
class SpamDetector(private val sensitivity: SpamSensitivity = SpamSensitivity.MEDIUM) {

    private class Signal(val pattern: Regex, val weight: Float, val reason: String, val scam: Boolean)

    private fun s(weight: Float, reason: String, scam: Boolean, vararg phrases: String) =
        Signal(Regex("(?i)\\b(?:${phrases.joinToString("|")})"), weight, reason, scam)

    private val signals = listOf(
        s(0.45f, "Threatens account suspension or blocking", true,
            "(?:account|card|wallet|sim|number|service)s? (?:will be |has been |is |are )?(?:suspended|blocked|locked|deactivated|disabled|terminated|closed)",
            "(?:suspend|block|deactivate)(?:ed)? (?:your|ur) (?:account|card|sim)"),
        s(0.45f, "Asks you to verify identity or update KYC via a link", true,
            "(?:update|complete|verify|re-?verify) (?:your |ur )?(?:kyc|pan|aadhaar|identity|account details|billing|payment (?:info|details|method))",
            "kyc (?:is )?(?:expired|pending|update|suspended)"),
        s(0.55f, "Asks for a code, PIN or password", true,
            "(?:share|send|tell|forward|reply with) (?:me |us )?(?:the |your |ur )?(?:otp|code|pin|password|cvv|card number)"),
        s(0.35f, "Claims unusual or unauthorised activity", true,
            "unusual (?:activity|sign-?in|login)", "unauthori[sz]ed (?:transaction|access|login|attempt)", "suspicious (?:activity|login|transaction)"),
        s(0.4f, "Prize or lottery claim", true,
            "(?:you(?:'ve| have)? |u )(?:won|been selected)", "lottery", "lucky draw", "claim (?:your |ur )?(?:prize|reward|gift|cash)", "jackpot", "winner"),
        s(0.4f, "Undelivered-parcel or fee scam wording", true,
            "(?:package|parcel|shipment) (?:is |was |has been )?(?:on hold|held|could not be delivered|undeliverable|returned)",
            "(?:redelivery|customs|shipping|clearance) fee", "incomplete (?:address|shipping)", "update (?:your )?(?:delivery )?address"),
        s(0.4f, "Unpaid toll/fine or tax refund wording", true,
            "unpaid toll", "toll (?:balance|due|violation)", "outstanding (?:toll|fine|penalty)", "tax refund", "refund (?:is )?pending", "irs", "hmrc", "income tax refund"),
        s(0.4f, "Job or investment lure", true,
            "work from home", "part[- ]time (?:job|work)", "earn (?:rs\\.?|₹|\\$)?\\s?\\d", "daily (?:income|earning|profit)", "guaranteed (?:returns?|profit)",
            "double your", "crypto(?:currency)? (?:investment|trading|profit)", "telegram", "task[- ]based"),
        s(0.4f, "Electricity/utility disconnection threat", true,
            "(?:electricity|power|gas|connection) (?:will be |is )?(?:disconnected|cut)", "disconnection (?:tonight|today)"),
        s(0.35f, "Urgent pressure language", false,
            "urgent", "immediately", "act now", "final (?:notice|warning|reminder)", "within 24 ?h(?:ou)?rs", "expires? today", "last chance", "respond now"),
        s(0.3f, "Pushes you to click a link", false,
            "click (?:here|the link|below|on the link|now)", "tap (?:here|the link)", "visit (?:the )?link", "open (?:the )?link", "log ?in (?:here|now|at)"),
        s(0.25f, "Free-gift or loan bait", false,
            "free (?:gift|iphone|recharge|voucher|money)", "gift card", "loan (?:approved|sanctioned|offer)", "pre-?approved loan", "instant loan", "credit score"),
        s(0.3f, "Adult or gambling content", false,
            "casino", "betting", "bet now", "rummy", "teen patti", "hot singles", "dating site", "xxx"),
        s(0.2f, "Wrong-number conversation opener", false,
            "is this \\w+\\?", "sorry,? wrong number", "hi,? are you (?:free|there)", "long time no see"),
    )

    private val safeHints = Regex("(?i)\\b(?:do not share|don't share|never share|never ask|will never ask|not requested by you|if not you|if this wasn't you)\\b")
    private val brandMention = Regex("(?i)\\b(?:paypal|amazon|apple|icloud|netflix|usps|fedex|dhl|ups|irs|hdfc|icici|sbi|axis|kotak|paytm|phonepe|flipkart|chase|wells ?fargo|bank of america|whatsapp|e-?zpass|india post|royal mail|hmrc|bank)\\b")

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

        signals.forEach { sig -> if (sig.pattern.containsMatchIn(text)) hit(sig.weight, sig.reason, sig.scam) }

        val urls = entities.filter { it.type == EntityType.URL }
        val risky = HashSet<String>()
        urls.forEach { e ->
            val risk = UrlTools.assess(e.value)
            if (risk.score >= 0.3f) {
                risky += e.value
                risk.reasons.forEach { hit(minOf(risk.score, 0.7f), it, scam = risk.score >= 0.5f) }
            }
        }

        val mentionsBrand = brandMention.containsMatchIn(text)
        val hasUrl = urls.isNotEmpty()
        if (mentionsBrand && sender.kind == SenderKind.PHONE_NUMBER && hasUrl) {
            hit(0.55f, "Claims to be a company but comes from a personal phone number with a link", true)
        }
        if (mentionsBrand && sender.kind == SenderKind.EMAIL && hasUrl) {
            hit(0.5f, "Claims to be a company but comes from an email address with a link", true)
        }
        if (sender.kind == SenderKind.PHONE_NUMBER && hasUrl && urls.none { UrlTools.isOfficialBrandDomain(it.value) } && reasons.isNotEmpty()) {
            hit(0.2f, "Unknown number sent a link", false)
        }
        if (TextNormalizer.mixedScriptWordCount(text) > 0) hit(0.45f, "Uses lookalike characters to disguise words", true)
        if (TextNormalizer.containsZeroWidth(text)) hit(0.3f, "Contains hidden characters", false)
        val letters = text.filter { it.isLetter() }
        if (letters.length > 30 && letters.count { it.isUpperCase() } > letters.length * 0.7) hit(0.15f, "Written mostly in capitals", false)

        // Legit OTP/bank alerts carry "do not share" warnings; dampen unless a dangerous link exists.
        if (safeHints.containsMatchIn(text) && risky.isEmpty()) {
            notSpam = 1f - (1f - notSpam) * 0.4f
            notScam = 1f - (1f - notScam) * 0.3f
        }
        if (sender.commercialSuffix == CommercialSuffix.TRANSACTIONAL || sender.commercialSuffix == CommercialSuffix.GOVERNMENT) {
            if (risky.isEmpty()) { notSpam = 1f - (1f - notSpam) * 0.5f; notScam = 1f - (1f - notScam) * 0.5f }
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
            scamScore >= sensitivity.scamThreshold -> SpamVerdict.SCAM
            spamScore >= sensitivity.spamThreshold -> SpamVerdict.SPAM
            promo -> SpamVerdict.PROMOTIONAL
            else -> SpamVerdict.CLEAN
        }
        val score = maxOf(spamScore, scamScore)
        return SpamAssessment(verdict, score, if (verdict == SpamVerdict.CLEAN && score < 0.2f) emptyList() else reasons, risky)
    }
}
