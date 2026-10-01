package app.yarn.intelligence

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Deterministic, offline extraction of actionable entities from message text.
 * Works on every device with zero downloads; the app layers ML Kit entity extraction on top
 * when its model is available, and merges results.
 */
class EntityExtractor(
    /** Interpret 03/04/2025 as 3 April (true) or March 4 (false). */
    private val dayFirst: Boolean = true,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    fun extract(text: String, messageTimeMillis: Long = System.currentTimeMillis()): List<Entity> {
        if (text.isBlank()) return emptyList()
        val found = ArrayList<Entity>()
        found += urls(text)
        found += emails(text)
        found += upiIds(text)
        found += otps(text)
        found += amounts(text)
        found += accountRefs(text)
        found += trackingNumbers(text)
        found += orderIds(text)
        found += bookingRefs(text)
        found += flights(text)
        found += dates(text, messageTimeMillis)
        found += addresses(text)
        found += phones(text)
        return resolveOverlaps(found)
    }

    // ---- URLs, emails, UPI ------------------------------------------------------------------

    private fun urls(text: String): List<Entity> = URL.findAll(text).mapNotNull { m ->
        var value = m.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '\'', '"')
        // Keep a closing paren only if the URL contains an opening one (Wikipedia-style links).
        if (m.value.endsWith(")") && value.count { it == '(' } > value.count { it == ')' }) value += ")"
        if (value.length < 4) return@mapNotNull null
        val host = UrlTools.host(value) ?: return@mapNotNull null
        if (!value.contains("://") && !value.startsWith("www.", true) && host.substringAfterLast('.') !in BARE_TLDS) {
            return@mapNotNull null
        }
        Entity(EntityType.URL, value, UrlTools.withScheme(value), m.range.first, m.range.first + value.length, mapOf("host" to host))
    }.toList()

    private fun emails(text: String): List<Entity> = EMAIL.findAll(text).map {
        Entity(EntityType.EMAIL, it.value, it.value.lowercase(), it.range.first, it.range.last + 1)
    }.toList()

    private fun upiIds(text: String): List<Entity> = UPI.findAll(text).mapNotNull { m ->
        val handle = m.groupValues[2].lowercase()
        if (handle in UPI_HANDLES || handle.startsWith("ok")) {
            Entity(EntityType.UPI_ID, m.value, m.value.lowercase(), m.range.first, m.range.last + 1)
        } else {
            null
        }
    }.toList()

    // ---- OTP ------------------------------------------------------------------------------

    private fun otps(text: String): List<Entity> {
        val keywords = OTP_KEYWORD.findAll(text).toList()
        if (keywords.isEmpty()) return emptyList()
        val candidates = OTP_CANDIDATE.findAll(text).mapNotNull { m ->
            val code = m.groupValues[1].ifEmpty { m.groupValues[2] + m.groupValues[3] }
            if (code.isEmpty()) return@mapNotNull null
            val before = text.substring(maxOf(0, m.range.first - 12), m.range.first).lowercase()
            val after = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 12)).lowercase()
            // Skip money, account fragments, phone chunks, years and times.
            if (CURRENCY_BEFORE.containsMatchIn(before)) return@mapNotNull null
            if (ACCOUNT_BEFORE.containsMatchIn(before)) return@mapNotNull null
            if (after.startsWith(":") && after.drop(1).take(2).all { it.isDigit() }) return@mapNotNull null
            if (after.trimStart().startsWith("rs") || after.trimStart().startsWith("inr")) return@mapNotNull null
            if (before.endsWith("+") || (before.trimEnd().lastOrNull()?.isDigit() == true && before.endsWith(" "))) {
                if (!before.contains("code") && !before.contains("otp")) return@mapNotNull null
            }
            m to code
        }.toList()
        if (candidates.isEmpty()) return emptyList()

        val strong = STRONG_OTP.find(text)
        val best = if (strong != null) {
            candidates.minByOrNull { (m, _) -> kotlin.math.abs(m.range.first - strong.range.first) }
        } else {
            candidates.minByOrNull { (m, _) ->
                keywords.minOf { k ->
                    val d = if (m.range.first >= k.range.last) m.range.first - k.range.last else (k.range.first - m.range.last) * 2
                    kotlin.math.abs(d)
                }
            }
        } ?: return emptyList()
        val (match, code) = best
        val nearest = keywords.minOf { kotlin.math.abs(it.range.first - match.range.first) }
        if (strong == null && nearest > 80) return emptyList()
        val attrs = HashMap<String, String>()
        OTP_VALIDITY.find(text)?.let { v ->
            val n = v.groupValues[1].toIntOrNull()
            if (n != null) attrs["expiresInMinutes"] = (if (v.groupValues[2].lowercase().startsWith("h")) n * 60 else n).toString()
        }
        return listOf(Entity(EntityType.OTP, match.value, code, match.range.first, match.range.last + 1, attrs))
    }

    // ---- Money ----------------------------------------------------------------------------

    private fun amounts(text: String): List<Entity> {
        val out = ArrayList<Entity>()
        for (m in AMOUNT_PREFIX.findAll(text)) {
            val currency = normalizeCurrency(m.groupValues[1]) ?: continue
            val num = m.groupValues[2]
            out += amountEntity(text, m.range.first, m.range.last + 1, m.value, currency, num)
        }
        for (m in AMOUNT_SUFFIX.findAll(text)) {
            val currency = normalizeCurrency(m.groupValues[2]) ?: continue
            out += amountEntity(text, m.range.first, m.range.last + 1, m.value, currency, m.groupValues[1])
        }
        return out
    }

    private fun amountEntity(text: String, start: Int, end: Int, raw: String, currency: String, num: String): Entity {
        val value = num.replace(",", "")
        val window = text.substring(maxOf(0, start - 60), minOf(text.length, end + 40)).lowercase()
        val direction = when {
            DEBIT.containsMatchIn(window) -> "debit"
            CREDIT.containsMatchIn(window) -> "credit"
            DUE.containsMatchIn(window) -> "due"
            BALANCE.containsMatchIn(window) -> "balance"
            else -> null
        }
        val attrs = buildMap {
            put("currency", currency)
            direction?.let { put("direction", it) }
        }
        return Entity(EntityType.AMOUNT, raw.trim(), "$currency $value", start, end, attrs)
    }

    private fun normalizeCurrency(raw: String): String? = when (raw.lowercase().trimEnd('.').replace(" ", "")) {
        "₹", "rs", "inr", "rupees", "rupee" -> "INR"
        "$", "usd", "us$", "dollars", "dollar" -> "USD"
        "€", "eur", "euro", "euros" -> "EUR"
        "£", "gbp", "pounds" -> "GBP"
        "¥", "jpy", "yen" -> "JPY"
        "aed", "dhs" -> "AED"
        "sgd", "s$" -> "SGD"
        "cad", "c$" -> "CAD"
        "aud", "a$" -> "AUD"
        else -> null
    }

    private fun accountRefs(text: String): List<Entity> = ACCOUNT.findAll(text).map { m ->
        val digits = m.groupValues[1].filter { it.isDigit() }
        Entity(EntityType.ACCOUNT_REF, m.value, digits, m.range.first, m.range.last + 1)
    }.toList()

    // ---- Logistics & bookings -----------------------------------------------------------------

    private fun trackingNumbers(text: String): List<Entity> {
        val out = ArrayList<Entity>()
        val lower = text.lowercase()
        fun add(m: MatchResult, group: Int, carrier: String?) {
            val range = m.groups[group]!!.range
            val value = m.groups[group]!!.value.replace(" ", "").uppercase()
            val attrs = buildMap {
                carrier?.let { put("carrier", it) }
                trackingUrl(carrier, value)?.let { put("trackUrl", it) }
            }
            out += Entity(EntityType.TRACKING_NUMBER, m.groups[group]!!.value, value, range.first, range.last + 1, attrs)
        }
        UPS.findAll(text).forEach { add(it, 0, "UPS") }
        USPS.findAll(text).forEach { if (lower.contains("usps") || lower.contains("postal") || lower.contains("track")) add(it, 0, "USPS") }
        if (lower.contains("fedex")) FEDEX.findAll(text).forEach { add(it, 1, "FedEx") }
        if (lower.contains("dhl")) DHL.findAll(text).forEach { add(it, 1, "DHL") }
        GENERIC_TRACKING.findAll(text).forEach { m ->
            val v = m.groupValues[1]
            if (v.any { it.isDigit() } && v.length >= 8) {
                val carrier = when {
                    lower.contains("fedex") -> "FedEx"
                    lower.contains("dhl") -> "DHL"
                    lower.contains("usps") -> "USPS"
                    lower.contains("ups ") -> "UPS"
                    lower.contains("india post") || lower.contains("speed post") -> "India Post"
                    lower.contains("delhivery") -> "Delhivery"
                    lower.contains("bluedart") || lower.contains("blue dart") -> "Blue Dart"
                    else -> null
                }
                add(m, 1, carrier)
            }
        }
        return out
    }

    private fun trackingUrl(carrier: String?, number: String): String? = when (carrier) {
        "UPS" -> "https://www.ups.com/track?tracknum=$number"
        "USPS" -> "https://tools.usps.com/go/TrackConfirmAction?tLabels=$number"
        "FedEx" -> "https://www.fedex.com/fedextrack/?trknbr=$number"
        "DHL" -> "https://www.dhl.com/global-en/home/tracking/tracking-express.html?submit=1&tracking-id=$number"
        "Delhivery" -> "https://www.delhivery.com/track-v2/package/$number"
        "Blue Dart" -> "https://www.bluedart.com/web/guest/trackdartresult?trackFor=0&trackNo=$number"
        "India Post" -> "https://www.indiapost.gov.in/_layouts/15/dop.portal.tracking/trackconsignment.aspx"
        else -> null
    }

    private fun orderIds(text: String): List<Entity> = ORDER.findAll(text).mapNotNull { m ->
        val v = m.groupValues[1]
        if (!v.any { it.isDigit() }) return@mapNotNull null
        val g = m.groups[1]!!.range
        Entity(EntityType.ORDER_ID, v, v.uppercase(), g.first, g.last + 1)
    }.toList()

    private fun bookingRefs(text: String): List<Entity> = BOOKING.findAll(text).mapNotNull { m ->
        val v = m.groupValues[2]
        if (v.length < 5 || v.lowercase() in STOPWORD_REFS) return@mapNotNull null
        val g = m.groups[2]!!.range
        Entity(EntityType.BOOKING_REF, v, v.uppercase(), g.first, g.last + 1, mapOf("label" to m.groupValues[1].trim()))
    }.toList()

    private fun flights(text: String): List<Entity> {
        if (!FLIGHT_CONTEXT.containsMatchIn(text)) return emptyList()
        return FLIGHT.findAll(text).mapNotNull { m ->
            val code = m.groupValues[1] + m.groupValues[2]
            if (m.groupValues[1] in setOf("RS", "PM", "AM", "NO", "OK", "ID")) return@mapNotNull null
            Entity(EntityType.FLIGHT, m.value, code, m.range.first, m.range.last + 1)
        }.toList()
    }

    private fun addresses(text: String): List<Entity> = US_ADDRESS.findAll(text).map {
        Entity(EntityType.ADDRESS, it.value.trim(), it.value.trim(), it.range.first, it.range.last + 1, confidence = 0.7f)
    }.toList()

    private fun phones(text: String): List<Entity> = PHONE.findAll(text).mapNotNull { m ->
        val digits = m.value.filter { it.isDigit() }
        if (digits.length !in 8..15) return@mapNotNull null
        // Reference numbers are not phone numbers: "UPI: 664017641271", "Ref No 512…", "UTR …", "AWB …".
        val before = text.substring(maxOf(0, m.range.first - 24), m.range.first).lowercase()
        if (REFERENCE_BEFORE.containsMatchIn(before)) return@mapNotNull null
        // A long unbroken digit run is an ID unless it carries a country code (+…, 91…, 0…) or is toll-free.
        val plain = m.value.trim()
        if (plain.all { it.isDigit() } && digits.length >= 12 && !digits.startsWith("91") && !digits.startsWith("0")) return@mapNotNull null
        val value = (if (m.value.trim().startsWith("+")) "+" else "") + digits
        Entity(EntityType.PHONE, m.value.trim(), value, m.range.first, m.range.first + m.value.trimEnd().length)
    }.toList()

    // ---- Dates ------------------------------------------------------------------------------

    private fun dates(text: String, messageTime: Long): List<Entity> {
        val base = LocalDateTime.ofInstant(Instant.ofEpochMilli(messageTime), zone)
        val dates = ArrayList<Pair<IntRange, LocalDate>>()

        for (m in ISO_DATE.findAll(text)) {
            safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { dates += m.range to it }
        }
        for (m in NUMERIC_DATE.findAll(text)) {
            if (dates.any { it.first.overlaps(m.range) }) continue
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            val (day, month) = when {
                a > 12 -> a to b
                b > 12 -> b to a
                dayFirst -> a to b
                else -> b to a
            }
            if (y == null && m.groupValues[0].contains('.')) continue // "3.5" is a number, not a date
            if (y == null && (m.groupValues[1].length < 2 || m.groupValues[2].length < 2)) continue // "1/2", "24/7"
            val date = if (y != null) safeDate(y, month, day) else inferYear(base.toLocalDate(), month, day)
            date?.let { dates += m.range to it }
        }
        for (m in DAY_MONTH.findAll(text)) {
            if (dates.any { it.first.overlaps(m.range) }) continue
            val month = monthOf(m.groupValues[2]) ?: continue
            val day = m.groupValues[1].toInt()
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            (if (y != null) safeDate(y, month, day) else inferYear(base.toLocalDate(), month, day))?.let { dates += m.range to it }
        }
        for (m in MONTH_DAY.findAll(text)) {
            if (dates.any { it.first.overlaps(m.range) }) continue
            val month = monthOf(m.groupValues[1]) ?: continue
            val day = m.groupValues[2].toInt()
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            (if (y != null) safeDate(y, month, day) else inferYear(base.toLocalDate(), month, day))?.let { dates += m.range to it }
        }
        for (m in RELATIVE_DAY.findAll(text)) {
            if (dates.any { it.first.overlaps(m.range) }) continue
            val word = m.value.lowercase()
            val today = base.toLocalDate()
            val date = when {
                word.startsWith("day after") -> today.plusDays(2)
                word.startsWith("tomorrow") -> today.plusDays(1)
                word.startsWith("today") || word.startsWith("tonight") -> today
                else -> {
                    val dow = DAYS.entries.firstOrNull { word.contains(it.key) }?.value ?: continue
                    if (word.startsWith("next")) today.with(TemporalAdjusters.next(dow)).let { if (it.minusDays(7).isAfter(today)) it.minusDays(7) else it }
                    else today.with(TemporalAdjusters.nextOrSame(dow))
                }
            }
            dates += m.range to date
        }

        val times = TIME.findAll(text).mapNotNull { m ->
            val hourRaw = (m.groups[1]?.value ?: m.groups[4]?.value)?.toIntOrNull() ?: return@mapNotNull null
            val minute = (m.groups[2]?.value ?: m.groups[5]?.value)?.toIntOrNull() ?: 0
            val ampm = m.groups[3]?.value?.lowercase()?.replace(".", "")
            var hour = hourRaw
            if (ampm != null) {
                if (hour !in 1..12) return@mapNotNull null
                if (ampm == "pm" && hour != 12) hour += 12
                if (ampm == "am" && hour == 12) hour = 0
            } else if (hour > 23) return@mapNotNull null
            if (minute > 59) return@mapNotNull null
            m.range to LocalTime.of(hour, minute)
        }.toList()

        val out = ArrayList<Entity>()
        val usedTimes = HashSet<IntRange>()
        for ((range, date) in dates) {
            val time = times.firstOrNull { (tr, _) ->
                tr !in usedTimes && (tr.first - range.last in 1..30 || range.first - tr.last in 1..30)
            }
            val start = if (time != null) minOf(range.first, time.first.first) else range.first
            val end = if (time != null) maxOf(range.last, time.first.last) + 1 else range.last + 1
            time?.let { usedTimes += it.first }
            val value = if (time != null) LocalDateTime.of(date, time.second).toString() else date.toString()
            out += Entity(
                EntityType.DATE_TIME, text.substring(start, end), value, start, end,
                buildMap { if (time == null) put("allDay", "true") },
            )
        }
        if (EVENT_CONTEXT.containsMatchIn(text)) {
            for ((range, time) in times) {
                if (range in usedTimes || out.any { it.start <= range.first && it.end > range.last }) continue
                var dt = LocalDateTime.of(base.toLocalDate(), time)
                if (dt.isBefore(base)) dt = dt.plusDays(1)
                out += Entity(EntityType.DATE_TIME, text.substring(range.first, range.last + 1), dt.toString(), range.first, range.last + 1, mapOf("timeOnly" to "true"), 0.7f)
            }
        }
        return out
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.of(y, m, d) }.getOrNull()

    private fun inferYear(today: LocalDate, month: Int, day: Int): LocalDate? {
        val candidate = safeDate(today.year, month, day) ?: return null
        // Year-less dates more than a month in the past almost always mean next year's occurrence.
        return if (candidate.isBefore(today.minusDays(30))) candidate.plusYears(1) else candidate
    }

    private fun monthOf(s: String): Int? = MONTHS.entries.firstOrNull { s.lowercase().startsWith(it.key) }?.value

    private fun IntRange.overlaps(o: IntRange) = first <= o.last && o.first <= last

    // ---- Overlap resolution -----------------------------------------------------------------

    private fun resolveOverlaps(all: List<Entity>): List<Entity> {
        val sorted = all.sortedWith(compareBy<Entity> { PRIORITY.indexOf(it.type) }.thenByDescending { it.end - it.start })
        val kept = ArrayList<Entity>()
        for (e in sorted) {
            if (kept.none { it.start < e.end && e.start < it.end }) kept += e
        }
        return kept.sortedBy { it.start }
    }

    companion object {
        private val PRIORITY = listOf(
            EntityType.URL, EntityType.EMAIL, EntityType.UPI_ID, EntityType.OTP, EntityType.TRACKING_NUMBER,
            EntityType.AMOUNT, EntityType.ACCOUNT_REF, EntityType.BOOKING_REF, EntityType.ORDER_ID,
            EntityType.FLIGHT, EntityType.DATE_TIME, EntityType.ADDRESS, EntityType.PHONE,
        )

        private val BARE_TLDS = setOf(
            "com", "net", "org", "in", "io", "co", "me", "ly", "gl", "info", "biz", "xyz", "top", "app",
            "dev", "us", "uk", "link", "site", "online", "shop", "club", "live", "page", "to", "gov", "edu",
            "click", "icu", "buzz", "gd", "at", "ru", "de", "fr", "tk", "ml", "cf", "ga", "gq", "sbs", "cfd", "id", "gg",
        )

        private val URL = Regex(
            "(?i)(?<![@\\w.-])(?:(?:https?://|www\\.)[^\\s<>\"]+|(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,24}(?:/[^\\s<>\"]*)?)",
        )
        private val EMAIL = Regex("(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,24}\\b")
        private val UPI = Regex("\\b([a-zA-Z0-9._-]{2,64})@([a-zA-Z]{2,32})\\b(?!\\w|\\.\\w)")
        private val UPI_HANDLES = setOf(
            "upi", "ybl", "ibl", "axl", "paytm", "apl", "okaxis", "oksbi", "okhdfcbank", "okicici", "icici",
            "hdfcbank", "sbi", "axisbank", "kotak", "idfcbank", "yesbank", "fbl", "pnb", "boi", "barodampay",
            "freecharge", "jupiteraxis", "slice", "naviaxis", "waicici", "wahdfcbank", "wasbi", "waaxis",
        )

        private val OTP_KEYWORD = Regex(
            "(?i)\\b(otp|one[- ]time[- ](?:password|passcode|pin|code)|verification|verify|security code|login code|" +
                "sign[- ]?in code|auth(?:entication|orization)? code|access code|confirmation code|passcode|pin|code|2fa|mfa|tan)\\b",
        )
        private val OTP_CANDIDATE = Regex("(?<![\\d.,/:])(?:[A-Z]-)?(\\d{4,8})(?![\\d.,/]\\d)|(?<![\\d.,])(\\d{3})[- ](\\d{3})(?![\\d])")
        private val STRONG_OTP = Regex(
            "(?i)(?:\\d{4,8}\\s+is\\s+(?:your|the)|(?:otp|code|pin|passcode)\\s*(?:is|:|-)\\s*(?:[A-Z]-)?\\d)",
        )
        private val OTP_VALIDITY = Regex("(?i)valid(?:\\s+only)?\\s+(?:for|till|upto|up to|within)\\s+(\\d{1,3})\\s*(min|mins|minutes|hrs|hours|hour)")
        private val CURRENCY_BEFORE = Regex("(₹|rs\\.?|inr|\\$|usd|€|£|eur|gbp)\\s*$")
        private val ACCOUNT_BEFORE = Regex("(a/c|acct|account|card|ending|xx|\\*\\*|no\\.)\\s*[:#]?\\s*$")

        private const val NUM = "(\\d{1,3}(?:,\\d{2,3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)"
        private val AMOUNT_PREFIX = Regex("(?i)(₹|rs\\.?|inr|us\\$|usd|\\$|€|eur|£|gbp|¥|jpy|aed|sgd|s\\$|cad|c\\$|aud|a\\$)\\s?$NUM(?![\\d])")
        private val AMOUNT_SUFFIX = Regex("(?i)(?<![\\w.])$NUM\\s?(inr|usd|eur|gbp|rupees?|dollars?|euros?)\\b")
        private val DEBIT = Regex("\\b(debited|spent|paid|sent|withdrawn|purchase|charged|deducted|txn of|payment of|transferred)\\b")
        private val CREDIT = Regex("\\b(credited|received|refund(?:ed)?|deposited|cashback|reversed)\\b")
        private val DUE = Regex("\\b(due|bill|outstanding|minimum amount|payable)\\b")
        private val BALANCE = Regex("\\b(balance|bal|avl|available)\\b")

        private val ACCOUNT = Regex("(?i)\\b(?:a/c|acct|account|card)(?:\\s*(?:no\\.?|number|ending(?:\\s+(?:with|in))?))?\\s*[:#]?\\s*((?:[x*]{2,}|\\.{2,})\\s?\\d{3,6})")

        private val UPS = Regex("\\b1Z[0-9A-Z]{16}\\b")
        private val USPS = Regex("\\b(?:9[2-5]\\d{20}|\\d{20,22}|[A-Z]{2}\\d{9}US)\\b")
        private val FEDEX = Regex("\\b(\\d{12}|\\d{15})\\b")
        private val DHL = Regex("\\b(\\d{10})\\b")
        private val GENERIC_TRACKING = Regex("(?i)\\b(?:tracking|track|awb|consignment|shipment|docket)\\s*(?:no\\.?|number|id|#|code)?\\s*(?:is|:|-)?\\s*([A-Z0-9]{8,30})\\b")
        private val ORDER = Regex("(?i)\\border\\s*(?:no\\.?|number|id|#)?\\s*(?:is|:|-)?\\s*#?([A-Z0-9][A-Z0-9-]{4,29})\\b")
        private val BOOKING = Regex("(?i)\\b(pnr|booking\\s*(?:ref(?:erence)?|id|code|no\\.?)|confirmation\\s*(?:no\\.?|number|code|#)|reservation\\s*(?:no\\.?|number|code|#))\\s*(?:is|:|-)?\\s*([A-Z0-9]{5,12})\\b")
        private val STOPWORD_REFS = setOf("number", "details", "status", "please", "confirmed")
        private val FLIGHT_CONTEXT = Regex("(?i)\\b(flight|boarding|departs?|departure|gate|airline|terminal)\\b")
        private val FLIGHT = Regex("\\b([A-Z]{2}|[A-Z]\\d|\\d[A-Z])\\s?-?(\\d{2,4})\\b")

        private val US_ADDRESS = Regex(
            "\\b\\d{1,6}\\s+(?:[A-Z][A-Za-z0-9.']*\\s){1,4}(?:St|Street|Ave|Avenue|Rd|Road|Blvd|Boulevard|Ln|Lane|Dr|Drive|Ct|Court|Way|Pl|Place|Pkwy|Parkway|Hwy|Highway|Ter|Terrace|Cir|Circle)\\b\\.?" +
                "(?:,?\\s+(?:Apt|Suite|Ste|Unit|#)\\s*[\\w-]+)?(?:,\\s*[A-Z][A-Za-z .]+)?(?:,\\s*[A-Z]{2}(?:\\s+\\d{5}(?:-\\d{4})?)?)?",
        )
        private val REFERENCE_BEFORE = Regex("(?:upi|ref|utr|rrn|txn|trxn|transaction|a/c|acct|account|awb|order|pnr|folio|policy|id|no\\.?)\\s*(?:no\\.?|id|number|#)?\\s*[:#-]?\\s*$")
        private val PHONE = Regex("(?<![\\w+/])\\+?\\d[\\d\\s().-]{6,18}\\d(?![\\w/])")

        private val ISO_DATE = Regex("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b")
        private val NUMERIC_DATE = Regex("\\b(\\d{1,2})[/.-](\\d{1,2})(?:[/.-](\\d{4}|\\d{2}))?\\b")
        private const val MONTH_RE = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
        private val DAY_MONTH = Regex("(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?[\\s-]+(?:of\\s+)?$MONTH_RE\\.?(?:,?[\\s-]+(\\d{4}|\\d{2})(?!\\d|:))?\\b")
        private val MONTH_DAY = Regex("(?i)\\b$MONTH_RE\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?!\\d|:)(?:,?\\s+(\\d{4}))?\\b")
        private val RELATIVE_DAY = Regex("(?i)\\b(day after tomorrow|tomorrow|today|tonight|(?:this |next |on )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday))\\b")
        private val TIME = Regex("(?i)\\b(\\d{1,2})(?::(\\d{2}))?\\s?(am|pm|a\\.m\\.|p\\.m\\.)(?![a-z])|\\b([01]?\\d|2[0-3]):([0-5]\\d)(?:\\s?(?:hrs|hours|h))?\\b")
        private val EVENT_CONTEXT = Regex("(?i)\\b(meet|meeting|appointment|call|dinner|lunch|breakfast|party|flight|departure|reminder|scheduled|pickup|pick up|delivery|interview|class|show|reservation|at)\\b")

        private val MONTHS = linkedMapOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
            "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        )
        private val DAYS = linkedMapOf(
            "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
            "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
            "sunday" to DayOfWeek.SUNDAY,
        )
    }
}
