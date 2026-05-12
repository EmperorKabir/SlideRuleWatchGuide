# reference watch Guide

Educational Android app that simulates the reference pilot's chronograph slide-rule
bezel. Rotate the outer bezel and watch every slide-rule equation
update live in a persistent grouped panel — division, multiplication,
speed/time/distance, statute & nautical mile to km conversions, and the
60/36 time-unit conversions documented in the source spreadsheet.

> **Personal use, sideload only.** This build retains /
> reference watch wordmarks and the anchor logo for educational fidelity. It is
> never published to the Play Store.

## Stack

- Kotlin 2.0 + Jetpack Compose (Material 3)
- minSdk 30, targetSdk 34, compileSdk 34
- kotlinx.datetime for system-clock hands

## Project layout

```
app/src/main/kotlin/com/sliderulewatchguide/
├─ MainActivity.kt
├─ SlideRuleApp.kt
├─ dial/        # DialMath, DialCanvas, BezelGesture, MarkerSpec, DialColors
├─ controls/    # StepArrows, AngleSlider, AlignInput
├─ equations/   # Equation, EquationEngine (pure), EquationsPanel
├─ viewmodel/   # DialViewModel
└─ ui/theme/    # Color, Theme, Type
```

The math layer (`dial/DialMath.kt`) and the equation engine
(`equations/EquationEngine.kt`) are pure Kotlin and have unit tests in
`app/src/test/kotlin/`.

## Build

```pwsh
.\gradlew.bat :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Unit tests:

```pwsh
.\gradlew.bat :app:testDebugUnitTest
```

## Sideload

```pwsh
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Or copy the APK to the phone and tap it (allow "install from unknown
sources" once).

## Marker geometry — why these values

| Marker | Inner-scale value | Why |
|---|---|---|
| Red 60 / MPH (12 o'clock) | 60 | 60 minutes / hour |
| Red 10 (~2:30) | 10 | unit index for × ÷ |
| Red 36 | 36 | 60 × 60 = 3600 |
| KM | 61.0 | km reference |
| STAT | 61 / 1.609344 ≈ 37.91 | 1 statute mile = 1.609344 km |
| NAUT | 61 / 1.852 ≈ 32.94 | 1 nautical mile = 1.852 km |

## Source-of-truth equations

`sheet.csv` (rows 10–33) — the user-supplied spreadsheet of equations
the app must demonstrate.

## CI (GitHub Actions)

A ready-to-go workflow lives at `docs/github-actions-workflow.yml`. It's
not under `.github/workflows/` because the PAT this project was first
pushed with lacked the `workflow` scope. To enable CI:

1. Create a new GitHub PAT with `repo` + `workflow` scopes.
2. `cmdkey /generic:git:https://github.com /user:EmperorKabir /pass:NEW_TOKEN`
   (or replace via Windows Credential Manager UI).
3. Move the file: `mkdir -p .github/workflows && mv docs/github-actions-workflow.yml .github/workflows/build.yml`.
4. Commit and push. The Actions tab will then build a debug APK on every
   push and upload it as a downloadable artifact.

## Trademark notice

"BREITLING", "NAVITIMER" and the winged-anchor mark are trademarks of
SA. This is an unaffiliated educational fan project. Sideload
only.
