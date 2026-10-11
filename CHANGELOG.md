# Changelog

## 1.1

Played on phones, foldables, tablets, TVs, an XR headset and AI Glasses,
on the device and on every emulator leg of the CI.

### The game
- Start a round at any level reached before: the title screen shows a
  level pick under the podium once there is a choice, with arrows to tap
  or left and right on a D-pad or keyboard. The record is kept across
  launches; a Reset progress row in Settings forgets it along with the
  high scores.
- Pause: a round in flight freezes when the screen goes away, when
  Settings opens, on Back, and on P, Escape or a remote's play/pause
  button. Tap, OK or the same key resumes; the autopilot resumes itself.
- Haptics: a light tick for every broken brick and a click for a lost
  ball, with a switch in Settings.
- The AUTO badge sits by the level number while the autopilot plays.
- A launcher shortcut starts an Auto Play round straight away.

### Auto Play
- A fixed 120 Hz timestep: the same game at any refresh rate, and the
  same bench numbers on every machine.
- Dead rebounds lean toward the nearest brick, and a ball that has gone
  without a brick for six seconds breaks its pattern and changes lane.
  Landscape campaigns go from 96% to 98% won on the bench; portrait
  stays at 100%.
- The bench replays one seed or one level (`--seed`, `--level`).

### Everywhere
- French and Spanish, and the app is listed in the system's per-app
  language picker (Android 13+).
- A screen reader hears the round: level begins and cleared, ball lost
  with the lives left, game over and win with the score, pause; the
  playfield describes level, bricks left and lives.
- Settings is a centred dialog on a wide window (tablet, TV, XR panel,
  desktop) and a bottom sheet on a phone.
- Predictive back, and a portrait default size for free-form windows.
- Target API 37.

## 1.0

The Compose port: ten levels, bonuses, scoring and high scores, Auto
Play with its bench, real-time synthesised sounds, D-pad and keyboard
steering, the tabletop control surface on a half-opened foldable, and
play on AI Glasses with the touchbar steering.
