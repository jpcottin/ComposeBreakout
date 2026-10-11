package com.jpcottin.composebreakout

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.jpcottin.composebreakout.engine.Game
import com.jpcottin.composebreakout.engine.HighScore
import com.jpcottin.composebreakout.theme.BreakoutTheme
import com.jpcottin.composebreakout.ui.GameScreen
import com.jpcottin.composebreakout.ui.GlassesState
import com.jpcottin.composebreakout.ui.LevelsGallery
import com.jpcottin.composebreakout.ui.Settings
import com.jpcottin.composebreakout.ui.SettingsCard
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

/** The title with a level pick: level 4 reached before, level 3 chosen. */
private fun titlePickGame(aspect: Float): Game = titleGame(aspect).apply {
    furthestLevel = 4
    selectLevel(2)
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
private fun Screen(game: Game, autoPlay: Boolean = false, tabletop: Boolean = false, glasses: Boolean = false) {
    BreakoutTheme {
        GameScreen(
            game = game, settings = Settings(autoPlay = autoPlay), onOpenSettings = {},
            paused = true, tabletop = tabletop, glasses = glasses,
        )
    }
}

/** The phone's game, frozen while the glasses play. */
private fun handedOffGame(aspect: Float): Game = playingGame(aspect).apply { glassesActive = true }

/** The glasses' own game: touchbar mode, a brick or two down. */
private fun glassesGame(aspect: Float): Game = Game().apply {
    setAspect(aspect)
    controlMode = Game.ControlMode.TOUCHBAR
    restoreSession(2, 310, 5)
    debug.setBall(-0.15f, 0.10f, 0.35f, -1f)
    debug.killBrick(wallColumns + 3)
    step(this, 0.3f)
}

// ── Title ────────────────────────────────────────────────────────────────────

@PreviewTest
@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Composable
fun TitlePhoneScreenshot() = Screen(titleGame(PHONE))

@PreviewTest
@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Composable
fun TitleLevelPickPhoneScreenshot() = Screen(titlePickGame(PHONE))

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

/** A foldable half-opened: the playfield above the hinge, the control surface below. */
@PreviewTest
@Preview(name = "Tabletop foldable", device = Devices.FOLDABLE, showBackground = true)
@Composable
fun PlayingTabletopScreenshot() = Screen(playingGame(673f / 420f), tabletop = true)

/** A round frozen mid-flight, waiting for the tap. */
private fun pausedGame(aspect: Float): Game = playingGame(aspect).apply { pause() }

@PreviewTest
@Preview(name = "Paused", device = Devices.PHONE, showBackground = true)
@Composable
fun PausedScreenshot() = Screen(pausedGame(PHONE))

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

// ── The ten walls ────────────────────────────────────────────────────────────

@PreviewTest
@Preview(name = "Levels", widthDp = 720, heightDp = 900, showBackground = true)
@Composable
fun LevelsGalleryScreenshot() {
    BreakoutTheme { LevelsGallery() }
}

// ── AI Glasses ───────────────────────────────────────────────────────────────

@PreviewTest
@Preview(name = "Glasses", widthDp = 640, heightDp = 360, showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun GlassesScreenshot() = Screen(glassesGame(640f / 360f), glasses = true)

@PreviewTest
@Preview(name = "Glasses title", widthDp = 640, heightDp = 360, showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun GlassesTitleScreenshot() = Screen(titleGame(640f / 360f).apply { controlMode = Game.ControlMode.TOUCHBAR }, glasses = true)

@PreviewTest
@Preview(name = "On glasses banner", device = Devices.PHONE, showBackground = true)
@Composable
fun OnGlassesScreenshot() = Screen(handedOffGame(PHONE))

@PreviewTest
@Preview(name = "Settings glasses paired", widthDp = 400, heightDp = 440, showBackground = true)
@Composable
fun SettingsGlassesPairedScreenshot() {
    BreakoutTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            SettingsContent(
                settings = Settings(), onSoundChange = {}, onAutoPlayChange = {}, onDone = {},
                glasses = GlassesState.CONNECTED,
            )
        }
    }
}

@PreviewTest
@Preview(name = "Settings glasses active", widthDp = 400, heightDp = 440, showBackground = true)
@Composable
fun SettingsGlassesActiveScreenshot() {
    BreakoutTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            SettingsContent(
                settings = Settings(), onSoundChange = {}, onAutoPlayChange = {}, onDone = {},
                glasses = GlassesState.ACTIVE,
            )
        }
    }
}

// ── Settings ─────────────────────────────────────────────────────────────────

@PreviewTest
@Preview(name = "Settings card", widthDp = 640, heightDp = 480, showBackground = true, backgroundColor = 0xFF080A17)
@Composable
fun SettingsCardScreenshot() {
    BreakoutTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SettingsCard(
                settings = Settings(soundEnabled = true, autoPlay = false),
                onSoundChange = {}, onAutoPlayChange = {}, onDone = {},
                glasses = GlassesState.CONNECTED,
            )
        }
    }
}

@PreviewTest
@Preview(name = "Settings", widthDp = 400, heightDp = 440, showBackground = true)
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
