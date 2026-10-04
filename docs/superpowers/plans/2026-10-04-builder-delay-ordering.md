# Builder Delay Ordering Implementation Plan

**Goal:** Let users place wait steps anywhere among task actions and set delays up to 24 hours with editable hours, minutes, and seconds counters.

**Architecture:** Represent builder-created waits as the existing `SYSTEM_WAIT` action so they share one reorderable action list and the existing sequential runtime. When opening tasks that contain the older canonical delay node, migrate that node into a legacy wait draft in its old position; preserve unrelated canonical nodes. A focused Compose duration editor edits hours/minutes/seconds and persists the existing wait seconds configuration.

**Tech Stack:** Kotlin, Jetpack Compose, Android string resources, JUnit, Gradle.

**Review Focus:**
- A wait previously saved as a canonical node remains at the end of the sequence when first opened, matching prior runtime order.
- A newly added wait can move before and after actions and retains its position after save/reopen.
- Entered values clamp to a maximum of 24 hours and to valid minute/second ranges.
- Arabic UI still displays counters left-to-right as hours, minutes, seconds.
- All shipped locales contain labels and accessibility descriptions for the three counters and their adjustment buttons.

## Tasks

1. [x] Add pure duration conversion and clamp tests for 0, 1 second, unit rollovers, exact 24 hours, and overflow; implement a `WaitDuration` helper.
2. [x] Add tests for converting an existing canonical `WaitNode` to a `SYSTEM_WAIT` action draft without changing its prior end-of-list position; implement the migration helper.
3. [x] Replace the canonical delay-only list with a wait-add button that appends `SYSTEM_WAIT` to `actionDrafts`; on load migrate canonical delay nodes and preserve all other canonical action nodes; persist their old remaining canonical nodes as before.
4. [x] Add a three-column Compose editor with hour/minute/second numeric inputs, increment/decrement buttons, fixed LTR column order, clamping, and localized labels; replace the wait slider/custom seconds field.
5. [x] Translate new strings in all shipped locales, run feature tests and Android lint, build the debug APK, and check the final diff.
