package com.jpcottin.composebreakout.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BreakoutColorScheme = darkColorScheme(
    primary = PaddleCyan,
    onPrimary = SpaceBlack,
    primaryContainer = SpaceSurfaceVariant,
    onPrimaryContainer = PaddleCyanLight,
    secondary = TitleGold,
    onSecondary = SpaceBlack,
    tertiary = AutoGreen,
    onTertiary = SpaceBlack,
    background = SpaceBlack,
    onBackground = OnSpaceWhite,
    surface = SpaceSurface,
    onSurface = OnSpaceWhite,
    surfaceVariant = SpaceSurfaceVariant,
    onSurfaceVariant = OnSpaceMuted,
    outline = LaneLine,
    error = BrickRowColors[0],
)

/** The game is dark on every device, whatever the system setting. */
@Composable
fun BreakoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BreakoutColorScheme,
        typography = BreakoutTypography,
        content = content,
    )
}
