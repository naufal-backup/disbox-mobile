package com.disbox.mobile.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.disbox.mobile.ui.theme.AppTypography

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF5865F2), // accent
    onPrimary = Color.White,
    secondary = Color(0xFF00D4AA), // teal
    onSecondary = Color.White,
    tertiary = Color(0xFFF0A500), // amber
    onTertiary = Color.White,
    background = Color(0xFF05050A), // bg-deep
    onBackground = Color(0xFFFFFFFF), // text-primary
    surface = Color(0xFF0A0A15), // bg-base
    onSurface = Color(0xFFC0C0E0), // text-secondary
    surfaceVariant = Color(0xFF121222), // bg-surface (cards)
    onSurfaceVariant = Color(0xFF8A8AAA), // text-muted
    outline = Color(0xFF1C1C32), // bg-elevated
    error = Color(0xFFED4245), // red
    outlineVariant = Color(0xFF242440) // bg-hover
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF5865F2),
    onPrimary = Color.White,
    secondary = Color(0xFF00D4AA),
    onSecondary = Color.White,
    tertiary = Color(0xFFF0A500),
    onTertiary = Color.White,
    background = Color(0xFFE0E0EB), // bg-deep
    onBackground = Color(0xFF0A0A1A), // text-primary
    surface = Color(0xFFF0F2F5), // bg-base
    onSurface = Color(0xFF353550), // text-secondary
    surfaceVariant = Color(0xFFFFFFFF), // bg-surface (cards)
    onSurfaceVariant = Color(0xFF5A5A7A), // text-muted
    outline = Color(0xFFE8EBF0), // bg-hover
    error = Color(0xFFED4245)
)

@Composable
fun DisboxMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled to match Linux aesthetics
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
