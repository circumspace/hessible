package com.circumspace.contactstr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.circumspace.contactstr.data.ContactsViewModel
import com.circumspace.contactstr.data.nostr.ProfileViewModel
import com.circumspace.contactstr.session.SessionViewModel
import com.circumspace.contactstr.settings.AppPrefsViewModel
import com.circumspace.contactstr.ui.ContactstrApp
import com.circumspace.contactstr.ui.theme.ContactstrTheme

class MainActivity : ComponentActivity() {
    private val runtimePermissions = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        // For the app-owned "Hessible Birthdays" calendar; denial just skips calendar sync.
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    private val requestRuntimePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* sync proceeds once granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureRuntimePermissions()
        enableEdgeToEdge()
        setContent {
            // Activity-scoped so session, contacts, and prefs are shared across destinations.
            val prefs: AppPrefsViewModel = viewModel()
            val darkOverride by prefs.darkOverride.collectAsStateWithLifecycle()
            val dark = darkOverride ?: isSystemInDarkTheme()

            // Keep the system bar icons legible: light icons on a dark app theme and vice versa.
            // Must track the in-app theme override, not just the system setting.
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }

            ContactstrTheme(darkTheme = dark) {
                // Themed root so screen-transition gaps show the app background (not a white
                // window flash in dark mode).
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val session: SessionViewModel = viewModel()
                    val contacts: ContactsViewModel = viewModel()
                    val profiles: ProfileViewModel = viewModel()
                    ContactstrApp(
                        session = session,
                        contacts = contacts,
                        profiles = profiles,
                        isDark = dark,
                        themeOverride = darkOverride,
                        onCycleTheme = { prefs.cycleTheme() },
                    )
                }
            }
        }
    }

    private fun ensureRuntimePermissions() {
        val missing = runtimePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) requestRuntimePermissions.launch(missing.toTypedArray())
    }
}
