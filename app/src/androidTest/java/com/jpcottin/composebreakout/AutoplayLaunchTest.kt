package com.jpcottin.composebreakout

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jpcottin.composebreakout.engine.Game
import com.jpcottin.composebreakout.ui.GameViewModel
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The unattended launch the CI multi-runs and the launcher shortcut use:
 *
 *   adb shell am start -n com.jpcottin.composebreakout/.MainActivity --ez autoplay true
 *
 * must start a round with the autopilot on, whatever the stored Auto Play
 * switch says, and the HUD must show the AUTO badge.
 */
@RunWith(AndroidJUnit4::class)
class AutoplayLaunchTest {

    @get:Rule val composeTestRule = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun wipeTheStore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "datastore").deleteRecursively()
        composeTestRule.mainClock.autoAdvance = false
    }

    @After
    fun closeTheActivity() {
        if (!::scenario.isInitialized) return
        // The unattended launch turns the stored Auto Play switch on, and
        // DataStore keeps that in memory for the life of the process: left
        // on, the autopilot would resume the rounds the other tests pause.
        scenario.onActivity { ViewModelProvider(it)[GameViewModel::class.java].setAutoPlay(false) }
        Thread.sleep(300)                                       // let the write land
        scenario.close()
    }

    private fun frames(count: Int) = composeTestRule.mainClock.advanceTimeBy(16L * count)

    private fun game(): Game {
        lateinit var game: Game
        scenario.onActivity { game = ViewModelProvider(it)[GameViewModel::class.java].game }
        return game
    }

    @Test
    fun theAutoplayExtraStartsAnUnattendedRound() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).putExtra(GameViewModel.EXTRA_AUTOPLAY, true)
        scenario = ActivityScenario.launch<MainActivity>(intent)
        val game = game()
        // The extra is honoured once the stored settings have loaded, which
        // happens off the frame clock; give it a moment and a few frames.
        for (attempt in 1..40) {
            frames(5)
            if (game.state == Game.State.PLAYING) break
            Thread.sleep(50)
        }
        assertEquals(Game.State.PLAYING, game.state)
        assertTrue(game.autoPlayActive)
        // The badge follows the stored switch, which comes back through
        // DataStore a moment after the engine has already turned, and on a
        // software renderer its first frames can still be laying out: wait
        // for it to be displayed, not merely present.
        for (attempt in 1..60) {
            frames(5)
            if (runCatching { composeTestRule.onNodeWithText("AUTO").assertIsDisplayed() }.isSuccess) break
            Thread.sleep(50)
        }
        composeTestRule.onNodeWithText("AUTO").assertIsDisplayed()
        // And it keeps going: the autopilot launches the held ball itself.
        for (attempt in 1..40) {
            frames(10)
            if (!game.ballHeld) break
        }
        assertTrue("the autopilot should have launched the ball", !game.ballHeld)
    }

    @Test
    fun aPlainLaunchStaysOnTheTitle() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val game = game()
        frames(30)
        Thread.sleep(200)
        frames(30)
        assertEquals(Game.State.TITLE, game.state)
    }
}
