package com.circumspace.contactstr.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vitorpamplona.quartz.nip55AndroidSigner.api.CommandType
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.Permission
import com.vitorpamplona.quartz.nip55AndroidSigner.client.ExternalSignerLogin
import com.vitorpamplona.quartz.nip55AndroidSigner.client.NostrSignerExternal
import com.vitorpamplona.quartz.nip55AndroidSigner.client.isExternalSignerInstalled
import com.circumspace.contactstr.data.ContactsViewModel
import com.circumspace.contactstr.data.PasteImport
import com.circumspace.contactstr.data.VCardIo
import com.circumspace.contactstr.data.nostr.ProfileViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.circumspace.contactstr.session.SessionViewModel
import com.circumspace.contactstr.ui.about.AboutScreen
import com.circumspace.contactstr.ui.detail.AddEditContactScreen
import com.circumspace.contactstr.ui.detail.ContactDetailScreen
import com.circumspace.contactstr.ui.list.ContactListScreen
import com.circumspace.contactstr.ui.settings.SettingsScreen
import com.circumspace.contactstr.ui.signin.SignInScreen

private object Routes {
    const val SIGN_IN = "signin"
    const val LIST = "list"
    const val ADD = "add"
    const val DETAIL = "detail/{contactId}"
    const val EDIT = "edit/{contactId}"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun detail(contactId: String) = "detail/$contactId"
    fun edit(contactId: String) = "edit/$contactId"
}

@Composable
fun ContactstrApp(
    session: SessionViewModel,
    contacts: ContactsViewModel,
    profiles: ProfileViewModel,
    isDark: Boolean,
    themeOverride: Boolean?,
    onCycleTheme: () -> Unit,
) {
    val navController = rememberNavController()
    val identity by session.identity.collectAsStateWithLifecycle()

    // When the app returns to the foreground, nudge the sync outbox — an external signer (Amber)
    // can only service a signing request while we're foregrounded, so this is where a publish that
    // couldn't sign in the background finally goes through.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) contacts.retrySync()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Load (or clear) the persisted contacts for the active identity and start/stop relay sync.
    LaunchedEffect(identity) {
        val current = identity
        if (current != null) {
            contacts.openFor(current)
            // Prime the Web-of-Trust set (the signed-in user's follows) for search ranking.
            profiles.setOwner(current.pubKeyHex)
        } else {
            contacts.closeSession()
            profiles.setOwner(null)
        }
    }
    val syncState by contacts.syncState.collectAsStateWithLifecycle()
    val contactList by contacts.contacts.collectAsStateWithLifecycle()
    val ownerProfile by profiles.ownerProfile.collectAsStateWithLifecycle()
    val identityPicture = ownerProfile?.picture?.takeIf { it.isNotBlank() }
    val relays by contacts.relays.collectAsStateWithLifecycle()
    val blossomServers by contacts.blossomServers.collectAsStateWithLifecycle()
    // Stored (non-derived) categories across all contacts — suggestions in the category editor.
    val suggestedCategories = remember(contactList) {
        contactList.flatMap { it.categories }
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    // --- Amber / NIP-55 external signer wiring ---
    val context = LocalContext.current
    val amberAvailable = remember { isExternalSignerInstalled(context) }

    // Delivers foreground signer-prompt results back to the active external signer.
    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        (session.identity.value?.signer as? NostrSignerExternal)?.newResponse(result.data ?: Intent())
    }
    LaunchedEffect(identity) {
        (identity?.signer as? NostrSignerExternal)?.registerForegroundLauncher { intent ->
            foregroundLauncher.launch(intent)
        }
    }

    val amberLoginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK &&
            result.data != null &&
            session.completeAmberLogin(result.data!!)
        ) {
            navController.navigate(Routes.LIST) { popUpTo(Routes.SIGN_IN) { inclusive = true } }
        }
    }
    val onAmberSignIn: () -> Unit = {
        // Pre-grant the permissions sync needs, so background signing/encryption doesn't prompt per contact.
        val permissions = listOf(
            Permission(CommandType.SIGN_EVENT, 30078),
            Permission(CommandType.SIGN_EVENT, 5),
            // Blossom photo-upload authorization events (BUD-01), so uploads don't prompt per photo.
            Permission(CommandType.SIGN_EVENT, 24242),
            Permission(CommandType.NIP44_ENCRYPT),
            Permission(CommandType.NIP44_DECRYPT),
        )
        amberLoginLauncher.launch(ExternalSignerLogin.createIntent(permissions))
    }

    // QR contact import: scan → parse as vCard (falls back to the paste grammar) → upsert.
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val content = result.contents ?: return@rememberLauncherForActivityResult
        val imported = runCatching { VCardIo.parse(content.byteInputStream()) }
            .getOrDefault(emptyList())
            .ifEmpty { PasteImport.parse(content).mapNotNull { it.contact } }
        imported.forEach { contacts.upsert(it) }
        Toast.makeText(
            context,
            if (imported.isEmpty()) "No contact found in QR code" else "Imported ${imported.size} contact(s)",
            Toast.LENGTH_SHORT,
        ).show()
    }
    val onScanQr = {
        scanLauncher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setBeepEnabled(false)
                .setOrientationLocked(false)
                .setPrompt("Scan a contact QR code"),
        )
    }

    // Computed ONCE: if it were reactive to `identity`, creating a key would rebuild the nav
    // graph and reset to LIST, skipping the sign-in screen's backup-key dialog. Screen-to-screen
    // movement is driven by explicit navigation (onSignedIn / onSignOut) instead.
    val start = remember { if (session.identity.value == null) Routes.SIGN_IN else Routes.LIST }

    NavHost(
        navController = navController,
        startDestination = start,
        // Layered "push": forward nav slides the new screen in from the right while the previous
        // one parallax-recedes left; back reverses it.
        enterTransition = { navPushEnter() },
        exitTransition = { navPushExit() },
        popEnterTransition = { navPopEnter() },
        popExitTransition = { navPopExit() },
    ) {
        composable(Routes.SIGN_IN) {
            SignInScreen(
                session = session,
                amberAvailable = amberAvailable,
                onAmberSignIn = onAmberSignIn,
                onSignedIn = {
                    navController.navigate(Routes.LIST) {
                        popUpTo(Routes.SIGN_IN) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.LIST) {
            ContactListScreen(
                contacts = contacts,
                profiles = profiles,
                identity = identity,
                identityPicture = identityPicture,
                themeOverride = themeOverride,
                onCycleTheme = onCycleTheme,
                onScanQr = onScanQr,
                onAdd = { navController.navigate(Routes.ADD) },
                onOpen = { id -> navController.navigate(Routes.detail(id)) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onAbout = { navController.navigate(Routes.ABOUT) },
            )
        }

        composable(Routes.ADD) {
            AddEditContactScreen(
                existing = null,
                profiles = profiles,
                suggestedCategories = suggestedCategories,
                onUploadPhoto = { uri -> contacts.uploadPhoto(uri) },
                onSave = { contacts.upsert(it); navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.DETAIL) { entry ->
            val id = entry.arguments?.getString("contactId").orEmpty()
            ContactDetailScreen(
                contacts = contacts,
                profiles = profiles,
                contactId = id,
                onEdit = { navController.navigate(Routes.edit(id)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.EDIT) { entry ->
            val id = entry.arguments?.getString("contactId")
            val existing = id?.let { contacts.get(it) }
            AddEditContactScreen(
                existing = existing,
                profiles = profiles,
                suggestedCategories = suggestedCategories,
                onUploadPhoto = { uri -> contacts.uploadPhoto(uri) },
                onSave = { contacts.upsert(it); navController.popBackStack() },
                // Delete returns all the way to the list (skips the now-stale detail view).
                onDelete = existing?.let { c ->
                    { contacts.delete(c.id); navController.popBackStack(Routes.LIST, inclusive = false) }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                identity = identity,
                identityPicture = identityPicture,
                syncState = syncState,
                contacts = contactList,
                relays = relays,
                blossomServers = blossomServers,
                onAddRelay = { contacts.addRelay(it) },
                onRemoveRelay = { contacts.removeRelay(it) },
                onToggleRelay = { url, enabled -> contacts.setRelayEnabled(url, enabled) },
                onSetDurability = { url, d -> contacts.setRelayDurability(url, d) },
                onDetectLocalRelay = { contacts.detectLocalRelay() },
                onAddBlossomServer = { contacts.addBlossomServer(it) },
                onRemoveBlossomServer = { contacts.removeBlossomServer(it) },
                onImport = { imported -> imported.forEach { contacts.upsert(it) } },
                onSignOut = {
                    session.signOut()
                    navController.navigate(Routes.SIGN_IN) { popUpTo(0) }
                },
                onWipeAndSignOut = {
                    // Erase the device-local footprint first (needs the active owner still set),
                    // then sign out (clears the key and removes the Android account).
                    contacts.wipeLocalData()
                    session.signOut()
                    navController.navigate(Routes.SIGN_IN) { popUpTo(0) }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}

// --- Navigation transitions: a layered horizontal "push" with parallax ---
private const val NAV_ANIM_MS = 300

private fun navPushEnter() =
    slideInHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth } +
        fadeIn(tween(NAV_ANIM_MS))

private fun navPushExit() =
    slideOutHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth / 4 } +
        fadeOut(tween(NAV_ANIM_MS))

private fun navPopEnter() =
    slideInHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth / 4 } +
        fadeIn(tween(NAV_ANIM_MS))

private fun navPopExit() =
    slideOutHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth } +
        fadeOut(tween(NAV_ANIM_MS))
