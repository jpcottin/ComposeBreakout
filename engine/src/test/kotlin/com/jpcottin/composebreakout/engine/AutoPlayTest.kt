package com.jpcottin.composebreakout.engine

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PHONE = 1080f / 2400f
private const val DT = 1f / 60f

private fun step(g: Game, seconds: Float) { var t = 0f; while (t < seconds) { g.update(DT); t += DT } }

/** A game in play with the autopilot on. */
private fun auto(aspect: Float = PHONE): Game = Game().apply {
    setAspect(aspect)
    debug.startGame()
    autoPlayActive = true
}

private fun brickAt(g: Game, row: Int, col: Int): Game.Brick = g.bricks[row * g.wallColumns + col]
private fun keepOnly(g: Game, vararg keep: Game.Brick) {
    for (i in g.bricks.indices) if (g.bricks[i] !in keep) g.debug.killBrick(i)
}

class AutoPlayTest {
    @Test fun launchesTheHeldBallOnItsOwn() {
        val g = auto()
        step(g, 0.3f)
        assertTrue(g.ballHeld)            // the serve delay is respected
        step(g, 0.5f)
        assertFalse(g.ballHeld)
    }

    @Test fun predictsWhereTheBallLands() {
        val g = auto()
        keepOnly(g, brickAt(g, 3, 0))     // one brick stays, far from the ball's path
        g.debug.setBall(-0.30f, 0.10f, 0.6f, 1.0f)   // falling to the right, nothing in the way
        g.update(DT)
        assertTrue(g.aiLanding.lands)
        val predictedX = g.aiLanding.x
        val predictedT = g.aiLanding.t
        // Let it fall with the paddle parked out of the way and see where it
        // was when it crossed the paddle line.
        g.autoPlayActive = false
        var actualX = 0f
        var actualT = 0f
        for (i in 0 until 300) {
            g.debug.setPaddleX(0.50f - 0.1f)         // far right, away from the ball
            g.update(DT)
            actualT += DT
            val b = g.balls.firstOrNull()
            if (b == null || b.y + World.BALL_R >= World.PADDLE_Y - World.PADDLE_HH) {
                if (b != null) actualX = b.x
                break
            }
            actualX = b.x
        }
        assertEquals(predictedX, actualX, 0.03f)
        assertEquals(predictedT, actualT, 0.10f)
    }

    @Test fun goesUnderTheBall() {
        val g = auto()
        g.debug.setBall(0.30f, 0.20f, 0f, 1f)      // dropping straight down at x 0.30
        g.update(DT)
        assertTrue(g.aiHasTarget)
        assertEquals(0.30f, g.aiTarget, World.PADDLE_HW)   // within a paddle of the landing
        step(g, 1f)
        assertEquals(5, g.lives)
        assertTrue(g.paddleHits >= 1)
    }

    @Test fun catchesABallComingOffTheWall() {
        val g = auto()
        g.debug.setBall(0.35f, 0.30f, 1f, 0.5f)    // heading for the right wall, then back
        g.update(DT)
        assertTrue(g.aiLanding.lands)
        assertTrue(g.aiLanding.x < 0.35f)          // it comes down left of where it is now
        step(g, 1.5f)
        assertEquals(5, g.lives)
        assertTrue(g.paddleHits >= 1)
    }

    @Test fun aimsTheReboundAtTheLastBrick() {
        val g = auto()
        // One brick left, far to the left; the ball drops straight down on
        // the right. A centre hit would send it back up the empty right side
        // for ever: the plan takes it off-centre so a rebound reaches the
        // brick — within a couple of trips, not by luck.
        keepOnly(g, brickAt(g, 3, 0))
        g.debug.setBall(0.30f, 0.10f, 0f, 1f)
        g.update(DT)
        assertTrue(g.aiLanding.lands)
        assertNotEquals(0.30f, g.aiTarget)         // not a centre hit
        var i = 0
        while (i < 60 * 12 && g.state == Game.State.PLAYING) { g.update(DT); i++ }
        assertEquals("the last brick is still up after 12 s", Game.State.LEVEL_CLEAR, g.state)
        assertEquals(5, g.lives)
    }

    @Test fun neverPlansADeadCentreHit() {
        // |u| < 0.12 all leave at the 8-degree minimum, left or right by the
        // sign of a hair's width of paddle placement: the plan never lives there.
        val g = auto()
        for (i in 0 until 20) {
            g.debug.setBall(-0.3f + 0.03f * i, 0.10f, 0f, 1f)
            g.update(DT)
            assertTrue(g.aiLanding.lands)
            assertTrue("ball $i", abs(g.aiPlanU) >= 0.15f)
        }
    }

    @Test fun collectsACapsuleItCanReachInTime() {
        val g = auto()
        g.debug.setBall(0f, 0f, 0f, -1f)            // the ball is on its way up: time to spare
        g.debug.spawnPowerUp(0.25f, 0.45f, Game.PowerUpType.LIFE)
        g.update(DT)
        assertTrue(g.aiHasTarget)
        assertEquals(0.25f, g.aiTarget, 0.02f)
        step(g, 1f)
        assertEquals(6, g.lives)
    }

    @Test fun leavesACapsuleTheBallDoesNotAllow() {
        val g = auto()
        // The ball lands on the left in half a second; a capsule on the far
        // right would cost the ball. Catch the ball.
        g.debug.setBall(-0.30f, 0.30f, 0f, 1f)
        g.debug.spawnPowerUp(0.33f, 0.50f, Game.PowerUpType.WIDE)
        g.update(DT)
        assertTrue(g.aiHasTarget)
        assertTrue(g.aiTarget < 0f)
        step(g, 1f)
        assertEquals(5, g.lives)
    }

    @Test fun skipsAWideCapsuleWhileWideWithTimeLeft() {
        val g = auto()
        g.debug.activatePowerUp(Game.PowerUpType.WIDE)
        g.debug.setBall(0f, 0f, 0f, -1f)
        g.debug.spawnPowerUp(0.25f, 0.45f, Game.PowerUpType.WIDE)
        g.update(DT)
        assertTrue(g.aiHasTarget)
        assertTrue(abs(g.aiTarget - 0.25f) > 0.05f)
        // An extra life in the same spot is always worth it.
        val h = auto()
        h.debug.activatePowerUp(Game.PowerUpType.WIDE)
        h.debug.setBall(0f, 0f, 0f, -1f)
        h.debug.spawnPowerUp(0.25f, 0.45f, Game.PowerUpType.LIFE)
        h.update(DT)
        assertTrue(h.aiHasTarget)
        assertEquals(0.25f, h.aiTarget, 0.02f)
    }

    @Test fun savesTheBallThatComesDownFirst() {
        val g = auto()
        g.debug.setBall(-0.30f, 0.40f, 0f, 1f)     // this one lands in a quarter second
        g.debug.addBall(0.30f, -0.60f, 0f, 1f)     // this one takes over a second
        g.update(DT)
        assertTrue(g.aiHasTarget)
        assertTrue(g.aiTarget < 0f)
    }

    @Test fun replansWhenTheBallBounces() {
        val g = auto()
        g.debug.setBall(0.30f, 0.30f, 1f, 0.3f)    // about to hit the right wall
        g.update(DT)
        val before = g.aiLanding.x
        step(g, 0.5f)                               // bounced: new direction, new plan
        assertTrue(g.aiLanding.lands)
        assertEquals(before, g.aiLanding.x, 0.05f)  // same landing, re-derived
        assertTrue(g.balls[0].dx < 0f)
    }

    @Test fun clearsLevelOneWithoutLosingABall() {
        for (seed in 1..4) {
            val g = Game()
            g.setAspect(PHONE)
            g.seed((seed.toLong() * 0x9E3779B9L).toInt())
            g.debug.startGame()
            g.autoPlayActive = true
            var i = 0
            while (i < 60 * 180 && g.state == Game.State.PLAYING) { g.update(DT); i++ }
            assertEquals("seed $seed: bricks left ${g.breakableLeft}", Game.State.LEVEL_CLEAR, g.state)
            assertEquals("seed $seed", 5, g.lives)
        }
    }

    @Test fun handlesMultiBall() {
        val g = Game()
        g.setAspect(PHONE)
        g.debug.startLevel(3)
        g.autoPlayActive = true
        step(g, 1f)                                 // launched
        g.debug.activatePowerUp(Game.PowerUpType.MULTI)
        assertEquals(3, g.balls.size)
        val lives = g.lives
        var i = 0
        while (i < 60 * 20 && g.state == Game.State.PLAYING) { g.update(DT); i++ }
        assertTrue(g.lives >= lives)                // three balls up, none of them cost a life
    }

    @Test fun playsTheLaneOnAWideScreen() {
        val g = Game()
        g.setAspect(2400f / 1080f)
        g.seed(7)
        g.debug.startGame()
        g.autoPlayActive = true
        var i = 0
        while (i < 60 * 240 && g.state == Game.State.PLAYING) { g.update(DT); i++ }
        assertEquals("bricks left ${g.breakableLeft}", Game.State.LEVEL_CLEAR, g.state)
    }

    @Test fun autoPlayOffLeavesThePaddleAlone() {
        val g = auto()
        g.autoPlayActive = false
        g.debug.setBall(0.30f, 0.20f, 0f, 1f)
        step(g, 0.5f)
        assertEquals(0f, g.paddleX, 1e-6f)
        assertFalse(g.aiHasTarget)
    }
}

/** The bench's guard rests on seeded runs giving the same numbers every
 *  time: the same seed must play the very same campaign twice. */
class DeterminismTest {
    private fun campaign(seed: Int, aspect: Float): Triple<Long, Int, Game.State> {
        val g = Game().apply { setAspect(aspect); seed(seed); debug.startGame(); autoPlayActive = true }
        var t = 0f
        while (t < 400f && g.state != Game.State.WIN && g.state != Game.State.GAME_OVER) {
            g.update(DT); t += DT
            while (g.state == Game.State.LEVEL_CLEAR) g.update(DT)
        }
        return Triple(g.score, g.bricksBroken, g.state)
    }

    @Test fun theSameSeedPlaysTheSameCampaign() {
        assertEquals(campaign(7, PHONE), campaign(7, PHONE))
        assertEquals(campaign(7, 2.2f), campaign(7, 2.2f))
    }

    @Test fun differentSeedsPlayDifferentCampaigns() {
        assertTrue(campaign(7, PHONE) != campaign(8, PHONE))
    }

    @Test fun theFrameSizeDoesNotChangeTheGame() {
        // 60 Hz frames and 120 Hz frames land on the same ticks.
        fun play(frame: Float): Pair<Long, Int> {
            val g = Game().apply { setAspect(PHONE); seed(3); debug.startGame(); autoPlayActive = true }
            var t = 0f
            while (t < 30f) { g.update(frame); t += frame }
            return g.score to g.bricksBroken
        }
        assertEquals(play(1f / 60f), play(1f / 120f))
    }
}
