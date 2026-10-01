# Atomic Stability, Architecture & AI Provider Overhaul

Baseline: v3.91.3 / 9e5e2fa132f22601244a208509f3060dc1ac66e3

This branch executes the approved overhaul as one atomic programme. No intermediate release or merge is allowed. Tasks close strictly in order and each task must preserve or improve existing behavior.

## Ordered tasks

- [x] T00 Baseline Freeze
- [x] T01 Repository Fitness Gates
- [x] T02 Dispatcher/Lifecycle Foundation
- [ ] T03 SecureStorage V2
- [ ] T04 Persistence Safety
- [ ] T05 ExecutionEngine Decomposition
- [ ] T06 SystemController Decomposition
- [ ] T07 Trigger Editor Decomposition
- [ ] T08 Action Editor Decomposition
- [ ] T09 Builder Screen Decomposition
- [ ] T10 Shared Network Security
- [ ] T11 AI Domain V2
- [ ] T12 Secret References
- [ ] T13 AI Adapter Contract
- [ ] T14 Native Adapters
- [ ] T15 Gateway Support
- [ ] T16 Provider Registry
- [ ] T17 Model Registry & Capabilities
- [ ] T18 AI Data Migration
- [ ] T19 AI Add Provider UX
- [ ] T20 AI Routing & Health
- [ ] T21 Overall UI/UX Polish
- [ ] T22 Performance Pass
- [ ] T23 Privileged/Network Device Matrix
- [ ] T24 CI Consolidation
- [ ] T25 Fault Injection
- [ ] T26 Full Regression
- [ ] T27 Documentation Regeneration
- [ ] T28 Release Candidate Audit
- [ ] T29 Atomic Merge

## Non-negotiable invariants

- Preserve canonical workflow semantics and durable execution checkpoints.
- Never weaken Normal/Shizuku/Root least-privilege routing or read-back verification.
- Never persist API keys in Room, DataStore, SavedStateHandle, navigation, logs or diagnostics.
- Cloud credentials require HTTPS. Local cleartext is permitted only through an explicit local policy and must never receive cloud credentials.
- No destructive database downgrade is accepted as a recovery mechanism.
- Provider, dialect, endpoint/connection, credential and model are separate AI concepts.
- Verification and model discovery are separate operations.
- A provider may be enabled only after successful explicit verification.
- Each TriggerType, ActionType and ProviderDialect must have runtime behavior, validation, presentation/capability state and tests.

## Final closure

The programme closes only after detekt, lint, JVM, migration, AI contract, security, adversarial network, release/R8, signing, AAB, 16 KB, dependency verification, emulator release smoke, process-death, builder UI, RTL/accessibility and performance-regression gates pass.
