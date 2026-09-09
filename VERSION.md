# Version history

Version scheme: `MAJOR.MINOR`. MINOR increments for normal changes; MAJOR for
breaking or architectural changes. `versionCode` increments on every change and
stays in sync with `app/build.gradle.kts`.

---

## 0.1 — versionCode 2 — 2026-09-09

First implementation of the Shliach Tzibbur client on top of the Tzibbur API.

- **Auth**: SMS one-time-code login/registration (`/v1/auth/start`, `/v1/auth/verify`).
  Auth layer built around an `AuthMethod` abstraction so email / Google can be
  added later without reworking callers.
- **Groups**: list (Room-backed, offline-capable), create with category, pull-to-refresh.
- **Messages**: history with backward pagination, durable send via an outbox
  (store-before-send, reconcile by `clientMessageId`), live updates over the
  WebSocket while a conversation is open, "store then ack" delivery ordering.
- **Group settings**: rename, `whoCanPost` / `whoCanAddMembers` (admins),
  local mute, leave, delete.
- **Members**: list, add by phone number, promote/demote (with `last_admin`
  handling), remove.
- **User settings**: display-name edit, phone (read-only), email/Google
  placeholders, device list, privacy/terms viewer, sign out.
- **App settings**: theme (System/Light/Dark), language (System + English,
  Yiddish, Hebrew, Arabic, Spanish), notification toggle, background-sync
  service toggle.
- **Notifications**: `NotificationHelper` + channels; optional foreground
  `MessageSyncService` holding the WebSocket; `PendingSyncWorker` periodic
  `/v1/pending` poll as the fallback delivery path; `OutboxWorker`;
  `BootReceiver` to restore delivery after reboot.
- **Localisation**: 5 languages, per-app language via `AppCompatDelegate`
  application locales (+ `locales_config.xml` for the Android 13 system picker),
  RTL support for Yiddish / Hebrew / Arabic.
- **Theme**: full light + dark Material 3 colour schemes, follows system by
  default; no dynamic colour (brand consistency across old devices).
- **Compatibility**: `minSdk 24`; no Google Play Services / FCM dependency;
  non-touch / D-pad focus highlighting; small-screen-conscious layouts.
- **Architecture**: single module, package-by-feature, MVVM with
  `StateFlow`, manual DI via `AppContainer`, Room cache, DataStore for
  session + settings, OkHttp (HTTP + WebSocket), kotlinx.serialization.
- **Build**: added KSP + kotlinx-serialization plugins; `androidx.core` and the
  lifecycle/activity/navigation/room stack pinned to versions that build against
  `compileSdk 36.1` (the template's defaults require API 37, not yet released).
  Lint left advisory — the bundled lint crashes on this preview toolchain.
