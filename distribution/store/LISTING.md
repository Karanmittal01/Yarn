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

YOUR INBOX, SORTED
Every message is filed into Personal, OTP, Transactions, Shopping, Travel, Updates or Offers, so messages from people you care about are never buried under alerts and ads.
• OTP: one-time codes in one place, with one-tap Copy and automatic clean-up of old codes
• Transactions: bank debits and credits, card spends, UPI, bills and investments
• Shopping: orders, food and grocery deliveries, refunds and couriers
• Travel: trains, flights, cabs and office pickups
• Updates: appointments, account changes and service notices
• Offers: sales and marketing, kept out of your way

BUILT FOR INDIA
Yarn shows the names and logos of about 1,000 Indian banks, shops, apps, couriers, telecoms and government services instead of sender codes like VM-ABCDEF-S. It understands the -P, -S, -T and -G sender tags, so a sale from your grocery app lands in Offers while your order update lands in Shopping. Yarn is available in English and Hindi.

SPAM AND SCAM PROTECTION
Fake KYC alerts, prize scams, parcel-fee tricks, instant-loan bait and dangerous links are spotted and moved to Spam. Yarn warns you before you open a risky link. Nothing is ever deleted silently.

ON-DEVICE AI
• Smart replies suggested in every chat
• Translate any message with one tap, including Hinglish
• Summarise long chats and rewrite your message to sound shorter, friendlier or more professional, on phones with Gemini Nano or with a free model you add
• Sorting learns from your corrections, on your phone only

CHATS THAT JUST WORK
• Delivered ticks you can rely on
• Dual SIM: pick the SIM for every message
• Scheduled sending, photos, videos and group messages
• Copy a code or mark a message as read straight from the notification
• Choose how each tab notifies you, for example keep Offers silent

RECYCLE BIN
Deleted messages wait in the recycle bin for 7 days, or as long as you choose. Search it and restore what you need in a tap.

EVERYTHING ONE TAP AWAY
Tap your photo for starred, archived, spam and blocked chats, your SIMs and every setting, in one short menu.

PRIVATE BY DESIGN
• Messages are stored in an encrypted database on your phone
• Sorting, spam protection and smart features run on your phone
• No ads, no analytics, no Yarn servers
• App lock with fingerprint, face or screen lock
• When your phone can't translate a message by itself, only that message is sent to Google Translate, and only when you tap Translate. You can turn this off.

BACKUP TO YOUR GOOGLE DRIVE
Optionally back up your messages, encrypted, to a private folder in your own Google Drive, and restore them on a new phone by signing in. Backups use your Google storage and are free.

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
- Does the app allow users to purchase digital goods? **No**

**Target audience:** 18 and over.

**News app:** No. **Government app:** No. **Financial features:** none (Yarn shows bank SMS but offers no financial services). **Health:** No.

**Data safety**
- Does your app collect or share any of the required user data types? **Yes**
- Is all user data encrypted in transit? **Yes**
- Do you provide a way for users to request that their data is deleted? **Yes** (turn off backup and delete Yarn's hidden app data in Google Drive; uninstalling deletes on-device data)
- Data types collected (only when the user turns on Google Drive backup):
  - **Messages → SMS or MMS**: Collected, not shared, optional, purpose **App functionality** (backup and restore)
  - **Photos and videos**: Collected, not shared, optional, purpose **App functionality** (only if "Include photos & videos" is on)
  - **Personal info → Email address**: Collected, not shared, optional, purpose **App functionality / Account management** (shows the backup account)
  - **Messages → SMS or MMS** also go to Google Translate, but only the one message the user taps Translate on, and only when the phone can't translate it offline ("Translate online when needed", on by default, can be turned off). This is user-initiated, so it is not "shared"; it is processed ephemerally.
- Messages, contacts and everything else processed on the phone are not "collected", because they never leave the device.

**Sensitive permissions → SMS and Call Log permissions**
- Core functionality: **Default SMS handler**
- Description: "Yarn is a full replacement SMS/MMS app. It must be the default SMS handler to read, send, receive, store and delete the user's messages."
- If asked for a video: record a short screen recording (Yarn asking to become the default SMS app, then the inbox and sending a message), upload it to YouTube as Unlisted, and paste the link.

## After filling everything
Go to **Publishing overview → Send changes for review**. Once Google approves (usually a few days for a new app), the Play page shows the name, icon, screenshots and description instead of "app.yarn.messages (unreviewed)".
