package com.circumspace.contactstr.crypto

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.decodePrivateKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip19Bech32.toNsec

/**
 * Wraps a Nostr [signer] (the base type, so it can be either a local in-process signer or an
 * external NIP-55 signer such as Amber) together with its public identity.
 *
 * [privKeyHex] / [nsec] are non-null only for local identities; an Amber-backed identity has no
 * private key in-process (signing/encryption happen inside the signer app).
 */
class NostrIdentity private constructor(
    val signer: NostrSigner,
    val pubKeyHex: String,
    val privKeyHex: String?,
) {
    val npub: String = pubKeyHex.hexToByteArray().toNpub()
    val nsec: String? = privKeyHex?.let { it.hexToByteArray().toNsec() }
    val isExternal: Boolean get() = privKeyHex == null

    companion object {
        /** Generate a brand-new local identity (fresh secp256k1 key pair). */
        fun generate(): NostrIdentity = local(KeyPair())

        /** Import a local identity from a bech32 nsec. Returns null if invalid. */
        fun fromNsec(input: String): NostrIdentity? {
            val hex = decodePrivateKeyAsHexOrNull(input.trim()) ?: return null
            return fromPrivKeyHex(hex)
        }

        /** Reconstruct a local identity from a stored hex private key. */
        fun fromPrivKeyHex(hex: String): NostrIdentity = local(KeyPair(privKey = hex.hexToByteArray()))

        /** An external (NIP-55 / Amber) identity — no private key held in-process. */
        fun external(signer: NostrSigner, pubKeyHex: String): NostrIdentity =
            NostrIdentity(signer, pubKeyHex, privKeyHex = null)

        private fun local(keyPair: KeyPair): NostrIdentity =
            NostrIdentity(
                signer = NostrSignerInternal(keyPair),
                pubKeyHex = keyPair.pubKey.toHexKey(),
                privKeyHex = keyPair.privKey?.toHexKey(),
            )
    }
}
