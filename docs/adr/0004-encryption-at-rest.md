# 0004 — Encryption at rest: SQLCipher + Tink + Android Keystore

- **Date:** 2026-10-16
- **Status:** Accepted

## Context
The app stores health data, body photos and private conversations. If the phone is lost, rooted, or backed up, that data must stay unreadable.

## Decision
- **Database:** Room on top of SQLCipher for Android (Zetetic's current `sqlcipher-android` artifact, not the deprecated `android-database-sqlcipher`). The 256-bit DB passphrase is random, generated once, and stored encrypted with Tink AEAD, whose keyset is wrapped by a hardware-backed Android Keystore key.
- **Photos and other files:** encrypted with Tink streaming AEAD (AES-GCM-HKDF) in app-private storage; never written to the shared gallery.
- **Preferences:** DataStore, with sensitive values Tink-encrypted.
- **App lock:** androidx.biometric (biometric or device credential) on launch and after a timeout.
- `android:allowBackup="false"` and data-extraction rules that exclude everything; a password-protected encrypted export is the only backup path (later phase).
- `androidx.security:security-crypto` is **not** used — it is deprecated; Tink is used directly.

## Consequences
- If the owner wipes the phone or clears app data without an export, data is gone. That is intended.
- SQLCipher adds a few MB to the APK and slight query overhead — negligible for this data size.
