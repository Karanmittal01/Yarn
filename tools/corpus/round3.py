"""Round three of the corpus: hundreds of further distinct formats, so that what Yarn sees in real
inboxes (bank notices where no money moves, carrier-labelled spam, number sellers, regional
languages, odd spacing) is covered by tests and not just imagined. Called from make_sms_corpus.py.

Tabs as there: P personal, O OTP, T transactions, S shopping, V travel, U updates, F offers, X spam.
"""

BANKS = ["HDFCBK", "ICICIB", "SBIINB", "AXISBK", "KOTAKB", "IDFCFB", "YESBNK", "INDUSB", "PNBSMS", "BOBTXN", "CANBNK", "UNIONB", "FEDBNK",
         "AUBANK", "RBLBNK", "IOBCHN", "CENTBK", "IDBIBK", "SCBANK", "HSBCIN", "CITIBK", "DBSBNK", "KVBANK", "SIBSMS", "KTKBNK", "EQUTAS", "UJJIVN"]
CARDS = ["SBICRD", "AMEXIN", "ONECRD", "HDFCBK", "ICICIB", "AXISBK", "BOBCRD", "SCAPIA", "UNICRD", "JUPITR"]


def build(add_once, people):
    # Each format three times over, from different senders with different values: the same notice
    # from 27 banks tests 27 sender IDs.
    def add(want, senders, texts, copies=1):
        add_once(want, senders, texts, copies * 3)

    # ---------------------------------------------------------------------------------------------
    # The user's own messages, word for word (each was in the wrong tab).
    # ---------------------------------------------------------------------------------------------
    add("U", ["=VM-HDFCBK-S"], ["Manage your HDFC Bank Credit Card on MyCards. Save this link for quick access anytime: mycards.hdfc.bank.in"], 1)
    add("U", ["=JM-HDFCBK-S"], ["Card Closure Request Received!\nFor your HDFC Bank Credit Card 0430 closure request. Processing will complete in 7 days,  subject to Nil Outstanding."], 1)
    add("U", ["=JX-HDFCBK-S"], ["HDFC Bank has updated your CKYC record with ref 30058269716102. Please keep it safe. Need help? Email support@hdfc.bank.in or visit a branch."], 1)
    add("U", ["=VM-HDFCBK-S"], ["Successful:\nHDFC Bank Demographic Auth on Aadhaar xx0103 for TATA on 18-11-26,22:07\nAuth Code: 9f74f54cac3e42d5a74e7f59abe5a4b8"], 1)
    add("X", ["=+919978839747"], ["Airtel Warning: SPAM | Dear Sir/ Madam:\ne-Challan of INR.1500 Generated on Vehicle no: DL10EV9491\n\nReceipt no. CHN0178209109\nPay/View Now: https://mparivahan.lozan.com/674927/"], 1)
    add("X", ["=+919711261683"], ["9999422624\n999099-7887\n8585-9191-66\n9355-390-555\n8375-0000-89\n99-90-20-80-40\nWant Similar & vip No.\n Call/Whtsap 9540676767 7834969696"], 1)

    # ---------------------------------------------------------------------------------------------
    # Bank notices where no money moves: Updates
    # ---------------------------------------------------------------------------------------------
    add("U", BANKS, [
        "Card Closure Request Received! For your Credit Card {l4} closure request. Processing will complete in 7 days, subject to Nil Outstanding.",
        "Your request to close Savings Account XX{l4} has been received. Ref no {ref}. It will be processed within 7 working days.",
        "Bank has updated your CKYC record with ref {ref}{l2}. Please keep it safe.",
        "Your CKYC identifier is {ref}{l2}. Use it for faster KYC with any financial institution.",
        "Manage your Credit Card anytime on the mobile app. Save this link for quick access: {link:mycards.hdfc.bank.in}",
        "Your Debit Card PIN has been successfully generated for card ending {l4}.",
        "International usage has been enabled on your Debit Card XX{l4} as requested.",
        "Contactless payments have been disabled on your card ending {l4}. You can enable them on the app anytime.",
        "Your Credit Card XX{l4} limit has been revised to Rs {lim}. Enjoy more spending power!",
        "Your email ID has been successfully updated in our records on {date}. Not you? Call 1800 2662.",
        "Your registered address has been updated as per your request dated {date}.",
        "Your net banking user ID has been unlocked. You can now log in.",
        "Dear Customer, your account XX{l4} has been converted to a Salary Account as per your employer's request.",
        "Your request for a duplicate account statement has been received. It will be sent to your registered email in 2 days.",
        "Interest rates on Fixed Deposits have been revised w.e.f. {date}. Visit the website for details.",
        "Important: our {city} branch will move to a new address from {date}. Your account details remain unchanged.",
        "Banks will remain closed on {date} on account of Diwali. Digital banking services will be available as usual.",
        "Your UPI PIN has been set successfully for A/c XX{l4}.",
        "Your UPI ID {vpa} has been deregistered as requested.",
        "We have received your request to update your PAN. It will be updated in 48 hours.",
        "Your Form 16A / TDS certificate for Q2 FY 2026-27 is now available on net banking.",
        "Your Positive Pay details for cheque no {l5} have been registered successfully.",
        "Video KYC for your account application {ref} is pending. Complete it on the app before {date}.",
        "Your new Credit Card XX{l4} has been delivered. Activate it on the app to start using it.",
        "Your Credit Card XX{l4} statement for the period ending {date} has been generated and sent to your email.",
        "Your account XX{l4} is now linked to your Aadhaar for DBT benefits.",
        "Thank you for updating your nominee. Nominee: {NAME}.",
        "Your loan foreclosure request for loan account {ca} has been received. Our team will contact you within 48 hours.",
        "Your Standing Instruction for Rs {amt} to {NAME} has been registered and will run monthly from {date}.",
        "Your mandate for SIP of Rs {amt_s} has been registered. UMRN: HDFC{ref}.",
        "Your Recurring Deposit account XX{l4} has been opened successfully. Maturity date: {date}.",
        "A new device was registered for mobile banking on your account on {date} at {time}. Not you? Call 18002586161 immediately.",
        "Your Debit Card XX{l4} has been hotlisted as requested. A replacement card will be sent to your registered address.",
        "Your request to increase the UPI limit has been processed. New daily limit: Rs 1,00,000.",
        "Your Demat account {ca} has been opened. Login details have been sent to your email.",
        "Demographic Auth on Aadhaar xx{l4} for TATA on {date},{time} Successful. Auth Code: {hash}{hash}",
        "Your account XX{l4} will be debit-frozen from {date} as KYC is pending. Please visit your home branch with valid ID.",
    ], 1)

    # ---------------------------------------------------------------------------------------------
    # More ways money moves: Transactions
    # ---------------------------------------------------------------------------------------------
    add("T", BANKS, [
        "Rs.{amt} reversed to your A/c XX{l4} on {date} for failed UPI txn {ref}.",
        "Dear Customer, Rs {amt_s} charged on A/c XX{l4} towards non-maintenance of minimum balance for {date}. Incl GST.",
        "INR {amt_s} has been debited from your account XX{l4} towards Debit Card annual fee + GST.",
        "Your A/c XX{l4} is credited with Rs.{amt} on {date} by RTGS from {NAME}. UTR {ref}.",
        "Avl Bal in A/c XX{l4} as on {date} {time} is INR {bal}. Uncleared bal INR 0.00",
        "Mini statement A/c XX{l4}: {date} DR {amt_s}; {date2} CR {amt}; Bal {bal}",
        "Rs.{amt} withdrawn from A/c XX{l4} via Cardless Cash at ATM on {date}. Avl Bal Rs.{bal}.",
        "NACH debit of Rs {amt} for BAJAJ FINANCE LTD from A/c XX{l4} has been returned unpaid. Reason: Insufficient Funds. Charges apply.",
        "Your Credit Card XX{l4} bill payment of Rs.{amt} is received. Available limit Rs.{lim}.",
        "Credit Card XX{l4}: cashback of Rs {amt_s} has been credited to your card account.",
        "Your EMI request for Rs {amt} on Credit Card XX{l4} has been booked. EMI of Rs {amt_s} for 6 months will be billed from your next statement.",
        "Rs {amt} debited from your A/c XX{l4} for ASBA application of SWIGGY IPO. Amount blocked till allotment.",
        "UPI mandate: Rs {amt} blocked in A/c XX{l4} for IPO application. Unblocked if not allotted.",
        "Rs {amt_l} credited to your A/c XX{l4} towards loan disbursement of loan {ca}.",
        "Your Sovereign Gold Bond interest of Rs {amt_s} has been credited to A/c XX{l4}.",
        "Rs.{amt} debited from A/c XX{l4} for PPF deposit on {date}.",
        "Credit of Rs {amt} to A/c XX{l4} on {date} : Dividend - ITC LTD.",
        "Dear Customer, A/c XX{l4} debited by Rs {amt_s} for SMS alert charges for the period.",
        "Your forex card ending {l4} has been loaded with USD {amt_s}. Available balance USD {amt_s}.",
        "TXN of Rs {amt_s} at {merchant} on your Debit Card XX{l4} was declined due to incorrect PIN.",
        "Your payment of Rs {amt} to {NAME} via NEFT is successful. Ref {ref}.",
        "Rs.{amt} credited to A/c XX{l4} by Cash Deposit at {city} branch on {date}. Avl Bal Rs.{bal}",
        "Your auto-debit of Rs {amt} for LIC premium failed due to insufficient balance in A/c XX{l4}.",
        "Interest of Rs {amt_s} on your FD XX{l4} has been credited to A/c XX{l4}.",
        "प्रिय ग्राहक, आपके खाते XX{l4} में ₹{amt} जमा हुए हैं। शेष राशि ₹{bal}",
        "আপনার অ্যাকাউন্ট XX{l4} থেকে ₹{amt} ডেবিট হয়েছে।",
        "உங்கள் கணக்கு XX{l4} இல் ₹{amt} வரவு வைக்கப்பட்டது.",
    ], 1)
    add("T", CARDS, [
        "You've spent Rs {amt} on your Credit Card ending {l4} at {merchant} on {date}. Available limit Rs {lim}.",
        "Txn of Rs {amt_s} on Card XX{l4} at {merchant} is successful. Not you? Call 18001080 to block.",
        "Minimum amount due Rs {amt_s} on your Credit Card XX{l4} is overdue. Late payment charges of Rs 500 have been levied.",
        "Your Credit Card XX{l4} statement: Total due Rs {amt}, Min due Rs {amt_s}, Due date {date}.",
        "Refund of Rs {amt_s} from {merchant} has been credited to your Credit Card XX{l4}.",
        "Annual fee of Rs 499 + GST has been charged on your Credit Card XX{l4}.",
        "Your Scapia card spend of Rs {amt} at {merchant} earned {l3} Scapia coins.",
    ], 1)
    add("T", ["BAJAJF", "KREDTB", "MONEYV", "NAVIFN", "TATACP", "HDBFSL", "MUTHOT", "LICHFL", "PNBHFL", "INDIFI", "FIBEAP"], [
        "Your EMI of Rs {amt} for loan {ca} will be auto-debited on {date}. Please keep sufficient balance.",
        "Dear Customer, EMI of Rs {amt} towards loan {ca} is overdue by 5 days. Please pay immediately to avoid penal charges and impact on your credit score.",
        "Loan of Rs {amt_l} disbursed to your bank account XX{l4}. Your first EMI of Rs {amt} is due on {date}.",
        "We have received Rs {amt} towards your gold loan {ca}. Outstanding: Rs {amt_l}.",
        "Your loan account {ca} has been foreclosed. Amount paid: Rs {amt_l}. NOC will be issued in 7 days.",
    ], 1)
    add("T", ["LICIND", "HDFCLI", "ICICIP", "SBILIF", "STARHL", "NIVABU", "CAREHL", "ACKOIN", "DIGITI", "TATAAI", "ICILOM"], [
        "Premium of Rs {amt} received for policy {ca}. Next due date {date}. Thank you.",
        "Your claim no {ref} has been approved. Rs {amt} will be credited to your bank account XX{l4} within 3 working days.",
        "Rs {amt} has been paid to your bank account for claim {ref}. -Star Health",
        "Your policy {ca} premium of Rs {amt} is due on {date}. Pay now to avoid a lapse in cover.",
    ], 1)
    add("T", ["ZERODH", "GROWWI", "UPSTOX", "ANGELB", "KOTAKS", "ICICID", "HDFSEC", "CAMSPL", "KFINTK", "NSDLCS", "CDSLEV", "MFCNTR", "COINDC"], [
        "Your redemption of {l2}.{l3} units of HDFC Mid Cap Fund for Rs {amt} has been processed. Amount will be credited in 2 working days.",
        "Trade confirmation: BUY {l2} RELIANCE @ {amt_s} on NSE. Brokerage Rs 20.",
        "Payout of Rs {amt} has been processed to your bank account XX{l4}.",
        "Your SIP installment of Rs {amt_s} for folio {ca} has failed due to insufficient balance.",
        "NAV-based allotment: {l2}.{l3} units allotted in folio {ca} for Rs {amt_s}.",
        "Credit in your demat account *{l4}: {l2} shares of TATASTEEL on {date}. -NSDL",
        "Margin shortfall of Rs {amt} in your account. Add funds by 3:30 PM to avoid square-off.",
    ], 1)
    add("T", ["BBPSBL", "PAYTMB", "PHONPE", "AMZPAY", "CREDIN", "MOBIKW", "AIRPAY", "BAJAJP"], [
        "Your electricity bill payment of Rs {amt_s} for consumer {ca} is successful. Transaction ref {ref}. -Bharat BillPay",
        "Paid Rs {amt_s} for DTH recharge of Tata Play ID {ca}. Txn {ref}.",
        "Rs {amt_s} paid for Delhi Jal Board water bill, K no {ca}. Payment successful.",
        "Your FASTag recharge of Rs {amt} for {veh} is successful.",
        "Payment of Rs {amt_s} for Metro card {ca} recharge is successful.",
        "Rs {amt_s} cashback credited to your PhonePe wallet for your bill payment.",
    ], 1)
    add("T", ["DPSSCH", "KVSSCH", "BYJUSS", "ALLENI", "AAKASH", "VITUNI", "AMITYU", "MANIPL", "NOBROK", "APNACM", "MYGATE", "NOBRKR"], [
        "Fee reminder: Rs {amt} for Quarter 3 is due by {date}. Late fee of Rs 100/day applies after the due date.",
        "Fee of Rs {amt} received for Roll No {l5}. Thank you.",
        "Your maintenance bill of Rs {amt} for {date} is generated. Due {date2}.",
        "Penalty of Rs 250 added to your maintenance dues for late payment.",
        "Rent payment of Rs {amt} to {NAME} is successful via credit card. Fee: Rs {amt_s}.",
    ], 1)
    add("T|U", ["UIDAI-G", "PMKSNY-G", "DBTBHR-G", "PFMSIN-G", "EPFOHO-G", "NSPGOV-G"], [
        "PM-KISAN: Rs 2000 has been credited to your bank account XX{l4} for the {l2}th installment.",
        "Your DBT benefit of Rs {amt_s} under LPG subsidy has been credited to A/c XX{l4}.",
        "Scholarship amount of Rs {amt} has been credited to your account XX{l4}. -NSP",
    ], 1)

    # ---------------------------------------------------------------------------------------------
    # Government, civic and official notices: Updates
    # ---------------------------------------------------------------------------------------------
    add("U", ["UIDAI-G", "ITDEFL-G", "CBDTIN-G", "EPFOHO-G", "DIGILK-G", "MPRVHN-G", "PASSPT-G", "ECISMS-G", "NDMAIN-G", "MYGOVT-G", "CPGRMS-G",
              "SANCHR-G", "TRAIND-G", "IMDWTH-G", "DLTPOL-G", "UMANGG-G", "CMOFFC-G", "NHAIIN-G", "GSTIND-G", "PMJAYS-G", "COWINS-G", "CERSAI-G"], [
        "Your Aadhaar authentication was performed on {date} at {time}. If not done by you, lock your biometrics at myaadhaar.uidai.gov.in.",
        "Your PAN is now operative. -Income Tax Department",
        "Intimation u/s 143(1) for AY 2026-27 has been sent to your registered email. -CPC",
        "Your passport application file no {ca} has been granted. Passport will be dispatched shortly.",
        "Police verification for your passport application {ca} is complete.",
        "Your appointment at Passport Seva Kendra {city} is confirmed for {date} at {time}.",
        "Your RC for vehicle {veh} has been approved and will be dispatched by speed post.",
        "Your PUC certificate for vehicle {veh} expires on {date}. Renew it to avoid a fine.",
        "Polling for your constituency is on {date}. Your booth: Govt School, Sector 12, Booth no {l2}. Carry your EPIC.",
        "Heat wave warning: avoid going out between 12 PM and 4 PM. Drink plenty of water. -NDMA",
        "Earthquake drill in your area on {date} at 11 AM. Do not panic. -District Administration",
        "Your grievance {ref} has been resolved. Rate the resolution on pgportal.gov.in",
        "Your mobile number is registered under DND. You will not receive promotional calls or SMS. -TRAI",
        "You can block your lost or stolen phone on sancharsaathi.gov.in. -DoT",
        "Your GSTR-3B for October 2026 has been filed successfully. ARN {ref}.",
        "Your Ayushman card has been generated. Download it from beneficiary.nha.gov.in.",
        "Your EPF claim {ref} has been settled. Amount will reach your bank in 3 working days.",
        "Your e-PAN has been sent to your registered email ID.",
        "Traffic advisory: Ring Road closed between {city} Gate and ITO on {date} from 6 AM to 11 AM due to the parade.",
        "Your driving licence {ca} expires on {date}. Apply for renewal on parivahan.gov.in.",
        "Dear citizen, ration for November is available at your fair price shop. Collect by {date}.",
        "Your CKYC record has been updated by HDFC Bank. KYC identifier: {ref}{l2}. -CERSAI",
    ], 1)
    add("U|T", ["MPRVHN-G", "DLTPOL-G", "MHTRAF-G", "BLRTRF-G", "PUNETP-G", "HYDTRF-G"], [
        "A challan no {ref} of Rs {amt_s} has been issued against vehicle {veh} for jumping a red light on {date}. Pay at echallan.parivahan.gov.in",
        "E-challan {ref} for vehicle {veh} has been paid successfully. Amount Rs {amt_s}.",
    ], 1)

    # ---------------------------------------------------------------------------------------------
    # Telecom and internet service notices
    # ---------------------------------------------------------------------------------------------
    add("U", ["JIOINF", "AIRTEL", "VICARE", "BSNLIN", "ACTFIB", "TATAPL", "HATHWY", "EXCITL"], [
        "International roaming has been activated on {phone}. Charges apply as per your plan.",
        "Your SIM swap request has been received. Your new SIM will be active in 4 hours. If not requested by you, call 198 immediately.",
        "Voicemail: you have 1 new voice message from {phone}. Dial 121 to listen.",
        "Your Hellotune has been set to 'Kesariya'. Valid for 30 days.",
        "Your number {phone} will be ported out as per request {ref}. If not requested by you, call 198.",
        "Your broadband service is restored. We regret the inconvenience.",
        "Technician {first} will visit your address tomorrow between 10 AM and 1 PM for your new fibre connection.",
        "Your complaint {ref} regarding slow internet has been registered. We will resolve it in 24 hours.",
        "Your daily data quota is exhausted. Speed reduced to 64 Kbps till midnight.",
        "Missed call alert: {phone} tried to call you at {time}.",
        "Welcome to Airtel! Your plan is active. Dial *121# for balance and offers.",
    ], 1)
    add("T", ["JIOINF", "AIRTEL", "VICARE", "BSNLIN", "TATAPL", "DISHTV", "ACTFIB"], [
        "Recharge successful! Rs 349 plan active on {phone} till {date}. 2GB/day + unlimited calls.",
        "Your Airtel Xstream Fiber bill of Rs {amt_s} for account {ca} is due on {date}.",
        "Payment of Rs {amt_s} received for Tata Play subscriber ID {ca}. Thank you.",
        "Your postpaid bill of Rs {amt_s} is overdue. Outgoing services will be barred if unpaid by {date}.",
    ], 1)

    # ---------------------------------------------------------------------------------------------
    # One-time codes, written every way they come
    # ---------------------------------------------------------------------------------------------
    add("O", ["HDFCBK", "ICICIT", "SBIOTP", "AXISBK", "KOTAKB", "YESBNK", "INDUSB", "PNBSMS", "BOBOTP", "CANBNK", "IDFCFB", "AUBANK", "FEDBNK"], [
        "OTP:{otp} for card ending {l4} for txn of INR {amt} at {merchant}. Valid for 5 min. Never share OTP.",
        "Dear Customer, use OTP {otp} to complete your NetBanking registration. Do not share it with anyone.",
        "{otp} is the One Time Password to set your UPI PIN for A/c XX{l4}. Do not share.",
        "Your OTP for E-mandate registration of Rs {amt} with Zerodha is {otp}.",
        "OTP {otp} for changing your registered mobile number. If not requested by you, call us now.",
        "OTP is {otp} to block your Debit Card ending {l4}. Valid for 10 minutes.",
        "Use {otp} to verify your Aadhaar for eKYC. Do not share with anyone. -SBI",
        "Your transaction password for beneficiary addition is {otp}. Valid 30 min.",
        "{otp} is your OTP to view your CKYC record. Do not share it.",
        "Your OTP for loan closure is {otp}. Valid for 10 minutes.",
    ], 1)
    add("O", ["AMAZON", "FLPKRT", "MYNTRA", "NYKAAA", "MEESHO", "AJIOIN", "SWIGGY", "ZOMATO", "BLINKT", "ZEPTON", "BIGBSK", "JIOMRT", "TATANU",
              "UBERIN", "OLACAB", "RAPIDO", "MMTRIP", "GOIBIB", "IXIGOO", "REDBUS", "IRCTCI", "INDIGO", "AIRIND", "PAYTMB", "PHONPE", "CREDIN",
              "GROWWI", "ZERODH", "UPSTOX", "NETFLX", "HOTSTR", "SPOTIF", "LNKDIN", "NAUKRI", "PRACTO", "APOLLO", "TRUECL", "BMSHOW", "DOMINO",
              "LENSKT", "URBNCO", "OYOROM", "CULTFT", "UNACAD", "PWLIVE", "BYJUSS", "TINDER", "BUMBLE", "SHAADI", "JEEVAN", "OLXIND", "QUIKRR",
              "CARDKO", "SPINNY", "ACKOIN", "POLBZR", "TATA1M", "PHEASY", "NETMED", "CIBILO", "EXPRNC"], [
        "Your login OTP is {otp}. Do not share it with anyone. {hash}",
        "<#> {otp} is your verification code. {hash}",
        "Hi! {otp} is your OTP to sign in. It is valid for {min} minutes. Please do not share it with anyone.",
        "Dear user, your OTP is {otp}. Use it to verify your mobile number.",
        "OTP: {otp}. Valid for 10 mins. Do not share.",
        "{otp} - use this code to reset your password.",
        "Your one time password for account verification is {otp}.",
        "Verification code: {otp}. If you didn't request this, ignore this message.",
        "Use code {otp} to confirm your booking.",
        "{l3}-{l3} is your verification code.",
    ], 1)
    add("O", ["UIDAI-G", "UMANGG-G", "DIGILK-G", "ITDEFL-G", "EPFOHO-G", "MPRVHN-G", "PASSPT-G", "ECISMS-G", "NSDLPN", "UTIITS", "CIBILO", "ESIGNN", "PROTEN", "COWINS-G"], [
        "{otp} is OTP for your Voter ID linking. Valid for 10 minutes.",
        "OTP for Passport Seva login is {otp}.",
        "Your OTP for eSign of document {ref} is {otp}. Valid for 10 min.",
        "OTP for CIBIL score check is {otp}. Do not share.",
        "{otp} is your OTP for UMANG registration.",
        "आपका ओटीपी {otp} है। कृपया इसे किसी के साथ साझा न करें। -UIDAI",
    ], 1)
    add("O|S", ["AMAZON", "FLPKRT", "SWIGGY", "ZOMATO", "BLINKT", "ZEPTON", "BIGBSK", "DELHVY", "EKARTL", "SHADOW", "XPRESB", "MEESHO", "MYNTRA", "JIOMRT"], [
        "Your order {oid} is out for delivery. Share OTP {otp4} with the delivery agent to receive it.",
        "Delivery code for your order: {otp4}. Share only after you receive the package.",
        "Your Instamart order is arriving! Share PIN {otp4} with {first} at the door.",
    ], 1)
    add("O|U", ["INDANE", "HPGASL", "BHRTGS", "MYGATE", "NOBROK"], [
        "Your Delivery Authentication Code (DAC) for LPG refill is {otp4}. Share it only with the delivery person.",
        "Gate pass code for your guest {first}: {otp4}. Valid today only.",
    ], 1)

    # ---------------------------------------------------------------------------------------------
    # Shopping: every step of an order
    # ---------------------------------------------------------------------------------------------
    add("S", ["AMAZON", "FLPKRT", "MYNTRA", "MEESHO", "NYKAAA", "AJIOIN", "TATACL", "CROMAR", "RELDIG", "VIJAYS", "LNSKRT", "FRSTCY", "PEPRFY",
              "URBNLD", "IKEAIN", "DECTHL", "BEWAKF", "SNAPDL", "SHOPSY", "TATANU", "BOATLS", "NOISEE", "JIOMRT", "LIFSTL", "WESTSD", "MAXFAS"], [
        "Order confirmed! Your order {oid} for {item} will be delivered by {date}.",
        "Your order {oid} is delayed due to unforeseen circumstances. New delivery date: {date}. We're sorry for the delay.",
        "Your package with {item} has been handed over to our courier partner. Track: {link:amzn.in/d}",
        "Hi {first}, your order has been packed and is ready to ship!",
        "Your order {oid} could not be delivered today. We'll try again tomorrow.",
        "Invoice for your order {oid} is ready. Download it from My Orders.",
        "Installation for your {item} is scheduled for {date}. Our technician will call you before visiting.",
        "We've picked up your return for order {oid}. Refund will be processed once it reaches our warehouse.",
        "Exchange confirmed for {item}. The new item will be delivered by {date}.",
        "Your pre-order for iPhone 18 Pro is confirmed. Expected dispatch: {date}.",
        "Seller has cancelled your order {oid} as the item is out of stock. Your refund of Rs {amt_s} has been initiated.",
        "How was your {item}? Rate and review your purchase: {link:fkrt.it}",
        "Your order {oid} was delivered and received by {first} (security guard).",
        "Size not right? You can exchange {item} until {date}.",
        "Your Amazon Fresh order will arrive between 6 PM and 8 PM today.",
    ], 1)
    add("S", ["SWIGGY", "ZOMATO", "EATSRE", "DOMINO", "MCDIND", "PIZZAH", "KFCIND", "BKINGS", "FAASOS", "BOXEIT", "SWGYIN"], [
        "Your order from {rest} has been picked up and is on the way.",
        "{rest} has accepted your order. Estimated delivery in {min} minutes.",
        "Sorry! {rest} couldn't accept your order. Your refund of Rs {amt_s} will reach you in 5-7 days.",
        "Your Swiggy Genie package has been delivered to {first}.",
    ], 1)
    add("S", ["BLINKT", "ZEPTON", "INSTMT", "BIGBSK", "BBDALY", "MILKBK", "CNTRYD", "FRSHTH", "LICIOS", "DMARTR", "JIOMRT", "SPNCRS"], [
        "Your Blinkit order is out for delivery. Arriving in {min} mins.",
        "Your bb daily milk order for tomorrow has been placed. Rs {amt_s} will be deducted from your wallet.",
        "Item unavailable: Amul Butter 500g was out of stock and has been removed from your order. Refund Rs {amt_s} initiated.",
        "Your Country Delight delivery for tomorrow morning is confirmed.",
        "Your Licious order will be delivered between 7 and 8 PM.",
    ], 1)
    add("S", ["TATA1M", "PHEASY", "NETMED", "APOLLO", "MEDPLS", "TRUEMD"], [
        "Your prescription has been verified. Order {oid} will be dispatched today.",
        "Your medicine order {oid} is out for delivery. Pay Rs {amt_s} on delivery.",
        "Doctor consultation required for order {oid}. Our doctor will call you shortly.",
    ], 1)
    add("S|T", ["AMAZON", "FLPKRT", "MYNTRA", "AJIOIN", "NYKAAA", "SWIGGY", "ZOMATO", "MEESHO"], [
        "Refund of Rs {amt_s} for {item} has been initiated to your original payment method. It may take 5-7 days.",
        "Your refund for order {oid} has been credited to your bank account. Ref {ref}.",
    ], 1)

    # ---------------------------------------------------------------------------------------------
    # Couriers
    # ---------------------------------------------------------------------------------------------
    add("S|U", ["DELHVY", "BLUDRT", "EKARTL", "SHADOW", "XPRESB", "DTDCIN", "ECOMEX", "INPOST", "SHPRKT", "AMZLOG", "GATIKW", "PROFCR", "SMARTR", "TRKON"], [
        "Your shipment {awb} has reached the {city} hub and will be delivered in 2 days.",
        "Delivery rescheduled: your parcel {awb} will now be delivered on {date} as requested.",
        "Your parcel {awb} has been delivered and signed by {first}. Thank you.",
        "Pickup for your return shipment {awb} is scheduled for tomorrow.",
        "Your COD shipment {awb} of Rs {amt_s} is out for delivery. Keep exact change ready.",
        "Delivery agent {first} ({phone}) will deliver your shipment {awb} today.",
        "Speed Post article {awb}IN delivered at {city} on {date}. -India Post",
    ], 1)

    # ---------------------------------------------------------------------------------------------
    # Travel
    # ---------------------------------------------------------------------------------------------
    add("V", ["IRCTCI", "IRCTCS", "NTESIN", "RAILWY", "CRISRL"], [
        "PNR {pnr}: WL 12 has been confirmed. Coach S4, Berth 23. Happy journey!",
        "Train {trn} will arrive at platform 5 of {stn}. Scheduled departure {time}.",
        "Your Tatkal ticket PNR {pnr} for {date} is booked. CNF B1 45.",
        "Train {trn} on {date} is cancelled due to operational reasons. Full refund will be credited automatically.",
        "Diverted: Train {trn} will run via {stn} on {date}.",
        "Your e-catering order for PNR {pnr} will be delivered at {stn} at {time}.",
        "Retiring room booking at {stn} confirmed for {date}. Booking ID {ref}.",
    ], 1)
    add("V", ["INDIGO", "AIRIND", "AKASAA", "SPJETT", "AIXCNT", "VSTARA", "STARAI", "ALLIAN"], [
        "Gate change: flight 6E {flight} to {to} will now depart from gate 31.",
        "Your flight AI {flight} {from}-{to} is now boarding at gate 12.",
        "Baggage for flight QP {flight} will arrive on belt 4.",
        "We're sorry: flight SG {flight} on {date} has been cancelled. Choose a free reschedule or full refund at spicejet.com.",
        "Your seat 12A on flight 6E {flight} is confirmed. Check in from 48 hours before departure.",
        "Reminder: your flight {from}-{to} departs tomorrow at {time}. Web check-in is open.",
        "Your DigiYatra boarding is active for flight 6E {flight}.",
    ], 1)
    add("V", ["MMTRIP", "GOIBIB", "CLRTRP", "EMTRIP", "YATRAA", "IXIGOO", "REDBUS", "ABHIBS", "ZINGBS", "OYOROM", "TREBOH", "FABHTL", "AIRBNB",
              "THOMSC", "VFSGLB", "ZOOMCR", "REVVIN", "DELMTR", "MUMMTR", "BLUSMT", "NAMMAY"], [
        "Your bus {veh} departs from {city} at {time}. Boarding point: Kashmere Gate. Seat 14.",
        "Your bus is running 30 minutes late. Live tracking: {link:rdbs.in}",
        "Your stay at {city} is confirmed. Check-in {date} 12 PM, check-out {date2} 11 AM.",
        "Your holiday package to Goa is confirmed. Itinerary has been emailed to you.",
        "Your visa application {ref} has been received at the VFS Global centre in {city}.",
        "Your Zoomcar trip starts at {time}. Car {veh} is parked at Sector 29.",
        "Your Delhi Metro QR ticket from Rajiv Chowk to {city} is valid till {time}.",
        "Your Airbnb host {first} has confirmed your reservation for {date}.",
        "Your cab to the airport is scheduled for {time} tomorrow. Driver details will be shared 30 mins before pickup.",
    ], 1)
    add("V|O", ["UBERIN", "OLACAB", "RAPIDO", "NAMMAY", "BLUSMT", "INDRIV", "MERUCB"], [
        "Your ride with {first} is confirmed. {veh}, white Dzire. Start OTP {otp4}.",
        "Your Rapido captain has arrived. OTP {otp4}.",
    ], 1)
    add("V|T", ["UBERIN", "OLACAB", "RAPIDO", "IRCTCI", "MMTRIP", "INDIGO", "REDBUS"], [
        "Thanks for riding with Uber. Your trip fare: Rs {amt_s}. Paid via UPI.",
        "Refund of Rs {amt_s} for PNR {pnr} has been processed to your payment method.",
        "Your booking for {from}-{to} on {date} is confirmed. Amount paid: Rs {amt}. Booking ID {ref}.",
    ], 1)

    # ---------------------------------------------------------------------------------------------
    # Health, education, jobs, home services, apps, society: Updates
    # ---------------------------------------------------------------------------------------------
    add("U", ["APOLLO", "PRACTO", "MAXHSP", "FORTIS", "MEDANT", "AIIMSD", "THYRCR", "LALPTH", "METROP", "REDCLF", "HEALTF", "PHARME", "TATA1M", "MFINE",
              "CULTFT", "HLTHKT", "DENTAL", "NARAYN", "MANIPL", "ASTERH", "CLOUDN"], [
        "Your blood sample collection is scheduled for {date} between 7 and 8 AM. Please fast for 10 hours.",
        "Reminder: take your medicine Metformin 500 mg after dinner.",
        "Your video consultation with Dr. {first} starts in 10 minutes. Join from the app.",
        "Your health check-up report is ready. View it here: {link:apollo247.com/r}",
        "Your token number is {l2} for OPD at {time}. Please be on time.",
        "Your vaccination (Hepatitis B, dose 2) is due on {date}.",
        "Thank you for donating blood today. You can donate again after 90 days.",
        "Your cult.fit membership is paused till {date} as requested.",
        "Your eye test appointment at Lenskart {city} is confirmed for {date}.",
        "Discharge summary for patient {NAME} has been shared on email.",
    ], 1)
    add("U", ["CBSEIN", "UPSCGV-G", "NTAEXM-G", "SSCGOV-G", "IGNOUU", "DUNIVR", "AMITYU", "VITUNI", "DPSSCH", "KVSSCH", "UNACAD", "BYJUSS",
              "PWLIVE", "ALLENI", "AAKASH", "COURSR", "UDEMYY", "CHEGGS", "GREEDU", "IELTSB"], [
        "Your ward {first} was absent from school today. Please send a leave note.",
        "Parent-teacher meeting on Saturday {date} from 9 AM to 12 PM.",
        "Results for Class XII Board exams have been declared. Check at results.cbse.nic.in",
        "Your admit card for JEE Main Session 2 is available. Exam date: {date}.",
        "Your answer key challenge for application {ca} has been accepted.",
        "School will remain closed tomorrow due to heavy rain. Online classes will continue.",
        "Your IELTS test result is available. Overall band 7.5.",
        "Congratulations! You have completed the course 'Python for Data Science'. Download your certificate.",
        "Assignment 4 is due tonight at 11:59 PM.",
        "Your seat has been allotted in Round 2 counselling. Report to the college by {date}.",
    ], 1)
    add("U", ["NAUKRI", "LNKDIN", "INDEED", "APNAJB", "SHINE", "IIMJOB", "INSTHR", "FOUNDT", "TCSCAR", "INFOSS", "WIPROC", "ACCENT"], [
        "Your interview with Infosys is scheduled on {date} at {time}. Join link sent to your email.",
        "Congratulations! You have been shortlisted for the next round at TCS.",
        "Your application for Data Analyst at Flipkart has been viewed by the recruiter.",
        "Offer letter released. Please accept it on the candidate portal by {date}.",
        "Your background verification is complete. Welcome aboard!",
        "Your salary slip for October 2026 is available on the employee portal.",
    ], 1)
    add("U", ["URBNCO", "HOUSJY", "NOBROK", "MYGATE", "APNACM", "ADDA247", "SULKHA", "JUSTDL", "BROKRG", "SERVIZ", "LGESRV", "SAMSRV", "MARUTI", "HYUNDI",
              "TATAMT", "HEROMC", "BAJAJA", "ROYALE", "ATHERE", "OLAELC"], [
        "Your Urban Company professional {first} will reach between 10 and 11 AM tomorrow.",
        "Your AC service booking {ref} is confirmed for {date}.",
        "Your car {veh} is due for its 10,000 km service. Book a slot at your nearest Maruti workshop.",
        "Water supply will be shut on {date} from 10 AM to 4 PM for tank cleaning. -RWA",
        "Lift in Tower B will be under maintenance tomorrow from 11 AM to 1 PM.",
        "Visitor {first} (Swiggy) has been allowed in at {time}.",
        "Your LG washing machine service request {ref} is resolved. Rate the technician.",
        "Your Ather scooter's firmware update is ready. Install it from the dashboard.",
    ], 1)
    add("U", ["GOOGLE", "MSFTIN", "APPLEI", "WHATSP", "FACEBK", "INSTAG", "NETFLX", "HOTSTR", "SPOTIF", "PRIMEV", "JIOCNM", "SONYLV", "ZEE5IN",
              "TRUECL", "DROPBX", "ZOOMUS", "GITHUB", "CANVAI"], [
        "Your Google account password was changed. If this wasn't you, secure your account.",
        "New sign-in to your Microsoft account from Chrome on Windows in {city}.",
        "Your Apple ID was used to sign in to an iPad.",
        "Your JioCinema premium subscription has been renewed till {date}.",
        "Your Netflix plan will change to Standard from your next billing date.",
        "Your Spotify Premium Family invite was accepted by {first}.",
        "Your Instagram account was accessed from a new device. If this was you, ignore this message.",
        "Your Zoom meeting 'Weekly sync' starts in 15 minutes.",
    ], 1)

    # ---------------------------------------------------------------------------------------------
    # Offers: everything selling something, from banks to jewellers
    # ---------------------------------------------------------------------------------------------
    add("F", ["TANISQ-P", "KALYAN-P", "MALABR-P", "JOYALK-P", "CARATL-P", "PCJEWL-P", "SENCO-P", "BLUSTN-P", "MELORA-P"], [
        "Gold rate today: 22K Rs 7,250/g. Make your Dhanteras special with 0% making charges on gold coins. Visit your nearest showroom.",
        "Wedding season offer: flat 25% off on diamond jewellery making charges. Till {date}. T&C",
        "Book your Akshaya Tritiya gold now and get protection against price rise. Book online: {link:tanishq.co.in/b}",
    ], 1)
    add("F", ["MARUTI-P", "HYUNDI-P", "TATAMT-P", "MAHNDR-P", "KIAIND-P", "TOYOTA-P", "HONDAC-P", "HEROMC-P", "TVSMTR-P", "OLAELC-P", "ATHERE-P"], [
        "Drive home the new Creta this festive season. Benefits up to Rs 50,000. Book a test drive: {link:hyundai.co.in/td}",
        "Exchange your old bike and get Rs 10,000 extra on the new Splendor+. Offer ends {date}.",
        "Zero down payment on the Ola S1. EMI from Rs 2,999. Book now!",
    ], 1)
    add("F", ["LAKMES-P", "VLCCIN-P", "NATURL-P", "GOLDGY-P", "ANYTMF-P", "CULTFT-P", "TALWLK-P", "ENRICH-P", "JAWEDH-P", "KAYAKL-P"], [
        "This weekend only: hair spa at Rs 499. Book your slot at Naturals {city}. Call {phone}",
        "Join Cult this month and get 2 months free. Limited slots!",
        "Get glowing skin for the wedding season. Flat 30% off on facials. Book now.",
    ], 1)
    add("F", ["LODHAG-P", "GODREJ-P", "DLFLTD-P", "PRESTG-P", "SOBHAL-P", "MAGICB-P", "99ACRS-P", "HOUSNG-P", "NOBROK-P", "SQRYDS-P"], [
        "Ready-to-move 3BHK in {city} at Rs 1.2 Cr*. Site visit with free cab pickup. Call {phone}",
        "Plots near the new airport starting Rs 25 Lakh. RERA approved. Limited inventory.",
        "Looking to rent? 5,000+ verified flats with zero brokerage on NoBroker.",
    ], 1)
    add("F", ["HDFCBK", "ICICIB", "SBICRD", "AXISBK", "KOTAKB", "IDFCFB", "YESBNK", "INDUSB", "AUBANK", "RBLBNK", "BOBCRD", "AMEXIN", "SCBANK", "HSBCIN"], [
        "Your Credit Card is eligible for a Personal Loan of up to Rs {amt_l} with instant disbursal. Check now: {link:hdfcbk.io/HDFCBK/s}",
        "Get Rs 2,000 off on Samsung Galaxy S26 with your card. Valid on Amazon till {date}.",
        "Upgrade to Infinia Metal Edition. Exclusive invite only for you. Know more.",
        "Open an FD at 7.75% p.a. in just 2 minutes on the app. Senior citizens get 0.5% extra.",
        "Get 10X reward points on dining this weekend with your card. T&C apply.",
        "You have a pre-qualified offer for a Car Loan at attractive rates. Apply online.",
        "Buy now, pay later: convert your spends above Rs 2,500 into EMIs at 0 processing fee.",
        "Earn up to 5% cashback on bill payments with the new Millennia card. Apply now.",
        "Refer a friend to open a savings account and earn Rs 500 for every successful referral.",
        "Invest in Sovereign Gold Bonds Series III. Subscription open till {date}. Apply via net banking.",
    ], 1)
    add("F", ["AIRTEL", "JIOINF", "VICARE", "BSNLIN", "TATAPL", "DISHTV", "ACTFIB", "AIRTEL-P", "JIOINF-P"], [
        "Get Disney+ Hotstar free with the Rs 449 plan. Recharge now: {link:i.airtel.in/r}",
        "Your number is eligible for a free 5G upgrade. Get unlimited 5G data now.",
        "Switch to JioFiber and get 30 days free. Book now: {link:jio.com/f}",
        "Watch the India vs Australia final on Tata Play. Add the sports pack for Rs 49.",
        "Recharge Rs 299 today and get 2GB extra data every day. Hurry!",
    ], 1)
    add("F", ["AMAZON", "FLPKRT", "MYNTRA", "NYKAAA", "AJIOIN", "TATACL", "CROMAR", "RELDIG", "LIFSTL", "WESTSD", "MAXFAS", "PANTLN", "BEWAKF",
              "SWIGGY", "ZOMATO", "BLINKT", "ZEPTON", "BIGBSK", "DOMINO", "PIZZAH", "MCDIND", "KFCIND", "BKINGS", "STARBK", "CHAAYS", "HALDRM"], [
        "Prices drop at midnight! Black Friday sale starts tonight. Set a reminder: {link:myntr.it}",
        "Hi {first}, items in your wishlist are now on sale. Grab them before they're gone!",
        "Free delivery on your next 5 orders with Swiggy One Lite. Join for Rs 99.",
        "It's raining deals! Up to 60% off on monsoon essentials.",
        "Your Rs 200 off coupon expires tonight. Use it on orders above Rs 799.",
        "Pizza Mania at Rs 99. Order now and get it in 30 minutes!",
        "New launch: Pumpkin Spice Latte is back! Order on the Starbucks app.",
        "Shop for Rs 2,999 and get a gift worth Rs 999 free. Only this weekend.",
        "Happy Diwali! Celebrate with sweets from Haldiram's. Extra 10% off on gift boxes.",
        "Your Flipkart SuperCoins are expiring. Use them before {date}.",
    ], 1)
    add("F", ["MMTRIP", "GOIBIB", "CLRTRP", "EMTRIP", "YATRAA", "IXIGOO", "INDIGO", "AIRIND", "AKASAA", "SPJETT", "REDBUS", "OYOROM", "BKMYSH", "PVRCIN",
              "INOXMV", "DISTRC", "THOMSC", "SOTCIN"], [
        "Fly to Dubai from Rs 9,999 one way. Book by {date}!",
        "Long weekend coming up! Up to 40% off on hotels in Rishikesh and Manali.",
        "Buy 1 get 1 on movie tickets this Wednesday with your HDFC card.",
        "Europe tour packages from Rs 1,49,999 per person. EMI available.",
        "Grab a free seat upgrade on your next flight. Use code UPGRADE.",
    ], 1)
    add("F", ["POLBZR", "ACKOIN", "DIGITI", "HDFCER", "ICILOM", "TATAAI", "NIVABU", "CAREHL", "STARHL", "LICIND", "MAXLIF", "HDFCLI", "BAJAJA",
              "GROWWI", "ZERODH", "UPSTOX", "ANGELB", "PAYTMM", "INDMNY", "SMLCSE", "KUVERA", "ETMONY"], [
        "Your health insurance renewal is due. Renew online and get 10% off. {link:pbz.in/r}",
        "Protect your family with Rs 1 Cr term cover at Rs 15/day. Get a quote.",
        "New NFO: Nippon India Nifty Midcap 150 Index Fund. Invest from Rs 500. NFO closes {date}.",
        "Start your SIP with just Rs 100 on Groww. Download the app.",
        "Open a free Demat account in 5 minutes. Zero brokerage on delivery.",
    ], 1)
    add("F", ["UNACAD", "BYJUSS", "PWLIVE", "UPGRAD", "SIMPLI", "GREATL", "SCALER", "COURSR", "ALLENI", "AAKASH", "VEDANT", "TOPPRS"], [
        "Free demo class for Class 10 Maths this Sunday. Register now: {link:vdnt.in/d}",
        "Crack CAT 2027 with India's top mentors. Batch starts {date}. Early bird discount 30%.",
        "Get a job-ready certificate in AI and ML. 100% placement assistance. Apply now.",
    ], 1)
    add("F|U", ["BJPIND-P", "INCIND-P", "AAPIND-P", "NAMOAP", "MYGOVT", "PMOIND"], [
        "Join us at the public rally in {city} on {date} at 4 PM. Your vote, your voice.",
        "Happy Diwali from the Government of India. Celebrate a green and safe Diwali.",
    ], 1)
    add("U|F", ["AMAZON", "FLPKRT", "SWIGGY", "ZOMATO", "UBERIN", "OLACAB", "HDFCBK", "ICICIB", "AIRTEL", "JIOINF", "MAKEMT", "INDIGO"], [
        "How was your experience with us? Rate us in 10 seconds: {link:survey.in/r}",
        "We value your feedback. Please take a 2-minute survey about your recent visit.",
        "Wishing you and your family a very Happy Diwali!",
    ], 1)

    # ---------------------------------------------------------------------------------------------
    # People: more of how Indians text
    # ---------------------------------------------------------------------------------------------
    people([
        "Beta khana kha liya?", "Papa ki dawai le aana shaam ko", "Bhaiya 2 kg aloo aur 1 kg pyaaz bhej dena, Flat 402",
        "Ok", "K", "Hmm", "👍", "😂😂😂", "❤️", "Gn", "Gm bhai", "Thik hai", "Haan bol", "Call kar", "Free hai?",
        "Plz call back urgently", "Where r u?? Waiting since 20 mins", "Reached?", "Leaving now, 15 min mein pahunch jaunga",
        "Didi ki shaadi ki date fix ho gayi - 12 Feb!", "Happy anniversary to the best parents ❤️", "Jai Shri Ram 🙏 Good morning",
        "Aaj ka match dekha? Kohli ne kya maara!", "Bro send notes of chapter 5 pls", "Ma'am kal test hai kya?",
        "Sir mera leave approve kar dijiye, ghar pe function hai", "Team, standup moved to 10:30 tomorrow",
        "Invoice bhej diya hai mail pe, check kar lena", "Rs 500 GPay kar diya, receipt bhejun?", "Paise mil gaye, thanks",
        "Kal 5 baje Connaught Place, metro gate 2", "Address: B-42, Sector 15, Noida 201301. Landmark: near Shiv mandir",
        "My flight is at 6:45 AM, can you drop me to the airport?", "Doctor ne bola 3 din rest karna hai",
        "Nani ki tabiyat theek nahi hai, hospital ja rahe hain", "Exam kaisa gaya?", "Result aa gaya! 92% 🎉",
        "Interview clear ho gaya bhai!!", "Can you share the wifi password?", "Gas wala aaya tha, cylinder le liya",
        "Kya aap kal aa sakte ho? Pipe leak ho raha hai - Ramesh plumber", "Madam, aaj kaam pe nahi aa paungi, bacche ki tabiyat kharab hai",
        "Tution fees is 3000 this month. Please send by 5th - Anjali ma'am", "Hi, this is Rahul from HR. Can we talk at 4?",
        "Your cake order is ready for pickup - Sweet Tooth Bakery", "Bhai, PUBG khelega?", "Check this out: https://www.instagram.com/reel/abc123",
        "Song link https://open.spotify.com/track/xyz", "Location https://maps.google.com/?q=28.61,77.20", "Video dekh https://youtu.be/dQw4w9WgXcQ",
        "Saved your number. This is Neha from the yoga class 🙂", "Missed your call, in a meeting. Will call back.",
        "Sorry, can't talk now.", "I'll be late, start dinner without me",
        "Bhai 2000 udhaar de de, salary aate hi lauta dunga",
        "Sent 1500 for the trip on PhonePe", "UPI pe 250 bhej de chai ka", "Aaj bank band hai kya?", "HDFC ka app chal raha hai tera?",
        "Card block karwa diya maine, kho gaya tha", "Mera account hack ho gaya lagta hai, OTP aa rahe hain",
        "Free mein Netflix chahiye? Mera password le le", "Wedding card: You're invited to Rohan & Priya's wedding on 14 Dec. RSVP 9876543210",
        "RIP Sharma uncle. Antim sanskar kal subah 10 baje", "Happy Holi! 🎨", "Eid Mubarak to you and family 🌙",
        "Merry Christmas! 🎄", "Sat Sri Akal ji, Gurpurab di lakh lakh vadhaiyan", "Vanakkam! Pongal vazhthukkal",
        "নমস্কার, কেমন আছো?", "ಹೇಗಿದ್ದೀರಾ?", "எப்போ வருவே?", "ఎక్కడ ఉన్నావు?", "कहाँ हो? फ़ोन उठाओ",
    ], "P")

    # Asking for a code: fine from someone you know.
    people(["Can you send me the OTP you got from Swiggy? I ordered on your account", "Arre tera OTP aaya hoga Amazon ka, bata de jaldi"], "P", kinds=("c",))

    # ---------------------------------------------------------------------------------------------
    # Spam and scams people really get
    # ---------------------------------------------------------------------------------------------
    add("X", ["+"], [
        "Airtel Warning: SPAM | Dear Customer, your electricity will be disconnected tonight. Call {phone} immediately.",
        "Airtel Warning: SPAM | Congratulations! You won Rs 25 lakh. Call {phone}",
        "Airtel Warning: SPAM | Earn Rs 3000 daily from home. WhatsApp {phone}",
        "Suspected Spam: Your KYC is pending. Update now {link:kyc-online.co}",
        "VIP mobile numbers available: 98100-00000, 99999-12345, 90000-90000. Want similar fancy no.? Call/WhatsApp {phone}",
        "Fancy number for sale 9999-888-777. Golden, platinum and diamond numbers available. Call {phone}",
        "Dear Sir, your vehicle {veh} has an e-Challan of Rs 2000 pending. Pay now or face court action: {link:echallan-pay.in/v}",
        "Your e-challan {ref} is pending. Pay immediately: {link:parivahan-echallan.co.in/p}",
        "PM Yojana: Rs 6,000 will be credited to your account. Register now to claim: {link:pmyojana-free.in/r}",
        "Free laptop yojana 2026 for all students. Apply now: {link:free-laptop-yojana.xyz/a}",
        "Congratulations! Your SBI reward points worth Rs 9,850 are about to expire. Redeem now: {link:sbi.reward-points.top/r}",
        "Your LIC policy has a bonus of Rs 1,25,000. To claim, call our officer {phone} today.",
        "Upgrade your SIM to 5G now. Reply with 1 or call {phone} or your SIM will be blocked within 24 hours.",
        "Your Netflix subscription has failed. Update your payment information here: {link:netflix-update.click/u}",
        "Amazon: Your order of iPhone 16 Pro (Rs 1,39,900) is placed. If not you, call {phone} to cancel.",
        "We noticed a login to your Paytm from a new device. If not you, call our helpline {phone} now.",
        "Your mobile number is selected for a Rs 15 lakh personal loan at 2% interest. Call {phone}",
        "Instant loan in 5 minutes without CIBIL. Download the app: {link:quickcash-loan.apk.link/d}",
        "Hello, I am Priya from Mumbai. I saw your profile. Want to make friends? WhatsApp me {phone}",
        "Hot and beautiful girls available in your city. Call {phone}",
        "Satta matka fast result. Daily guaranteed game. Contact {phone}",
        "IPL betting tips: 100% sure win. Join our VIP group: {link:t.me}",
        "Invest Rs 10,000 and get Rs 50,000 in 7 days. Guaranteed returns. WhatsApp {phone}",
        "Your parcel from Dubai is held at customs. Pay Rs 3,500 clearance fee to release it: {link:customs-clearance.cc/p}",
        "This is Inspector Sharma from Cyber Crime. Your Aadhaar is linked to a money laundering case. Call {phone} immediately or you will be arrested.",
        "Sir, galti se aapke number pe Rs 2000 ka recharge ho gaya hai, please wapas bhej dijiye {phone}",
        "Your gas connection will be disconnected today. Update your KYC by calling {phone}",
        "Dear user, your WhatsApp account will be banned. Verify now: {link:whatsapp-verify.top/v}",
        "Your credit card has been charged Rs 49,999. If not done by you, call {phone} to cancel the transaction.",
        "Congratulations! Your Jio number won the KBC lucky draw of Rs 35 lakh. Contact manager {phone}",
        "Govt job vacancy in Railways. No exam. Direct joining. Registration fee Rs 999. Call {phone}",
        "Work from home: like YouTube videos and earn Rs 50 per like. Message on Telegram @earn_daily{l3}",
        "Your electricity meter is not updated. Your power will be cut at 9:30 PM. Contact electricity officer {phone}",
        "Dear customer, your account is blocked due to PAN not updated. Click: {link:pan-update-bank.co.in/u}",
        "आपका बिजली कनेक्शन आज रात काट दिया जाएगा। तुरंत {phone} पर संपर्क करें।",
        "आपने 25 लाख की लॉटरी जीती है। इनाम पाने के लिए {phone} पर कॉल करें।",
        "Mummy maine naya number liya hai, ye purana band ho gaya. Mujhe 5000 ki jaldi zarurat hai, is UPI pe bhej do {vpa}",
        "Hi dad, my phone broke, this is my new number. Can you send Rs 10,000 urgently? I'll explain later.",
    ], 1)
    add("X", ["=support@bankalerts-sbi.com", "=noreply@paytm-kyc.in", "=cybercell.delhi@gmail.com", "=winner.kbc2026@gmail.com"], [
        "Your SBI YONO account is suspended. Complete KYC: {link:yono-sbi-kyc.top/k}",
        "Congratulations! You won Rs 25,00,000 in KBC. Contact on WhatsApp {phone}",
    ], 1)
    add("X|F", ["WINZOG-P", "MPLGAM-P", "RUMMYC-P", "JUNGLE-P", "POKERB-P", "A23GAM-P"], [
        "Win up to Rs 1 crore playing rummy! Deposit Rs 100, get Rs 1000 bonus. Download now.",
        "Play poker and win real cash. Join now with Rs 2,000 welcome bonus.",
    ], 1)
    add("F|X|P", ["+"], [
        "Diwali dhamaka at Gupta Electronics! LED TV at Rs 9,999. Visit Sector 18 market or call {phone}",
        "Fresh paneer and dairy delivered every morning. Call Shyam Dairy {phone}",
        "Admission open for Class 1-12 at Bright Future Coaching. Call {phone}",
        "Packers and movers at lowest rates. Call {phone} for a free quote.",
    ], 1)

    # Either tab is right for these.
    add("U|T", BANKS, ["Your cheque no {l5} for Rs {amt} has been received for clearing. It will be credited after realisation.",], 1)
    add("T|U", ["TATAPL", "DISHTV", "AIRTEL"], ["Low balance: your DTH balance is Rs 12. Recharge before {date} to keep watching.",], 1)
    add("U|S", ["ZOMATO", "SWIGGY", "EAZYDN"], ["Your table for 4 at {rest} is confirmed for {date} at {time}. Booking via Zomato Dining.",], 1)
    add("S|U", ["MYGATE", "NOBROK", "APNACM"], ["Your parcel from Amazon has been kept at the society gate.",], 1)
    add("U|S|T", ["MARUTI", "HYUNDI", "TATAMT", "HEROMC"], ["Your vehicle {veh} is ready for pickup at the service centre. Invoice: Rs {amt}.",], 1)
    add("S|U", ["ROYALE"], ["Your new Royal Enfield Classic 350 has been dispatched to the dealership. Delivery on {date}.",], 1)
    add("V|U", ["VFSGLB"], ["Your passport is ready for collection at VFS {city}.",], 1)
    add("F|U", ["ACKOIN", "DIGITI", "POLBZR"], ["Your bike insurance expires in 3 days. Renew now in 2 minutes with ACKO.",], 1)
