package com.lebeche.calendario.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Paleta de la identidad Lebeche
val LebecheBlue = Color(0xFF8FD6EF)
val LebecheAmber = Color(0xFFE8A33D)
val LebecheDark = Color(0xFF141414)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00747B),
    onPrimary = Color.White,
    primaryContainer = LebecheBlue,
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDEA8),
    onSecondaryContainer = Color(0xFF2A1700),
    tertiary = Color(0xFF6E5E00),
    background = Color(0xFFFAF8F2),
    onBackground = LebecheDark,
    surface = Color.White,
    onSurface = LebecheDark,
    surfaceVariant = Color(0xFFE4F0F2),
    onSurfaceVariant = Color(0xFF3E494A),
    outline = Color(0xFF73898B),
    error = Color(0xFFBA1A1A)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun CalendarioLebecheTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, shapes = AppShapes, content = content)
}