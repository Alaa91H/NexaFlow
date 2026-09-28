# Canonical Automation Architecture Decision Records

These ADRs are the accepted architecture contract for the canonical automation
migration. They complement the existing production-hardening architecture;
they do **not** create a second workflow engine, capability resolver, scheduler,
or execution stack.

| ADR | Decision |
|---|---|
| ADR-001 | Canonical Workflow AST |
| ADR-002 | Stable IDs and registries |
| ADR-003 | Multi-selection semantics |
| ADR-004 | Legacy migration strategy |
| ADR-005 | Schema-driven configuration UI |
| ADR-006 | Workflow compiler integration |
| ADR-007 | Capability resolution |
| ADR-008 | Execution planning |
| ADR-009 | Error and failure semantics |
| ADR-010 | Idempotency, retry, verification and compensation |
| ADR-011 | Provider architecture |
| ADR-012 | Diagnostics and execution journal |
| ADR-013 | Versioning policy |
| ADR-014 | Security and secret handling |
| ADR-015 | Legacy retirement policy |

## Global invariants

1. Existing `WorkflowInterpreter`, `WorkflowDocumentCompiler`,
   `SemanticWorkflowPlanner`, `CapabilityRouter`, and `OperationRegistry`
   are extended rather than replaced.
2. Families are UX taxonomy. Runtime executes typed canonical intent.
3. Migration is one-to-one before optimization/consolidation.
4. No rewrite-on-read migration.
5. No fake success, silent data loss, or hidden runtime defaults.
6. Legacy compatibility remains until the explicit ADR-015 retirement gates pass.
7. New capabilities prefer target/operation/predicate/provider extensions over
   new persisted `ActionType` or `TriggerType` entries.

All ADRs are **Accepted**. Changes to an accepted decision require a superseding
ADR and matching CI updates; silently editing the architecture contract is not
permitted.
