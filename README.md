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

> **Status:** this is the project skeleton — the two modules, the theme, the
> playfield canvas drawing the title demo, the settings sheet, device traits,
> the icon, tests and CI. The gameplay, the Auto Play autopilot, the adaptive
> inputs (D-pad, tabletop), the sounds and the glasses mode land in the next
> pull requests.

## Modules

| Module | What it is |
|---|---|
| `:engine` | The game: the world, the simulation, the Auto Play autopilot. Pure Kotlin (`kotlin("jvm")`), no Android. JUnit tests; the bench is its `main` (`./gradlew :engine:run`). |
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
- **An icon that is the game** — adaptive launcher icon (bricks with a gloss,
  the ball mid-bounce with its trail, the paddle), a monochrome layer for
  themed icons, and a TV banner with the name set in a bitmap block font.
- AGP 9, Kotlin 2.4, Compose BOM 2026.09, `minSdk 26`, `compileSdk 37`.

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

The screenshot tests render the `@Preview`s in `app/src/screenshotTest`
(phone, foldable, tablet, TV, the settings sheet) and compare them with the
references committed under `app/src/screenshotTestDebug/reference`.

## CI/CD

GitHub Actions runs on every push and pull request to `main`; every job gates
merges.

| Job | What it does | Artifacts |
|-----|-------------|-----------|
| **Build, lint, tests, screenshots** | Engine JUnit tests, Android lint, app unit tests, Compose preview screenshot validation, debug APK | `debug-apk`, `screenshot-test-report` on failure |
| **Instrumented** | Launches the activity on three emulators — a phone (API 34), a foldable (API 36, `pixel_fold`) and a TV (API 34, `android-tv`) — and checks the title and the settings sheet | test report on failure |

## License

[Apache License 2.0](LICENSE).
