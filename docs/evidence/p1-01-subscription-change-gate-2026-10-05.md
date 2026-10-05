# P1-01 checkpoint — subscription-change regression coverage — 2026-10-05

**Implementation commit:** `621c083e24b01185ce35457f2d0d4d46259b1b86` (`test(engine): cover subscription change rebinding gate`).
**Base:** `c45598b412b8663adcd98d320703f48e2d46ec45` (`main`).

## Work completed

Added focused JVM coverage for `SubscriptionChangeGate`, the guard used by `ConnectivityMonitor` and its legacy telephony listener. The tests establish that the initial callback only seeds the observed subscription, 10,000 repeated notifications for the same subscription do not request rebinding, a changed subscription requests rebinding once, and `reset()` starts a new observation cycle.

## Verification

Command, run from the isolated `p1-01-subscription-gate-test` worktree on Windows with the configured Android SDK:

```text
./gradlew :core:automation-engine:testDebugUnitTest --tests com.nexaflow.core.engine.SubscriptionChangeGateTest --no-daemon --console=plain
```

Result: `BUILD SUCCESSFUL in 1m 48s`; 234 actionable tasks (4 executed, 230 up-to-date). JUnit XML reports 3 tests, 0 failures, 0 errors.

## Remaining acceptance work

This is regression coverage for the subscription-change gate only. It does not test executor rejection after shutdown or executor leak behavior. The required 72-hour device soak, Binder transaction observation, `ApplicationExitInfo` review, and physical-device retest remain `NOT TESTED` because no Android device was connected. P1-01 remains `PARTIAL`; this checkpoint does not close it or the G1 gate.
