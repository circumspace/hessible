package com.circumspace.contactstr.sync

import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import com.circumspace.contactstr.data.persistence.ContactStore

/**
 * Performs a full reconciliation of the Contactstr local store with ContactsContract.
 * Called by the OS on periodic sync and by [ContactsContractHelper.requestSync].
 */
class ContactsSyncAdapter(context: Context, autoInitialize: Boolean) :
    AbstractThreadedSyncAdapter(context, autoInitialize) {

    override fun onPerformSync(
        account: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        syncResult: SyncResult,
    ) {
        val contacts = ContactStore(context).load(account.name)
        ContactsContractHelper(context).fullSync(account, contacts)
    }
}
