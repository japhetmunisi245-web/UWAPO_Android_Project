package com.uwapo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF17365D)
val Blue = Color(0xFF2563EB)
val LightBackground = Color(0xFFF8FAFC)
val DarkBackground = Color(0xFF0F172A)
val DarkSurface = Color(0xFF1E293B)

private val LightScheme = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E8F0),
    onPrimaryContainer = Navy,
    secondary = Blue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E40AF),
    background = LightBackground,
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFB42318)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Navy,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF3B82F6),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF1E3A8A),
    onSecondaryContainer = Color(0xFFBFDBFE),
    background = DarkBackground,
    onBackground = Color(0xFFF8FAFC),
    surface = DarkSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF64748B),
    error = Color(0xFFF87171)
)

@Composable
fun UWAPOTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}
