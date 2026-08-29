# Strict Task Lifecycle Guarantee

## Problem

Location-triggered automations could bypass the occurrence-aware exit coordinator. The monitor maintained a compatibility active marker but dispatched the end behavior directly through `ExecutionEngine`, allowing activation, process restart, and exit delivery to diverge.

## Fix

Location monitoring now uses the same durable lifecycle path as the other stateful monitors:

1. A location transition creates a unique occurrence and calls `ExecutionEngine.runAutomation` with an `AutomationLifecycleContext`.
2. The monitor marks the compatibility active state only after the durable runtime ledger confirms that exact occurrence.
3. Every location exit is submitted to `ExitCoordinator`, which atomically claims the active occurrence before dispatching end actions.
4. The active marker is removed only after the coordinator reports a confirmed successful exit. `AlreadyInProgress` and `RecoveryRequired` retain the marker and durable lifecycle state.
5. Runtime ledger entries are restored before location updates begin, so a process death cannot silently discard a real active task.
6. Location evaluations are serialized to prevent an exit callback from racing an activation callback.

## Guarantees

A successful lifecycle completion is now backed by a durable terminal transition. A failed main action or end action is recorded as a failure and retained for recovery rather than being reported as completed or discarded. Repeated location fixes cannot execute the same end behavior concurrently.

The Android CI pipeline remains the authoritative build validation because the local sandbox does not provide an Android SDK.
