#!/usr/bin/env python3
"""Announce a published GitHub release in Telegram (channel + community topic).

The announcement is driven by the release page itself: the notes file is the
exact body GitHub shows, and the two APKs are the exact assets attached to the
release. Nothing is retyped, so subscribers read the same changelog as the
repository.

Transport is the plain Telegram Bot API over HTTPS - no userbot session, no
MTProto client, no hosted platform. A bot token and two chat targets are the
whole configuration; they arrive as GitHub Actions secrets.

Targets
  channel    TELEGRAM_CHANNEL_CHAT_ID   e.g. @NexaFlowApp
  community  TELEGRAM_COMMUNITY_CHAT_ID e.g. @NexaFlowCommunity
             TELEGRAM_COMMUNITY_TOPIC_ID (optional) e.g. 4 (a forum topic)

Usage
  python3 scripts/publish_telegram_release.py \
      --tag v3.91.16 --notes-file release-notes.md \
      --phone NexaFlow-v3.91.16.apk --wear NexaFlow-Wear-v3.91.16.apk

  Add --dry-run to render the messages without contacting Telegram.
"""
from __future__ import annotations

import argparse
import html
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

try:  # Windows consoles default to cp1252; keep reports readable.
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:  # pragma: no cover
    pass

API_BASE = "https://api.telegram.org"
MESSAGE_LIMIT = 3800  # Telegram rejects > 4096; leave room for the ellipsis.
CAPTION_LIMIT = 900
MAX_ATTEMPTS = 4

LINK_RE = re.compile(r"\[([^\]]+)\]\(([^)\s]+)\)")
BOLD_RE = re.compile(r"\*\*([^*]+)\*\*")
CODE_RE = re.compile(r"`([^`]+)`")
HEADING_RE = re.compile(r"^\s{0,3}#{1,6}\s+(.*)$")
BULLET_RE = re.compile(r"^\s*[-*]\s+")
HR_RE = re.compile(r"^\s*([-*_])\1{2,}\s*$")


class TelegramError(RuntimeError):
    """A Bot API call failed in a way the workflow should surface."""


# ---------------------------------------------------------------------------
# Rendering
# ---------------------------------------------------------------------------

def escape_html(text: str) -> str:
    """Escape the three characters Telegram HTML treats as markup."""
    return html.escape(text, quote=False)


def markdown_to_html(markdown: str) -> str:
    """Convert the subset of GitHub markdown used by release notes.

    Every line is escaped first, then the few inline constructs are restored,
    so a changelog that mentions `<` or `&` cannot break the message.
    """
    out: list[str] = []
    for raw in markdown.splitlines():
        line = raw.rstrip()
        if HR_RE.match(line):
            continue
        heading = HEADING_RE.match(line)
        if heading:
            text = BOLD_RE.sub(r"\1", heading.group(1)).strip()
            if text:
                out.append(f"<b>{_inline(text)}</b>")
            continue
        bullet = BULLET_RE.match(line)
        if bullet:
            line = "• " + line[bullet.end():]
        out.append(_inline(line))
    text = "\n".join(out)
    return re.sub(r"\n{3,}", "\n\n", text).strip()


def _inline(line: str) -> str:
    escaped = escape_html(line)
    escaped = LINK_RE.sub(lambda m: f'<a href="{m.group(2)}">{m.group(1)}</a>', escaped)
    escaped = BOLD_RE.sub(r"<b>\1</b>", escaped)
    escaped = CODE_RE.sub(r"<code>\1</code>", escaped)
    return escaped


def split_messages(text: str, limit: int = MESSAGE_LIMIT) -> list[str]:
    """Split rendered HTML on line boundaries, never inside a long line."""
    chunks: list[str] = []
    current: list[str] = []
    size = 0

    def flush() -> None:
        nonlocal current, size
        body = "\n".join(current).strip()
        if body:
            chunks.append(body)
        current, size = [], 0

    for line in text.splitlines():
        pieces = _hard_wrap(line, limit)
        for piece in pieces:
            extra = len(piece) + (1 if current else 0)
            if size + extra > limit:
                flush()
            current.append(piece)
            size += extra
    flush()
    return chunks


def _hard_wrap(line: str, limit: int) -> list[str]:
    """Break a single oversized line at spaces, keeping each piece under limit."""
    if len(line) <= limit:
        return [line]
    pieces: list[str] = []
    rest = line
    while len(rest) > limit:
        cut = rest.rfind(" ", 0, limit)
        if cut <= 0:
            cut = limit
        pieces.append(rest[:cut])
        rest = rest[cut:].lstrip()
    if rest:
        pieces.append(rest)
    return pieces


def release_url(repo: str, tag: str) -> str:
    return f"https://github.com/{repo}/releases/tag/{tag}"


def build_intro(tag: str, repo: str, prerelease: bool, asset_names: list[str]) -> str:
    label = "إصدار تجريبي" if prerelease else "إصدار جديد"
    lines = [
        f"🚀 <b>NexaFlow {escape_html(tag)}</b> — {label}",
        "",
        "تطبيق أتمتة أندرويد: مشغّلات وأفعال وقيود وسجل تنفيذ كامل.",
        "",
        f'📄 <a href="{release_url(repo, tag)}">سجل التغييرات الكامل على GitHub</a>',
        f'💬 <a href="https://t.me/NexaFlowCommunity">المجتمع والنقاش</a>'
        f' · <a href="https://t.me/NexaFlowApp">القناة</a>',
        "",
        "📦 الملفات المرفقة:",
    ]
    for name in asset_names:
        lines.append(f"• <code>{escape_html(name)}</code>")
    lines.append("")
    lines.append("سجل التغييرات كما نُشر على GitHub:")
    return "\n".join(lines)


def build_caption(name: str, tag: str) -> str:
    caption = f"<b>NexaFlow {escape_html(tag)}</b>\n<code>{escape_html(name)}</code>"
    return caption[:CAPTION_LIMIT]


# ---------------------------------------------------------------------------
# Transport
# ---------------------------------------------------------------------------

class TelegramClient:
    """Minimal Bot API client: sendMessage + sendDocument, with retries."""

    def __init__(self, token: str, base_url: str = API_BASE) -> None:
        if not token:
            raise TelegramError("TELEGRAM_BOT_TOKEN is empty")
        self._token = token
        self._base_url = base_url.rstrip("/")
        self.sent: list[str] = []

    def send_message(self, chat_id: str, text: str, topic_id: int | None = None) -> dict:
        payload = {
            "chat_id": chat_id,
            "text": text,
            "parse_mode": "HTML",
            # Bot API object parameters travel as JSON *strings*; a bare dict
            # would be urlencoded as a Python repr and rejected as malformed.
            "link_preview_options": json.dumps({"is_disabled": True}),
        }
        if topic_id is not None:
            payload["message_thread_id"] = topic_id
        return self._call("sendMessage", payload)

    def send_document(
        self,
        chat_id: str,
        path: Path,
        caption: str,
        topic_id: int | None = None,
    ) -> dict:
        fields = {"chat_id": chat_id, "caption": caption, "parse_mode": "HTML"}
        if topic_id is not None:
            fields["message_thread_id"] = str(topic_id)
        return self._call("sendDocument", fields, document=path)

    def _call(
        self,
        method: str,
        fields: dict,
        document: Path | None = None,
    ) -> dict:
        last_error = ""
        for attempt in range(1, MAX_ATTEMPTS + 1):
            try:
                body, content_type = _encode(fields, document)
                request = urllib.request.Request(
                    f"{self._base_url}/bot{self._token}/{method}",
                    data=body,
                    headers={"Content-Type": content_type},
                    method="POST",
                )
                with urllib.request.urlopen(request, timeout=60) as response:
                    reply = json.loads(response.read().decode("utf-8"))
                if reply.get("ok"):
                    self.sent.append(method)
                    return reply.get("result", {})
                last_error = str(reply.get("description") or reply)
            except urllib.error.HTTPError as exc:
                detail = _read_error(exc)
                if exc.code == 429:
                    wait = _retry_after(detail, exc.headers.get("Retry-After"))
                    print(f"telegram {method}: rate limited, retrying in {wait}s")
                    time.sleep(wait)
                    continue
                last_error = f"HTTP {exc.code}: {detail}"
                if exc.code < 500:
                    break  # 4xx (bad token, not admin, bad HTML) will not heal.
            except (urllib.error.URLError, TimeoutError, OSError) as exc:
                last_error = f"{type(exc).__name__}: {exc}"
            if attempt < MAX_ATTEMPTS:
                time.sleep(min(2 ** attempt, 10))
        raise TelegramError(f"{method} failed: {last_error}")


def _read_error(exc: urllib.error.HTTPError) -> str:
    try:
        payload = json.loads(exc.read().decode("utf-8"))
        return str(payload.get("description") or payload)
    except Exception:  # pragma: no cover - non-JSON gateway error page
        return exc.reason if isinstance(exc.reason, str) else str(exc.reason)


def _retry_after(detail: str, header: str | None) -> int:
    match = re.search(r"retry after (\d+)", detail, re.IGNORECASE)
    if match:
        return int(match.group(1))
    try:
        return max(int(header or 0), 1)
    except ValueError:
        return 3


def _encode(fields: dict, document: Path | None) -> tuple[bytes, str]:
    """Return a urlencoded body, or multipart when a file is attached."""
    if document is None:
        # Scalars only: callers serialize Bot API object parameters to JSON
        # first (see send_message), so urlencode never sees a container.
        return urllib.parse.urlencode(fields).encode("utf-8"), (
            "application/x-www-form-urlencoded"
        )
    boundary = f"----NexaFlow{uuid.uuid4().hex}"
    parts: list[bytes] = []
    for key, value in fields.items():
        parts.append(
            f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n'
            f"{value}\r\n".encode("utf-8")
        )
    parts.append(
        (
            f'--{boundary}\r\nContent-Disposition: form-data; name="document"; '
            f'filename="{document.name}"\r\n'
            "Content-Type: application/vnd.android.package-archive\r\n\r\n"
        ).encode("utf-8")
    )
    parts.append(document.read_bytes())
    parts.append(f"\r\n--{boundary}--\r\n".encode("utf-8"))
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


# ---------------------------------------------------------------------------
# Wiring
# ---------------------------------------------------------------------------

def targets_from_env(env: dict[str, str]) -> list[tuple[str, int | None]]:
    """(chat_id, topic_id) pairs, channel first. Missing channel is fatal."""
    channel = env.get("TELEGRAM_CHANNEL_CHAT_ID", "").strip()
    community = env.get("TELEGRAM_COMMUNITY_CHAT_ID", "").strip()
    topic_raw = env.get("TELEGRAM_COMMUNITY_TOPIC_ID", "").strip()
    if not channel:
        raise TelegramError("TELEGRAM_CHANNEL_CHAT_ID is not configured")
    topic: int | None = None
    if community and topic_raw:
        if not topic_raw.isdigit():
            raise TelegramError(
                f"TELEGRAM_COMMUNITY_TOPIC_ID must be numeric, got {topic_raw!r}"
            )
        topic = int(topic_raw)
    targets: list[tuple[str, int | None]] = [(channel, None)]
    if community:
        targets.append((community, topic))
    return targets


def publish(
    client: TelegramClient | None,
    targets: list[tuple[str, int | None]],
    *,
    tag: str,
    repo: str,
    intro: str,
    changelog: list[str],
    documents: list[tuple[Path, str]],
    dry_run: bool = False,
) -> None:
    for chat_id, topic_id in targets:
        label = f"{chat_id}" + (f"/{topic_id}" if topic_id else "")
        print(f"announcing {tag} to {label}")
        if dry_run:
            print(intro)
            print(f"[{len(changelog)} changelog message(s)]")
            for document, _ in documents:
                print(f"[document] {document}")
            continue
        assert client is not None
        client.send_message(chat_id, intro, topic_id)
        for chunk in changelog:
            client.send_message(chat_id, chunk, topic_id)
        for document, caption in documents:
            client.send_document(chat_id, document, caption, topic_id)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True, help="release tag, e.g. v3.91.16")
    parser.add_argument("--notes-file", required=True, help="release body (markdown)")
    parser.add_argument("--repo", default="Alaa91H/NexaFlow", help="owner/name")
    parser.add_argument("--phone", help="phone APK path")
    parser.add_argument("--wear", help="Wear OS APK path")
    parser.add_argument(
        "--prerelease", action="store_true", help="label the announcement as a preview"
    )
    parser.add_argument("--dry-run", action="store_true", help="print, do not send")
    args = parser.parse_args()

    notes = Path(args.notes_file).read_text(encoding="utf-8")
    if not notes.strip():
        raise SystemExit(f"empty release notes: {args.notes_file}")

    documents: list[tuple[Path, str]] = []
    for option in (args.phone, args.wear):
        if not option:
            continue
        path = Path(option)
        if not path.is_file() or path.stat().st_size == 0:
            raise SystemExit(f"missing or empty release asset: {path}")
        documents.append((path, build_caption(path.name, args.tag)))

    # The intro already announces the changelog, so the notes start directly:
    # a standalone "changelog" heading would just be another message.
    changelog = split_messages(markdown_to_html(notes))
    intro = build_intro(
        args.tag, args.repo, args.prerelease, [p.name for p, _ in documents]
    )

    try:
        targets = targets_from_env(os.environ)
        token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
        client = None if args.dry_run else TelegramClient(token)
        publish(
            client,
            targets,
            tag=args.tag,
            repo=args.repo,
            intro=intro,
            changelog=changelog,
            documents=documents,
            dry_run=args.dry_run,
        )
    except TelegramError as exc:
        raise SystemExit(f"telegram release announcement failed: {exc}") from exc

    print(f"TELEGRAM_ANNOUNCEMENT: ok ({args.tag})")


if __name__ == "__main__":
    main()
