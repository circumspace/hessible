package com.circumspace.contactstr.ui.about

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.circumspace.contactstr.R

private const val APP_VERSION = "0.1.0"
private const val AUTHOR = "hermeticvm"
private const val NOSTR_URL = "https://njump.me/npub1rfw075gc6pc693w5v568xw4mnu7umlzpkfxmqye0cgxm7qw8tauqfck3t8"
private const val SOURCE_URL = "https://github.com/circumspace"
private const val LIGHTNING_ADDRESS = "hermeticvm@minibits.cash"
private const val MONERO_ADDRESS = "8AuPVyudY9hRedjkRzCisrDq5rnzbUvCTckcQr5dUaGWa1yzo77uMUP8LPpSQvPBbGEktHpPqkHFPdXuCYBEL6iz9kXAhFW"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val appName = stringResource(R.string.app_name)
    var showCredits by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }

    fun open(url: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("About") },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Text(appName, style = MaterialTheme.typography.headlineSmall)
            Text("Version $APP_VERSION", style = MaterialTheme.typography.labelMedium)
            Text(
                "Your contacts, encrypted on Nostr relays — readable only by your key " +
                    "(NIP-44 encryption, stored as NIP-78 app data).",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            // ── Credits ──────────────────────────────────────────────────────
            TextButton(onClick = { showCredits = !showCredits }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showCredits) "Hide Credits" else "Show Credits", style = MaterialTheme.typography.bodyLarge)
            }
            AnimatedVisibility(
                visible = showCredits,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Credits", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))

                        Text("Author", style = MaterialTheme.typography.labelMedium)
                        Text(AUTHOR, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))

                        Text("Follow on nostr", style = MaterialTheme.typography.labelMedium)
                        Text(
                            NOSTR_URL,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { open(NOSTR_URL) },
                        )
                        Spacer(Modifier.height(8.dp))

                        Text("Source Code", style = MaterialTheme.typography.labelMedium)
                        Text(
                            SOURCE_URL,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { open(SOURCE_URL) },
                        )
                    }
                }
            }

            // ── Support ──────────────────────────────────────────────────────
            TextButton(onClick = { showSupport = !showSupport }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showSupport) "Hide Support" else "Show Support", style = MaterialTheme.typography.bodyLarge)
            }
            AnimatedVisibility(
                visible = showSupport,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Support", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Thanks for your support",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (open("lightning:$LIGHTNING_ADDRESS").isFailure) {
                                            Toast.makeText(context, "No Lightning app found", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("⚡️", style = MaterialTheme.typography.headlineSmall)
                                Spacer(Modifier.width(8.dp))
                                Text(LIGHTNING_ADDRESS, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(LIGHTNING_ADDRESS))
                                Toast.makeText(context, "Lightning address copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Lightning address")
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (open("monero:$MONERO_ADDRESS").isFailure) {
                                            Toast.makeText(context, "No Monero app found", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.monero256),
                                    contentDescription = "Monero",
                                    modifier = Modifier.size(32.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    MONERO_ADDRESS,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(MONERO_ADDRESS))
                                Toast.makeText(context, "Monero address copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Monero address")
                            }
                        }
                    }
                }
            }
        }
    }
}
