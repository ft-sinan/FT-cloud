package com.ftcloud.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ftcloud.app.core.model.AppThemeMode

private val FTLightColorScheme = lightColorScheme(
    primary = Color(0xFF0B6EFD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E6FF),
    onPrimaryContainer = Color(0xFF001A43),
    secondary = Color(0xFF1F6E8C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC7E7F7),
    onSecondaryContainer = Color(0xFF001F2B),
    tertiary = Color(0xFF00875A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC8F5E2),
    onTertiaryContainer = Color(0xFF002114),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF161C24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF161C24),
    surfaceVariant = Color(0xFFE9EEF6),
    onSurfaceVariant = Color(0xFF454F5B),
)

private val FTDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF002F6C),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD8E6FF),
    secondary = Color(0xFF86CFEA),
    onSecondary = Color(0xFF003547),
    secondaryContainer = Color(0xFF0A4D66),
    onSecondaryContainer = Color(0xFFC7E7F7),
    tertiary = Color(0xFF6DD58C),
    onTertiary = Color(0xFF00391C),
    tertiaryContainer = Color(0xFF00522B),
    onTertiaryContainer = Color(0xFFC8F5E2),
    background = Color(0xFF0E141B),
    onBackground = Color(0xFFE3E8EF),
    surface = Color(0xFF151D26),
    onSurface = Color(0xFFE3E8EF),
    surfaceVariant = Color(0xFF222D3A),
    onSurfaceVariant = Color(0xFFB8C2CC),
)

private val FTAmoledColorScheme = FTDarkColorScheme.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF070A0E),
    surfaceVariant = Color(0xFF141A22),
)

private val FTTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun FTCloudTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
    }

    val context = LocalContext.current
    val baseScheme = when {
        themeMode == AppThemeMode.AMOLED -> FTAmoledColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> FTDarkColorScheme
        else -> FTLightColorScheme
    }

    val finalScheme = if (themeMode == AppThemeMode.AMOLED) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color(0xFF06080B),
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = finalScheme,
        typography = FTTypography,
        content = content,
    )
}
