# Shliach Tzibbur

An open-source Android client for the **Tzibbur** group-messaging service
(`https://api.tzibbur.me`). Built to run well on modern phones and on older,
non-touch, small-screen devices alike — no Google Play Services required.

## Features

- Phone (SMS one-time-code) sign-in, with SIM-number prefill and code auto-detect
- Group list and creation; per-group message threads with a durable send outbox
- Live delivery over a WebSocket, with a WorkManager `/v1/pending` fallback
- Group settings, member management, and a contact picker backed by `/v1/contacts/check`
- Contacts screen: see who's on Tzibbur, add them to groups, invite the rest
- User settings (profile, devices, legal), app settings (theme, language, notifications)
- Local message notifications via an optional foreground service
- 5 languages (English, Yiddish, Hebrew, Arabic, Spanish; RTL-aware)
- Light / dark / follow-system theme
- Offline-capable: groups, messages and members are cached in Room

## Tech

Kotlin · Jetpack Compose (Material 3) · single Gradle module · MVVM with
`StateFlow` · manual DI (`AppContainer`) · Room · DataStore · WorkManager ·
OkHttp (HTTP + WebSocket) · kotlinx.serialization. `minSdk 24`.

## Build

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # JVM unit tests
```

The API is documented in [`tzibbur-api.md`](tzibbur-api.md). A full walkthrough
of the packages and classes is in [`CODEBASE.md`](CODEBASE.md); the changelog is
in [`VERSION.md`](VERSION.md).

## Status

Early development. The UI has not yet been verified on a device/emulator, and a
few permissions used for convenience (`RECEIVE_SMS`, `READ_SMS`) are restricted
on the Play Store. See "Known limitations" in `CODEBASE.md`.
