package com.jpcottin.composebreakout.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Properties every level must keep, whatever its layout: the rules a
 * designer could break by editing one case in [Levels.brickAt].
 */
class LevelsTest {
    private val lanes = listOf(1080f / 2400f, 2152f / 2076f, 2400f / 1080f)   // 7, 10 and 10 columns

    private fun wall(level: Int, aspect: Float): Game = Game().apply {
        setAspect(aspect)
        debug.startLevel(level)
    }

    @Test fun everyLevelCanBeCleared() {
        for (aspect in lanes) for (level in 1..Levels.COUNT) {
            val g = wall(level, aspect)
            assertTrue("level $level has nothing to break", g.breakableLeft > 0)
        }
    }

    @Test fun steelNeverBlocksTheWholeWidthOfARow() {
        // A full steel row would wall off everything above it for ever.
        for (aspect in lanes) for (level in 1..Levels.COUNT) {
            val g = wall(level, aspect)
            for (row in 0 until g.rows) {
                val steel = g.bricks.count { it.row == row && it.alive && it.type == Game.BrickType.STEEL }
                assertTrue("level $level row $row is all steel", steel < g.wallColumns)
            }
        }
    }

    @Test fun holesOnlyOnTheBottomRow() {
        for (aspect in lanes) for (level in 1..Levels.COUNT) {
            val g = wall(level, aspect)
            for (b in g.bricks) if (!b.alive) assertEquals("level $level hole off the bottom row", g.rows - 1, b.row)
        }
    }

    @Test fun difficultyNeverDrops() {
        // Rows, ball speed and the hits a wall takes to clear never go down
        // from one level to the next.
        var rows = 0
        var speed = 0f
        var hits = 0
        for (level in 1..Levels.COUNT) {
            val g = wall(level, lanes[0])
            assertTrue("level $level has fewer rows", Levels.rows(level) >= rows)
            assertTrue("level $level is slower", Levels.ballSpeed(level) > speed)
            val toClear = g.bricks.filter { it.alive && it.type != Game.BrickType.STEEL }.sumOf { it.type.hits }
            assertTrue("level $level clears in fewer hits ($toClear < $hits)", toClear > hits)
            rows = Levels.rows(level); speed = Levels.ballSpeed(level); hits = toClear
        }
    }

    @Test fun theFirstLevelsAreGentle() {
        for (level in 1..2) {
            val g = wall(level, lanes[0])
            assertTrue(g.bricks.all { it.alive && it.type == Game.BrickType.NORMAL })
        }
    }

    @Test fun theLastLevelUsesEveryKind() {
        val kinds = wall(Levels.COUNT, lanes[0]).bricks.filter { it.alive }.map { it.type }.toSet()
        assertEquals(Game.BrickType.entries.toSet(), kinds)
    }

    @Test fun pointsPerKindRewardTheHits() {
        val byHits = Game.BrickType.entries.filter { it != Game.BrickType.STEEL }.sortedBy { it.hits }
        for (i in 1 until byHits.size) assertTrue(byHits[i].points > byHits[i - 1].points)
        assertEquals(0, Game.BrickType.STEEL.points)
    }

    @Test fun levelNumbersClamp() {
        assertEquals(1, Levels.clamp(0))
        assertEquals(1, Levels.clamp(-7))
        assertEquals(Levels.COUNT, Levels.clamp(99))
        assertEquals(Levels.rows(1), Levels.rows(0))
        assertEquals(Levels.rows(Levels.COUNT), Levels.rows(50))
    }
}
