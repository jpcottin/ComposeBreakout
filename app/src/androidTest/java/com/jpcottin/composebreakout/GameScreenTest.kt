package com.jpcottin.composebreakout

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun pauseTheClock() {
        composeTestRule.mainClock.autoAdvance = false
    }

    private fun frames(count: Int) = composeTestRule.mainClock.advanceTimeBy(16L * count)

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
    }
}
