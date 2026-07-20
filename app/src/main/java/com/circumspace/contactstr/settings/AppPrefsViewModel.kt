package com.circumspace.contactstr.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide UI preferences, persisted in (non-sensitive) SharedPreferences.
 * The theme override survives restarts: null = follow system, true = dark, false = light.
 */
class AppPrefsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("contactstr_prefs", Context.MODE_PRIVATE)

    private val _darkOverride = MutableStateFlow(readTheme())
    val darkOverride: StateFlow<Boolean?> = _darkOverride.asStateFlow()

    private fun readTheme(): Boolean? = when (prefs.getString(KEY_THEME, null)) {
        "dark" -> true
        "light" -> false
        else -> null
    }

    /** Set an explicit theme, or null to follow the system. */
    fun setTheme(dark: Boolean?) {
        val value = when (dark) {
            true -> "dark"
            false -> "light"
            null -> null
        }
        prefs.edit().apply { if (value == null) remove(KEY_THEME) else putString(KEY_THEME, value) }.apply()
        _darkOverride.value = dark
    }

    /** Cycle System → Light → Dark → System. */
    fun cycleTheme() {
        setTheme(
            when (_darkOverride.value) {
                null -> false   // System → Light
                false -> true   // Light → Dark
                true -> null    // Dark → System
            },
        )
    }

    private companion object {
        const val KEY_THEME = "theme"
    }
}
