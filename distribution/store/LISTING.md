# Yarn — Google Play listing

The **Store listing** workflow (`.github/workflows/store-listing.yml`) publishes the app name,
short and full description below and the phone screenshots in this folder to Play whenever they
change on `main`, or when run by hand. Everything else on this page is filled in by hand in
**Play Console → Yarn → Grow users → Store presence**.

To redo the screenshots after a UI change (made-up people and numbers only):
```
./gradlew :app:testDebugUnitTest --tests '*UiSnapshotTest.store*'
python tools/store/frame.py        # needs Pillow; writes screenshot-1.png … screenshot-8.png
```

## App details

**App name** (max 30)
```
Yarn - AI Messaging
```

**Short description** (max 80)
```
AI SMS app that sorts OTPs, bank alerts, orders and offers, and stops scams.
```

**Full description** (max 4000)
```
Yarn is a fast, private SMS and MMS app with on-device AI that keeps your inbox calm.

FREE TO USE. YARN PRO FREE FOR 30 DAYS.
Yarn is free: texting, OTPs with one-tap Copy, translation, smart replies, app lock and Google Drive backup. Every new install also gets Yarn Pro free for 30 days. After that, Yarn stays free with the All and OTP tabs, or keep Pro for ₹199 a year or ₹999 once for life (early-bird prices; regular ₹499 a year and ₹2,999). The yearly plan renews until you cancel in Google Play. No ads, ever.

YOUR INBOX, SORTED (PRO)
Every message is filed into Personal, OTP, Transactions, Shopping, Travel, Updates or Offers, so messages from people you care about are never buried under alerts and ads.
• OTP: one-time codes in one place, with one-tap Copy (free)
• Transactions: bank debits and credits, card spends, UPI, bills and investments
• Shopping: orders, food and grocery deliveries, refunds and couriers
• Travel: trains, flights, cabs and office pickups
• Updates: appointments, account changes and service notices
• Offers: sales and marketing, kept out of your way

BUILT FOR INDIA
Yarn names about 1,000 Indian banks, shops, apps, couriers, telecoms and government services instead of sender codes like VM-ABCDEF-S, and with Pro shows their logos. Missing a logo? Long-press the sender's picture to ask for it. Yarn understands the -P, -S, -T and -G sender tags and is available in English and Hindi.

SPAM AND SCAM PROTECTION (PRO)
Fake KYC alerts, prize scams, parcel-fee tricks, instant-loan bait and dangerous links are spotted and moved to Spam. Yarn warns you before you open a risky link. Nothing is ever deleted silently.

ON-DEVICE AI
• Smart replies suggested in every chat
• Translate any message with one tap, including Hinglish
• Pro: summarise long chats and rewrite your message to sound shorter, friendlier or more professional, on phones with Gemini Nano or with a free model you add
• Sorting learns from your corrections, on your phone only

CHATS THAT JUST WORK
• Delivered ticks you can rely on
• Dual SIM: pick the SIM for every message
• Photos, videos and group messages
• Copy a code or mark a message as read straight from the notification
• Pro: scheduled sending, and choose how each tab notifies you

MAKE IT YOURS (PRO)
Pick an accent colour for buttons and your message bubbles, and a wallpaper for your chats.

CLEAN-UP AND RECYCLE BIN
Clear old codes and offers in a tap, or with Pro let Yarn do it automatically. Deleted messages wait in the recycle bin for 7 days, or with Pro for 30 days or forever.

PRIVATE BY DESIGN
• Messages are stored in an encrypted database on your phone
• Sorting, spam protection and smart features run on your phone
• No ads, no analytics, no Yarn servers
• App lock with fingerprint, face or screen lock
• When your phone can't translate a message by itself, only that message is sent to Google Translate, and only when you tap Translate. You can turn this off.

BACKUP TO YOUR GOOGLE DRIVE
Back up your messages, encrypted, to a private folder in your own Google Drive whenever you like, or every day automatically with Pro, and restore them on a new phone by signing in. Backups use your Google storage.

Yarn works with SMS and MMS. Android does not allow third-party apps to use RCS chat.
```

**App category:** Communication
**Tags:** SMS, Messaging, Spam blocker
**Contact email:** your support email
**Website:** https://karanmittal01.github.io/Yarn/
**Privacy policy:** https://karanmittal01.github.io/Yarn/privacy.html

## Graphics
| Field | File |
|---|---|
| App icon (512×512) | `../play-store-icon-512.png` |
| Feature graphic (1024×500) | `feature-graphic.png` |
| Phone screenshots (1080×1920) | `screenshot-1.png` … `screenshot-8.png` (published by the workflow) |

## App content (Policy and programmes → App content)

**Privacy policy:** https://karanmittal01.github.io/Yarn/privacy.html

**Ads:** No, my app does not contain ads.

**Sign-in details** (formerly "App access"): **No**, no part of the app is restricted. (No Yarn account exists; Google sign-in is optional and only for backup; App lock is off by default.)

**Content rating:** start the questionnaire, category **All other app types / Communication**.
- Violence, sexuality, language, controlled substances, gambling: **No** to all
- Does the app allow users to interact or exchange content? **Yes** (it sends and receives SMS/MMS)
- Does the app share the user's location with other users? **No**
- Does the app allow users to purchase digital goods? **Yes** (Yarn Pro, through Google Play Billing)

**Target audience:** 18 and over.

**News app:** No. **Government app:** No. **Financial features:** none (Yarn shows bank SMS but offers no financial services). **Health:** No.

**Data safety**
- Does your app collect or share any of the required user data types? **Yes**
- Is all user data encrypted in transit? **Yes**
- Do you provide a way for users to request that their data is deleted? **Yes** (turn off backup and delete Yarn's hidden app data in Google Drive; uninstalling deletes on-device data)
- Data types collected (only when the user turns on Google Drive backup):
  - **Messages → SMS or MMS**: Collected, not shared, optional, purpose **App functionality** (backup and restore)
  - **Photos and videos**: Collected, not shared, optional, purpose **App functionality** (your profile photo, and message photos/videos only if "Include photos & videos" is on)
  - **Personal info → Name**: Collected, not shared, optional, purpose **App functionality** (the profile name, so it comes back on a new phone)
  - **Personal info → Email address**: Collected, not shared, optional, purpose **App functionality / Account management** (shows the backup account)
  - **App activity → Other user-generated content**: Collected, not shared, optional, purpose **App functionality** (chat drafts and settings in the backup)
  - **Messages → SMS or MMS** also go to Google Translate, but only the one message the user taps Translate on, and only when the phone can't translate it offline ("Translate online when needed", on by default, can be turned off). This is user-initiated, so it is not "shared"; it is processed ephemerally.
- Messages, contacts and everything else processed on the phone are not "collected", because they never leave the device.
- **Purchases** go through Google Play Billing: Google collects the payment details, not Yarn, so nothing extra is declared for them.
- **Feedback** (WhatsApp or email) is sent by the user from their own app, so it isn't collected by Yarn either.
- **Logo requests** (Pro): after the user confirms, Yarn itself texts the sender ID to the developer, which reveals the user's phone number. Declare **Personal info → Phone number**: Collected, not shared, optional, purpose **App functionality** (to receive logo requests). Not processed ephemerally.
- The **trial's start date** is kept on the phone, in Google Block Store and (with backup on) in the user's own Drive app folder; none of it reaches the developer, so nothing is declared for it.

**Sensitive permissions → SMS and Call Log permissions**
- Core functionality: **Default SMS handler**
- Description: "Yarn is a full replacement SMS/MMS app. It must be the default SMS handler to read, send, receive, store and delete the user's messages."
- If asked for a video: record a short screen recording (Yarn asking to become the default SMS app, then the inbox and sending a message), upload it to YouTube as Unlisted, and paste the link.

## After filling everything
Go to **Publishing overview → Send changes for review**. Once Google approves (usually a few days for a new app), the Play page shows the name, icon, screenshots and description instead of "app.yarn.messages (unreviewed)".
