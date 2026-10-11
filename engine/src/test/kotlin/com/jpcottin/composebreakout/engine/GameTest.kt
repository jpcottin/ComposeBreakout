package com.jpcottin.composebreakout.engine

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The engine on a portrait phone (aspect 0.45) unless a test says otherwise. */
private fun newGame(aspect: Float = 1080f / 2400f): Game = Game().apply { setAspect(aspect) }

private fun step(g: Game, seconds: Float, dt: Float = 1f / 60f) {
    var t = 0f
    while (t < seconds) { g.update(dt); t += dt }
}

class InitialStateTest {
    @Test fun startsOnTitle() {
        val g = newGame()
        assertEquals(Game.State.TITLE, g.state)
        assertEquals(5, g.lives)
        assertEquals(0L, g.score)
        assertEquals(1, g.level)
    }
}

class WallTest {
    // The wall is laid out for the lane: fixed-size bricks, as many columns
    // as fit, capped at 10 on wide screens.
    @Test fun columnsFollowTheLaneWidth() {
        assertEquals(7, World.wallColumns(1080f / 2400f))     // portrait phone
        assertEquals(10, World.wallColumns(2152f / 2076f))    // unfolded foldable
        assertEquals(10, World.wallColumns(2400f / 1080f))    // landscape / TV
        val g = newGame()
        assertEquals(5 * 7, g.bricks.size)                    // the title demo wall: five rows
        g.setAspect(2400f / 1080f)
        assertEquals(5 * 10, g.bricks.size)
    }

    @Test fun everyDemoBrickSitsInsideTheLane() {
        for (aspect in listOf(1080f / 2400f, 2152f / 2076f, 2400f / 1080f)) {
            val g = newGame(aspect)
            for (b in g.bricks) {
                assertTrue(abs(g.brickX(b)) + World.BRICK_HW < g.fieldHalfWidth)
                assertTrue(g.brickY(b) - World.BRICK_HH > World.TOP_WALL_Y)
            }
        }
    }
}

class TitleDemoTest {
    @Test fun demoBallStaysInsideTheLane() {
        val g = newGame()
        repeat(60 * 20) {
            g.update(1f / 60f)
            assertTrue(abs(g.demoBallX) + World.BALL_R <= g.fieldHalfWidth + 1e-4f)
            assertTrue(g.demoBallY < World.PADDLE_Y)          // never below the paddle line
        }
    }

    @Test fun demoPaddleChasesTheBall() {
        val g = newGame()
        step(g, 3f)
        assertTrue(abs(g.demoPaddleX - g.demoBallX) < 0.15f)
    }

    // A giant frame is clamped to 0.05 s so the demo ball can't warp through a
    // wall on a hitch: a 1 s frame must move it exactly like a 0.05 s one.
    @Test fun clampsHugeDtHitch() {
        val huge = newGame()
        val ref = newGame()
        huge.update(1f)
        ref.update(0.05f)
        assertEquals(ref.demoBallX, huge.demoBallX, 0f)
        assertEquals(ref.demoBallY, huge.demoBallY, 0f)
    }
}

class SettingsTest {
    @Test fun musicFollowsSoundAndState() {
        var music: Boolean? = null
        val g = Game(object : GameHost { override fun music(on: Boolean) { music = on } })
        g.setAspect(0.45f)
        g.soundEnabled = true
        assertEquals(false, music)                            // the title has no music
        g.soundEnabled = false
        assertEquals(false, music)
    }

    @Test fun highScoresAreCappedAtFive() {
        val g = newGame()
        g.restoreHighScores((1..8).map { HighScore(it * 100L, it) })
        assertEquals(5, g.highScores.size)
    }
}

class GeometryTest {
    @Test fun paddleSpeedScalesWithTheLane() {
        val phone = World.paddleSpeed(1080f / 2400f)
        val wide = World.paddleSpeed(2400f / 1080f)
        assertEquals(World.PADDLE_SPEED, phone, 1e-5f)
        assertTrue(wide > phone)
        assertEquals(World.PADDLE_SPEED * World.MAX_FIELD_HW / 0.45f, wide, 1e-5f)
    }

    @Test fun laneCornersClearNothingOnAPhone() {
        // On a portrait phone the lane is the whole width: no margins.
        assertEquals(0.45f, World.fieldHalfWidth(0.45f), 1e-6f)
        assertTrue(sqrt(0.63f * 0.63f + 0.62f * 0.62f) < 1f)
    }
}
