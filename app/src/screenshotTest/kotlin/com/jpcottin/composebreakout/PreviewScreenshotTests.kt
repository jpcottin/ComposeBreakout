package com.jpcottin.composebreakout

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.jpcottin.composebreakout.engine.Game
import com.jpcottin.composebreakout.engine.HighScore
import com.jpcottin.composebreakout.theme.BreakoutTheme
import com.jpcottin.composebreakout.ui.GameScreen
import com.jpcottin.composebreakout.ui.Settings
import com.jpcottin.composebreakout.ui.SettingsContent

/*
 * Every screen of the game as a Compose preview, rendered by the screenshot
 * tests and compared with the committed references. The engine is seeded
 * and stepped deterministically, so each preview is the same picture every
 * time. The frame loop is paused: the engine is stepped here, not by vsync.
 */

private const val PHONE = 411f / 891f
private const val FOLDABLE = 673f / 841f
private const val TABLET = 1280f / 800f
private const val TV = 1920f / 1080f

private fun step(g: Game, seconds: Float) { repeat((seconds * 60f).toInt()) { g.update(1f / 60f) } }

/** The title screen, one second in, with a podium. */
private fun titleGame(aspect: Float): Game = Game().apply {
    setAspect(aspect)
    restoreHighScores(listOf(HighScore(2450, 4), HighScore(1310, 3), HighScore(620, 2)))
    step(this, 1f)
}

/** Level 3, a few bricks down, a capsule falling, the wide paddle on. */
private fun playingGame(aspect: Float): Game = Game().apply {
    setAspect(aspect)
    restoreSession(3, 480, 4)
    debug.setBall(0.10f, 0.20f, 0.45f, -1f)
    repeat(4) { debug.killBrick(2 * wallColumns + 2 + it) }
    debug.activatePowerUp(Game.PowerUpType.WIDE)
    debug.spawnPowerUp(-0.20f, 0.30f, Game.PowerUpType.MULTI)
    step(this, 0.3f)
}

/** A served ball waiting for the tap. */
private fun servedGame(aspect: Float): Game = Game().apply {
    setAspect(aspect)
    restoreSession(1, 0, 5)
    step(this, 1f)
}

private fun levelClearGame(aspect: Float): Game = Game().apply {
    setAspect(aspect)
    restoreSession(2, 730, 5)
    debug.clearWall()
    step(this, 0.5f)
}

private fun gameOverGame(aspect: Float): Game = Game().apply {
    setAspect(aspect)
    restoreHighScores(listOf(HighScore(2450, 4), HighScore(620, 2)))
    restoreSession(5, 1980, 1)
    debug.setBall(0.30f, 0.68f, 0f, 1f)
    step(this, 1.5f)
}

private fun winGame(aspect: Float): Game = Game().apply {
    setAspect(aspect)
    restoreSession(10, 9640, 3)
    debug.clearWall()
    step(this, 1f)
}

@Composable
private fun Screen(game: Game, autoPlay: Boolean = false) {
    BreakoutTheme {
        GameScreen(game = game, settings = Settings(autoPlay = autoPlay), onOpenSettings = {}, paused = true)
    }
}

// ── Title ────────────────────────────────────────────────────────────────────

@PreviewTest
@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Composable
fun TitlePhoneScreenshot() = Screen(titleGame(PHONE))

@PreviewTest
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Composable
fun TitleFoldableScreenshot() = Screen(titleGame(FOLDABLE))

@PreviewTest
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Composable
fun TitleTabletScreenshot() = Screen(titleGame(TABLET))

@PreviewTest
@Preview(name = "TV", device = Devices.TV_1080p, showBackground = true, uiMode = Configuration.UI_MODE_TYPE_TELEVISION)
@Composable
fun TitleTvScreenshot() = Screen(titleGame(TV))

// ── In play ──────────────────────────────────────────────────────────────────

@PreviewTest
@Preview(name = "Playing phone", device = Devices.PHONE, showBackground = true)
@Composable
fun PlayingPhoneScreenshot() = Screen(playingGame(PHONE), autoPlay = true)

@PreviewTest
@Preview(name = "Served phone", device = Devices.PHONE, showBackground = true)
@Composable
fun ServedPhoneScreenshot() = Screen(servedGame(PHONE))

@PreviewTest
@Preview(name = "Playing foldable", device = Devices.FOLDABLE, showBackground = true)
@Composable
fun PlayingFoldableScreenshot() = Screen(playingGame(FOLDABLE))

@PreviewTest
@Preview(name = "Playing TV", device = Devices.TV_1080p, showBackground = true, uiMode = Configuration.UI_MODE_TYPE_TELEVISION)
@Composable
fun PlayingTvScreenshot() = Screen(playingGame(TV))

@PreviewTest
@Preview(name = "Level clear", device = Devices.PHONE, showBackground = true)
@Composable
fun LevelClearScreenshot() = Screen(levelClearGame(PHONE))

// ── End screens ──────────────────────────────────────────────────────────────

@PreviewTest
@Preview(name = "Game over", device = Devices.PHONE, showBackground = true)
@Composable
fun GameOverScreenshot() = Screen(gameOverGame(PHONE))

@PreviewTest
@Preview(name = "Win tablet", device = Devices.TABLET, showBackground = true)
@Composable
fun WinTabletScreenshot() = Screen(winGame(TABLET))

// ── Settings ─────────────────────────────────────────────────────────────────

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
