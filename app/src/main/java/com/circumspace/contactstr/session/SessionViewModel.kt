package com.circumspace.contactstr.session

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.circumspace.contactstr.crypto.NostrIdentity
import com.circumspace.contactstr.data.persistence.KeyVault
import com.circumspace.contactstr.sync.ContactsContractHelper
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignerResult
import com.vitorpamplona.quartz.nip55AndroidSigner.client.ExternalSignerLogin
import com.vitorpamplona.quartz.nip55AndroidSigner.client.NostrSignerExternal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Holds the signed-in Nostr identity (local nsec or external Amber/NIP-55), persisted via
 * [KeyVault] so it survives process death — restored on startup before the first frame.
 */
class SessionViewModel(app: Application) : AndroidViewModel(app) {
    private val vault = KeyVault(app)

    private val _identity = MutableStateFlow<NostrIdentity?>(null)
    val identity: StateFlow<NostrIdentity?> = _identity.asStateFlow()
    private val _restored = MutableStateFlow(false)
    val restored: StateFlow<Boolean> = _restored.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            restore(vault.load())?.also { identity ->
                ensureAccount(identity.pubKeyHex)
                _identity.value = identity
            }
            _restored.value = true
        }
    }

    /** Returns the identity without setting [_identity] — caller handles that. */
    private fun restore(stored: String?): NostrIdentity? = when {
        stored == null -> null
        stored.startsWith(EXTERNAL_PREFIX) -> {
            val parts = stored.removePrefix(EXTERNAL_PREFIX).split("|")
            val pubKeyHex = parts.getOrNull(0)
            val packageName = parts.getOrNull(1)
            if (pubKeyHex != null && packageName != null) {
                runCatching { externalIdentity(pubKeyHex, packageName) }.getOrNull()
            } else {
                null
            }
        }
        else -> runCatching { NostrIdentity.fromPrivKeyHex(stored) }.getOrNull()
    }

    fun createNewIdentity(): NostrIdentity = NostrIdentity.generate().also { persistLocal(it) }

    fun signInWithNsec(nsec: String): Boolean {
        val identity = NostrIdentity.fromNsec(nsec) ?: return false
        persistLocal(identity)
        return true
    }

    /** Complete an Amber (NIP-55) login from the returned intent. Returns true on success. */
    fun completeAmberLogin(data: Intent): Boolean {
        val result = ExternalSignerLogin.parseResult(data)
        if (result !is SignerResult.RequestAddressed.Successful) return false
        val raw = result.result.pubkey
        val pubKeyHex = if (raw.startsWith("npub")) decodePublicKeyAsHexOrNull(raw) ?: return false else raw
        val packageName = result.result.packageName

        vault.save("$EXTERNAL_PREFIX$pubKeyHex|$packageName")
        _identity.value = externalIdentity(pubKeyHex, packageName)
        return true
    }

    fun signOut() {
        _identity.value?.let { removeAccount(it.pubKeyHex) }
        vault.clear()
        _identity.value = null
    }

    private fun externalIdentity(pubKeyHex: String, packageName: String): NostrIdentity {
        val signer = NostrSignerExternal(pubKeyHex, packageName, getApplication<Application>().contentResolver)
        return NostrIdentity.external(signer, pubKeyHex)
    }

    private fun persistLocal(identity: NostrIdentity) {
        identity.privKeyHex?.let { vault.save(it) }
        ensureAccount(identity.pubKeyHex)
        _identity.value = identity
    }

    private fun ensureAccount(pubKeyHex: String) {
        val am = AccountManager.get(getApplication())
        val account = Account(pubKeyHex, ContactsContractHelper.ACCOUNT_TYPE)
        am.addAccountExplicitly(account, null, null)
        android.content.ContentResolver.setIsSyncable(account, "com.android.contacts", 1)
        android.content.ContentResolver.setSyncAutomatically(account, "com.android.contacts", true)
    }

    private fun removeAccount(pubKeyHex: String) {
        val am = AccountManager.get(getApplication())
        val account = Account(pubKeyHex, ContactsContractHelper.ACCOUNT_TYPE)
        @Suppress("DEPRECATION")
        am.removeAccount(account, null, null)
    }

    private companion object {
        const val EXTERNAL_PREFIX = "ext|"
    }
}
