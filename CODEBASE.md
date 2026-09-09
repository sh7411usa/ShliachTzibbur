# Shliach Tzibbur — Codebase overview

An open-source Android client for the **Tzibbur** group-messaging service
(`https://api.tzibbur.me`, documented in `tzibbur-api.md`).

- Language: Kotlin · UI: Jetpack Compose (Material 3)
- `minSdk 24`, `compileSdk 36.1`, single Gradle module (`:app`)
- No Google Play Services / FCM dependency
- MVVM, package-by-feature, manual dependency injection

---

## Module layout

```
com.sh7411usa.shliachtzibbur
├── ShliachTzibburApp        Application: builds AppContainer, applies theme + locale,
│                            drives background-sync state, creates notification channels
├── MainActivity             AppCompatActivity hosting Compose; splash, edge-to-edge,
│                            POST_NOTIFICATIONS request, notification deep-link intake,
│                            foreground/visible-group hints for the sync layer
│
├── di/
│   └── AppContainer         Lazily-built singletons (stores, API, repositories,
│                            sync manager). Holds a synchronous token snapshot for
│                            the OkHttp interceptor and WebSocket.
│
├── core/
│   ├── model/               Pure domain types, no framework deps:
│   │   Enums                Role, GroupKind, SenderKind, WhoCanPost, WhoCanAddMembers,
│   │                        AuthPlatform, ThemeMode — each with a lowercase `wire`
│   │                        token and a tolerant `fromWire()` (unknown → safe default)
│   │   User, Device
│   │   Group, GroupSettings, GroupLimits
│   │   Member, AddMembersResult
│   │   Message              (splits the server "Name: text" body prefix), OutboxMessage
│   │   ConversationItem     Delivered | Pending — a row in a conversation
│   │   Auth                 AuthChallenge, Session, AuthResult, AuthMethod (Sms wired;
│   │                        Email/Google declared for future use)
│   │   Legal                LegalKind, LegalDocument
│   │   Paging               Page<T> (cursor), MessagePage (seq)
│   │
│   ├── result/
│   │   ApiException         RFC 7807 problem parsed into { type slug, status,
│   │                        fieldErrors, retryAfterSeconds }; ErrorType constants
│   │   ApiResult<T>         Success | Failure; `apiCatching { }` wraps a call and
│   │                        never lets an exception cross a repository boundary
│   │
│   ├── util/
│   │   Log                  Logging facade (no logging library); debug logs stripped
│   │                        in release
│   │   Ids                  UUID generation for clientMessageId
│   │   DeviceInfo           platform / deviceModel / best-effort region for auth
│   │   PhoneNumbers         minimal E.164 assembly + sanity check (no libphonenumber;
│   │                        server does real validation)
│   │   Timestamps           RFC 3339 <-> epoch / localized formatting (java.time via
│   │                        core-library desugaring)
│   │
│   └── net/
│       NetJson              shared kotlinx.serialization Json (lenient, tolerant)
│       NetworkFactory       builds the one shared OkHttpClient + HttpEngine
│       AuthInterceptor      adds `Authorization: Bearer` except under /v1/auth/
│       TokenProvider        synchronous token accessor (fun interface)
│       OkHttpAwait          `Call.await()` without okhttp-coroutines
│       HttpEngine           executes a request, returns body on 2xx, throws typed
│                            ApiException otherwise (Retry-After honoured)
│       ErrorParser          problem+json -> ApiException (handles both `errors`
│                            shapes: [{path,message}] and {field: reason})
│       TzibburApi           one suspend fn per REST endpoint, returns domain models
│       dto/                 wire DTOs + `toDomain()` mappers
│       ws/
│         WsFrames           sealed WsEvent (Hello, Messages, GroupChanged, Pong,
│                            ErrorFrame, Unknown) + WsParser (tolerant) + WsOutbound
│         TzibburWebSocket   resilient `/v1/ws` client: application-level ping,
│                            queued acks, exponential-backoff reconnect, 4029 handling
│
├── data/
│   ├── local/               Room (destructive migration — it is a cache, re-synced
│   │                        from the server):
│   │   AppDatabase          v1: groups, messages, members, outbox
│   │   entity/              *Entity + mappers. GroupEntity flattens settings/limits
│   │                        and adds local-only cursors (lastReadSeq, deliveredSeq)
│   │                        and a last-message preview
│   │   dao/                 GroupDao, MessageDao, MemberDao, OutboxDao — Flow reads,
│   │                        upserts, seq-advance updates
│   │
│   ├── prefs/               Preferences DataStore:
│   │   SessionStore         token + userId + deviceId (Flow<Session?>)
│   │   SettingsStore        AppSettings: themeMode, languageTag, notificationsEnabled,
│   │                        syncServiceEnabled, mutedGroupIds (mute is local — the API
│   │                        has no endpoint to persist it)
│   │
│   └── repo/                Repositories: network + Room + DataStore, expose Flows,
│                            return ApiResult (never throw)
│       AuthRepository       start/verify SMS (via AuthMethod), sign-out wipes local state
│       ProfileRepository    /v1/me, PATCH name, devices; caches the user in memory
│       GroupRepository      Room-backed group list & one-group flow (merges local mute),
│                            full refresh (pages the cursor), create/update/delete/leave,
│                            categories cache, resync-on-WebSocket-event
│       MessageRepository    conversation flow (messages + outbox, deduped by
│                            clientMessageId), history paging (beforeSeq), durable
│                            send via the outbox with reconciliation, local read-seq,
│                            `ackDelivery` (store-then-ack)
│       MemberRepository     Room-backed member list, add by phone, role change, remove,
│                            contacts/check
│       LegalRepository      privacy / terms, memory-cached
│
├── sync/                    Delivery machinery (there is no notifications *screen*):
│   AppForegroundState       process-wide "app visible" / "which group is open" hints
│   NotificationHelper       two channels (Messages / Sync); MessagingStyle per-group
│                            notifications; the ongoing foreground notification;
│                            deep-link PendingIntent to a group
│   SyncManager              the one place messages become rows: persist -> notify
│                            (unless muted / self / on-screen) -> ack. Shared by the
│                            service and the worker. Also runs a WebSocket session.
│   MessageSyncService       optional foreground service (dataSync) holding the
│                            WebSocket; stops itself on sign-out
│   PendingSyncWorker        periodic (~15 min) GET /v1/pending — the fallback delivery
│                            path, and the only one when the service toggle is off
│   OutboxWorker             flushes queued outgoing messages
│   SyncController           reconciles worker + service against session & the setting;
│                            expedited outbox/poll requests
│   BootReceiver             restores delivery after reboot / app update
│
└── ui/
    AppViewModels            CreationExtras.container shortcut; NavArg keys; one
                             AppViewModelFactory for every ViewModel
    theme/                   Color (full light + dark Material 3 schemes, restrained
                             indigo/amber brand), Theme (ThemeMode-aware, no dynamic
                             colour), Type
    locale/                  AppLanguage (System + English, Yiddish, Hebrew, Arabic,
                             Spanish; RTL flags), LocaleManager (AppCompat app locales)
    common/                  rememberIsTouchDevice, Modifier.focusHighlight (D-pad
                             focus ring), LoadingBox / EmptyState / ErrorRow /
                             ConfirmDialog / SectionHeader / SegmentedChoice /
                             PrimaryButton / SecondaryButton, ApiException.toUserMessage
    navigation/              Routes, ShliachNavHost (auth graph vs main graph chosen by
                             session; bottom-nav MainTabScaffold for Groups / Settings)
    auth/                    AuthViewModel (+ shared across the auth graph),
                             AuthLandingScreen, PhoneEntryScreen, CodeVerifyScreen
    groups/                  GroupsViewModel + GroupsScreen (Room-backed list,
                             pull-to-refresh + toolbar refresh, unread/mute),
                             CreateGroupViewModel + CreateGroupScreen (category chips),
                             categoryLabel
    messages/                MessagesViewModel (group + conversation + self id; starts
                             a WebSocket session while open; send/retry/loadOlder/
                             markRead) + MessagesScreen (bubbles, system-thread style,
                             input bar with post-permission gating)
    groupsettings/           GroupSettingsViewModel + GroupSettingsScreen (name,
                             whoCanPost/whoCanAddMembers, mute, leave, delete),
                             MembersViewModel + MembersScreen (+ add-members dialog,
                             promote/demote/remove)
    usersettings/            UserSettingsViewModel + UserSettingsScreen (display name,
                             read-only phone/email/Google, sign out), DevicesScreen,
                             LegalScreen
    settings/                SettingsHomeScreen (hub: account / appearance+notifications
                             / privacy / terms)
    appsettings/             AppSettingsViewModel + AppSettingsScreen (theme, language,
                             notification toggle, background-sync toggle, version)
```

---

## Key flows

### Authentication
`AuthLandingScreen` → `PhoneEntryScreen` (`POST /v1/auth/start`, sends `platform` +
`deviceModel`) → `CodeVerifyScreen` (`POST /v1/auth/verify`). On success the
`Session` is written to `SessionStore`; the root nav graph switches to the main
app because it observes `SessionStore.session`. The repository takes an
`AuthMethod` so email / Google can be added without touching callers.

### Delivery ("store first, then ack")
Incoming messages (WebSocket `messages` frame **or** `GET /v1/pending`) go through
`SyncManager.ingest`: **(1)** write to Room, **(2)** post a notification unless the
group is muted, the message is your own, or you are looking at that group,
**(3)** `POST /v1/groups/{id}/ack` and advance the local `deliveredSeq`. The ack is
only sent after the Room write succeeds, per the API's requirement.

`readSeq` / `unreadCount` are treated as server hints only (no endpoint advances
them); unread state is tracked locally as `lastReadSeq` per group.

### Sending (durable outbox)
`MessageRepository.send` writes an `OutboxEntity` (with a client UUID) *before*
the network call, so a queued message survives process death. On success the row
is deleted (or kept `PENDING` until the message returns via history/WebSocket,
since `POST …/messages` "is not always a full message object"); reconciliation is
by `clientMessageId`. `client_message_id_reused` is treated as success.
`OutboxWorker` retries backgrounded sends.

### Notifications without FCM
A foreground `MessageSyncService` holds the WebSocket for prompt delivery; it is
**opt-in** (`app_settings_sync_service`) because of the persistent notification.
`PendingSyncWorker` polls `/v1/pending` every ~15 min and is the delivery path
when the service is off. `SyncController` keeps both in step with the session and
the setting; `BootReceiver` restores them after a reboot.

### Localisation & RTL
Five languages: `values/` (English, authoritative) plus `values-es`, `values-ar`,
`values-iw` (Hebrew — legacy code for old-device compatibility; stored BCP-47 tag
is `he`) and `values-yi`. The chosen language is persisted in `SettingsStore` and
applied with `AppCompatDelegate.setApplicationLocales`; `res/xml/locales_config.xml`
feeds the Android 13 system language picker. Yiddish, Hebrew and Arabic lay out
right-to-left (`android:supportsRtl="true"`, start/end paddings throughout).

### Theme
`ThemeMode` (System / Light / Dark) persisted in `SettingsStore`, applied both to
Compose (`ShliachTzibburTheme`) and to `AppCompatDelegate.setDefaultNightMode`
(so the pre-Compose frame matches). Full light and dark Material 3 colour schemes;
dynamic colour is deliberately not used for brand consistency across old devices.

### Non-touch / D-pad
`rememberIsTouchDevice()` exposes touch capability; `Modifier.focusHighlight()`
draws a visible focus ring on list rows and controls. Every action is reachable
from a focusable control (toolbar refresh mirrors pull-to-refresh; an overflow
menu mirrors long-press). `uses-feature android.hardware.touchscreen
required="false"`.

---

## Dependencies added (all justified per CLAUDE.md §10)

Jetpack: navigation-compose, lifecycle-{viewmodel,runtime}-compose + lifecycle-service,
room (+ KSP), datastore-preferences, work-runtime-ktx, appcompat (per-app locales +
DayNight base), core-splashscreen, material-icons-core.
Non-Jetpack: OkHttp (HTTP **and** WebSocket — no usable WebSocket client at
`minSdk 24`), kotlinx-serialization-json, kotlinx-coroutines, desugar_jdk_libs
(java.time on API 24).

---

## Build & test

- `./gradlew :app:assembleDebug` — build the debug APK
- `./gradlew :app:testDebugUnitTest` — JVM unit tests:
  `ErrorParserTest` (problem+json, both `errors` shapes, Retry-After, 401),
  `WsParserTest` (hello/messages/unknown/error frames), `ModelTest` (body-prefix
  split, enum fallbacks, phone assembly/validation)
- `./gradlew :app:lintDebug` — static analysis

### Not verified in this environment
- **Lint** currently crashes with an internal `DetectorError` in its UAST/FIR
  file visitor (reading `import` statements) — a bug in the bundled lint on this
  preview toolchain (Kotlin 2.2 + AGP 9.2), not in project code. `lint { }` is
  set to advisory so it never blocks the build; revisit when the toolchain is
  updated.
- No emulator/device was available, so screens were not exercised visually. UI
  behaviour is covered by reasoning + unit tests only. Specifically unverified:
  RTL rendering, D-pad traversal, notification presentation, the foreground
  service lifecycle, and real API interaction.
- `androidx.core` was pinned to 1.16.0 (and lifecycle/activity/navigation/room to
  matching versions) because the template's 1.19.0 requires `compileSdk 37`,
  which is not yet available. Bump these together once API 37 ships.

## Known limitations / follow-ups

- **Yiddish strings** were translated for meaning but need review by a native
  speaker before release. Spanish / Hebrew / Arabic are more confident but also
  merit a native pass.
- WebSocket `group` event payloads are only partly documented; `SyncManager`
  responds by refetching the affected group rather than trusting the payload.
- `POST /v1/groups` is sent with only `name` + `category`; settings are applied
  with a follow-up `PATCH` (nested settings-at-creation is unverified in the API).
- The auth token is stored in app-private DataStore, not the Keystore — a
  reasonable hardening follow-up.
- Contact picking is manual phone entry (no `READ_CONTACTS` permission yet).
