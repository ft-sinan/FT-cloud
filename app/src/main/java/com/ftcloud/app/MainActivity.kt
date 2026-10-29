package com.ftcloud.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import com.ftcloud.app.core.model.AppSettings
import com.ftcloud.app.ui.navigation.FTCloudAppShell
import com.ftcloud.app.ui.navigation.IncomingSharePayload
import com.ftcloud.app.ui.theme.FTCloudTheme

class MainActivity : FragmentActivity() {

    private val incomingShareState = mutableStateOf<IncomingSharePayload?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingShareState.value = extractIncomingShare(intent)

        val container = (application as FTCloudApplication).container

        setContent {
            val settings by container.settingsRepository.settingsFlow.collectAsState(
                initial = AppSettings(
                    isConfigured = container.credentialStore.getChannelId().isNotBlank() &&
                        container.credentialStore.getBots().isNotEmpty(),
                    channelId = container.credentialStore.getChannelId(),
                    channelTitle = container.credentialStore.getChannelTitle(),
                    botCount = container.credentialStore.getBots().size,
                )
            )

            LaunchedEffect(settings.blockScreenshots) {
                if (settings.blockScreenshots) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            var unlockedThisSession by rememberSaveable { mutableStateOf(false) }
            val requiresUnlock = settings.biometricLockEnabled && !unlockedThisSession

            LaunchedEffect(requiresUnlock) {
                if (requiresUnlock) {
                    promptBiometricUnlock(
                        onUnlocked = { unlockedThisSession = true },
                    )
                }
            }

            FTCloudTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                if (requiresUnlock) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(72.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("FT-Cloud is Locked", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Authenticate with biometrics or your device screen lock to access your cloud vault.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    promptBiometricUnlock(onUnlocked = { unlockedThisSession = true })
                                }
                            ) {
                                Text("Unlock FT-Cloud")
                            }
                        }
                    }
                } else {
                    FTCloudAppShell(
                        container = container,
                        settings = settings,
                        pendingShare = incomingShareState.value,
                        onClearPendingShare = { incomingShareState.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val extracted = extractIncomingShare(intent)
        if (extracted != null) {
            incomingShareState.value = extracted
        }
    }

    private fun promptBiometricUnlock(onUnlocked: () -> Unit) {
        val biometricManager = BiometricManager.from(this)
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }

        if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            onUnlocked()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock FT-Cloud")
            .setSubtitle("Verify your identity to open your Telegram Cloud Drive")
            .setAllowedAuthenticators(authenticators)
            .build()

        runCatching { prompt.authenticate(promptInfo) }.onFailure { onUnlocked() }
    }

    private fun extractIncomingShare(intent: Intent?): IncomingSharePayload? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val singleUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (singleUri != null || !sharedText.isNullOrBlank()) {
                    IncomingSharePayload(
                        uris = listOfNotNull(singleUri),
                        sharedText = if (singleUri == null) sharedText else null,
                    )
                } else {
                    null
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    .orEmpty()
                if (uris.isNotEmpty()) IncomingSharePayload(uris = uris) else null
            }

            else -> null
        }
    }
}
