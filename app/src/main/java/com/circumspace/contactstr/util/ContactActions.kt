package com.circumspace.contactstr.util

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.core.net.toUri

/** Launches an intent, showing a toast instead of crashing if no app can handle it. */
private fun Context.safeStart(intent: Intent, failMessage: String) {
    try {
        startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(this, failMessage, Toast.LENGTH_SHORT).show()
    }
}

fun Context.dialNumber(number: String) =
    safeStart(Intent(Intent.ACTION_DIAL, "tel:${Uri.encode(number)}".toUri()), "No dialer app found")

/** Opens the default SMS app composing to [number]. Works with any dialable number. */
fun Context.sendSms(number: String) =
    safeStart(Intent(Intent.ACTION_SENDTO, "smsto:${Uri.encode(number)}".toUri()), "No messaging app found")

/**
 * Opens a messenger chat via a ContactsContract Data row the messenger itself wrote (see
 * [com.circumspace.contactstr.sync.ContactsContractHelper.messengerLinks]). Launching the row's
 * VIEW intent is exactly what the stock Contacts app does for "Message on WhatsApp/Signal/Telegram".
 */
fun Context.openMessengerData(dataId: Long, mimeType: String) {
    val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataId)
    safeStart(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType), "Messenger app not found")
}

fun Context.sendEmail(address: String) =
    safeStart(Intent(Intent.ACTION_SENDTO, "mailto:$address".toUri()), "No email app found")

fun Context.openMap(address: String) =
    safeStart(Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(address)}".toUri()), "No maps app found")

fun Context.openWebsite(url: String) {
    val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    safeStart(Intent(Intent.ACTION_VIEW, normalized.toUri()), "No browser found")
}

/** Opens a Nostr identity (npub / nprofile) in an installed Nostr app via the `nostr:` scheme. */
fun Context.openNostr(identifier: String) {
    val uri = if (identifier.startsWith("nostr:")) identifier else "nostr:$identifier"
    safeStart(Intent(Intent.ACTION_VIEW, uri.toUri()), "No Nostr app found to open this profile")
}
