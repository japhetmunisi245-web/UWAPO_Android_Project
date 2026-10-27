package com.uwapo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF17365D)
private val Blue = Color(0xFF2563EB)
private val LightBackground = Color(0xFFF5F7FB)
private val Scheme = lightColorScheme(
    primary = Navy,
    secondary = Blue,
    background = LightBackground,
    surface = Color.White,
    error = Color(0xFFB42318)
)

@Composable
fun UWAPOTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
