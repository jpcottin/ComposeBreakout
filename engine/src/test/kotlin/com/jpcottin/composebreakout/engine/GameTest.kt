package com.jpcottin.composebreakout.engine

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// ── helpers ───────────────────────────────────────────────────────────────────

private const val PHONE = 1080f / 2400f       // portrait phone, 7 columns
private const val FOLDABLE = 2152f / 2076f    // unfolded, 10 columns (the lane cap)
private const val LANDSCAPE = 2400f / 1080f   // landscape / TV, 10 columns
private const val STRIP_Y = 0.90f             // a world y inside the control strip
private const val DT = 1f / 60f

/** A host that records everything the game asks of it. */
private class RecordingHost : GameHost {
    val sounds = ArrayList<SoundEvent>()
    val breaks = ArrayList<Float>()
    val music = ArrayList<Boolean>()
    var haptics = 0
    val tables = ArrayList<List<HighScore>>()
    override fun sound(event: SoundEvent) { sounds.add(event) }
    override fun brickBreak(hz: Float) { breaks.add(hz) }
    override fun music(on: Boolean) { music.add(on) }
    override fun haptic() { haptics++ }
    override fun highScoresChanged(table: List<HighScore>) { tables.add(table) }
    override fun furthestLevelChanged(level: Int) { furthest.add(level) }
    val furthest = ArrayList<Int>()
}

private fun newGame(aspect: Float = PHONE, host: GameHost = GameHost.None): Game =
    Game(host).apply { setAspect(aspect) }

private fun step(g: Game, seconds: Float, dt: Float = DT) {
    var t = 0f
    while (t < seconds) { g.update(dt); t += dt }
}

/** Tap = press and release; the game consumes it on the next update(). */
private fun tap(g: Game, wx: Float = 0f, wy: Float = 0f) {
    g.onPointerDown(0, wx, wy)
    g.onPointerUp(0)
    g.update(DT)
}

private fun startGame(g: Game) { tap(g); assertEquals(Game.State.PLAYING, g.state) }

/** A game in play on level 1, the ball held on the paddle. */
private fun playing(aspect: Float = PHONE, host: GameHost = GameHost.None): Game =
    newGame(aspect, host).also { startGame(it) }

/** The brick at (row, col): the wall is built row-major. */
private fun brickAt(g: Game, row: Int, col: Int): Game.Brick = g.bricks[row * g.wallColumns + col]

/** Keep only the listed bricks alive. */
private fun keepOnly(g: Game, vararg keep: Game.Brick) {
    for (i in g.bricks.indices) if (g.bricks[i] !in keep) g.debug.killBrick(i)
}

/** Drop the ball into the pit, away from the paddle: one life gone. */
private fun loseABall(g: Game) {
    g.debug.setBall(0.30f, 0.68f, 0f, 1f)
    step(g, 0.5f)
}

/** Drive a game straight to a game over at the given score. */
private fun gameOverAt(g: Game, score: Long) {
    g.debug.setScore(score)
    g.debug.setLives(1)
    loseABall(g)
    assertEquals(Game.State.GAME_OVER, g.state)
}

// ── Initial state ─────────────────────────────────────────────────────────────

class InitialStateTest {
    @Test fun startsOnTitle() {
        val g = newGame()
        assertEquals(Game.State.TITLE, g.state)
        assertEquals(5, g.lives)
        assertEquals(0L, g.score)
        assertEquals(1, g.level)
        assertTrue(g.balls.isEmpty())
    }

    @Test fun tapStartsGame() {
        val g = newGame()
        tap(g)
        assertEquals(Game.State.PLAYING, g.state)
        assertEquals(1, g.level)
        assertEquals(5, g.lives)
        assertEquals(0L, g.score)
        assertTrue(g.ballHeld)
        assertEquals(1, g.balls.size)
    }

    @Test fun positionlessTapStartsGameToo() {
        val g = newGame()
        g.tap(); g.update(DT)
        assertEquals(Game.State.PLAYING, g.state)
    }
}

// ── The wall ──────────────────────────────────────────────────────────────────

class WallTest {
    @Test fun columnsFollowTheLaneWidth() {
        assertEquals(7, World.wallColumns(PHONE))
        assertEquals(10, World.wallColumns(FOLDABLE))
        assertEquals(10, World.wallColumns(LANDSCAPE))
        val g = newGame()
        assertEquals(5 * 7, g.bricks.size)              // the title demo wall: five rows
        g.setAspect(LANDSCAPE)
        assertEquals(5 * 10, g.bricks.size)
    }

    @Test fun level1IsFourRowsOfNormalBricks() {
        val g = playing()
        assertEquals(4, g.rows)
        assertEquals(4 * 7, g.bricks.size)
        assertTrue(g.bricks.all { it.alive && it.type == Game.BrickType.NORMAL && it.hp == 1 })
    }

    @Test fun rowsGrowWithLevel() {
        val expected = intArrayOf(4, 5, 5, 6, 6, 7, 7, 8, 8, 9)
        for (l in 1..10) assertEquals(expected[l - 1], Levels.rows(l))
        val g = playing()
        g.debug.startLevel(10)
        assertEquals(9, g.rows)
    }

    @Test fun everyWallFitsTheLane() {
        for (aspect in listOf(PHONE, FOLDABLE, LANDSCAPE)) {
            val g = playing(aspect)
            for (l in 1..10) {
                g.debug.startLevel(l)
                for (b in g.bricks) {
                    assertTrue(abs(g.brickX(b)) + World.BRICK_HW < g.fieldHalfWidth)
                    assertTrue(g.brickY(b) - World.BRICK_HH > World.TOP_WALL_Y)
                    assertTrue(g.brickY(b) + World.BRICK_HH < World.PADDLE_Y - 0.25f)
                }
            }
        }
    }

    @Test fun tougherBricksArriveWithTheLevels() {
        val g = playing()
        g.debug.startLevel(2)
        assertTrue(g.bricks.all { it.type == Game.BrickType.NORMAL })
        g.debug.startLevel(3)
        assertTrue(g.bricks.any { it.type == Game.BrickType.TOUGH && it.hp == 2 })
        assertTrue(g.bricks.none { it.type == Game.BrickType.STEEL })
        g.debug.startLevel(5)
        assertTrue(g.bricks.any { it.type == Game.BrickType.STEEL })
        g.debug.startLevel(7)
        assertTrue(g.bricks.any { it.type == Game.BrickType.ARMOR && it.hp == 3 })
    }

    @Test fun steelDoesNotCountTowardTheClear() {
        val g = playing()
        g.debug.startLevel(5)
        val steel = g.bricks.count { it.type == Game.BrickType.STEEL }
        assertTrue(steel > 0)
        assertEquals(g.bricks.size - steel, g.breakableLeft)
    }

    @Test fun level4BottomRowHasHoles() {
        val g = playing()
        g.debug.startLevel(4)
        val bottom = g.rows - 1
        assertTrue(brickAt(g, bottom, 0).alive)
        assertFalse(brickAt(g, bottom, 1).alive)
        assertTrue(brickAt(g, 0, 1).alive)
    }

    @Test fun levelClampsAtTen() {
        val g = playing()
        g.debug.startLevel(42)
        assertEquals(10, g.level)
        g.debug.startLevel(-3)
        assertEquals(1, g.level)
    }

    @Test fun everyDemoBrickSitsInsideTheLane() {
        for (aspect in listOf(PHONE, FOLDABLE, LANDSCAPE)) {
            val g = newGame(aspect)
            for (b in g.bricks) assertTrue(abs(g.brickX(b)) + World.BRICK_HW < g.fieldHalfWidth)
        }
    }
}

// ── Serve and launch ─────────────────────────────────────────────────────────

class ServeTest {
    @Test fun ballRestsOnThePaddleUntilLaunched() {
        val g = playing()
        step(g, 2f)
        assertTrue(g.ballHeld)
        assertEquals(g.paddleX, g.balls[0].x, 1e-6f)
        assertEquals(World.PADDLE_Y - World.PADDLE_HH - World.BALL_R, g.balls[0].y, 1e-6f)
    }

    @Test fun heldBallFollowsThePaddle() {
        val g = playing()
        g.onPointerDown(0, 0.30f, STRIP_Y)
        step(g, 0.3f)
        assertTrue(g.ballHeld)    // launched by the strip only after the grace
        assertTrue(g.paddleX > 0.1f)
        assertEquals(g.paddleX, g.balls[0].x, 1e-6f)
    }

    @Test fun earlyTapIsIgnored() {
        val g = playing()
        tap(g)
        step(g, 0.2f)
        assertTrue(g.ballHeld)
    }

    @Test fun tapLaunchesAfterTheServeDelay() {
        val g = playing()
        step(g, Game.SERVE_DELAY + 0.05f)
        assertTrue(g.serveReady)
        tap(g)
        assertFalse(g.ballHeld)
        assertTrue(g.balls[0].dy < 0f)                 // up
        assertTrue(abs(g.balls[0].dx) > 0.2f)          // and to a side
    }

    @Test fun holdingTheStripLaunchesToo() {
        val g = playing()
        g.onPointerDown(0, 0f, STRIP_Y)
        step(g, Game.SERVE_DELAY + 0.1f)
        assertFalse(g.ballHeld)
    }
}

// ── Paddle control (finger in the bottom strip) ───────────────────────────────

class PaddleControlTest {
    @Test fun movesTowardFingerInStrip() {
        val g = playing()
        g.onPointerDown(0, 0.30f, STRIP_Y)
        step(g, 0.1f)
        assertTrue(g.paddleX > 0.05f)
    }

    @Test fun ignoresFingerAboveStrip() {
        val g = playing()
        g.onPointerDown(0, 0.30f, 0.0f)
        step(g, 0.5f)
        assertEquals(0f, g.paddleX, 1e-6f)
    }

    @Test fun touchbarModeSteersFromAnywhere() {
        val g = playing()
        g.controlMode = Game.ControlMode.TOUCHBAR
        g.onPointerDown(0, -0.30f, 0.0f)
        step(g, 0.5f)
        assertTrue(g.paddleX < -0.2f)
    }

    @Test fun stopsOnTheFingerX() {
        val g = playing()
        g.onPointerDown(0, 0.20f, STRIP_Y)
        step(g, 1f)
        assertEquals(0.20f, g.paddleX, 0.01f)
    }

    @Test fun clampedToLaneEdge() {
        val g = playing()
        g.onPointerDown(0, 5f, STRIP_Y)
        step(g, 2f)
        assertEquals(g.fieldHalfWidth - World.PADDLE_HW, g.paddleX, 1e-5f)
    }

    // A mid-session viewport change (fold/unfold, projected display) must
    // clamp the paddle back inside the new, narrower playfield.
    @Test fun aspectShrinkClampsThePaddle() {
        val g = playing(LANDSCAPE)
        g.onPointerDown(0, 5f, STRIP_Y)
        step(g, 2f)
        g.onPointerUp(0)
        assertEquals(World.MAX_FIELD_HW - World.PADDLE_HW, g.paddleX, 1e-5f)
        g.setAspect(PHONE)
        assertTrue(g.paddleX <= PHONE - World.PADDLE_HW + 1e-5f)
    }

    @Test fun dragSteersThePaddle() {
        val g = playing()
        g.onPointerDown(0, 0.20f, STRIP_Y)
        step(g, 0.5f)
        g.onPointerMove(0, -0.20f, STRIP_Y)
        step(g, 0.5f)
        assertEquals(-0.20f, g.paddleX, 0.01f)
    }

    @Test fun cancelReleasesAllPointers() {
        val g = playing()
        g.onPointerDown(0, 0.40f, STRIP_Y)
        g.onPointerDown(1, 0.40f, STRIP_Y)
        step(g, 0.05f)
        g.onPointersCancel()
        val x = g.paddleX
        step(g, 0.5f)
        assertEquals(x, g.paddleX, 1e-6f)
    }

    @Test fun noTouchNoMovement() {
        val g = playing()
        step(g, 1f)
        assertEquals(0f, g.paddleX, 1e-6f)
    }

    // A giant frame is clamped to 0.05 s so the paddle can't warp on a hitch.
    @Test fun clampsHugeDtHitch() {
        val huge = playing()
        val ref = playing()
        huge.onPointerDown(0, 0.40f, STRIP_Y)
        ref.onPointerDown(0, 0.40f, STRIP_Y)
        huge.update(1f)
        ref.update(0.05f)
        assertEquals(ref.paddleX, huge.paddleX, 0f)
    }
}

// ── Ball physics ─────────────────────────────────────────────────────────────

class BallTest {
    @Test fun aBallCountsItsFlightSinceTheLastBrick() {
        val g = newGame()
        g.debug.startGame()
        g.debug.setBall(0f, 0.5f, 0f, 1f)                // heading down, away from the wall
        g.debug.setPaddleX(0f)
        step(g, 0.5f)
        val b = g.balls.first()
        assertTrue("dry ${b.dry}", b.dry > 0.4f)
        g.debug.setBall(0f, -0.2f, 0f, -1f)              // straight up into the wall
        val fresh = g.balls.first()
        val before = g.bricksBroken
        step(g, 1.5f)
        assertTrue(g.bricksBroken > before)
        assertTrue("dry ${fresh.dry} should have reset on the hit", fresh.dry < 1.5f)
    }

    @Test fun bouncesOffTheSideWalls() {
        val g = playing()
        g.debug.setBall(0.40f, 0.0f, 1f, 0f)
        step(g, 0.3f)
        assertTrue(g.balls[0].dx < 0f)
        assertTrue(g.balls[0].x <= g.fieldHalfWidth - World.BALL_R + 1e-5f)
    }

    @Test fun bouncesOffTheCeiling() {
        val g = playing()
        keepOnly(g, brickAt(g, 0, 0))   // one brick stays so the level does not end
        g.debug.setBall(0.20f, 0.0f, 0f, -1f)
        step(g, 1.2f)
        assertTrue(g.balls[0].dy > 0f)
        assertTrue(g.balls[0].y >= World.TOP_WALL_Y + World.BALL_R - 1e-5f)
    }

    @Test fun bouncesOffThePaddle() {
        val g = playing()
        g.debug.setBall(0f, 0.50f, 0f, 1f)
        step(g, 0.3f)
        assertTrue(g.balls[0].dy < 0f)
        assertEquals(1, g.paddleHits)
        assertEquals(5, g.lives)
    }

    @Test fun paddleTipSendsTheBallSideways() {
        val g = playing()
        g.debug.setBall(World.PADDLE_HW, 0.55f, 0f, 1f)
        step(g, 0.3f)
        assertTrue(g.balls[0].dx > 0.8f)
    }

    @Test fun paddleCentreSendsItNearlyStraightUp() {
        val g = playing()
        g.debug.setBall(0f, 0.55f, 0f, 1f)
        step(g, 0.3f)
        assertTrue(abs(g.balls[0].dx) < 0.2f)
        assertTrue(g.balls[0].dy < -0.9f)
    }

    @Test fun passingThePaddleCostsALifeAndServesAgain() {
        val g = playing()
        loseABall(g)
        assertEquals(4, g.lives)
        assertEquals(1, g.ballsLost)
        assertTrue(g.ballHeld)
        assertEquals(Game.State.PLAYING, g.state)
    }

    @Test fun losingTheLastLifeEndsTheGame() {
        val g = playing()
        g.debug.setLives(1)
        loseABall(g)
        assertEquals(Game.State.GAME_OVER, g.state)
        assertEquals(0, g.lives)
        assertTrue(g.balls.isEmpty())
    }

    @Test fun breaksABrickAndScores() {
        val g = playing()
        val target = brickAt(g, g.rows - 1, 3)
        g.debug.setBall(g.brickX(target), g.brickY(target) + 0.15f, 0f, -1f)
        step(g, 0.3f)
        assertFalse(target.alive)
        assertEquals(10L, g.score)
        assertEquals(1, g.bricksBroken)
        assertTrue(g.balls[0].dy > 0f)
    }

    @Test fun toughBrickTakesTwoHits() {
        val g = playing()
        g.debug.startLevel(3)
        val target = brickAt(g, 0, 3)
        assertEquals(Game.BrickType.TOUGH, target.type)
        keepOnly(g, target, brickAt(g, 0, 0))   // another brick stays: no level clear
        g.debug.setBall(g.brickX(target), g.brickY(target) + 0.15f, 0f, -1f)
        step(g, 0.3f)
        assertTrue(target.alive)
        assertEquals(1, target.hp)
        assertEquals(0L, g.score)
        g.debug.setBall(g.brickX(target), g.brickY(target) + 0.15f, 0f, -1f)
        step(g, 0.3f)
        assertFalse(target.alive)
        assertEquals(20L * 3, g.score)
    }

    @Test fun steelBrickNeverBreaks() {
        val g = playing()
        g.debug.startLevel(5)
        val steel = brickAt(g, 3, 1)
        assertEquals(Game.BrickType.STEEL, steel.type)
        keepOnly(g, steel, brickAt(g, 0, 5))
        g.debug.setBall(g.brickX(steel), g.brickY(steel) + 0.15f, 0f, -1f)
        step(g, 0.3f)
        assertTrue(steel.alive)
        assertEquals(0L, g.score)
        assertTrue(g.balls[0].dy > 0f)
        assertEquals(Game.State.PLAYING, g.state)
    }

    @Test fun sideHitReflectsHorizontally() {
        val g = playing()
        val target = brickAt(g, g.rows - 1, 3)
        keepOnly(g, target, brickAt(g, 0, 0))   // another brick stays: no level clear
        g.debug.setBall(g.brickX(target) - 0.20f, g.brickY(target), 1f, 0f)
        step(g, 0.4f)
        assertTrue(g.balls[0].dx < 0f)
        assertTrue(abs(g.balls[0].dy) >= 0.25f - 1e-5f)   // never shuttles flat
        assertFalse(target.alive)
    }

    @Test fun speedsUpAsTheWallThins() {
        val g = playing()
        val base = g.ballSpeed
        val target = brickAt(g, g.rows - 1, 3)
        g.debug.setBall(g.brickX(target), g.brickY(target) + 0.15f, 0f, -1f)
        step(g, 0.3f)
        assertTrue(g.ballSpeed > base)
    }

    @Test fun fasterWithEveryLevel() {
        val g = playing()
        var last = 0f
        for (l in 1..10) {
            g.debug.startLevel(l)
            assertTrue(g.ballSpeed > last)
            last = g.ballSpeed
        }
    }

    // At the clamped worst-case dt (0.05 s) the ball moves more than its own
    // diameter per frame. Substepped collision must keep it inside the walls
    // and breaking bricks instead of tunnelling through them.
    @Test fun neverTunnelsAtTheClampedWorstCaseDt() {
        val g = playing()
        g.debug.startLevel(10)
        g.debug.setBall(0.1f, 0.3f, 0.3f, -1f)
        repeat(200) {
            g.onPointerDown(0, g.balls.firstOrNull()?.x ?: 0f, STRIP_Y)
            g.update(0.05f)
            for (b in g.balls) {
                assertTrue(abs(b.x) <= g.fieldHalfWidth - World.BALL_R + 1e-4f)
                assertTrue(b.y >= World.TOP_WALL_Y + World.BALL_R - 1e-4f)
            }
        }
        assertTrue(g.bricksBroken > 0)
    }
}

// ── Bonuses ──────────────────────────────────────────────────────────────────

class BonusTest {
    @Test fun fallingCapsuleIsCollectedByThePaddle() {
        val g = playing()
        g.debug.spawnPowerUp(0f, 0.55f, Game.PowerUpType.WIDE)
        step(g, 0.5f)
        assertTrue(g.powerUps.isEmpty())
        assertTrue(g.wideTimer > 0f)
        assertEquals(Game.POWER_UP_SCORE.toLong(), g.score)
        assertTrue(g.bonusFlashTimer > 0f)
    }

    @Test fun uncollectedCapsuleFallsPast() {
        val g = playing()
        g.debug.spawnPowerUp(-0.40f, 0.55f, Game.PowerUpType.WIDE)
        step(g, 2.5f)
        assertTrue(g.powerUps.isEmpty())
        assertEquals(0L, g.score)
        assertEquals(0f, g.wideTimer, 0f)
    }

    @Test fun widePaddleWidensThenExpires() {
        val g = playing()
        g.debug.activatePowerUp(Game.PowerUpType.WIDE)
        assertEquals(World.WIDE_PADDLE_HW, g.paddleHalfWidth, 0f)
        step(g, Game.WIDE_DURATION + 0.1f)
        assertEquals(World.PADDLE_HW, g.paddleHalfWidth, 0f)
    }

    @Test fun widePaddleAtTheEdgeIsClampedIn() {
        val g = playing()
        g.debug.setPaddleX(g.fieldHalfWidth - World.PADDLE_HW)
        g.debug.activatePowerUp(Game.PowerUpType.WIDE)
        g.update(DT)
        assertEquals(g.fieldHalfWidth - World.WIDE_PADDLE_HW, g.paddleX, 1e-5f)
        step(g, Game.WIDE_DURATION + 0.1f)
        assertTrue(g.paddleX <= g.fieldHalfWidth - World.PADDLE_HW + 1e-5f)
    }

    @Test fun slowBallSlowsThenExpires() {
        val g = playing()
        val base = g.ballSpeed
        g.debug.activatePowerUp(Game.PowerUpType.SLOW)
        assertEquals(base * Levels.SLOW_FACTOR, g.ballSpeed, 1e-5f)
        step(g, Game.SLOW_DURATION + 0.1f)
        assertEquals(base, g.ballSpeed, 1e-5f)
    }

    @Test fun multiBallSplitsTheBallInThree() {
        val g = playing()
        g.debug.launch()
        g.debug.activatePowerUp(Game.PowerUpType.MULTI)
        assertEquals(3, g.balls.size)
        assertNotEquals(g.balls[0].dx, g.balls[1].dx)
        assertNotEquals(g.balls[0].dx, g.balls[2].dx)
    }

    @Test fun multiBallLaunchesAHeldBall() {
        val g = playing()
        assertTrue(g.ballHeld)
        g.debug.activatePowerUp(Game.PowerUpType.MULTI)
        assertFalse(g.ballHeld)
        assertEquals(3, g.balls.size)
    }

    @Test fun multiBallCapsAtSix() {
        val g = playing()
        g.debug.launch()
        g.debug.activatePowerUp(Game.PowerUpType.MULTI)
        g.debug.activatePowerUp(Game.PowerUpType.MULTI)
        assertEquals(Game.MAX_BALLS, g.balls.size)
    }

    @Test fun losingOneOfSeveralBallsCostsNoLife() {
        val g = playing()
        g.debug.setBall(0f, 0.0f, 0f, -1f)
        g.debug.addBall(0.30f, 0.68f, 0f, 1f)
        step(g, 0.5f)
        assertEquals(5, g.lives)
        assertEquals(1, g.balls.size)
    }

    @Test fun extraLifeAddsOneUpToNine() {
        val g = playing()
        g.debug.activatePowerUp(Game.PowerUpType.LIFE)
        assertEquals(6, g.lives)
        g.debug.setLives(Game.MAX_LIVES)
        g.debug.activatePowerUp(Game.PowerUpType.LIFE)
        assertEquals(Game.MAX_LIVES, g.lives)
    }

    @Test fun timedBonusesEndWithTheBall() {
        val g = playing()
        g.debug.activatePowerUp(Game.PowerUpType.WIDE)
        g.debug.activatePowerUp(Game.PowerUpType.SLOW)
        loseABall(g)
        assertEquals(0f, g.wideTimer, 0f)
        assertEquals(0f, g.slowTimer, 0f)
        assertEquals(4, g.lives)
    }

    // Over a few dozen broken bricks the 12% drop chance must fire.
    @Test fun brokenBricksDropCapsulesSometimes() {
        val g = playing()
        g.debug.startLevel(2)
        var drops = 0
        var broken = 0
        repeat(100) { i ->
            val b = g.bricks[1 + i % (g.bricks.size - 1)]
            keepOnly(g, b, brickAt(g, 0, 0))
            g.debug.setBall(g.brickX(b), g.brickY(b) + 0.1f, 0f, -1f)
            step(g, 0.2f)
            if (!b.alive) broken++
            drops += g.powerUps.size
            g.debug.startLevel(2)
        }
        assertEquals(100, broken)
        assertTrue("$drops drops in 100 breaks", drops in 3..30)
    }
}

// ── Level clear / win ─────────────────────────────────────────────────────────

class ProgressionTest {
    @Test fun clearingTheWallPaysABonusAndAdvances() {
        val g = playing()
        g.debug.clearWall()
        g.update(DT)
        assertEquals(Game.State.LEVEL_CLEAR, g.state)
        assertEquals(Game.LEVEL_CLEAR_SCORE.toLong(), g.score)
        assertTrue(g.balls.isEmpty())
        step(g, Game.LEVEL_CLEAR_PAUSE + 0.1f)
        assertEquals(Game.State.PLAYING, g.state)
        assertEquals(2, g.level)
        assertEquals(5, g.rows)
        assertTrue(g.ballHeld)
    }

    @Test fun levelClearBonusScalesWithLevel() {
        val g = playing()
        g.debug.startLevel(3)
        g.debug.clearWall()
        g.update(DT)
        assertEquals(Game.LEVEL_CLEAR_SCORE.toLong() * 3, g.score)
    }

    @Test fun clearingLevelTenWins() {
        val host = RecordingHost()
        val g = playing(host = host)
        g.debug.startLevel(10)
        g.debug.clearWall()
        g.update(DT)
        assertEquals(Game.State.WIN, g.state)
        assertEquals(1000L, g.score)
        assertTrue(g.newHighScore)
        assertEquals(listOf(HighScore(1000, 10)), host.tables.last())
    }

    @Test fun gameOverTapReturnsToTitle() {
        val g = playing()
        g.debug.setLives(1)
        loseABall(g)
        step(g, Game.END_SCREEN_GRACE + 0.1f)
        tap(g)
        assertEquals(Game.State.TITLE, g.state)
        assertEquals(1, g.level)
        assertEquals(5 * 7, g.bricks.size)
    }

    @Test fun gameOverIgnoresEarlyTap() {
        val g = playing()
        g.debug.setLives(1)
        loseABall(g)
        tap(g)
        assertEquals(Game.State.GAME_OVER, g.state)
    }

    @Test fun titleReturnClearsLeftovers() {
        val g = playing()
        g.debug.spawnPowerUp(-0.3f, 0.0f, Game.PowerUpType.SLOW)
        val target = brickAt(g, g.rows - 1, 3)
        g.debug.setBall(g.brickX(target), g.brickY(target) + 0.15f, 0f, -1f)
        step(g, 0.3f)
        assertTrue(g.particles.isNotEmpty())
        g.debug.setLives(1)
        loseABall(g)
        step(g, Game.END_SCREEN_GRACE + 0.1f)
        tap(g)
        assertEquals(Game.State.TITLE, g.state)
        assertTrue(g.powerUps.isEmpty())
        assertTrue(g.balls.isEmpty())
        assertTrue(g.particles.isEmpty())
        assertTrue(g.explosions.isEmpty())
        assertEquals(0f, g.shakeX, 0f)
    }

    // A fold/unfold mid-level changes how many columns fit: the wall is
    // rebuilt for the new width, score and lives stay, a fresh ball is served.
    @Test fun foldMidLevelRebuildsTheWall() {
        val g = playing()
        g.debug.setScore(70)
        g.debug.setLives(3)
        g.debug.launch()
        g.setAspect(FOLDABLE)
        assertEquals(10, g.wallColumns)
        assertEquals(4 * 10, g.bricks.size)
        assertEquals(Game.State.PLAYING, g.state)
        assertTrue(g.ballHeld)
        assertEquals(70L, g.score)
        assertEquals(3, g.lives)
    }

    // A wide screen plays in a central lane: the ball and the paddle stay
    // inside it, the margins outside are dead space.
    @Test fun wideScreenPlaysInACentralLane() {
        val g = playing(LANDSCAPE)
        assertEquals(World.MAX_FIELD_HW, g.fieldHalfWidth, 0f)
        g.debug.setBall(0.5f, 0.0f, 1f, 0f)
        step(g, 1f)
        assertTrue(g.balls[0].x <= World.MAX_FIELD_HW - World.BALL_R + 1e-5f)
        g.onPointerDown(0, 2f, STRIP_Y)
        step(g, 2f)
        assertEquals(World.MAX_FIELD_HW - World.PADDLE_HW, g.paddleX, 1e-5f)
    }
}

// ── Process-death resume ─────────────────────────────────────────────────────

class RestoreSessionTest {
    @Test fun resumesLevelScoreAndLives() {
        val g = newGame()
        g.restoreSession(4, 1234, 2)
        assertEquals(Game.State.PLAYING, g.state)
        assertEquals(4, g.level)
        assertEquals(1234L, g.score)
        assertEquals(2, g.lives)
        assertEquals(Levels.rows(4), g.rows)
        assertTrue(g.ballHeld)
        assertTrue(g.isMidGame)
    }

    @Test fun rejectsOutOfRangeInput() {
        for ((level, score, lives) in listOf(
            Triple(0, 100L, 3), Triple(11, 100L, 3), Triple(3, -1L, 3),
            Triple(3, 100L, 0), Triple(3, 100L, 10),
        )) {
            val g = newGame()
            g.restoreSession(level, score, lives)
            assertEquals(Game.State.TITLE, g.state)
            assertEquals(0L, g.score)
        }
    }

    @Test fun acceptsTheBoundaries() {
        val g = newGame()
        g.restoreSession(Levels.COUNT, 0, Game.MAX_LIVES)
        assertEquals(Game.State.PLAYING, g.state)
        assertEquals(Levels.COUNT, g.level)
        assertEquals(Game.MAX_LIVES, g.lives)
        val h = newGame()
        h.restoreSession(1, 0, 1)
        assertEquals(Game.State.PLAYING, h.state)
        assertEquals(1, h.lives)
    }

    // The saved round may sit past the record the host has stored (the
    // record is written asynchronously): the restore raises it and says so.
    @Test fun restoringPastTheRecordRaisesIt() {
        val host = RecordingHost()
        val g = newGame(host = host)
        g.restoreSession(7, 500, 3)
        assertEquals(7, g.furthestLevel)
        assertEquals(listOf(7), host.furthest)
    }

    // A restore mid-round (a stale saved state applied late) replaces the
    // round in flight wholesale: fresh wall, held serve, no bonus left over.
    @Test fun replacesTheRoundInFlight() {
        val g = playing()
        g.debug.setScore(900)
        g.debug.setLives(2)
        g.debug.launch()
        g.debug.activatePowerUp(Game.PowerUpType.WIDE)
        g.debug.killBrick(0)
        step(g, 0.5f)
        g.pause()
        assertTrue(g.paused)
        g.restoreSession(2, 40, 4)
        assertEquals(2, g.level)
        assertEquals(40L, g.score)
        assertEquals(4, g.lives)
        assertTrue(g.ballHeld)
        assertFalse(g.paused)
        assertEquals(0f, g.wideTimer, 0f)
        assertTrue(g.bricks.all { it.alive })
        assertEquals(0, g.bricksBroken)
    }

    // The restored lives are the ones at stake: with one left, the next lost
    // ball ends the game, and the restored score goes to the table.
    @Test fun restoredLivesAreTheOnesAtStake() {
        val g = newGame()
        g.restoreSession(3, 900, 1)
        g.debug.launch()
        loseABall(g)
        assertEquals(Game.State.GAME_OVER, g.state)
        assertEquals(listOf(HighScore(900, 3)), g.highScores)
        assertTrue(g.newHighScore)
    }

    // The title screen's level pick is not the saved round's level.
    @Test fun leavesTheTitlePickAlone() {
        val g = newGame()
        g.furthestLevel = 8
        g.selectLevel(2)
        assertEquals(3, g.startLevel)
        g.restoreSession(6, 100, 3)
        assertEquals(6, g.level)
        assertEquals(3, g.startLevel)
    }

    // A held serve has nothing to freeze, so a pause straight after the
    // restore (the activity is still coming up) leaves the round playable.
    @Test fun aRestoredServeIgnoresAnEarlyPause() {
        val g = newGame()
        g.restoreSession(2, 100, 3)
        g.pause()
        assertFalse(g.paused)
        assertTrue(g.isMidGame)
        step(g, 0.5f)
        g.debug.launch()
        assertFalse(g.ballHeld)
        step(g, 0.5f)
        assertEquals(Game.State.PLAYING, g.state)
    }
}

// ── High scores ──────────────────────────────────────────────────────────────

class HighScoresTest {
    @Test fun tableOrdersDisplacesAndCaps() {
        val host = RecordingHost()
        val g = playing(host = host)
        g.restoreHighScores(listOf(HighScore(300, 1), HighScore(100, 1)))
        gameOverAt(g, 200)
        assertTrue(g.newHighScore)
        assertEquals(1, g.newHighScoreRank)
        assertEquals(listOf(300L, 200L, 100L), g.highScores.map { it.score })
        assertEquals(g.highScores, host.tables.last())
    }

    @Test fun cappedAtFiveAndLowScoresAreNotRanked() {
        val host = RecordingHost()
        val g = playing(host = host)
        g.restoreHighScores((5 downTo 1).map { HighScore(it * 100L, it) })
        gameOverAt(g, 50)
        assertFalse(g.newHighScore)
        assertTrue(host.tables.isEmpty())
        assertEquals(5, g.highScores.size)
        step(g, Game.END_SCREEN_GRACE + 0.1f); tap(g); startGame(g)
        gameOverAt(g, 450)
        assertTrue(g.newHighScore)
        assertEquals(1, g.newHighScoreRank)
        assertEquals(listOf(500L, 450L, 400L, 300L, 200L), g.highScores.map { it.score })
    }

    @Test fun zeroScoreIsNotRecorded() {
        val host = RecordingHost()
        val g = playing(host = host)
        gameOverAt(g, 0)
        assertFalse(g.newHighScore)
        assertTrue(host.tables.isEmpty())
    }

    @Test fun tieWithExistingEntryDoesNotDuplicate() {
        val g = playing()
        g.restoreHighScores(listOf(HighScore(200, 1)))
        gameOverAt(g, 200)
        assertFalse(g.newHighScore)
        assertEquals(1, g.highScores.size)
    }

    @Test fun restoredTableIsSortedAndDeduplicated() {
        val g = newGame()
        g.restoreHighScores(listOf(HighScore(100, 1), HighScore(300, 2), HighScore(100, 1), HighScore(200, 1)))
        assertEquals(listOf(300L, 200L, 100L), g.highScores.map { it.score })
    }
}

// ── The host: sounds, music, haptics ─────────────────────────────────────────

class HostTest {
    @Test fun paddleAndWallBouncesMakeSounds() {
        val host = RecordingHost()
        val g = playing(host = host)
        g.debug.setBall(0f, 0.50f, 0f, 1f)
        step(g, 0.3f)
        assertTrue(SoundEvent.PADDLE in host.sounds)
        g.debug.setBall(0.40f, 0.0f, 1f, 0f)
        step(g, 0.3f)
        assertTrue(SoundEvent.WALL in host.sounds)
    }

    @Test fun brickBreakIsPitchedByRow() {
        val host = RecordingHost()
        val g = playing(host = host)
        val target = brickAt(g, g.rows - 1, 3)
        g.debug.setBall(g.brickX(target), g.brickY(target) + 0.15f, 0f, -1f)
        step(g, 0.3f)
        assertEquals(1, host.breaks.size)
        assertEquals(523.25f, host.breaks[0], 0.01f)   // the bottom row is the lowest note
    }

    @Test fun lostBallIsFeltAndHeard() {
        val host = RecordingHost()
        val g = playing(host = host)
        loseABall(g)
        assertEquals(1, host.haptics)
        assertTrue(SoundEvent.BALL_LOST in host.sounds)
    }

    @Test fun soundOffSilencesEverything() {
        val host = RecordingHost()
        val g = newGame(host = host)
        g.soundEnabled = false
        startGame(g)
        g.debug.setBall(0f, 0.50f, 0f, 1f)
        step(g, 0.3f)
        loseABall(g)
        assertTrue(host.sounds.isEmpty())
        assertTrue(host.breaks.isEmpty())
        assertEquals(1, host.haptics)                    // haptics are not sound
    }

    @Test fun musicFollowsTheRound() {
        val host = RecordingHost()
        val g = newGame(host = host)
        startGame(g)
        assertEquals(true, host.music.last())
        g.glassesActive = true                            // handed off to the glasses
        assertEquals(false, host.music.last())
        g.glassesActive = false
        assertEquals(true, host.music.last())
        g.soundEnabled = false
        assertEquals(false, host.music.last())
    }

    @Test fun glassesHandOffFreezesThePhone() {
        val g = playing()
        g.debug.launch()
        g.glassesActive = true
        val x = g.balls[0].x
        val y = g.balls[0].y
        step(g, 1f)
        assertEquals(x, g.balls[0].x, 0f)
        assertEquals(y, g.balls[0].y, 0f)
    }
}

// ── Title demo ───────────────────────────────────────────────────────────────

class TitleDemoTest {
    @Test fun demoBallStaysInItsBand() {
        val g = newGame()
        repeat(60 * 20) {
            g.update(DT)
            assertTrue(abs(g.demoBallX) + World.BALL_R <= g.fieldHalfWidth + 1e-4f)
            assertTrue(g.demoBallY >= Game.DEMO_CEILING_Y - 1e-4f)
            assertTrue(g.demoBallY < World.PADDLE_Y)
        }
    }

    @Test fun demoPaddleChasesTheBall() {
        val g = newGame()
        step(g, 3f)
        assertTrue(abs(g.demoPaddleX - g.demoBallX) < 0.15f)
    }
}

// ── Keys and the D-pad ───────────────────────────────────────────────────────

class SteerTest {
    @Test fun heldKeyMovesThePaddleAtItsUsualSpeed() {
        val g = playing()
        g.setSteer(1)
        step(g, 0.1f)
        assertEquals(World.paddleSpeed(PHONE) * 0.1f, g.paddleX, 0.02f)
        g.setSteer(-1)
        step(g, 0.3f)
        assertTrue(g.paddleX < 0f)
    }

    @Test fun releasedKeyStopsThePaddle() {
        val g = playing()
        g.setSteer(1)
        step(g, 0.2f)
        g.setSteer(0)
        val x = g.paddleX
        step(g, 0.5f)
        assertEquals(x, g.paddleX, 1e-6f)
        assertFalse(g.moveHeld)
    }

    @Test fun keyStopsAtTheLaneEdge() {
        val g = playing()
        g.setSteer(1)
        step(g, 3f)
        assertEquals(g.fieldHalfWidth - World.PADDLE_HW, g.paddleX, 1e-5f)
    }

    @Test fun holdingAKeyLaunchesTheHeldBallToo() {
        val g = playing()
        g.setSteer(-1)
        step(g, Game.SERVE_DELAY + 0.1f)
        assertFalse(g.ballHeld)
    }

    @Test fun cancelReleasesTheKey() {
        val g = playing()
        g.setSteer(1)
        g.onPointersCancel()
        step(g, 0.5f)
        assertEquals(0f, g.paddleX, 1e-6f)
    }

    @Test fun fingerWinsOverTheKey() {
        val g = playing()
        g.setSteer(1)
        g.onPointerDown(0, -0.30f, STRIP_Y)
        step(g, 1f)
        // Both steer; the key says the right edge, the finger says -0.30. The
        // key is the active one while held: a TV has no finger at all.
        assertTrue(g.paddleX > 0f)
    }
}

// ── Pause ────────────────────────────────────────────────────────────────────

class PauseTest {
    @Test fun pauseFreezesABallInFlight() {
        val g = playing()
        g.debug.launch()
        step(g, 0.2f)
        g.pause()
        assertTrue(g.paused)
        val x = g.balls[0].x; val y = g.balls[0].y
        step(g, 1f)
        assertEquals(x, g.balls[0].x, 0f)
        assertEquals(y, g.balls[0].y, 0f)
    }

    @Test fun tapResumes() {
        val g = playing()
        g.debug.launch()
        step(g, 0.2f)
        g.pause()
        g.resume()                       // a human: still waiting for the tap
        assertTrue(g.paused)
        val y = g.balls[0].y
        tap(g)
        assertFalse(g.paused)
        step(g, 0.2f)
        assertNotEquals(y, g.balls[0].y)
    }

    @Test fun autopilotResumesByItself() {
        val g = playing()
        g.autoPlayActive = true
        step(g, 1f)                      // launched by the autopilot
        g.pause()
        assertTrue(g.paused)
        g.resume()
        assertFalse(g.paused)
    }

    @Test fun aHeldKeyDoesNotResumeButAFingerDoes() {
        val g = playing()
        g.debug.launch()
        step(g, 0.2f)
        g.pause()
        g.setSteer(1)
        step(g, 0.5f)
        assertTrue(g.paused)
        assertEquals(0f, g.paddleX, 1e-6f)
        g.setSteer(0)
        g.onPointerDown(0, 0.3f, STRIP_Y)   // a finger on the strip is a tap
        step(g, 0.2f)
        assertFalse(g.paused)
    }

    @Test fun nothingToPauseOutsideAFlight() {
        val title = newGame()
        title.pause()
        assertFalse(title.paused)
        val held = playing()             // the serve waits anyway
        held.pause()
        assertFalse(held.paused)
        val over = playing()
        over.debug.setLives(1)
        loseABall(over)
        over.pause()
        assertFalse(over.paused)
    }

    @Test fun pausedClockStillBreathes() {
        val g = playing()
        g.debug.launch()
        g.pause()
        val t = g.animTime
        step(g, 0.5f)
        assertTrue(g.animTime > t)
    }

    @Test fun aLostRoundClearsThePause() {
        val g = playing()
        g.debug.setLives(1)
        g.debug.setBall(0.30f, 0.68f, 0f, 1f)
        g.pause()
        tap(g)                           // resume: the ball drops into the pit
        step(g, 0.5f)
        assertEquals(Game.State.GAME_OVER, g.state)
        assertFalse(g.paused)
    }
}

// ── The fixed timestep ───────────────────────────────────────────────────────

class TimestepTest {
    /** The same seconds of play, delivered as 60, 90 or 120 frames a second,
     *  put the ball in the very same place. */
    @Test fun refreshRateDoesNotChangeTheGame() {
        fun play(frameDt: Float): Pair<Float, Float> {
            val g = playing()
            g.debug.setBall(0.1f, 0.2f, 0.35f, -1f)
            var t = 0f
            while (t < 4f - 1e-4f) { g.update(frameDt); t += frameDt }
            return g.balls[0].x to g.balls[0].y
        }
        val at60 = play(1f / 60f)
        val at90 = play(1f / 90f)
        val at120 = play(1f / 120f)
        assertEquals(at60.first, at90.first, 1e-4f)
        assertEquals(at60.second, at90.second, 1e-4f)
        assertEquals(at60.first, at120.first, 1e-4f)
        assertEquals(at60.second, at120.second, 1e-4f)
    }

    @Test fun theRemainderOfAFrameCarriesOver() {
        val g = newGame()
        repeat(3) { g.update(1f / 90f) }          // 1, 1 and 2 ticks: four in all
        assertEquals(4 * Game.TICK, g.animTime, 1e-5f)
        g.update(Game.TICK * 0.4f)                 // not a whole tick yet
        assertEquals(4 * Game.TICK, g.animTime, 1e-5f)
        g.update(Game.TICK * 0.7f)                 // now it is
        assertEquals(5 * Game.TICK, g.animTime, 1e-5f)
    }

    @Test fun aHitchIsClampedNotSkipped() {
        val g = newGame()
        g.update(5f)
        assertEquals(Game.MAX_FRAME, g.animTime, 1e-5f)
        g.update(-1f)
        assertEquals(Game.MAX_FRAME, g.animTime, 1e-5f)
    }
}

class LevelSelectTest {
    @Test fun aFreshDeviceOffersOnlyLevelOne() {
        val g = newGame()
        assertEquals(1, g.furthestLevel)
        g.selectLevel(1)
        assertEquals(1, g.startLevel)
        g.selectLevel(-1)
        assertEquals(1, g.startLevel)
    }

    @Test fun thePickStaysBetweenOneAndTheFurthestLevel() {
        val g = newGame()
        g.furthestLevel = 4
        g.selectLevel(2); assertEquals(3, g.startLevel)
        g.selectLevel(5); assertEquals(4, g.startLevel)
        g.selectLevel(-9); assertEquals(1, g.startLevel)
    }

    @Test fun aRoundStartsAtThePickedLevelFromScratch() {
        val g = newGame()
        g.furthestLevel = 6
        g.selectLevel(4)
        g.tap()
        g.update(DT)
        assertEquals(Game.State.PLAYING, g.state)
        assertEquals(5, g.level)
        assertEquals(0L, g.score)
        assertEquals(5, g.lives)
        assertEquals(Levels.rows(5) * g.wallColumns, g.bricks.size)
    }

    @Test fun thePickIsFrozenOnceARoundIsUnderWay() {
        val g = newGame()
        g.furthestLevel = 3
        g.tap(); g.update(DT)
        g.selectLevel(2)
        assertEquals(1, g.startLevel)
    }

    @Test fun reachingANewLevelRaisesTheRecordAndTellsTheHost() {
        val host = RecordingHost()
        val g = newGame(host = host)
        g.debug.startGame()
        assertEquals(1, g.furthestLevel)
        assertTrue(host.furthest.isEmpty())              // level 1 is no news
        g.debug.startLevel(3)
        assertEquals(3, g.furthestLevel)
        assertEquals(listOf(3), host.furthest)
        g.debug.startLevel(2)                            // going back changes nothing
        assertEquals(3, g.furthestLevel)
        assertEquals(listOf(3), host.furthest)
    }

    @Test fun clearingALevelInPlayCountsAsReachingTheNext() {
        val host = RecordingHost()
        val g = newGame(host = host)
        g.debug.startGame()
        g.debug.clearWall()
        g.debug.setBall(0f, 0.3f, 0f, -1f)
        step(g, 5f)                                      // the clear, the interlude, level 2
        assertEquals(2, g.level)
        assertEquals(2, g.furthestLevel)
        assertEquals(listOf(2), host.furthest)
    }

    @Test fun aSessionSavedInTheInterludeResumesAtTheNextLevel() {
        val g = newGame()
        g.debug.startGame()
        g.debug.clearWall()
        g.debug.setBall(0f, 0.3f, 0f, -1f)
        var frames = 0
        while (g.state != Game.State.LEVEL_CLEAR && frames++ < 600) g.update(DT)
        assertEquals(Game.State.LEVEL_CLEAR, g.state)
        assertTrue(g.isMidGame)                          // worth saving...
        assertEquals(1, g.level)                         // ...but not as the wall just cleared
        assertEquals(2, g.resumeLevel)
        step(g, 5f)                                      // into level 2
        assertEquals(Game.State.PLAYING, g.state)
        assertEquals(2, g.resumeLevel)
    }

    @Test fun aRestoredRecordNeverGoesDown() {
        val g = newGame()
        g.debug.startLevel(5)
        g.furthestLevel = 3                              // the host read an older value
        assertEquals(3, g.furthestLevel)                 // the setter trusts the host...
        g.furthestLevel = maxOf(g.furthestLevel, 5)      // ...so the host keeps the larger one
        assertEquals(5, g.furthestLevel)
    }

    @Test fun loweringTheRecordPullsThePickDownWithIt() {
        val g = newGame()
        g.furthestLevel = 8
        g.selectLevel(7)
        assertEquals(8, g.startLevel)
        g.furthestLevel = 2
        assertEquals(2, g.startLevel)
    }

    @Test fun thePickSurvivesAGameOver() {
        val g = newGame()
        g.furthestLevel = 4
        g.selectLevel(2)
        g.tap(); g.update(DT)
        g.debug.setLives(1)
        g.debug.setBall(0f, 0.8f, 0f, 1f)
        step(g, 2f)
        assertEquals(Game.State.GAME_OVER, g.state)
        step(g, 3f)
        g.tap(); g.update(DT)
        assertEquals(Game.State.TITLE, g.state)
        assertEquals(3, g.startLevel)
    }
}
