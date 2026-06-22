package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PremiumDarkColorScheme = darkColorScheme(
    primary = GoldPremium,
    onPrimary = DarkBackground,
    secondary = GoldAlt,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = WhiteText,
    surface = DarkSurface,
    onSurface = WhiteText,
    surfaceVariant = BubbleAI,
    onSurfaceVariant = WhiteText
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    // We enforce the custom Premium Dark look precisely
    MaterialTheme(
        colorScheme = PremiumDarkColorScheme,
        typography = Typography,
        content = content
    )
}
