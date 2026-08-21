package com.lebeche.calendario.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta de la identidad Lebeche
val LebecheBlue = Color(0xFF8FD6EF)
val LebecheAmber = Color(0xFFE8A33D)
val LebecheDark = Color(0xFF141414)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00696E),
    onPrimary = Color.White,
    primaryContainer = LebecheBlue,
    onPrimaryContainer = Color(0xFF002022),
    secondary = LebecheAmber,
    onSecondary = Color(0xFF2A1700),
    background = Color(0xFFFCFCF7),
    surface = Color.White,
    onSurface = LebecheDark
)

@Composable
fun CalendarioLebecheTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
