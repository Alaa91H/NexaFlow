# NexaFlow Architecture Decision Records

These ADRs are normative for the canonical automation migration. They document
the decisions that T02 freezes before implementation continues.

## Status vocabulary

- `Accepted`: binding architectural decision.
- `Superseded`: replaced by a later ADR that names it explicitly.
- `Deprecated`: still readable for compatibility but must not be extended.

## Canonical automation ADR set

1. ADR-001 — Canonical Workflow AST
2. ADR-002 — Stable IDs and Registry
3. ADR-003 — Multi-selection semantics
4. ADR-004 — Legacy migration strategy
5. ADR-005 — Schema-driven UI
6. ADR-006 — Workflow Compiler
7. ADR-007 — Capability Resolver
8. ADR-008 — Execution Planner
9. ADR-009 — Error and failure semantics
10. ADR-010 — Idempotency and retry policy
11. ADR-011 — Provider architecture
12. ADR-012 — Diagnostics and execution journal
13. ADR-013 — Versioning policy
14. ADR-014 — Security and secret handling
15. ADR-015 — Legacy removal policy

Any future change that contradicts one of these decisions must add a new ADR
that explicitly supersedes the old one; silent architectural drift is not
allowed.
