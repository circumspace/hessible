package com.circumspace.contactstr.ui.settings

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.circumspace.contactstr.crypto.NostrIdentity
import com.circumspace.contactstr.data.Durability
import com.circumspace.contactstr.data.RelayConfig
import com.circumspace.contactstr.data.VCardIo
import com.circumspace.contactstr.data.nostr.SyncState
import com.circumspace.contactstr.domain.Contact
import com.circumspace.contactstr.ui.common.IdentityAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    identity: NostrIdentity?,
    identityPicture: String?,
    syncState: SyncState,
    contacts: List<Contact>,
    relays: List<RelayConfig>,
    onAddRelay: (String) -> Unit,
    onRemoveRelay: (String) -> Unit,
    onToggleRelay: (String, Boolean) -> Unit,
    onSetDurability: (String, Durability) -> Unit,
    onDetectLocalRelay: () -> Unit,
    onImport: (List<Contact>) -> Unit,
    onSignOut: () -> Unit,
    onWipeAndSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showWipeConfirm by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/x-vcard"),
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(VCardIo.export(contacts).toByteArray())
                }
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) "Exported ${contacts.size} contacts" else "Export failed",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val imported = runCatching {
                context.contentResolver.openInputStream(uri)?.use { VCardIo.parse(it) }
            }.getOrNull().orEmpty()
            onImport(imported)
            Toast.makeText(context, "Imported ${imported.size} contacts", Toast.LENGTH_SHORT).show()
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section("Identity") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IdentityAvatar(npub = identity?.npub, size = 48, pictureUrl = identityPicture)
                        Text("Public key (npub)", style = MaterialTheme.typography.labelMedium)
                        SelectionContainer {
                            Text(
                                identity?.npub ?: "—",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedButton(onClick = {
                            identity?.npub?.let { clipboard.setText(AnnotatedString(it)) }
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Text("  Copy npub")
                        }
                    }
                }
            }

            Section("Sync") {
                SyncCard(syncState)
                Text(
                    "Contacts are stored as NIP-44-encrypted kind-30078 events, replicated in full " +
                        "to every enabled relay below.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Section("Relays") {
                RelaySection(
                    relays = relays,
                    connected = syncState.relays.filter { it.connected }.map { it.url }.toSet(),
                    onAddRelay = onAddRelay,
                    onRemoveRelay = onRemoveRelay,
                    onToggleRelay = onToggleRelay,
                    onSetDurability = onSetDurability,
                    onDetectLocalRelay = onDetectLocalRelay,
                )
            }

            Section("Backup") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { exporter.launch("contactstr.vcf") },
                        modifier = Modifier.weight(1f),
                        enabled = contacts.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null)
                        Text("  Export")
                    }
                    OutlinedButton(
                        onClick = {
                            importer.launch(arrayOf("text/x-vcard", "text/vcard", "text/directory", "text/plain", "*/*"))
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Text("  Import")
                    }
                }
                Text(
                    "Export all contacts to a vCard (.vcf) file, or import from one exported by " +
                        "another app. Imported contacts sync to your relays.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Text("  Sign out")
            }

            Section("Danger zone") {
                OutlinedButton(
                    onClick = { showWipeConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null)
                    Text("  Sign out & erase local data")
                }
                Text(
                    "Removes every contact from this phone and deletes the encrypted local copy, " +
                        "then signs out. Events already published to relays can’t be guaranteed " +
                        "deleted — but they’re NIP-44 encrypted, so they stay unreadable by anyone " +
                        "without your nsec.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null) },
            title = { Text("Erase local data?") },
            text = {
                Text(
                    "This deletes all contacts from this phone and the encrypted local copy, then " +
                        "signs you out. Deletion from relays isn’t guaranteed, but relay copies are " +
                        "NIP-44 encrypted and unreadable without your nsec. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWipeConfirm = false
                    onWipeAndSignOut()
                }) {
                    Text("Erase & sign out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SyncCard(state: SyncState) {
    val total = state.relays.size
    val connected = state.relays.count { it.connected }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (total > 0) "$connected / $total relays connected" else "Connecting…",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (state.syncing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }

            val lastSync = state.lastSyncAtMs?.let {
                DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS).toString()
            } ?: "never"
            Text("Last synced: $lastSync", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Published ${state.published} · Received ${state.received} · Pending ${state.pendingWrites}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelaySection(
    relays: List<RelayConfig>,
    connected: Set<String>,
    onAddRelay: (String) -> Unit,
    onRemoveRelay: (String) -> Unit,
    onToggleRelay: (String, Boolean) -> Unit,
    onSetDurability: (String, Durability) -> Unit,
    onDetectLocalRelay: () -> Unit,
) {
    // Nudge: every enabled relay is FREE → contacts may be pruned over time.
    val enabled = relays.filter { it.enabled }
    if (enabled.isNotEmpty() && enabled.all { it.durability == Durability.FREE }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Text(
                "Your contacts are only on free relays, which may prune old data. Add a paid or " +
                    "self-hosted relay — or run a local relay like Citrine — for durable storage.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            relays.forEach { relay ->
                RelayRow(
                    relay = relay,
                    connected = relay.url in connected,
                    onToggle = { onToggleRelay(relay.url, it) },
                    onSetDurability = { onSetDurability(relay.url, it) },
                    onRemove = { onRemoveRelay(relay.url) },
                )
                HorizontalDivider()
            }
        }
    }

    var newUrl by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = newUrl,
            onValueChange = { newUrl = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("wss://relay.example.com") },
            label = { Text("Add relay") },
        )
        IconButton(
            onClick = { onAddRelay(newUrl); newUrl = "" },
            enabled = newUrl.isNotBlank(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add relay")
        }
    }

    OutlinedButton(onClick = onDetectLocalRelay, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Lan, contentDescription = null)
        Text("  Detect local relay (Citrine)")
    }
    Text(
        "“Paid” and “self-hosted” relays are durability hints you set per relay; they help you keep " +
            "contacts on relays that won’t prune. A detected local relay is an on-device backup.",
        style = MaterialTheme.typography.labelSmall,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelayRow(
    relay: RelayConfig,
    connected: Boolean,
    onToggle: (Boolean) -> Unit,
    onSetDurability: (Durability) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = if (connected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                ),
        )
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                relay.url,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DurabilityChip(relay.durability, onSetDurability)
        }
        Switch(checked = relay.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove relay")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurabilityChip(current: Durability, onSet: (Durability) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // LOCAL is auto-assigned to a detected on-device relay and not user-selectable.
    val selectable = listOf(Durability.FREE, Durability.PAID, Durability.SELF_HOSTED)
    Box {
        AssistChip(
            onClick = { if (current != Durability.LOCAL) expanded = true },
            enabled = current != Durability.LOCAL,
            label = { Text(current.label()) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            selectable.forEach { d ->
                DropdownMenuItem(
                    text = { Text(d.label()) },
                    onClick = { onSet(d); expanded = false },
                )
            }
        }
    }
}

private fun Durability.label(): String = when (this) {
    Durability.FREE -> "Free"
    Durability.PAID -> "Paid"
    Durability.SELF_HOSTED -> "Self-hosted"
    Durability.LOCAL -> "Local"
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}
