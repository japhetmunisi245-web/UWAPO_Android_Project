package com.uwapo.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.uwapo.desktop.ui.DesktopUWAPORoot

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "UWAPO - Membership & Financial Management",
        state = WindowState(size = DpSize(1280.dp, 850.dp))
    ) {
        UWAPODesktopTheme {
            DesktopUWAPORoot()
        }
    }
}

@Composable
fun UWAPODesktopTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF006C4C),
        onPrimary = androidx.compose.ui.graphics.Color.White,
        primaryContainer = androidx.compose.ui.graphics.Color(0xFF89F8C7),
        onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF002114),
        secondary = androidx.compose.ui.graphics.Color(0xFF4D6356),
        background = androidx.compose.ui.graphics.Color(0xFF191C1A),
        surface = androidx.compose.ui.graphics.Color(0xFF202421),
        onSurface = androidx.compose.ui.graphics.Color(0xFFE1E3DF)
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}
