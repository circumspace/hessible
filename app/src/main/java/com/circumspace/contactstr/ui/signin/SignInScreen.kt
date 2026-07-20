package com.circumspace.contactstr.ui.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.circumspace.contactstr.R
import com.circumspace.contactstr.crypto.NostrIdentity
import com.circumspace.contactstr.session.SessionViewModel

@Composable
fun SignInScreen(
    session: SessionViewModel,
    amberAvailable: Boolean,
    onAmberSignIn: () -> Unit,
    onSignedIn: () -> Unit,
) {
    var nsecInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var justCreated by remember { mutableStateOf<NostrIdentity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your contacts, encrypted on Nostr — readable only by your key.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(40.dp))

        Button(
            onClick = { justCreated = session.createNewIdentity() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Text("  Create a new identity")
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text("or sign in with an existing key", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = nsecInput,
            onValueChange = { nsecInput = it; error = null },
            label = { Text("nsec…") },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                if (!session.signInWithNsec(nsecInput)) error = "That doesn't look like a valid nsec."
                else onSignedIn()
            },
            enabled = nsecInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Key, contentDescription = null)
            Text("  Sign in")
        }

        Spacer(Modifier.height(24.dp))
        if (amberAvailable) {
            OutlinedButton(onClick = onAmberSignIn, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Lock, contentDescription = null)
                Text("  Sign in with Amber")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Recommended — the app never sees your key; Amber signs on its behalf.",
                style = MaterialTheme.typography.labelSmall,
            )
        } else {
            Text(
                "Install Amber (a NIP-55 signer) to sign in without sharing your key with the app.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }

    val created = justCreated
    if (created != null) {
        BackupKeyDialog(
            identity = created,
            onConfirm = { justCreated = null; onSignedIn() },
        )
    }
}

@Composable
private fun BackupKeyDialog(
    identity: NostrIdentity,
    onConfirm: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = { /* force an explicit acknowledgement */ },
        title = { Text("Back up your key") },
        text = {
            Column {
                Text(
                    "This nsec is the ONLY way to read your contacts. There is no recovery. " +
                        "Save it somewhere safe before continuing.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Text("Public key (npub):", style = MaterialTheme.typography.labelMedium)
                SelectionContainer { Text(identity.npub, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(12.dp))
                Text("Private key (nsec):", style = MaterialTheme.typography.labelMedium)
                SelectionContainer { Text(identity.nsec ?: "", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    identity.nsec?.let { clipboard.setText(AnnotatedString(it)) }
                }) { Text("Copy nsec") }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("I've saved it — continue") }
        },
    )
}
