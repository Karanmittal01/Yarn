package app.yarn.intelligence

object UrlTools {
    private val SCHEME = Regex("(?i)^[a-z][a-z0-9+.-]*://")
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    val SHORTENERS = setOf(
        "bit.ly", "tinyurl.com", "goo.gl", "is.gd", "cutt.ly", "rb.gy", "shorturl.at", "tiny.cc",
        "ow.ly", "rebrand.ly", "t.ly", "s.id", "v.gd", "buff.ly", "bl.ink", "short.gy", "lnkd.in",
        "qrco.de", "urlz.fr", "tiny.one", "shorturl.gg", "u.to", "clck.ru",
    )

    /** TLDs disproportionately used for throwaway phishing domains. */
    val RISKY_TLDS = setOf(
        "xyz", "top", "click", "loan", "work", "gq", "tk", "ml", "cf", "ga", "icu", "buzz", "cyou",
        "rest", "sbs", "cfd", "monster", "quest", "bar", "lol", "mom", "support", "zip", "mov",
    )

    /** Brand keyword -> registrable domains that legitimately belong to it. */
    val BRANDS: Map<String, Set<String>> = mapOf(
        "paypal" to setOf("paypal.com", "paypal.me"),
        "amazon" to setOf("amazon.com", "amazon.in", "amazon.co.uk", "amazon.de", "amzn.to", "amzn.in", "amazon.ca", "amazon.com.au", "amazonaws.com", "amazonpay.in", "amazon.jobs"),
        "apple" to setOf("apple.com", "icloud.com"),
        "microsoft" to setOf("microsoft.com", "live.com", "outlook.com", "office.com"),
        "netflix" to setOf("netflix.com"),
        "google" to setOf("google.com", "g.co", "goo.gle", "googleusercontent.com", "googleapis.com", "withgoogle.com", "google.co.in"),
        "usps" to setOf("usps.com"),
        "fedex" to setOf("fedex.com"),
        "dhl" to setOf("dhl.com", "dhl.de", "dhl.co.uk"),
        "ups" to setOf("ups.com"),
        "irs" to setOf("irs.gov"),
        "hdfc" to setOf("hdfcbank.com", "hdfc.bank.in", "hdfcbank.bank.in"),
        "icici" to setOf("icicibank.com", "icici.bank.in"),
        "sbi" to setOf("onlinesbi.sbi", "sbi.co.in", "sbi.bank.in", "onlinesbi.com"),
        "axis" to setOf("axisbank.com", "axis.bank.in"),
        "kotak" to setOf("kotak.com", "kotak.bank.in"),
        "paytm" to setOf("paytm.com", "paytm.me"),
        "phonepe" to setOf("phonepe.com"),
        "flipkart" to setOf("flipkart.com", "fkrt.it", "dl.flipkart.com"),
        "chase" to setOf("chase.com"),
        "wellsfargo" to setOf("wellsfargo.com"),
        "bankofamerica" to setOf("bankofamerica.com", "bofa.com"),
        "whatsapp" to setOf("whatsapp.com", "wa.me"),
        "instagram" to setOf("instagram.com"),
        "facebook" to setOf("facebook.com", "fb.com", "fb.me"),
        "ezpass" to setOf("e-zpassny.com", "ezpassnj.com", "ezpassva.com", "e-zpassiag.com", "ezpassmd.com"),
        "indiapost" to setOf("indiapost.gov.in"),
        "royalmail" to setOf("royalmail.com"),
        "hmrc" to setOf("gov.uk"),
    )

    fun withScheme(url: String): String = if (SCHEME.containsMatchIn(url)) url else "https://$url"

    fun host(url: String): String? {
        val noScheme = url.replace(SCHEME, "")
        val authority = noScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase().trimEnd('.')
        return host.takeIf { it.isNotEmpty() && (it.contains('.') || IPV4.matches(it)) }
    }

    /** Approximate registrable domain: last two labels, or three for common second-level ccTLDs. */
    fun registrableDomain(host: String): String {
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val secondLevel = setOf("co", "com", "org", "net", "gov", "ac", "edu", "bank")
        return if (labels[labels.size - 2] in secondLevel && labels.last().length == 2) {
            labels.takeLast(3).joinToString(".")
        } else {
            labels.takeLast(2).joinToString(".")
        }
    }

    data class UrlRisk(val url: String, val score: Float, val reasons: List<String>)

    fun assess(url: String): UrlRisk {
        val reasons = mutableListOf<String>()
        var score = 0f
        val host = host(url) ?: return UrlRisk(url, 0.3f, listOf("Malformed link"))
        val domain = registrableDomain(host)
        val tld = host.substringAfterLast('.')
        if (IPV4.matches(host)) { score += 0.6f; reasons += "Link points to a raw IP address" }
        if (host.startsWith("xn--") || host.contains(".xn--")) { score += 0.5f; reasons += "Link uses an internationalised lookalike domain" }
        if (domain in SHORTENERS) { score += 0.3f; reasons += "Link is shortened and hides its destination" }
        if (tld in RISKY_TLDS) { score += 0.35f; reasons += "Link uses a domain ending often abused by scammers (.$tld)" }
        if (url.substringBefore('/').contains('@') || url.replace(SCHEME, "").substringBefore('/').contains('@')) {
            score += 0.5f; reasons += "Link hides its real destination with '@'"
        }
        if (host.count { it == '.' } >= 4) { score += 0.15f; reasons += "Link has an unusually deep subdomain" }
        if (host.count { it == '-' } >= 2) { score += 0.15f; reasons += "Domain contains several hyphens" }
        val compactHost = host.replace("-", "").replace(".", "")
        val hostTokens = host.split('.', '-').toSet()
        val deleeted = listOf(deleet(compactHost, 'i'), deleet(compactHost, 'l'))
        val deleetedTokens = hostTokens.map { deleet(it, 'i') }.toSet() + hostTokens.map { deleet(it, 'l') }
        for ((brand, official) in BRANDS) {
            // Short brand names ("ups", "sbi") must be a whole label to avoid hits like "groups".
            val mentions = if (brand.length <= 5) {
                brand in hostTokens || brand in deleetedTokens
            } else {
                compactHost.contains(brand) || deleeted.any { it.contains(brand) }
            }
            if (mentions && official.none { domain == it || host == it || host.endsWith(".$it") }) {
                score += 0.6f
                reasons += "Domain imitates ${brand.replaceFirstChar { it.uppercase() }} but is not an official ${brand} domain"
                break
            }
        }
        return UrlRisk(url, score.coerceAtMost(1f), reasons)
    }

    /** Undo common digit-for-letter substitutions ("amaz0n", "paypa1"). */
    private fun deleet(s: String, one: Char): String = buildString(s.length) {
        s.forEach { c ->
            append(
                when (c) {
                    '0' -> 'o'; '1' -> one; '3' -> 'e'; '4' -> 'a'; '5' -> 's'; '7' -> 't'; '@' -> 'a'
                    else -> c
                },
            )
        }
    }

    /** Is this URL on an official domain of any brand we know? */
    fun isOfficialBrandDomain(url: String): Boolean {
        val host = host(url) ?: return false
        return BRANDS.values.any { set -> set.any { host == it || host.endsWith(".$it") } }
    }
}
