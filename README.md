# Hessible

A privacy-first Android contacts app. Your contacts are encrypted end-to-end and stored on
Nostr relays — readable only with your key, syncable across devices, with no central server.

> Early work in progress.

## What it does

- **Encrypted everywhere** — each contact is a NIP-44-encrypted `kind:30078` event, replicated to
  every relay you enable. Encrypted at rest on-device too (Android Keystore).
- **System integration** — contacts sync into Android's contacts provider, so they show up in the
  Phone, Messaging, and Mail apps.
- **Relay management** — add/remove relays, mark them paid/self-hosted, and detect a local relay
  (e.g. Citrine) for durable on-device backup.
- **Nostr-aware** — search profiles by name, show avatars, verify NIP-05, highlight linked
  contacts, pin favorites.
- **vCard import/export** and sign-in with a local key (nsec) or an external signer (Amber/NIP-55).

## Build

Requires Android Studio with a recent AGP/Kotlin toolchain.

```
./gradlew assembleDebug
```

## License

Apache License 2.0 — see [LICENSE](LICENSE). Copyright © hermeticvm.
