# Yarn

A local-first SMS/MMS messenger for Android with private, on-device intelligence.

* **Messaging:** SMS & MMS (group MMS, attachments), dual-SIM, delivery reports, scheduled
  messages, undo send, drafts, offline queue with automatic retry when signal returns,
  direct reply from notifications, quick responses from the dialer, share-to-Yarn.
* **Organisation:** automatic categories (Personal, Work, OTP, Banking, Payments, Bills,
  Deliveries, Shopping, Travel, Updates, Promotions), Important view, pin/star/archive/mute,
  full multi-select on chats and messages, swipe actions, encrypted full-text search.
* **Safety:** spam/scam/phishing detection with explanations, dangerous-link warnings,
  block numbers and keywords (also in Android's system block list).
* **Intelligence:** OTP copy, contextual actions (track package, add to calendar, call,
  directions), summaries, smart replies, rewriting, offline translation — all free and
  on-device by default. Learns from your corrections; you can review or reset everything.
* **Privacy:** encrypted database, app lock, screenshot blocking, encrypted backups you
  store wherever you like. No accounts, no servers, no analytics.

RCS is not supported because Android offers no public RCS API to third-party apps.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the design, Android limitations,
database schema and AI approach.

## Build

Requirements: JDK 17+, Android SDK with platform 36.

```bash
./gradlew assembleDebug        # debug APK
./gradlew test                 # all unit tests
./gradlew assembleRelease      # per-ABI release APKs (sign before installing)
./gradlew bundleRelease        # App Bundle for Play
```

minSdk 29 (Android 10), targetSdk 36.

## Optional on-device language model

Summaries and rewriting use Gemini Nano automatically on phones that support it. On other
phones you can import a free open model (Settings › AI & learning › *Your own on-device
model*), e.g. Gemma 3 1B IT or Qwen 2.5 1.5B Instruct in MediaPipe `.task`/`.litertlm`
format. Without either, Yarn still provides extractive summaries, smart replies, extraction,
categories and spam protection.

## Status

The code compiles, the APK builds, and the unit tests pass (core modules and Room SQL).
Telephony paths (sending/receiving SMS/MMS, carrier MMS behaviour, dual-SIM) need testing on
real devices with SIM cards across carriers before release — they cannot run on an
emulator-free CI. UI strings are currently in code; extract them to `strings.xml` before
localising.
