package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PanelDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF042029),
    primaryContainer = CyanContainer,
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = EmeraldOnline,
    onSecondary = Color(0xFF022C22),
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = IndigoAccent,
    onTertiary = Color(0xFF11153A),
    tertiaryContainer = IndigoContainer,
    onTertiaryContainer = Color(0xFFC7D2FE),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateContainer,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = MidnightSurface,
    surfaceContainer = SlateContainer,
    surfaceContainerHigh = SlateContainerHigh,
    surfaceContainerHighest = Color(0xFF223354),
    outline = SlateBorder,
    outlineVariant = Color(0xFF1A2640),
    error = RoseCritical,
    onError = Color.White,
    errorContainer = RoseContainer,
    onErrorContainer = Color(0xFFFECDD3)
)

val PanelShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PanelVpnTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PanelDarkColorScheme,
        typography = PanelTypography,
        shapes = PanelShapes,
        content = content
    )
}
