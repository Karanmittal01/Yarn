"""Writes core/intelligence/src/test/resources/sms-corpus.tsv: a large, realistic Indian inbox.

    python tools/corpus/make_sms_corpus.py

Every template is modelled on the real formats Indian banks, wallets, shops, couriers, airlines,
telecoms, utilities and government services send, plus the scams people actually receive. Values
(names, amounts, cards, references) are made up and change per copy. Each row says which inbox
tab the message belongs in; SpamCorpusTest fails on every row Yarn files elsewhere.

Tabs: P personal, O OTP, T transactions, S shopping, V travel, U updates, F offers, X spam/scam.
"a|b" means either is fine. Every tab except X also means "never spam".
Senders: "+91…" an unknown number, "c:+91…" a saved contact, anything else a sender ID.
"""
import os
import random

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "core", "intelligence", "src", "test", "resources", "sms-corpus.tsv")
R = random.Random(20261126)

OPS = ["AX", "AD", "BP", "BZ", "CP", "JD", "JK", "JM", "VD", "VK", "VM", "TX", "TM", "QP", "BW", "DM"]
FIRST = ["Rahul", "Priya", "Amit", "Neha", "Karan", "Pooja", "Vikram", "Anjali", "Rohit", "Sneha", "Arjun", "Kavya", "Sanjay", "Meera", "Aditya", "Ritu", "Manish", "Divya", "Suresh", "Lakshmi"]
LAST = ["Sharma", "Verma", "Gupta", "Singh", "Patel", "Reddy", "Iyer", "Nair", "Kumar", "Mehta", "Joshi", "Das", "Rao", "Mishra", "Agarwal", "Khan", "Bose", "Pillai"]
MERCHANTS = ["SWIGGY", "ZOMATO LTD", "AMAZON PAY INDIA", "FLIPKART INTERNET", "RELIANCE RETAIL", "BIGBASKET", "UBER INDIA", "IRCTC", "BPCL FUEL", "DMART AVENUE",
             "APOLLO PHARMACY", "PAY*ATRIA CONVE", "RAZ*ZOMATO", "PYU*Swiggy", "GOOGLE PLAY", "NETFLIX.COM", "MAKEMYTRIP", "CROMA", "MYNTRA", "BLINKIT", "ZEPTO", "SHELL INDIA",
             "STARBUCKS", "DECATHLON", "NYKAA", "JIOMART", "SPENCERS", "HPCL", "PVR INOX", "BOOKMYSHOW"]
SHOPS = ["MagicPin", "Swiggy", "Zomato", "BookMyShow", "Blinkit", "Uber", "Myntra", "Dunzo", "Rapido", "Zepto"]
ITEMS = ["boAt Airdopes 141", "Prestige Pressure Cooker 5L", "Redmi Note 14 Pro", "Puma Running Shoes", "Philips Trimmer BT3221", "Milton Water Bottle 1L", "Harry Potter Box Set",
         "Fire-Boltt Smart Watch", "Wildcraft Backpack", "Lakme Kajal", "Samsung 25W Charger", "Noise Buds VS102", "Bajaj Mixer Grinder", "Levi's Men's Jeans", "Pigeon Kettle"]
RESTAURANTS = ["Behrouz Biryani", "Domino's Pizza", "McDonald's", "Haldiram's", "Burger King", "Faasos", "Wow! Momo", "Chaayos", "Paradise Biryani", "KFC"]
CITIES = [("DEL", "New Delhi"), ("BOM", "Mumbai"), ("BLR", "Bengaluru"), ("MAA", "Chennai"), ("HYD", "Hyderabad"), ("CCU", "Kolkata"), ("PNQ", "Pune"), ("GOI", "Goa"), ("JAI", "Jaipur"), ("AMD", "Ahmedabad")]
STATIONS = ["NDLS", "BCT", "SBC", "MAS", "HWH", "PUNE", "JP", "ADI", "LKO", "CNB", "BPL", "SC"]
MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]


def name():
    return f"{R.choice(FIRST)} {R.choice(LAST)}"


def upper_name():
    return name().upper()


def digits(n):
    return "".join(R.choice("0123456789") for _ in range(n))


def amt(lo=50, hi=50000, paise=None):
    v = R.uniform(lo, hi)
    if paise is None:
        paise = R.random() < 0.5
    whole = int(v)
    s = f"{whole:,}" if R.random() < 0.6 else str(whole)
    if paise:
        s += f".{R.randint(0, 99):02d}"
    return s


def inr_amt(lo=50, hi=50000):
    return f"{amt(lo, hi, True)}"


def date():
    d, m, y = R.randint(1, 28), R.randint(1, 12), 26
    return R.choice([f"{d:02d}-{m:02d}-{y}", f"{d:02d}/{m:02d}/20{y}", f"{d:02d}-{MONTHS[m - 1]}-{y}", f"{d:02d}{MONTHS[m - 1]}{y}", f"{d:02d} {MONTHS[m - 1]} 20{y}", f"20{y}-{m:02d}-{d:02d}"])


def time():
    return f"{R.randint(0, 23):02d}:{R.randint(0, 59):02d}" + (f":{R.randint(0, 59):02d}" if R.random() < 0.4 else "")


def vpa():
    n = R.choice(FIRST).lower()
    return R.choice([f"{n}.{R.choice(LAST).lower()}@okaxis", f"{n}{digits(2)}@ybl", f"{digits(10)}@paytm", f"q{digits(9)}@ybl", f"paytmqr{digits(10)}@paytm",
                     f"{n}@oksbi", f"{n}{digits(3)}@okhdfcbank", f"swiggy.payu@hdfcbank", f"bharatpe.{digits(8)}@icici", f"{n}@ibl"])


def phone():
    return R.choice("6789") + digits(9)


def otp(n=6):
    return digits(n)


def pnr():
    return digits(10)


def awb():
    return R.choice(["", "SF", "FMPP", "14", "7D"]) + digits(R.choice([10, 11, 12]))


def oid():
    return R.choice([f"{digits(3)}-{digits(7)}-{digits(7)}", f"OD{digits(18)}", f"#{digits(10)}", f"{digits(8)}", f"ORD{digits(9)}"])


def vehicle():
    return f"{R.choice(['KA', 'DL', 'MH', 'TN', 'HR', 'UP', 'TS', 'GJ', 'WB'])}{R.randint(1, 50):02d}{R.choice(['AB', 'CD', 'MN', 'X', 'EP', 'Q'])}{R.randint(1000, 9999)}"


def ref():
    return digits(12)


def link(host, n=6):
    return f"https://{host}/" + "".join(R.choice("abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789") for _ in range(n))


FILLERS = {
    "name": name, "NAME": upper_name, "amt": amt, "amt_s": lambda: amt(20, 2000), "amt_l": lambda: amt(10000, 300000), "bal": lambda: amt(500, 400000, True),
    "lim": lambda: amt(10000, 500000, True), "date": date, "time": time, "vpa": vpa, "phone": phone, "otp": otp, "otp4": lambda: otp(4), "pnr": pnr,
    "awb": awb, "oid": oid, "veh": vehicle, "ref": ref, "l4": lambda: digits(4), "l3": lambda: digits(3), "l2": lambda: digits(2), "l5": lambda: digits(5),
    "merchant": lambda: R.choice(MERCHANTS), "shop": lambda: R.choice(SHOPS), "item": lambda: R.choice(ITEMS), "rest": lambda: R.choice(RESTAURANTS),
    "first": lambda: R.choice(FIRST), "min": lambda: str(R.choice([3, 5, 10, 15, 30])), "n": lambda: str(R.randint(2, 4)),
    "from": lambda: R.choice(CITIES)[0], "to": lambda: R.choice(CITIES)[0], "city": lambda: R.choice(CITIES)[1], "stn": lambda: R.choice(STATIONS), "stn2": lambda: R.choice(STATIONS),
    "flight": lambda: str(R.randint(100, 9999)), "pct": lambda: str(R.choice([10, 20, 25, 30, 40, 50, 60, 70, 80])), "gb": lambda: str(R.choice([1, 1.5, 2, 3])),
    "hash": lambda: "".join(R.choice("abcdefghijklmnopqrstuvwxyzABCDEFGHIJ0123456789+/") for _ in range(11)), "trn": lambda: str(R.randint(12001, 22999)),
    "ca": lambda: digits(R.choice([9, 10, 12])), "date2": date, "city2": lambda: R.choice(CITIES)[1], "first2": lambda: R.choice(LAST), "pct2": lambda: str(R.choice(["10.5", "10.99", "11.25", "8.5"])),
}


def fill(template):
    cache = {}
    out, i = [], 0
    while i < len(template):
        if template[i] == "{":
            j = template.index("}", i)
            key = template[i + 1:j]
            base = key.rstrip("0123456789") if key.rstrip("0123456789") in FILLERS else key
            if key not in cache:
                if key.startswith("link:"):
                    cache[key] = link(key[5:])
                else:
                    cache[key] = FILLERS[base]()
            out.append(cache[key])
            i = j + 1
        else:
            out.append(template[i])
            i += 1
    return "".join(out)


ROWS = []
COPIES = 2


def add(want, senders, texts, copies=2):
    """[senders]: header codes ("HDFCBK"), "CODE-S"-style with suffix, "+" for an unknown number, "c" for a contact."""
    for t in texts:
        # Every template appears several times, each with its own values and sender header.
        for _ in range(copies * COPIES):
            s = R.choice(senders)
            if s == "+":
                sender = "+91" + phone()
            elif s == "c":
                sender = "c:+91" + phone()
            elif s.startswith("="):
                sender = s[1:]
            elif "-" in s:
                sender = f"{R.choice(OPS)}-{s}"
            else:
                sender = f"{R.choice(OPS)}-{s}-{R.choice(['S', 'S', 'T'])}"
            ROWS.append((sender, want, fill(t)))


# ---------------------------------------------------------------------------------------------
# Transactions: money moved, or is owed
# ---------------------------------------------------------------------------------------------
add("T", ["HDFCBK"], [
    "Sent Rs.{amt}\nFrom HDFC Bank A/C *{l4}\nTo {NAME}\nOn {date}\nRef {ref}\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808",
    "Rs.{amt} debited from a/c **{l4} on {date} to VPA {vpa}(UPI Ref No {ref}). Not you? Call on 18002586161 to report",
    "Update! INR {amt} debited from HDFC Bank XX{l4} on {date}. Info: UPI-{merchant}. Avl bal:INR {bal}",
    "Spent Rs.{amt} On HDFC Bank Card {l4} At {merchant} On {date}:{time} Not You? To Block+Reissue Call 18002323232/SMS BLOCK CC {l4} to 7308080808",
    "Credit Alert!\nRs.{amt} credited to HDFC Bank A/c XX{l4} on {date} from VPA {vpa} (UPI {ref})",
    "Money Received - INR {amt} in HDFC Bank A/c xx{l4} on {date} by A/c linked to mobile no xx{l3} (IMPS Ref No. {ref}) Avl bal: INR {bal}",
    "Your HDFC Bank Credit Card ending {l4} has been charged Rs {amt} for your Standing Instruction at NETFLIX on {date}.",
    "Dear Customer, Rs.{amt} has been debited from account **{l4} towards EMI of loan ending {l4} on {date}. -HDFC Bank",
    "Payment of Rs. {amt} received towards your HDFC Bank Credit Card ending {l4} on {date}. Avl limit: Rs {lim}",
    "UPDATE: Available Bal in HDFC Bank A/c XX{l4} as on {date} is INR {bal}. Cheque clearing subject to realisation.",
], 3)
add("T", ["ICICIB", "ICICIT"], [
    "ICICI Bank Acct XX{l3} debited for Rs {amt} on {date}; {NAME} credited. UPI:{ref}. Call 18002662 for dispute. SMS BLOCK {l3} to 9215676766.",
    "Dear Customer, Acct XX{l3} is credited with Rs {amt} on {date} from {NAME}. UPI:{ref}-ICICI Bank.",
    "INR {amt} spent using ICICI Bank Card XX{l4} on {date} on {merchant}. Avl Limit: INR {lim}. If not you, call 1800 2662/SMS BLOCK {l4} to 9215676766.",
    "Rs {amt} spent on ICICI Bank Card XX{l4} on {date} at {merchant}. Avl Lmt: Rs {lim}. To dispute, call 18002662/SMS BLOCK {l4} to 9215676766. To convert this txn to EMI give a missed call on 9924667667. Know more about EMI conversion at {link:icici.co/ICICIT/a}",
    "ICICI Bank Account XX{l3} credited:Rs. {amt} on {date}. Info NEFT-{NAME}. Available Balance is Rs. {bal}.",
    "Payment of Rs {amt} has been received on your ICICI Bank Credit Card Account 4xxx{l4} on {date}.Thank you.",
    "Your ICICI Bank Credit Card XX{l4} statement has been sent to your email. Total Amount due: INR {amt}. Minimum Amount due: INR {amt_s}. Due Date: {date}.",
], 3)
add("T", ["SBIUPI", "SBIINB", "SBIPSG", "ATMSBI", "CBSSBI"], [
    "Dear UPI user A/C X{l4} debited by {amt} on date {date} trf to {NAME} Refno {ref}. If not u? call 1800111109. -SBI",
    "Dear SBI User, your A/c X{l4}-credited by Rs.{amt} on {date} transfer from {NAME} Ref No {ref} -SBI",
    "Your A/C XXXXX{l5} Credited INR {amt} on {date} -Deposit by transfer from {NAME}. Avl Bal INR {bal}-SBI",
    "Dear Customer, Rs.{amt} withdrawn at SBI ATM S1NW{l4} from A/c XX{l4} on {date}. Avl Bal Rs.{bal}. Not you? Call 1800111109 -SBI",
    "Your a/c no. XXXXXXXX{l4} is debited for Rs.{amt} on {date} and a/c XXXXXX{l4} credited (IMPS Ref no {ref}). -SBI",
    "Dear Customer, your A/c XXXXX{l4} has been debited with INR {amt} on {date} towards Annual Debit Card Maintenance Charges. -SBI",
], 3)
add("T", ["AXISBK"], [
    "INR {amt} debited\nA/c no. XX{l4}\n{date}, {time}\nUPI/P2A/{ref}/{NAME}\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank",
    "Spent\nCard no. XX{l4}\nINR {amt}\n{date} {time}\n{merchant}\nAvl Lmt INR {lim}\nSMS BLOCK {l4} to 919951860002, if not you - Axis Bank",
    "INR {amt} credited\nA/c no. XX{l4}\n{date}, {time}\nUPI/P2A/{ref}/{NAME}\nAxis Bank",
    "Payment of INR {amt} received for Axis Bank Credit Card no. XX{l4} on {date}. Available limit: INR {lim}.",
], 3)
add("T", ["KOTAKB"], [
    "Sent Rs.{amt} from Kotak Bank AC X{l4} to {vpa} on {date}.UPI Ref {ref}. Not you, https://kotak.com/KBANKT/Fraud",
    "Received Rs.{amt} in your Kotak Bank AC X{l4} from {vpa} on {date}.UPI Ref:{ref}.",
    "Rs.{amt} debited from Kotak Bank Debit Card XX{l4} at {merchant} on {date}. Avl bal Rs.{bal}. Not you? Call 18602662666",
], 3)
add("T", ["IDFCFB", "YESBNK", "INDUSB", "PNBSMS", "BOBTXN", "CANBNK", "UNIONB", "FEDBNK", "AUBANK", "RBLBNK", "BANDHN", "IOBCHN", "CENTBK", "IDBIBK"], [
    "Your A/C XXXXXXX{l4} has been debited by INR {amt} on {date} towards UPI-{ref}. New bal: INR {bal}. If not done by you, call 18004194332 -IDFC FIRST Bank",
    "INR {amt} debited from A/c XX{l4} on {date}. Info: UPI/{ref}/{merchant}. Avl Bal: INR {bal}. Not you? Report to your bank.",
    "A/c XX{l4} Credited for Rs.{amt} on {date} by NEFT {NAME} SALARY. Avl Bal Rs.{bal}",
    "Dear Customer, your A/c XX{l4} is debited with Rs.{amt} on {date} for ATM withdrawal. Avl Bal Rs.{bal}",
    "Your account XX{l4} has been credited with interest of Rs.{amt_s} on {date}. Balance Rs.{bal}",
    "Rs.{amt} transferred from A/c ...{l4} to:IMPS/{ref}. Total Bal:Rs.{bal}CR. Avlbl Amt:Rs.{bal}. Not you? Call 18001802222",
    "Your Fixed Deposit no XX{l4} of Rs.{amt_l} has matured and the proceeds have been credited to A/c XX{l4} on {date}.",
    "ECS/NACH debit of Rs.{amt} towards LIC PREMIUM from A/c XX{l4} on {date} is successful.",
    "Cheque no {l5} for Rs.{amt} presented in A/c XX{l4} has been returned unpaid on {date}. Reason: Insufficient funds. Charges Rs 590 levied.",
], 2)
add("T", ["SBICRD", "SBICRD-S", "AMEXIN", "ONECRD", "CITIBK", "SCBANK", "HSBCIN", "AUBANK", "SLICEIT", "UNIBNK"], [
    "Rs.{amt} spent on your SBI Credit Card ending {l4} at {merchant} on {date}. Trxn. not done by you? Report at https://sbicard.com/Dispute",
    "Alert: You've spent INR {amt} on your AMEX card ** {l5} at {merchant} on {date} at {time} IST. Call 18004190691 if this was not made by you.",
    "Rs. {amt} spent on your OneCard ending {l4} at {merchant} on {date}. Avl limit Rs. {lim}.",
    "We have received payment of Rs.{amt} towards your Credit Card ending {l4} on {date}. Thank you.",
    "Transaction of INR {amt} on your card ending {l4} at {merchant} was declined due to insufficient limit.",
    "Your slice payment of Rs {amt} to {merchant} was successful. UPI Ref {ref}",
    "Reminder: Total amount due Rs {amt} on your Credit Card ending {l4} is due on {date}. Please pay to avoid late fee.",
], 2)
add("T", ["PAYTMB", "PYTMBK", "iPaytm"], [
    "Paid Rs.{amt_s} to {merchant} from Paytm Balance. Updated Balance: Rs.{bal}. Txn ID: {ref}",
    "Rs.{amt_s} received from {NAME} in your Paytm Wallet. Updated Balance: Rs {bal}",
    "Rs.{amt} sent to {vpa} from Paytm Payments Bank a/c XX{l4}. UPI Ref: {ref}",
], 3)
add("T", ["AMAZON", "AMZPAY", "AMZNIN", "=AMAZON", "APAYIN"], [
    "Payment of Rs {amt_s} using Amazon Pay Balance is successful at {shop}. Updated Balance: Rs {bal}. For help/stmt: https://www.amazon.in/cstxn",
    "Payment of Rs 167.05 using Amazon Pay Balance is successful at MagicPin. Updated Balance: Rs 0.00. For help/stmt: https://www.amazon.in/cstxn",
    "Rs {amt_s} has been added to your Amazon Pay balance. Updated balance: Rs {bal}.",
    "Refund of Rs {amt_s} for your order {oid} has been credited to your Amazon Pay balance.",
    "Your Amazon Pay Later bill of Rs {amt} is due on {date}. Pay now to avoid late fees: https://www.amazon.in/apl",
], 2)
add("T", ["PHONPE", "PHONEPE", "MOBIKW", "CREDIN", "CREDCL", "LZYPAY", "SIMPLP", "FRECHG", "BHRTPE", "GPAYIN"], [
    "Rs {amt_s} paid to {merchant} via PhonePe. UPI Ref No {ref}.",
    "Your payment of Rs {amt} towards HDFC Bank Credit Card ending {l4} is successful. Bill paid via CRED.",
    "Payment of Rs {amt_s} to {shop} via LazyPay is successful. Your LazyPay due is Rs {amt_s}.",
    "Rs.{amt_s} debited from your MobiKwik wallet for {merchant}. Balance Rs {bal}",
    "Your Simpl bill of Rs {amt} is due on {date}. Pay on the Simpl app to keep using Simpl.",
    "Recharge of Rs {amt_s} for {phone} is successful. Txn ID {ref}. Thank you for using PhonePe.",
], 2)
add("T", ["NETCFT", "IDFCFT", "ICICFT", "PAYTMF", "AXISFT", "SBIFTG", "IHMCLF"], [
    "Rs.{amt_s} deducted from your FASTag wallet for {city} Toll Plaza on {date}. Vehicle {veh}. Avl bal Rs.{bal}",
    "Your FASTag for vehicle {veh} has been recharged with Rs.{amt} on {date}. Available balance Rs.{bal}.",
    "Toll of Rs {amt_s} paid at {city} Plaza for {veh}. FASTag balance Rs {bal}. Low balance? Recharge to avoid double toll.",
], 2)
add("T", ["AIRTEL", "JIOINF", "VICARE", "BSNLIN", "ACTFIB", "AIRTLE"], [
    "Your Airtel bill of Rs {amt_s} for {phone} is due on {date}. Pay now on Airtel Thanks app to avoid late fee.",
    "Recharge of Rs {amt_s} successful for Jio number {phone}. Plan valid till {date}.",
    "Payment of Rs {amt_s} for your ACT Fibernet account {ca} has been received. Thank you.",
    "Dear Customer, bill of Rs {amt_s} for your Vi postpaid number {phone} has been generated. Due date {date}.",
], 2)
add("T", ["BESCOM", "TPDDLD", "ADANIE", "MSEDCL", "BSESRP", "TATAPW", "IGLGAS", "MAHGAS", "CESCLT", "TNEBLD", "UHBVNL"], [
    "Dear Consumer, your electricity bill for CA no {ca} of Rs {amt_s} is due on {date}. Pay online to avoid disconnection.",
    "Payment of Rs {amt_s} received for consumer no {ca} on {date}. Thank you. -Tata Power",
    "Your IGL gas bill for BP no {ca} is Rs {amt_s}, due date {date}. Pay via IGL Connect app.",
    "Bill of Rs.{amt_s} generated for Consumer No.{ca}. Due date {date}. Pay at https://wss.mahadiscom.in",
], 2)
add("T", ["LICIND", "HDFCLI", "ICICIP", "SBILIF", "MAXLIF", "TATAAI", "BAJAJA", "STARHL"], [
    "Premium of Rs {amt} for policy no {ca} is due on {date}. Pay online at licindia.in to keep your policy in force.",
    "We have received your premium payment of Rs.{amt} for policy {ca}. Thank you. -HDFC Life",
    "Renewal premium of Rs {amt} for your health policy {ca} is due on {date}.",
], 2)
add("T", ["BAJAJF", "HDBFSL", "TATACP", "HOMECR", "MUTHOT", "LICHFL", "NAVIFN", "KREDTB"], [
    "Your EMI of Rs {amt} for loan a/c {ca} is due on {date}. Please keep sufficient balance in your bank account.",
    "EMI of Rs {amt} for loan {ca} has been received on {date}. Thank you. -Bajaj Finance",
    "Your EMI of Rs {amt} for loan account {ca} has bounced. Bounce charges of Rs 500 levied. Pay now to avoid further charges.",
], 2)
add("T", ["ZERODH", "GROWWI", "UPSTOX", "ANGELB", "CDSLEV", "NSDLCS", "CAMSPL", "KFINTK", "NSEIND", "BSELTD"], [
    "Funds of Rs.{amt} added to your Zerodha account successfully.",
    "Your SIP of Rs.{amt_s} in Parag Parikh Flexi Cap Fund has been processed. Units allotted: {l2}.{l3} at NAV {l3}.{l2}.",
    "Debit in a/c *{l4} for {l2}-INFY-EQ on {date}. If not initiated by you, contact your broker. -CDSL",
    "Your order to BUY {l2} shares of TCS has been executed at Rs {amt}. Order no {ref}.",
    "Withdrawal of Rs.{amt} from your Groww balance has been processed to bank a/c XX{l4}.",
], 2)
# PF money arriving: a transaction, or a passbook notice.
add("T|U", ["EPFOHO", "EPFOMS"], [
    "Dear {NAME}, your passbook balance against GRBNG00{l5}000 is Rs. {amt_l}/-. Contribution of Rs. {amt_s}/- for due month 102026 has been received.",
], 3)
add("T", ["ZOMATO", "SWIGGY", "UBERIN", "OLACAB", "RAPIDO", "BLINKT", "ZEPTON", "MYNTRA", "FLPKRT"], [
    "Refund of Rs {amt_s} for your order {oid} has been processed to your original payment method. It will reflect in 5-7 working days.",
    "Rs {amt_s} has been credited to your Zomato Money balance as refund for order {oid}.",
    "Payment of Rs {amt_s} received for your Uber trip on {date}. Thank you for riding with Uber.",
], 2)

# ---------------------------------------------------------------------------------------------
# OTP: carries a code to type in
# ---------------------------------------------------------------------------------------------
add("O", ["HDFCBK", "ICICIT", "SBIOTP", "AXISBK", "KOTAKB", "IDFCFB", "YESBNK", "SBICRD", "AMEXIN"], [
    "OTP is {otp} for txn of INR {amt} at {merchant} on HDFC Bank card ending {l4}. Valid till {time}. Do not share OTP for security reasons",
    "{otp} is OTP for txn of INR {amt} at {merchant} on ICICI Bank card XX{l4}. OTPs are SECRET. DO NOT disclose it to anyone. Bank NEVER asks for OTP.",
    "OTP for online purchase of Rs. {amt} at {merchant} thru State Bank Debit Card ending {l4} is {otp}. Do not share this with anyone.",
    "{otp} is your OTP to login to Axis Mobile. Do not share it with anyone. Axis Bank never asks for OTP.",
    "Your OTP for Kotak NetBanking login is {otp}. Valid for 5 mins. Never share your OTP with anyone.",
    "Dear Customer, {otp} is the OTP for adding beneficiary {NAME} to your account. Do not share it with anyone including bank staff.",
    "{otp} is your One Time Password for resetting your NetBanking password. Valid for 10 minutes.",
], 2)
add("O", ["AMAZON", "FLPKRT", "MYNTRA", "SWIGGY", "ZOMATO", "PAYTMB", "PHONPE", "GPAYIN", "UBERIN", "OLACAB", "NYKAAA", "MEESHO", "BLINKT", "ZEPTON", "IRCTCI", "MMTRIP", "JIOINF", "AIRTEL", "NETFLX", "HOTSTR", "ZERODH", "GROWWI", "CREDIN", "DREAM1", "LNKDIN"], [
    "{otp} is your Amazon OTP. Do not share it with anyone.",
    "Your Flipkart OTP is {otp}. Please do not share it with anyone.",
    "{otp} is your OTP for Swiggy login. Valid for 10 minutes. Do not share. {hash}",
    "<#> {otp} is your OTP to log in to Zomato. Do not share it with anyone. {hash}",
    "Use OTP {otp} to log in to your PhonePe account. Never share your OTP with anyone.",
    "Your verification code is {otp}. It expires in {min} minutes.",
    "{otp} is your verification code for Myntra. Don't share it with anyone.",
    "Use {otp} as your login OTP. OTP is confidential and valid for 10 mins. Please do not share it with anyone.",
    "Your IRCTC registration OTP is {otp}. Do not share it with anyone.",
    "OTP for your MakeMyTrip login is {otp}. Valid for 15 minutes.",
    "{otp} is the OTP to verify your mobile number on Jio. Valid for 10 mins.",
    "Your Zerodha Kite login OTP is {otp}. Do not share this with anyone.",
    "Your OTP for Groww is {otp}. Do not share it with anyone. -Groww",
    "Dear user, {otp} is your one time password for Dream11. Valid for 10 min.",
    "{otp} is your Uber code. Never share this code.",
], 1)
add("O", ["WHATSP", "GOOGLE", "=Google", "INSTAG", "FACEBK", "TELEGR", "=57575", "=Telegram", "APPLEI", "MSFTIN", "SNAPCH", "TRUECL"], [
    "Your WhatsApp code: {l3}-{l3}\nDon't share this code with others",
    "G-{otp} is your Google verification code.",
    "{l3} {l3} is your Instagram code. Don't share it.",
    "{otp} is your Facebook confirmation code",
    "Telegram code: {l5}\n\nYou can also tap on this link to log in:\nhttps://t.me/login/{l5}",
    "Your Apple ID Code is: {otp}. Do not share it with anyone.",
    "Use {otp} as Microsoft account security code",
    "Snapchat code: {otp}. Happy Snapping!",
    "{otp} is your Truecaller verification code",
], 2)
add("O", ["UIDAIG-G", "ITDEFL-G", "EPFOHO-G", "DIGILK-G", "NSDLPN", "UTIITS", "CDSLOT", "NSEOTP", "MPRVHN-G", "UMANGG-G"], [
    "OTP for Aadhaar (XX{l4}) is {otp} (valid for 10 mins). To update Aadhaar, upload documents on myAadhaar portal. -UIDAI",
    "{otp} is the OTP for e-filing login for PAN XXXXX{l4}X. Valid for 15 minutes. -Income Tax Department",
    "{otp} is your OTP for UAN activation. Do not share it with anyone. -EPFO",
    "{otp} is your OTP to access DigiLocker. Valid for 10 minutes. Do not share.",
    "OTP for PAN application acknowledgement {ref} is {otp}. -Protean",
    "{otp} is the OTP for your pledge request in demat a/c *{l4}. Do not share. -CDSL",
    "Your OTP for driving licence application is {otp}. -Parivahan",
], 2)
add("O", ["SWIGGY", "ZOMATO", "AMAZON", "DELHVY", "EKARTL", "SHADOW", "OLACAB", "UBERIN", "RAPIDO", "INDANE", "HPGASL", "BHRTGS", "MYGATE"], [
    "Share OTP {otp4} with the delivery partner to receive your order. Do not share it before delivery.",
    "Your delivery OTP for order {oid} is {otp4}. Share it with the Ekart wishmaster only at the time of delivery.",
    "DAC for your Indane LPG cylinder delivery is {otp4}. Share it with the delivery person only on receiving the cylinder.",
    "{otp4} is your MyGate OTP to approve your guest entry.",
], 1)
add("V|O", ["OLACAB", "RAPIDO", "UBERIN"], [
    "Your Ola ride OTP is {otp4}. Share it with your driver {first} to start the ride.",
    "Your PIN for Rapido ride is {otp4}. Captain {first} is on the way.",
], 1)
add("O", ["HDFCBK", "SBIOTP", "PNBSMS"], [
    "आपका ओटीपी {otp} है। इसे किसी के साथ साझा न करें। -SBI",
    "{otp} आपका सत्यापन कोड है। यह 10 मिनट के लिए मान्य है।",
], 2)

# ---------------------------------------------------------------------------------------------
# Shopping: an order's journey
# ---------------------------------------------------------------------------------------------
add("S", ["AMAZON", "AMZNIN", "=AMAZON"], [
    "Ordered: '{item}...' and {n} more item(s). Arriving by tomorrow. Track: {link:amzn.in/d}",
    "Shipped: '{item}...' will be delivered on {date}. Track your package: {link:amzn.in/d}",
    "Delivered: '{item}...' Your package was delivered. Rate your experience: {link:amzn.in/r}",
    "Arriving today: '{item}' will be delivered by 9 PM. Track at {link:amzn.in/d}",
    "Your Amazon order {oid} has been cancelled. Any amount paid will be refunded within 2-4 business days.",
    "Your return request for '{item}' has been approved. Pickup scheduled for {date}.",
], 2)
add("S", ["FLPKRT", "FKRTIT", "MYNTRA", "MEESHO", "NYKAAA", "AJIOIN", "TATACL", "CROMAR", "PURPLE", "FRSTCY", "LNSKRT", "DECTHL", "JIOMRT", "SNAPDL", "PEPRFY"], [
    "Your order for {item} has been shipped and will be delivered by {date}. Track: {link:fkrt.it}",
    "Delivered: {item} from your order {oid} was delivered successfully. Rate it at {link:fkrt.it}",
    "Your Myntra order {oid} has been packed and will be shipped soon. Track it on the Myntra app.",
    "Your Meesho order {oid} is out for delivery today. Please keep Rs {amt_s} ready for Cash on Delivery.",
    "Thank you for shopping with Nykaa! Your order {oid} has been confirmed. We'll notify you when it ships.",
    "Your AJIO order {oid} has been dispatched via Delhivery AWB {awb}.",
    "Hi {first}, your order {oid} is confirmed and will reach you by {date}. -Tata CLiQ",
    "Your replacement request for order {oid} has been received. Pickup will be scheduled within 48 hours.",
    "Order {oid} cancelled as requested. Refund of Rs {amt_s} will be processed in 5-7 days.",
], 1)
add("S", ["SWIGGY", "ZOMATO", "BLINKT", "ZEPTON", "BIGBSK", "INSTMT", "DUNZOO", "DOMINO", "MCDIND", "EATSRE"], [
    "Your order from {rest} is on its way! {first} will deliver it in {min} mins.",
    "Your Zomato order from {rest} has been delivered. Enjoy your meal!",
    "Your Blinkit order {oid} has been packed. Delivery partner is on the way.",
    "Order confirmed! Your Zepto order will arrive in 10 minutes.",
    "Your bigbasket order {oid} will be delivered between 7 AM - 9 AM tomorrow.",
    "{rest} is preparing your order. Track it live on the Swiggy app.",
    "Your Domino's order {oid} is out for delivery. Expected by {time}.",
], 1)
add("S|U", ["DELHVY", "BLUDRT", "EKARTL", "SHADOW", "XPRESB", "DTDCIN", "ECOMEX", "INPOST", "INDPST-G", "SHPRKT", "AMZLOG"], [
    "Your shipment {awb} from {shop} is out for delivery today. Track: {link:dlvry.in}",
    "Shipment AWB {awb} has been delivered on {date}. Thank you for choosing Blue Dart.",
    "Your Ekart shipment {awb} will be delivered today. Track: {link:ekrt.in}",
    "Dear customer, your parcel {awb} is in transit and will reach you by {date}.",
    "Article {awb}IN booked on {date} is out for delivery. -India Post",
    "We attempted to deliver your shipment {awb} today but could not reach you. We will try again tomorrow.",
    "Your DTDC consignment {awb} has been picked up and is on its way.",
], 1)

# ---------------------------------------------------------------------------------------------
# Travel
# ---------------------------------------------------------------------------------------------
add("V", ["IRCTCI", "IRCTCS", "RAILWY", "NTESIN"], [
    "PNR:{pnr},TRN:{trn},DOJ:{date},SL,{stn}-{stn2},Dep:{time},\n{NAME}+{n},S5 32,S5 33,\nFare:{amt_s},SC:17.7+PG CHGS. -IRCTC",
    "PNR {pnr} chart prepared. Coach B2 Berth 34, B2 35. Train {trn} departs {stn} at {time}. Happy journey!",
    "Train {trn} is running late by 45 min. Expected departure from {stn} at {time}. -NTES",
    "Your ticket for PNR {pnr} has been cancelled. Refund of Rs {amt_s} will be credited to your account in 3-4 days.",
], 2)
add("V", ["INDIGO", "AIRIND", "AKASAA", "SPJETT", "AIXCNT", "VSTARA"], [
    "Your IndiGo flight 6E {flight} from {from} to {to} on {date} departs at {time}. Web check-in is now open: {link:goindigo.in/c}",
    "Boarding pass issued. Flight AI {flight} {from}-{to}, Seat 14C, Gate 22. Boarding at {time}.",
    "Flight QP {flight} on {date} is delayed. New departure time {time}. We regret the inconvenience. -Akasa Air",
    "Your booking PNR {otp} is confirmed. {from} to {to} on {date}. Manage booking at airindia.com",
], 2)
add("V", ["MMTRIP", "GOIBIB", "CLRTRP", "IXIGOO", "REDBUS", "YATRAA", "OYOROM", "ABHIBS", "EMTRIP"], [
    "Your hotel booking at Treebo {city} is confirmed for {date}. Booking ID NH{ref}.",
    "Your redBus ticket {ref} from {city} to {city2} on {date} is confirmed. Boarding point: Majestic, {time}.",
    "Your flight booking {from}-{to} on {date} is confirmed. Booking ID {ref}. View e-ticket on the MakeMyTrip app.",
    "Check-in reminder: your OYO stay in {city} starts tomorrow. Show booking ID {ref} at the front desk.",
], 2)
add("V|O", ["UBERIN", "OLACAB", "RAPIDO", "NAMMAY", "BLUSMT", "MERUCB"], [
    "Your Uber is arriving. {first} in a white Swift Dzire {veh} is {min} mins away.",
    "Your Ola cab {veh} (Indica) driver {first} {phone} is on the way. OTP {otp4}.",
    "Captain {first} is arriving in Bike {veh}. Share OTP {otp4} to start the ride.",
    "Your ride has started. Track your trip and share it with family: {link:m.uber.com/t}",
    "Your BluSmart ride is scheduled for {time} tomorrow from {city} Airport Terminal 1.",
], 1)
add("V", ["MOVEIN", "ROUTMT", "SHUTTL"], [
    "Your pickup is scheduled at {time} from Prestige Tech Park. Vehicle no {veh}, driver {first}.",
    "Your Shuttl bus will arrive at Huda City Centre in 5 mins. Bus {veh}.",
], 2)

# ---------------------------------------------------------------------------------------------
# Updates: notices with nothing to pay or type in
# ---------------------------------------------------------------------------------------------
add("U", ["HDFCBK", "ICICIB", "SBIINB", "AXISBK", "KOTAKB", "IDFCFB", "YESBNK", "PNBSMS", "CANBNK", "BOBTXN", "INDUSB"], [
    "You logged in to HDFC Bank NetBanking on {date} at {time}. Not you? Call 18002586161.",
    "Your new Debit Card ending {l4} has been dispatched via Blue Dart AWB {awb}. -ICICI Bank",
    "Your mobile number has been successfully updated in your account XX{l4} on {date}. If not done by you, call 1800111109.",
    "Your NetBanking password was changed on {date}. If you did not do this, call us immediately.",
    "Your cheque book request for A/c XX{l4} has been processed and will be delivered in 7 days.",
    "Dear Customer, your KYC has been updated successfully. Thank you for banking with us.",
    "Your UPI ID {vpa} has been successfully linked to A/c XX{l4}.",
    "SBI never asks for your card details, PIN, OTP or password over call, SMS or email. Beware of fraudsters. Report cyber fraud at 1930.",
    "Your e-statement for the period ending {date} has been sent to your registered email ID.",
    "Scheduled maintenance: NetBanking will be unavailable on {date} from 00:30 to 04:30 hrs.",
    "Your Debit Card ending {l4} has been blocked as requested. Apply for a new card on the mobile app.",
    "Nominee details for your account XX{l4} have been updated successfully.",
], 1)
add("U", ["UIDAIG-G", "ITDEFL-G", "CBDTIN-G", "EPFOHO-G", "DIGILK-G", "MPRVHN-G", "ECISMS-G", "GOVTIN-G", "CBSE-G", "MYGOVT-G", "NHAIIN-G", "PIBIND-G"], [
    "Your Aadhaar XX{l4} has been updated successfully. Download the updated e-Aadhaar from myaadhaar.uidai.gov.in -UIDAI",
    "Your Income Tax Return for AY 2026-27 has been processed. Acknowledgement no {ref}. -ITD",
    "Your PAN is linked with Aadhaar. -Income Tax Department",
    "Your claim with ref no {ref} has been settled. -EPFO",
    "Your Driving Licence DL-{l4}{l5}{l4} has been dispatched by speed post. -Parivahan",
    "A document has been issued to your DigiLocker: Class X Marksheet.",
    "Your Voter ID application ref {ref} has been approved. e-EPIC can be downloaded from voters.eci.gov.in",
    "Cyclone alert: heavy rain expected in your area on {date}. Stay indoors and follow official advisories. -NDMA",
], 1)
add("U|F", ["JIOINF", "AIRTEL", "VICARE", "BSNLIN"], [
    "You have used 90% of your daily high speed data. Speed will reduce to 64 Kbps after 100%.",
    "Your Jio plan on {phone} expires on {date}. Recharge to continue enjoying unlimited calls and data.",
    "Dear customer, your Airtel number {phone} will be eligible for the 5G network in your area.",
], 2)
add("U", ["APOLLO", "PRACTO", "THYRCR", "LALPTH", "MAXHSP", "FORTIS", "1MGLAB", "CULTFT", "MFINE", "NARAYN"], [
    "Your appointment with Dr. {first} {first2} at Apollo Hospitals is confirmed for {date} at {time}.",
    "Your lab report for booking {ref} is ready. Download it from the Thyrocare app.",
    "Reminder: your appointment with Dr. {first} is tomorrow at {time}. Reply C to cancel.",
    "Your sample has been collected and will be processed. Reports will be available in 24 hours. -Dr Lal PathLabs",
    "Your cult.fit class Yoga is booked for {date} at {time} at HSR Layout centre.",
], 2)
add("U", ["NAUKRI", "LNKDIN", "UNACAD", "BYJUSS", "VEDANT", "CBSEIN", "UPSCGV-G", "AMITYU", "IGNOUU"], [
    "A recruiter from Infosys viewed your profile on Naukri today.",
    "Your application for Software Engineer at TCS has been received. We will get back to you soon.",
    "Your class 'Polity Live' starts at {time} today. Join from the Unacademy app.",
    "Your admit card for the exam on {date} is available for download on the official website.",
], 2)
add("U", ["MYGATE", "NOBROK", "APNACM", "ADDA247", "ZOMATO", "SWIGGY", "OLACAB"], [
    "{first} from Amazon is at the gate. Approve entry in the MyGate app.",
    "Your society maintenance receipt for {date} is available on ApnaComplex.",
    "Your complaint {ref} has been registered. Our team will resolve it within 48 hours.",
    "Your support ticket {ref} has been resolved. Reply REOPEN if you need more help.",
], 1)
add("U", ["INDANE", "HPGASL", "BHRTGS", "TATAPW", "BESCOM", "BSESYM"], [
    "Your Indane refill booking no {ref} for consumer {ca} has been registered on {date}.",
    "Power supply will be interrupted in your area on {date} from 10 AM to 2 PM for maintenance. -BESCOM",
], 2)
add("S|U", ["INDANE", "HPGASL", "BHRTGS"], [
    "Your LPG cylinder has been delivered on {date}. Cash memo no {ref}.",
], 2)
add("U", ["NETFLX", "HOTSTR", "PRIMEV", "SPOTIF", "YTPREM", "SONYLV"], [
    "Your Netflix membership has been cancelled. You can still watch until {date}.",
    "Your Disney+ Hotstar subscription has been renewed. Enjoy streaming!",
    "Your Spotify Premium plan will renew on {date}.",
], 2)

# ---------------------------------------------------------------------------------------------
# Offers: selling something, never spam
# ---------------------------------------------------------------------------------------------
add("F", ["HDFCBK-P", "ICICIB-P", "AXISBK-P", "KOTAKB-P", "SBICRD-P", "BAJAJF-P", "TATACP-P", "IDFCFB-P", "AUBANK-P", "YESBNK-P"], [
    "Dear Customer, you are pre-approved for a Personal Loan of up to Rs {amt_l} at {pct2}% p.a. Apply now: {link:hdfcbk.io/HDFCBK/s} T&C",
    "Get an Axis Bank Credit Card with zero joining fee and 5% cashback on bill payments. Apply now: {link:axisbk.in/cc}",
    "Congratulations! Your ICICI Bank Credit Card is eligible for a credit limit upgrade. Click to know more. T&C apply",
    "Instant Personal Loan up to Rs 5 Lakh in 10 mins. No documents needed. Apply on Kotak811 app. T&C",
    "Your loan of Rs {amt_l} is approved! Get money in your account in 2 minutes. Click {link:bjfin.in/pl} T&C apply",
], 2)
add("F", ["HDFCBK", "ICICIB", "AXISBK", "SBICRD", "KOTAKB"], [
    "Your HDFC Bank A/c ending xx{l4} is eligible for a Lifetime FREE RuPay Credit Card. Check: {link:1.hdfc.bank.in/HDFCBK/s} T&C",
    "Enjoy {pct}% off on Swiggy with your ICICI Bank Credit Card this weekend. Use code ICICI{l2}. T&C apply.",
    "Convert your purchases into easy EMIs at low interest. Know more: {link:axisbk.in/emi}",
    "Your reward points are waiting! Redeem them for vouchers before they expire. Visit {link:sbicard.com/rewards}",
], 2)
add("F", ["AMAZON-P", "FLPKRT-P", "MYNTRA-P", "MEESHO-P", "NYKAAA-P", "AJIOIN-P", "SWIGGY-P", "ZOMATO-P", "BLINKT-P", "ZEPTON-P", "BIGBSK-P", "DOMINO-P", "TATACL-P", "CROMAR-P", "LNSKRT-P"], [
    "The Great Indian Festival is LIVE! Up to {pct}% off on mobiles, electronics & more. Shop now: {link:amzn.to}",
    "Big Billion Days are here! Grab the best deals before they're gone. {link:fkrt.it}",
    "End of Reason Sale: flat {pct}% off on top brands. Shop now on Myntra!",
    "Sirf aaj! Har product Rs 99 mein. Abhi order karo! {link:meesho.com/s}",
    "Hungry? Get {pct}% off up to Rs 100 on your next order. Use code TRYNEW. Order now!",
    "Your cart misses you! Items in your cart are selling fast. Complete your order now.",
    "Buy 1 Get 1 Free on all medium pizzas today only! Order now: {link:dominos.co.in/o}",
    "Craving something sweet? Free delivery on orders above Rs 199 for the next 2 hours!",
], 2)
add("F", ["JIOINF-P", "AIRTEL-P", "VICARE-P", "BSNLIN-P", "JIOINF", "AIRTEL"], [
    "Get Unlimited 5G data with the Rs 349 plan. Recharge now on MyJio: {link:jio.com/r}",
    "Dhamaka offer! Get 2GB/day extra data on recharge of Rs 299. Recharge now.",
    "Upgrade to Airtel Black and get Netflix, Amazon Prime and Disney+ Hotstar at no extra cost. Know more: {link:i.airtel.in/b}",
    "धमाका ऑफ़र! ₹299 के रिचार्ज पर पाएं 2GB/दिन। अभी रिचार्ज करें।",
], 2)
add("F", ["LICIND-P", "POLBZR-P", "ACKOIN-P", "DIGITI-P", "HDFCLI-P", "MAXLIF-P", "TATAAI-P"], [
    "Secure your family's future with a term plan of Rs 1 Crore at just Rs 490/month. Get a quote now: {link:pbz.in/term}",
    "Your car insurance expires soon. Renew now and save up to {pct}%! {link:acko.com/r}",
], 2)
add("F", ["PRESTG-P", "GODREJ-P", "DLFLTD-P", "LODHAG-P", "SOBHAL-P", "HOUSNG-P", "MAGICB-P"], [
    "Book your dream 2 & 3 BHK homes in {city} starting at Rs 75 Lakh*. Site visit this weekend. Call {phone}",
    "Pre-launch offer! Luxury villas with zero brokerage. Limited units. Register now: {link:godrejproperties.com/l}",
], 2)
add("F", ["UNACAD-P", "BYJUSS-P", "UPGRAD-P", "SIMPLI-P", "PWLIVE-P", "COURSR-P"], [
    "Become a Data Scientist in 6 months with upGrad. Batch starts {date}. Enrol now: {link:upgrad.com/ds}",
    "Free live class on UPSC Prelims strategy tomorrow at 7 PM. Register now!",
], 2)
add("F", ["PVRCIN-P", "BKMYSH-P", "MAKEMT-P", "IXIGOO-P", "INDIGO-P", "REDBUS-P", "OYOROM-P", "GOIBIB-P"], [
    "Flat Rs 500 off on flights this weekend with MakeMyTrip. Use code MMTSUPER. Book now!",
    "Weekend getaway? Up to {pct}% off on hotels in Goa. Book on OYO now!",
    "Get 2 movie tickets at Rs 199 this Tuesday at PVR. Book now on BookMyShow.",
    "IndiGo sale! Fares starting at Rs 1,499 all-inclusive. Book now: {link:goindigo.in/s}",
], 2)
add("F|X", ["RUMMYC-P", "JUNGLE-P", "MPLGAM-P", "WINZOG-P", "DREAM1-P", "MY11CR-P"], [
    "Play Rummy and win real cash daily! Get Rs 2000 welcome bonus on first deposit. Download now: {link:rummycircle.com/d}",
    "IPL is here! Create your fantasy team and win up to Rs 1 Crore. Join now on Dream11!",
], 2)
add("F", ["MEESHO", "NYKAAA", "SWIGGY", "ZOMATO", "BIGBSK", "URBNCO", "ZEPTON"], [
    "Hi {first}, your favourite products are now on sale! Up to {pct}% off. Shop now on Nykaa.",
    "Get {pct}% off on your next order. Use code SAVE{pct} at checkout. Valid till {date}. T&C",
    "Your Urban Company coupon of Rs 150 expires today. Book a service now!",
], 2)

# ---------------------------------------------------------------------------------------------
# Personal: people
# ---------------------------------------------------------------------------------------------
PERSONAL = [
    "Hey, are you free for lunch tomorrow?", "Happy birthday! Have a great year ahead 🎉", "Bhai kal ka plan pakka? 8 baje milte hain",
    "Maine {amt_s} bhej diye hain GPay pe, check kar lena", "Mummy ko bol dena main late aaunga aaj", "Call me when you reach home",
    "Running 10 mins late, sorry!", "Did you get the money I transferred?", "Can you pick up milk on the way back?", "Where are you? We're waiting at the gate",
    "Ok done 👍", "Thanks a lot yaar, really appreciate it", "Kya kar raha hai? Free ho toh call kar", "Reached safely. Will call you tonight.",
    "Meeting moved to 4 PM, see you there", "Sending you the photos from the trip", "Bro check this out {link:youtu.be}", "Here's the location {link:maps.app.goo.gl}",
    "Join the call: {link:meet.google.com}", "Look at this dress on Myntra, should I buy it? {link:myntr.it}", "This phone is on sale on Amazon, check it {link:amzn.in/d}",
    "Pls send {amt_s} on GPay, will return tomorrow", "Paid the electricity bill, you can relax", "Sale at Lifestyle this weekend, let's go?",
    "OTP aaya kya? Amazon wala delivery boy wait kar raha hai",
    "Aaj dinner pe kya banau?", "Congrats on the new job!! Party kab de raha hai?", "Happy Diwali to you and your family 🪔", "Good morning ☀️",
    "School fees ki last date kal hai, yaad se bhar dena", "I've booked the cab, it'll be there by 7", "My new number is {phone}, save it",
    "Doctor ne kal subah 10 baje bulaya hai", "Train late hai 2 ghante, don't wait for dinner", "Can we reschedule our call to tomorrow?",
    "The parcel reached, thanks for sending it", "Bhaiya, gas cylinder book kar diya hai", "Kal office nahi aa raha, tabiyat theek nahi hai",
    "Your order from my shop is ready, please collect it by evening - Sharma Kirana", "Sir, I am the plumber, I'll come at 11 tomorrow",
    "Hi, this is {first} from the PTA group. Meeting at school on Saturday 10 AM.", "Please share the report by EOD", "Haan theek hai, kal milte hain",
    "I have transferred Rs {amt} to your HDFC account, please confirm", "Sent you Rs {amt_s} for the movie tickets", "Can you lend me your Netflix password? 😅",
    "Didi, rakhi mil gayi, thank you ❤️", "Flight landed. Taking a cab now.", "Tell papa I'll call after the meeting",
]
for t in PERSONAL:
    for s in ["c", "+", "c", "+"]:
        ROWS.append(("c:+91" + phone() if s == "c" else "+91" + phone(), "P", fill(t)))

# A friend may ask for a code; a stranger asking is the oldest OTP scam there is.
for _ in range(2 * COPIES):
    ROWS.append(("c:+91" + phone(), "P", "Can you send me the OTP that came on your phone? Need it for the booking"))
    ROWS.append(("+91" + phone(), "X", "Hi, I sent an OTP to your number by mistake. Can you send me the OTP that came on your phone? Urgent please"))

# ---------------------------------------------------------------------------------------------
# Spam and scams: must be filtered
# ---------------------------------------------------------------------------------------------
add("X", ["+"], [
    "Dear customer your SBI YONO account will be blocked today please update your PAN card immediately click here {link:sbi-yono-kyc.xyz/u}",
    "Dear Customer, Your HDFC Bank account will be suspended today. Please update your KYC immediately: {link:hdfc-kyc-update.in/v}",
    "Your electricity connection will be disconnected tonight at 9:30 PM as your previous month bill was not updated. Please immediately contact our electricity officer {phone}",
    "Dear consumer, your power will be disconnected today at 10 PM. Your last month bill is not updated. Call electricity officer {phone} immediately.",
    "Congratulations! You have won Rs 25,00,000 in KBC lottery. To claim your prize call Rana Pratap Singh on {phone} on WhatsApp.",
    "Part time job offer! Earn Rs 5000 daily from home by liking YouTube videos. WhatsApp me: {link:wa.me}",
    "Hello, I am HR from Amazon. We are hiring for work from home. Earn 3000-8000 daily. Contact on Telegram @amzn_hr_{l3}",
    "India Post: Your parcel is on hold at our warehouse due to incomplete address. Update within 24 hours: {link:indiapost-track.top/in}",
    "Your package could not be delivered due to incomplete shipping address. Please update: {link:indpost.cc/upd}",
    "Dear user, your PAN card has been suspended. Update KYC now: {link:bit.ly}",
    "Your Paytm KYC is expired. Your wallet will be blocked in 24 hrs. Call {phone} to update KYC immediately.",
    "Income Tax Department: You are eligible for a tax refund of Rs 15,490. Click to claim: {link:incometax-refund.co/claim}",
    "Your vehicle {veh} has a pending traffic challan of Rs 500. Pay immediately to avoid legal action: {link:echallan-parivahan.cc/pay}",
    "Your Netflix account is on hold. Update your payment details: {link:netflix-billing-help.com/acc}",
    "Your Amazon account has been locked due to suspicious activity. Verify your identity: {link:amazon-secure-verify.top/l}",
    "Dear customer your SBI reward points Rs 7,250 will expire today. Redeem now by installing app: {link:sbi-rewardz.apk.link/d}",
    "Earn guaranteed returns of 300% in 30 days with our crypto trading signals. Join Telegram: {link:t.me}",
    "Join our VIP stock tips group for daily 20% profit. Limited seats. {link:chat.whatsapp.com}",
    "Instant loan approved Rs 50,000 without documents. Click to get money now: {link:quick-loan-cash.xyz/a}",
    "Your SIM card will be blocked in 2 hours due to incomplete KYC. Call customer care {phone} immediately.",
    "FedEx: Your parcel contains illegal items and is held by customs. Press 1 or call {phone} to speak to the officer.",
    "Your Airtel number will be deactivated today. Complete re-verification now: {link:airtel-kyc.in/r}",
    "Dear Sir, I sent Rs 5000 to your account by mistake. Please return it to my number {phone}. I will send screenshot.",
    "Your FASTag KYC is pending and it will be deactivated today. Update now: {link:fastag-kyc-update.com/k}",
    "Congratulations! Your mobile number has won an iPhone 16 in the Flipkart lucky draw. Claim now: {link:flipkart-lucky.win/c}",
    "Hot singles in your area are waiting to chat with you! Join free now: {link:datingmeet.click/j}",
    "Get Rs 10,000 bonus on your first deposit! Play Teen Patti and win real cash daily: {link:tp-royal.vip/d}",
    "URGENT: Your Aadhaar is linked to illegal activity. Call CBI officer {phone} immediately or you will be arrested.",
    "Your credit card reward points worth Rs 5,999 are expiring today. Redeem now: {link:hdfc-rewards.top/r}",
    "Dear customer, your electricity bill of Rs 1,250 is pending. Your connection will be cut tonight. Pay now: {link:bit.ly}",
], 2)
add("X", ["=sbi.kyc.update@gmail.com", "=support.alerts@icloud.com", "=noreply@amaz0n-in.com"], [
    "Your SBI account is blocked. Update your KYC immediately: {link:sbi-kyc-verify.top/u}",
    "Apple ID: Your account was used to sign in on a new device. If not you, verify here: {link:appleid-verify.support/v}",
    "Amazon: unusual sign-in detected on your account. Verify now or your account will be locked: {link:amazon-in.verify-acc.xyz/l}",
], 2)
add("X", ["KYCUPD-S", "REWARD-S", "LOTTRY-T", "TAXREF-S"], [
    "Your ICICI Bank account KYC expires today and the account will be blocked. Update now: {link:icici.co.kyc-verify.top/x}",
    "Congratulations! You have won Rs 10,000 cash reward. Claim your prize now: {link:rewards-claim.xyz/c}",
], 2)
# Hard ones: people-like openers from unknown numbers. Either is fine, but never Offers or Shopping.
add("P|X", ["+"], [
    "Hi, is this {first}? I got your number from the yoga class.",
    "Hello dear, long time no see. How are you doing?",
], 2)


# ---------------------------------------------------------------------------------------------
# Round two: real messages that sound alarming (must never be spam, even on High), more formats
# ---------------------------------------------------------------------------------------------
add("S|U", ["DELHVY-S", "XPRESB-S", "SHPRKT-S", "ECOMEX-S", "SHADOW-S", "BLUDRT-S", "QWKDEL-S", "NMBSHP-S"], [
    "We could not deliver your shipment {awb} as the address was incomplete. Please update your address on the app to reschedule delivery.",
    "Delivery attempt failed for AWB {awb}: customer not reachable. We will reattempt tomorrow. Reschedule: {link:dlvry.in/r}",
    "Your package {awb} is held at our hub due to an incorrect pincode. Update your delivery address within 3 days or it will be returned to the seller.",
], 2)
add("U|T", ["HDFCBK-S", "ICICIB-S", "SBIINB-S", "AXISBK-S", "KOTAKB-S", "CANBNK-S", "PNBSMS-S", "NSDLPB-S", "DCBBNK-S", "ESAFBK-S"], [
    "Your Debit Card XX{l4} has been blocked due to 3 incorrect PIN attempts. It will be unblocked automatically after 24 hours.",
    "Dear Customer, your account XX{l4} will be frozen for debits as your KYC is due. Please visit your branch or update via net banking to avoid restrictions.",
    "Your Credit Card XX{l4} has been temporarily blocked due to suspicious transactions. Call 18002586161 immediately to unblock.",
    "Alert: unusual login attempt on your NetBanking from a new device on {date}. If this was not you, call 1800 2662 immediately.",
    "Your account XX{l4} has been marked inoperative due to no transactions in 2 years. Visit your branch with KYC documents to reactivate.",
], 2)
add("U|T", ["TPDDLD-S", "BSESRP-S", "MSEDCL-S", "BESCOM-S", "TNEBLD-S", "UPPCLD-S", "PSPCLD-S", "DHBVNL-S"], [
    "Dear Consumer, your electricity supply for CA No {ca} is liable to be disconnected on {date} due to non-payment of Rs {amt_s}. Please pay immediately to avoid disconnection.",
    "Final notice: your electricity bill of Rs {amt_s} for consumer {ca} is overdue. Pay by {date} to avoid disconnection. Pay online: {link:bsesdelhi.com/p}",
], 2)
add("U|T", ["MPRVHN-G", "DLTPOL-G", "ECHALN-G", "MHTRAF-G", "BLRTRF-G", "PARIVH-S"], [
    "A challan of Rs {amt_s} has been issued to vehicle {veh} on {date} for over-speeding. Pay at https://echallan.parivahan.gov.in within 60 days to avoid court proceedings.",
    "Your vehicle {veh} has a pending e-challan no DL{ref}. Pay online at echallan.parivahan.gov.in. -Delhi Traffic Police",
], 2)
add("U|T", ["ITDEFL-G", "CBDTIN-G", "INCTAX-G"], [
    "Refund of Rs {amt} for AY 2026-27 has been issued to your bank account XX{l4}. -Income Tax Department",
    "Your ITR-1 for AY 2026-27 has been successfully verified. Ack no {ref}. Refund, if any, will be processed shortly.",
    "Dear taxpayer, outstanding demand of Rs {amt} is pending against PAN XXXXX{l4}X. Please respond on the e-filing portal to avoid recovery.",
], 2)
add("U", ["JIOINF-S", "AIRTEL-S", "VICARE-S", "BSNLIN-S"], [
    "Your SIM will be blocked for outgoing services as your re-verification is pending. Visit the nearest Jio store with your ID proof by {date}.",
    "You have received a missed call from {phone} at {time}. -Airtel",
    "Dear customer, your number {phone} has been successfully ported to Airtel. Welcome!",
    "Your eSIM activation request has been received. It will be active within 2 hours.",
], 2)
add("U|F", ["NAUKRI-S", "APNAJB-S", "INDEED-S", "SHINE-S", "WRKIND-S"], [
    "3 new work from home jobs match your profile on Naukri. Apply now: {link:nkri.in/j}",
    "Part-time data entry jobs near you are hiring. Check jobs on the apna app.",
], 2)
add("U|T|F", ["LICIND-S", "HDFCLI-S", "MAXLIF-S", "SBILIF-S"], [
    "Your policy {ca} has lapsed due to non-payment of premium. Revive it within 6 months to continue your cover.",
    "Your policy {ca} will mature on {date}. The maturity amount of Rs {amt_l} will be credited to your bank account.",
], 2)
add("T", ["HDFCBK", "ICICIT", "SBIUPI", "AXISBK", "KOTAKB"], [
    "AutoPay of Rs {amt_s} for NETFLIX will be debited from A/c XX{l4} on {date}. To cancel, visit your UPI app.",
    "Your UPI AutoPay mandate for Rs {amt_s} to SPOTIFY has been successfully executed on {date}. UPI Ref {ref}.",
    "Loan amount of Rs {amt_l} has been disbursed to your A/c XX{l4} on {date}. First EMI on {date2}.",
    "Dividend of Rs {amt_s} from INFOSYS LTD credited to your A/c XX{l4} on {date}.",
    "Rs.{amt_s} debited from A/c XX{l4} towards SMS alert charges for the quarter.",
    "Your NPS contribution of Rs {amt} has been credited to PRAN XXXX{l4} on {date}.",
    "Interest of Rs {amt_s} credited to your RD account XX{l4}. Maturity date {date}.",
    "आपके खाते XX{l4} से ₹{amt} डेबिट किए गए हैं। उपलब्ध शेष ₹{bal} -SBI",
    "आपके खाते XX{l4} में ₹{amt} जमा किए गए। -PNB",
], 2)
add("T", ["DPSSCH-S", "RYANIN-S", "AMITYS-S", "KVSSCH-S", "FIITJE-S", "ALLENI-S", "APNACM-S", "MYGATE-S", "NOBROK-S"], [
    "Dear Parent, fee of Rs {amt} for Term 2 is due on {date}. Please pay through the school app to avoid a late fee.",
    "Maintenance of Rs {amt} for Flat B-{l3} for {date} is due. Pay on ApnaComplex before the due date.",
    "We have received fee payment of Rs {amt} for {first}. Receipt no {ref}. Thank you.",
    "Rent of Rs {amt} paid to {NAME} via NoBroker. Rent receipt sent to your email.",
], 2)
add("T", ["GOOGLE", "=Google", "APPLEI", "NETFLX", "SPOTIF", "YTPREM", "PRIMEV", "HOTSTR", "SWGONE", "ZMTGLD"], [
    "You paid Rs {amt_s} to Google Play for YouTube Premium. Order GPA.{l4}-{l4}-{l4}-{l5}.",
    "Your Netflix membership payment of Rs 649 was successful. Thanks for watching!",
    "Rs {amt_s} charged for your Swiggy One membership renewal. Valid till {date}.",
    "Your payment of Rs 299 for Amazon Prime Video failed. Update your payment method to continue watching.",
], 2)
add("U|V|S", ["BKMYSH", "PVRCIN", "INOXMV", "DISTRC", "INSDRS"], [
    "Your tickets for Pushpa 3 at PVR {city}, Screen 4, {date} {time} are confirmed. Seats F7, F8. Booking ID {ref}.",
    "Booking confirmed! Sunburn Arena {city} on {date}. Show the QR code in the BookMyShow app at the entry.",
], 2)
add("U|F|T", ["HDFCBK", "ICICIB", "SBICRD", "AMEXIN", "AXISBK"], [
    "You have earned {l3} reward points on your Credit Card XX{l4} for the statement dated {date}. Total points: {l5}.",
], 2)
add("S|T", ["FLPKRT", "AMAZON", "MYNTRA", "MEESHO"], [
    "Payment for your order {oid} failed. If money was deducted, it will be refunded in 5-7 days. Please place the order again.",
    "Cash of Rs {amt_s} collected for order {oid}. Thank you for shopping with us!",
], 2)
add("T|U", ["ZERODH", "CDSLEV", "NSDLCS", "KFINTK", "BSELTD"], [
    "Congratulations! {l2} shares of SWIGGY LTD IPO have been allotted to your demat account on {date}.",
    "Your Consolidated Account Statement for {date} has been sent to your registered email ID. -NSDL",
], 2)
add("F", ["HDFCBK", "ICICIB", "SBICRD", "AXISBK", "KOTAKB", "IDFCFB", "AUBANK", "INDUSB"], [
    "Shop on Amazon with your ICICI Bank Credit Card and get {pct}% instant discount up to Rs 1,500. Offer valid till {date}. T&C",
    "Dear Customer, get a Personal Loan at {pct2}% p.a. with zero processing fee. Instant disbursal. Apply: {link:icici.co/ICICIB/s}",
    "Exclusive for you: Get an IndusInd Bank Credit Card with lifetime free membership. Apply now: {link:indusind.com/cc}",
    "Upgrade to a Platinum Debit Card and enjoy airport lounge access. Know more: {link:axisbk.in/dc}",
], 2)
# Local shops and services text from personal numbers: an offer or a person, never spam.
add("F|P", ["+"], [
    "Diwali offer at Sharma Sweets! Flat 20% off on all mithai boxes till Sunday. Visit our store at Sector 18.",
    "New arrivals at Fashion Point boutique. 30% off on all kurtis this week. Call {phone} for home delivery.",
    "Fresh vegetables home delivery from Gupta Sabzi Wala. Order on WhatsApp {phone}.",
], 2)
# People, written the way people write.
PEOPLE2 = [
    "Bank ka kaam ho gaya, ye dekh {link:youtu.be}", "Urgent, call me back please. Mom is not well", "Gate 3 pe hu, aaja",
    "Your Ola is here? I'm at gate 2 of the mall", "Pay me back 250 for the cab whenever", "Flat 302, Tower B, Sunshine Apartments. Come by 7",
    "Sent you the drive link {link:drive.google.com/file/d}", "Check my reel {link:instagram.com/reel}", "Bhai mera account block ho gaya, 2000 bhej de",
    "Won the match!! 🏆 Party tonight", "Did you see the HDFC offer on iPhones? 10% off till Sunday", "Payment ho gaya? Order confirm hua?",
    "The courier guy called, said he'll come tomorrow", "Meri train 2 ghante late hai", "Kal ka OTP wala scene samjha nahi, call kar",
    "Aaj office mein meeting thi, late ho jaunga", "Ma'am, I am Ravi from Sunrise Tuitions. Class is cancelled today.",
]
for t in PEOPLE2:
    for s in ["c", "+", "c", "+"]:
        ROWS.append(("c:+91" + phone() if s == "c" else "+91" + phone(), "P", fill(t)))
# More scams people actually get.
add("X", ["+"], [
    "Dear customer, Rs 4,999 will be debited from your account for Amazon Prime auto-renewal today. If not done by you, call {phone} to cancel.",
    "Priya upbhokta, aaj raat 9:30 baje aapki bijli kaat di jayegi kyunki aapka pichhle mahine ka bill update nahi hua hai. Turant bijli adhikari se sampark karein {phone}",
    "प्रिय उपभोक्ता, आज रात 9:30 बजे आपकी बिजली काट दी जाएगी। कृपया तुरंत बिजली अधिकारी से संपर्क करें {phone}",
    "Hi, I am from Meesho. Your number won a Tata Safari in our lucky draw. Call {phone} to claim your prize.",
    "Earn Rs 150 per review! Rate hotels on Google and earn daily. Simple prepaid task. WhatsApp {link:wa.me}",
    "Your loan EMI is overdue. Pay today or we will send your morphed photos to all your contacts.",
    "TRAI: Your mobile number will be blocked in 2 hours due to illegal activity. Press 1 to speak to our officer.",
    "Mumbai Customs: A parcel in your name with drugs found. Your Aadhaar is used for money laundering. Call officer {phone} now.",
    "Free 3 months Jio recharge of Rs 239 for all users! Click here to claim now: {link:jio-free-recharge.click/c}",
    "Your HDFC credit card points worth Rs 9,840 expire today. Download the HDFC Rewards app to redeem: {link:hdfc-reward.apk.link/d}",
    "Congratulations! You have been selected for a work from home job. Earn Rs 3000 to 8000 daily. Contact HR on Telegram.",
    "Dear user, your Paytm account is temporarily blocked due to KYC. Complete your KYC immediately: {link:paytm-kyc.co.in/v}",
    "Your Instagram account will be deleted for copyright violation. Appeal now: {link:instagram-help-center.top/a}",
    "Hello sir, your son has been arrested by police. Send Rs 50,000 immediately on this number to release him. Don't tell anyone.",
    "I sent Rs 3000 to your number by mistake on Paytm, please send it back to {phone}. Please sir it's my hospital money.",
], 2)


def main():
    seen = set()
    rows = []
    for sender, want, text in ROWS:
        kind = "contact" if sender.startswith("c:") else "number" if sender.startswith("+") else sender.split("-")[1] if "-" in sender else sender
        key = (kind, want, text)
        if key in seen:
            continue
        seen.add(key)
        rows.append((sender, want, text.replace("\t", " ").replace("\n", "\\n")))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("# Generated by tools/corpus/make_sms_corpus.py: sender<TAB>tabs<TAB>text\n")
        for r in rows:
            f.write("\t".join(r) + "\n")
    counts = {}
    for _, w, _ in rows:
        counts[w] = counts.get(w, 0) + 1
    print(OUT, len(rows), "messages", dict(sorted(counts.items())))


if __name__ == "__main__":
    main()
