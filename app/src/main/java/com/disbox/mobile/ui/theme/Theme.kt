package com.disbox.mobile.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF5865F2),
    onPrimary = Color.White,
    secondary = Color(0xFF00D4AA),
    onSecondary = Color.White,
    tertiary = Color(0xFFF0A500),
    onTertiary = Color.White,
    background = Color(0xFF05050A),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0A0A15),
    onSurface = Color(0xFFC0C0E0),
    surfaceVariant = Color(0xFF121222),
    onSurfaceVariant = Color(0xFF8A8AAA),
    outline = Color(0xFF1C1C32),
    error = Color(0xFFED4245),
    outlineVariant = Color(0xFF242440)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF5865F2),
    onPrimary = Color.White,
    secondary = Color(0xFF00D4AA),
    onSecondary = Color.White,
    tertiary = Color(0xFFF0A500),
    onTertiary = Color.White,
    background = Color(0xFFE0E0EB),
    onBackground = Color(0xFF0A0A1A),
    surface = Color(0xFFF0F2F5),
    onSurface = Color(0xFF353550),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF5A5A7A),
    outline = Color(0xFFE8EBF0),
    error = Color(0xFFED4245)
)

private val AppTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

@Composable
fun DisboxMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accentColor: String = "#5865F2",
    content: @Composable () -> Unit
) {
    val primaryColor = try { Color(android.graphics.Color.parseColor(accentColor)) } catch (e: Exception) { Color(0xFF5865F2) }
    
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme.copy(primary = primaryColor)
        else -> LightColorScheme.copy(primary = primaryColor)
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    androidx.compose.material3.MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
