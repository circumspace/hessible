package com.circumspace.contactstr.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.circumspace.contactstr.data.nostr.ProfileViewModel
import com.circumspace.contactstr.domain.Contact
import com.circumspace.contactstr.domain.NostrProfile
import com.circumspace.contactstr.data.nostr.Nip05Verifier
import com.circumspace.contactstr.ui.common.LetterAvatar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditContactScreen(
    existing: Contact?,
    profiles: ProfileViewModel,
    onSave: (Contact) -> Unit,
    onDelete: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.displayName ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var email by remember { mutableStateOf(existing?.email ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var website by remember { mutableStateOf(existing?.website ?: "") }
    var nostr by remember { mutableStateOf(existing?.nostr ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var photoUri by remember { mutableStateOf(existing?.photoUri) }

    var nostrSuggestions by remember { mutableStateOf<List<NostrProfile>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    // NIP-05 verification results per pubkey-hex (null = unknown/in-flight, true/false = checked).
    val verified = remember { mutableStateMapOf<String, Boolean>() }

    // Debounced NIP-50 search: typing a name (not an npub) queries the search relay. Re-running on
    // each keystroke cancels the prior search (LaunchedEffect key change), so results never race/flicker.
    LaunchedEffect(nostr) {
        val q = nostr.trim()
        if (q.length >= 3 && !q.startsWith("npub") && !q.startsWith("nprofile")) {
            delay(350)
            searching = true
            nostrSuggestions = runCatching { profiles.search(q) }.getOrDefault(emptyList())
            searching = false
        } else {
            nostrSuggestions = emptyList()
            searching = false
        }
    }

    // Verify each result's NIP-05 in the background; the badge upgrades as checks complete.
    LaunchedEffect(nostrSuggestions) {
        nostrSuggestions.forEach { p ->
            if (p.nip05.isNotBlank() && verified[p.pubKeyHex] == null) {
                launch { verified[p.pubKeyHex] = Nip05Verifier.verify(p.nip05, p.pubKeyHex) }
            }
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) photoUri = uri.toString() }

    Scaffold(
        modifier = Modifier.fillMaxWidth(),
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New contact" else "Edit contact") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (existing != null && onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                    IconButton(
                        onClick = {
                            onSave(
                                (existing ?: Contact(displayName = "")).copy(
                                    displayName = name.trim(),
                                    phone = phone.trim(),
                                    email = email.trim(),
                                    address = address.trim(),
                                    website = website.trim(),
                                    nostr = nostr.trim(),
                                    note = note.trim(),
                                    photoUri = photoUri,
                                ),
                            )
                        },
                        enabled = name.isNotBlank(),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Tappable photo (placeholder until one is chosen).
            Box(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clip(CircleShape)
                    .clickable {
                        pickPhoto.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                contentAlignment = Alignment.BottomEnd,
            ) {
                val uri = photoUri
                if (uri != null) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Contact photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(120.dp).clip(CircleShape),
                    )
                } else {
                    LetterAvatar(
                        seed = name.ifBlank { "?" },
                        label = name.trim().firstOrNull()?.uppercase() ?: "+",
                        size = 120,
                    )
                }
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            Field(name, { name = it }, "Name", capitalization = KeyboardCapitalization.Words)
            Field(phone, { phone = it }, "Phone", leading = Icons.Filled.Phone, keyboardType = KeyboardType.Phone)
            Field(email, { email = it }, "Email", leading = Icons.Filled.Email, keyboardType = KeyboardType.Email)
            Field(address, { address = it }, "Address", leading = Icons.Filled.Place, singleLine = false)
            Field(website, { website = it }, "Website", leading = Icons.Filled.Language, keyboardType = KeyboardType.Uri)

            // Results float in a dropdown anchored to the field, so they stay visible above the
            // keyboard and scroll independently of the form (no layout disruption).
            val resultsOpen = searching || nostrSuggestions.isNotEmpty()
            ExposedDropdownMenuBox(
                expanded = resultsOpen,
                onExpandedChange = {},
                modifier = Modifier.fillMaxWidth(),
            ) {
                Field(
                    nostr, { nostr = it }, "Nostr (npub, or search by name)",
                    leading = Icons.Filled.AlternateEmail,
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable),
                )
                ExposedDropdownMenu(
                    expanded = resultsOpen,
                    onDismissRequest = { nostrSuggestions = emptyList() },
                ) {
                    if (searching) {
                        DropdownMenuItem(
                            enabled = false,
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text("  Searching Nostr…", style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            onClick = {},
                        )
                    }
                    nostrSuggestions.forEach { profile ->
                        SuggestionRow(
                            profile = profile,
                            verified = verified[profile.pubKeyHex] == true,
                            onClick = {
                                nostr = profile.npub
                                if (name.isBlank()) name = profile.name
                                if (website.isBlank() && profile.website.isNotBlank()) website = profile.website
                                nostrSuggestions = emptyList()
                            },
                        )
                    }
                }
            }

            Field(note, { note = it }, "Note", leading = Icons.Filled.Notes, singleLine = false)
        }
    }
}

/** A single Nostr search result: avatar, name (+ verified badge), nip05, and a bio capped to 3 lines. */
@Composable
private fun SuggestionRow(
    profile: NostrProfile,
    verified: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        onClick = onClick,
        leadingIcon = {
            if (profile.picture.isNotBlank()) {
                AsyncImage(
                    model = profile.picture,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )
            } else {
                LetterAvatar(
                    seed = profile.npub,
                    label = profile.name.firstOrNull()?.uppercase() ?: "?",
                    size = 40,
                )
            }
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.name.ifBlank { profile.npub.take(14) + "…" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (verified) {
                        Icon(
                            Icons.Filled.Verified,
                            contentDescription = "NIP-05 verified",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp).size(16.dp),
                        )
                    }
                }
                if (profile.nip05.isNotBlank()) {
                    Text(
                        profile.nip05,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (profile.about.isNotBlank()) {
                    Text(
                        profile.about,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    )
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = leading?.let { { Icon(it, contentDescription = null) } },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
        modifier = modifier.fillMaxWidth(),
    )
}
