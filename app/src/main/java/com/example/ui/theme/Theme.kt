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
    primary = BioTealLight,
    onPrimary = LabDeepTeal,
    primaryContainer = Color(0xFF005254),
    onPrimaryContainer = Color(0xFFB3F3EE),
    secondary = SerumAmberLight,
    onSecondary = Color(0xFF3E2400),
    secondaryContainer = Color(0xFF5A3600),
    onSecondaryContainer = Color(0xFFFFDEA8),
    tertiary = MediaCoralLight,
    onTertiary = Color(0xFF4C0014),
    tertiaryContainer = Color(0xFF72142B),
    onTertiaryContainer = Color(0xFFFFD9DF),
    background = LabDeepTeal,
    onBackground = Color(0xFFE4F2F2),
    surface = LabDarkSurface,
    onSurface = Color(0xFFE4F2F2),
    surfaceVariant = LabDarkCard,
    onSurfaceVariant = Color(0xFFB4D2D4),
    outline = Color(0xFF477276)
)

private val LightColorScheme = lightColorScheme(
    primary = BioTealPrimary,
    onPrimary = Color.White,
    primaryContainer = BioTealContainer,
    onPrimaryContainer = OnBioTealContainer,
    secondary = SerumAmber,
    onSecondary = Color.White,
    secondaryContainer = SerumAmberSoft,
    onSecondaryContainer = Color(0xFF452200),
    tertiary = MediaCoral,
    onTertiary = Color.White,
    tertiaryContainer = MediaCoralSoft,
    onTertiaryContainer = Color(0xFF420010),
    background = LightLabBackground,
    onBackground = LightLabOnSurface,
    surface = LightLabSurface,
    onSurface = LightLabOnSurface,
    surfaceVariant = LightLabSurfaceVariant,
    onSurfaceVariant = LightLabOnSurfaceVariant,
    outline = Color(0xFF8AA8AC)
)

val LabShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
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
