package com.circumspace.contactstr.ui.detail

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.circumspace.contactstr.data.blob.ImageProcessing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.circumspace.contactstr.data.nostr.ProfileViewModel
import com.circumspace.contactstr.domain.Contact
import com.circumspace.contactstr.domain.NostrProfile
import com.circumspace.contactstr.data.BirthdayDate
import com.circumspace.contactstr.data.nostr.Nip05Verifier
import com.circumspace.contactstr.ui.common.LetterAvatar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditContactScreen(
    existing: Contact?,
    profiles: ProfileViewModel,
    suggestedCategories: List<String> = emptyList(),
    onUploadPhoto: (suspend (android.net.Uri) -> com.circumspace.contactstr.domain.ContactPhoto?)? = null,
    onSave: (Contact) -> Unit,
    onDelete: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.displayName ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var email by remember { mutableStateOf(existing?.email ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var website by remember { mutableStateOf(existing?.website ?: "") }
    var nostr by remember { mutableStateOf(existing?.nostr ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    // The encrypted, synced photo (canonical). [localPreview] is the just-picked image shown
    // instantly while its upload runs; [uploading]/[uploadFailed] drive the picker's feedback.
    var photo by remember { mutableStateOf(existing?.photo) }
    var localPreview by remember { mutableStateOf<android.net.Uri?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var uploadFailed by remember { mutableStateOf(false) }
    val categories = remember { (existing?.categories ?: emptyList()).toMutableStateList() }
    var newCategory by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf(existing?.birthday) }
    var showBirthdayPicker by remember { mutableStateOf(false) }

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

    // Show the (cropped) image immediately → encrypt+upload in the background. On success the
    // durable [photo] descriptor is set; on failure we surface it and keep the letter avatar.
    val startUpload: (android.net.Uri) -> Unit = { uri ->
        if (onUploadPhoto != null) {
            localPreview = uri
            uploadFailed = false
            uploading = true
            scope.launch {
                val result = runCatching { onUploadPhoto(uri) }.getOrNull()
                if (result != null) {
                    photo = result
                } else {
                    uploadFailed = true
                    localPreview = null
                }
                uploading = false
            }
        }
    }

    // Skin the (AppCompat) cropper to the app's Material 3 palette by feeding live colors into its
    // options, so its toolbar/menu/guides match the app and follow dark mode + dynamic color —
    // no duplicated XML theme. Read here in composable scope; used later in the picker callback.
    val scheme = MaterialTheme.colorScheme
    val cropSurface = scheme.surface.toArgb()
    val cropOnSurface = scheme.onSurface.toArgb()
    val cropPrimary = scheme.primary.toArgb()
    val cropScrim = scheme.scrim.copy(alpha = 0.6f).toArgb()

    // Crop step: the picked image goes through a square crop/zoom UI; its output feeds the upload.
    val cropPhoto = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) result.uriContent?.let(startUpload)
    }

    // Modern system photo picker → hand the chosen image to the cropper.
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            cropPhoto.launch(
                CropImageContractOptions(
                    uri = uri,
                    cropImageOptions = CropImageOptions().apply {
                        fixAspectRatio = true
                        aspectRatioX = 1
                        aspectRatioY = 1
                        cropShape = CropImageView.CropShape.OVAL
                        outputCompressFormat = Bitmap.CompressFormat.JPEG
                        // Cap the cropper's output; ImageProcessing still normalizes to its own size.
                        outputRequestWidth = ImageProcessing.SIZE
                        outputRequestHeight = ImageProcessing.SIZE
                        activityTitle = "Crop photo"
                        cropMenuCropButtonTitle = "Use photo"
                        // Match the app's Material 3 colors.
                        activityBackgroundColor = cropSurface
                        toolbarColor = cropSurface
                        toolbarTitleColor = cropOnSurface
                        toolbarBackButtonColor = cropOnSurface
                        toolbarTintColor = cropPrimary
                        activityMenuTextColor = cropPrimary
                        progressBarColor = cropPrimary
                        guidelinesColor = cropPrimary
                        borderLineColor = cropPrimary
                        borderCornerColor = cropPrimary
                        backgroundColor = cropScrim
                    },
                ),
            )
        }
    }

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
                                    photo = photo,
                                    // Once a durable encrypted photo exists, drop the non-portable
                                    // local URI; otherwise keep whatever the contact already had.
                                    photoUri = if (photo != null) null else existing?.photoUri,
                                    categories = categories.toList(),
                                    birthday = birthday,
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
                val model: Any? = localPreview ?: photo ?: existing?.photoUri
                if (model != null) {
                    AsyncImage(
                        model = model,
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
                if (uploading) {
                    Box(
                        modifier = Modifier.size(120.dp).clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
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
            if (uploadFailed) {
                Text(
                    "Couldn't upload photo — check your connection or Blossom servers in Settings, then tap to retry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
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
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
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

            // ── Birthday: opens a date picker; year is optional ──
            Text("Birthday", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showBirthdayPicker = true }) {
                    Icon(Icons.Filled.Cake, contentDescription = null)
                    Text("  " + (birthday?.let { BirthdayDate.display(it) } ?: "Add birthday"))
                }
                if (birthday != null) {
                    IconButton(onClick = { birthday = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear birthday")
                    }
                }
            }

            // ── Categories: assigned chips (tap ✕ to remove), suggestions, and a free-form add ──
            Text("Categories", style = MaterialTheme.typography.labelLarge)
            val addCategory = {
                val clean = newCategory.trim().lowercase()
                // "nostr" is reserved — it's derived from the Nostr field, never stored.
                if (clean.isNotEmpty() && clean != Contact.CATEGORY_NOSTR && clean !in categories) {
                    categories.add(clean)
                }
                newCategory = ""
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { cat ->
                    InputChip(
                        selected = true,
                        onClick = { categories.remove(cat) },
                        shape = CircleShape,
                        label = { Text(cat.replaceFirstChar { it.uppercase() }) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove $cat",
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
                suggestedCategories.filterNot { it in categories || it == Contact.CATEGORY_NOSTR }
                    .forEach { cat ->
                        InputChip(
                            selected = false,
                            onClick = { categories.add(cat) },
                            shape = CircleShape,
                            label = { Text(cat.replaceFirstChar { it.uppercase() }) },
                        )
                    }
            }
            OutlinedTextField(
                value = newCategory,
                onValueChange = { newCategory = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = CircleShape,
                placeholder = { Text("Add category") },
                trailingIcon = {
                    IconButton(onClick = addCategory, enabled = newCategory.isNotBlank()) {
                        Icon(Icons.Filled.Add, contentDescription = "Add category")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addCategory() }),
            )
        }
    }

    if (showBirthdayPicker) {
        BirthdayPickerDialog(
            initial = birthday,
            onDismiss = { showBirthdayPicker = false },
            onPick = { birthday = it; showBirthdayPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayPickerDialog(initial: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let { BirthdayDate.toUtcMillisOrNull(it) },
    )
    var includeYear by remember { mutableStateOf(initial?.let { BirthdayDate.hasYear(it) } ?: true) }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onPick(BirthdayDate.fromUtcMillis(it, includeYear)) } },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state, showModeToggle = true)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = includeYear, onCheckedChange = { includeYear = it })
            Text("Include year of birth", style = MaterialTheme.typography.bodyMedium)
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
