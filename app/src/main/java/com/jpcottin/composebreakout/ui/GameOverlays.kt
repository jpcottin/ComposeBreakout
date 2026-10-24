package com.jpcottin.composebreakout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.jpcottin.composebreakout.R
import com.jpcottin.composebreakout.engine.Game
import com.jpcottin.composebreakout.theme.AutoGreen
import com.jpcottin.composebreakout.theme.OnSpaceMuted
import com.jpcottin.composebreakout.theme.PaddleCyan
import com.jpcottin.composebreakout.theme.PodiumColors
import com.jpcottin.composebreakout.theme.TitleGold
import kotlin.math.roundToInt
import kotlin.math.sin


/*
 * The chrome over the playfield: everything that is text or a tint rather
 * than the canvas. Each piece reads the frame counter (see [observe]) so it
 * follows the engine step by step, and places itself in world coordinates
 * (see [atWorld]) like the canvas does.
 */

/**
 * The round's events for a screen reader: a polite live region whose text
 * changes when a level starts or is cleared, a ball is lost, the game ends
 * or pauses. It draws nothing. The text only changes on an event, so a
 * reader says each thing once.
 */
@Composable
internal fun Announcer(game: Game, frame: LongState) {
    observe(frame)
    val levelStart = stringResource(R.string.a11y_level_start, game.level)
    val levelClear = stringResource(R.string.a11y_level_clear, game.level)
    val ballLost = stringResource(R.string.a11y_ball_lost, pluralStringResource(R.plurals.a11y_lives, game.lives, game.lives))
    val gameOver = stringResource(R.string.a11y_game_over, game.score)
    val win = stringResource(R.string.a11y_win, game.score)
    val paused = stringResource(R.string.a11y_paused)
    val events = remember { AnnouncerEvents() }
    val announcement = remember { mutableStateOf("") }
    events.update(game)?.let { kind ->
        announcement.value = when (kind) {
            AnnouncerEvents.Kind.LEVEL_START -> levelStart
            AnnouncerEvents.Kind.LEVEL_CLEAR -> levelClear
            AnnouncerEvents.Kind.BALL_LOST -> ballLost
            AnnouncerEvents.Kind.GAME_OVER -> gameOver
            AnnouncerEvents.Kind.WIN -> win
            AnnouncerEvents.Kind.PAUSED -> paused
            // An equal value is no change for the live region: clear it on a
            // resume so the next pause is announced too.
            AnnouncerEvents.Kind.RESUMED -> ""
        }
    }
    Box(
        Modifier
            .size(1.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announcement.value
            },
    )
}

/** Watches the engine step by step and names what just happened. */
private class AnnouncerEvents {
    enum class Kind { LEVEL_START, LEVEL_CLEAR, BALL_LOST, GAME_OVER, WIN, PAUSED, RESUMED }

    private var state = Game.State.TITLE
    private var level = 0
    private var lives = 0
    private var paused = false

    fun update(game: Game): Kind? {
        var kind: Kind? = null
        if (game.state != state) {
            kind = when (game.state) {
                Game.State.PLAYING -> Kind.LEVEL_START
                Game.State.LEVEL_CLEAR -> Kind.LEVEL_CLEAR
                Game.State.GAME_OVER -> Kind.GAME_OVER
                Game.State.WIN -> Kind.WIN
                Game.State.TITLE -> null
            }
        } else if (game.state == Game.State.PLAYING) {
            if (game.paused && !paused) kind = Kind.PAUSED
            else if (!game.paused && paused) kind = Kind.RESUMED
            else if (game.lives < lives) kind = Kind.BALL_LOST
            else if (game.level != level) kind = Kind.LEVEL_START
        }
        state = game.state; level = game.level; lives = game.lives; paused = game.paused
        return kind
    }
}

/** Which words the hints use: what the player taps, presses or touches. */
internal enum class InputHint { TOUCH, KEYS, TOUCHBAR }

/** Reads the frame counter: the calling composable now recomposes every step. */
@Suppress("UNUSED_EXPRESSION")
internal fun observe(frame: LongState) { frame.longValue }

/**
 * Places the element's centre at world point (wx, wy) inside its parent: the
 * same mapping the canvas uses, so text and shapes line up on every screen
 * whatever the insets.
 */
internal fun Modifier.atWorld(wx: Float, wy: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val scale = constraints.maxHeight / 2f
    val cx = constraints.maxWidth / 2f + wx * scale
    val cy = constraints.maxHeight / 2f + wy * scale
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place((cx - placeable.width / 2f).roundToInt(), (cy - placeable.height / 2f).roundToInt())
    }
}

/** The style with every specified size multiplied by [k] (the TV reads from across the room). */
internal fun TextStyle.scaled(k: Float): TextStyle =
    if (k == 1f) this else copy(
        fontSize = if (fontSize.isSpecified) fontSize * k else fontSize,
        letterSpacing = if (letterSpacing.isSpecified) letterSpacing * k else letterSpacing,
        lineHeight = if (lineHeight.isSpecified) lineHeight * k else lineHeight,
    )

/** The title: the name in the band between the demo wall and the bouncing
 *  ball, the podium under it, the tap hint above the paddle. */
@Composable
internal fun TitleOverlay(game: Game, frame: LongState, farViewing: Boolean, hint: InputHint, textScale: Float) {
    observe(frame)
    val pulse = 0.5f + 0.5f * sin(game.animTime * 4f)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.atWorld(0f, -0.36f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "COMPOSE", style = MaterialTheme.typography.titleLarge.scaled(textScale), color = PaddleCyan)
            Text(
                text = stringResource(R.string.title_line),
                style = if (farViewing) MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp)
                        else MaterialTheme.typography.displayLarge.scaled(textScale),
                color = TitleGold,
                textAlign = TextAlign.Center,
            )
        }
        // High-score podium (top three, gold / silver / bronze) and, under
        // it, where the next round starts: any level reached before. The
        // pick follows the podium rather than sitting at a height of its
        // own, so a short window never runs the two together.
        // A short window (small text) has room for the best score only.
        val podiumRows = if (textScale < 1f) 1 else 3
        Column(Modifier.atWorld(0f, 0.17f), horizontalAlignment = Alignment.CenterHorizontally) {
            for ((i, hs) in game.highScores.take(podiumRows).withIndex()) {
                val c = PodiumColors[i]
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Box(Modifier.size(width = 24.dp * textScale, height = 6.dp * textScale).background(c, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(16.dp))
                    Text(text = "%,d".format(hs.score), style = MaterialTheme.typography.titleLarge.scaled(textScale), color = c)
                    Spacer(Modifier.width(24.dp))
                    Text(text = "L${hs.level}", style = MaterialTheme.typography.titleMedium.scaled(textScale), color = c.copy(alpha = 0.8f))
                }
            }
            if (game.furthestLevel > 1) {
                val previous = stringResource(R.string.a11y_previous_level)
                val next = stringResource(R.string.a11y_next_level)
                // The glasses' touchbar is a single button: the pick is shown
                // there, made on the phone, so no arrows.
                val arrows = hint != InputHint.TOUCHBAR
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (arrows) IconButton(
                        onClick = { game.selectLevel(-1) },
                        enabled = game.startLevel > 1,
                        modifier = Modifier.semantics { contentDescription = previous },
                    ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null, tint = PaddleCyan) }
                    val startLevelLabel = stringResource(R.string.a11y_start_level, game.startLevel)
                    Text(
                        text = stringResource(R.string.start_level, game.startLevel),
                        style = MaterialTheme.typography.titleMedium.scaled(textScale),
                        color = PaddleCyan,
                        modifier = Modifier.padding(horizontal = 8.dp).semantics { contentDescription = startLevelLabel },
                    )
                    if (arrows) IconButton(
                        onClick = { game.selectLevel(1) },
                        enabled = game.startLevel < game.furthestLevel,
                        modifier = Modifier.semantics { contentDescription = next },
                    ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = PaddleCyan) }
                }
            }
        }
        Text(
            text = stringResource(
                when (hint) {
                    InputHint.TOUCH -> R.string.tap_to_play
                    InputHint.KEYS -> R.string.press_to_play
                    InputHint.TOUCHBAR -> R.string.touch_to_play
                },
            ),
            style = MaterialTheme.typography.titleMedium.scaled(textScale),
            color = Color.White.copy(alpha = 0.4f + 0.5f * pulse),
            modifier = Modifier.atWorld(0f, 0.44f),
        )
    }
}

/** Score, lives and level in the band above the ceiling; the AUTO badge
 *  sits by the level number while the autopilot plays. */
@Composable
internal fun Hud(game: Game, frame: LongState, chromePadding: androidx.compose.ui.unit.Dp, textScale: Float, autoPlay: Boolean) {
    observe(frame)
    val hudLabel = stringResource(R.string.a11y_hud)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = chromePadding, vertical = 6.dp)
            .padding(end = 48.dp)   // the gear's column
            .semantics { contentDescription = hudLabel },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val scoreLabel = stringResource(R.string.hud_score)
        Text(
            text = "%,d".format(game.score),
            style = MaterialTheme.typography.headlineMedium.scaled(textScale),
            color = Color.White,
            modifier = Modifier.semantics { contentDescription = "$scoreLabel ${game.score}" },
        )
        Spacer(Modifier.weight(1f))
        val livesLabel = stringResource(R.string.hud_lives)
        Row(Modifier.semantics { contentDescription = "$livesLabel ${game.lives}" }) {
            repeat(game.lives) {
                Box(
                    Modifier
                        .padding(horizontal = 3.dp * textScale)
                        .size(width = 18.dp * textScale, height = 5.dp * textScale)
                        .background(PaddleCyan, RoundedCornerShape(3.dp)),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (autoPlay) {
            Text(
                text = stringResource(R.string.auto_badge),
                style = MaterialTheme.typography.labelLarge.scaled(textScale),
                color = AutoGreen.copy(alpha = 0.55f + 0.4f * sin(game.animTime * 3.5f)),
                modifier = Modifier.padding(end = 16.dp),
            )
        }
        val levelLabel = stringResource(R.string.hud_level)
        Text(
            text = "${game.level}",
            style = MaterialTheme.typography.headlineMedium.scaled(textScale),
            color = TitleGold,
            modifier = Modifier.semantics { contentDescription = "$levelLabel ${game.level}" },
        )
    }
}

/** A served ball waits for a tap. */
@Composable
internal fun LaunchHint(game: Game, frame: LongState, hint: InputHint, textScale: Float) {
    observe(frame)
    val pulse = 0.5f + 0.5f * sin(game.animTime * 4f)
    Text(
        text = stringResource(
            when (hint) {
                InputHint.TOUCH -> R.string.tap_to_launch
                InputHint.KEYS -> R.string.press_to_launch
                InputHint.TOUCHBAR -> R.string.touch_to_launch
            },
        ),
        style = MaterialTheme.typography.labelLarge.scaled(textScale),
        color = Color(0xFFA6CCFF).copy(alpha = 0.4f + 0.5f * pulse),
        modifier = Modifier.atWorld(0f, 0.45f),
    )
}

/** A round frozen mid-flight: the field dims, the word, the way back in. */
@Composable
internal fun PausedOverlay(game: Game, frame: LongState, hint: InputHint, textScale: Float) {
    observe(frame)
    val pulse = 0.5f + 0.5f * sin(game.animTime * 4f)
    Box(Modifier.fillMaxSize().background(Color(0x66060812))) {
        val pausedLabel = stringResource(R.string.a11y_paused_overlay)
        Text(
            text = stringResource(R.string.paused),
            style = MaterialTheme.typography.displayMedium.scaled(textScale),
            color = PaddleCyan,
            modifier = Modifier.atWorld(0f, -0.05f).semantics { contentDescription = pausedLabel },
        )
        Text(
            text = stringResource(
                when (hint) {
                    InputHint.TOUCH -> R.string.tap_to_resume
                    InputHint.KEYS -> R.string.press_to_resume
                    InputHint.TOUCHBAR -> R.string.touch_to_resume
                },
            ),
            style = MaterialTheme.typography.labelLarge.scaled(textScale),
            color = Color(0xFFA6CCFF).copy(alpha = 0.4f + 0.5f * pulse),
            modifier = Modifier.atWorld(0f, 0.45f),
        )
    }
}

/** The level just cleared, big and green, for the pause before the next one. */
@Composable
internal fun LevelClearOverlay(game: Game, frame: LongState, textScale: Float) {
    observe(frame)
    Column(Modifier.atWorld(0f, -0.10f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.level_clear),
            style = MaterialTheme.typography.headlineMedium.scaled(textScale),
            color = AutoGreen,
        )
        Text(
            text = "${game.level}",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 160.sp * textScale, letterSpacing = 0.sp),
            color = AutoGreen,
        )
    }
}

/** GAME OVER / WIN share one layout: tinted overlay, big score, the rank
 *  when it is a new high score, the title pulsing under it. */
@Composable
internal fun EndScreen(game: Game, frame: LongState, title: String, tint: Color, textScale: Float) {
    observe(frame)
    val pulse = 0.5f + 0.5f * sin(game.animTime * 4f)
    Box(Modifier.fillMaxSize().background(tint.copy(alpha = tint.alpha * (0.7f + 0.3f * pulse)))) {
        if (game.newHighScore && game.newHighScoreRank in 0..2) {
            val c = PodiumColors[game.newHighScoreRank]
            Column(Modifier.atWorld(0f, -0.42f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "#${game.newHighScoreRank + 1}",
                    style = MaterialTheme.typography.displayMedium.scaled(textScale),
                    color = c.copy(alpha = 0.65f + 0.35f * pulse),
                )
                Text(
                    text = stringResource(R.string.new_high_score),
                    style = MaterialTheme.typography.labelLarge.scaled(textScale),
                    color = c,
                )
            }
        }
        val scoreColor = if (game.newHighScore) TitleGold.copy(alpha = 0.8f + 0.2f * pulse) else Color.White
        val finalScoreLabel = stringResource(R.string.a11y_final_score, game.score)
        Text(
            text = "%,d".format(game.score),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp * textScale),
            color = scoreColor,
            maxLines = 1,
            modifier = Modifier.atWorld(0f, -0.05f).semantics { contentDescription = finalScoreLabel },
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.scaled(textScale),
            color = Color.White.copy(alpha = 0.5f + 0.5f * pulse),
            modifier = Modifier.atWorld(0f, 0.30f),
        )
    }
}

/** The phone while the round runs on the glasses: a banner over the frozen
 *  field, the gear still reachable to bring the game back. */
@Composable
internal fun OnGlassesBanner(game: Game, frame: LongState, textScale: Float) {
    observe(frame)
    val pulse = 0.5f + 0.5f * sin(game.animTime * 3f)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99060812)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.atWorld(0f, -0.05f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.on_glasses),
                style = MaterialTheme.typography.displayMedium.scaled(textScale),
                color = PaddleCyan.copy(alpha = 0.7f + 0.3f * pulse),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.on_glasses_hint),
                style = MaterialTheme.typography.titleMedium.scaled(textScale),
                color = OnSpaceMuted,
            )
        }
    }
}

/** The points a bonus just earned, fading where they were earned. */
@Composable
internal fun BonusFlash(game: Game, frame: LongState, textScale: Float) {
    observe(frame)
    if (game.bonusFlashTimer <= 0f) return
    Text(
        text = "+${game.bonusFlash}",
        style = MaterialTheme.typography.titleLarge.scaled(textScale),
        color = TitleGold.copy(alpha = (game.bonusFlashTimer / Game.BONUS_FLASH_TIME).coerceIn(0f, 1f)),
        modifier = Modifier.atWorld(game.bonusFlashX, game.bonusFlashY),
    )
}
