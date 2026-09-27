# NexaFlow Local AI

NexaFlow talks to models through a provider-neutral abstraction
(`core:ai-runtime`). No engine code is specific to any vendor.

## Providers

One `OpenAiCompatibleProvider` covers all OpenAI-compatible runtimes:

- Ollama, LM Studio, vLLM, llama.cpp-compatible servers, LocalAI
- Any HTTPS OpenAI-compatible cloud endpoint

Configured in Settings → AI & Agents → Providers with endpoint, model and
API key (keys live in `SecureStorage`, never enter model context). Local
endpoints stay HTTP on private networks without enabling app-wide cleartext;
public endpoints require HTTPS; redirects are rejected and DNS/private
ranges are validated. A **Test connection** action probes the endpoint.

## Capability probing

Each provider reports `AiProviderCapabilities`: native tool calling,
structured output, streaming, vision/reasoning/context window, local flag.
Probing is explicit (tool-call reconstruction test, streaming detection);
unknown backends default to text-only and still work through the fallback
below.

## Structured-JSON fallback

Models without native function calling emit
`{"tool": "<name>", "arguments": {...}}` payloads. `AiStructuredToolParser`
accepts only known tool names with JSON-object arguments inside size bounds
— unknown names fail closed as plain text — and the engine executes them
through the exact same bounded tool loop, validation and trace coverage as
native calls. The format instruction is provider-bound only and is never
stored in history.

## Routing

Settings → AI model:

- **Automatic** (prefers local; cloud fallback explicit and off by default)
- **Local only** (never routes to cloud)
- **Cloud only** (never routes to local)
- **Custom provider** (never silently falls back)

Routing is deterministic and capability-aware (tool/structured-capable
models first). It applies live to NexaFlow Chat.

## Chat

The AI tab streams assistant deltas, cancellations, bounded transcripts and
tool progress, and executes validated create/update/enable/disable/delete/run
operations through the same command service as external agents — with
revision and idempotency rules intact. Voice input fills the composer via
the system recognizer. Conversation transcripts are per-chat and in-memory;
no plaintext secrets are stored.
