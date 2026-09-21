package com.lebeche.calendario.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lebeche.calendario.R

// Paleta basada en la PWA Barrioteca Acalencá
val PwaCream = Color(0xFFF5F5F0)
val PwaInk = Color(0xFF141414)
val PwaPrimary = Color(0xFF8A5A00)
val PwaAccent = Color(0xFFE8A33D)

// Tipografía Inter (la misma familia que usa la PWA). Se usan fuentes variables
// para cubrir todos los pesos (400..700) y la cursiva.
@OptIn(ExperimentalTextApi::class)
private val InterFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.inter, FontWeight.Medium, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_italic, FontWeight.Medium, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter, FontWeight.SemiBold, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter_italic, FontWeight.SemiBold, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter, FontWeight.Bold, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.inter_italic, FontWeight.Bold, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val LightColors = lightColorScheme(
    primary = PwaPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDEA8),
    onPrimaryContainer = Color(0xFF2A1700),
    secondary = PwaAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFFFDEA8),
    onSecondaryContainer = Color(0xFF2A1700),
    tertiary = PwaAccent,
    background = PwaCream,
    onBackground = PwaInk,
    surface = Color.White,
    onSurface = PwaInk,
    surfaceVariant = Color(0xFFE7E7E2),
    onSurfaceVariant = Color(0xFF47443F),
    outline = Color(0xFF77736C),
    error = Color(0xFFBA1A1A)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

// Tipografía con Inter. Los títulos van en cursiva + negrita, como los encabezados de la PWA.
private val AppTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = InterFamily, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold),
        displayMedium = displayMedium.copy(fontFamily = InterFamily, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold),
        displaySmall = displaySmall.copy(fontFamily = InterFamily, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold),
        headlineLarge = headlineLarge.copy(fontFamily = InterFamily, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontFamily = InterFamily, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontFamily = InterFamily, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontFamily = InterFamily, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = InterFamily),
        titleSmall = titleSmall.copy(fontFamily = InterFamily),
        bodyLarge = bodyLarge.copy(fontFamily = InterFamily),
        bodyMedium = bodyMedium.copy(fontFamily = InterFamily),
        bodySmall = bodySmall.copy(fontFamily = InterFamily),
        labelLarge = labelLarge.copy(fontFamily = InterFamily),
        labelMedium = labelMedium.copy(fontFamily = InterFamily),
        labelSmall = labelSmall.copy(fontFamily = InterFamily),
    )
}

@Composable
fun CalendarioLebecheTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, shapes = AppShapes, typography = AppTypography, content = content)
}