package com.jpcottin.composebreakout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jpcottin.composebreakout.engine.Game
import com.jpcottin.composebreakout.engine.World
import com.jpcottin.composebreakout.theme.BallCream
import com.jpcottin.composebreakout.theme.BallGlow
import com.jpcottin.composebreakout.theme.BrickRowColors
import com.jpcottin.composebreakout.theme.LaneLine
import com.jpcottin.composebreakout.theme.PaddleCyan
import com.jpcottin.composebreakout.theme.PaddleCyanLight
import com.jpcottin.composebreakout.theme.SpaceSurface
import com.jpcottin.composebreakout.theme.SteelGrey
import kotlin.math.sin

/**
 * Draws the playfield from the engine's state: the lane, the wall, the ball
 * and the paddle. The world is mapped to pixels with one scale (half the
 * view height) around the view centre, so a world unit is the same size on
 * both axes and the engine never sees pixels.
 *
 * [frame] is read so the canvas redraws once per engine step; the engine
 * itself is a plain mutable object, not Compose state.
 */
@Composable
fun PlayfieldCanvas(game: Game, frame: Long, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_VARIABLE") val tick = frame
        drawPlayfield(game)
    }
}

/** World -> pixel mapping for a [DrawScope]. */
class WorldToPixels(size: Size) {
    val scale = size.height / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    fun x(wx: Float) = cx + wx * scale
    fun y(wy: Float) = cy + wy * scale
    fun len(w: Float) = w * scale
}

fun DrawScope.drawPlayfield(game: Game) {
    val m = WorldToPixels(size)
    val fieldHW = game.fieldHalfWidth

    // Side margins on a wide screen: the lane stops short of the edges.
    if (fieldHW < game.aspect - 0.001f) {
        val margin = m.len(game.aspect - fieldHW)
        drawRect(SpaceSurface, topLeft = Offset.Zero, size = Size(margin, size.height))
        drawRect(SpaceSurface, topLeft = Offset(size.width - margin, 0f), size = Size(margin, size.height))
        for (sgn in listOf(-1f, 1f)) {
            val x = m.x(sgn * fieldHW)
            drawLine(LaneLine.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), strokeWidth = m.len(0.006f))
        }
    }
    // The ceiling, under the HUD.
    drawLine(
        LaneLine.copy(alpha = 0.35f),
        Offset(m.x(-fieldHW), m.y(World.TOP_WALL_Y)),
        Offset(m.x(fieldHW), m.y(World.TOP_WALL_Y)),
        strokeWidth = m.len(0.006f),
    )

    // The wall.
    val wallAlpha = if (game.state == Game.State.TITLE) 0.85f else 1f
    for (b in game.bricks) {
        if (!b.alive) continue
        drawBrick(m, game.brickX(b), game.brickY(b), b, wallAlpha)
    }

    if (game.state == Game.State.TITLE) {
        drawBall(m, game.demoBallX, game.demoBallY, 1f)
        val pulse = 0.5f + 0.5f * sin(game.animTime * 4f)
        drawPaddle(m, game.demoPaddleX, World.PADDLE_Y, World.PADDLE_HW, 0.5f + 0.5f * pulse)
    }
}

/** A brick: a rounded body in its row colour, a lighter top bevel and a darker
 *  bottom edge. Steel is grey with two rivets; tougher bricks carry a lighter
 *  inner core per hit left. */
fun DrawScope.drawBrick(m: WorldToPixels, wx: Float, wy: Float, b: Game.Brick, alpha: Float) {
    val color = if (b.type == Game.BrickType.STEEL) SteelGrey else BrickRowColors[b.row % BrickRowColors.size]
    val hw = m.len(World.BRICK_HW)
    val hh = m.len(World.BRICK_HH)
    val left = m.x(wx) - hw
    val top = m.y(wy) - hh
    val radius = CornerRadius(hh * 0.35f)
    drawRoundRect(color.copy(alpha = alpha), Offset(left, top), Size(2f * hw, 2f * hh), radius)
    // top bevel (lighter) and bottom edge (darker)
    drawRoundRect(Color.White.copy(alpha = 0.35f * alpha), Offset(left, top), Size(2f * hw, hh * 0.5f), radius)
    drawRect(Color.Black.copy(alpha = 0.35f * alpha), Offset(left, top + 2f * hh * 0.82f), Size(2f * hw, 2f * hh * 0.18f))
    if (b.type == Game.BrickType.STEEL) {
        for (sgn in listOf(-1f, 1f))
            drawCircle(Color(0xFF3A3F4D).copy(alpha = alpha), m.len(0.006f), Offset(m.x(wx) + sgn * hw * 0.6f, m.y(wy)))
    } else if (b.hp > 1) {
        for (i in 1 until b.hp) {
            val k = 1f - 0.30f * i
            drawRoundRect(
                Color.White.copy(alpha = 0.30f * alpha),
                Offset(m.x(wx) - hw * k, m.y(wy) - hh * k), Size(2f * hw * k, 2f * hh * k), radius,
            )
        }
    }
}

/** The paddle: a rounded cyan bar with a bright top line. */
fun DrawScope.drawPaddle(m: WorldToPixels, wx: Float, wy: Float, halfW: Float, alpha: Float) {
    val hw = m.len(halfW)
    val hh = m.len(World.PADDLE_HH)
    val left = m.x(wx) - hw
    val top = m.y(wy) - hh
    drawRoundRect(PaddleCyan.copy(alpha = alpha), Offset(left, top), Size(2f * hw, 2f * hh), CornerRadius(hh))
    drawRoundRect(
        PaddleCyanLight.copy(alpha = 0.9f * alpha),
        Offset(left + hh, top + hh * 0.3f), Size(2f * hw - 2f * hh, hh * 0.5f), CornerRadius(hh * 0.25f),
    )
}

/** The ball: a soft halo under a bright disc. */
fun DrawScope.drawBall(m: WorldToPixels, wx: Float, wy: Float, alpha: Float) {
    val r = m.len(World.BALL_R)
    val center = Offset(m.x(wx), m.y(wy))
    drawCircle(
        Brush.radialGradient(listOf(BallGlow.copy(alpha = 0.45f * alpha), BallGlow.copy(alpha = 0f)), center, r * 2.6f),
        r * 2.6f, center,
    )
    drawCircle(BallCream.copy(alpha = alpha), r, center)
}
