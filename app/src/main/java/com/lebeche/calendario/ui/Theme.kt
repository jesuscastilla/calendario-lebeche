package com.lebeche.calendario.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lebeche.calendario.R

// Paleta basada en la PWA Barrioteca Acalencá
val PwaCream = Color(0xFFF5F5F0)
val PwaInk = Color(0xFF141414)
val PwaPrimary = Color(0xFF8A5A00)
val PwaAccent = Color(0xFFE8A33D)

// Tipografías Lebeche: Courgette (Madre), Garet (Títulos) y Open Sans (Cuerpo de texto)
private val CourgetteFamily = FontFamily(Font(R.font.courgette, FontWeight.Normal, FontStyle.Normal))

private val GaretFamily = FontFamily(
    Font(R.font.garet_book, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.garet_heavy, FontWeight.Bold, FontStyle.Normal)
)

private val OpenSansFamily = FontFamily(
    Font(R.font.opensans_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.opensans_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.opensans_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.opensans_bolditalic, FontWeight.Bold, FontStyle.Italic)
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

// Tipografía de Lebeche. Los títulos van en Garet (o Open Sans Bold), el texto en Open Sans, y el display principal en Courgette.
private val AppTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = CourgetteFamily, fontWeight = FontWeight.Normal),
        displayMedium = displayMedium.copy(fontFamily = CourgetteFamily, fontWeight = FontWeight.Normal),
        displaySmall = displaySmall.copy(fontFamily = CourgetteFamily, fontWeight = FontWeight.Normal),
        headlineLarge = headlineLarge.copy(fontFamily = CourgetteFamily, fontWeight = FontWeight.Normal),
        headlineMedium = headlineMedium.copy(fontFamily = CourgetteFamily, fontWeight = FontWeight.Normal),
        headlineSmall = headlineSmall.copy(fontFamily = GaretFamily, fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontFamily = GaretFamily, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = GaretFamily, fontWeight = FontWeight.Bold),
        titleSmall = titleSmall.copy(fontFamily = GaretFamily, fontWeight = FontWeight.Bold),
        bodyLarge = bodyLarge.copy(fontFamily = OpenSansFamily),
        bodyMedium = bodyMedium.copy(fontFamily = OpenSansFamily),
        bodySmall = bodySmall.copy(fontFamily = OpenSansFamily),
        labelLarge = labelLarge.copy(fontFamily = OpenSansFamily, fontWeight = FontWeight.Bold),
        labelMedium = labelMedium.copy(fontFamily = OpenSansFamily, fontWeight = FontWeight.Bold),
        labelSmall = labelSmall.copy(fontFamily = OpenSansFamily, fontWeight = FontWeight.Bold),
    )
}

@Composable
fun CalendarioLebecheTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, shapes = AppShapes, typography = AppTypography, content = content)
}