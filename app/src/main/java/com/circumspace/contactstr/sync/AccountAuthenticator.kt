package com.circumspace.contactstr.sync

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.NetworkErrorException
import android.content.Context
import android.os.Bundle

/**
 * Stub authenticator — Contactstr manages its own sign-in via Nostr keys; we only need this
 * class to satisfy the AccountManager / SyncAdapter framework requirement.
 */
class AccountAuthenticator(context: Context) : AbstractAccountAuthenticator(context) {

    override fun addAccount(
        response: AccountAuthenticatorResponse,
        accountType: String,
        authTokenType: String?,
        requiredFeatures: Array<out String>?,
        options: Bundle,
    ): Bundle = Bundle()

    override fun confirmCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        options: Bundle?,
    ): Bundle? = null

    override fun editProperties(
        response: AccountAuthenticatorResponse,
        accountType: String,
    ): Bundle = Bundle()

    override fun getAuthToken(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String,
        options: Bundle,
    ): Bundle = Bundle()

    override fun getAuthTokenLabel(authTokenType: String): String = authTokenType

    override fun hasFeatures(
        response: AccountAuthenticatorResponse,
        account: Account,
        features: Array<out String>,
    ): Bundle = Bundle().apply { putBoolean(android.accounts.AccountManager.KEY_BOOLEAN_RESULT, false) }

    override fun updateCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String?,
        options: Bundle?,
    ): Bundle? = null
}
