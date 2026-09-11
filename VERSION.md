# Version history

Version scheme: `MAJOR.MINOR`. MINOR increments for normal changes; MAJOR for
breaking or architectural changes. `versionCode` increments on every change and
stays in sync with `app/build.gradle.kts`.

---

## 0.14 — versionCode 15 — 2026-09-10

Key hand-off fixes.

- **Key-share SMS**: the "text the key to new members" targets are now derived
  from the numbers actually added (the member list rarely echoes phone numbers,
  so the previous approach produced an empty list and sent nothing). Recipients
  are `;`-joined and stripped to digits/`+`. If no messaging app handles the
  intent, the key is copied to the clipboard with a toast instead.
- Added a `<queries>` block (SMS `smsto:` + `https` VIEW) for Android 11+
  package visibility. `ACTION_SENDTO` needs no SMS permission.
- **Encryption screen**: each key is now a tappable chip showing the masked hex;
  tapping it copies the full key to the clipboard.

## 0.13 — versionCode 14 — 2026-09-10

Encryption trust + key hand-off.

- **Admin-verified encryption control**: an "encryption on/off" service message
  is only honoured (and only rendered as a tag) when its sender is a group admin
  — a member can no longer flip other clients' encryption off. Seeing an actual
  ciphertext message still enables encryption locally as a fallback.
  `MessageRepository` takes an `adminIds(groupId)` lookup for this.
- **Share the key when adding members**: on the Add members screen for an
  encrypted group, a checkbox (on by default) texts the current key to the
  newly-added registered Tzibbur users, with instructions to paste it into
  that group's Encryption screen.

## 0.12 — versionCode 13 — 2026-09-10

Polls, pinned messages, stickers and conversation polish.

- **Polls** — attach → Poll: a question + 2+ options sent as a `$POLL:` message
  (non-ST clients read the text and can vote by replying with a number). ST shows
  an interactive card: results stay hidden until you vote or the poll closes,
  only your first vote counts, others' votes are remembered. The author can
  **End poll** (`RE:<seq>:END`); otherwise it auto-closes after a week. A closed
  poll drops a results-summary row at the close position with a jump-to-poll
  arrow. Summaries can't be copied or replied to. New pure `PollSpec` / `PollToken`
  / `Polls.aggregate`.
- **Pinned messages** — an admin long-presses a message (or poll) → Pin; a
  `$PIN:<seq>` control message goes out and clients honour it only if the sender
  is an admin. Shows as a tag plus a banner at the top of the thread (tap to jump
  / an unpin button for admins). `$UNPIN:<seq>` clears it. New `PinControl`.
- **Stickers** — a message that is just 1–3 emoji (and not a reply) renders large
  with no bubble.
- **Encrypted length** — the composer now shows the *real* wire length
  (`MessageCrypto.projectedCipherLength`, base64 + tag) against 1000 as you type,
  turns red and blocks Send when over; `MessageRepository.send` re-checks.
- **Encryption in the lists** — a lock icon on encrypted groups in the group list
  and next to the title in the thread.
- **Deferred encryption announce** — turning encryption on for a group with fewer
  than 3 members no longer posts the "encryption on" message; an admin's client
  posts it automatically once the 3rd member joins. Until then the admin sees a
  "starts at 3 members" tag.
- **3-member posting floor** — no messages until the group has at least 3 members.
- **Contacts search** — a search field on the Contacts screen.
- **Search highlighting** — group-search message hits show a highlighted snippet
  windowed around the match (`SearchSnippet`).
- **Group-list long-press** — Open / Group settings / Leave (confirm) / and for
  admins Members + Delete / and Manage encryption when encrypted.
- **Scroll-to-bottom FAB** — on touch devices, when the thread isn't at the end.
- `MessagesScreen`'s row building moved to a pure `deriveConversation`
  (`ConversationRows.kt`), unit-tested.

### Deferred
- Editing a poll; multi-select polls; poll results in notifications.
- Multiple simultaneous pins.
- Thread-search highlighting inside bubbles (group-search results only).
- Announcing pending encryption from a background sync path (only on screen open).

## 0.11 — versionCode 12 — 2026-09-10

Group encryption (`$E1` — AES-256-GCM, client-side shared key).

- **Turn it on** at group creation or later, from **Group settings → Encryption**.
  Only admins can toggle it or change the group key (enforced in the UI).
- **Keys** are 64-hex-char AES-256 keys, shared out of band. The Encryption
  screen lists all keys (current + old), and anyone can add/delete keys locally
  (with a "you may lose the ability to read messages" warning). Admins can
  **generate** a new group key or mark a key as the send key.
- **Service messages**: enabling / disabling / re-keying posts a plain message
  that Shliach Tzibbur shows as a small grey tag ("X turned on encryption");
  other clients see a readable sentence linking
  `github.com/sh7411usa/ShliachTzibbur`. Kept well under the 1000-char limit.
- **Reading**: opening an encrypted group prompts for the key if the newest
  encrypted message can't be opened. Older messages the current key can't open
  are tried against every known key, then marked **not decryptable** — tapping
  that badge lets you paste more keys and retries them all.
- **Sending**: plaintext can't be sent to an encrypted group. A plaintext
  message from another client is shown with an **insecure** badge; decrypted
  messages get a small **lock** badge and a **View original** menu item.
- Crypto: `"$E1:" + base64(AES-256-GCM(ct||tag))`, plaintext wrapped as
  `"!" + text`. Nonce = `SHA-256("STZ/E1 " + seq + " " + senderId)[:12]`,
  derived from the *anticipated* seq; the receiver searches ±3 seq offsets and
  all keys. New dependency-free `core/crypto` package; keys in a private
  DataStore (`EncryptionStore`), same posture as the auth token.
- Encrypted-group message length limit is lower (~720 chars) to fit the
  ciphertext in the 1000-char body; the composer counter reflects it.

### Known limitations
- No key exchange — keys are distributed entirely out of band.
- GCM nonce is deterministic from (seq, sender); the same sender racing two
  offline sends at the same anticipated seq can reuse a nonce (mitigated by
  spacing anticipated seqs across the outbox).
- No forward secrecy / ratcheting — one static shared key at a time.
- Search across an encrypted group only matches messages a local key can open.

## 0.10 — versionCode 11 — 2026-09-10

Emoji reactions, plus composer polish.

- **Character counter** in the composer (`used / 1000`). A reply's hidden
  `RE:<seq> ` marker counts toward the limit, and the field stops accepting
  input once the *effective* length (marker + body) hits the maximum.
- **Message menu**: "Copy #<seq>" removed. Added a one-tap reaction row —
  👍 ❤️ 😂 😮 😢 — and a "⋮" that opens a full emoji chooser.
- **Emoji reactions**: a reaction is sent as an `RE:<seq> <emoji>` reply whose
  body is only emoji (new `Reactions` helper detects this). Instead of a bubble,
  the emoji is shown as a badge on the message it reacts to. Badges collapse to
  one chip per emoji with a count; tapping the row expands to show who reacted
  with what. Because the service can't un-send a message, only each person's
  most recent reaction to a message is shown.
- Reaction messages don't become a group's "last message" preview and don't add
  to its unread count.
- `ReplyToken` moved from `ui.common` to `core.util` (now used by the data layer
  too).

### Deferred
- Removing your own reaction (needs message deletion, which the API doesn't
  support).
- Tapping a quoted preview to scroll to the original message.

## 0.9 — versionCode 10 — 2026-09-10

Replies in the conversation view.

- **Reply**: long-press a message → **Reply** (also the D-pad centre key now
  opens the message menu on non-touch devices). The composer shows a preview
  strip of the message being answered, with an "×" to cancel.
- On send, a marker `RE:<seq> ` is prepended to the message text (the service has
  no native reply concept). New dependency-free `ReplyToken` parses / formats /
  strips it.
- A message whose text starts with that marker renders a **quoted preview** of
  the referenced message inside the bubble — sender name + snippet, WhatsApp /
  Telegram style — and the marker itself is hidden. If the referenced message
  isn't loaded in the thread, the quote shows "Message #<seq>" /
  "Original message unavailable".

### Deferred
- Tapping a quoted preview to scroll to the original message.

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
