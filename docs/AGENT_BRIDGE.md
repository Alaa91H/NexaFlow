# NexaFlow Desktop Bridge

The desktop bridge is the local transport adapter between desktop agents and the Android Agent API.

## USB/ADB flow

Agent -> MCP stdio -> nexaflow-agent -> temporary adb forward -> 127.0.0.1:8766 on Android -> /mcp -> NexaFlow control plane.

The forward is created only while a bridge command is running and is removed when the command exits.

## Pairing

The user creates a five-minute one-time challenge from Settings > AI & Agents. The bridge submits that challenge through the temporary transport and receives a permanent refresh credential. The refresh credential is stored locally and is rotated every time the bridge creates a short-lived access session.

## Commands

- pair: consume a one-time pairing payload and save the resulting agent profile.
- status: exchange a session and read /api/v1/status.
- devices: list ADB devices and saved profiles without exposing credentials.
- mcp: proxy newline-delimited stdio JSON-RPC to the phone's /mcp endpoint.

## MCP compatibility

The bridge preserves legacy MCP 2025-11-25 traffic. For MCP 2026-07-28 it derives MCP-Protocol-Version, Mcp-Method, and Mcp-Name from the JSON-RPC envelope so the Android server can enforce modern routing-header parity.

## Network policy

USB is the default desktop path and does not expose any Android listener to the network. The CLI contains a direct-endpoint client for an explicitly trusted future LAN/VPN listener. Public direct URLs require HTTPS and private HTTP targets are revalidated before each request.

An Android-side LAN listener remains a separate opt-in feature and must never replace the loopback default.
