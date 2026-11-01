# Ink Glass — what still needs a device

Everything that can be proven on this machine is proven by the gates below.
Four items from §9/§10/§12 of the prompt need running Android hardware, and this
machine has none: the SDK installed here (`~/AppData/Local/nf-tools/android-sdk`)
carries `platform-tools`, `platforms` and `build-tools` only — no `emulator`
package and no system image — and `adb devices` lists no attached device.
`cmdline-tools`' `sdkmanager` is refused by Google now, so an emulator image
cannot be fetched non-interactively without a signed-in SDK licence flow.

This file records the gap rather than claiming the checks.

## Proven on this machine (no device)

| Gate | Command | Result |
| --- | --- | --- |
| Unit tests | `./gradlew testDebugUnitTest` | 3199 tests, 0 failures |
| Lint | `./gradlew lintDebug` | green |
| Debug build | `./gradlew assembleDebug` | green |
| Release build | `./gradlew assembleRelease -PallowDebugSigning=true` | green |
| Contrast (§5.1, §8) | `InkContrastTest` | every text pair ≥ 4.5:1, boundary ≥ 3:1 |
| Glass fallback (§10) | `GlassModeTest` | API × Battery Saver × preference |
| Sparkline buckets (§6) | `SparklineTest` | 6 × 4-hour buckets, proportional heights |
| Script typography (§5.5) | `ScriptTypographyTest` | no tracking/case on Arabic |
| Category mapping (§5.2) | `DesignTokensTest` | first trigger → category → hue |
| Bidi titles (§5.5) | `RoutineCard`/`FlowChip` previews + `TextDirection.Content` | code-level only |
| 10 locales build | `lintDebug` (MissingTranslation as error) + `assembleRelease` | green |

## Needs a device

1. **Screenshots versus the reference artboard** (Dashboard at 390dp, Trigger
   picker). The prompt asks for these attached to the PR. Previews exist for
   every shared component in `InkComponentPreviews.kt`, but a rendered capture
   needs a device or an emulator.
2. **TalkBack pass.** Announcements (routine name/state/trigger summary/expanded,
   picker checked state, sparkline “{n} runs today”) are wired through semantics
   and compile, but “announces correctly” is only provable with TalkBack running.
3. **Font scale 200% and the Arabic RTL walk.** Layouts use start/end and the
   Dock uses `heightIn(min = 68.dp)`, so nothing is fixed-height, but the
   reflow itself needs a screen.
4. **Macrobenchmark Dashboard scroll (50 routines), before/after.** The module
   (`:macrobenchmark`) has startup and planner benchmarks only; a scroll
   benchmark seeded with 50 routines has to be written and run on hardware, and
   the P90 budget is a measurement, not a build result. No number is claimed.

## When hardware is available

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.nexaflow.app/.MainActivity
# RTL walk
adb shell "am start -n com.nexaflow.app/.MainActivity --es locale ar"
# font scale
adb shell settings put system font_scale 2.0
# macrobenchmark (after writing the scroll benchmark)
./gradlew :macrobenchmark:connectedDebugAndroidTest
```
