# Hessible — Project State

_Snapshot for context continuity / session handover. Update as things change._

## What the app is

A privacy-first Android contacts app. Each contact is a **NIP-44-encrypted `kind:30078`** event
(NIP-78 app data) on Nostr relays — readable only with the user's key — **mirrored into Android's
system contacts** so contacts appear in Phone/Mail/Messaging. Encrypted at rest locally (Android
Keystore via `SecureBlob`). Sign-in with a local key (nsec) or an external signer (Amber / NIP-55).

- Package / applicationId: `com.circumspace.contactstr` (internal; **do not rename** — backs the
  relay `d`-tag prefix `circumspace.contactstr/contact/<uuid>` and the Android account type).
- User-visible name: **Hessible** (`@string/app_name`).
- Upstream: https://github.com/circumspace/hessible (Apache 2.0, author `hermeticvm`).

## Architecture (quick map)

- `data/nostr/SyncManager` — the sync core. **Durable outbox** of publish *intents* (`OutboxStore`):
  edits/deletes are enqueued as plaintext, then a single-flight `drain()` signs (Amber) + sends,
  keeping each entry until the relay `OK`. Failed/҂unavailable signing leaves the intent queued.
  Subscribes to `kind:30078` + `kind:5`; timestamped **tombstones** suppress resurrection.
  `resync()` reconnects dropped sockets + re-subscribes; `retry()` drains the outbox.
- `data/nostr/RelayPool` — raw WebSocket relays (OkHttp). `reconnect()` revives dead sockets.
- `data/nostr/ProfileViewModel` — two pools: search (NIP-50) + metadata (author reads). Profile
  search w/ WoT + quality ranking + bridge filtering, owner avatar, NIP-05 verification.
- `data/ContactsViewModel` — in-memory contacts, encrypted local cache, sync, favorites, relay
  config, categories, birthdays; mirrors to ContactsContract + birthday calendar. `retrySync()`
  (→ `resync()`) is called on app foreground (`ON_RESUME` in `ContactstrApp`).
- `data/persistence/` — `ContactStore`, `RelayStore`, `OutboxStore` (all SecureBlob-encrypted).
- `data/{ContactJson,VCardIo,PasteImport,BirthdayDate}` — serialization + import helpers.
- `sync/` — `AccountAuthenticator` + `SyncAdapter` + `ContactsContractHelper` (system-contacts
  bridge, fingerprint-skips unchanged writes) + `BirthdayCalendarHelper` (owned local calendar).
- `HessibleApp` — Application; tuned Coil `ImageLoader` (mem + 64MB disk cache, crossfade).
- `ui/` — Compose screens: SignIn, ContactList, ContactDetail, AddEditContact, Settings, About.

## Works (implemented + on-device)

- **CRUD + E2E encryption** (verified via `nak`: relay content is ciphertext only). Local cache
  AES-GCM sealed.
- **System-contacts mirroring** via SyncAdapter; unchanged contacts skipped via a SYNC1 fingerprint.
- **Relay management** (Settings → Relays): add/remove/enable, durability chips, free-only nudge,
  local-relay (Citrine) detection at `ws://127.0.0.1:4869`. Full replication to all enabled relays.
- **Durable, reliable publishing** (outbox) + **reconnect/resync on foreground** — the fix for
  Amber's flaky background signing and for dropped mobile sockets.
- **Nostr profile search** (NIP-50): two-stage pull-reveal search bar + category filter chips;
  bridge filtering; token-aware ranking; results in a dropdown; NIP-05 verified badge; capped bio.
- **Categories** (vCard CATEGORIES) — filter chips + editable chips in add/edit; drives the
  inward-halo highlight for Nostr contacts (via derived `nostr` category).
- **Avatars** — contact + own identity; Coil disk-cached; inward-halo highlight.
- **Favorites** (max 7): list multi-select + detail-view star toggle.
- **Multi-select delete** (long-press) + durable deletions (kind:5 + tombstones).
- **Birthdays** (vCard BDAY, year-optional): Path A (ContactsContract `TYPE_BIRTHDAY` → Google/
  Samsung calendars) **and** Path B (owned local "Hessible Birthdays" `CalendarContract` calendar,
  yearly recurring — shows in any calendar app incl. GrapheneOS/Etar). M3 date picker w/ year toggle.
- **QR sharing**: show a contact as a vCard QR; scan to import (zxing).
- **Paste import** (Settings): free-text, one contact/line, shape-based field extraction, preview
  before import, dedup by exact name. Unit-tested (`PasteImportTest`).
- **Theme** System/Light/Dark cycle; **vCard import/export**; sign-out + erase-local-data.
- **App icon**: transparent-bg adaptive icon w/ preserved drop shadow (art in `icon.png`).

## Not yet implemented (planned)

- **Contact image upload** (Blossom / NIP-96) — deferred; open decision encrypted-vs-public blobs.
  `photoUri` still local-URI; avatars are fetched live, not stored.
- **NIP-65 outbox model** — publish own relay list; per-contact relay discovery.
- **nsec backup** ("Copy nsec" for local identities + gate before sign-out/erase). Not built.
- **Live cross-device propagation while both foregrounded** — currently converges on
  foreground/next-start, not via a timer. A foreground-only periodic re-subscribe would add it.
- Customizable per-category highlighting; curated category list in Settings; configurable Citrine port.

## Unstable / caveats

- **Search recall is limited** — only `relay.noswhere.com` + `search.nos.today` (nos.today's index
  is weak; **`relay.nostr.band` is dead — never re-add**). Some names won't surface until more
  NIP-50 relays are added (user will source them).
- **Amber (external signer) is the source of most sync grief** — background signing is unreliable;
  the outbox + foreground-resync work around it, but signing still needs the app foregrounded.
- **Relays don't reliably honor NIP-09** — deletion durability rests on client tombstones, not relays.
- **Sync is foreground-triggered, not background-live** (deliberate: no battery-draining service).
- Wireless-debug/USB to the test phone is flaky; see the memory note on deploy workflow.

## Tested / verified

- Encryption on relays (`nak`), paste parser + vCard (unit tests), on-device installs on Pixel 9
  Pro XL + a DC-1 (Android 13, 1200x1600). Deploy: `adb push` + `pm install` (streamed install
  hangs over wireless for the 39MB APK).
- Only automated tests: `VCardIoTest`, `PasteImportTest`. No tests for sync/crypto/VMs.

## Pending review / confirmation

- **Bidirectional sync**: confirm DC-1 pulls the Pixel's ~14h-old updates (`3fd0746f`,`91e54945`)
  on foreground; round-trip an edit both directions.
- **Rescue Chris**: re-save on Pixel so the lost edit finally publishes.
- **Sample cleanup**: delete the famous-CS-name sample contacts (crash-free now), then `nak`-verify
  the relay's live count drops to only real contacts.
- Birthday calendar visibility across Google/Samsung/Etar; QR round-trip with a stock camera app.
- About "Source Code" link points at the org (`github.com/circumspace`) — update to the hessible repo.

## Dev workflow

- Build: `./gradlew assembleDebug`; tests: `./gradlew testDebugUnitTest`.
- Deploy (wireless preferred; USB flaky): `adb push app/build/outputs/apk/debug/app-debug.apk
  /data/local/tmp/h.apk && adb -s <dev> shell pm install -r /data/local/tmp/h.apk`.
- Test relays with the `nak` CLI (not JS). Devices/pairing details in the session memory notes.
