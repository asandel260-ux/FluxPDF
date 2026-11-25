package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FluxLightColorScheme = lightColorScheme(
    primary = VermilionLight,
    onPrimary = Color.White,
    primaryContainer = VermilionContainerLight,
    onPrimaryContainer = OnVermilionContainerLight,
    secondary = SlateAccentLight,
    onSecondary = Color.White,
    secondaryContainer = SlateContainerLight,
    onSecondaryContainer = OnSlateContainerLight,
    tertiary = OchreTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = OchreContainerLight,
    onTertiaryContainer = InkPrimary,
    background = PaperBackground,
    onBackground = InkPrimary,
    surface = PaperSurface,
    onSurface = InkPrimary,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = PaperSurface,
    surfaceContainer = PaperSurfaceContainer,
    surfaceContainerHigh = PaperSurfaceContainerHigh,
    surfaceContainerHighest = PaperSurfaceVariant,
    outline = InkOutline,
    outlineVariant = InkOutlineSubtle,
)

private val FluxDarkColorScheme = darkColorScheme(
    primary = VermilionDark,
    onPrimary = Color(0xFF280803),
    primaryContainer = VermilionContainerDark,
    onPrimaryContainer = OnVermilionContainerDark,
    secondary = SlateAccentDark,
    onSecondary = Color(0xFF0A1D29),
    secondaryContainer = SlateContainerDark,
    onSecondaryContainer = OnSlateContainerDark,
    tertiary = OchreTertiaryDark,
    onTertiary = Color(0xFF2B1E05),
    tertiaryContainer = OchreContainerDark,
    onTertiaryContainer = IvoryPrimary,
    background = ObsidianBackground,
    onBackground = IvoryPrimary,
    surface = ObsidianSurface,
    onSurface = IvoryPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = IvorySecondary,
    surfaceContainerLowest = Color(0xFF090A0D),
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceContainerHigh,
    surfaceContainerHighest = ObsidianSurfaceVariant,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineSubtle,
)

val FluxShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun FluxPdfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) FluxDarkColorScheme else FluxLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FluxTypography,
        shapes = FluxShapes,
        content = content,
    )
}
