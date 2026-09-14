Hessible 0.1.2 is a size, speed, and reliability update. There are no new screens — the app is
smaller, starts on better relays, and mirrors and syncs contacts with less work.

### Highlights

- **Much smaller download:** the release build is now minified and resource-shrunk, cutting the APK
  from roughly 32 MB to roughly 20 MB.
- **Faster startup and sync:** contact list updates, system-contacts mirroring, and at-rest key
  access are all cheaper, so large address books sync with noticeably less overhead.
- **Better default relays:** a fresh install now starts with `relay.damus.io`, Yakihonne
  (`nostr-01.yakihonne.com`), and the project's home relay. Relays that prune old data or have
  gone offline are no longer in the default line-up.
- **Encrypted photo storage defaults:** contact photos upload to `blossom.circum.space` first, with
  `blossom.band` as a fallback, and the encrypted copy is mirrored for redundancy.

### Install

Download `hessible-v0.1.2.apk` below and install it on Android 8.0 or newer. It installs as an
update over Hessible 0.1.1 without removing app data. Android may ask you to allow installs from
the browser or file manager you used to open the APK. The accompanying `.sha256` file can be used
to verify the download.

Release signing certificate SHA-256:
`EC:82:BE:84:EA:62:1F:01:4B:9E:DE:7E:E5:B6:AF:D2:3B:03:95:EF:1C:FA:80:FE:3F:BE:39:CA:88:B8:75:E2`

### Please note

- This is an early preview. Back up your Nostr secret key before relying on Hessible as your only
  copy of important contacts.
- The new relay and Blossom defaults apply to fresh installs. Existing relay and photo-server
  settings are preserved across the update.
- Relay availability and retention policies vary. Configure multiple relays, including a durable
  paid, self-hosted, or on-device relay when possible.
- Nostr deletion requests are advisory. Hessible remembers tombstones locally to hide stale copies,
  but a relay may retain previously published encrypted events.
- The APK continues to use the Android package ID `com.circumspace.contactstr`.
