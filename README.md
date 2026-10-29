# Disc Golf Tour

A 3D disc golf game for Android in the style of the early-2000s EA golf games: build a golfer, play a 9-hole
course, and watch your ratings grow the more you play. Native Kotlin, OpenGL ES 3.0, no third-party libraries.

## How a throw works

1. **Aim** with the on-screen arrows (or left stick), pick a disc, pick a shape (hyzer / flat / anhyzer).
   A cyan guide shows the flight of a perfect throw, including the curve and wind.
2. **Press and hold** the big button. The power bar fills.
3. **Release** to lock the power. The gold **PIN** mark is the power that reaches the basket.
   Hold past 100% and the throw overcooks (bigger miss).
4. A marker sweeps across the **accuracy bar**. **Tap** to stop it in the gold zone for a clean release.

## Controls

| Input | Action |
|---|---|
| A / Space / right trigger / on-screen button | Hold to charge, release to lock, press to stop the marker |
| Left stick, on-screen arrows, keyboard A / D | Aim |
| D-pad left/right, L1/R1, keyboard Q / E | Change disc |
| D-pad up/down, keyboard W / S | Change shot shape |
| Y | Scorecard |
| Start / B / Back | Pause |

## The golfer (RPG side)

Five ratings, 1-99:

| Rating | What it does |
|---|---|
| Power | Arm speed: 15 to 30 m/s release speed |
| Accuracy | Width of the gold zone, smaller release error |
| Putting | Better odds of a disc dropping into the chains, forgiving putts |
| Touch | Slows the power bar so partial-power shots are easier to time |
| Spin | Steadier flights, less random drift |

They improve two ways:

- **Use it and it grows.** Every stat has its own XP bar. Long drives feed Power, hitting the gold zone feeds Accuracy,
  putting feeds Putting, approach shots near the pin feed Touch, and well-matched discs and perfect power feed Spin.
- **Level up and spend.** Holes, birdies and finishing rounds give player XP. Each level gives 3 points to spend on any
  rating on the Golfer Card, and unlocks new discs for your bag (Falcon at level 2 up to the Wraith at level 9).

Character creator: name, handedness (lefties mirror every curve), four builds with different starting ratings,
10 points to spend, and appearance (skin, hair style and colour, headwear, shirt, shorts, body type).
The golfer is saved on the device after every hole.

## Project layout

```
app/src/main/java/com/discgolf/game/
  core/          Pure Kotlin, no Android types (unit-tested on the JVM)
    DiscFlight.kt      Flight model: gravity, drag, lift by angle of attack, turn and fade, rolling, launch + caddie maths
    SwingMeter.kt      Hold/release power bar and the accuracy marker
    PlayerProfile.kt   Ratings, XP, levels, appearance, save format
    Discs.kt           Disc models (speed/glide/turn/fade), shot shapes, handedness
    Course.kt          9 holes, trees, water, the basket's chain catch logic
    GameController.kt  The state machine for a round, scoring and progression
  gfx/           OpenGL ES 3 renderer, procedural meshes, the customizable low-poly golfer
  ui/            HUD (drawn with Canvas), glossy menu styling, golfer portrait
  GameActivity / MenuActivity / GolferActivity / SaveStore
app/src/test/    JVM unit tests (physics, meter, stats, save format, a full scripted round)
```

## Build and install

1. Open this folder in **Android Studio** (Koala or newer; it uses AGP 8.5.2, Gradle 8.9, JDK 17).
2. Let Gradle sync, then press **Run** with a phone connected by USB (USB debugging on),
   or **Build > Build APK(s)** and copy `app/build/outputs/apk/debug/app-debug.apk` to the phone.
3. Run the logic tests with `./gradlew test`.

A Bluetooth or USB gamepad works with no setup. Landscape only.

## Tuning

All flight constants live in one place: `Tuning` in `core/DiscFlight.kt`. Disc stats are in `Discs.kt`, hole layouts in
`Courses.defaultCourse()` in `Course.kt`, and progression rates in `PlayerProfile.kt` and `GameController.finishThrow`.

## Status

Compiled against the Android 34 SDK and the game logic is covered by unit tests. The rendering, HUD layout and touch
feel have not been run on a device yet, so expect some visual and feel tuning on first launch.

## Ideas for next

Elevation and terrain, more courses, tournament mode with computer opponents, forehand throws, disc wear, sound and music,
an unlockable gear shop, and a replay camera.
