# Ink Glass — merging with `main`

The redesign lives on `design/ink-glass`, so `main` keeps shipping fixes while the
branch is reviewed. This file records how the branch is kept current: what was
replayed, every conflict and how it was resolved, what `main` owns that the
branch must not touch, and the gates that prove the two still fit together.

**Current state:** `design/ink-glass` is rebased onto `main` at `e88ae7be`
(*v3.91.19 — the Hilt/R8 release fix*), 2026-11-01. The pre-rebase tip is kept
as `backup/ink-glass-prerebase` until the branch is merged. Nothing from `main`
was reverted: every commit below `e88ae7be` is intact and the branch's eleven
commits sit on top of it.

```bash
git fetch origin main
git rebase origin/main          # never merge main into the branch
```

## What `main` owns (do not "restore" the old versions)

`main` moved the whole repository onto a new toolchain in v3.91.18/v3.91.19 and
the branch must follow it, not the other way round:

| Area | `main` value | Why it stays |
| --- | --- | --- |
| Gradle / AGP | 9.8.2 / 9.5.0 | wrapper and AGP 9.5 task fixes |
| Kotlin / KSP | 2.4.21 / 2.3.13 | compiler warnings are build failures |
| Compose BOM / material3 | 2026.09.00 / 1.5.0-alpha29 | the version the Ink components target |
| `SyncedSlider` | one helper for every slider | material3 1.5 removed the value overload |
| `app/proguard-rules.pro` | `_HiltModules` keeps | v3.91.18 crashed on launch without them |
| `lint.xml` | `UseRequiresApi` is an error | new `@TargetApi` fails the build |
| `gradle/verification-metadata.xml` | regenerated on Linux, cross-platform artifacts added by hand | dependency verification is on (`verify-metadata=true`) |

## Conflicts and how they were resolved

1. **`gradle/libs.versions.toml`** — the branch had added Haze to the old
   catalog. Took `main`'s catalog in full and re-added exactly two lines:
   `vHaze = "1.7.3"` and `dev-chrisbanes-haze-haze`. No other version moved.
2. **`gradle/verification-metadata.xml`** — `main`'s regenerated file is the
   base, including the Windows/macOS `aapt2` and detekt-classpath entries it
   added because the regeneration ran on Linux. Haze's metadata pulls Compose
   Multiplatform coordinates that `main` never resolved, so the merge is a
   union at component level: the three `dev.chrisbanes.haze` components plus the
   61 `org.jetbrains.compose.*`, `org.jetbrains.androidx.*` and
   `androidx.activity`/`androidx.navigationevent`/`androidx.tracing` components
   were added. An artifact-level diff confirmed no artifact is missing inside a
   shared component, and no entry was removed. Verification was **not**
   regenerated on Windows, which would have dropped the macOS-only checksums.
3. **`AutomationBuilderScreen.kt`** — `main` had changed the inline battery
   trigger's slider to `SyncedSlider`; the branch had removed that inline block
   entirely because the battery threshold now renders in its own
   `BatteryThresholdSection` below the catalogue. Kept the branch's structure
   (that is the redesign) and applied `main`'s API fix to the section instead.
4. **`BuilderComponents.kt`** — git auto-merged `main`'s `SyncedSlider` helper
   into the branch's file. The helper gained one optional `modifier` parameter
   so the numeric field's `Modifier.weight(1f)` row layout survives; `SliderRow`
   keeps using the helper unchanged.
5. **`SchemaFieldControls.kt`, `BatteryThresholdSection.kt`** — the last two
   value-overload `Slider` calls became `SyncedSlider`, so no deprecated
   material3 1.5 API remains anywhere in the branch.
6. **`CHANGELOG.md`** — `main`'s v3.91.19 and v3.91.18 entries are untouched.
   The `[Unreleased]` section had been spliced twice by the replay (Ink Glass
   bullets stranded under the trigger audit's "Unchanged" heading); it is now
   one set of `Changed / Added / Fixed / Removed / Unchanged` headings, with no
   bullet dropped and one new bullet recording this rebase.

## Gates run on the rebased branch

All commands use the repository's own wrapper, JDK 21 and Android SDK 37.

| Gate | Result |
| --- | --- |
| `:app:assembleDebug` | **green** — 650 tasks, no warnings (compiler warnings are errors) |
| `:app:testDebugUnitTest`, `:core:ui-components`, `:feature:automation-builder`, `:feature:dashboard`, `:feature:automations`, `:feature:history`, `:feature:themes`, `:core:datastore` unit suites | **green** |
| `:app:assembleRelease -PallowDebugSigning=true` | **green** — R8 full mode, resource shrinking, Hilt keep rules and Haze consumer rules all apply without new keep rules |
| `lintDebug` | **green** after one fix: the redesign stopped using `noto_sans_arabic.ttf`, and `UnusedResources` is an error, so the orphan font was deleted (the Arabic family is IBM Plex Sans Arabic). No lint suppression or baseline was added. |
| `gradle/verification-metadata.xml` | parses, every resolved artifact verifies (otherwise the three builds above fail before compiling) |

Notes:

- `assembleRelease` locally needs `-PallowDebugSigning=true`; the production
  keystore is a CI secret. The helper prints exactly that when it is missing.
- `:app:lintDebug` reports **no errors and one warning** — the agent
  coordinator's `ANDROID_ID` read that `main` already reviewed — so the branch
  adds no lint debt and needs no baseline or suppression.
- `scripts/check_verification_metadata.py`, `scripts/check_diff_coverage.py`
  and the translation/parity scripts need `python3`, which is not installed on
  this Windows host; they remain CI gates (the workflow runs them on Linux).

## Keeping the next merge cheap

- Rebase onto `origin/main` after every release, as here. The branch touches
  almost only `feature/`, `core/ui-components`, theme files, resources and
  `docs/`; `main`'s fixes live in the engine, ROM layer, build files and
  CI, so conflicts stay small and mechanical.
- When a toolchain release removes an API the redesign uses (`Slider` was the
  first), fix it in the branch with `main`'s own helper instead of restoring the
  old call site.
- After adding a dependency, merge verification metadata; never regenerate it on
  a different OS than the release lane without re-adding the other platform's
  artifacts.
- Run the four gates above before asking for a merge. A device pass
  (`docs/ink-glass/DEVICE_CHECKS.md`) is still required after a toolchain bump,
  because release-only failures — like the v3.91.18 Hilt crash — do not appear in
  a debug build.
