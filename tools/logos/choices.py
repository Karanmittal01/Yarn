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

# Senders from a real inbox (Nov 2026).
pick(P, "quikwallet", "gyftr", "louisphilippe", "hudle", "playo")
pick(W, "woohoo", "tatacliqluxury", "coupl")
# Snitch: its website uses the same lattice "S" as the app, so it is the real mark.
pick(P, "snitch")

# Logos the user supplied (Nov 2026): Blue Dart and Shopwise were missing; Jio's official white-on-red mark.
pick("user", "bluedart", "jio", "shopwise")
TUNE["bluedart"] = {"ops": [("outside", 0.499, 0.499, 0.40, (255, 255, 255))], "inner": 0.47, "cap": 0.80}
TUNE["jio"] = {"ops": [("outside", 0.5, 0.5, 0.47, (255, 0, 0))]}
TUNE["shopwise"] = {"ops": [("crop", 0.10, 0.10, 0.90, 0.85)]}

# Round 3 review, page 1: fintech, banks, insurers, funds, lenders.
pick(W, "stashfin", "moneytap", "paysense", "cheq", "goodscore", "jiofinance", "wintwealth", "gripinvest", "axisdirect",
     "hdfcsky", "scripbox", "fisdom", "paypal", "westernunion", "bookmyforex", "xoxoday", "twid", "crifhighmark", "equifax",
     "wazirx", "coinswitch", "mudrex", "zebpay", "augmont", "mmtcpamp", "rupeek", "indiagold", "shivalikbank", "nkgsbbank",
     "barclays", "turtlemint", "pluminsurance", "zuno", "royalsundaram", "iffcotokio", "canarahsbclife", "bandhanlife",
     "ageasfederal", "indiafirstlife", "sudlife", "pramericalife", "orientalinsurance", "nationalinsurance", "bandhanmf",
     "jmfinancial", "whiteoakcapital", "samco", "aliceblue", "choice", "religare", "nuvama", "gichousing", "repcohome",
     "satincreditcare")
pick(P, "truebalance", "onescore", "stablemoney", "gullak", "intermiles", "coindcx", "safegold", "abhyudayabank",
     "kalupurbank", "apnasahakaribank", "rajkotnagarikbank", "coverfox", "renewbuy", "ditto", "futuregenerali", "magmahdi",
     "shriramgeneral", "universalsompo", "aviva", "shriramlife", "franklintempleton", "invescomf", "canararobeco",
     "sundarammf", "barodabnpparibasmf", "licmf", "mahindramanulife", "ventura", "mcx", "sammaancapital", "aadharhousing",
     "aptus", "muthootfincorp", "spandana", "creditaccessgrameen")
pick(X, "sbmbank", "qwikcilver", "payback", "unitybank", "capitalsmallfinancebank", "nainitalbank", "bharatcoopbank",
     "janatasahakaribank", "libertygeneral", "unionmf", "pgimindia")
# Round 3, page 2: lenders, internet, home, gadgets, fashion, beauty, footwear.
pick(W, "ugrocapital", "flexiloans", "kinaracapital", "avanse", "incred", "siti", "fastway", "netplus", "keralavision",
     "shopclues", "woodenstreet", "livspace", "homelane", "bergerpaints", "jaquar", "crompton", "bajajelectricals",
     "ttkprestige", "butterfly", "luminous", "iqoo", "poco", "tecno", "lava", "mivi", "jbl", "acer", "canon", "zebronics",
     "ambrane", "zara", "soch", "wowskinscience", "minimalist", "myglamm", "forestessentials", "kamaayurveda", "patanjali",
     "reebok", "crocs")
pick(P, "fusionfinance", "asirvad", "indifi", "tikona", "alliancebroadband", "den", "connectbroadband", "moglix", "hometown",
     "asianpaints", "hindware", "borosil", "milton", "cello", "dyson", "livpure", "aosmith", "exide", "amaron", "bigc",
     "noise", "fireboltt", "boult", "tcl", "marksspencer", "uspoloassn", "jackjones", "wforwoman", "libas", "themancompany",
     "bombayshavingcompany", "mcaffeine", "thedermaco", "dotkey", "himalaya", "healthglow", "titaneye", "redtape",
     "libertyshoes", "relaxo", "neemans")
pick(X, "credila", "sify", "nilkamal", "duroflex", "kurlon", "orientelectric", "usha", "atomberg", "wonderchef", "vguard",
     "sangeethamobiles", "bajajelectronics", "infinix", "panasonic", "portronics", "beardo", "bathbodyworks", "mochi",
     "pcjeweller")
# Round 3, page 3: jewellers, stores, food, gifts, rides, hotels, airlines, apps.
pick(W, "bluestone", "grtjewellers", "lalithaajewellery", "naturesbasket", "ratnadeep", "vishalmegamart", "vmart", "heritage",
     "thirdwavecoffee", "cafecoffeeday", "chaipoint", "bikanervala", "ovenstory", "biryanibykilo", "freshmenu", "healthkart",
     "muscleblaze", "manmatters", "fernsnpetals", "igp", "winni", "snabbit", "rentomojo", "furlenco", "savaari", "revv",
     "royalbrothers", "nammametro", "hyderabadmetro", "chennaimetro", "expedia", "ihg", "radisson", "starair", "fly91",
     "etihad", "singaporeairlines", "lufthansa", "britishairways", "flydubai", "intrcity", "nuego", "flixbus",
     "bengaluruairport", "digiyatra", "hoichoi", "pokerbaazi", "x", "josh", "tinder", "bumble", "aisle")
pick(P, "giva", "pngjewellers", "reliancejewels", "moreretail", "spar", "motherdairy", "bluetokai", "timhortons", "lapinoz",
     "paradisebiryani", "oziva", "bebodywise", "traya", "hamleys", "bakingo", "supertails", "headsupfortails", "quickride",
     "bounce", "akbartravels", "hyatt", "hilton", "accor", "sterlingholidays", "clubmahindra", "zostel", "thaiairways",
     "airarabia", "airasia", "zingbus", "delhiairport", "vfsglobal", "blsinternational", "hungama", "shemaroome", "stage",
     "a23", "snapchat", "moj")
pick(X, "candere", "tbz", "bhima", "yogabar", "archies", "crossword", "meru", "oberoihotels", "paytminsider")
# Round 3, page 4: matrimony, business apps, hospitals, labs, learning, jobs, homes, discoms, government.
pick(W, "tally", "vyapar", "instamojo", "petpooja", "wellnessforever", "davaindia", "kimshospitals", "dragarwalseyehospital",
     "yashodahospitals", "kokilabenhospital", "hindujahospital", "neubergdiagnostics", "orangehealth", "pathkindlabs",
     "coursera", "udemy", "duolingo", "doubtnut", "careers360", "srichaitanya", "teachmint", "seekho", "timesjobs", "hirist",
     "instahyre", "jobhai", "stanzaliving", "zolo", "embassygroup", "inorbit", "mgvcl", "apepdcl", "jbvnl",
     "mpcentraldiscom", "cspdcl", "torrentgas", "bhagyanagargas", "mngl", "bbmp", "eshram", "shreemaruticourier")
pick(P, "shaadicom", "bharatmatrimony", "jeevansathi", "zoom", "khatabook", "okcredit", "rainbowchildrenshospital",
     "motherhoodhospitals", "clovedental", "artemis", "parashealth", "vijayadiagnostic", "agilusdiagnostics", "fittr",
     "collegedunia", "shiksha", "fiitjee", "narayanagroup", "classplus", "masai", "squareyards", "puravankara",
     "shapoorjipallonji", "hiranandani", "phoenixmalls", "nexusmalls", "best", "ugvcl", "dgvcl", "pgvcl", "apspdcl",
     "tgnpdcl", "tpcentralodisha", "nbpdcl", "sbpdcl", "mpeastdiscom", "hpseb", "upcl", "avvnl", "jdvvnl", "hescom",
     "mescom", "gescom", "sabarmatigas", "bwssb", "hmwssb", "pmkisan", "cbic", "mca", "delhipolice", "safexpress")
pick(X, "carehospitals", "nta", "hirect", "tatahousing", "mpwestdiscom", "gailgas", "ghmc", "irdai")
# Round 3, page 5: couriers, cars, bikes, tyres, charging.
pick(W, "pidge", "volkswagen", "citroen", "bmw", "mercedesbenz", "audi", "volvocars", "byd", "ktm", "revolt", "gomechanic",
     "statiq")
pick(P, "aramex", "ups", "tciexpress", "vrllogistics", "jeep", "suzukimotorcycle", "droom", "bikewale", "ceat")
pick(X, "jawa", "mrf", "apollotyres", "jktyre")
pick(X, "inorbit")  # its icon may not be the mall's own
# From Wikimedia Commons wordmarks, cropped to the symbol.
pick("c1", "bandhanbank", "bankofmaharashtra")
pick("cu", "unionbank")
TUNE["bandhanbank"] = {"ops": [("crop", 0.0, 0.0, 0.215, 1.0)]}
TUNE["unionbank"] = {"ops": [("crop", 0.0, 0.0, 0.26, 1.0)]}
TUNE["bankofmaharashtra"] = {"ops": [("crop", 0.0, 0.0, 0.232, 0.76)]}

# Round 4: the last ones, from each brand's own website (h1 = site icon, h2/h3 = header logo) where possible.
pick(P, "cowin", "southindianbank")
pick("site", "campus", "lakme", "dlf", "dhbvn")
pick("h1", "karnatakabank", "nainitalbank")
pick("h2", "plum", "kalyanjewellers", "hp", "lenovo", "whirlpool", "haier", "jungleerummy", "zupee", "lodha",
     "professionalcouriers", "trackon", "saraswatbank", "pnbmetlife", "geojit", "hdbfinancial", "manappuram", "shadowfax",
     "incometaxdept", "sbmbank")
pick("h3", "tatamotors", "sbimutualfund")
pick(W, "bluestar", "sotc", "mpl", "kotaklife", "herofincorp", "ltfinance", "dtdc", "ujjivanbank",
     "capitalsmallfinancebank", "janatasahakaribank")
pick(P, "pgimindia", "bharatgas")
pick("h2", "credila", "sify", "nilkamal", "duroflex", "kurlon", "usha", "atomberg", "vguard", "infinix", "beardo",
     "bathbodyworks", "mochi", "pcjeweller", "candere", "bhima", "archies", "crossword", "oberoihotels", "paytminsider",
     "inorbit", "gailgas", "irdai", "jawa", "mrf", "apollotyres", "jktyre")
pick("h3", "yogabar", "tatahousing")
pick(W, "orientelectric", "wonderchef", "bajajelectronics", "portronics")
# BSNL's Play icon has a "NEW" ribbon in the corner: paint it out and keep the globe and name.
pick(P, "bsnl")
TUNE["bsnl"] = {"ops": [("fill", 0.6, 0.68, 1.0, 1.0, (255, 255, 255)), ("crop", 0.2, 0.03, 0.8, 0.77)]}
pick("h2", "nainitalbank")
pick(X, "incometaxdept", "sbimutualfund")  # their header captures are banners with tiny text
TUNE["bsnl"] = {"ops": [("fill", 0.6, 0.68, 1.0, 1.0, (255, 255, 255)), ("recolor", (238, 242, 238), 70, (255, 255, 255), 0.5, 0.5, 0.0),
                        ("crop", 0.2, 0.03, 0.8, 0.77)]}
pick("h2", "pgimindia")
TUNE["bsnl"] = {"ops": [("fill", 0.6, 0.68, 1.0, 1.0, (255, 255, 255)), ("recolor", (238, 242, 238), 70, (255, 255, 255), 0.5, 0.5, 0.0),
                        ("crop", 0.24, 0.03, 0.76, 0.665)]}
TUNE["bsnl"] = {"ops": [("fill", 0.6, 0.68, 1.0, 1.0, (255, 255, 255)), ("recolor", (238, 242, 238), 130, (255, 255, 255), 0.5, 0.5, 0.0),
                        ("crop", 0.22, 0.03, 0.78, 0.69)]}
# Second website pass.
pick("g2", "ucobank", "subway", "sangeethamobiles")
pick("g3", "svcbank", "sbimutualfund")
TUNE["ucobank"] = {"ops": [("crop", 0.0, 0.195, 1.0, 0.86), ("pad", 0.06, (255, 255, 255))]}
TUNE["subway"] = {"ops": [("brighten", 2.0)]}

# Smytten (SMYTTN): navy disc with transparent corners, so fill them navy and let it fill the circle.
pick(P, "smytten")
TUNE["smytten"] = {"ops": [("outside", 0.5, 0.5, 0.49, (15, 22, 40))]}

# Shipfast (SHPFAS), Velocity's shipping service: its site icon matches the logo the user sent; keep the blue square.
pick(W, "shipfast")
TUNE["shipfast"] = {"ops": [("crop", 0.12, 0.12, 0.88, 0.88)]}

# Hoi (HOIYTC, airport lounges; app by PAX Innovation) and Routematic (RMATIC, office cabs).
pick(P, "hoi")
pick(W, "routematic")

# Pay with Rewards (PAYRWD): the logo the user sent, cropped to the tile with the star coin.
pick("user", "paywithrewards")
TUNE["paywithrewards"] = {"ops": [("crop", 0.28, 0.14, 0.72, 0.575)], "inner": 0.44, "cap": 0.74}
# Pine Labs: its "pine labs" wordmark rather than the EMI World app icon. Qwikcilver (QWKCLR) is Pine Labs'
# gift-card business now; its own site is gone, so it shows Pine Labs' logo.
pick(K, "pinelabs")
pick("pl", "qwikcilver")
