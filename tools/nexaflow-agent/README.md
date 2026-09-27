# NexaFlow Agent Bridge

Desktop bridge for local AI agents and MCP clients. It has no third-party Python dependencies.

## Requirements

- Python 3.10 or newer
- Android platform-tools (adb) for USB mode
- NexaFlow with AI Agent Access enabled

## Pair over USB

1. Open NexaFlow > Settings > AI & Agents.
2. Enable AI Agent Access.
3. Generate a one-time pairing payload.
4. Connect and authorize USB debugging.
5. Run:

    python tools/nexaflow-agent/nexaflow_agent.py pair --payload '<PAIRING_JSON>'

If exactly one authorized device is connected, it is selected automatically. Use --serial when several devices are attached.

## Inspect status and devices

    python tools/nexaflow-agent/nexaflow_agent.py devices
    python tools/nexaflow-agent/nexaflow_agent.py status

## MCP stdio

Configure an MCP-capable desktop agent to execute:

    python tools/nexaflow-agent/nexaflow_agent.py mcp

The bridge opens a temporary adb forward to the phone's loopback-only Agent API, rotates the refresh credential when exchanging sessions, forwards newline-delimited JSON-RPC, and retries one time after an HTTP 401.

## Security

- Refresh and access credentials are never printed.
- The profile file is atomically replaced; on POSIX it is restricted to mode 0600.
- HTTP redirects are rejected.
- Requests and responses are bounded to 256 KiB.
- Public direct endpoints require HTTPS.
- Plain HTTP direct endpoints are accepted only for loopback, private, or link-local addresses.
- Android remains loopback-only by default. Direct LAN access is not enabled merely by installing this bridge.
