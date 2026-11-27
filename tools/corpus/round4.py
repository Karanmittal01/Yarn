"""Round four of the corpus: one of each format from a 5,000-message test inbox that Yarn filed
wrongly before (prize and KYC scams from unregistered sender IDs, "unlocked a benefit" offers from
6-digit promotional numbers, cancelled orders, card mandates), plus notices that High filtering
once caught. Called from make_sms_corpus.py; senders are used exactly as written.
"""

ROWS = [
    ('AD-CREDIT', 'X', 'Refund pending! Click https://bank.example/p3vq6j and enter your UPI PIN to receive money.'),
    ('JD-REWARD', 'X', 'Dear customer, suspicious activity detected. Call our fake support desk at +91 9042934962 now.'),
    ('110242', 'F', 'CRED: Congratulations! You have unlocked a personalised benefit. Sign in to your account to view details.'),
    ('VK-KYCUPD', 'X', 'Congratulations! Your mobile number has won Rs.8 lakh in a lucky draw. Claim now: https://bank.example/9cvfky'),
    ('VK-KYCUPD', 'X', 'Police case registered against your ID. Download the attached verification app: https://claim.example/6fi9h5'),
    ('110123', 'F', 'BookMyShow: Congratulations! You have unlocked a personalised benefit. Sign in to your account to view details.'),
    ('VK-KYCUPD', 'X', 'SEBI/RBI notice: Your investment account requires re-verification. Submit PAN and bank details: https://example.com/pyg9ly'),
    ('AD-ALERTS', 'X', 'Part-time work from home! Earn Rs.6817 daily. No experience required. WhatsApp +91 9076826964.'),
    ('550763', 'F', 'Swiggy: Congratulations! You have unlocked a personalised benefit. Sign in to your account to view details.'),
    ('AD-CREDIT', 'X', 'Your account has been flagged. Share OTP 453247 with our verification executive to prevent suspension.'),
    ('JD-WINNER', 'X', 'Loan approved instantly up to Rs.460,301. Pay a refundable processing fee to release funds: https://claim.example/gjlgzt'),
    ('AD-PAYNOW', 'X', 'Your credit card reward points will expire today. Redeem using https://example.com/9424n7'),
    ('660709', 'F', 'Amazon: Congratulations! You have unlocked a personalised benefit. Sign in to your account to view details.'),
    ('JD-REWARD', 'X', 'Your KYC has failed. Send Aadhaar, PAN and card details to complete verification.'),
    ('+918809582901', 'X', 'Your KYC has failed. Send Aadhaar, PAN and card details to complete verification.'),
    ('MEESHO', 'S', 'Zomato: Order ODRH8RAZAFLUOV cancelled as requested. Any prepaid amount will be refunded as per policy.'),
    ('660865', 'F', 'Axis Bank: Congratulations! You have unlocked a personalised benefit. Sign in to your account to view details.'),
    ('JD-REWARD', 'X', 'Your FASTag/KYC is expiring. Verify vehicle details immediately: https://bank.example/e8ps1m'),
    ('120263', 'F', 'Jio: Congratulations! You have unlocked a personalised benefit. Sign in to your account to view details.'),
    ('JD-WINNER', 'X', 'Electricity disconnection notice: supply will stop tonight. Clear pending bill at https://bank.example/4r0kbt'),
    ('INDPOST', 'T|U', 'Traffic notice: Challan number 5659483005 has been generated for vehicle DL79AB5361. Verify through official portal.'),
    ('TATACL', 'S', "Domino's: Order ODVFQELEWH4WSC cancelled as requested. Any prepaid amount will be refunded as per policy."),
    ('AD-CREDIT', 'X', 'Your SIM will be deactivated in 2 hours. Press 1 or call +91 8890876629 to speak to an executive.'),
    ('FLIPKT', 'S', 'Croma: Order ODRR31WVKWW86H cancelled as requested. Any prepaid amount will be refunded as per policy.'),
    ('+916174493093', 'X', 'Electricity disconnection notice: supply will stop tonight. Clear pending bill at https://track.example/ny41o3'),
    ('AD-CREDIT', 'X', 'Investment tip: Guaranteed 5% daily returns. Join our private group at https://claim.example/ddrst9'),
    ('+918692280216', 'X', 'Police case registered against your ID. Download the attached verification app: https://secure.example/velfow'),
    ('BP-KOTAKB', 'U', 'Standing instruction on card XX8865 for IRCTC of Rs.499 has been cancelled.'),
    ('TM-FEDBNK-S', 'U', 'Standing instruction on card XX1774 for CROMA of Rs.25,000 has been cancelled.'),
    ('ZOMATO', 'S', 'Nykaa: Order ODJCK7VDWG1GFB cancelled as requested. Any prepaid amount will be refunded as per policy.'),
    ('AD-CREDIT', 'X', 'Your parcel is on hold due to an incomplete address. Pay Rs.25 redelivery fee here: https://example.com/l5scmf'),
    ('BIGBKT', 'S', 'Swiggy: Order ODWRXNEW995QAR cancelled as requested. Any prepaid amount will be refunded as per policy.'),
    ('VK-KYCUPD', 'X', 'URGENT: Your bank account will be blocked today. Update KYC immediately: https://claim.example/ewgleu'),
    ('SPOTIFY', 'U', "We couldn't process your subscription payment. Update your payment method through the official app."),
    ('PRIMEV', 'U', "We couldn't process your subscription payment. Update your payment method through the official app."),
    ('NETFLX', 'U', "We couldn't process your subscription payment. Update your payment method through the official app."),
]


def build(add_once):
    for sender, want, text in ROWS:
        add_once(want, ["=" + sender], [text], 1)
