package com.ftcloud.app.ui.screens.onboarding

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ftcloud.app.core.pairing.DevicePairingManager
import com.ftcloud.app.di.FTCloudContainer
import com.ftcloud.app.ui.components.FTCloudShieldMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OnboardingScreen(
    container: FTCloudContainer,
    onConnected: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var channelInput by rememberSaveable { mutableStateOf("") }
    val botTokens = remember { mutableStateListOf("") }
    var showTokenPlaintext by rememberSaveable { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectingStatus by remember { mutableStateOf("Verifying Bot Swarm & Channel…") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showPastePairingDialog by remember { mutableStateOf(false) }
    var pairingKeyInput by rememberSaveable { mutableStateOf("") }
    var pairingPinInput by rememberSaveable { mutableStateOf("") }
    var pairingDialogError by remember { mutableStateOf<String?>(null) }

    var pendingEncryptedRestorePrompt by remember { mutableStateOf(false) }
    var restorePassphrase by rememberSaveable { mutableStateOf("") }
    var isRestoring by remember { mutableStateOf(false) }
    var restoreError by remember { mutableStateOf<String?>(null) }

    suspend fun finishConnectionWithAutoSync() {
        connectingStatus = "Syncing cloud index & media catalog…"
        val inspection = runCatching { container.manifestSync.inspectPinnedManifest() }.getOrNull()
        if (inspection != null) {
            if (!inspection.encrypted) {
                runCatching { container.driveRepository.restoreManifestFromChannel() }
                runCatching { container.channelMediaImporter.syncPendingBotUpdates() }
                isConnecting = false
                onConnected()
            } else {
                isConnecting = false
                pendingEncryptedRestorePrompt = true
            }
        } else {
            runCatching { container.channelMediaImporter.syncPendingBotUpdates() }
            isConnecting = false
            onConnected()
        }
    }

    fun handleQrStringDecoded(qrText: String) {
        isConnecting = true
        errorMessage = null
        connectingStatus = "Linking phone & verifying Bot Swarm…"
        scope.launch {
            runCatching {
                container.driveRepository.connectViaPairingInput(rawInput = qrText)
            }.onSuccess {
                finishConnectionWithAutoSync()
            }.onFailure { err ->
                isConnecting = false
                errorMessage = err.message ?: "Failed to pair from QR code."
            }
        }
    }

    val cameraQrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap == null) return@rememberLauncherForActivityResult
        scope.launch {
            val decoded = withContext(Dispatchers.Default) {
                DevicePairingManager.decodeQrBitmap(bitmap)
            }
            if (decoded != null) {
                handleQrStringDecoded(decoded)
            } else {
                errorMessage = "Could not detect an FT Cloud QR code in that photo. Move closer to Phone 1's screen or use Paste Pairing Key."
            }
        }
    }

    val imageQrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }?.let { bmp ->
                    DevicePairingManager.decodeQrBitmap(bmp)
                }
            }
            if (decoded != null) {
                handleQrStringDecoded(decoded)
            } else {
                errorMessage = "No valid FT Cloud QR code found in the selected image."
            }
        }
    }

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
                FTCloudShieldMark(size = 62.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "FT Cloud",
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Text(
                        text = "Unlimited personal cloud drive powered by your Telegram Bot Swarm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Instant Second-Phone Login Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.PhonelinkSetup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Logging in on a Second Phone?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "On your first phone, open FT Cloud → Settings → Link a Second Phone. All your folders & imported media restore automatically in ~1 second (no re-scanning!).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !isConnecting,
                            onClick = { cameraQrLauncher.launch(null) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scan QR")
                        }
                        FilledTonalButton(
                            enabled = !isConnecting,
                            onClick = {
                                val clip = clipboard.getText()?.text.orEmpty()
                                if (pairingKeyInput.isBlank() && clip.contains("FTCLOUD1:")) {
                                    pairingKeyInput = clip.trim()
                                }
                                pairingDialogError = null
                                showPastePairingDialog = true
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pairing Key")
                        }
                    }

                    OutlinedButton(
                        enabled = !isConnecting,
                        onClick = { imageQrLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import QR Screenshot from Gallery")
                    }
                }
            }

            HorizontalDivider()

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
                                text = "Manual Setup (First Phone)",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        TextButton(onClick = { showGuide = !showGuide }) {
                            Text(if (showGuide) "Hide guide" else "60s guide")
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
                    connectingStatus = "Verifying Bot Swarm & Channel…"
                    scope.launch {
                        runCatching {
                            container.swarmManager.verifyAndConnectSwarm(
                                rawChannelId = channelInput,
                                rawTokens = botTokens.toList(),
                            )
                        }.onSuccess {
                            finishConnectionWithAutoSync()
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
                    Text(connectingStatus)
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

    if (showPastePairingDialog) {
        AlertDialog(
            onDismissRequest = { if (!isConnecting) showPastePairingDialog = false },
            icon = { Icon(Icons.Outlined.PhonelinkSetup, contentDescription = null) },
            title = { Text("Login with Pairing Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Paste the encrypted FTCLOUD1:... key shared from your first phone and enter the 6-digit PIN shown on its screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = pairingKeyInput,
                        onValueChange = {
                            pairingKeyInput = it
                            pairingDialogError = null
                        },
                        label = { Text("Encrypted Pairing Key (FTCLOUD1:...)") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (DevicePairingManager.requiresManualPin(pairingKeyInput)) {
                        OutlinedTextField(
                            value = pairingPinInput,
                            onValueChange = {
                                pairingPinInput = it.filter { ch -> ch.isDigit() }.take(6)
                                pairingDialogError = null
                            },
                            label = { Text("6-Digit Pairing PIN") },
                            placeholder = { Text("123456") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (pairingDialogError != null) {
                        Text(
                            text = pairingDialogError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isConnecting && pairingKeyInput.isNotBlank(),
                    onClick = {
                        isConnecting = true
                        pairingDialogError = null
                        connectingStatus = "Decrypting pairing key & connecting Swarm…"
                        scope.launch {
                            runCatching {
                                container.driveRepository.connectViaPairingInput(
                                    rawInput = pairingKeyInput,
                                    manualPin = pairingPinInput,
                                )
                            }.onSuccess {
                                showPastePairingDialog = false
                                finishConnectionWithAutoSync()
                            }.onFailure { err ->
                                isConnecting = false
                                pairingDialogError = err.message ?: "Could not pair device."
                            }
                        }
                    },
                ) {
                    Text(if (isConnecting) "Connecting…" else "Unlock & Login")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isConnecting,
                    onClick = { showPastePairingDialog = false },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    if (pendingEncryptedRestorePrompt) {
        AlertDialog(
            onDismissRequest = { if (!isRestoring) onConnected() },
            icon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
            title = { Text("Unlock Encrypted Cloud Index") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Your channel's pinned drive index is protected with a Vault passphrase. Enter it once to restore all your folders, photos, and files:"
                    )
                    OutlinedTextField(
                        value = restorePassphrase,
                        onValueChange = {
                            restorePassphrase = it
                            restoreError = null
                        },
                        label = { Text("Vault passphrase") },
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
                    enabled = !isRestoring && restorePassphrase.isNotBlank(),
                    onClick = {
                        isRestoring = true
                        restoreError = null
                        scope.launch {
                            runCatching {
                                container.driveRepository.configureVaultPassphrase(restorePassphrase)
                                container.driveRepository.restoreManifestFromChannel(restorePassphrase)
                            }.onSuccess {
                                isRestoring = false
                                pendingEncryptedRestorePrompt = false
                                onConnected()
                            }.onFailure { err ->
                                isRestoring = false
                                restoreError = err.message ?: "Could not unlock manifest"
                            }
                        }
                    }
                ) {
                    Text(if (isRestoring) "Restoring…" else "Unlock & Restore")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isRestoring,
                    onClick = {
                        pendingEncryptedRestorePrompt = false
                        onConnected()
                    }
                ) {
                    Text("Skip for now")
                }
            },
        )
    }
}
