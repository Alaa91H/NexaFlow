# NexaFlow Remote Relay (device side)

Phase 15 of `docs/AGENT_PLATFORM.md`. Remote agents (ChatGPT, Claude,
Gemini, off-LAN custom agents) reach the phone without the phone ever
opening a public listener: the phone dials **out** to a relay server over a
single connection and serves signed requests on it.

> Server-side relay implementation is out of scope for this repository. This
> document specifies the wire contract the server must speak and what the
> device (`:core:agent-relay`) enforces.

## Frame transport

- One ordered text-message channel per connection (a WebSocket
  implementation plugs into `AgentRelayTransport`; the protocol itself is
  transport-agnostic).
- Every frame is a single JSON object, max 256 KB (`MAX_FRAME_BYTES`, mirroring
  the loopback HTTP parser bound).
- Device -> server: `hello`, `response`, `ping`, `pong`.
- Server -> device: `request`, `ping`, `pong`.

## hello

Sent once per connection:

```json
{"type": "hello", "v": 1, "deviceId": "pixel-8", "timestamp": 1729948800000, "nonce": "id-1"}
```

## request (server -> device)

```json
{
  "type": "request",
  "v": 1,
  "deviceId": "pixel-8",
  "agentId": "opencode",
  "requestId": "req-42",
  "timestamp": 1729948801000,
  "nonce": "n-77",
  "method": "POST",
  "target": "/api/v1/tasks",
  "headers": {"authorization": "Bearer <access-token>", "idempotency-key": "..."},
  "bodyBase64": "<standard-base64, may be empty>",
  "signature": "<base64url HMAC-SHA256>"
}
```

### Signature

`signature = base64url(HMAC_SHA256(linkKey, canonical))` where canonical is
the UTF-8 bytes of these `\n`-joined fields:

```text
nexaflow-relay-v1
deviceId
agentId
requestId
timestamp
nonce
method
target
headers (sorted "k=v" joined with "&", possibly empty)
bodyBase64
```

All signed fields are pre-validated to exclude `\n`, so the join is
unambiguous. Comparisons are constant-time.

### Device validation order

1. Envelope parses as JSON and `v == 1`, else `400 invalid_envelope` /
   `unsupported_version`.
2. `deviceId` equals this device, else `403 device_mismatch`.
3. `agentId`/`requestId` match the token pattern, else `400 invalid_identity`.
4. Method in `GET/POST/PUT/PATCH/DELETE`, target syntactically valid and on
   the allow-list (`/api/v1/*`, `/mcp`, `/a2a`, public JSON documents; the
   agent card stays local-only), else `404 not_found`.
5. Headers bounded (<= 64 entries, name <= 128, value <= 8192 chars), else
   `431 headers_too_large`.
6. Body decodes and fits 256 KB, else `400 invalid_body` / `413`.
7. Timestamp inside +-5 min skew, else `408 stale_request`.
8. Nonce well-formed and unseen in the bounded 4096-entry window, else
   `400 invalid_nonce` / `409 replay_rejected`.
9. HMAC verifies with the provisioned link key, else `401 invalid_signature`.

Only then is the nonce recorded and the request forwarded to the same local
agent API pipeline as loopback REST (bearer/scope/rate-limit/idempotency/
revision semantics unchanged, including the agent's own access-token
authorization inside `headers.authorization`).

Error responses are minimal `{"error":{"code":"..."}}` frames; validation
failures never reach the handler.

## response (device -> server)

```json
{"type": "response", "v": 1, "requestId": "req-42", "status": 201, "headers": {...}, "bodyBase64": "..."}
```

## Heartbeats and recovery

- Idle connections send `ping`; a missing `pong`/frame beyond
  `pingInterval + pongTimeout` (30 s + 10 s defaults) triggers reconnect.
- Reconnect uses bounded exponential backoff (1 s x2, max 60 s) plus jitter
  until `stop()`; clean closes reconnect immediately.
- Transports must throw `AgentRelayReadTimeout` (not return) on read
  timeouts so heartbeats can fire.

## Provisioning

Settings -> AI & Agents -> Remote Access provisions a 256-bit link key into
`SecureStorage` (`AgentRelayLinkStore`: `provision` / `rotate` / `import` /
`deprovision`). Deprovisioning stops remote access without touching agent
grants or automations. The global Agent Access kill switch stops the relay
client along with every other transport.

## Security invariants

- Outbound-only: no public phone port, no NAT/firewall changes.
- Defense in depth: link-key HMAC + nonce replay window + timestamp skew +
  the agent's own rotating Bearer session + scopes + route allow-list.
- Secrets stay references (`secret://...`); audit stays redacted; relay
  frames never carry plaintext secrets or link keys.
