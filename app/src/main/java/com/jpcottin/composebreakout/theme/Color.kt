package com.jpcottin.composebreakout.theme

import androidx.compose.ui.graphics.Color

// The game's palette: deep space, a cyan paddle, warm ball, rainbow bricks.
val SpaceBlack = Color(0xFF080A17)
val SpaceSurface = Color(0xFF12162B)
val SpaceSurfaceVariant = Color(0xFF1F2544)
val PaddleCyan = Color(0xFF4DB8F2)
val PaddleCyanLight = Color(0xFFCCF2FF)
val BallCream = Color(0xFFFFF7D9)
val BallGlow = Color(0xFFFFE68C)
val TitleGold = Color(0xFFFFCC4D)
val AutoGreen = Color(0xFF40FF61)
val OnSpaceWhite = Color(0xFFE8ECF8)
val OnSpaceMuted = Color(0xFF8A93B5)
val LaneLine = Color(0xFF66A6F2)
val SteelGrey = Color(0xFF8C94A8)

/** Brick colours by row: a bright rainbow so every row reads at a glance. */
val BrickRowColors = listOf(
    Color(0xFFFF524D),   // red
    Color(0xFFFF9933),   // orange
    Color(0xFFFFE640),   // yellow
    Color(0xFF66F266),   // green
    Color(0xFF4DD9FF),   // cyan
    Color(0xFF6685FF),   // blue
    Color(0xFFCC73FF),   // purple
    Color(0xFFFF73CC),   // pink
    Color(0xFFEBEBF2),   // white
)

/** Bonus capsule colours: wide, slow, multi, life. */
val PowerUpColors = listOf(
    Color(0xFF4DD9FF),
    Color(0xFFCC73FF),
    Color(0xFFFFA633),
    Color(0xFF66F273),
)

/** Podium colours: gold, silver, bronze. */
val PodiumColors = listOf(Color(0xFFFFD933), Color(0xFFC7D1EB), Color(0xFFB87838))
