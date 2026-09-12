Hessible 0.1.1 introduces a new visual identity built around two interwoven ribbons forming an H.

### Highlights

- **New launcher icon:** a distinct coral, amber, and purple H replaces the original amphora design.
- **Native adaptive artwork:** Android 8.0 and newer receive separate foreground and background
  layers that remain correctly framed across launcher icon shapes.
- **Themed icon support:** Android 13 and newer can use a monochrome version of the same mark when
  themed icons are enabled.
- **Consistent branding:** the About screen, repository README, and Play Store export use artwork
  generated from the same editable vector source.
- **Reusable source assets:** editable SVG artwork and generated Android, macOS, and Linux exports
  are included under `branding/icons`.

### Install

Download `hessible-v0.1.1.apk` below and install it on Android 8.0 or newer. It can be installed as
an update over Hessible 0.1.0 without removing app data. Android may ask you to allow installs from
the browser or file manager you used to open the APK. The accompanying `.sha256` file can be used
to verify the download.

Release signing certificate SHA-256:
`EC:82:BE:84:EA:62:1F:01:4B:9E:DE:7E:E5:B6:AF:D2:3B:03:95:EF:1C:FA:80:FE:3F:BE:39:CA:88:B8:75:E2`

### Please note

- This is an early preview. Back up your Nostr secret key before relying on Hessible as your only
  copy of important contacts.
- Relay availability and retention policies vary. Configure multiple relays, including a durable
  paid, self-hosted, or on-device relay when possible.
- Nostr deletion requests are advisory. Hessible remembers tombstones locally to hide stale copies,
  but a relay may retain previously published encrypted events.
- The APK continues to use the Android package ID `com.circumspace.contactstr`.
