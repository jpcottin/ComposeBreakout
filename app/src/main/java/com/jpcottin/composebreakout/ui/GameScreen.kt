package com.jpcottin.composebreakout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.window.core.layout.WindowSizeClass
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpcottin.composebreakout.R
import com.jpcottin.composebreakout.engine.Game
import com.jpcottin.composebreakout.engine.World
import com.jpcottin.composebreakout.glasses.GlassesSession
import com.jpcottin.composebreakout.theme.LaneLine
import com.jpcottin.composebreakout.theme.OnSpaceMuted
import com.jpcottin.composebreakout.theme.PaddleCyan
import com.jpcottin.composebreakout.theme.focusRing
/** The whole game on one screen: the playfield, the chrome and the settings sheet. */
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = Settings())
    var showSettings by remember { mutableStateOf(false) }
    LogDeviceTraits()
    // The speakers follow the activity: silent in the background.
    LifecycleResumeEffect(viewModel) {
        viewModel.audio.resume()
        viewModel.game.resume()
        onPauseOrDispose {
            viewModel.audio.pause()
            viewModel.game.pause()
        }
    }
    // The clock stops while the sheet is up: a round waits for the player.
    val glassesConnected by GlassesSession.connected.collectAsStateWithLifecycle()
    val glassesActive by GlassesSession.active.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val onGlasses = viewModel.role == GameViewModel.Role.GLASSES
    GameScreen(
        game = viewModel.game,
        settings = settings,
        onOpenSettings = { viewModel.game.pause(); showSettings = true },
        paused = showSettings,
        tabletop = !onGlasses && isTabletopPosture(),
        glasses = onGlasses,
    )
    if (showSettings) {
        val glassesState = when {
            glassesActive -> GlassesState.ACTIVE
            glassesConnected -> GlassesState.CONNECTED
            else -> GlassesState.NONE
        }
        val onGlassesToggle = {
            if (glassesActive) GlassesSession.bringBack()
            else if (activity != null) GlassesSession.launch(activity)
            showSettings = false
        }
        // A phone gets a sheet sliding up from the thumb; a wide window (a
        // tablet, a TV, an XR panel) a card in the middle of the screen.
        val wide = currentWindowAdaptiveInfoV2().windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
        if (wide) SettingsDialog(
            settings = settings,
            onSoundChange = viewModel::setSoundEnabled,
            onAutoPlayChange = viewModel::setAutoPlay,
            onHapticsChange = viewModel::setHaptics,
            onDismiss = { showSettings = false },
            glasses = glassesState,
            onGlassesToggle = onGlassesToggle,
        ) else SettingsSheet(
            settings = settings,
            onSoundChange = viewModel::setSoundEnabled,
            onAutoPlayChange = viewModel::setAutoPlay,
            onHapticsChange = viewModel::setHaptics,
            onDismiss = { showSettings = false },
            glasses = glassesState,
            onGlassesToggle = onGlassesToggle,
        )
    }
}

/**
 * The stateless screen: drives the engine at the display's frame rate while
 * it is composed, maps touches and keys into the world, and lays the chrome
 * over the playfield. [paused] stops the clock (previews, screenshot tests,
 * the settings sheet). In the [tabletop] posture (a foldable half-opened,
 * the hinge across the middle) the playfield takes the upper half and a
 * control surface the lower half, so the fold never cuts through the game.
 * On the [glasses] the background is pure black (transparent on AR lenses),
 * there is no gear, and the hints speak of the touchbar.
 */
@Composable
fun GameScreen(
    game: Game,
    settings: Settings,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    tabletop: Boolean = false,
    glasses: Boolean = false,
) {
    // The engine is a plain object, not Compose state: this counter is what
    // tells Compose a step happened. Everything that shows engine state
    // reads it (see [observe]) and so recomposes or redraws once per step.
    val frame = remember { mutableLongStateOf(0L) }

    // The frame loop: one engine step per vsync, with the real elapsed time.
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) game.update((now - last) / 1_000_000_000f)
                last = now
                frame.longValue++
            }
        }
    }
    val farViewing = isFarViewing()
    val noPointer = hasNoPointer()
    val chromePadding = if (farViewing) 48.dp else 16.dp   // overscan-safe on a TV
    val textScale = if (farViewing) 1.5f else 1f

    // Keys and the D-pad: the playfield holds focus while the game runs and
    // takes it back when the settings sheet closes. Up from the playfield
    // reaches the gear; down from the gear comes back.
    val playfieldFocus = remember { FocusRequester() }
    val gearFocus = remember { FocusRequester() }
    LaunchedEffect(paused) { if (!paused) runCatching { playfieldFocus.requestFocus() } }

    val background = if (glasses) Color.Black else MaterialTheme.colorScheme.background
    // What a screen reader says the field is, read once per step.
    observe(frame)
    val fieldState = if (game.isMidGame)
        stringResource(
            R.string.a11y_field_state, game.level,
            pluralStringResource(R.plurals.a11y_bricks_left, game.breakableLeft, game.breakableLeft),
            pluralStringResource(R.plurals.a11y_lives, game.lives, game.lives),
        )
    else ""
    val content: @Composable (Modifier) -> Unit = { playfieldModifier ->
    Box(
        modifier = playfieldModifier
            .background(background)
            .onSizeChanged { if (it.height > 0) game.setAspect(it.width.toFloat() / it.height) }
            .focusRequester(playfieldFocus)
            .focusable()
            .onKeyEvent { event ->
                val steer = when (event.key) {
                    Key.DirectionLeft, Key.A -> -1
                    Key.DirectionRight, Key.D -> 1
                    else -> 0
                }
                when {
                    steer != 0 -> {
                        when (event.type) {
                            KeyEventType.KeyDown -> game.setSteer(steer)
                            KeyEventType.KeyUp -> game.setSteer(0)
                        }
                        true
                    }
                    event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter ||
                        event.key == Key.Spacebar || event.key == Key.ButtonA -> {
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) game.tap()
                        true
                    }
                    event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown -> {
                        gearFocus.requestFocus(); true
                    }
                    else -> false
                }
            }
            .pointerInput(game) {
                // Every finger reaches the engine in world coordinates; the
                // engine decides what a touch does from where it lands. A
                // change a button already consumed (the gear) is not a tap.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val asp = size.width.toFloat() / size.height
                        for (change in event.changes) {
                            if (change.isConsumed) continue
                            val wx = (change.position.x / size.width * 2f - 1f) * asp
                            val wy = change.position.y / size.height * 2f - 1f
                            val id = change.id.value.toInt()
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
            .semantics {
                contentDescription = "Playfield"
                stateDescription = fieldState
            },
    ) {
        PlayfieldCanvas(game, frame, Modifier.fillMaxSize())
        Announcer(game, frame)

        val hint = if (glasses) InputHint.TOUCHBAR else if (noPointer) InputHint.KEYS else InputHint.TOUCH
        when (game.state) {
            Game.State.TITLE -> TitleOverlay(game, frame, farViewing, hint, textScale)
            Game.State.PLAYING, Game.State.LEVEL_CLEAR -> {
                Hud(game, frame, chromePadding, textScale, autoPlay = settings.autoPlay)
                if (game.state == Game.State.LEVEL_CLEAR) LevelClearOverlay(game, frame, textScale)
                else if (game.paused) PausedOverlay(game, frame, hint, textScale)
                else if (game.serveReady) LaunchHint(game, frame, hint, textScale)
            }
            Game.State.GAME_OVER -> EndScreen(game, frame, stringResource(R.string.game_over), Color(0x99990D14), textScale)
            Game.State.WIN -> EndScreen(game, frame, stringResource(R.string.you_win), Color(0x99198026), textScale)
        }
        BonusFlash(game, frame, textScale)
        if (!glasses && game.glassesActive) OnGlassesBanner(game, frame, textScale)

        // The gear, top-right, under the status bar. Not on the glasses:
        // Settings lives on the phone.
        if (!glasses) Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(chromePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val gearInteraction = remember { MutableInteractionSource() }
            IconButton(
                onClick = onOpenSettings,
                interactionSource = gearInteraction,
                modifier = Modifier
                    .focusRequester(gearFocus)
                    .onKeyEvent { event ->
                        if (event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown) {
                            playfieldFocus.requestFocus(); true
                        } else false
                    }
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
    }

    if (tabletop) {
        Column(modifier.fillMaxSize().background(background)) {
            content(Modifier.fillMaxWidth().weight(1f))
            ControlSurface(game, frame, Modifier.fillMaxWidth().weight(1f))
        }
    } else {
        content(modifier.fillMaxSize())
    }
}

/**
 * The lower half of a half-opened foldable: a steering surface the size of a
 * hand. A finger anywhere on it steers the paddle toward the finger's x, a
 * tap launches — it is the control strip, grown to fill the lower screen.
 */
@Composable
private fun ControlSurface(game: Game, frame: LongState, modifier: Modifier = Modifier) {
    observe(frame)
    val held = game.moveHeld
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .pointerInput(game) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            if (change.isConsumed) continue
                            // The surface spans the playfield's width: its x
                            // is the playfield's x, its y is the strip.
                            val wx = (change.position.x / size.width * 2f - 1f) * game.aspect
                            val id = change.id.value.toInt() + CONTROL_SURFACE_POINTER_BASE
                            when {
                                change.changedToDown() -> game.onPointerDown(id, wx, CONTROL_SURFACE_Y)
                                change.changedToUp() -> game.onPointerUp(id)
                                change.pressed -> game.onPointerMove(id, wx, CONTROL_SURFACE_Y)
                            }
                            change.consume()
                        }
                    }
                }
            }
            .semantics { contentDescription = "Control surface" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // The steering marker, under the paddle: where the finger acts.
            val scale = size.width / (2f * game.aspect)
            val cx = size.width / 2f + game.paddleX * scale
            val w = game.paddleHalfWidth * scale
            val h = size.height * 0.06f
            drawLine(LaneLine.copy(alpha = 0.30f), Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 3f)
            drawRoundRect(
                PaddleCyan.copy(alpha = if (held) 0.9f else 0.45f),
                Offset(cx - w, size.height / 2f - h / 2f), Size(2f * w, h), CornerRadius(h / 2f),
            )
        }
        Text(
            text = stringResource(R.string.slide_to_steer),
            style = MaterialTheme.typography.labelLarge,
            color = OnSpaceMuted,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
        )
    }
}

/** Pointers from the control surface get their own ids, so a finger there
 *  and one on the playfield never share a slot. */
private const val CONTROL_SURFACE_POINTER_BASE = 1_000_000
/** The control surface acts like the strip: its touches land in it. */
private const val CONTROL_SURFACE_Y = (World.STRIP_TOP_Y + 1f) / 2f

