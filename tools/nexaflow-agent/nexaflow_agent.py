#!/usr/bin/env python3
"""NexaFlow desktop bridge.

Zero-dependency Python 3 CLI that connects desktop/local agents to the
loopback-only NexaFlow Agent API through ADB/USB or an explicitly supplied
trusted direct endpoint. Refresh credentials are persisted per user; access
sessions are short-lived and rotated automatically.
"""

from __future__ import annotations

import argparse
import contextlib
import dataclasses
import ipaddress
import json
import os
import pathlib
import socket
import stat
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Iterator

MAX_HTTP_BYTES = 256 * 1024
MAX_CONFIG_BYTES = 1024 * 1024
DEFAULT_REMOTE_PORT = 8766
PROFILE_VERSION = 1
DEFAULT_PROFILE = "default"


class BridgeError(RuntimeError):
    """Expected bridge failure safe to show to the user."""


@dataclasses.dataclass(frozen=True)
class PairingPayload:
    challenge_id: str
    challenge_secret: str = dataclasses.field(repr=False)
    port: int = DEFAULT_REMOTE_PORT
    pairing_path: str = "/api/v1/auth/pair/complete"
    session_path: str = "/api/v1/auth/session"
    mcp_path: str = "/mcp"

    @classmethod
    def parse(cls, raw: str) -> "PairingPayload":
        try:
            value = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise BridgeError("Pairing payload is not valid JSON") from exc
        if not isinstance(value, dict):
            raise BridgeError("Pairing payload must be a JSON object")

        challenge_id = _bounded_string(value.get("challengeId"), "challengeId", 512)
        challenge_secret = _bounded_string(
            value.get("challengeSecret"),
            "challengeSecret",
            512,
        )
        port = value.get("port", DEFAULT_REMOTE_PORT)
        if not isinstance(port, int) or not 1 <= port <= 65535:
            raise BridgeError("Pairing payload port is invalid")

        return cls(
            challenge_id=challenge_id,
            challenge_secret=challenge_secret,
            port=port,
            pairing_path=_safe_path(value.get("pairingPath"), "/api/v1/auth/pair/complete"),
            session_path=_safe_path(value.get("sessionPath"), "/api/v1/auth/session"),
            mcp_path=_safe_path(value.get("mcpPath"), "/mcp"),
        )


@dataclasses.dataclass
class Profile:
    name: str
    agent_id: str
    transport: str
    refresh_token: str = dataclasses.field(repr=False)
    remote_port: int = DEFAULT_REMOTE_PORT
    serial: str | None = None
    direct_url: str | None = None
    session_path: str = "/api/v1/auth/session"
    mcp_path: str = "/mcp"

    @classmethod
    def from_json(cls, name: str, value: Any) -> "Profile":
        if not isinstance(value, dict):
            raise BridgeError(f"Profile {name!r} is invalid")
        transport = value.get("transport")
        if transport not in {"adb", "direct"}:
            raise BridgeError(f"Profile {name!r} has an invalid transport")
        remote_port = value.get("remotePort", DEFAULT_REMOTE_PORT)
        if not isinstance(remote_port, int) or not 1 <= remote_port <= 65535:
            raise BridgeError(f"Profile {name!r} has an invalid remote port")
        return cls(
            name=name,
            agent_id=_bounded_string(value.get("agentId"), "agentId", 256),
            transport=transport,
            refresh_token=_bounded_string(
                value.get("refreshToken"),
                "refreshToken",
                8192,
            ),
            remote_port=remote_port,
            serial=_optional_string(value.get("serial"), 256),
            direct_url=_optional_string(value.get("directUrl"), 2048),
            session_path=_safe_path(value.get("sessionPath"), "/api/v1/auth/session"),
            mcp_path=_safe_path(value.get("mcpPath"), "/mcp"),
        )

    def to_json(self) -> dict[str, Any]:
        return {
            "agentId": self.agent_id,
            "transport": self.transport,
            "refreshToken": self.refresh_token,
            "remotePort": self.remote_port,
            "serial": self.serial,
            "directUrl": self.direct_url,
            "sessionPath": self.session_path,
            "mcpPath": self.mcp_path,
        }


class ProfileStore:
    def __init__(self, path: pathlib.Path | None = None) -> None:
        self.path = path or default_profile_path()

    def load(self) -> dict[str, Profile]:
        if not self.path.exists():
            return {}
        if self.path.stat().st_size > MAX_CONFIG_BYTES:
            raise BridgeError("Bridge profile file is unexpectedly large")
        try:
            value = json.loads(self.path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise BridgeError("Unable to read bridge profiles") from exc
        if not isinstance(value, dict) or value.get("version") != PROFILE_VERSION:
            raise BridgeError("Bridge profile file version is unsupported")
        profiles = value.get("profiles", {})
        if not isinstance(profiles, dict):
            raise BridgeError("Bridge profile file is invalid")
        return {
            name: Profile.from_json(name, profile)
            for name, profile in profiles.items()
            if isinstance(name, str) and 1 <= len(name) <= 128
        }

    def get(self, name: str) -> Profile:
        profile = self.load().get(name)
        if profile is None:
            raise BridgeError(f"Unknown profile: {name}")
        return profile

    def put(self, profile: Profile) -> None:
        profiles = self.load()
        profiles[profile.name] = profile
        self._write(profiles)

    def _write(self, profiles: dict[str, Profile]) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        value = {
            "version": PROFILE_VERSION,
            "profiles": {
                name: profile.to_json()
                for name, profile in sorted(profiles.items())
            },
        }
        encoded = json.dumps(value, indent=2, sort_keys=True) + "\n"
        fd, temp_name = tempfile.mkstemp(
            prefix=self.path.name + ".",
            dir=str(self.path.parent),
            text=True,
        )
        temp_path = pathlib.Path(temp_name)
        try:
            if os.name != "nt":
                os.fchmod(fd, stat.S_IRUSR | stat.S_IWUSR)
            with os.fdopen(fd, "w", encoding="utf-8") as stream:
                stream.write(encoded)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temp_path, self.path)
            if os.name != "nt":
                os.chmod(self.path, stat.S_IRUSR | stat.S_IWUSR)
        finally:
            with contextlib.suppress(FileNotFoundError):
                temp_path.unlink()


@dataclasses.dataclass(frozen=True)
class AdbDevice:
    serial: str
    state: str
    detail: str


class AdbClient:
    def __init__(self, binary: str = "adb") -> None:
        self.binary = binary

    def devices(self) -> list[AdbDevice]:
        result = self._run(["devices", "-l"])
        return parse_adb_devices(result.stdout)

    @contextlib.contextmanager
    def forward(self, serial: str, remote_port: int) -> Iterator[int]:
        result = self._run(
            ["-s", serial, "forward", "tcp:0", f"tcp:{remote_port}"]
        )
        try:
            local_port = int(result.stdout.strip())
        except ValueError as exc:
            raise BridgeError(
                "ADB did not return the allocated local forwarding port"
            ) from exc
        try:
            yield local_port
        finally:
            with contextlib.suppress(BridgeError):
                self._run(
                    ["-s", serial, "forward", "--remove", f"tcp:{local_port}"]
                )

    def _run(self, args: list[str]) -> subprocess.CompletedProcess[str]:
        try:
            result = subprocess.run(
                [self.binary, *args],
                text=True,
                capture_output=True,
                timeout=15,
                check=False,
            )
        except FileNotFoundError as exc:
            raise BridgeError(
                "ADB was not found. Install Android platform-tools or pass --adb."
            ) from exc
        except subprocess.TimeoutExpired as exc:
            raise BridgeError("ADB command timed out") from exc
        if result.returncode != 0:
            detail = (result.stderr or result.stdout).strip()
            raise BridgeError(f"ADB failed: {detail or 'unknown error'}")
        return result


def parse_adb_devices(raw: str) -> list[AdbDevice]:
    devices: list[AdbDevice] = []
    for line in raw.splitlines():
        line = line.strip()
        if not line or line.startswith("List of devices attached"):
            continue
        parts = line.split(maxsplit=2)
        if len(parts) < 2:
            continue
        devices.append(
            AdbDevice(
                serial=parts[0],
                state=parts[1],
                detail=parts[2] if len(parts) > 2 else "",
            )
        )
    return devices


@dataclasses.dataclass(frozen=True)
class HttpResponse:
    status: int
    body: bytes
    headers: dict[str, str]

    def json(self) -> Any:
        try:
            return json.loads(self.body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise BridgeError("NexaFlow returned invalid JSON") from exc


class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: ANN001
        return None


class HttpClient:
    def __init__(self) -> None:
        self._opener = urllib.request.build_opener(NoRedirectHandler())

    def request(
        self,
        base_url: str,
        method: str,
        path: str,
        *,
        json_body: Any | None = None,
        body: bytes | None = None,
        headers: dict[str, str] | None = None,
    ) -> HttpResponse:
        base = validate_base_url(base_url)
        target = urllib.parse.urljoin(base.rstrip("/") + "/", path.lstrip("/"))
        request_headers = {
            "Accept": "application/json",
            "User-Agent": "nexaflow-agent/1",
            **(headers or {}),
        }
        payload = body
        if json_body is not None:
            payload = json.dumps(
                json_body,
                separators=(",", ":"),
            ).encode("utf-8")
            request_headers["Content-Type"] = "application/json"
        req = urllib.request.Request(
            target,
            data=payload,
            headers=request_headers,
            method=method,
        )
        try:
            with self._opener.open(req, timeout=15) as response:
                return HttpResponse(
                    status=response.status,
                    body=_bounded_read(response),
                    headers={k.lower(): v for k, v in response.headers.items()},
                )
        except urllib.error.HTTPError as exc:
            return HttpResponse(
                status=exc.code,
                body=_bounded_read(exc),
                headers={k.lower(): v for k, v in exc.headers.items()},
            )
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            raise BridgeError(f"Unable to reach NexaFlow: {exc}") from exc


@contextlib.contextmanager
def open_profile_connection(
    profile: Profile,
    adb: AdbClient,
) -> Iterator[str]:
    if profile.transport == "direct":
        if not profile.direct_url:
            raise BridgeError("Direct profile has no URL")
        yield validate_base_url(profile.direct_url)
        return
    if not profile.serial:
        raise BridgeError("ADB profile has no device serial")
    with adb.forward(profile.serial, profile.remote_port) as local_port:
        yield f"http://127.0.0.1:{local_port}"


class SessionManager:
    def __init__(
        self,
        store: ProfileStore,
        client: HttpClient,
    ) -> None:
        self.store = store
        self.client = client

    def exchange(self, profile: Profile, base_url: str) -> str:
        response = self.client.request(
            base_url,
            "POST",
            profile.session_path,
            json_body={"refreshToken": profile.refresh_token},
        )
        if response.status != 200:
            raise BridgeError(
                f"Session exchange failed with HTTP {response.status}"
            )
        value = response.json()
        if not isinstance(value, dict):
            raise BridgeError("Session response is invalid")
        access_token = _bounded_string(
            value.get("accessToken"),
            "accessToken",
            8192,
        )
        rotated = _bounded_string(
            value.get("rotatedRefreshToken"),
            "rotatedRefreshToken",
            8192,
        )
        profile.refresh_token = rotated
        self.store.put(profile)
        return access_token


def complete_pairing(
    args: argparse.Namespace,
    store: ProfileStore,
    client: HttpClient,
    adb: AdbClient,
) -> None:
    payload = PairingPayload.parse(read_pairing_payload(args))
    mode, serial, direct_url = choose_pair_transport(args, adb)
    if mode == "adb":
        assert serial is not None
        connection = adb.forward(serial, payload.port)
        stack = contextlib.ExitStack()
        local_port = stack.enter_context(connection)
        base_url = f"http://127.0.0.1:{local_port}"
    else:
        stack = contextlib.ExitStack()
        base_url = validate_base_url(direct_url or "")

    with stack:
        response = client.request(
            base_url,
            "POST",
            payload.pairing_path,
            json_body={
                "challengeId": payload.challenge_id,
                "challengeSecret": payload.challenge_secret,
            },
        )
    if response.status != 200:
        raise BridgeError(
            f"Pairing failed with HTTP {response.status}: "
            + safe_error_message(response)
        )
    value = response.json()
    if not isinstance(value, dict):
        raise BridgeError("Pairing response is invalid")
    agent_id = _bounded_string(value.get("agentId"), "agentId", 256)
    refresh_token = _bounded_string(
        value.get("refreshToken"),
        "refreshToken",
        8192,
    )
    profile_name = args.profile or DEFAULT_PROFILE
    if not 1 <= len(profile_name) <= 128:
        raise BridgeError("Profile name must be between 1 and 128 characters")
    profile = Profile(
        name=profile_name,
        agent_id=agent_id,
        transport=mode,
        refresh_token=refresh_token,
        remote_port=payload.port,
        serial=serial,
        direct_url=direct_url,
        session_path=payload.session_path,
        mcp_path=payload.mcp_path,
    )
    store.put(profile)
    print(
        json.dumps(
            {
                "profile": profile.name,
                "agentId": profile.agent_id,
                "transport": profile.transport,
                "serial": profile.serial,
                "url": profile.direct_url,
            },
            indent=2,
        )
    )


def show_status(
    args: argparse.Namespace,
    store: ProfileStore,
    client: HttpClient,
    adb: AdbClient,
) -> None:
    profile = store.get(args.profile)
    sessions = SessionManager(store, client)
    with open_profile_connection(profile, adb) as base_url:
        access = sessions.exchange(profile, base_url)
        response = client.request(
            base_url,
            "GET",
            "/api/v1/status",
            headers={"Authorization": f"Bearer {access}"},
        )
    if response.status != 200:
        raise BridgeError(f"Status request failed with HTTP {response.status}")
    print(json.dumps(response.json(), indent=2, sort_keys=True))


def show_devices(
    store: ProfileStore,
    adb: AdbClient,
) -> None:
    devices = [dataclasses.asdict(device) for device in adb.devices()]
    profiles = [
        {
            "name": profile.name,
            "agentId": profile.agent_id,
            "transport": profile.transport,
            "serial": profile.serial,
            "url": profile.direct_url,
        }
        for profile in store.load().values()
    ]
    print(json.dumps({"adbDevices": devices, "profiles": profiles}, indent=2, sort_keys=True))


def run_mcp_stdio(
    args: argparse.Namespace,
    store: ProfileStore,
    client: HttpClient,
    adb: AdbClient,
) -> None:
    profile = store.get(args.profile)
    sessions = SessionManager(store, client)
    with open_profile_connection(profile, adb) as base_url:
        access = sessions.exchange(profile, base_url)
        while True:
            line = sys.stdin.buffer.readline(MAX_HTTP_BYTES + 2)
            if not line:
                break
            if len(line) > MAX_HTTP_BYTES + 1:
                emit_stdio_error(None, -32600, "MCP stdio message exceeds size limit")
                continue
            if not line.strip():
                continue

            try:
                message = json.loads(line)
            except json.JSONDecodeError:
                emit_stdio_error(None, -32700, "Parse error")
                continue
            if not isinstance(message, dict):
                emit_stdio_error(None, -32600, "Invalid JSON-RPC message")
                continue

            response = _forward_mcp(client, base_url, profile, message, access)
            if response.status == 401:
                access = sessions.exchange(profile, base_url)
                response = _forward_mcp(client, base_url, profile, message, access)

            is_notification = "id" not in message
            if is_notification and response.status == 202:
                continue
            if response.status not in range(200, 300):
                emit_stdio_error(
                    message.get("id"),
                    -32000,
                    f"NexaFlow bridge HTTP {response.status}",
                )
                continue
            if not response.body:
                continue
            try:
                body_value = json.loads(response.body)
            except json.JSONDecodeError:
                emit_stdio_error(
                    message.get("id"),
                    -32603,
                    "NexaFlow returned invalid JSON-RPC",
                )
                continue
            sys.stdout.write(json.dumps(body_value, separators=(",", ":")) + "\n")
            sys.stdout.flush()


def _forward_mcp(
    client: HttpClient,
    base_url: str,
    profile: Profile,
    message: dict[str, Any],
    access_token: str,
) -> HttpResponse:
    return client.request(
        base_url,
        "POST",
        profile.mcp_path,
        body=json.dumps(message, separators=(",", ":")).encode("utf-8"),
        headers=build_mcp_headers(message, access_token),
    )


def build_mcp_headers(message: dict[str, Any], access_token: str) -> dict[str, str]:
    headers = {
        "Authorization": f"Bearer {access_token}",
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    method = message.get("method")
    params = message.get("params")
    meta = params.get("_meta") if isinstance(params, dict) else None
    protocol = (
        meta.get("io.modelcontextprotocol/protocolVersion")
        if isinstance(meta, dict)
        else None
    )
    if protocol == "2026-07-28":
        if not isinstance(method, str) or not method:
            raise BridgeError("Modern MCP message is missing method")
        headers["MCP-Protocol-Version"] = protocol
        headers["Mcp-Method"] = method
        if method == "tools/call":
            name = params.get("name") if isinstance(params, dict) else None
            if not isinstance(name, str) or not name:
                raise BridgeError("Modern tools/call is missing tool name")
            headers["Mcp-Name"] = name
    return headers


def choose_pair_transport(
    args: argparse.Namespace,
    adb: AdbClient,
) -> tuple[str, str | None, str | None]:
    if args.url:
        return "direct", None, validate_base_url(args.url)
    if args.serial:
        return "adb", args.serial, None
    online = [device for device in adb.devices() if device.state == "device"]
    if len(online) == 1:
        return "adb", online[0].serial, None
    if not online:
        raise BridgeError("No authorized ADB device found. Connect one or pass --url.")
    raise BridgeError("Multiple ADB devices are connected. Pass --serial explicitly.")


def read_pairing_payload(args: argparse.Namespace) -> str:
    sources = int(bool(args.payload)) + int(bool(args.payload_file))
    if sources > 1:
        raise BridgeError("Use only one pairing payload source")
    if args.payload:
        return args.payload
    if args.payload_file:
        path = pathlib.Path(args.payload_file)
        if path.stat().st_size > MAX_HTTP_BYTES:
            raise BridgeError("Pairing payload file is too large")
        return path.read_text(encoding="utf-8")
    if sys.stdin.isatty():
        raise BridgeError("Provide --payload, --payload-file, or pipe the pairing payload on stdin")
    raw = sys.stdin.read(MAX_HTTP_BYTES + 1)
    if len(raw.encode("utf-8")) > MAX_HTTP_BYTES:
        raise BridgeError("Pairing payload is too large")
    return raw


def validate_base_url(raw: str) -> str:
    try:
        parsed = urllib.parse.urlsplit(raw)
    except ValueError as exc:
        raise BridgeError("Direct URL is invalid") from exc
    if parsed.scheme not in {"http", "https"}:
        raise BridgeError("Direct URL must use http or https")
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise BridgeError("Direct URL must not contain credentials, query, or fragment")
    if not parsed.hostname or parsed.path not in {"", "/"}:
        raise BridgeError("Direct URL must contain only scheme, host, and port")
    try:
        port = parsed.port
    except ValueError as exc:
        raise BridgeError("Direct URL port is invalid") from exc
    if port is not None and not 1 <= port <= 65535:
        raise BridgeError("Direct URL port is invalid")
    if parsed.scheme == "http" and not host_is_private(parsed.hostname):
        raise BridgeError("Plain HTTP is allowed only for loopback/private/link-local addresses")
    return raw.rstrip("/")


def host_is_private(host: str) -> bool:
    try:
        addresses = [ipaddress.ip_address(host)]
    except ValueError:
        try:
            addresses = [
                ipaddress.ip_address(item[4][0])
                for item in socket.getaddrinfo(host, None, type=socket.SOCK_STREAM)
            ]
        except (socket.gaierror, ValueError) as exc:
            raise BridgeError("Unable to resolve direct endpoint host") from exc
    if not addresses:
        return False
    return all(
        address.is_loopback or address.is_private or address.is_link_local
        for address in addresses
    )


def safe_error_message(response: HttpResponse) -> str:
    try:
        value = response.json()
    except BridgeError:
        return "request rejected"
    if isinstance(value, dict):
        error = value.get("error")
        if isinstance(error, dict):
            message = error.get("message")
            if isinstance(message, str) and message:
                return message[:256]
    return "request rejected"


def emit_stdio_error(request_id: Any, code: int, message: str) -> None:
    value = {
        "jsonrpc": "2.0",
        "id": request_id,
        "error": {"code": code, "message": message},
    }
    sys.stdout.write(json.dumps(value, separators=(",", ":")) + "\n")
    sys.stdout.flush()


def default_profile_path() -> pathlib.Path:
    if os.name == "nt":
        root = pathlib.Path(
            os.environ.get("APPDATA", str(pathlib.Path.home() / "AppData" / "Roaming"))
        )
        return root / "NexaFlowAgent" / "profiles.json"
    root = pathlib.Path(os.environ.get("XDG_CONFIG_HOME", str(pathlib.Path.home() / ".config")))
    return root / "nexaflow-agent" / "profiles.json"


def _bounded_read(stream: Any) -> bytes:
    value = stream.read(MAX_HTTP_BYTES + 1)
    if len(value) > MAX_HTTP_BYTES:
        raise BridgeError("NexaFlow response exceeds bridge size limit")
    return value


def _bounded_string(value: Any, field: str, limit: int) -> str:
    if not isinstance(value, str):
        raise BridgeError(f"{field} is missing or invalid")
    value = value.strip()
    if not 1 <= len(value) <= limit:
        raise BridgeError(f"{field} is outside the allowed length")
    return value


def _optional_string(value: Any, limit: int) -> str | None:
    if value is None:
        return None
    if not isinstance(value, str) or not 1 <= len(value) <= limit:
        raise BridgeError("Profile string field is invalid")
    return value


def _safe_path(value: Any, default: str) -> str:
    if value is None:
        return default
    if not isinstance(value, str) or not value.startswith("/") or len(value) > 256 or "://" in value or "\\" in value:
        raise BridgeError("Pairing endpoint path is invalid")
    parsed = urllib.parse.urlsplit(value)
    if parsed.scheme or parsed.netloc or parsed.fragment:
        raise BridgeError("Pairing endpoint path is invalid")
    return value


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(prog="nexaflow-agent")
    root.add_argument("--config", help="Override the profile store path")
    root.add_argument("--adb", default=os.environ.get("ADB", "adb"), help="ADB executable path")
    sub = root.add_subparsers(dest="command", required=True)

    pair = sub.add_parser("pair", help="Consume a one-time NexaFlow pairing payload")
    pair.add_argument("--profile", default=DEFAULT_PROFILE)
    pair.add_argument("--serial", help="ADB device serial")
    pair.add_argument("--url", help="Explicit trusted direct endpoint")
    pair.add_argument("--payload", help="Pairing payload JSON")
    pair.add_argument("--payload-file", help="File containing pairing payload JSON")

    status = sub.add_parser("status", help="Show NexaFlow Agent API status")
    status.add_argument("--profile", default=DEFAULT_PROFILE)

    sub.add_parser("devices", help="List ADB devices and saved profiles")

    mcp = sub.add_parser("mcp", help="Run a stdio MCP proxy to the paired NexaFlow device")
    mcp.add_argument("--profile", default=DEFAULT_PROFILE)
    return root


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    config_path = pathlib.Path(args.config).expanduser() if args.config else None
    store = ProfileStore(config_path)
    client = HttpClient()
    adb = AdbClient(args.adb)
    try:
        if args.command == "pair":
            complete_pairing(args, store, client, adb)
        elif args.command == "status":
            show_status(args, store, client, adb)
        elif args.command == "devices":
            show_devices(store, adb)
        elif args.command == "mcp":
            run_mcp_stdio(args, store, client, adb)
        else:
            raise BridgeError("Unknown command")
        return 0
    except BridgeError as exc:
        print(f"nexaflow-agent: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
