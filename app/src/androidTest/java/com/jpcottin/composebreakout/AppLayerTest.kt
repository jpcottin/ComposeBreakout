package com.jpcottin.composebreakout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jpcottin.composebreakout.theme.BreakoutTheme
import com.jpcottin.composebreakout.ui.Settings
import com.jpcottin.composebreakout.ui.SettingsContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The settings sheet's semantics and layout. */
@RunWith(AndroidJUnit4::class)
class AppLayerTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun aScreenReaderHearsWhetherEachSwitchIsOn() {
        composeTestRule.setContent {
            BreakoutTheme { SettingsContent(Settings(soundEnabled = true, autoPlay = false), {}, {}, onDone = {}) }
        }
        val sound = composeTestRule.onNode(hasText("Sound") and isToggleable())
        sound.assertIsOn()
        composeTestRule.onNode(hasText("Auto Play") and isToggleable()).assertIsOff()
    }

    @Test
    fun doneIsReachableOnAShortWindow() {
        composeTestRule.setContent {
            BreakoutTheme { Box(Modifier.height(320.dp)) { SettingsContent(Settings(), {}, {}, onDone = {}) } }
        }
        composeTestRule.onNodeWithText("Done").performScrollTo().assertIsDisplayed()
    }
}
