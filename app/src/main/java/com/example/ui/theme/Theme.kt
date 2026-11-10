package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = BioIndigoLight,
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = SerumAmberLight,
    onSecondary = Color(0xFF3E2400),
    secondaryContainer = Color(0xFF5A3600),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = MediaCoralLight,
    onTertiary = Color(0xFF4C0519),
    tertiaryContainer = Color(0xFF881337),
    onTertiaryContainer = Color(0xFFFFE4E6),
    background = LabDeepIndigo,
    onBackground = Color(0xFFF1F5F9),
    surface = LabDarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = LabDarkCard,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF475569)
)

private val LightColorScheme = lightColorScheme(
    primary = BioIndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = BioIndigoContainer,
    onPrimaryContainer = OnBioIndigoContainer,
    secondary = SerumAmber,
    onSecondary = Color.White,
    secondaryContainer = SerumAmberSoft,
    onSecondaryContainer = Color(0xFF451A03),
    tertiary = MediaCoral,
    onTertiary = Color.White,
    tertiaryContainer = MediaCoralSoft,
    onTertiaryContainer = Color(0xFF4C0519),
    background = LightLabBackground,
    onBackground = LightLabOnSurface,
    surface = LightLabSurface,
    onSurface = LightLabOnSurface,
    surfaceVariant = LightLabSurfaceVariant,
    onSurfaceVariant = LightLabOnSurfaceVariant,
    outline = Color(0xFF94A3B8)
)

val LabShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = LabShapes,
            content = content
        )
    }
}
