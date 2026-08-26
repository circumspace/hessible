Hessible 0.1.0 is the first packaged preview of a private, Nostr-native address book for Android.
It keeps contacts under your own Nostr identity instead of putting a central service in charge of
your data.

### Highlights

- **Private by design:** contact records are NIP-44 encrypted before they leave the device and are
  stored as addressable Nostr events across the relays you choose.
- **Reliable multi-device sync:** a durable outbox retries offline edits, while persistent
  tombstones prevent deleted contacts from being resurrected by stale relay data.
- **Nostr-aware contacts:** link an `npub`, search public profiles by name, resolve NIP-05
  identities, and use profile metadata and avatars in the address book.
- **Android integration:** mirror Hessible contacts into Android's contacts provider, including
  phone, SMS, email, map, browser, and installed-messenger actions.
- **Flexible organization:** favorites, categories, birthdays, notes, addresses, and websites are
  all supported.
- **Easy transfer and sharing:** import or export standard vCards, paste contact lists, and share or
  scan contact QR codes.
- **Encrypted photos:** contact images can be encrypted locally and mirrored to configured Blossom
  servers.
- **Your choice of keys:** create or import a local Nostr key, or connect an external Amber signer.

### Install

Download `hessible-v0.1.0.apk` below and install it on Android 8.0 or newer. Android may ask you to
allow installs from the browser or file manager you used to open the APK. The accompanying
`.sha256` file can be used to verify the download.

Release signing certificate SHA-256:
`EC:82:BE:84:EA:62:1F:01:4B:9E:DE:7E:E5:B6:AF:D2:3B:03:95:EF:1C:FA:80:FE:3F:BE:39:CA:88:B8:75:E2`

### Please note

- This is an early preview. Back up your Nostr secret key before relying on Hessible as your only
  copy of important contacts.
- Relay availability and retention policies vary. Configure multiple relays, including a durable
  paid, self-hosted, or on-device relay when possible.
- Nostr deletion requests are advisory. Hessible remembers tombstones locally to hide stale copies,
  but a relay may retain previously published encrypted events.
- The APK currently uses the existing Android package ID `com.circumspace.contactstr`.
