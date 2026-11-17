# Source picked for each brand after looking at every candidate: web, site, play, wiki, or None (no logo).
W, P, S, K, X = "web", "play", "site", "wiki", None
C = {}
def pick(src, *slugs):
    for s in slugs:
        C[s] = src

# Page 1: banks, cards, investing
pick(P, "icicibank", "bobcard", "bankofbaroda", "idfcfirstbank", "americanexpress", "indianbank", "karurvysyabank",
     "dcbbank", "paytmpaymentsbank", "indiapostpaymentsbank", "airtelpaymentsbank", "onecard", "unicards", "cdsl",
     "zerodha", "groww", "cams", "mfcentral", "bse", "nse", "nps", "tatacapital", "muthootfinance", "kreditbee", "navi",
     "maxlife", "starhealth", "digitinsurance", "policybazaar", "paytm", "googlepay", "cred", "mobikwik", "amazonpay")
pick(W, "sbicard", "canarabank", "indusindbank", "federalbank", "aubank", "dbsbank", "hsbc", "standardchartered", "citi",
     "indianoverseasbank", "cityunionbank", "slice", "jupiter", "fi", "niyo", "upstox", "angelone", "kfintech", "moneyview",
     "lic", "lichousingfinance", "acko", "phonepe")
# Page 2: payments, shopping, food, deliveries, travel
pick(W, "freecharge", "simpl", "razorpay", "cashfree", "purplle", "tatacliq", "dmart", "lenskart", "bewakoof", "pepperfry",
     "decathlon", "blinkit", "pizzahut", "kfc", "countrydelight", "milkbasket", "pharmeasy", "medplus", "indiapost",
     "shiprocket", "uber", "indigo", "airindia", "vistara", "makemytrip", "cleartrip", "easemytrip", "bses")
pick(P, "bhim", "npci", "lazypay", "payu", "jiomart", "licious", "amazon", "flipkart", "shopsy", "myntra", "ajio", "meesho",
     "nykaa", "tataneu", "reliancedigital", "croma", "vijaysales", "firstcry", "snapdeal", "ikea", "boat", "zepto",
     "bigbasket", "bbdaily", "swiggy", "zomato", "eatsure", "dominos", "mcdonalds", "freshtohome", "tata1mg", "netmeds",
     "delhivery", "ekart", "ecomexpress", "xpressbees", "porter", "ola", "rapido", "irctc", "airindiaexpress", "akasaair",
     "spicejet", "goibibo", "yatra", "ixigo", "redbus", "abhibus", "oyo", "bookmyshow")
pick(X, "fastag", "dunzo")
# Page 3: bills, telecom, government, apps, new banks, insurance, funds
pick(P, "bescom", "adanielectricity", "tneb", "tangedco", "kseb", "cesc", "mahanagargas", "hpgas", "actfibernet", "hathway",
     "tataplay", "dishtv", "jio", "airtel", "mtnl", "uidaiaadhaar", "epfo", "gst", "parivahan", "google", "whatsapp",
     "spotify", "apollo", "practo", "csbbank", "dhanlaxmibank", "suryodaybank", "janabank", "utkarshbank", "deutschebank",
     "tjsbbank", "nsdlpaymentsbank", "scapia", "payzapp", "iciciprudential", "icicilombard", "bajajallianz", "tataaig",
     "adityabirlacapital", "carehealth", "reliancegeneral", "manipalcigna", "miraeasset")
pick(W, "msedcl", "torrentpower", "igl", "vi", "digilocker", "mygov", "netflix", "hotstar", "urbancompany", "cosmosbank",
     "kiwi", "hdfclife", "hdfcergo", "nivabupa", "newindiaassurance", "unitedindiainsurance", "chola", "insurancedekho",
     "hdfcmutualfund", "iciciprudentialmf", "axismutualfund", "kotakmutualfund", "paragparikhmf")
pick(X, "cowin", "jiopaymentsbank")
# Page 4: investing, loans, payments, telecom, fashion
pick(P, "utimutualfund", "tatamutualfund", "quantmutualfund", "icicidirect", "hdfcsecurities", "paytmmoney", "kuvera", "iifl",
     "fyers", "cibil", "shriramfinance", "homecredit", "smfgindiacredit", "kissht", "mpokket", "pnbhousing",
     "sundaramfinance", "lendingkart", "pinelabs", "supermoney", "billdesk", "ccavenue", "rupay", "visa", "excitel",
     "spectra", "d2h", "gtpl", "asianet", "railwire", "youbroadband", "lifestyle", "westside", "pantaloons", "hm", "adidas",
     "woodland", "levis", "allensolly", "vanheusen", "peterengland", "raymond")
pick(W, "dspmutualfund", "motilaloswal", "kotaksecurities", "indmoney", "etmoney", "smallcase", "edelweiss", "jar",
     "experian", "bankbazaar", "mahindrafinance", "poonawallafincorp", "piramalfinance", "fibe", "cashe", "zestmoney",
     "freo", "dhani", "pluxee", "zaggle", "mastercard", "sundirect", "airtelxstream", "shoppersstop", "maxfashion",
     "uniqlo", "puma", "nike", "skechers", "bata", "metroshoes", "manyavar")
pick(X, "trends", "campus")
# Page 5: fashion, electronics, home, food, stays
pick(P, "fabindia", "biba", "thesouledstore", "mamaearth", "sugarcosmetics", "tira", "caratlane", "malabargold", "oneplus",
     "voltas", "godrej", "havells", "daikin", "kentro", "eurekaforbes", "urbanladder", "thesleepcompany", "poorvika",
     "spencers", "starbazaar", "lulu", "swiggytoing", "burgerking", "tacobell", "chaayos", "barbequenation", "magicpin",
     "eazydiner", "thomascook", "agoda", "fabhotels", "marriott", "lemontree")
pick(W, "thebodyshop", "titan", "tanishq", "joyalukkas", "fastrack", "hopscotch", "apple", "samsung", "xiaomi", "realme",
     "vivo", "oppo", "nothing", "motorola", "asus", "lg", "philips", "ifb", "wakefit", "homecentre", "reliancesmart",
     "metrocashcarry", "haldirams", "truemeds", "starbucks", "wowmomo", "faasos", "behrouzbiryani", "box8", "theobroma",
     "baskinrobbins", "bookingcom", "airbnb", "treebo", "tajhotels", "itchotels")
pick(X, "snitch", "plum", "lakme", "kalyanjewellers", "dell", "hp", "lenovo", "sony", "whirlpool", "bluestar", "haier",
     "subway", "sotc")
# Page 6: travel, cars, fuel, entertainment, health
pick(W, "allianceair", "emirates", "confirmtkt", "railyatri", "zoomcar", "park","cars24", "spinny", "hyundai", "honda",
     "toyota", "kia", "renault", "nissan", "royalenfield", "shell", "jiobp", "nayaraenergy", "primevideo", "jiohotstar",
     "sonyliv", "zee5", "aha", "youtube", "gaana", "audible", "pocketfm", "kukufm", "pvrinox", "cinepolis", "winzo",
     "rummycircle", "maxhealthcare", "metropolis", "redcliffelabs", "healthifyme", "cloudnine")
pick(P, "qatarairways", "nammayatri", "indrive", "yulu", "ksrtc", "msrtc", "upsrtc", "apsrtc", "tgsrtc", "cardekho",
     "carwale", "marutisuzuki", "mahindra", "mgmotor", "skoda", "heromotocorp", "bajajauto", "tvsmotor", "ather",
     "olaelectric", "yamaha", "indianoil", "hpcl", "sunnxt", "mxplayer", "jiosaavn", "wynkmusic", "district", "dream11",
     "my11circle", "timesprime", "fortis", "manipalhospitals", "narayanahealth", "medanta", "drlalpathlabs", "thyrocare",
     "healthians", "cultfit", "pristyncare", "medibuddy")
pick(X, "blusmart", "tatamotors", "jiocinema", "jungleerummy", "zupee", "mpl")
# Page 7: learning, jobs, homes, apps, couriers, utilities, government
pick(P, "aster", "byjus", "unacademy", "vedantu", "simplilearn", "allen", "aakash", "testbook", "adda247", "scaler", "ignou",
     "naukri", "linkedin", "foundit", "internshala", "shine", "housingcom", "brigadegroup", "justdial", "sulekha", "quikr",
     "facebook", "sharechat", "zoho", "borzo", "fedex", "dhl", "gati", "adanitotalgas", "gujaratgas", "uppcl", "jvvnl",
     "uhbvn", "wbsedcl", "tgspdcl", "delhijalboard", "rbi", "passportseva", "umang", "esic", "ayushmanbharat")
pick(W, "physicswallah", "upgrad", "greatlearning", "indeed", "apna", "workindia", "99acres", "magicbricks", "nobroker",
     "prestigegroup", "sobha", "indiamart", "udaan", "olx", "truecaller", "microsoft", "instagram", "telegram", "pspcl",
     "noidapower", "trai", "sebi", "electioncommission", "nhai", "sancharsaathi", "delhimetro", "mumbaimetro")
pick(X, "cuemath", "cbse", "dlf", "lodha", "professionalcouriers", "trackon", "dhbvn", "indianrailways")

# Still to decide from all four sources (web | site | play | wiki).
REVIEW = ["hdfcbank", "sbi", "axisbank", "kotakbank", "pnb", "unionbank", "yesbank", "idbibank", "rblbank", "bandhanbank",
          "centralbank", "bankofindia", "ucobank", "bankofmaharashtra", "southindianbank", "karnatakabank", "equitasbank",
          "ujjivanbank", "jkbank", "saraswatbank", "finobank", "nsdl", "sharekhan", "bajajfinserv", "hdbfinancial",
          "manappuram", "ltfinance", "bluedart", "shadowfax", "dtdc", "smartr", "tatapower", "indanegas", "bharatgas",
          "bsnl", "incometaxdept", "punjabsindbank", "tamilnadmercantilebank", "esafbank", "svcbank", "tataaia",
          "pnbmetlife", "kotaklife", "sbimutualfund", "nipponindiamf", "sbilife", "sbigeneral", "5paisa", "geojit",
          "paisabazaar", "herofincorp", "aavasfinanciers", "canfinhomes", "bharatpe", "zudio", "sencogold", "amul"]

# Per-logo tweaks passed to fit(): zoom, override_bg, inner, cap.
TUNE = {
    # HDFC's Play icon has a "NEW" ribbon: keep just the white square with the mark.
    "hdfcbank": {"ops": [("crop", 0.258, 0.316, 0.739, 0.798), ("pad", 0.12, (255, 255, 255))]},
    # Kotak's has a "NEW" tab over the navy disc: paint it navy and keep the disc.
    "kotakbank": {"ops": [("fill", 0.0, 0.0, 1.0, 0.26, (0, 0, 90)), ("recolor", (237, 28, 36), 160, (0, 0, 90), 0.5, 0.55, 0.30),
                          ("outside", 0.498, 0.53, 0.42, (0, 0, 90))]},
}

# Decided from the four-way review (web | site | play | wiki).
pick(W, "rblbank", "centralbank", "finobank", "sharekhan", "tataaia", "tamilnadmercantilebank", "nipponindiamf", "sbilife", "aavasfinanciers",
     "sencogold")
pick(S, "pnb", "equitasbank", "dell", "jiocinema")
pick(K, "jkbank", "esafbank", "tatapower", "zudio", "amul")
pick(P, "bharatpe", "sbigeneral", "bajajfinserv", "hdfcbank", "kotakbank")
pick(X, "saraswatbank", "kotaklife", "pnbmetlife", "sbimutualfund", "5paisa", "geojit", "paisabazaar", "herofincorp",
     "canfinhomes", "hdbfinancial", "manappuram", "ltfinance", "shadowfax", "dtdc", "smartr", "indanegas", "bharatgas",
     "bsnl", "incometaxdept", "nsdl", "punjabsindbank", "svcbank", "bankofindia", "ucobank", "bankofmaharashtra",
     "southindianbank", "karnatakabank", "ujjivanbank", "idbibank", "unionbank", "bandhanbank", "bluedart")
# From Wikimedia Commons: SBI's keyhole as is; Yes Bank's tick and "YES", and Axis Bank's "A", cropped from their wordmarks.
pick("c1", "sbi", "yesbank", "axisbank")
TUNE["yesbank"] = {"ops": [("crop", 0.03, 0.30, 0.53, 0.69)]}
TUNE["axisbank"] = {"ops": [("crop", 0.0, 0.0, 0.29, 1.0)]}
