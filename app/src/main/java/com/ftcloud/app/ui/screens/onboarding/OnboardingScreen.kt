package com.ftcloud.app.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ftcloud.app.core.model.CloudManifestEnvelope
import com.ftcloud.app.di.FTCloudContainer
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    container: FTCloudContainer,
    onConnected: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var channelInput by rememberSaveable { mutableStateOf("") }
    val botTokens = remember { mutableStateListOf("") }
    var showTokenPlaintext by rememberSaveable { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(true) }
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var pendingRestorePrompt by remember { mutableStateOf(false) }
    var restorePassphrase by rememberSaveable { mutableStateOf("") }
    var isRestoring by remember { mutableStateOf(false) }
    var restoreError by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "FT-Cloud",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "Unlimited personal drive powered by your Telegram Bot Swarm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.HelpOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "60-Second Setup Guide",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        TextButton(onClick = { showGuide = !showGuide }) {
                            Text(if (showGuide) "Hide" else "Show")
                        }
                    }

                    AnimatedVisibility(visible = showGuide) {
                        Column(
                            modifier = Modifier.padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "1. Open @BotFather in Telegram → send /newbot → copy your Bot Token.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "2. Create a private Telegram Channel and add your bot(s) as Administrators (with Post, Edit, Delete & Pin permissions).",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "3. Copy the Channel ID (e.g. -1001234567890) or @username. Optional: add 2+ bots for parallel Swarm speeds & automatic FloodWait failover!",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = channelInput,
                onValueChange = {
                    channelInput = it
                    errorMessage = null
                },
                label = { Text("Telegram Channel ID or @username") },
                placeholder = { Text("-1001234567890") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Bot Swarm Tokens (${botTokens.size})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                TextButton(onClick = { showTokenPlaintext = !showTokenPlaintext }) {
                    Text(if (showTokenPlaintext) "Hide tokens" else "Show tokens")
                }
            }

            botTokens.forEachIndexed { index, token ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { updated ->
                            botTokens[index] = updated
                            errorMessage = null
                        },
                        label = { Text("Bot #${index + 1} Token") },
                        placeholder = { Text("123456789:AAH...") },
                        singleLine = true,
                        visualTransformation = if (showTokenPlaintext) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (botTokens.size > 1) {
                        IconButton(onClick = { botTokens.removeAt(index) }) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove bot",
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { botTokens.add("") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add Another Swarm Bot (Optional)")
            }

            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    isConnecting = true
                    errorMessage = null
                    scope.launch {
                        runCatching {
                            container.swarmManager.verifyAndConnectSwarm(
                                rawChannelId = channelInput,
                                rawTokens = botTokens.toList(),
                            )
                        }.onSuccess { result ->
                            isConnecting = false
                            val pinned = result.pinnedManifestMessage
                            val hasExistingManifest = pinned?.document?.fileName == CloudManifestEnvelope.MANIFEST_FILE_NAME ||
                                pinned?.caption?.contains("#ftcloud_manifest") == true
                            if (hasExistingManifest) {
                                pendingRestorePrompt = true
                            } else {
                                onConnected()
                            }
                        }.onFailure { err ->
                            isConnecting = false
                            errorMessage = err.message ?: "Failed to connect to Telegram channel."
                        }
                    }
                },
                enabled = !isConnecting && channelInput.isNotBlank() && botTokens.any { it.isNotBlank() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Verifying Bot Swarm & Channel…")
                } else {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Connect Telegram Cloud", fontWeight = FontWeight.SemiBold)
                }
            }

            Text(
                text = "Zero third-party servers: your bot tokens are encrypted with Android Keystore and talk strictly to api.telegram.org.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (pendingRestorePrompt) {
        AlertDialog(
            onDismissRequest = { if (!isRestoring) onConnected() },
            icon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
            title = { Text("Existing Cloud Drive Found!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "FT-Cloud detected a pinned drive index in this Telegram channel. Restore all your folders, photos, and files onto this device?"
                    )
                    OutlinedTextField(
                        value = restorePassphrase,
                        onValueChange = {
                            restorePassphrase = it
                            restoreError = null
                        },
                        label = { Text("Vault passphrase (leave blank if unencrypted)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (restoreError != null) {
                        Text(
                            text = restoreError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    enabled = !isRestoring,
                    onClick = {
                        isRestoring = true
                        restoreError = null
                        scope.launch {
                            runCatching {
                                if (restorePassphrase.isNotBlank()) {
                                    container.driveRepository.configureVaultPassphrase(restorePassphrase)
                                }
                                container.driveRepository.restoreManifestFromChannel(restorePassphrase)
                            }.onSuccess {
                                isRestoring = false
                                pendingRestorePrompt = false
                                onConnected()
                            }.onFailure { err ->
                                isRestoring = false
                                restoreError = err.message ?: "Could not restore manifest"
                            }
                        }
                    }
                ) {
                    Text(if (isRestoring) "Restoring…" else "Restore Drive")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isRestoring,
                    onClick = {
                        pendingRestorePrompt = false
                        onConnected()
                    }
                ) {
                    Text("Skip for now")
                }
            },
        )
    }
}
