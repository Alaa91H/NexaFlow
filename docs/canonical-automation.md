# Canonical automation platform

> **Current guide.** The canonical platform is the single runtime and
> configuration path for NexaFlow automations. The phase-by-phase migration
> record lives in the [canonical automation inventory](canonical-automation-inventory.md);
> this guide describes the shipped surface and how it is verified.

## What the platform is

Automations are expressed as a **typed canonical AST** (`CanonicalAst.kt`,
T04) with typed values (no raw `Map<String, String>` core contract), stable
string identities (`CanonicalIdentityRegistry.kt`, T03) resolved from the
frozen 233-node legacy surface through the reviewed mapping table
(`LegacyMappingTable.kt`, T15 — 233/233). Legacy input is converted by the
legacy adapter (T14) plus the typed family upgrades (T17–T25); nothing is
guessed at runtime.

The runtime path is single and fail-closed:

```
legacy input → adapter (T14 + T15 + family overrides) → canonical plan
  → validation pipeline (T09) → execution plan (T10) → atomic commands
```

The cutover (T26) refuses to plan when the conversion is not fully verified;
unverified input is a typed failure, never a best-effort execution.

## The contracts that surround execution

| Concern | Contract | Phase |
| --- | --- | --- |
| Selection / execution semantics | `SelectionSemantics.kt` | T05 |
| Semantic rules (contradictions, batch writes) | `NodeSemanticRules.kt` | T06 |
| Capability routing | `CapabilityGraph.kt` (never silently) | T07 |
| Configuration schema (source of truth) | `NodeSchema.kt` | T08 |
| Validation pipeline | `CanonicalValidationPipeline.kt` | T09 |
| Execution planner | `ExecutionPlanner.kt` | T10 |
| Error model + journal (secrets redacted) | `ExecutionJournal.kt` | T11 |
| Configurator state machine | `NodeConfiguratorState.kt` | T12 |
| Persistence policy (dual-read / V3-write) | `WorkflowPersistencePolicy.kt` | T27 |
| Controlled migration rollout | `WorkflowMigrationOrchestrator.kt` | T28 |
| Safe consolidation optimizer | `CanonicalConsolidationOptimizer.kt` | T29 |
| Diagnostics UI model | `CanonicalDiagnosticsModel.kt` | T30 |
| Plugin SDK contract | `PluginCanonicalContract.kt` | T31 |
| Deterministic fault injection | `FaultInjectionController.kt` | T32 |
| Performance budgets | `CanonicalPerformanceBudget.kt` | T33 |
| Security auditor | `CanonicalSecurityAuditor.kt` | T34 |
| Accessibility / RTL contract | `CanonicalAccessibilityModel.kt` | T35 |
| Device matrix simulation | `CanonicalDeviceMatrixSimulator.kt` | T36 |
| Plugin condition contract | `PluginConditionContract.kt` | T41 |
| Documentation closure | `docs/canonical-automation.md` | T42 |
| Measured performance regression | `CanonicalPerfRegression.kt` | T43 |
| Release closure | `scripts/check_canonical_release_closure.py` | T44 |

## Standing guarantees

- **No generic shell.** Privileged operations are typed, allowlisted
  capability requests; a workflow can never carry a raw command string.
- **Secrets are never exposed.** Secret-reference payloads render as a fixed
  label in diagnostics, summaries and accessibility announcements; the
  security auditor refuses secrets in observation payloads (T34).
- **Unknown is not false.** Plugin condition reads resolve to the typed
  five-state result; only a real verdict produces a boolean (T41).
- **Legacy is retired by containment, not deletion.** The legacy enums remain
  the storage compatibility surface; canonical platform surfaces refuse new
  legacy-type usage and the containment boundary can only shrink (T39, see
  the retirement ledger in the inventory).
- **Migration and optimization are separate concerns.** Migration never
  performs opportunistic consolidation (ADR-004/ADR-008 boundary).

## Verification (the gates)

Every phase ships a CI gate under `scripts/check_canonical_*.py` plus a
unittest in `scripts/tests/`. The heaviest aggregates:

- `scripts/check_canonical_architecture_fitness.py` (T37) — dependency
  direction, purity boundaries, gate wiring.
- `scripts/check_canonical_release_readiness.py` (T38) — every canonical gate
  passes, statuses recorded, changelog ready, tree clean.
- `scripts/check_canonical_final_audit.py` (T40) — the closing audit: every
  gate tested, wired and green; every phase T05–T39 closed in the inventory.

The gates are deterministic: no clocks, no randomness, no network. CI runs
them on every push together with the Kotlin unit suites (`testDebugUnitTest`),
which cover the canonical model classes in `:domain` and `:core:plugin-sdk`.

## Status

The migration record is closed through T44. Operational verification status
(live device coverage, release publication) is tracked separately in
[VALIDATION.md](VALIDATION.md); canonical gate results recorded there are
local-run evidence, not device certifications.
