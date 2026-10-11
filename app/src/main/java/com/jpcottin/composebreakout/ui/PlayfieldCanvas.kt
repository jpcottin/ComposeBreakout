package com.jpcottin.composebreakout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.jpcottin.composebreakout.engine.Game
import com.jpcottin.composebreakout.engine.World
import com.jpcottin.composebreakout.theme.BallCream
import com.jpcottin.composebreakout.theme.BallGlow
import com.jpcottin.composebreakout.theme.BrickRowColors
import com.jpcottin.composebreakout.theme.LaneLine
import com.jpcottin.composebreakout.theme.PaddleCyan
import com.jpcottin.composebreakout.theme.PaddleCyanLight
import com.jpcottin.composebreakout.theme.PowerUpColors
import com.jpcottin.composebreakout.theme.SpaceBlack
import com.jpcottin.composebreakout.theme.SpaceSurface
import com.jpcottin.composebreakout.theme.SteelGrey
import kotlin.math.sin

/**
 * Draws the playfield from the engine's state: the lane, the wall, the
 * balls, the capsules, the debris and the paddle, plus the control strip and
 * the bonus bars. The world is mapped to pixels with one scale (half the
 * view height) around the view centre, so a world unit is the same size on
 * both axes and the engine never sees pixels.
 *
 * [frame] is read inside the draw lambda, so the canvas redraws (without
 * recomposing) once per engine step; the engine itself is a plain mutable
 * object, not Compose state.
 */
@Composable
fun PlayfieldCanvas(game: Game, frame: LongState, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION") frame.longValue
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
    val inRound = game.state != Game.State.TITLE

    // Side margins on a wide screen: the lane stops short of the edges. On
    // the glasses (touchbar mode) they stay black: black is transparent on
    // additive AR lenses, a tint would be a grey slab.
    if (fieldHW < game.aspect - 0.001f) {
        val margin = m.len(game.aspect - fieldHW)
        val marginColor = if (game.controlMode == Game.ControlMode.TOUCHBAR) Color.Black else SpaceSurface
        drawRect(marginColor, topLeft = Offset.Zero, size = Size(margin, size.height))
        drawRect(marginColor, topLeft = Offset(size.width - margin, 0f), size = Size(margin, size.height))
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

    // Everything in play shakes together when a ball is lost.
    translate(m.len(game.shakeX), m.len(game.shakeY)) {
        val wallAlpha = if (inRound) 1f else 0.85f
        for (b in game.bricks) if (b.alive) drawBrick(m, game.brickX(b), game.brickY(b), b, wallAlpha)

        if (!inRound) {
            drawBall(m, game.demoBallX, game.demoBallY, 1f)
            val pulse = 0.5f + 0.5f * sin(game.animTime * 4f)
            drawPaddle(m, game.demoPaddleX, World.PADDLE_Y, World.PADDLE_HW, 0.5f + 0.5f * pulse)
        } else {
            for (pu in game.powerUps) drawPowerUp(m, pu, game.animTime)
            for (b in game.balls) drawBall(m, b.x, b.y, 1f)
            val paddleAlpha = if (game.state == Game.State.GAME_OVER) 0.35f else 1f
            drawPaddle(m, game.paddleX, World.PADDLE_Y, game.paddleHalfWidth, paddleAlpha)
        }

        // Flash rings: expanding, fading.
        for (e in game.explosions) {
            val t = e.progress
            val r = m.len(e.radius * (1f + t * 3f))
            val c = Color(e.r.coerceIn(0f, 1f), e.g.coerceIn(0f, 1f), e.b.coerceIn(0f, 1f))
            val center = Offset(m.x(e.x), m.y(e.y))
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = (1f - t) * 0.85f), c.copy(alpha = 0f)), center, r), r, center)
        }
        // Debris fragments: spinning squares that shrink and fade.
        for (p in game.particles) {
            val life = p.life.coerceIn(0f, 1f)
            val s = m.len(p.size * life)
            val c = Color(p.r.coerceIn(0f, 1f), p.g.coerceIn(0f, 1f), p.b.coerceIn(0f, 1f), life)
            rotate(p.rot * 57.2958f, Offset(m.x(p.x), m.y(p.y))) {
                drawRect(c, Offset(m.x(p.x) - s, m.y(p.y) - s), Size(2f * s, 2f * s))
            }
        }
    }

    // The control strip and the bonus bars: shake-free, phone mode, in play.
    if (game.state == Game.State.PLAYING && game.controlMode == Game.ControlMode.STRIP) drawControlStrip(m, game)
    if (game.state == Game.State.PLAYING || game.state == Game.State.LEVEL_CLEAR) drawBonusBars(m, game)
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

/** A bonus capsule: a pulsing glow, a coloured disc and a glyph that says
 *  what it does (a bar for wide, a clock for slow, three dots for multi, a
 *  plus for a life). */
fun DrawScope.drawPowerUp(m: WorldToPixels, pu: Game.PowerUp, animTime: Float) {
    val c = PowerUpColors[pu.type.ordinal]
    val center = Offset(m.x(pu.x), m.y(pu.y))
    val glow = m.len(Game.POWER_UP_R * (1.6f + 0.25f * sin(animTime * 5f)))
    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.35f), c.copy(alpha = 0f)), center, glow), glow, center)
    drawCircle(c, m.len(Game.POWER_UP_R), center)
    val ink = SpaceBlack
    when (pu.type) {
        Game.PowerUpType.WIDE ->
            drawRect(ink, Offset(center.x - m.len(0.022f), center.y - m.len(0.005f)), Size(m.len(0.044f), m.len(0.010f)))
        Game.PowerUpType.SLOW -> {
            drawCircle(ink, m.len(0.016f), center)
            drawCircle(c, m.len(0.011f), center)
            drawRect(ink, Offset(center.x - m.len(0.0025f), center.y - m.len(0.011f)), Size(m.len(0.005f), m.len(0.012f)))
        }
        Game.PowerUpType.MULTI -> {
            drawCircle(ink, m.len(0.006f), Offset(center.x, center.y - m.len(0.010f)))
            drawCircle(ink, m.len(0.006f), Offset(center.x - m.len(0.011f), center.y + m.len(0.007f)))
            drawCircle(ink, m.len(0.006f), Offset(center.x + m.len(0.011f), center.y + m.len(0.007f)))
        }
        Game.PowerUpType.LIFE -> {
            drawRect(ink, Offset(center.x - m.len(0.018f), center.y - m.len(0.005f)), Size(m.len(0.036f), m.len(0.010f)))
            drawRect(ink, Offset(center.x - m.len(0.005f), center.y - m.len(0.018f)), Size(m.len(0.010f), m.len(0.036f)))
        }
    }
}

/** The touch strip below the paddle: a faint backdrop, a divider line, and a
 *  steering marker under the paddle so the player knows where their finger acts. */
private fun DrawScope.drawControlStrip(m: WorldToPixels, game: Game) {
    val held = game.moveHeld
    val top = m.y(World.STRIP_TOP_Y)
    drawRect(Color(0xFF4D99FF).copy(alpha = if (held) 0.10f else 0.05f), Offset(0f, top), Size(size.width, size.height - top))
    drawLine(LaneLine.copy(alpha = 0.30f), Offset(0f, top), Offset(size.width, top), strokeWidth = m.len(0.005f))
    val cy = m.y((World.STRIP_TOP_Y + 1f) * 0.5f)
    drawRoundRect(
        Color(0xFF66BFFF).copy(alpha = if (held) 0.90f else 0.40f),
        Offset(m.x(game.paddleX) - m.len(0.030f), cy - m.len(0.004f)), Size(m.len(0.060f), m.len(0.008f)),
        CornerRadius(m.len(0.004f)),
    )
}

/** The timed bonuses with their remaining time as a bar, at the left of the
 *  control strip: the only part of the screen the wall, the balls and the
 *  capsules never reach. */
private fun DrawScope.drawBonusBars(m: WorldToPixels, game: Game) {
    val iconX = -game.aspect + 0.055f
    val barX0 = -game.aspect + 0.095f
    val barMaxW = 0.16f
    var row = 0
    fun timedRow(timer: Float, duration: Float, c: Color) {
        val y = 0.76f + row * 0.06f
        val progress = timer / duration
        val pulse = if (timer < 2f) 0.5f + 0.5f * sin(game.animTime * 18f) else 1f
        drawCircle(c.copy(alpha = pulse), m.len(0.020f), Offset(m.x(iconX), m.y(y)))
        drawRect(c.copy(alpha = 0.25f), Offset(m.x(barX0), m.y(y) - m.len(0.006f)), Size(m.len(barMaxW), m.len(0.012f)))
        if (progress > 0.01f)
            drawRect(c.copy(alpha = 0.85f), Offset(m.x(barX0), m.y(y) - m.len(0.006f)), Size(m.len(barMaxW * progress), m.len(0.012f)))
        row++
    }
    if (game.wideTimer > 0f) timedRow(game.wideTimer, Game.WIDE_DURATION, PowerUpColors[Game.PowerUpType.WIDE.ordinal])
    if (game.slowTimer > 0f) timedRow(game.slowTimer, Game.SLOW_DURATION, PowerUpColors[Game.PowerUpType.SLOW.ordinal])
}
