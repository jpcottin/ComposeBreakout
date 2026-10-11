package com.jpcottin.composebreakout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpcottin.composebreakout.R
import com.jpcottin.composebreakout.engine.Game
import com.jpcottin.composebreakout.theme.OnSpaceMuted
import com.jpcottin.composebreakout.theme.PaddleCyan
import com.jpcottin.composebreakout.theme.TitleGold
import com.jpcottin.composebreakout.theme.focusRing
import kotlin.math.sin

/** The whole game on one screen: the playfield, the chrome and the settings sheet. */
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = Settings())
    var showSettings by remember { mutableStateOf(false) }
    LogDeviceTraits()
    GameScreen(
        game = viewModel.game,
        settings = settings,
        onOpenSettings = { showSettings = true },
    )
    if (showSettings) {
        SettingsSheet(
            settings = settings,
            onSoundChange = viewModel::setSoundEnabled,
            onAutoPlayChange = viewModel::setAutoPlay,
            onDismiss = { showSettings = false },
        )
    }
}

/**
 * The stateless screen: drives the engine at the display's frame rate while
 * it is composed, maps touches into the world, and lays the chrome over the
 * playfield. [paused] stops the clock (previews, screenshot tests).
 */
@Composable
fun GameScreen(
    game: Game,
    settings: Settings,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
) {
    var frame by remember { mutableLongStateOf(0L) }
    var aspect by remember { mutableFloatStateOf(game.aspect) }

    // The frame loop: one engine step per vsync, with the real elapsed time.
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) game.update((now - last) / 1_000_000_000f)
                last = now
                frame++
            }
        }
    }

    val farViewing = isFarViewing()
    val chromePadding = if (farViewing) 48.dp else 16.dp   // overscan-safe on a TV

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { if (it.height > 0) { aspect = it.width.toFloat() / it.height; game.setAspect(aspect) } }
            .pointerInput(game) {
                // Every finger reaches the engine in world coordinates; the
                // engine decides what a touch does from where it lands.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val asp = size.width.toFloat() / size.height
                        for (change in event.changes) {
                            val wx = (change.position.x / size.width * 2f - 1f) * asp
                            val wy = change.position.y / size.height * 2f - 1f
                            val id = change.id.value.toInt() and 0xF
                            when {
                                change.changedToDown() -> game.onPointerDown(id, wx, wy)
                                change.changedToUp() -> game.onPointerUp(id)
                                change.pressed -> game.onPointerMove(id, wx, wy)
                            }
                            change.consume()
                        }
                    }
                }
            }
            .semantics { contentDescription = "Playfield" },
    ) {
        PlayfieldCanvas(game, frame, Modifier.fillMaxSize())

        if (game.state == Game.State.TITLE) TitleOverlay(game, frame, farViewing)

        // The gear, top-right, under the status bar.
        val gearInteraction = remember { MutableInteractionSource() }
        IconButton(
            onClick = onOpenSettings,
            interactionSource = gearInteraction,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(chromePadding)
                .focusRing(gearInteraction, CircleShape),
        ) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.settings),
                tint = if (settings.autoPlay) MaterialTheme.colorScheme.tertiary else OnSpaceMuted,
            )
        }
    }
}

/**
 * The title: the name in the band between the demo wall and the bouncing
 * ball, the tap hint above the paddle. Both are placed by fractions of the
 * full height, like the world the canvas draws, so they land in the same
 * place whatever the insets or the screen.
 */
@Composable
private fun TitleOverlay(game: Game, frame: Long, farViewing: Boolean) {
    @Suppress("UNUSED_VARIABLE") val tick = frame
    val pulse = 0.5f + 0.5f * sin(game.animTime * 4f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val titleTop = maxHeight * TITLE_TOP_FRACTION
        val hintTop = maxHeight * HINT_TOP_FRACTION
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = titleTop),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "COMPOSE",
                style = MaterialTheme.typography.titleLarge,
                color = PaddleCyan,
            )
            Text(
                text = stringResource(R.string.title_line),
                style = if (farViewing) MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp)
                        else MaterialTheme.typography.displayLarge,
                color = TitleGold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = if (hasNoPointer()) stringResource(R.string.press_to_play) else stringResource(R.string.tap_to_play),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.4f + 0.5f * pulse),
            modifier = Modifier.align(Alignment.TopCenter).offset(y = hintTop),
        )
    }
}

/** The title block starts just under the demo wall (world y -0.52). */
private const val TITLE_TOP_FRACTION = 0.24f
/** The tap hint sits above the paddle line (world y 0.40). */
private const val HINT_TOP_FRACTION = 0.70f
