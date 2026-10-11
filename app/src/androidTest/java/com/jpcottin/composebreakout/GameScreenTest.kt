package com.jpcottin.composebreakout

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launches the real activity: the title must be up, the settings sheet must
 * open from the gear and show its rows.
 */
@RunWith(AndroidJUnit4::class)
class GameScreenTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun titleIsShownOnLaunch() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("BREAKOUT").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Playfield").assertIsDisplayed()
    }

    @Test
    fun gearOpensSettings() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Sound").assertIsDisplayed()
        composeTestRule.onNodeWithText("Auto Play").assertIsDisplayed()
    }
}
