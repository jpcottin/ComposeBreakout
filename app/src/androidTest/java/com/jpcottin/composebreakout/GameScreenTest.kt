package com.jpcottin.composebreakout

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.ViewModelProvider
import com.jpcottin.composebreakout.engine.Game
import com.jpcottin.composebreakout.ui.GameViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launches the real activity: the title must be up, a tap must start a
 * round with the HUD, and the settings sheet must open from the gear.
 *
 * The game steps once per frame for as long as it is on screen, which
 * Compose's test harness would otherwise read as "never idle". The test
 * clock is paused and advanced by hand, so every frame is explicit.
 */
@RunWith(AndroidJUnit4::class)
class GameScreenTest {

    @get:Rule val composeTestRule = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launchFromACleanStore() {
        // Settings and scores persist in DataStore: a switch left on by an
        // earlier run (Auto Play steering the paddle) must not reach a test.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "datastore").deleteRecursively()
        composeTestRule.mainClock.autoAdvance = false
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun closeTheActivity() {
        scenario.close()
    }

    private fun frames(count: Int) = composeTestRule.mainClock.advanceTimeBy(16L * count)

    /** Focus the playfield and prove it has focus before a key goes in:
     *  a key sent to an unfocused node is silently lost, and the failure
     *  that follows would point at the game instead of at the focus. */
    private fun focusThePlayfield(): SemanticsNodeInteraction {
        val playfield = composeTestRule.onNodeWithContentDescription("Playfield")
        for (attempt in 1..5) {
            playfield.performSemanticsAction(SemanticsActions.RequestFocus)
            frames(2)
            if (runCatching { playfield.assertIsFocused() }.isSuccess) break
        }
        playfield.assertIsFocused()
        return playfield
    }

    /** Step frames until [condition] holds, up to [maxFrames]. */
    private fun stepUntil(maxFrames: Int, condition: () -> Boolean) {
        var left = maxFrames
        while (left > 0 && !condition()) { frames(1); left-- }
    }

    @Test
    fun titleIsShownOnLaunch() {
        frames(5)
        composeTestRule.onNodeWithText("BREAKOUT").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Playfield").assertIsDisplayed()
    }

    @Test
    fun tapStartsARoundWithTheHud() {
        frames(5)
        composeTestRule.onNodeWithContentDescription("Playfield").performClick()
        frames(5)
        composeTestRule.onNode(hasContentDescription("HUD")).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Score 0").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Lives 5").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Level 1").assertIsDisplayed()
    }

    @Test
    fun gearOpensSettings() {
        frames(5)
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        frames(2)
        // The game pauses under the sheet, so the composition settles on its
        // own from here: let the clock run and the sheet slide in.
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Sound").assertIsDisplayed()
        composeTestRule.onNodeWithText("Auto Play").assertIsDisplayed()
        composeTestRule.onNodeWithText("Glasses").assertIsDisplayed()
        composeTestRule.onNodeWithText("No AI Glasses paired").assertIsDisplayed()   // none on an emulator
    }

    /** The engine behind the screen, from the activity's ViewModel store. */
    private fun game(): Game {
        lateinit var game: Game
        scenario.onActivity { game = ViewModelProvider(it)[GameViewModel::class.java].game }
        return game
    }

    @Test
    fun dPadStartsTheRoundAndSteers() {
        frames(5)
        val playfield = focusThePlayfield()
        playfield.performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }   // start the round
        val game = game()
        stepUntil(10) { game.state == Game.State.PLAYING }
        assertEquals(Game.State.PLAYING, game.state)
        playfield.performKeyInput { keyDown(Key.DirectionRight) }
        frames(20)
        playfield.performKeyInput { keyUp(Key.DirectionRight) }
        frames(2)
        assertTrue("paddle at ${game.paddleX}", game.paddleX > 0.05f)
        val held = game.paddleX
        frames(20)
        assertEquals(held, game.paddleX, 1e-6f)                      // released: it stays
    }

    /** A round in flight: the HUD up, the ball launched. */
    private fun launchARound(): Game {
        frames(5)
        val playfield = composeTestRule.onNodeWithContentDescription("Playfield")
        playfield.performClick()                                      // start the round
        frames(40)                                                    // past the serve delay
        playfield.performClick()                                      // launch
        frames(10)
        val game = game()
        assertEquals(Game.State.PLAYING, game.state)
        assertTrue(game.balls.isNotEmpty() && !game.ballHeld)
        return game
    }

    @Test
    fun leavingTheScreenPausesTheRoundAndATapResumesIt() {
        // A free-form window (an XR panel, a desktop window) stays resumed
        // behind the scenario's cover activity: nothing to pause it with.
        var multiWindow = false
        scenario.onActivity { multiWindow = it.isInMultiWindowMode }
        assumeFalse("free-form windows stay resumed", multiWindow)
        val game = launchARound()
        scenario.moveToState(Lifecycle.State.STARTED)                 // the screen goes away
        frames(2)
        assertTrue(game.paused)
        scenario.moveToState(Lifecycle.State.RESUMED)                 // and comes back
        frames(5)
        assertTrue(game.paused)                                       // still waiting for the player
        composeTestRule.onNodeWithContentDescription("Paused").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Playfield").performClick()
        frames(5)
        assertFalse(game.paused)
    }

    @Test
    fun theGearPausesARoundInFlight() {
        val game = launchARound()
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        frames(2)
        assertTrue(game.paused)
    }

    @Test
    fun escapeFreezesTheRoundAndFreesItAgain() {
        val game = launchARound()
        val playfield = focusThePlayfield()
        playfield.performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        stepUntil(10) { game.paused }
        assertTrue("Escape should have paused the round", game.paused)
        frames(2)                                                    // the overlay follows the flag by a frame
        composeTestRule.onNodeWithContentDescription("Paused").assertIsDisplayed()
        playfield.performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        stepUntil(10) { !game.paused }
        assertFalse("Escape again should have resumed it", game.paused)
        assertEquals(Game.State.PLAYING, game.state)
    }

    @Test
    fun backFreezesTheRoundInsteadOfLeaving() {
        val game = launchARound()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        frames(2)
        assertTrue(game.paused)
        assertEquals(Lifecycle.State.RESUMED, scenario.state)       // still here
        composeTestRule.onNodeWithContentDescription("Paused").assertIsDisplayed()
    }

    @Test
    fun theTitleOffersAnyLevelReachedBefore() {
        frames(5)
        composeTestRule.onNodeWithContentDescription("Next level").assertDoesNotExist()   // nothing reached yet
        val game = game()
        scenario.onActivity { game.furthestLevel = 3 }
        frames(2)
        composeTestRule.onNodeWithContentDescription("Start level 1").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Next level").performClick()
        frames(2)
        composeTestRule.onNodeWithContentDescription("Start level 2").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Playfield").performClick()         // start the round
        frames(5)
        assertEquals(Game.State.PLAYING, game.state)
        assertEquals(2, game.level)
        composeTestRule.onNodeWithContentDescription("Level 2").assertIsDisplayed()
    }

    @Test
    fun aScreenReaderHearsTheRoundStart() {
        frames(5)
        composeTestRule.onNodeWithContentDescription("Playfield").performClick()
        frames(5)
        val liveRegion = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        composeTestRule.onNode(liveRegion and hasContentDescription("Level 1 begins")).assertExists()
        val bricks = game().breakableLeft                            // 28 on a phone, 40 on a wide lane
        composeTestRule.onNodeWithContentDescription("Playfield")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Level 1, $bricks bricks left, 5 lives"))
    }
}
