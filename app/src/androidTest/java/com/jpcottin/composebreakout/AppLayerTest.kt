package com.jpcottin.composebreakout

import androidx.compose.foundation.layout.Box
import java.util.Locale
import com.jpcottin.composebreakout.ui.GameScreen
import com.jpcottin.composebreakout.engine.Game
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import android.os.LocaleList
import android.content.res.Configuration
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

    @Test
    fun aFrenchScreenReaderHearsFrenchLabels() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(Locale.FRENCH)) }
        val french = base.createConfigurationContext(config)
        val game = Game().apply { setAspect(411f / 891f) }
        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides french, LocalConfiguration provides config, LocalResources provides french.resources) {
                BreakoutTheme { GameScreen(game, Settings(), onOpenSettings = {}, paused = true) }
            }
        }
        composeTestRule.onNodeWithContentDescription(french.getString(R.string.a11y_playfield)).assertExists()
        composeTestRule.onNodeWithContentDescription("Playfield").assertDoesNotExist()
    }

}
