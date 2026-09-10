# Version history

Version scheme: `MAJOR.MINOR`. MINOR increments for normal changes; MAJOR for
breaking or architectural changes. `versionCode` increments on every change and
stays in sync with `app/build.gradle.kts`.

---

## 0.8 — versionCode 9 — 2026-09-10

Conversation view upgrades.

- **Message rendering**: bare URLs, e-mails and phone numbers are auto-linked
  (links + phones underlined, links + e-mails blue) and open via `ACTION_VIEW`
  (`https:`, `mailto:`, `tel:`, `geo:`). New dependency-free `Linkify` +
  `MessageText`; `MarkdownText` shares the linkifier.
- **Markdown in messages**, toggle in Settings → Messages, **default on**.
- **Message numbers**: optional greyed `#<seq>` above each bubble (Settings).
- **Long-press a message** → Copy / Copy #<seq>.
- **Attach** button in the input bar: a contact (name + number as text) via the
  system contact picker, or the current location as a `geo:` URI
  (`LocationManager`, no Play Services; `ACCESS_COARSE_LOCATION`).
- **Search**: the groups screen searches group names and every cached message
  body (`MessageDao.search`, debounced); a message hit opens its group. Each
  conversation has its own search that filters the thread.
- **Invite dialog**: "Send SMS" (opens the SMS app to `contact.e164` with the
  blurb prefilled) and "Share" (system chooser, passing the number).

### Deferred
- Search "jump to message" (open a thread scrolled to the hit).
- Thread search as prev/next over the full list (currently filters to matches).

## 0.7 — versionCode 8 — 2026-09-09

D-pad / feature-phone fixes plus the background-sync default.

- **Background sync service** now defaults to **on**.
- **Legal screens** are D-pad scrollable: a new `Modifier.dpadScrollable` makes
  the text container focusable and scrolls it on up/down/page keys.
- **Add-member manual number**: a phone keypad (`KeyboardType.Phone` +
  `ImeAction.Done`), the "+"/Add is a focusable labelled button and the Done key
  also adds. Typed numbers now appear as **removable chips above the field**
  instead of vanishing; national numbers are sent with the device region so they
  no longer come back "unparseable".
- **Message input on non-touch devices**: the input bar now pads for the
  navigation bar / soft-key labels and the IME, and the message field carries an
  `ImeAction.Send` (so the on-screen keyboard's send key works without focusing
  the send button).

## 0.6 — versionCode 7 — 2026-09-09

- **Unread counts**: computed locally as `lastMessageSeq - lastReadSeq` instead
  of the server's `unreadCount` (which never decreases). `lastReadSeq` advances
  to the newest visible message while a conversation is open, so opening a group
  clears its badge. First sync seeds read state from the server so a genuinely
  unread group still shows a count.
- **Unread badge**: a small filled circle with the number (e.g. a dot showing
  "12"), not the text "12 unread".
- **New-user sign-in, again**: the "display name required" rejection happens at
  `verify`, not `start`. `verify` now detects it, reveals a name field on the
  code screen, and retries the same code with the name — no raw error, no lost
  progress.

## 0.5 — versionCode 6 — 2026-09-09

- **New-user sign-in**: broaden the detection of "a display name is required to
  create an account" so `AuthViewModel` reveals the display-name field and
  retries `start` with the original phone number, instead of just showing the
  error. Matches the slug and, as a fallback, any "display name" text in the
  error detail / field errors (the exact shape is undocumented).

## 0.4 — versionCode 5 — 2026-09-09

- **Fix crash on sign out**: `AuthRepository.signOut()` called
  `RoomDatabase.clearAllTables()` on the main thread (via `viewModelScope`); now
  wrapped in `Dispatchers.IO`.
- **Markdown in Privacy Policy / Terms**: new dependency-free `MarkdownText`
  composable (headings, bold/italic, inline code, links, bullet/numbered lists,
  horizontal rules); `LegalScreen` uses it instead of plain text.
- Added `README.md`.

## 0.3 — versionCode 4 — 2026-09-09

Fixes from a second round of testing.

- **SMS code auto-detect**: the `SMS_RECEIVED` receiver is now registered
  `RECEIVER_EXPORTED` (required for the system SMS broadcast), and on start we
  also scan the inbox for a matching message from the last 25s (covers a code
  that landed during the permission dialog). Extraction prefers digits right
  after the word "code" (the real format is `Your tzibbur code: 000000`).
  `READ_SMS` added for the inbox scan.
- **Live messages**: after the WebSocket delivers its backlog the client now
  sends the WebSocket `ack` frame on that connection (previously only a REST
  ack), which is what makes the server start pushing live. Fixed a
  subscribe-after-emit race so the backlog isn't lost. The conversation screen
  also polls `refreshLatest` every 5s as a guaranteed fallback. WebSocket
  connect / ack / close are logged.
- **Devices**: the list now force-refreshes on every visit and via
  pull-to-refresh, marks "This device", sorts current-first, and offers a
  Remove action (best-effort `DELETE /v1/me/devices/{id}` — not in the
  documented API; a 404/405/501 disables the action and shows a notice).
- **Add members**: replaced the "one number per line" text box with a contact
  picker — searchable, tap to multi-select, contacts already in the group shown
  disabled, plus a "type a number" field. Backed by the existing
  `ContactsRepository` + `/v1/contacts/check`.
- **Permissions added**: `READ_SMS`.

## 0.2 — versionCode 3 — 2026-09-09

Second pass — login flow, contacts, groups UI, and a messaging fix.

- **Messaging fix**: sends no longer hang on "Sending…". After a 2xx the client
  re-fetches (`refreshLatest`) to reconcile the message by `clientMessageId`
  instead of trusting the POST reply; a 20s timeout marks a stuck send FAILED; a
  confirm-sweep runs while the conversation is open and on open (catches sends
  orphaned by process death). A FAILED bubble now offers **Retry** / **Delete**.
- **Login flow**:
  - Phone number is prefilled from the last-used number, else a single SIM's
    number; with 2+ SIMs the user picks (with an "other" option). Missing phone
    permission is requested inline, then re-detected.
  - The nickname field is gone from the first screen; if the server needs a
    display name for a new registration, the screen reveals a name field and
    retries with the same number.
  - The SMS code is auto-detected via a `RECEIVE_SMS` listener (no Play
    Services), with a spinner and a "enter manually" escape hatch.
- **Contacts screen**: lists device contacts flagged by Tzibbur registration
  (`/v1/contacts/check`, using Android's pre-computed E.164). A registered
  contact expands to show their groups and an "add to a group" picker (existing
  groups they're not in, plus "new group"). Unregistered contacts get an
  editable invite blurb shared through the system chooser.
- **Groups screen**: bottom tab bar removed; Contacts and Settings are now
  top-bar icons alongside refresh and add. Settings is a pushed screen with a
  back arrow.
- **Permissions added** (all runtime, graceful when denied): `READ_PHONE_STATE`,
  `READ_PHONE_NUMBERS`, `RECEIVE_SMS`, `READ_CONTACTS`.

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
