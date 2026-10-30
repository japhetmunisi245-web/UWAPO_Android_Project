package com.uwapo.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.uwapo.desktop.ui.DesktopUWAPORoot

val Navy = Color(0xFF17365D)
val Blue = Color(0xFF2563EB)
val LightBackground = Color(0xFFF8FAFC)
val DarkBackground = Color(0xFF0F172A)
val DarkSurface = Color(0xFF1E293B)

val DesktopLightScheme = lightColorScheme(
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

val DesktopDarkScheme = darkColorScheme(
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

fun main() = application {
    var isDarkTheme by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "UWAPO - Membership & Financial Management",
        state = WindowState(size = DpSize(1280.dp, 850.dp))
    ) {
        UWAPODesktopTheme(darkTheme = isDarkTheme) {
            DesktopUWAPORoot(
                isDarkTheme = isDarkTheme,
                onToggleTheme = { isDarkTheme = !isDarkTheme }
            )
        }
    }
}

@Composable
fun UWAPODesktopTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DesktopDarkScheme else DesktopLightScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}
