# Canonical automation refactor baseline

This document freezes the repository state used by the canonical automation
architecture migration.

## Baseline

- Base branch: `main`
- Base commit: `4af72b54870c9938d0147d6daccc4f33eece8eb0`
- Unified CI run: #711 / run `36388318722`
- CI conclusion: **success**
- Persisted trigger enum entries: **57**
- Persisted action enum entries: **176**
- Total persisted node kinds: **233**

The counts above are intentionally frozen while the migration foundation is
being built. New user-visible capabilities should be expressed through the
canonical target/operation/schema model instead of growing the legacy enums.

## Existing contracts at the baseline

The repository already contains foundations that must be reused rather than
reimplemented in parallel:

- `AutomationNodeCatalog` and typed legacy configuration schemas.
- Runtime-to-schema contract audit: `scripts/check_node_contracts.py`.
- `OperationRegistry`, `SemanticActionRouter`, `SemanticWorkflowPlanner`.
- Capability routing and Android/Shizuku/Root strategy selection.
- Workflow compiler/interpreter infrastructure.
- Action handler registry covering all legacy action types.

## T00 invariants

1. No persisted legacy type may disappear during the migration.
2. The 57/176 enum counts may change only in a commit that explicitly updates
   this baseline gate and documents why the capability cannot be represented
   canonically.
3. Runtime configuration keys remain schema-declared.
4. Existing V1/V2 automations remain readable and executable.
5. Migration and optimization are separate concerns.

## T00 closure evidence

- [x] Baseline commit identified.
- [x] Main CI is green at the baseline commit.
- [x] Trigger/action counts captured.
- [x] Existing semantic/capability foundation identified.
- [x] A CI drift gate protects the frozen legacy surface.

T01 owns the complete 233-node behavior/mapping inventory.
