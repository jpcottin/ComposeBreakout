# Compose Breakout

[![CI](https://github.com/jpcottin/ComposeBreakout/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/jpcottin/ComposeBreakout/actions/workflows/ci.yml)

A **Breakout-like** game written entirely in **Kotlin** and **Jetpack
Compose**, adaptive from a phone to a foldable, a tablet, Android XR and a TV
with a D-pad — and playable on Display AI Glasses when a pair is connected.

The game is drawn on a Compose `Canvas`; everything around it (the HUD, the
title, the settings sheet, the end screens) is Material 3. The game itself is
a pure-Kotlin module with no Android in it, so its tests and the Auto Play
bench run on any JVM in seconds. Sibling of
[VulkanBreakout](https://github.com/jpcottin/VulkanBreakout), the same game
with a hand-written Vulkan renderer.

> **Status:** the game is playable — ten levels, the four bonuses, the
> high-score table, the end screens — and Auto Play wins it. The adaptive
> inputs (D-pad, tabletop), the sounds and the glasses mode land in the next
> pull requests.

## The game

- **Ten levels**, easy to hard: 4 rows of bricks at level 1, 9 at level 10,
  the ball a little faster each level and faster still as the wall thins.
  Tough bricks (two hits) arrive at level 3, steel (never breaks, does not
  count) at level 5, armour (three hits) at level 7.
- **Controls:** the strip below the paddle steers — the paddle slides toward
  the finger — and any tap launches the ball resting on the paddle. The
  bounce angle follows where the ball meets the paddle: centre sends it up,
  the tips 65 degrees out. That is the aim.
- **Bonuses** drop from broken bricks: a wide paddle (10 s), a slow ball
  (8 s), multi-ball (every ball splits in three, up to six) and an extra
  life (up to nine). Five lives to start.
- **Scoring:** 10 / 20 / 30 points per brick kind times the level, 25 times
  the level per bonus, 100 times the level per cleared wall. The top five
  scores are kept; the top three show on the title screen.
- **Auto Play** (Settings): the autopilot plays the round. It works out
  where each ball comes down, gets the paddle there, and picks *where on
  the paddle* to take it so the rebound does the most damage: every
  candidate hit offset is flown against the wall with the real collision
  rules, scored by bricks hit per second of flight, and rejected when its
  own landing could not be caught in time. A rebound that hits nothing is
  judged one trip further, so the last brick in a corner still gets
  reached. Predictions are cached per ball and redone only on a bounce.
  Capsules are collected when the detour leaves time to be back under the
  ball. The gear turns green while it plays.

## Modules

| Module | What it is |
|---|---|
| `:engine` | The game: the world's geometry, the ten levels, the simulation (substepped collision, bonuses, scoring, high scores), the Auto Play autopilot. Pure Kotlin (`kotlin("jvm")`), no Android. JUnit tests; the bench is its `main` (`./gradlew :engine:run`). |
| `:app` | The Compose shell: `MainActivity`, the `Canvas` playfield, Material 3 chrome, device traits, DataStore for settings and high scores, previews, screenshot tests, instrumented tests. |

## Tech

- **Jetpack Compose + Material 3** — a `ModalBottomSheet` for Settings with
  `Switch` rows, `IconButton`s, Material typography and a fixed dark colour
  scheme (`theme/`).
- **One `Canvas`, one world** — the engine keeps world coordinates (x in
  [-aspect, aspect], y in [-1, 1], y down); the canvas maps them with one
  scale around the centre, so a world unit is square on every screen and the
  engine never sees a pixel. The frame loop is `withFrameNanos` in a
  `LaunchedEffect`: one engine step per vsync with the real elapsed time.
- **Device traits, not form-factor labels** — Compose's `mediaQuery { }`
  decides the inputs and margins from what the device can do: viewing
  distance drives overscan margins, pointer precision drives D-pad steering,
  window posture drives the tabletop layout on foldables. Previews fall back
  to `uiMode` when the Application class is not around.
- **Styles API** — the focus ring on the gear and the settings rows is a
  Compose `Style` driven by the component's `InteractionSource`, so a TV
  remote or a keyboard always shows where focus is.
- **The lane** — a wide screen (landscape, an unfolded foldable, a tablet, an
  XR panel) plays in a central lane of at most 10 brick columns with shaded
  margins, rather than a wall three times wider than a paddle can cover.
- **DataStore** for settings and the high-score table; the engine reports
  changes through a `GameHost` interface and never touches storage itself.
  A round in progress survives process death: the `ViewModel` registers a
  saved-state provider and the next instance resumes level, score and lives
  with a fresh wall.
- **Chrome placed in world coordinates** — the HUD overlays (title, launch
  hint, level banner, end screens, bonus flash) use a layout modifier that
  puts an element's centre at a world point, the same mapping the canvas
  uses, so text and shapes line up on every screen whatever the insets.
- **An icon that is the game** — adaptive launcher icon (bricks with a gloss,
  the ball mid-bounce with its trail, the paddle), a monochrome layer for
  themed icons, and a TV banner with the name set in a bitmap block font.
- AGP 9, Kotlin 2.4, Compose BOM 2026.09, `minSdk 26`, `compileSdk 37`.

## The Auto Play bench

The engine's `main` plays seeded, headless rounds with the autopilot and
reports how often it clears each level, how long it takes and how many
balls it loses. Every Auto Play change is measured here before it ships:

```bash
./gradlew :engine:run --args="--runs 100 --levels --campaigns"        # portrait phone
./gradlew :engine:run --args="--width 2400 --height 1080 --runs 50 --campaigns"
./gradlew :engine:run --args="--runs 1 --levels --trace"              # the scene at every lost ball
```

CI runs it with `--min-campaign-win 95` on a portrait phone and a landscape
screen: fewer than 95% of campaigns won fails the build.

## Build & run

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.jpcottin.composebreakout/.MainActivity
```

## Testing

```bash
./gradlew :engine:test                       # the game, on the JVM
./gradlew :app:testDebugUnitTest             # the app's JVM tests
./gradlew :app:validateDebugScreenshotTest   # Compose preview screenshots against the references
./gradlew :app:updateDebugScreenshotTest     # re-record the references after a deliberate change
./gradlew :app:connectedDebugAndroidTest     # instrumented tests on a device or emulator
```

The screenshot tests render the `@Preview`s in `app/src/screenshotTest` —
every screen of the game (title, in play, served ball, level clear, game
over, win, settings) on phone, foldable, tablet and TV — and compare them
with the references committed under `app/src/screenshotTestDebug/reference`.
The engine is seeded and stepped deterministically, so each preview is the
same picture every time.

The instrumented test drives the real activity with a paused test clock
(the game steps once per frame for as long as it is on screen, which the
Compose test harness would otherwise read as "never idle").

## CI/CD

GitHub Actions runs on every push and pull request to `main`; every job gates
merges.

| Job | What it does | Artifacts |
|-----|-------------|-----------|
| **Build, lint, tests, screenshots** | Engine JUnit tests, Android lint, app unit tests, Compose preview screenshot validation, debug APK | `debug-apk`, `screenshot-test-report` on failure |
| **Auto Play bench** | Seeded campaigns with the autopilot on a portrait phone (100) and a landscape screen (50); fails under a 95% campaign win rate | — |
| **Instrumented** | Launches the activity on three emulators — a phone (API 34), a foldable (API 36, `7.6in Foldable`) and a TV (API 36, `android-tv`) — and checks the title and the settings sheet | test report on failure |

## License

[Apache License 2.0](LICENSE).
