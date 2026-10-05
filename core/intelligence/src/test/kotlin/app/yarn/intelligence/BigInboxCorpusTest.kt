package app.yarn.intelligence

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * A large, realistic Indian inbox, written to the rule "file a message by what it IS":
 *  - OTP: it carries a code to type in.
 *  - TRANSACTIONS: money actually moved or is owed (debits, credits, refunds, bills, EMIs, dues).
 *  - SHOPPING: an order's journey.
 *  - UPDATES: account, travel, government, telecom and safety notices that need no money action.
 *  - OFFERS: anything selling something — including offers sent by banks on -S headers.
 *  - PERSONAL: people.
 * Banks send all six kinds, so "it's from a bank" is never enough to call it a transaction.
 */
class BigInboxCorpusTest {
    private val analyzer = MessageAnalyzer()

    private data class Case(val from: String, val text: String, val want: Set<CategoryGroup>)

    private val P = CategoryGroup.PERSONAL
    private val O = CategoryGroup.OTP
    private val T = CategoryGroup.TRANSACTIONS
    private val S = CategoryGroup.SHOPPING
    private val U = CategoryGroup.UPDATES
    private val F = CategoryGroup.OFFERS

    private fun c(from: String, text: String, vararg want: CategoryGroup) = Case(from, text, want.toSet())

    private val cases = listOf(
        // ---- The user's own examples (were wrongly in Transactions) ---------------------------------
        c("AX-PLPRKS-S", "Pine Labs will never call you for the card number/CVV/expiry/OTP/PIN for any purpose like KYC update. Do NOT disclose it to anyone on call/email. - Pine Labs", U),
        c("JM-AMEXIN-S", "AMEX never asks for OTP, Card details or sensitive information. Please do not share these with anyone. To read in another language, visit https://go.amex/QwzsKh", U),
        c("JM-HDFCBK-S", "Service Update\nYour HDFC Bank A/c ending xx2626 is eligible for a Lifetime FREE RuPay Credit Card.\nCheck: https://1.hdfc.bank.in/HDFCBK/s/XKK3Aj45 T&C", F),
        c("JM-HDFCBK-S", "Dear KARAN MITTAL,\nYour Domestic Airport Lounge Voucher for the calendar quarter Oct'2026 - Dec'2026 is available on your HDFC Bank Regalia ForexPlus Card.\nTo claim, visit https://1.hdfc.bank.in/HDFCBK/s/eqqd74Ak\nSo plan your international travel NOW!! And claim it before 31 Dec 2026 .\nFor any help, visit www.gvhelpdesk.com", F),
        // A statement with an amount due is a bill: Transactions.
        c("AX-MYSBIC-S", "E-statement of SBI Credit Card ending XX95 dated 01/10/2026 has been mailed. If not received, SMS ENRS to 5676791. Total Amt Due Rs 16477; Min Amt Due Rs 15856; Payable by 21/10/2026. Click https://sbicard.com/quickpaynet to pay your bill", T),

        // ---- Bank safety advisories -> Updates ------------------------------------------------------
        c("VM-SBIINB-S", "SBI never asks for your card details, PIN, OTP or password over call, SMS or email. Beware of fraudsters. Report cyber fraud at 1930.", U),
        c("AD-ICICIB-S", "ICICI Bank never asks for your OTP, CVV or PIN. Do not share them with anyone, even if they claim to be from the bank.", U),
        c("JD-AXISBK-S", "Beware! Fraudsters may pose as Axis Bank officials and ask you to update KYC via a link. Axis Bank will never send such links.", U),
        c("VK-KOTAKB-S", "Stay alert! Kotak never calls to ask for OTP/PIN/CVV. Do not install screen-sharing apps on anyone's request. Report fraud on 1930.", U),
        c("AX-HDFCBK-S", "Do not share your OTP, PIN or password with anyone. HDFC Bank will never ask for it. Stay safe from frauds.", U),
        c("JM-YESBNK-S", "Fraud alert: Never scan a QR code to receive money. QR codes are only for making payments. - YES BANK", U),
        c("VM-PNBSMS-S", "PNB never asks for confidential details. If you receive such calls, report to cybercrime.gov.in or call 1930.", U),
        c("AD-BOBTXN-S", "Bank of Baroda: Beware of fake customer care numbers on internet search. Use only official channels.", U),
        c("JK-IDFCFB-S", "IDFC FIRST Bank never asks for your login credentials. Do not respond to such requests.", U),
        c("VM-CANBNK-S", "Canara Bank cautions customers against sharing OTP/PIN. Bank officials never ask for these details.", U),
        c("AX-PAYTMB-S", "Paytm never asks you to share your PIN or OTP to receive money. Beware of fraud calls.", U),
        c("JD-PHONPE-S", "Remember: You never need to enter your UPI PIN to receive money on PhonePe. Stay safe.", U),
        c("VM-RBISAY-G", "RBI Kehta Hai: Do not share your card details, PIN or OTP with anyone. Be aware, be safe.", U),
        c("AD-INDUSB-S", "IndusInd Bank never asks you to download apps for KYC. Don't click unknown links.", U),
        c("JM-SCBANK-S", "Standard Chartered: We will never ask for your PIN or OTP. Please do not disclose it to anyone.", U),

        // ---- Bank & card offers on -S/-T headers -> Offers ------------------------------------------
        c("VM-HDFCBK-S", "Dear Customer, you are pre-approved for a Personal Loan of up to Rs 10,00,000 at attractive rates. Apply now: hdfc.bank.in/pl T&C", F),
        c("AD-ICICIB-S", "Congratulations! Your ICICI Bank Credit Card is eligible for a credit limit upgrade. Click to know more. T&C apply", F),
        c("JD-AXISBK-S", "Get an Axis Bank Credit Card with zero joining fee and 5% cashback on bill payments. Apply now: axisbk.in/cc", F),
        c("VK-KOTAKB-S", "Instant Personal Loan up to Rs 5 Lakh in 10 mins. No documents needed. Apply on Kotak811 app. T&C", F),
        c("AX-SBICRD-S", "Shop on Amazon this festive season and get 10% instant discount with your SBI Card. Offer valid till 15 Oct. T&C apply", F),
        c("JM-HDFCBK-S", "Your HDFC Bank Credit Card is eligible for an EMI conversion at 13% p.a. on your recent purchase. Convert now via NetBanking.", F, T),
        c("VM-IDFCFB-S", "Earn up to 7.25% p.a. on your savings with IDFC FIRST Bank. Open an account in 5 mins. Know more.", F),
        c("AD-YESBNK-S", "Exclusive for you: YES BANK FD at 7.75% p.a. for senior citizens. Book now on YES Mobile.", F),
        c("JD-AUBANK-S", "Lifetime free AU Credit Card is waiting for you! Apply now and get welcome rewards worth Rs 2000.", F),
        c("VM-ICICIB-S", "Unlock exclusive dining benefits with your ICICI Bank Credit Card. Up to 25% off at 2500+ restaurants. Know more.", F),
        c("AX-HDFCBK-S", "Your 2,450 reward points are waiting! Redeem them for flights, vouchers and more on SmartBuy.", F),
        c("JK-SBIINB-S", "Pre-approved car loan from SBI at 8.75% p.a. with zero processing fee. Visit your nearest branch.", F),
        c("VM-AMEXIN-S", "Your American Express Card offers: 20% off on Swiggy Instamart this weekend. Enrol now.", F),
        c("AD-ONECRD-S", "Your OneCard is ready! Get lifetime free metal card with 5x rewards. Activate now.", F),
        c("JD-BAJAJF-S", "Pre-approved loan offer of Rs 3,50,000 is waiting for you. Avail now with Bajaj Finserv. T&C", F),
        c("VM-CREDIN-S", "Your CRED coins are expiring soon! Use them for jackpot rewards before Sunday.", F),
        c("AX-KOTAKB-S", "Upgrade to Kotak Privy League and enjoy free lounge access and zero forex markup. Know more.", F),
        c("JM-HDFCBK-S", "Complimentary movie ticket every month with HDFC Bank Times Card. Apply today.", F),
        c("VK-SBICRD-S", "Your SBI Card is eligible for a Flexipay EMI offer on transactions above Rs 2500. Avail now.", F, T),
        c("AD-IDFCFB-S", "Congrats! You are eligible for a credit card with Rs 1,50,000 limit. No income proof needed. Get it now.", F),

        // ---- Real bank transactions -> Transactions --------------------------------------------------
        c("JM-HDFCBK-S", "Sent Rs.250.00 From HDFC Bank A/C *2626 To SWIGGY On 04/10/26 Ref 627712345678 Not You? Call 18002586161/SMS BLOCK UPI to 7308080808", T),
        c("AD-SBIINB-S", "Dear UPI user A/C X1234 debited by 500.0 on date 04Oct26 trf to RAHUL KUMAR Refno 427812345678. If not u? call 1800111109. -SBI", T),
        c("VM-ICICIB-S", "ICICI Bank Acct XX123 debited for Rs 1,299.00 on 04-Oct-26; Netflix credited. UPI:427812345679. Call 18002662 for dispute.", T),
        c("JD-AXISBK-S", "INR 15,000.00 credited to A/c no. XX4321 on 04-10-26 by NEFT from ACME PVT LTD. Avl Bal INR 48,250.12", T),
        c("VK-KOTAKB-S", "Rs.2000.00 is debited from Kotak Bank a/c XX9876 for ATM cash withdrawal on 04-Oct-26. Avl bal Rs 12000", T),
        c("AX-HDFCBK-S", "Spent Rs.3499 On HDFC Bank Card 1234 At AMAZON PAY On 2026-10-04:18:22:10. Not You? To Block+Reissue Call 18002586161", T),
        c("JM-SBICRD-S", "Rs.799.00 spent on your SBI Credit Card ending 95 at ZOMATO on 04/10/26. Trxn. not done by you? Report at https://sbicard.com/Dispute", T),
        c("VM-AMEXIN-S", "Alert: You've spent INR 2,340.00 on your AMEX card ** 31005 at UBER INDIA on 4 October 2026 at 09:12 PM IST.", T),
        c("AD-YESBNK-S", "INR 450.00 debited from YES BANK A/c XX5555 towards UPI/427812345680/Blinkit. Bal INR 9,876.00", T),
        c("JD-IDFCFB-S", "Your A/C XXXXXX1234 has been credited with INR 1,00,000.00 on 04-OCT-26 via IMPS. Available balance INR 2,30,000.00", T),
        c("VM-PNBSMS-S", "Ac XX7777 Debited INR 3,000.00 on 04-10-26 by Cheque No 000123. Aval Bal INR 20,123.00 -PNB", T),
        c("AX-CANBNK-S", "An amount of INR 600.00 has been DEBITED to your account XXX888 on 04/10/2026 towards LIC premium. Total Avail.bal INR 5,400.00", T),
        c("JM-INDUSB-S", "Your IndusInd Bank Credit Card XX4545 was used for INR 1,250.00 at DMART on 04-10-26.", T),
        c("VK-SCBANK-S", "Your StanChart Credit Card ending 6677 has been charged INR 899 at SPOTIFY.", T),
        c("AD-AUBANK-S", "Salary of INR 85,000.00 credited to your AU Bank account XX1212 on 01-Oct-26.", T),
        c("JM-HDFCBK-S", "Rs.5000.00 has been credited to your HDFC Bank A/c XX2626 by VPA rahul@okaxis on 04-10-26. Avl bal Rs.25,500", T),
        c("VM-ICICIB-S", "Dear Customer, your ICICI Bank Credit Card XX1001 payment of INR 16,477 has been received. Thank you.", T),
        c("AX-SBICRD-S", "We have received payment of Rs.16,477.00 towards your SBI Card ending 95 on 04-Oct-26. Thank you.", T),
        c("JD-AXISBK-S", "Your Axis Bank account has been debited Rs 1,500 towards SIP in Axis Bluechip Fund. Folio 9123456.", T),
        c("VM-KOTAKB-S", "Interest of Rs 1,234.00 credited to your Kotak FD no XX4455 on 01-10-26.", T),
        c("AD-BOBTXN-S", "Rs.10,000 transferred from A/c ...1234 to A/c ...9999 via RTGS. UTR BARBR52781234567. -Bank of Baroda", T),
        c("JK-HDFCBK-T", "Your HDFC Bank Debit Card XX2626 has been used for an online transaction of Rs 249.00 at GOOGLE PLAY.", T),
        c("VM-FEDBNK-S", "Rs 750.00 debited from your Federal Bank A/c XX8888 via UPI to Swiggy Limited on 04-10-2026.", T),
        c("AX-RBLBNK-S", "Your RBL Bank Credit Card XX3434 was declined for INR 5,000 at FLIPKART due to insufficient limit.", T),
        c("JM-ICICIB-S", "INR 2,500 refund from MYNTRA credited to your ICICI Bank Credit Card XX1001 on 04-Oct-26.", T),

        // ---- Bills, dues, EMIs -> Transactions --------------------------------------------------------
        c("VM-HDFCBK-S", "Your HDFC Bank Credit Card statement for Sep-26 is ready. Total due Rs 45,994.00, minimum due Rs 2,300.00, due date 23-Oct-26.", T),
        c("AD-ICICIB-S", "Reminder: Payment of INR 8,450 for your ICICI Bank Credit Card XX1001 is due on 10-Oct-26. Pay via iMobile to avoid charges.", T),
        c("JD-KOTAKB-S", "Hi KARAN, HDFC BANK CREDIT CARD bill ready for credit card 2774. Total due Rs. 45994.00. Pay on Kotak811 app before 23 Oct.", T),
        c("VK-BAJAJF-S", "EMI of Rs 4,567 for loan account 4XX123 is due on 05-10-2026. Please keep sufficient balance.", T),
        c("AX-TATAPW-S", "Dear Consumer, your electricity bill of Rs 2,340 for Sep-26 is due on 15-Oct-26. CA no 600012345.", T),
        c("JM-BESCOM-S", "BESCOM: Bill amount Rs.1,876 for RR No 123456 is due by 12/10/2026. Pay online to avoid disconnection.", T),
        c("VM-AIRTEL-S", "Your Airtel postpaid bill of Rs 599 is due on 08-Oct. Pay now on Airtel Thanks app.", T),
        c("AD-JIOINF-S", "Your JioFiber bill of Rs 1,178 has been generated. Due date 10-Oct-2026.", T),
        c("JD-LICIND-S", "Premium of Rs 12,500 for policy 123456789 is due on 15-Oct-26. Pay online at licindia.in", T),
        c("VM-MAHAGN-S", "Dear Customer, your Mahanagar Gas bill of Rs 845 is due on 12/10/2026.", T),
        c("AX-ACTFBR-S", "Your ACT Fibernet bill Rs 943.82 is due on 07-Oct-26. Pay now to enjoy uninterrupted service.", T),
        c("JM-TATASK-S", "Your Tata Play balance is Rs 45. Recharge by 06-Oct to continue watching.", T),
        c("VK-HDFCLI-S", "Your HDFC Life policy premium of Rs 25,000 is due on 20-Oct-26. Ignore if already paid.", T),
        c("AD-SBICRD-S", "Your SBI Card statement is generated. Total Amount Due Rs 16,477 Min Amt Due Rs 15,856. Payable by 21/10/2026.", T),
        c("JD-HDBFIN-S", "Your EMI of Rs 2,890 towards loan no 1234567 was bounced. Please pay immediately to avoid penalty.", T),
        c("VM-NPCLBL-S", "Your NoBroker rent payment of Rs 25,000 is scheduled for 05-Oct. Ensure balance in your account.", T),

        // ---- Account & card service notices (no money moved) -> Updates -------------------------------
        c("AX-HDFCBK-S", "You have successfully logged in to HDFC Bank NetBanking on 04-10-26 at 10:12. Not you? Call 18002586161.", U),
        c("VM-ICICIB-S", "Your ICICI Bank Debit Card XX1234 has been blocked as requested. A new card will be dispatched within 7 days.", U),
        c("JD-AXISBK-S", "Your Axis Bank cheque book with 25 leaves has been dispatched via Speed Post EA123456789IN.", U, S),
        c("AD-SBIINB-S", "Your SBI YONO profile has been updated successfully. Mobile number registered.", U),
        c("VK-KOTAKB-S", "Your Kotak 811 account KYC has been successfully completed. Thank you for banking with us.", U),
        c("JM-HDFCBK-S", "Your request to change your communication address has been processed. Ref no 12345678.", U),
        c("AX-SBICRD-S", "Your SBI Card has been delivered. Activate it via SBI Card app to start using it.", U, S),
        c("VM-IDFCFB-S", "Your nominee details have been successfully registered for A/c XX1234.", U),
        c("AD-YESBNK-S", "International usage on your YES BANK Debit Card has been enabled as requested.", U),
        c("JD-ICICIB-S", "Your fixed deposit 1234 will mature on 10-Oct-26. It will be auto-renewed for the same tenure.", U, T),
        c("VM-HDFCBK-S", "Dear Customer, NetBanking will be unavailable on 06-Oct from 1 AM to 4 AM due to scheduled maintenance.", U),
        c("AX-AXISBK-S", "Your credit limit has been increased to Rs 3,00,000 as requested. Happy spending!", U, T),
        c("JM-SBIINB-S", "Your PAN has been successfully linked with your SBI account XX1234.", U),
        c("VK-PAYTMB-S", "Your Paytm UPI Lite has been activated. You can now make small payments without PIN.", U),
        c("AD-GPAYIN-S", "Your bank account XX1234 has been linked to Google Pay. You can now send and receive money.", U),
        c("JD-NSDLCA-S", "Your demat account statement for September 2026 has been sent to your registered email.", U, T),
        c("VM-CDSLTX-S", "Your CDSL account has been successfully linked to your mobile number for alerts.", U),
        c("AX-HDFCBK-S", "Your Form 15G for FY 2026-27 has been successfully submitted for A/c XX2626.", U),
        c("JM-ICICIB-S", "Your ICICI Bank Credit Card XX1001 PIN has been changed successfully.", U),

        // ---- One-time codes ---------------------------------------------------------------------------
        c("JM-HDFCBK-S", "OTP is 482913 for txn of INR 3499.00 at AMAZON PAY on HDFC Bank card ending 1234. Valid till 18:30. Do not share OTP for security reasons.", O),
        c("AD-SBIINB-S", "Dear Customer, OTP for login to SBI YONO is 773910. Do not share it with anyone. -SBI", O),
        c("VM-ICICIB-S", "Your OTP for ICICI Bank NetBanking login is 228341. Valid for 3 mins. Never share it.", O),
        c("JD-AXISBK-S", "583201 is OTP for your Axis Bank Credit Card transaction of INR 1,250 at MYNTRA. Valid for 5 mins.", O),
        c("AX-AMEXIN-T", "Your American Express One Time Password is 913042. It is valid for 10 minutes.", O),
        c("JM-HDFCLI-S", "Your One Time Password is 102318. This OTP is valid for 3 mins. Do not share this OTP to anyone for security reasons. TnC apply- HDFC Life", O),
        c("VK-AUTOPE-S", "911847 is your AutoPe verification code. It is valid for 10 minutes. Do not share it.", O),
        c("57575711", "Your Priority Pass OTP is 344221. Use it to complete your login.", O),
        c("AD-AMAZON-T", "123456 is your Amazon OTP. Do not share it with anyone.", O),
        c("JD-FLPKRT-S", "Your Flipkart verification code is 662910. Don't share it with anyone.", O),
        c("VM-SWIGGY-T", "Your OTP to login to Swiggy is 5521. Valid for 10 min.", O),
        c("AX-ZOMATO-T", "4413 is your Zomato login OTP. Please do not share.", O),
        c("JM-UBERIN-T", "Your Uber code is 2234. Never share this code.", O),
        c("VK-OLACAB-S", "Your Ola ride OTP is 7718. Share it with your driver to start the ride.", O, U),
        c("AD-WHTSAP-T", "Your WhatsApp code: 482-913. Don't share this code with others.", O),
        c("JD-GOOGLE-T", "G-482913 is your Google verification code.", O),
        c("VM-PAYTMB-T", "Your Paytm login OTP is 820193. Valid for 2 minutes. Never share your OTP.", O),
        c("AX-PHONPE-T", "PhonePe OTP: 551290. Do not share this with anyone.", O),
        c("JM-IRCTCI-T", "Your IRCTC registration OTP is 448812. Valid for 10 minutes.", O),
        c("VK-UIDAI-G", "Your Aadhaar OTP for authentication is 391204. Valid for 10 minutes. Do not share.", O),
        c("AD-NSDLPN-G", "OTP for PAN-Aadhaar linking is 772341. Do not share it with anyone.", O),
        c("JD-ZEPTON-T", "Your Zepto OTP is 3349. Do not share it.", O),
        c("VM-MYNTRA-T", "Use 553921 as your Myntra login OTP. Valid for 10 mins.", O),
        c("AX-BLNKIT-T", "Your Blinkit verification code is 7712.", O),
        c("JM-CREDIN-T", "Your CRED OTP is 662301. Do not share it with anyone including CRED staff.", O),
        c("VK-ZERODH-T", "Your Kite login OTP is 840213. Valid for 5 minutes.", O),
        c("AD-GROWWI-T", "481029 is your Groww OTP. Never share it.", O),
        c("JD-DIGILK-G", "Your DigiLocker OTP is 731902. Valid for 5 minutes.", O),
        c("VM-JIOINF-T", "Your Jio OTP is 552913. Use it to login to MyJio.", O),
        c("AX-AIRTEL-T", "Your Airtel Thanks app verification code is 449201.", O),
        c("JM-LICIND-T", "OTP for LIC customer portal login is 330129. Valid for 10 minutes.", O),
        c("VK-DELHVY-S", "Delivery OTP for your Delhivery shipment AWB 1234567890 is 4321. Share it only with the delivery agent.", O, S),
        c("AD-AMAZON-S", "Your Amazon delivery code is 908213. Share it with the delivery agent only when you receive your package.", O, S),
        c("JD-HDFCBK-T", "Your HDFC Bank NetBanking third-party payee registration OTP is 902134. Never share it.", O),
        c("VM-SBICRD-T", "OTP 220913 for adding your SBI Card to Google Pay. Valid for 10 mins. Do not share.", O),

        // ---- Fintech, wallets, investments -> Transactions --------------------------------------------
        c("AX-PAYTMB-S", "Rs 199 paid to Jio Prepaid via Paytm UPI. UPI Ref 427812345681.", T),
        c("JM-PHONPE-S", "Received Rs.500 from Rahul Kumar on PhonePe. Credited to your HDFC Bank a/c.", T),
        c("VK-GPAYIN-S", "You received Rs 1,000 from Priya via Google Pay. UPI transaction ID 612345678902.", T),
        c("AD-CREDIN-S", "Payment of Rs 45,994 towards HDFC Bank Credit Card ending 2774 successful via CRED.", T),
        c("JD-CREDIN-S", "Update from CRED wallet: Rs 500 cashback has been credited to your CRED wallet.", T),
        c("VM-ZERODH-S", "Your order to buy 10 shares of TCS at Rs 3,850 has been executed. Order ID 2610041234.", T),
        c("AX-GROWWI-S", "SIP of Rs 2,000 in Parag Parikh Flexi Cap Fund has been processed. Units will be allotted at today's NAV.", T),
        c("JM-UPSTOX-S", "Funds of Rs 10,000 added to your Upstox account successfully.", T),
        c("VK-CAMSMF-S", "Units allotted: 45.678 units in HDFC Index Fund at NAV 218.92 for Rs 10,000. Folio 1234567.", T),
        c("AD-KFINTK-S", "Dividend of Rs 1,250 credited for folio 9876543 of Axis Bluechip Fund.", T),
        c("JD-MOBKWK-S", "Rs 150 has been added to your MobiKwik wallet. Wallet balance Rs 320.", T),
        c("VM-AMZPAY-S", "Rs 99 paid using Amazon Pay balance for your Prime Video subscription.", T),
        c("AX-SLICE-S", "Your slice card was used for Rs 450 at SWIGGY. Available limit Rs 9,550.", T),
        c("JM-SIMPLE-S", "Simpl: Rs 340 bill for Zomato is due on 15 Oct. Pay now to keep using Simpl.", T),
        c("VK-LAZYPY-S", "Your LazyPay bill of Rs 1,230 is due on 08-Oct. Pay now to avoid late fee.", T),
        c("AD-NPSTRU-S", "Contribution of Rs 5,000 credited to your NPS Tier I account PRAN 110012345678.", T),
        c("JD-FASTAG-S", "Rs 105 debited from your FASTag wallet for toll at Kherki Daula. Balance Rs 445.", T),
        c("VM-IHMCLF-S", "Low balance alert: Your FASTag balance is Rs 80. Recharge now to avoid double toll.", T),
        c("AX-PAYTMB-S", "Recharge of Rs 299 for Airtel 9811XXXXXX is successful. Validity 28 days.", T),
        c("JM-AIRTEL-S", "Recharge successful! Rs 349 credited to 9811XXXXXX. Enjoy 2GB/day for 28 days.", T),

        // ---- Shopping: order journey ------------------------------------------------------------------
        c("AX-AMAZON-S", "Your Amazon order #405-1234567-1234567 for boAt Airdopes has been shipped. Track: amzn.in/d/abc", S),
        c("JM-FLPKRT-S", "Your Flipkart order for Realme Narzo is out for delivery today. Keep Rs 0 ready. Track at fkrt.it/abc", S),
        c("VK-MYNTRA-S", "Your Myntra order has been delivered. Rate your experience.", S),
        c("AD-MEESHO-S", "Your Meesho order #123456789 is packed and will be shipped soon.", S),
        c("JD-AJIOCM-S", "Your AJIO return pickup is scheduled for tomorrow between 10 AM - 6 PM.", S),
        c("VM-NYKAAF-S", "Hi! Your Nykaa order NYK-12345 is on its way and will reach you by 6 Oct.", S),
        c("AX-BLNKIT-S", "Your Blinkit order is on the way! Arriving in 8 minutes.", S),
        c("JM-ZEPTON-S", "Your Zepto order has been delivered. Enjoy!", S),
        c("VK-SWIGGY-S", "Your Swiggy order from Meghana Foods is being prepared and will reach you in 30 mins.", S),
        c("AD-ZOMATO-S", "Your Zomato order from Truffles has been picked up by Rohan. Track live in the app.", S),
        c("JD-BBASKT-S", "Your bigbasket order BB12345 for 6 Oct, 7-9 AM slot is confirmed.", S),
        c("VM-JIOMRT-S", "Your JioMart order 123456 has been dispatched. Expected delivery 7 Oct.", S),
        c("AX-DELHVY-S", "Shipment AWB 1234567890 from Amazon is out for delivery. Agent: Suresh 98XXXXXX12.", S),
        c("JM-BLUDRT-S", "BlueDart: Your shipment 81234567890 has been delivered to KARAN at 14:20.", S),
        c("VK-EKARTL-S", "Ekart: We could not deliver your package today as you were unavailable. We will try again tomorrow.", S),
        c("AD-DTDCIN-S", "DTDC: Your consignment D1234567 is in transit and will be delivered by 8 Oct.", S),
        c("JD-SHDWFX-S", "Shadowfax: Your Swiggy Instamart order is arriving in 5 mins.", S),
        c("VM-CROMA1-S", "Your Croma order 12345 is ready for pickup at Croma Phoenix Mall store.", S),
        c("AX-TATACQ-S", "Your Tata CLiQ order has been cancelled as requested. Refund will be processed in 5-7 days.", S, T),
        c("JM-DOMINO-S", "Your Domino's order is out for delivery with our delivery expert Amit.", S),
        c("VK-FIRSTC-S", "Your Lenskart order is ready! Your glasses will be delivered by 9 Oct.", S),
        c("AD-PHARME-S", "Your PharmEasy order #PE12345 has been delivered. Get well soon!", S),
        c("JD-1MGTAB-S", "Your Tata 1mg order is packed and will be shipped today.", S),
        c("VM-IKEAIN-S", "Your IKEA order delivery is scheduled for 10 Oct between 9 AM and 1 PM.", S),

        // ---- Offers from shops, food, telecom, everyone ---------------------------------------------
        c("AX-AMAZON-S", "Great Indian Festival is LIVE! Up to 80% off on electronics. Shop now: amzn.in/gif", F),
        c("JM-FLPKRT-S", "Big Billion Days: Lowest prices of the year on mobiles. Starts at midnight!", F),
        c("VK-MYNTRA-S", "Your favourites are now 50% off! Hurry, sale ends tonight. myntr.it/abc", F),
        c("AD-SWIGGY-S", "Craving biryani? Get flat 60% off up to Rs 120 on your next order. Use code BIRYANI60.", F),
        c("JD-ZOMATO-S", "Hungry? Order from top restaurants and get free delivery today. Order now!", F),
        c("VM-ZEPTON-S", "Sale is live! Fresh fruits at 50% off. Delivered in 10 mins. Order now on Zepto.", F),
        c("AX-BLNKIT-S", "Diwali essentials at the best prices. Order on Blinkit and get it in minutes!", F),
        c("JM-JIOINF-S", "Jio True 5G Unlimited! Recharge with Rs 349 plan and enjoy unlimited 5G data. Recharge now.", F),
        c("VK-AIRTEL-S", "Get 3 months of Disney+ Hotstar free with Airtel Rs 499 plan. Recharge now.", F),
        c("AD-VIINDI-S", "Vi Hero Unlimited: Binge all night with free data from 12 AM to 6 AM. Recharge now.", F),
        c("JD-NYKAAF-S", "Pink Friday Sale: Up to 50% off + free gifts on orders above Rs 999.", F),
        c("VM-UBERIN-S", "Ride for less this weekend! 30% off on your next 3 Uber rides. Use code WEEKEND30.", F),
        c("AX-MKMTRP-S", "Flights from Rs 1,299! MakeMyTrip festive sale. Book now.", F),
        c("JM-BMSHOW-S", "Stree 3 is now showing! Book tickets now and get Rs 100 off with BookMyShow.", F),
        c("VK-PVRCIN-S", "Tuesday offer: Movie tickets at Rs 99 at PVR. Book now.", F),
        c("AD-DMART1-S", "DMart Ready: Free delivery on your first order above Rs 1000.", F),
        c("JD-RELDIG-S", "Reliance Digital Festival of Electronics: Up to 25% off on TVs. Visit your nearest store.", F),
        c("VM-PUMAIN-S", "PUMA End of Season Sale is LIVE. Flat 40% off. Shop now.", F),
        c("AX-LENSKT-S", "Buy 1 Get 1 Free on all eyeglasses at Lenskart. Limited time offer!", F),
        c("JM-DOMINO-S", "Any 2 medium pizzas at Rs 499 only. Order now! T&C apply.", F),
        c("VK-KFCIND-S", "Bucket offer: 8 pc chicken at Rs 599. Order now on KFC app.", F),
        c("AD-URBNCO-S", "AC service at Rs 499 only! Book now on Urban Company before the summer rush.", F),
        c("JD-PLYSTR-S", "Download the new Paytm app and get Rs 100 cashback on your first bill payment.", F),
        c("VM-PHONPE-S", "Get up to Rs 200 cashback on your electricity bill payment with PhonePe. T&C", F),
        c("AX-CREDIN-S", "Pay your rent with CRED and earn up to 1% cashback. Try now!", F),
        c("JM-SBICRD-S", "Book your flight tickets with SBI Card and get flat Rs 1500 off. Use code SBIFLY.", F),
        c("VK-HDFCBK-S", "Festive Treats are here! Up to 10% cashback on electronics with HDFC Bank cards. Know more.", F),
        c("AD-ICICIB-S", "Pay with ICICI Bank Credit Card on Amazon and get 10% instant discount. Valid till 31 Oct.", F),
        c("JD-LICHFL-S", "Home loans at 8.5% p.a. from LIC Housing Finance. Apply now and get instant approval.", F),
        c("VM-POLICY-S", "Save up to 25% on car insurance renewal with Policybazaar. Compare plans now.", F),
        c("AX-BYJUS1-S", "Free trial class for your child with BYJU'S! Book now.", F),
        c("JM-UNACAD-S", "Crack UPSC with Unacademy. Get 50% off on Plus subscription. Hurry!", F),
        c("VK-HOTSTR-S", "India vs Australia LIVE on JioHotstar! Subscribe now at Rs 149.", F),
        c("AD-TATANU-S", "Tata Neu: Get 5% NeuCoins on every purchase. Shop now.", F),
        c("JD-MEESHO-S", "Mega Blockbuster Sale on Meesho! Products starting at Rs 99. Shop now.", F),
        c("VM-SPNCER-S", "Weekend bonanza at Spencer's! Buy 2 get 1 free on groceries.", F),
        c("AX-OLAELC-S", "Ola S1 Pro at an unbelievable price this Diwali. Book a test ride now!", F),

        // ---- -P headers: always offers ------------------------------------------------------------
        c("AX-HDFCBK-P", "Dear Customer, get instant personal loan in 10 seconds on HDFC Bank. Apply now.", F),
        c("JM-SBIINB-P", "Open SBI PPF account online on YONO. Save tax under 80C.", F),
        c("VK-AIRTEL-P", "Airtel Black: One bill for mobile, DTH and fibre. Call 121 to know more.", F),
        c("AD-AMAZON-P", "Your order is waiting! Complete your purchase and get extra 10% off.", F),
        c("JD-ZOMATO-P", "Your order is on the way? No! But your favourite dish is 40% off now.", F),
        c("VM-JIOINF-P", "Your recharge is due. Recharge with Rs 299 and get 2GB/day.", F),

        // ---- Travel, rides, appointments, government, telecom -> Updates ----------------------------
        c("AX-IRCTCI-S", "PNR:4521367890, TRAIN:12951, DOJ:06-10-26, SL, NDLS-MMCT, Dep:16:55, B2 34. Have a safe journey.", CategoryGroup.TRAVEL),
        c("JM-INDIGO-S", "Web check-in is open for your IndiGo flight 6E 2134 DEL-BOM on 06 Oct. Check in now.", CategoryGroup.TRAVEL),
        c("VK-AIRIND-S", "Your Air India flight AI 865 is delayed. New departure time 19:30. Sorry for the inconvenience.", CategoryGroup.TRAVEL),
        c("AD-AKASAA-S", "Gate change: Your Akasa Air flight QP 1123 will now depart from Gate 34.", CategoryGroup.TRAVEL),
        c("JD-OLACAB-S", "Your Ola driver Ramesh is arriving in 2 mins. Cab KA03MN4567, white Swift Dzire.", CategoryGroup.TRAVEL),
        c("VM-UBERIN-S", "Your Uber driver Sunil has arrived. Look for a grey WagonR DL1CA1234.", CategoryGroup.TRAVEL),
        c("AX-RAPIDO-S", "Your Rapido captain is arriving in 3 mins. Bike: KA05XY1234.", CategoryGroup.TRAVEL),
        c("JM-REDBUS-S", "Your bus from Bangalore to Goa departs at 21:00 from Madiwala. Ticket TKT12345.", U),
        c("VK-MKMTRP-S", "Your hotel booking at Taj Fort Aguada is confirmed for 10-12 Oct. Booking ID MMT1234.", CategoryGroup.TRAVEL),
        c("AD-APOLLO-S", "Reminder: Your appointment with Dr Sharma at Apollo Clinic is tomorrow at 11:00 AM.", U),
        c("JD-PRACTO-S", "Your video consultation with Dr Mehta starts in 15 minutes. Join from the Practo app.", U),
        c("VM-UIDAI-G", "Your Aadhaar update request has been processed. Download e-Aadhaar from myaadhaar.uidai.gov.in", U),
        c("AX-EPFOHO-G", "Dear member, your PF contribution of Rs 3,600 for Sep-26 has been credited. UAN 100123456789.", T, U),
        c("JM-ITDEPT-G", "Your income tax return for AY 2026-27 has been processed. Refund of Rs 4,320 has been issued.", T, U),
        c("VK-GSTIND-G", "Your GSTR-3B for September 2026 has been filed successfully.", U),
        c("AD-ECISVP-G", "Your voter ID application has been approved. EPIC number ABC1234567.", U),
        c("JD-PASPRT-G", "Your passport application ARN 26-1234567 is under police verification.", U),
        c("VM-MPARIV-G", "Your vehicle DL01AB1234 PUC certificate expires on 12-Oct-26. Renew to avoid penalty.", U),
        c("AX-DLTRAF-G", "E-challan of Rs 500 issued for vehicle DL01AB1234 for overspeeding. Pay at echallan.parivahan.gov.in", T, U),
        c("JM-JIOINF-S", "You have consumed 100% of your daily 2GB data. Speed reduced to 64 Kbps till midnight.", U),
        c("VK-AIRTEL-S", "Your Airtel pack will expire in 2 days. Recharge to continue enjoying services.", U, F, T),
        c("AD-VIINDI-S", "Your Vi number has been ported successfully. Welcome to Vi!", U),
        c("JD-GOOGLE", "Your Google Account password was changed. If you didn't do this, secure your account now.", U),
        c("VM-WHTSAP-S", "A new device has logged in to your WhatsApp account.", U),
        c("AX-SWIGGY-S", "Your Swiggy One membership has been renewed. Enjoy free deliveries for 3 months.", U, T),
        c("JM-NETFLX-S", "Your Netflix membership will renew on 10 Oct. Rs 649 will be charged to your card.", T, U),
        c("VK-CBSEIN-G", "CBSE Class 12 results are out. Check at results.cbse.nic.in", U),
        c("AD-BSESRJ-S", "Power supply in your area will be interrupted on 06 Oct from 10 AM to 2 PM for maintenance.", U),
        c("JD-HPCLGS-S", "Your HP Gas cylinder booking is confirmed. Booking no 123456. Delivery in 2 days.", U, S),
        c("VM-IOCLPG-S", "Your Indane LPG cylinder has been delivered. Cash memo no 7654321.", U, S),
        c("AX-COWIN1-G", "Your vaccination certificate is ready. Download from cowin.gov.in", U),
        c("JM-ABHAIN-G", "Your ABHA health ID has been created successfully. ABHA no 12-3456-7890-1234.", U),
        c("VK-NHAIIN-G", "Your FASTag KYC is incomplete. Complete KYC by 31 Oct to avoid deactivation.", U),

        // ---- Insurance & loans: notices vs money --------------------------------------------------------
        c("AD-HDFCER-S", "Your HDFC ERGO health policy 123456 has been issued. Policy copy sent to your email.", U),
        c("JD-ICICIL-S", "Your ICICI Lombard motor claim 12345 has been approved. Amount Rs 18,000 will be credited in 3 days.", T, U),
        c("VM-STARHL-S", "Your Star Health policy will expire on 15-Oct-26. Renew now to stay covered.", U, T, F),
        c("AX-BAJAJF-S", "Your loan account 4XX123 has been closed successfully. NOC will be sent to your email.", U),
        c("JM-TATACP-S", "Your home loan application has been approved for Rs 45,00,000. Our executive will contact you.", U, T),

        // ---- Hindi and Hinglish -------------------------------------------------------------------------
        c("AD-SBIINB-S", "प्रिय ग्राहक, आपके खाते XX1234 से ₹500 डेबिट किए गए हैं। उपलब्ध शेष ₹12,000 -SBI", T),
        c("VM-PNBSMS-S", "आपके खाते XX7777 में ₹15,000 जमा किए गए। -PNB", T),
        c("JD-HDFCBK-S", "आपका ओटीपी 482913 है। इसे किसी के साथ साझा न करें।", O),
        c("AX-JIOINF-S", "धमाका ऑफ़र! ₹299 के रिचार्ज पर पाएं 2GB/दिन। अभी रिचार्ज करें।", F),
        c("JM-FLPKRT-S", "आपका ऑर्डर डिलीवर हो गया है। धन्यवाद!", S),
        c("VK-SBIINB-S", "SBI कभी भी आपसे OTP, PIN या पासवर्ड नहीं मांगता। धोखेबाज़ों से सावधान रहें।", U),
        c("AD-MEESHO-S", "Sirf aaj! Har product Rs 99 mein. Abhi order karo!", F),
        c("JD-SWIGGY-S", "Bhook lagi hai? 50% off on first order. Order karo abhi!", F),
        c("+919811117777", "Bhai kal ka plan pakka? 8 baje milte hain", P),
        c("+919811118888", "Maine 2000 bhej diye hain GPay pe, check kar lena", P),
        c("+919811119999", "Mummy ko bol dena main late aaunga aaj", P),

        // ---- People (never filed as business) -----------------------------------------------------------
        c("+919811110001", "Hey, are you free for lunch tomorrow?", P),
        c("+919811110002", "Happy birthday! Have a great year ahead 🎉", P),
        c("+919811110003", "Can you send me the OTP that came on your phone? Need it for the booking", P),
        c("+919811110004", "The parcel reached, thanks for sending it", P),
        c("+919811110005", "Meeting moved to 4 PM, see you there", P),
        c("+919811110006", "Paid the electricity bill, you can relax", P),
        c("+919811110007", "Sale at Lifestyle this weekend, let's go?", P),
        c("+919811110008", "Call me when you reach home", P),
        c("+919811110009", "Did you get the money I transferred?", P),
        c("+919811110010", "Running 10 mins late, sorry!", P),
    )

    @Test
    fun filedByWhatTheMessageIs() {
        val misses = cases.mapNotNull { (from, text, want) ->
            val contact = from.startsWith("+")
            val a = analyzer.analyze(MessageInput(text, SenderInfo(from, isContact = contact), 1_790_000_000_000L))
            val g = a.category.group
            if (g in want) null else "$from: got ${a.category} ($g) want $want — ${a.classification.reasons} — “${text.take(90).replace('\n', ' ')}”"
        }
        assertWithMessage("Misfiled ${misses.size}/${cases.size}:\n" + misses.joinToString("\n")).that(misses).isEmpty()
    }
}
