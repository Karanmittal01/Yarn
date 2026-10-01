package app.yarn.intelligence

/**
 * Turns business sender IDs like "AD-HDFCBK-S" into readable names ("HDFC Bank").
 * Unknown IDs fall back to their brand part ("VM-ACMEIN-T" -> "ACMEIN").
 */
object SenderNames {
    private class Brand(val prefix: Regex, val name: String, val category: Category)

    private val KNOWN: List<Brand> = listOf(
        Brand(Regex("^HDFC"), "HDFC Bank", Category.BANKING),
        Brand(Regex("^ICICI"), "ICICI Bank", Category.BANKING),
        Brand(Regex("^SBI"), "SBI", Category.BANKING),
        Brand(Regex("^ATMSBI"), "SBI", Category.BANKING),
        Brand(Regex("^AXIS"), "Axis Bank", Category.BANKING),
        Brand(Regex("^KOTAK"), "Kotak Bank", Category.BANKING),
        Brand(Regex("^BOB"), "Bank of Baroda", Category.BANKING),
        Brand(Regex("^PNB"), "PNB", Category.BANKING),
        Brand(Regex("^CANBNK"), "Canara Bank", Category.BANKING),
        Brand(Regex("^UNIONB"), "Union Bank", Category.BANKING),
        Brand(Regex("^IDFC"), "IDFC FIRST Bank", Category.BANKING),
        Brand(Regex("^YESB"), "Yes Bank", Category.BANKING),
        Brand(Regex("^INDUS"), "IndusInd Bank", Category.BANKING),
        Brand(Regex("^IDBI"), "IDBI Bank", Category.BANKING),
        Brand(Regex("^FEDBNK"), "Federal Bank", Category.BANKING),
        Brand(Regex("^AUBANK"), "AU Bank", Category.BANKING),
        Brand(Regex("^AMEX"), "American Express", Category.BANKING),
        Brand(Regex("^CITI"), "Citi", Category.BANKING),
        Brand(Regex("^SCBANK"), "Standard Chartered", Category.BANKING),
        Brand(Regex("^HSBC"), "HSBC", Category.BANKING),
        Brand(Regex("^CDSL"), "CDSL", Category.BANKING),
        Brand(Regex("^NSDL"), "NSDL", Category.BANKING),
        Brand(Regex("^ZERODH"), "Zerodha", Category.BANKING),
        Brand(Regex("^GROWW"), "Groww", Category.BANKING),
        Brand(Regex("^UPSTOX"), "Upstox", Category.BANKING),
        Brand(Regex("^BSELTD"), "BSE", Category.BANKING),
        Brand(Regex("^NSEIND"), "NSE", Category.BANKING),
        Brand(Regex("^PAYTM"), "Paytm", Category.PAYMENTS),
        Brand(Regex("^PHONPE"), "PhonePe", Category.PAYMENTS),
        Brand(Regex("^PHONEPE"), "PhonePe", Category.PAYMENTS),
        Brand(Regex("^GPAY"), "Google Pay", Category.PAYMENTS),
        Brand(Regex("^CRED"), "CRED", Category.PAYMENTS),
        Brand(Regex("^MOBIKW"), "MobiKwik", Category.PAYMENTS),
        Brand(Regex("^AMZPAY"), "Amazon Pay", Category.PAYMENTS),
        Brand(Regex("^AMAZON"), "Amazon", Category.SHOPPING),
        Brand(Regex("^AMZN"), "Amazon", Category.SHOPPING),
        Brand(Regex("^FLPKRT"), "Flipkart", Category.SHOPPING),
        Brand(Regex("^FLIPKT"), "Flipkart", Category.SHOPPING),
        Brand(Regex("^MYNTRA"), "Myntra", Category.SHOPPING),
        Brand(Regex("^AJIO"), "AJIO", Category.SHOPPING),
        Brand(Regex("^MEESHO"), "Meesho", Category.SHOPPING),
        Brand(Regex("^NYKAA"), "Nykaa", Category.SHOPPING),
        Brand(Regex("^BLINKT"), "Blinkit", Category.SHOPPING),
        Brand(Regex("^ZEPTO"), "Zepto", Category.SHOPPING),
        Brand(Regex("^BIGBSK"), "bigbasket", Category.SHOPPING),
        Brand(Regex("^SWIGGY"), "Swiggy", Category.SHOPPING),
        Brand(Regex("^ZOMATO"), "Zomato", Category.SHOPPING),
        Brand(Regex("^DLHVRY"), "Delhivery", Category.DELIVERY),
        Brand(Regex("^BLUDRT"), "Blue Dart", Category.DELIVERY),
        Brand(Regex("^EKART"), "Ekart", Category.DELIVERY),
        Brand(Regex("^DTDC"), "DTDC", Category.DELIVERY),
        Brand(Regex("^INPOST"), "India Post", Category.DELIVERY),
        Brand(Regex("^SHPRKT"), "Shiprocket", Category.DELIVERY),
        Brand(Regex("^UBER"), "Uber", Category.TRAVEL),
        Brand(Regex("^OLA"), "Ola", Category.TRAVEL),
        Brand(Regex("^RAPIDO"), "Rapido", Category.TRAVEL),
        Brand(Regex("^IRCTC"), "IRCTC", Category.TRAVEL),
        Brand(Regex("^INDIGO"), "IndiGo", Category.TRAVEL),
        Brand(Regex("^AIRIND"), "Air India", Category.TRAVEL),
        Brand(Regex("^VISTAR"), "Vistara", Category.TRAVEL),
        Brand(Regex("^MMTRIP"), "MakeMyTrip", Category.TRAVEL),
        Brand(Regex("^GOIBIB"), "Goibibo", Category.TRAVEL),
        Brand(Regex("^JIO"), "Jio", Category.UPDATES),
        Brand(Regex("^AIRTEL"), "Airtel", Category.UPDATES),
        Brand(Regex("^VIL"), "Vi", Category.UPDATES),
        Brand(Regex("^VFCARE"), "Vi", Category.UPDATES),
        Brand(Regex("^BSNL"), "BSNL", Category.UPDATES),
        Brand(Regex("^UIDAI"), "UIDAI (Aadhaar)", Category.UPDATES),
        Brand(Regex("^EPFO"), "EPFO", Category.UPDATES),
        Brand(Regex("^ITDEPT"), "Income Tax Dept", Category.UPDATES),
        Brand(Regex("^NPCI"), "NPCI", Category.PAYMENTS),
        Brand(Regex("^GOOGLE"), "Google", Category.UPDATES),
        Brand(Regex("^WHATSP"), "WhatsApp", Category.UPDATES),
        Brand(Regex("^NETFLX"), "Netflix", Category.UPDATES),
        Brand(Regex("^HOTSTR"), "Hotstar", Category.UPDATES),
    )

    fun isBusiness(address: String): Boolean {
        val a = address.trim()
        if (a.contains('@')) return false
        return a.any { it.isLetter() } || a.filter { it.isDigit() }.length in 3..8 && !a.startsWith("+")
    }

    /** Readable name for an alphanumeric sender, or null when [address] isn't one. */
    fun pretty(address: String): String? {
        val a = address.trim()
        if (a.contains('@') || a.none { it.isLetter() }) return null
        val brand = SenderAnalyzer.brandToken(a)
        return KNOWN.firstOrNull { it.prefix.containsMatchIn(brand) }?.name ?: brand
    }

    /** The usual category of a well-known business sender, used as a gentle prior. */
    fun brandCategory(address: String): Category? {
        val a = address.trim()
        if (a.contains('@') || a.none { it.isLetter() }) return null
        val brand = SenderAnalyzer.brandToken(a)
        return KNOWN.firstOrNull { it.prefix.containsMatchIn(brand) }?.category
    }
}
