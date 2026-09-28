# ADR-014 — Security and secret handling

**Status:** Accepted

## Context

Automations can contain webhook tokens, HTTP credentials, plugin data, phone
content, and privileged device operations.

## Decision

Security classification is part of canonical schema/operation metadata. Secret
values use dedicated types/storage paths and are excluded from summaries,
journals, logs, analytics, and ordinary exports unless explicitly protected.

High-risk operations require explicit capability/authorization checks.

## Invariants

- Secrets are never logged.
- Validation happens before privileged side effects.
- External inputs are bounded and validated.
- Plugin/provider boundaries enforce identity and permissions.
- Destructive/irreversible operations carry explicit safety metadata.
- Failures do not reveal secret values.

## Consequences

Security becomes a first-class architecture property instead of scattered
handler-specific behavior.
