package com.circumspace.contactstr.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
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
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Overscroll (px) needed at the top of the list to reveal the search bar. */
private const val REVEAL_SEARCH_PX = 120f

/**
 * Overscroll (px) for the second stage (filter chips) — deliberately much larger than
 * [REVEAL_SEARCH_PX] so a casual pull only opens search and the chips need a clearly
 * intentional, longer pull.
 */
private const val REVEAL_FILTERS_PX = 400f

/** Upward scroll (px) that hides the filter chips, then (on a further scroll) the search bar. */
private const val HIDE_THRESHOLD_PX = 160f

/** Fraction of each pull that feeds the elastic driver — the rubber-band resistance. */
private const val OVERSCROLL_DAMPING = 0.35f

/** Cap on the elastic driver so rows can't be spread arbitrarily far. */
private const val OVERSCROLL_MAX_PX = 240f

/** How many rows take part in the accordion spread (falloff reaches zero here). */
private const val SPREAD_ROWS = 10

/** Extra gap (px) opened between the top pair of rows at full pull; later gaps shrink to zero. */
private const val SPREAD_MAX_GAP_PX = 28f

/** A list element: section header or contact row — flat so the spread can use a global index. */
private sealed interface ListEntry {
    data class Header(val title: String) : ListEntry
    data class Row(val contact: com.circumspace.contactstr.domain.Contact) : ListEntry
}

/**
 * Cumulative downward shift for the item at [index] while overscrolling: each gap above it opens
 * by a graduated amount (largest at the top, zero from [SPREAD_ROWS] on), so rows spread apart and
 * spring back together without any scaling — and index 0 stays put.
 */
private fun accordionShift(index: Int, overscroll: Float): Float {
    if (overscroll <= 0f || index <= 0) return 0f
    val fraction = (overscroll / OVERSCROLL_MAX_PX).coerceAtMost(1f)
    var sum = 0f
    for (gap in 1..index.coerceAtMost(SPREAD_ROWS)) {
        sum += 1f - gap.toFloat() / SPREAD_ROWS
    }
    return fraction * SPREAD_MAX_GAP_PX * sum
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactListScreen(
    contacts: ContactsViewModel,
    profiles: ProfileViewModel,
    identity: NostrIdentity?,
    identityPicture: String?,
    themeOverride: Boolean?,
    onCycleTheme: () -> Unit,
    onScanQr: () -> Unit,
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
        profiles.ensureProfiles(list.mapNotNull { it.nostr.ifBlank { null } })
    }

    // Selection mode: long-press a row to enter, tap rows to toggle. Empty == not in selection mode.
    val selected: SnapshotStateMap<String, Unit> = remember { mutableStateMapOf() }
    val selectionMode = selected.isNotEmpty()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val clearSelection = { selected.clear() }
    val toggle = { id: String -> if (selected.remove(id) == null) selected[id] = Unit; Unit }

    // Back exits selection mode before leaving the screen. Drawer stays openable as usual.
    BackHandler(enabled = selectionMode) { clearSelection() }

    // ── Search & category filters, revealed in two stages by pulling down at the top ──
    var searchVisible by remember { mutableStateOf(false) }
    var filtersVisible by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val activeFilters = remember { mutableStateMapOf<String, Unit>() }
    val listState = rememberLazyListState()

    // Elastic overscroll driver (0..OVERSCROLL_MAX_PX). Written *synchronously* during the drag so
    // every row moves in the same frame as the finger (a coroutine hop here caused rows to lag);
    // only the release is animated, with a no-overshoot spring for a native decelerate feel.
    var overscrollPx by remember { mutableFloatStateOf(0f) }
    var releaseJob by remember { mutableStateOf<Job?>(null) }
    val relaxSpread = {
        if (overscrollPx > 0f && releaseJob?.isActive != true) {
            releaseJob = scope.launch {
                animate(
                    initialValue = overscrollPx,
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                ) { value, _ -> overscrollPx = value }
            }
        }
    }

    // Safety net: if a gesture ends without a fling callback (e.g. intercepted), relax the spread
    // instead of leaving the rows stuck mid-transition.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) relaxSpread()
        }
    }

    // Pull down at the top: reveal search, then (much further) filters — with a rubber-band feel.
    // Scroll up: collapse the rubber band, then hide filters, then search (never discarding an
    // active query or selected filters).
    val pullToReveal = remember(listState) {
        object : NestedScrollConnection {
            private var pulled = 0f  // downward overscroll accumulated (reveal stages)
            private var raised = 0f  // upward scroll accumulated (hide stages)

            private fun atTop() =
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0) return Offset.Zero
                pulled = 0f
                // First, unwind any accordion spread before the list itself scrolls.
                if (overscrollPx > 0f) {
                    releaseJob?.cancel()
                    val next = (overscrollPx + available.y).coerceAtLeast(0f)
                    val used = next - overscrollPx
                    overscrollPx = next
                    return Offset(0f, used)
                }
                if (source == NestedScrollSource.UserInput) {
                    raised += -available.y
                    if (raised > HIDE_THRESHOLD_PX) {
                        raised = 0f
                        if (filtersVisible && activeFilters.isEmpty()) {
                            filtersVisible = false
                        } else if (searchVisible && !filtersVisible && query.isBlank() && activeFilters.isEmpty()) {
                            searchVisible = false
                        }
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && atTop()) {
                    if (source == NestedScrollSource.UserInput) {
                        raised = 0f
                        pulled += available.y
                        if (!searchVisible && pulled > REVEAL_SEARCH_PX) {
                            pulled = 0f
                            searchVisible = true
                        } else if (searchVisible && !filtersVisible && pulled > REVEAL_FILTERS_PX) {
                            pulled = 0f
                            filtersVisible = true
                        }
                    }
                    // Accordion spread only once search + filters are fully revealed — mixing it
                    // with the reveal transitions reads as jank. The delta is still consumed
                    // during reveal pulls so the platform stretch never fires.
                    if (searchVisible && filtersVisible) {
                        releaseJob?.cancel()
                        overscrollPx = (overscrollPx + available.y * OVERSCROLL_DAMPING)
                            .coerceAtMost(OVERSCROLL_MAX_PX)
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                raised = 0f
                relaxSpread()
                return Velocity.Zero
            }
        }
    }

    // Every category present in the contacts (derived "nostr" included), Nostr suggested first.
    val allCategories = remember(list) {
        val cats = list.flatMap { it.effectiveCategories }.distinct().sorted()
        listOfNotNull(Contact.CATEGORY_NOSTR.takeIf { it in cats }) + cats.filterNot { it == Contact.CATEGORY_NOSTR }
    }

    // Apply search + category filters (OR across selected categories).
    val visibleList = remember(list, query, activeFilters.keys.toSet()) {
        list.filter { c ->
            val q = query.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                c.displayName.lowercase().contains(q) ||
                c.phone.lowercase().contains(q) ||
                c.email.lowercase().contains(q)
            val matchesFilter = activeFilters.isEmpty() ||
                c.effectiveCategories.any { it in activeFilters }
            matchesQuery && matchesFilter
        }
    }

    val closeSearch = {
        searchVisible = false
        filtersVisible = false
        query = ""
        activeFilters.clear()
    }
    BackHandler(enabled = searchVisible && !selectionMode) { closeSearch() }

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
                    onScanQr = { scope.launch { drawerState.close() }; onScanQr() },
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
                val favorites = visibleList.filter { it.favorite }.take(ContactsViewModel.MAX_FAVORITES)
                val others = visibleList.filterNot { it.favorite }
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    AnimatedVisibility(visible = searchVisible) {
                        SearchFilterRow(
                            query = query,
                            onQueryChange = { query = it },
                            filtersVisible = filtersVisible,
                            categories = allCategories,
                            activeFilters = activeFilters,
                            onClose = closeSearch,
                        )
                    }
                // Flat entry list so every element has a global index for the graduated spread.
                val entries = remember(favorites, others) {
                    buildList {
                        if (favorites.isNotEmpty()) {
                            add(ListEntry.Header("Favorites"))
                            favorites.forEach { add(ListEntry.Row(it)) }
                            add(ListEntry.Header("All contacts"))
                        }
                        others.forEach { add(ListEntry.Row(it)) }
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .nestedScroll(pullToReveal)
                        .fillMaxSize(),
                ) {
                    itemsIndexed(
                        entries,
                        key = { _, e ->
                            when (e) {
                                is ListEntry.Header -> "header-${e.title}"
                                is ListEntry.Row -> e.contact.id
                            }
                        },
                    ) { index, entry ->
                        // Accordion spread: placement-only offset (no relayout, no distortion);
                        // index 0 never moves, keeping the list attached to the search bar.
                        val spreadModifier = Modifier.offset {
                            IntOffset(0, accordionShift(index, overscrollPx).roundToInt())
                        }
                        when (entry) {
                            is ListEntry.Header -> Box(spreadModifier) { SectionHeader(entry.title) }
                            is ListEntry.Row -> Box(spreadModifier) {
                                ContactRow(
                                    contact = entry.contact,
                                    profilePicture = profilePictureFor(entry.contact, profileCache, profiles),
                                    isSelected = selected.containsKey(entry.contact.id),
                                    onClick = {
                                        if (selectionMode) toggle(entry.contact.id) else onOpen(entry.contact.id)
                                    },
                                    onLongClick = { toggle(entry.contact.id) },
                                )
                            }
                        }
                    }
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
    onScanQr: () -> Unit,
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
        label = { Text("Scan contact QR") },
        icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
        selected = false,
        onClick = onScanQr,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
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
                    // Category-driven so highlight rules can become user-configurable later.
                    highlightNostr = Contact.CATEGORY_NOSTR in contact.effectiveCategories,
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
        // Fixed height (M3 two-line list item): rows never resize when supporting text is absent
        // or an avatar pops in, so neighbours don't shift during startup.
        modifier = Modifier
            .height(CONTACT_ROW_HEIGHT_DP.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

/** Uniform contact row height (dp) — the Material 3 two-line list item height. */
private const val CONTACT_ROW_HEIGHT_DP = 72

/**
 * Two-stage search revealed by pulling down at the top: stage one is a full-width pill-shaped
 * search bar (rounded to match the app's circular avatars); pulling further reveals the category
 * filter chips beneath it, with the suggested "Nostr" chip first. Chips filter with OR semantics.
 */
@Composable
private fun SearchFilterRow(
    query: String,
    onQueryChange: (String) -> Unit,
    filtersVisible: Boolean,
    categories: List<String>,
    activeFilters: SnapshotStateMap<String, Unit>,
    onClose: () -> Unit,
) {
    val toggleFilter = { c: String -> if (activeFilters.remove(c) == null) activeFilters[c] = Unit; Unit }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = CircleShape,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close search")
                }
            },
            placeholder = { Text("Search contacts") },
        )
        AnimatedVisibility(visible = filtersVisible) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it }) { cat ->
                    FilterChip(
                        selected = activeFilters.containsKey(cat),
                        onClick = { toggleFilter(cat) },
                        shape = CircleShape,
                        label = {
                            Text(if (cat == Contact.CATEGORY_NOSTR) "Nostr" else cat.replaceFirstChar { it.uppercase() })
                        },
                    )
                }
            }
        }
    }
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
