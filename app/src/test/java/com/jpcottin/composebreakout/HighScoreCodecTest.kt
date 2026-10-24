package com.jpcottin.composebreakout

import com.jpcottin.composebreakout.engine.Game
import com.jpcottin.composebreakout.engine.HighScore
import com.jpcottin.composebreakout.ui.GameViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class HighScoreCodecTest {
    @Test
    fun roundTrips() {
        val table = listOf(HighScore(500, 3), HighScore(300, 1))
        assertEquals(table, GameViewModel.decodeScores(GameViewModel.encodeScores(table)))
    }

    @Test
    fun ignoresGarbageAndEmpty() {
        assertEquals(emptyList<HighScore>(), GameViewModel.decodeScores(null))
        assertEquals(emptyList<HighScore>(), GameViewModel.decodeScores(""))
        assertEquals(listOf(HighScore(10, 1)), GameViewModel.decodeScores("x:1,10:1,0:2,5"))
    }

    @Test
    fun dropsNegativeScoresAndTrailingSeparators() {
        assertEquals(listOf(HighScore(10, 1)), GameViewModel.decodeScores("-5:1,10:1,"))
        assertEquals(emptyList<HighScore>(), GameViewModel.decodeScores(",,"))
    }

    @Test
    fun anOutOfRangeLevelIsForgotten() {
        assertEquals(listOf(HighScore(300, 4)), GameViewModel.decodeScores("100:0,200:99,300:4,400:-1"))
    }

    @Test
    fun keepsTheLevelWithEachScore() {
        assertEquals(listOf(HighScore(10, 7), HighScore(10, 2)), GameViewModel.decodeScores("10:7,10:2"))
    }
}

/** The store is shared by the phone and the glasses instances: a table
 *  reported by one is folded into what the other already saved. */
class HighScoreMergeTest {
    private fun merge(stored: String?, vararg table: HighScore): List<HighScore> =
        GameViewModel.decodeScores(GameViewModel.mergeScores(stored, table.toList()))

    @Test
    fun anEmptyStoreTakesTheTableAsIs() {
        assertEquals(listOf(HighScore(300, 2), HighScore(100, 1)), merge(null, HighScore(300, 2), HighScore(100, 1)))
    }

    @Test
    fun foldsInTheOtherInstancesRowsBestFirst() {
        assertEquals(
            listOf(HighScore(500, 3), HighScore(400, 2), HighScore(300, 1)),
            merge("500:3,300:1", HighScore(400, 2)),
        )
    }

    @Test
    fun anIdenticalRowIsStoredOnce() {
        assertEquals(listOf(HighScore(500, 3), HighScore(200, 1)), merge("500:3", HighScore(500, 3), HighScore(200, 1)))
    }

    @Test
    fun theSameScoreOnAnotherLevelIsADifferentRow() {
        // Ties keep the stored row ahead of the new one.
        assertEquals(listOf(HighScore(500, 3), HighScore(500, 4)), merge("500:3", HighScore(500, 4)))
    }

    @Test
    fun capsAtTheTableSizeDroppingTheLowest() {
        val merged = merge("900:5,800:4,700:3,600:2,500:1", HighScore(650, 2))
        assertEquals(Game.MAX_SCORES, merged.size)
        assertEquals(listOf(900L, 800L, 700L, 650L, 600L), merged.map { it.score })
    }

    @Test
    fun garbageInTheStoreIsForgotten() {
        assertEquals(listOf(HighScore(100, 1)), merge("junk,0:9", HighScore(100, 1)))
    }
}
