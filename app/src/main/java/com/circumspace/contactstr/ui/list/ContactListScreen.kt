package com.circumspace.contactstr.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.circumspace.contactstr.crypto.NostrIdentity
import com.circumspace.contactstr.data.ContactsViewModel
import com.circumspace.contactstr.data.FavoriteOutcome
import com.circumspace.contactstr.data.nostr.ProfileViewModel
import com.circumspace.contactstr.domain.Contact
import com.circumspace.contactstr.domain.NostrProfile
import com.circumspace.contactstr.ui.common.ContactAvatar
import com.circumspace.contactstr.ui.common.IdentityAvatar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactListScreen(
    contacts: ContactsViewModel,
    profiles: ProfileViewModel,
    identity: NostrIdentity?,
    identityPicture: String?,
    themeOverride: Boolean?,
    onCycleTheme: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val list by contacts.contacts.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Warm the profile cache for any contacts linked to a Nostr identity, so their avatars
    // appear in the list. ensureProfile is idempotent + cached, and the list reacts to updates.
    val profileCache by profiles.cache.collectAsStateWithLifecycle()
    LaunchedEffect(list) {
        list.forEach { if (it.nostr.isNotBlank()) profiles.ensureProfile(it.nostr) }
    }

    // Selection mode: long-press a row to enter, tap rows to toggle. Empty == not in selection mode.
    val selected: SnapshotStateMap<String, Unit> = remember { mutableStateMapOf() }
    val selectionMode = selected.isNotEmpty()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val clearSelection = { selected.clear() }
    val toggle = { id: String -> if (selected.remove(id) == null) selected[id] = Unit; Unit }

    // Back exits selection mode before leaving the screen. Drawer stays openable as usual.
    BackHandler(enabled = selectionMode) { clearSelection() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Don't let an edge-swipe open the drawer while picking contacts.
        gesturesEnabled = !selectionMode,
        drawerContent = {
            ModalDrawerSheet {
                AppDrawer(
                    npub = identity?.npub,
                    pictureUrl = identityPicture,
                    themeOverride = themeOverride,
                    onCycleTheme = onCycleTheme,
                    onSettings = { scope.launch { drawerState.close() }; onSettings() },
                    onAbout = { scope.launch { drawerState.close() }; onAbout() },
                )
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (selectionMode) {
                    TopAppBar(
                        title = { Text("${selected.size} selected") },
                        navigationIcon = {
                            IconButton(onClick = clearSelection) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                            }
                        },
                        actions = {
                            TextButton(onClick = {
                                if (selected.size == list.size) clearSelection()
                                else list.forEach { selected[it.id] = Unit }
                            }) {
                                Text(if (selected.size == list.size) "None" else "All")
                            }
                        },
                    )
                } else {
                    TopAppBar(
                        title = {},
                        navigationIcon = {
                            // A tonal pill (avatar + chevron) reads clearly as a tappable menu button,
                            // unlike a bare avatar sitting where users expect a hamburger.
                            Surface(
                                onClick = { scope.launch { drawerState.open() } },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .semantics { contentDescription = "Open menu" },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 4.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                                ) {
                                    IdentityAvatar(npub = identity?.npub, size = 32, pictureUrl = identityPicture)
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                }
            },
            floatingActionButton = {
                // The FAB morphs with context: add a contact normally, or — while picking — a
                // favorite toggle stacked above a delete button that carries the live count.
                if (selectionMode) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FloatingActionButton(
                            onClick = {
                                when (contacts.toggleFavorite(selected.keys.toSet())) {
                                    FavoriteOutcome.EXCEEDS_LIMIT -> Toast.makeText(
                                        context,
                                        "You can pin up to ${ContactsViewModel.MAX_FAVORITES} favorites",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    else -> clearSelection()
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ) {
                            Icon(Icons.Filled.Star, contentDescription = "Toggle favorite")
                        }
                        ExtendedFloatingActionButton(
                            onClick = { showDeleteConfirm = true },
                            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            text = { Text("Delete (${selected.size})") },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                } else {
                    ExtendedFloatingActionButton(
                        onClick = onAdd,
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("Add contact") },
                    )
                }
            },
        ) { padding ->
            if (list.isEmpty()) {
                EmptyState(Modifier.padding(padding))
            } else {
                // Favorites pinned to the top under their own header (cap enforced on write).
                val favorites = list.filter { it.favorite }.take(ContactsViewModel.MAX_FAVORITES)
                val others = list.filterNot { it.favorite }
                LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                    if (favorites.isNotEmpty()) {
                        item(key = "header-favorites") { SectionHeader("Favorites") }
                        items(favorites, key = { it.id }) { contact ->
                            ContactRow(
                                contact = contact,
                                profilePicture = profilePictureFor(contact, profileCache, profiles),
                                isSelected = selected.containsKey(contact.id),
                                onClick = { if (selectionMode) toggle(contact.id) else onOpen(contact.id) },
                                onLongClick = { toggle(contact.id) },
                            )
                        }
                        item(key = "header-all") { SectionHeader("All contacts") }
                    }
                    items(others, key = { it.id }) { contact ->
                        ContactRow(
                            contact = contact,
                            profilePicture = profilePictureFor(contact, profileCache, profiles),
                            isSelected = selected.containsKey(contact.id),
                            onClick = { if (selectionMode) toggle(contact.id) else onOpen(contact.id) },
                            onLongClick = { toggle(contact.id) },
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        val count = selected.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text(if (count == 1) "Delete contact?" else "Delete $count contacts?") },
            text = {
                Text(
                    "This removes the selected ${if (count == 1) "contact" else "contacts"} from " +
                        "this phone and requests deletion from relays. Any copies that remain on a " +
                        "relay stay NIP-44 encrypted — unreadable by anyone without your nsec. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    contacts.deleteMany(selected.keys.toSet())
                    clearSelection()
                    showDeleteConfirm = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

/** Navigation drawer: identity header, nav items, and a separated theme toggle pinned to the bottom. */
@Composable
private fun ColumnScope.AppDrawer(
    npub: String?,
    pictureUrl: String?,
    themeOverride: Boolean?,
    onCycleTheme: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IdentityAvatar(npub = npub, size = 56, pictureUrl = pictureUrl)
        Text("Signed in", style = MaterialTheme.typography.labelMedium)
        Text(
            npub?.take(20)?.plus("…") ?: "—",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))

    NavigationDrawerItem(
        label = { Text("Settings") },
        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
        selected = false,
        onClick = onSettings,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
    NavigationDrawerItem(
        label = { Text("About") },
        icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
        selected = false,
        onClick = onAbout,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )

    Spacer(Modifier.weight(1f))
    HorizontalDivider()
    // Theme selector cycles System → Light → Dark, so the user can always return to System.
    val (themeIcon, themeLabel) = when (themeOverride) {
        null -> Icons.Outlined.BrightnessAuto to "System"
        false -> Icons.Outlined.LightMode to "Light"
        true -> Icons.Outlined.DarkMode to "Dark"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCycleTheme() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(themeIcon, contentDescription = null)
        Text(
            "Theme",
            modifier = Modifier.padding(start = 16.dp).weight(1f),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(themeLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    contact: Contact,
    profilePicture: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(contact.displayName) },
        supportingContent = {
            val sub = contact.phone.ifBlank { contact.email }
            if (sub.isNotBlank()) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            if (isSelected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                ContactAvatar(
                    contact = contact,
                    profilePicture = profilePicture,
                    highlightNostr = contact.nostr.isNotBlank(),
                )
            }
        },
        trailingContent = if (contact.favorite && !isSelected) {
            { Icon(Icons.Filled.Star, contentDescription = "Favorite", tint = MaterialTheme.colorScheme.tertiary) }
        } else {
            null
        },
        colors = if (isSelected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

/** Resolve a contact's display avatar URL from the fetched Nostr profile cache, if any. */
private fun profilePictureFor(
    contact: Contact,
    cache: Map<String, NostrProfile>,
    profiles: ProfileViewModel,
): String? =
    if (contact.nostr.isBlank()) null
    else profiles.lookup(cache, contact.nostr)?.picture?.takeIf { it.isNotBlank() }

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Contacts,
            contentDescription = null,
            modifier = Modifier.padding(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text("No contacts yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap “Add contact” to create your first one.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
