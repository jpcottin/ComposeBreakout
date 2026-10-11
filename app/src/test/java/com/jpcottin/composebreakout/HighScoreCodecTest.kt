package com.jpcottin.composebreakout

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
}
