package com.jpcottin.composebreakout

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.jpcottin.composebreakout.engine.Game
import com.jpcottin.composebreakout.theme.BreakoutTheme
import com.jpcottin.composebreakout.ui.GameScreen
import com.jpcottin.composebreakout.ui.Settings
import com.jpcottin.composebreakout.ui.SettingsContent

/** A game on its title screen, one second in, so the demo ball is mid-flight. */
private fun titleGame(aspect: Float): Game = Game().apply {
    setAspect(aspect)
    repeat(60) { update(1f / 60f) }
}

@PreviewTest
@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Composable
fun TitlePhoneScreenshot() {
    BreakoutTheme {
        GameScreen(game = titleGame(411f / 891f), settings = Settings(), onOpenSettings = {}, paused = true)
    }
}

@PreviewTest
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Composable
fun TitleFoldableScreenshot() {
    BreakoutTheme {
        GameScreen(game = titleGame(673f / 841f), settings = Settings(), onOpenSettings = {}, paused = true)
    }
}

@PreviewTest
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Composable
fun TitleTabletScreenshot() {
    BreakoutTheme {
        GameScreen(game = titleGame(1280f / 800f), settings = Settings(), onOpenSettings = {}, paused = true)
    }
}

@PreviewTest
@Preview(name = "TV", device = Devices.TV_1080p, showBackground = true, uiMode = Configuration.UI_MODE_TYPE_TELEVISION)
@Composable
fun TitleTvScreenshot() {
    BreakoutTheme {
        GameScreen(game = titleGame(1920f / 1080f), settings = Settings(), onOpenSettings = {}, paused = true)
    }
}

@PreviewTest
@Preview(name = "Settings", widthDp = 400, heightDp = 360, showBackground = true)
@Composable
fun SettingsScreenshot() {
    BreakoutTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            SettingsContent(
                settings = Settings(soundEnabled = true, autoPlay = true),
                onSoundChange = {},
                onAutoPlayChange = {},
                onDone = {},
            )
        }
    }
}
