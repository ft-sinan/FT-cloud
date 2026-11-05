package com.ftcloud.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ftcloud.app.BuildConfig
import com.ftcloud.app.core.model.AccentColor
import com.ftcloud.app.core.model.AppThemeMode
import com.ftcloud.app.ui.components.BlobShape
import com.ftcloud.app.ui.components.CloudMark
import com.ftcloud.app.ui.components.ExpressiveChip
import com.ftcloud.app.ui.components.ExpressiveStat
import com.ftcloud.app.ui.components.HeaderCircleButton
import com.ftcloud.app.ui.components.MorphingShapeBox
import com.ftcloud.app.ui.components.OrionSectionCard
import com.ftcloud.app.ui.components.expressivePastel
import com.ftcloud.app.ui.components.readableContentColor
import com.ftcloud.app.ui.components.springPress
import com.ftcloud.app.ui.theme.LocalAnimationsEnabled
import com.ftcloud.app.ui.theme.ftCloudColorScheme
import com.ftcloud.app.ui.theme.isDarkScheme
import com.ftcloud.app.ui.theme.localSelectionHaptic
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal enum class SettingsPage {
    HUB,
    APPEARANCE,
    MOTION,
    SWARM,
    SECURITY,
    COLLECTIONS,
    STORAGE,
    ACKNOWLEDGEMENTS,
}

/** Orion-style Settings Hub: quick-toggle morphing pills + pastel bento grid. */
@Composable
internal fun SettingsHubHeader(
    onBack: () -> Unit,
    channelTitle: String,
    botCount: Int,
    fileCount: Int,
    starredCount: Int,
    trashedCount: Int,
    encryptionEnabled: Boolean,
    biometricChecked: Boolean,
    onBiometricChange: (Boolean) -> Unit,
    dynamicChecked: Boolean,
    dynamicEnabled: Boolean,
    onDynamicChange: (Boolean) -> Unit,
    hapticsChecked: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    animationsChecked: Boolean,
    onAnimationsChange: (Boolean) -> Unit,
    compactChecked: Boolean,
    onCompactChange: (Boolean) -> Unit,
    glassChecked: Boolean,
    onGlassChange: (Boolean) -> Unit,
    themeMode: AppThemeMode,
    accentColor: AccentColor,
    storageSummary: String,
    onJump: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            CloudMark(size = 42.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Settings",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "FT Cloud v${BuildConfig.VERSION_NAME} · $botCount-Bot Swarm on ${channelTitle.ifBlank { "Telegram" }} · $fileCount ${if (fileCount == 1) "file" else "files"}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Quick toggles",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickToggleTile(
                icon = Icons.Filled.Fingerprint,
                label = "Biometric unlock",
                checked = biometricChecked,
                onCheckedChange = onBiometricChange,
                modifier = Modifier.weight(1f),
            )
            QuickToggleTile(
                icon = Icons.Filled.Palette,
                label = "Wallpaper colours",
                checked = dynamicChecked,
                enabled = dynamicEnabled,
                onCheckedChange = onDynamicChange,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickToggleTile(
                icon = Icons.Filled.Vibration,
                label = "Haptic feedback",
                checked = hapticsChecked,
                onCheckedChange = onHapticsChange,
                modifier = Modifier.weight(1f),
            )
            QuickToggleTile(
                icon = Icons.Filled.BlurOn,
                label = "Glass dock",
                checked = glassChecked,
                onCheckedChange = onGlassChange,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickToggleTile(
                icon = Icons.Filled.Animation,
                label = "Animations",
                checked = animationsChecked,
                onCheckedChange = onAnimationsChange,
                modifier = Modifier.weight(1f),
            )
            QuickToggleTile(
                icon = Icons.Filled.Compress,
                label = "Compact mode",
                checked = compactChecked,
                onCheckedChange = onCompactChange,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Everything else",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))

        SettingsBentoCard(
            icon = Icons.Filled.Palette,
            title = "Appearance",
            subtitle = if (dynamicChecked) {
                "Wallpaper colours · ${themeMode.label}"
            } else {
                "${accentColor.label} · ${themeMode.label}"
            },
            seed = Color(0xFF6B48BC),
            onClick = { onJump(SettingsPage.APPEARANCE) },
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(196.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsBentoCard(
                icon = Icons.Filled.CloudSync,
                title = "Swarm & Sync",
                subtitle = "$botCount swarm ${if (botCount == 1) "bot" else "bots"} · QR/PIN phone sync & scanner",
                seed = Color(0xFF0077A8),
                onClick = { onJump(SettingsPage.SWARM) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsBentoCard(
                    icon = Icons.Filled.Lock,
                    title = "Security",
                    subtitle = if (encryptionEnabled) "AES-256-GCM active" else "Encryption & app lock",
                    seed = Color(0xFF087A4E),
                    onClick = { onJump(SettingsPage.SECURITY) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                SettingsBentoCard(
                    icon = Icons.Filled.Star,
                    title = "Collections",
                    subtitle = "$starredCount starred · $trashedCount in trash",
                    seed = Color(0xFF4C4ECD),
                    onClick = { onJump(SettingsPage.COLLECTIONS) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsBentoCard(
                icon = Icons.Filled.Storage,
                title = "Storage & Updates",
                subtitle = storageSummary,
                seed = Color(0xFFC65D00),
                onClick = { onJump(SettingsPage.STORAGE) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            SettingsBentoCard(
                icon = Icons.Filled.TouchApp,
                title = "Motion & Feel",
                subtitle = "Touch, spacing, glass & layout",
                seed = Color(0xFFB03070),
                onClick = { onJump(SettingsPage.MOTION) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
internal fun QuickToggleTile(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val corner by animateDpAsState(
        targetValue = if (checked) 36.dp else 22.dp,
        animationSpec = if (animationsEnabled) {
            spring(dampingRatio = 0.68f, stiffness = 420f)
        } else {
            snap()
        },
        label = "quickToggleCorner",
    )
    val shape = RoundedCornerShape(corner)
    val container by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = if (animationsEnabled) tween(220) else snap(),
        label = "quickToggleContainer",
    )
    val contentColor = if (checked) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .height(78.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.48f }
            .clip(shape)
            .background(container)
            .springPress(shape, enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (checked) "On" else "Off",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.82f),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SettingsBentoCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    seed: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    val containerColor = expressivePastel(seed)
    val contentColor = readableContentColor(containerColor)
    val mutedContentColor = contentColor.copy(alpha = 0.72f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .springPress(shape, onClick = onClick),
    ) {
        MorphingShapeBox(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 28.dp, y = (-32).dp)
                .size(92.dp),
            color = seed.copy(alpha = 0.17f),
            lobes = 12,
            wave = 0.18f,
            spinMillis = 26_000,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            MorphingShapeBox(
                modifier = Modifier.size(42.dp),
                color = seed.copy(alpha = 0.24f),
                lobes = 9,
                wave = 0.13f,
                spinMillis = 22_000,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedContentColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun FeelToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    if (showDivider) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Composable
internal fun AppearancePreviewStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Active surface & accent tokens",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 24.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Aa",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 24.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 24.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.tertiaryContainer),
        )
    }
}

@Composable
internal fun ThemeModePreviews(
    selected: AppThemeMode,
    accentColor: AccentColor,
    dynamicColorEnabled: Boolean,
    onSelect: (AppThemeMode) -> Unit,
) {
    val context = LocalContext.current
    val lightScheme = remember(context, accentColor, dynamicColorEnabled) {
        ftCloudColorScheme(context, darkTheme = false, oled = false, useDynamicColor = dynamicColorEnabled, accentColor = accentColor)
    }
    val darkScheme = remember(context, accentColor, dynamicColorEnabled) {
        ftCloudColorScheme(context, darkTheme = true, oled = false, useDynamicColor = dynamicColorEnabled, accentColor = accentColor)
    }
    val oledScheme = remember(context, accentColor, dynamicColorEnabled) {
        ftCloudColorScheme(context, darkTheme = true, oled = true, useDynamicColor = dynamicColorEnabled, accentColor = accentColor)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppThemeMode.entries.forEach { mode ->
            val scheme = when (mode) {
                AppThemeMode.SYSTEM -> lightScheme
                AppThemeMode.LIGHT -> lightScheme
                AppThemeMode.DARK -> darkScheme
                AppThemeMode.AMOLED -> oledScheme
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MiniPhonePreview(
                    scheme = scheme,
                    splitScheme = if (mode == AppThemeMode.SYSTEM) darkScheme else null,
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (mode == AppThemeMode.AMOLED) "OLED" else mode.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected == mode) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected == mode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MiniPhonePreview(
    scheme: ColorScheme,
    splitScheme: ColorScheme?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val corner by animateDpAsState(
        targetValue = if (selected) 20.dp else 14.dp,
        animationSpec = if (animationsEnabled) tween(180) else snap(),
        label = "themePreviewCorner",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.62f)
            .clip(RoundedCornerShape(corner))
            .background(scheme.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(corner),
            )
            .clickable(onClick = onClick),
    ) {
        if (splitScheme != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .align(Alignment.CenterEnd)
                    .background(splitScheme.surface),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceContainerHigh),
            )
            Spacer(modifier = Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(5.dp))
                        .background(scheme.primaryContainer),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(5.dp))
                        .background(scheme.tertiaryContainer),
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.onSurface.copy(alpha = 0.45f)),
            )
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.onSurface.copy(alpha = 0.3f)),
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(11.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.primary),
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

/** Orion split-circle colour palette swatches (primary over secondaryContainer | tertiary). */
@Composable
internal fun PaletteSwatches(
    selected: AccentColor,
    dynamicColorActive: Boolean,
    onSelect: (AccentColor) -> Unit,
) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.isDarkScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccentColor.entries.forEach { accent ->
            val scheme = remember(context, accent, dark) {
                ftCloudColorScheme(
                    context = context,
                    darkTheme = dark,
                    oled = false,
                    useDynamicColor = false,
                    accentColor = accent,
                )
            }
            val isSelected = !dynamicColorActive && selected == accent
            Column(
                modifier = Modifier.width(62.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .clickable { onSelect(accent) },
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(scheme.primary),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(scheme.secondaryContainer),
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(scheme.tertiary),
                            )
                        }
                    }
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(scheme.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = accent.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Full-width 3D Creator Coin stage: the playful signature area at the bottom of Settings Hub. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun CreatorSpace(
    onAcknowledgements: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = localSelectionHaptic()
    val animationsEnabled = LocalAnimationsEnabled.current

    var flipped by remember { mutableStateOf(false) }
    var selectedBadge by remember { mutableStateOf<Int?>(null) }
    var tiltTargetX by remember { mutableFloatStateOf(0f) }
    var tiltTargetY by remember { mutableFloatStateOf(0f) }

    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = if (animationsEnabled) tween(680) else snap(),
        label = "creatorCoinFlip",
    )
    val tiltX by animateFloatAsState(
        targetValue = tiltTargetX,
        animationSpec = if (animationsEnabled) {
            spring(dampingRatio = 0.62f, stiffness = 420f)
        } else {
            snap()
        },
        label = "creatorCoinTiltX",
    )
    val tiltY by animateFloatAsState(
        targetValue = tiltTargetY,
        animationSpec = if (animationsEnabled) {
            spring(dampingRatio = 0.62f, stiffness = 420f)
        } else {
            snap()
        },
        label = "creatorCoinTiltY",
    )

    val creatorTransition = rememberInfiniteTransition(label = "creatorStage")
    val orbitAngle by creatorTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "creatorOrbit",
    )
    val shineOffset by creatorTransition.animateFloat(
        initialValue = -170f,
        targetValue = 170f,
        animationSpec = infiniteRepeatable(
            animation = tween(5_600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "creatorCoinShine",
    )
    val effectiveOrbitAngle = if (animationsEnabled) orbitAngle else 0.65f
    val effectiveShine = if (animationsEnabled) shineOffset else -170f

    LaunchedEffect(flipped) {
        if (flipped) {
            delay(2_400)
            flipped = false
        }
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun copyCreatorText(value: String) {
        clipboardManager.setText(AnnotatedString(value))
        haptic()
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(36.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f),
                        MaterialTheme.colorScheme.surfaceContainerLow,
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        MorphingShapeBox(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 62.dp, y = (-84).dp)
                .size(210.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            lobes = 12,
            wave = 0.19f,
            spinMillis = 30_000,
        )
        MorphingShapeBox(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-72).dp, y = 76.dp)
                .size(170.dp),
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
            lobes = 11,
            wave = 0.17f,
            spinMillis = 34_000,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ExpressiveChip(
                text = "CREATOR SPACE",
                icon = Icons.Filled.Star,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
                modifier = Modifier.align(Alignment.Start),
            )

            Box(
                modifier = Modifier.size(width = 276.dp, height = 228.dp),
                contentAlignment = Alignment.Center,
            ) {
                MorphingShapeBox(
                    modifier = Modifier.size(204.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.58f),
                    lobes = 12,
                    wave = 0.19f,
                    spinMillis = 24_000,
                )
                Box(
                    modifier = Modifier
                        .size(152.dp)
                        .graphicsLayer {
                            rotationY = rotation + tiltX
                            rotationX = -tiltY
                            cameraDistance = 18.dp.toPx()
                        }
                        .pointerInput(animationsEnabled) {
                            detectTapGestures(
                                onPress = { offset ->
                                    if (animationsEnabled) {
                                        val centerX = size.width / 2f
                                        val centerY = size.height / 2f
                                        if (centerX > 0f) {
                                            tiltTargetX =
                                                ((offset.x - centerX) / centerX)
                                                    .coerceIn(-1f, 1f) * 9f
                                        }
                                        if (centerY > 0f) {
                                            tiltTargetY =
                                                ((offset.y - centerY) / centerY)
                                                    .coerceIn(-1f, 1f) * 9f
                                        }
                                    }
                                    tryAwaitRelease()
                                    tiltTargetX = 0f
                                    tiltTargetY = 0f
                                },
                                onTap = {
                                    haptic()
                                    flipped = !flipped
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (rotation <= 90f) {
                        MorphingShapeBox(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            lobes = 12,
                            wave = 0.16f,
                            spinMillis = 18_000,
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(x = effectiveShine.dp)
                                    .width(34.dp)
                                    .fillMaxHeight()
                                    .graphicsLayer { rotationZ = 18f }
                                    .background(Color.White.copy(alpha = 0.14f)),
                            )
                            Text(
                                text = "S",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationY = 180f }
                                .clip(BlobShape(lobes = 12, wave = 0.16f))
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(60.dp),
                            )
                        }
                    }
                }

                listOf(Icons.Filled.Star, Icons.Filled.Code, Icons.Filled.Shield)
                    .forEachIndexed { index, icon ->
                        val angle = effectiveOrbitAngle + index * ((2f * PI.toFloat()) / 3f)
                        CreatorOrbitBadge(
                            icon = icon,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(
                                    x = (cos(angle) * 106f).dp,
                                    y = (sin(angle) * 80f).dp,
                                ),
                        )
                    }
            }

            Text(
                text = if (flipped) "Unlimited by design" else "Sinan",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (flipped) {
                Text(
                    text = "Encrypted · Decentralized · Swarm-Powered",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            } else {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .springPress(CircleShape) { copyCreatorText("@ft-sinan") }
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "@ft-sinan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Designed & built by",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "I built FT Cloud to turn your private Telegram channel into an unlimited, encrypted personal cloud drive — powered by a parallel Bot Swarm with zero servers and zero tracking.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap the emblem",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CreatorBadgeChip(
                    text = "Creator",
                    icon = Icons.Filled.Star,
                    selected = selectedBadge == 0,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { selectedBadge = if (selectedBadge == 0) null else 0 },
                )
                CreatorBadgeChip(
                    text = "Privacy first",
                    icon = Icons.Filled.Shield,
                    selected = selectedBadge == 1,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { selectedBadge = if (selectedBadge == 1) null else 1 },
                )
                CreatorBadgeChip(
                    text = "Bot Swarm",
                    icon = Icons.Filled.Cloud,
                    selected = selectedBadge == 2,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { selectedBadge = if (selectedBadge == 2) null else 2 },
                )
                CreatorBadgeChip(
                    text = "Kotlin · Android",
                    icon = Icons.Filled.Code,
                    selected = selectedBadge == 3,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = { selectedBadge = if (selectedBadge == 3) null else 3 },
                )
            }

            val selectedBadgeDescription = when (selectedBadge) {
                0 -> "Designed and built end-to-end by Sinan — product, expressive UI, encryption, and parallel Bot Swarm architecture."
                1 -> "No middleman servers, no telemetry, and optional client-side AES-256-GCM chunk encryption before data ever leaves your phone."
                2 -> "Parallel multi-bot load balancing splits large uploads, downloads, and channel scans across your Telegram Bot Swarm."
                3 -> "Native Kotlin with Jetpack Compose, Material 3 Expressive motion, Room offline index, Media3 streaming, and Android Keystore."
                else -> null
            }
            if (selectedBadgeDescription != null) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.68f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = selectedBadgeDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.68f))
                    .padding(vertical = 14.dp, horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExpressiveStat(
                    value = BuildConfig.VERSION_NAME,
                    label = "Version",
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .width(1.dp)
                        .height(34.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                ExpressiveStat(
                    value = "Android",
                    label = "Platform",
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .width(1.dp)
                        .height(34.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                ExpressiveStat(
                    value = "Kotlin",
                    label = "Built with",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Connect",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Long-press a tile to copy",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CreatorConnectTile(
                    icon = Icons.Filled.Code,
                    label = "GitHub",
                    modifier = Modifier.weight(1f),
                    onClick = { openUrl("https://github.com/ft-sinan") },
                    onLongClick = { copyCreatorText("https://github.com/ft-sinan") },
                )
                CreatorConnectTile(
                    icon = Icons.Filled.Send,
                    label = "Telegram",
                    modifier = Modifier.weight(1f),
                    onClick = { openUrl("https://t.me/Exotatic") },
                    onLongClick = { copyCreatorText("@Exotatic") },
                )
                CreatorConnectTile(
                    icon = Icons.Filled.Chat,
                    label = "WhatsApp",
                    modifier = Modifier.weight(1f),
                    onClick = { openUrl("https://wa.me/917558874118") },
                    onLongClick = { copyCreatorText("+91 7558874118") },
                )
                CreatorConnectTile(
                    icon = Icons.Filled.Email,
                    label = "Email",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:Mail.tosinaan@gmail.com"))
                            )
                        }
                    },
                    onLongClick = { copyCreatorText("Mail.tosinaan@gmail.com") },
                )
            }

            Spacer(Modifier.height(10.dp))
            val websiteShape = RoundedCornerShape(24.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp)
                    .clip(websiteShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                    .springPress(websiteShape) { openUrl("https://sinanmk.my.id") }
                    .padding(horizontal = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MorphingShapeBox(
                        modifier = Modifier.size(42.dp),
                        color = MaterialTheme.colorScheme.primary,
                        lobes = 9,
                        wave = 0.13f,
                    ) {
                        Icon(
                            Icons.Filled.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Visit my website",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Sinanmk.my.id",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.OpenInNew, contentDescription = null)
                }
            }

            Spacer(Modifier.height(14.dp))
            TextButton(onClick = onAcknowledgements) {
                Icon(
                    Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Acknowledgements & licences")
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "made with 🤍 by sinan",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CreatorBadgeChip(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.primary else containerColor
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else contentColor
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .springPress(CircleShape, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CreatorConnectTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = localSelectionHaptic()
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .height(82.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onLongClick = {
                    haptic()
                    onLongClick()
                },
                onClick = {
                    haptic()
                    onClick()
                },
            )
            .padding(horizontal = 6.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MorphingShapeBox(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.primary,
                lobes = 9,
                wave = 0.13f,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CreatorOrbitBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
internal fun AcknowledgementsContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AcknowledgementCard(
            title = "Orion Store — design inspiration",
            body = "Special thanks to RookieZ (RookieEnough) and the Orion Store project for the expressive Material 3 design inspiration behind FT Cloud's interface. FT Cloud is an independent project and is not affiliated with or endorsed by Orion Store.",
            icon = Icons.Filled.Star,
            url = "https://github.com/RookieEnough/Orion-Store",
        )
        AcknowledgementCard(
            title = "Space Grotesk",
            body = "Bundled geometric display & headline typeface under the SIL Open Font License 1.1.",
            icon = Icons.Filled.Palette,
            url = "https://fonts.google.com/specimen/Space+Grotesk",
        )
        AcknowledgementCard(
            title = "AndroidX & Jetpack Compose",
            body = "Compose, Material 3, Room, Navigation, DataStore, WorkManager, Media3 ExoPlayer and Biometric libraries by Google under Apache License 2.0.",
            icon = Icons.Filled.Code,
            url = "https://developer.android.com/jetpack/androidx",
        )
        AcknowledgementCard(
            title = "Coil & ZXing",
            body = "Asynchronous media thumbnails via Coil and QR code pairing generation/scanning via ZXing under Apache License 2.0.",
            icon = Icons.Filled.Info,
            url = "https://coil-kt.github.io/coil/",
        )
        AcknowledgementCard(
            title = "FT Cloud · Open Source",
            body = "FT Cloud source code and signed releases are published openly on GitHub by Sinan (@ft-sinan).",
            icon = Icons.Filled.WorkspacePremium,
            url = "https://github.com/ft-sinan/FT-cloud",
        )
    }
}

@Composable
private fun AcknowledgementCard(
    title: String,
    body: String,
    icon: ImageVector,
    url: String,
) {
    val context = LocalContext.current
    OrionSectionCard(title = title, icon = icon) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            },
        ) {
            Text("Open project")
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}
