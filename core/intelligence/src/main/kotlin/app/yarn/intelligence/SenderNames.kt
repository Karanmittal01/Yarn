package app.yarn.intelligence

/**
 * Turns business sender IDs like "AD-HDFCBK-S" into readable names ("HDFC Bank").
 * Unknown IDs fall back to their brand part ("VM-ACMEIN-T" -> "ACMEIN").
 */
object SenderNames {
    private val KNOWN: List<Pair<Regex, String>> = listOf(
        "HDFC" to "HDFC Bank", "ICICI" to "ICICI Bank", "SBI" to "SBI", "AXIS" to "Axis Bank", "KOTAK" to "Kotak Bank",
        "BOB" to "Bank of Baroda", "PNB" to "PNB", "CANBNK" to "Canara Bank", "UNIONB" to "Union Bank", "IDFC" to "IDFC FIRST Bank",
        "YESB" to "Yes Bank", "INDUS" to "IndusInd Bank", "IDBI" to "IDBI Bank", "FEDBNK" to "Federal Bank", "AUBANK" to "AU Bank",
        "AMEX" to "American Express", "CITI" to "Citi", "SCBANK" to "Standard Chartered", "HSBC" to "HSBC",
        "PAYTM" to "Paytm", "PHONPE" to "PhonePe", "PHONEPE" to "PhonePe", "GPAY" to "Google Pay", "CRED" to "CRED", "MOBIKW" to "MobiKwik",
        "AMAZON" to "Amazon", "AMZN" to "Amazon", "FLPKRT" to "Flipkart", "FLIPKT" to "Flipkart", "MYNTRA" to "Myntra", "AJIO" to "AJIO",
        "MEESHO" to "Meesho", "NYKAA" to "Nykaa", "BLINKT" to "Blinkit", "ZEPTO" to "Zepto", "BIGBSK" to "bigbasket",
        "SWIGGY" to "Swiggy", "ZOMATO" to "Zomato", "UBER" to "Uber", "OLA" to "Ola", "RAPIDO" to "Rapido",
        "IRCTC" to "IRCTC", "INDIGO" to "IndiGo", "AIRIND" to "Air India", "VISTAR" to "Vistara", "MMTRIP" to "MakeMyTrip", "GOIBIB" to "Goibibo",
        "JIO" to "Jio", "AIRTEL" to "Airtel", "VIL" to "Vi", "VFCARE" to "Vi", "BSNL" to "BSNL",
        "UIDAI" to "UIDAI (Aadhaar)", "EPFO" to "EPFO", "ITDEPT" to "Income Tax Dept", "NPCI" to "NPCI",
        "DLHVRY" to "Delhivery", "BLUDRT" to "Blue Dart", "EKART" to "Ekart", "DTDC" to "DTDC", "INPOST" to "India Post",
        "GOOGLE" to "Google", "WHATSP" to "WhatsApp", "NETFLX" to "Netflix", "HOTSTR" to "Hotstar",
    ).map { (k, v) -> Regex("^$k") to v }

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
        return KNOWN.firstOrNull { it.first.containsMatchIn(brand) }?.second ?: brand
    }
}
