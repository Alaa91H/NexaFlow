# ADR-014: Security and Secret Handling

**Status:** Accepted  
**Date:** 2026-09-28

## Context

Automation can access privileged device state, webhooks, HTTP endpoints,
plugins, notifications, messages and destructive package/system operations.
Canonical consolidation must not broaden authority accidentally.

## Decision

Security is part of schema/capability contracts. Sensitive fields are typed as
secret/reference values and use the existing secure-storage/vault direction;
plaintext secrets are not copied into diagnostics or canonical summaries.

Root/Shizuku/Accessibility/plugin/high-risk operations require explicit
capability policy and existing authorization/approval flows. Destructive or
externally consequential operations declare risk and confirmation requirements.
Webhook authentication and network/private-LAN policies remain explicit.

## Invariants

- No secrets in logs, history exports, evidence stores or UI summaries.
- Canonicalization never upgrades privilege.
- Plugins receive only approved typed capability requests.
- Privileged operations use allowlisted typed operations, not workflow-provided
  shell strings.
- Security/permission revocation invalidates capability availability.
- Destructive operations fail closed on missing/ambiguous configuration.

## Consequences

A simpler grouped UI does not weaken trust boundaries; security requirements
remain visible and enforceable even as execution providers vary.
