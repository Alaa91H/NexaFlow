# NexaFlow MCP

NexaFlow exposes a loopback-only MCP endpoint on the same bounded HTTP server as
the REST API:

\`\`\`text
http://127.0.0.1:8766/mcp
\`\`\`

It supports MCP \`2026-07-28\` as the preferred stateless protocol and
\`2025-11-25\` for initialize-based compatibility clients. Modern requests must
carry matching protocol, method and tool-name routing headers plus the
per-request MCP metadata envelope.

Every request uses the same paired-agent bearer authorization as REST. Tool
calls forward into the existing REST/control contract, so MCP does not create a
second persistence, scheduler, execution, privilege or lifecycle path.

The deterministic inventory includes status/capabilities/catalog tools, task
list/get/create/update/clone/enable/disable/delete/run, validation, schedule
preview, no-side-effect simulation, history and redacted audit.

Mutating tools require an idempotency key. Existing-task mutations and manual
runs also require the exact current revision, preventing retries from silently
duplicating side effects or overwriting newer definitions.
