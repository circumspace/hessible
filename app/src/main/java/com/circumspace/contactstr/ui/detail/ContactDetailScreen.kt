package com.circumspace.contactstr.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import android.widget.Toast
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import com.circumspace.contactstr.data.VCardIo
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Verified
import coil3.compose.AsyncImage
import com.circumspace.contactstr.data.BirthdayDate
import com.circumspace.contactstr.data.ContactsViewModel
import com.circumspace.contactstr.data.FavoriteOutcome
import com.circumspace.contactstr.data.nostr.ProfileViewModel
import com.circumspace.contactstr.domain.Contact
import com.circumspace.contactstr.ui.common.LetterAvatar
import com.circumspace.contactstr.sync.ContactsContractHelper
import com.circumspace.contactstr.util.dialNumber
import com.circumspace.contactstr.util.openMap
import com.circumspace.contactstr.util.openMessengerData
import com.circumspace.contactstr.util.openNostr
import com.circumspace.contactstr.util.openWebsite
import com.circumspace.contactstr.util.sendEmail
import com.circumspace.contactstr.util.sendSms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contacts: ContactsViewModel,
    profiles: ProfileViewModel,
    contactId: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    val list by contacts.contacts.collectAsStateWithLifecycle()
    val contact = list.firstOrNull { it.id == contactId }

    // Contact was deleted (e.g. from the edit screen) — leave this view.
    if (contact == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val context = LocalContext.current
    var showQr by remember { mutableStateOf(false) }
    var qr by remember(contact) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var qrLoading by remember(contact) { mutableStateOf(false) }

    // Enrich from the linked Nostr profile (non-destructive: fills gaps like the avatar).
    LaunchedEffect(contact.nostr) {
        if (contact.nostr.isNotBlank()) profiles.ensureProfile(contact.nostr)
    }
    val profileCache by profiles.cache.collectAsStateWithLifecycle()
    val profile = if (contact.nostr.isNotBlank()) profiles.lookup(profileCache, contact.nostr) else null

    LaunchedEffect(showQr, contact) {
        if (showQr && qr == null) {
            qrLoading = true
            qr = withContext(Dispatchers.Default) {
                runCatching {
                    BarcodeEncoder().encodeBitmap(
                        VCardIo.export(listOf(contact)),
                        BarcodeFormat.QR_CODE,
                        720,
                        720,
                    )
                }.getOrNull()
            }
            qrLoading = false
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showQr = true }) {
                        Icon(Icons.Filled.QrCode2, contentDescription = "Share as QR code")
                    }
                    IconButton(onClick = {
                        if (contacts.toggleFavorite(setOf(contact.id)) == FavoriteOutcome.EXCEEDS_LIMIT) {
                            Toast.makeText(
                                context,
                                "You can pin up to ${ContactsViewModel.MAX_FAVORITES} favorites",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }) {
                        if (contact.favorite) {
                            Icon(Icons.Filled.Star, contentDescription = "Unpin from favorites", tint = MaterialTheme.colorScheme.tertiary)
                        } else {
                            Icon(Icons.Filled.StarBorder, contentDescription = "Pin to favorites")
                        }
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
        ) {
            // Photo header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ContactPhoto(
                    contact = contact,
                    profilePicture = profile?.picture?.takeIf { it.isNotBlank() },
                    size = 132,
                )
                Text(
                    contact.displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                if (contact.categories.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        contact.categories.forEach { cat ->
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    cat.replaceFirstChar { it.uppercase() },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            if (contact.phone.isNotBlank()) {
                // Resolve reachable messengers off the main thread (a ContactsContract query),
                // so opening a contact never blocks the UI. Re-runs per contact.
                val messengerLinks by produceState(
                    initialValue = emptyList<ContactsContractHelper.MessengerLink>(),
                    contact.id,
                ) {
                    value = contacts.messengerLinks(contact.id)
                }
                PhoneSection(contact = contact, messengerLinks = messengerLinks)
            }
            if (contact.email.isNotBlank()) {
                ActionRow(
                    icon = Icons.Filled.Email,
                    label = "Email",
                    value = contact.email,
                    actionIcon = Icons.Filled.Email,
                    actionDescription = "Email ${contact.displayName}",
                    onAction = { context.sendEmail(contact.email) },
                )
            }
            if (contact.address.isNotBlank()) {
                LinkRow(
                    icon = Icons.Filled.Place,
                    label = "Address · tap to open maps",
                    value = contact.address,
                    onClick = { context.openMap(contact.address) },
                )
            }
            if (contact.website.isNotBlank()) {
                LinkRow(
                    icon = Icons.Filled.Language,
                    label = "Website",
                    value = contact.website,
                    onClick = { context.openWebsite(contact.website) },
                )
            }
            if (contact.nostr.isNotBlank()) {
                LinkRow(
                    icon = Icons.Filled.AlternateEmail,
                    label = "Nostr · tap to open profile",
                    value = profile?.name?.takeIf { it.isNotBlank() }?.let { "$it · ${contact.nostr.take(16)}…" }
                        ?: contact.nostr,
                    onClick = { context.openNostr(contact.nostr) },
                )
            }
            if (!profile?.nip05.isNullOrBlank()) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    overlineContent = { Text("Verified (NIP-05)") },
                    headlineContent = { Text(profile!!.nip05) },
                )
            }
            if (contact.note.isNotBlank()) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Notes, contentDescription = null) },
                    overlineContent = { Text("Note") },
                    headlineContent = { Text(contact.note) },
                )
            }

            if (!contact.birthday.isNullOrBlank()) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Cake, contentDescription = null) },
                    overlineContent = { Text("Birthday") },
                    headlineContent = { Text(BirthdayDate.display(contact.birthday)) },
                )
            }
        }
    }

    if (showQr) {
        // Standard vCard payload, so any contacts app (not just Hessible) can scan it.
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text(contact.displayName) },
            text = {
                val qrBitmap = qr
                if (qrLoading) {
                    androidx.compose.material3.CircularProgressIndicator()
                } else if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Contact QR code",
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Could not generate QR code for this contact.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showQr = false }) { Text("Done") }
            },
        )
    }
}

@Composable
private fun ContactPhoto(contact: Contact, profilePicture: String?, size: Int) {
    // Encrypted Blossom photo wins, then a local preview URI, then the linked Nostr profile's avatar.
    val model: Any? = contact.photo ?: contact.photoUri ?: profilePicture
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(CircleShape),
        )
    } else {
        LetterAvatar(seed = contact.displayName, label = contact.initials, size = size)
    }
}

/**
 * Phone field with a Call action, then messaging shortcuts: SMS always, plus a chip per messenger
 * the contact is *actually reachable on* — detected from the data rows WhatsApp / Telegram / Signal
 * write onto the aggregated system contact (only present for registered numbers). [messengerLinks]
 * is resolved off the main thread by the caller, so composition never blocks.
 */
@Composable
private fun PhoneSection(
    contact: Contact,
    messengerLinks: List<ContactsContractHelper.MessengerLink>,
) {
    val context = LocalContext.current
    ListItem(
        leadingContent = { Icon(Icons.Filled.Phone, contentDescription = null) },
        overlineContent = { Text("Phone") },
        headlineContent = { Text(contact.phone) },
        trailingContent = {
            IconButton(onClick = { context.dialNumber(contact.phone) }) {
                Icon(
                    Icons.Filled.Call,
                    contentDescription = "Call ${contact.displayName}",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Align the chips under the headline, past the list item's leading icon column.
            .padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = { context.sendSms(contact.phone) },
            leadingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.Message,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            label = { Text("SMS") },
        )
        messengerLinks.forEach { link ->
            AssistChip(
                onClick = { context.openMessengerData(link.dataId, link.mimeType) },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
                label = { Text(link.messenger.label) },
            )
        }
    }
}

/** A field with a trailing action button (call / email). */
@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    value: String,
    actionIcon: ImageVector,
    actionDescription: String,
    onAction: () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        overlineContent = { Text(label) },
        headlineContent = { Text(value) },
        trailingContent = {
            IconButton(onClick = onAction) {
                Icon(actionIcon, contentDescription = actionDescription, tint = MaterialTheme.colorScheme.primary)
            }
        },
    )
}

/** A whole-row-tappable field that opens an external app (maps / browser / Nostr). */
@Composable
private fun LinkRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        overlineContent = { Text(label) },
        headlineContent = { Text(value) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
    )
}
