# Yarn — Architecture

Yarn is a local-first SMS/MMS messenger for Android with on-device intelligence.
This document explains how it is built, what Android allows, and why.

## 1. Modules

| Module | Type | Responsibility |
|---|---|---|
| `:app` | Android (Kotlin, Compose, Material 3) | UI, telephony integration, storage, notifications, workers |
| `:core:intelligence` | Pure Kotlin/JVM | Entity extraction, categorisation, spam/scam detection, learning, priority, summaries, quick replies |
| `:core:mms` | Pure Kotlin/JVM | WAP/MMS PDU encoder and parser (OMA-TS-MMS-ENC) |
| `:core:crypto` | Pure Kotlin/JVM | Streaming passphrase encryption for backups |

The three core modules have no Android dependencies, so they are fast to unit-test and
reusable (e.g. for a future desktop companion).

## 2. App layers (`:app`)

```
ui/ (Compose screens + ViewModels)
  └── data/repo (ConversationRepository, TelephonySync)
  └── messaging/ (MessageSender, IncomingProcessor, SendErrors, MediaTools)
  └── ai/ (IntelligenceEngine, ML Kit services, Gemini Nano, local LLM, AssistantService)
  └── telephony/ (TelephonyStore, SimManager, DefaultSmsApp, PhoneNumbers)
  └── data/db (Room + SQLCipher), data/prefs (DataStore), data/contacts
receivers/ (SMS_DELIVER, WAP_PUSH_DELIVER, sent/delivered/MMS callbacks, boot, headless send)
work/ (SendWorker, SyncWorker, MmsDownloadWorker, ReanalyzeWorker, MaintenanceWorker)
```

Dependency injection is a hand-written `AppContainer` (no reflection, no codegen, fast start).

### Message flow

**Incoming SMS:** `SMS_DELIVER` → `IncomingProcessor.onSms` → write to the system provider
(`content://sms`, required of the default SMS app) **and** our DB under one lock → analysis
(category, spam, entities, priority) → notification (MessagingStyle, direct reply, “Copy code”).
Class 0 (“flash”) SMS are shown and not stored; voicemail-indicator SMS are ignored.

**Incoming MMS:** `WAP_PUSH_DELIVER` → parse `M-Notification.ind` → placeholder row →
`SmsManager.downloadMultimediaMessage` into a FileProvider file → parse `M-Retrieve.conf` →
work out the real group thread (sender + To/Cc − own numbers) → persist parts to
`content://mms` → `M-Acknowledge.ind` → analyse/notify. Auto-download honours the roaming
setting; expired notifications show as expired; failures retry with backoff then ask the user.

**Outgoing:** every message is written to our DB *before* any radio work (`QUEUED`,
`SCHEDULED` or `DELAYED` for undo-send). `SendWorker` drains the queue:
* SMS → `divideMessage` + `sendMultipartTextMessage` with per-part sent/delivery intents.
  Callbacks carry the attempt number so stale callbacks are ignored.
* MMS (attachments, subject, group with group-MMS on, email recipients, or carrier
  SMS→MMS threshold) → `PduComposer.sendReq` (SMIL + parts, images recompressed to the
  carrier's `maxMessageSize`/dimensions) → `sendMultimediaMessage`.
* Failure classification (`SendErrors`): *no service / radio off* → `WAITING_FOR_SERVICE`
  (does not consume retries; retried with backoff and immediately when airplane mode turns
  off; gives up after 72 h); transient errors → retry (15 s, 1 m, 5 m, 15 m); permanent →
  `FAILED` + notification with Retry.
* A message stuck in `SENDING` (process killed before the callback) becomes
  “Status unknown” rather than being silently re-sent — avoiding duplicates.
* Scheduled messages use one `AlarmManager` alarm (exact if the user allowed it), re-armed
  on boot/update.

### Sync with the system store

`TelephonySync` imports `content://sms` and `content://mms` incrementally using its own
cursors (so history is never skipped if new messages arrive mid-import), streams rows in
batches of 400, and defers analysis to a background pass (newest first). A debounced
`ContentObserver` picks up changes made by other apps; a daily job reconciles deletions.
All provider writes and imports share one mutex, so imports never duplicate our own rows.

## 3. Android limitations (and what Yarn does about them)

| Topic | Reality | Yarn's approach |
|---|---|---|
| **RCS** | There is **no public RCS API** for third-party apps. RCS is only available to Google Messages (and carrier/OEM apps via private agreements). | Not implemented, and not faked. Yarn uses SMS/MMS. The UI says so in Settings › About. |
| Default SMS app | Only the default app receives `SMS_DELIVER`/`WAP_PUSH_DELIVER` and may write the SMS store. | Onboarding requests `ROLE_SMS`. Without it Yarn is read-only and shows a banner. |
| MMS transport | Uses the carrier's MMSC over **mobile data** (not Wi-Fi, except where the carrier supports it). | Clear error states (“Mobile data is off…”), retries, manual download. |
| Background limits | Doze/App Standby delay work. | Receivers use `goAsync`; WorkManager expedited jobs; default SMS apps get temporary allowlisting on delivery. |
| Exact alarms | Android 14+ requires user consent for exact alarms. | Falls back to `setAndAllowWhileIdle`; Settings links to the permission. |
| Own phone number | Often unavailable from the SIM. | Group-MMS self-exclusion is best-effort; 1-To heuristic for 1:1 MMS. |
| System block list | Writable only by default SMS/dialer/carrier apps. | Used when default; local block rules always apply. |
| Cross-device sync | Requires a server for real-time sync. | No server (zero cost): encrypted backups saved to any storage provider the user chooses (Drive, Dropbox, SD card…) and restored on another phone. |

## 4. Database (Room over SQLCipher)

The database is encrypted with SQLCipher. A random 256-bit passphrase is generated per
install and wrapped with a non-exportable Android Keystore AES-GCM key. If the key is lost
(restore onto another device) the cache is rebuilt from the system store. The database and
preferences are excluded from Auto Backup.

| Table | Purpose / key columns |
|---|---|
| `conversations` | `id` = OS thread id; `addresses`, cached `displayName`, `snippet`, `lastMessageAt`, `unreadCount`, `pinned/starred/archived/muted/blocked/spam`, `category` + `categoryLocked`, `importance`, `draft`, `preferredSubId` |
| `messages` | `conversationId`, `kind` (SMS/MMS), `providerId` (unique per kind), `status` lifecycle, retry fields (`attempts`, `nextAttemptAt`, `scheduledAt`, part counters), `subId`, AI fields (`category`, `categorySource`, `categoryReasons`, `spamVerdict`, `spamScore`, `spamReasons`, `riskyUrls`, `priority`, `important`), MMS fields (`mmsContentLocation`, `mmsTransactionId`, `mmsExpiry`, `mmsMessageId`) |
| `messages_fts` | FTS4 (unicode61) external-content index over `body`/`subject`, kept in sync by Room triggers |
| `attachments` | `messageId`, `mimeType`, `uri` (`content://mms/part/N` or private file), `fileName`, `size` |
| `message_entities` | Extracted OTPs, amounts, dates, links, tracking numbers… with `source` = rules / mlkit |
| `corrections` | Every user correction (category or spam) — the training data for learning |
| `sender_rules` | Sticky per-sender category and trust decisions |
| `block_rules` | Blocked numbers and keyword filters |

Message status values are persisted integers (`MessageStatus`) and must never be renumbered.

## 5. AI approach — free, on-device first

Every model and API Yarn uses is free with no per-use cost:

| Capability | Engine | Where it runs | Notes |
|---|---|---|---|
| Categories, priority | `:core:intelligence` rules + naive Bayes learned from corrections | On device, always | Explainable (“Why this category?”) |
| Spam / scam / phishing | Weighted signals (noisy-OR): urgency, credential requests, prize/parcel/toll/KYC scams, lookalike & leetspeak brand domains, punycode, shorteners, risky TLDs, brand-from-personal-number, homoglyphs, India TRAI sender suffixes | On device, always | Contacts never auto-filtered; risky links blocked behind a warning |
| Extraction | Regex/grammar extractor (OTP, money with direction, dates, links, UPI, tracking + carrier links, PNR/booking, orders, flights, phones, addresses) + **ML Kit Entity Extraction** | On device | ML Kit model (~5 MB) downloads once on Wi-Fi |
| Smart replies | **ML Kit Smart Reply** → built-in intent rules | On device | English; declines sensitive topics |
| Translation | **ML Kit Translate** + Language ID | On device | Language packs download once on Wi-Fi |
| Summaries / rewriting | **Gemini Nano** via ML Kit GenAI (supported phones) → **user-imported open model** (Gemma / Qwen via MediaPipe) → optional **self-hosted** OpenAI-compatible server (e.g. Ollama) → extractive TextRank summary | On device (server only if the user sets one up) | Engine shown on every result |

**Learning while keeping the user in control.** Moving a message/conversation to a
category or marking spam stores a correction; the model is rebuilt from corrections in
milliseconds. Near-duplicate templated messages (same sender, ≥50% bigram overlap) adopt
the user's label immediately; with more data a naive Bayes model blends into the rule
scores. Users can make a choice sticky per sender, review every correction and rule under
Settings › AI & learning, delete any of them, or reset learning entirely.

## 6. Security & privacy

* Encrypted DB (SQLCipher + Keystore), no analytics, no network for messaging.
* Encrypted backups: PBKDF2-SHA256 (310k iterations) → AES-256-GCM in 64 KiB chunks with
  chunk index + final flag authenticated (detects truncation, reordering, tampering).
* App lock (biometric or device credential), screenshot blocking (`FLAG_SECURE`),
  notification previews toggle and private lock-screen version.
* OTPs copied with the clipboard “sensitive” flag; optional OTP auto-delete.
* Optional self-hosted AI is off by default and only used for an explicit tap.

## 7. UI

Jetpack Compose + Material 3 with dynamic colour and dark/light themes. The inbox and
conversation show side by side on wide screens (tablets, unfolded foldables, landscape).
Lists use stable keys and Paging 3 for conversations of any size. Rows and bubbles provide
merged content descriptions, state descriptions and custom long-press labels for TalkBack.

## 8. Testing

* `:core:mms` — PDU round-trips, notification parsing, unknown-header skipping, truncation.
* `:core:crypto` — chunk boundaries, wrong passphrase, truncation, tampering.
* `:core:intelligence` — extraction, categories, scams vs legit OTP/bank alerts, learning.
* `:app` (Robolectric) — real Room schema: aggregates, FTS search, queue timing, uniqueness, cascades.

Run everything with `./gradlew test`.
