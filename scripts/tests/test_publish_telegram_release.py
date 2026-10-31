"""Tests for the Telegram release announcer (scripts/publish_telegram_release.py).

The announcer is the last surface between the repository and subscribers: a
broken chunk, an unescaped changelog angle bracket or a missing chat target
must fail here, not in a tag run after the release is already public.
"""
from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
import urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "publish_telegram_release", ROOT / "scripts" / "publish_telegram_release.py"
)
assert SPEC and SPEC.loader
tg = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(tg)

NOTES = """### Added

- **AGENT_RUN** action: run a saved agent from a task.
- Links to [the channel](https://t.me/NexaFlowApp).

### Fixed

- A `<setting>` name with & ampersand no longer breaks the message.
"""


class MarkdownTests(unittest.TestCase):
    def test_headings_become_bold_and_bullets_keep_their_text(self):
        rendered = tg.markdown_to_html(NOTES)
        self.assertIn("<b>Added</b>", rendered)
        self.assertIn("• ", rendered)
        self.assertIn("AGENT_RUN", rendered)

    def test_angle_brackets_and_ampersands_are_escaped(self):
        rendered = tg.markdown_to_html(NOTES)
        self.assertIn("&lt;setting&gt;", rendered)
        self.assertIn("&amp; ampersand", rendered)
        self.assertNotIn("<setting>", rendered)

    def test_links_become_anchors(self):
        rendered = tg.markdown_to_html(NOTES)
        self.assertIn('<a href="https://t.me/NexaFlowApp">the channel</a>', rendered)

    def test_horizontal_rules_are_dropped(self):
        self.assertNotIn("---", tg.markdown_to_html("---\n### Title"))


class SplitTests(unittest.TestCase):
    def test_every_line_survives_and_each_chunk_fits(self):
        rendered = "\n".join(f"- line {i} " + "x" * 40 for i in range(60))
        chunks = tg.split_messages(rendered, limit=500)
        self.assertGreater(len(chunks), 1)
        for chunk in chunks:
            self.assertLessEqual(len(chunk), 500)
        joined = "\n".join(chunks)
        for i in range(60):
            self.assertIn(f"line {i}", joined)

    def test_one_oversized_line_is_wrapped_not_truncated(self):
        line = " ".join(["word"] * 400)
        chunks = tg.split_messages(line, limit=300)
        self.assertGreater(len(chunks), 1)
        for chunk in chunks:
            self.assertLessEqual(len(chunk), 300)
        self.assertEqual(len(" ".join(chunks).split()), 400)


class MessageTests(unittest.TestCase):
    def test_intro_names_the_tag_links_the_release_and_lists_assets(self):
        intro = tg.build_intro(
            "v3.91.16",
            "Alaa91H/NexaFlow",
            False,
            ["NexaFlow-v3.91.16.apk", "NexaFlow-Wear-v3.91.16.apk"],
        )
        self.assertIn("NexaFlow v3.91.16", intro)
        self.assertIn(
            "https://github.com/Alaa91H/NexaFlow/releases/tag/v3.91.16", intro
        )
        self.assertIn("NexaFlow-Wear-v3.91.16.apk", intro)

    def test_prerelease_is_labelled(self):
        intro = tg.build_intro("v3.92.0-rc1", "Alaa91H/NexaFlow", True, [])
        self.assertIn("إصدار تجريبي", intro)


class TargetTests(unittest.TestCase):
    def test_channel_is_required(self):
        with self.assertRaises(tg.TelegramError):
            tg.targets_from_env({"TELEGRAM_COMMUNITY_CHAT_ID": "@NexaFlowCommunity"})

    def test_channel_and_community_topic(self):
        targets = tg.targets_from_env(
            {
                "TELEGRAM_CHANNEL_CHAT_ID": "@NexaFlowApp",
                "TELEGRAM_COMMUNITY_CHAT_ID": "@NexaFlowCommunity",
                "TELEGRAM_COMMUNITY_TOPIC_ID": "4",
            }
        )
        self.assertEqual(targets, [("@NexaFlowApp", None), ("@NexaFlowCommunity", 4)])

    def test_non_numeric_topic_fails_loudly(self):
        with self.assertRaises(tg.TelegramError):
            tg.targets_from_env(
                {
                    "TELEGRAM_CHANNEL_CHAT_ID": "@NexaFlowApp",
                    "TELEGRAM_COMMUNITY_CHAT_ID": "@NexaFlowCommunity",
                    "TELEGRAM_COMMUNITY_TOPIC_ID": "general",
                }
            )

    def test_channel_only_when_community_is_not_configured(self):
        self.assertEqual(
            tg.targets_from_env({"TELEGRAM_CHANNEL_CHAT_ID": "@NexaFlowApp"}),
            [("@NexaFlowApp", None)],
        )


class EncodeTests(unittest.TestCase):
    def test_link_preview_options_travel_as_parseable_json(self):
        # The Bot API rejects a urlencoded Python repr; this failed for real in
        # the first live announcement, so pin the wire format.
        calls = []

        class Capture(tg.TelegramClient):
            def _call(self, method, fields, document=None):  # noqa: D401
                body, content_type = tg._encode(fields, document)
                calls.append((method, fields, body, content_type))
                return {}

        client = Capture("token")
        client.send_message("@NexaFlowApp", "hello")

        method, fields, body, content_type = calls[0]
        self.assertEqual(method, "sendMessage")
        self.assertEqual(content_type, "application/x-www-form-urlencoded")
        parsed = urllib.parse.parse_qs(body.decode("utf-8"))
        self.assertEqual(
            json.loads(parsed["link_preview_options"][0]), {"is_disabled": True}
        )

    def test_topic_id_is_sent_as_message_thread_id(self):
        calls = []

        class Capture(tg.TelegramClient):
            def _call(self, method, fields, document=None):
                calls.append(fields)
                return {}

        Capture("token").send_message("@NexaFlowCommunity", "hello", 4)
        self.assertEqual(calls[0]["message_thread_id"], 4)


class PublishTests(unittest.TestCase):
    def test_dry_run_sends_nothing(self):
        class Exploding:
            def send_message(self, *args, **kwargs):  # pragma: no cover
                raise AssertionError("dry run must not send messages")

            def send_document(self, *args, **kwargs):  # pragma: no cover
                raise AssertionError("dry run must not send documents")

        tg.publish(
            Exploding(),
            [("@NexaFlowApp", None)],
            tag="v3.91.16",
            repo="Alaa91H/NexaFlow",
            intro="intro",
            changelog=["notes"],
            documents=[],
            dry_run=True,
        )

    def test_real_publish_orders_intro_changelog_then_documents(self):
        class Recorder:
            def __init__(self):
                self.calls = []

            def send_message(self, chat_id, text, topic_id=None):
                self.calls.append(("message", chat_id, text, topic_id))

            def send_document(self, chat_id, path, caption, topic_id=None):
                self.calls.append(("document", chat_id, path.name, topic_id))

        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "NexaFlow-v3.91.16.apk"
            apk.write_bytes(b"apk")
            recorder = Recorder()
            tg.publish(
                recorder,
                [("@NexaFlowApp", None), ("@NexaFlowCommunity", 4)],
                tag="v3.91.16",
                repo="Alaa91H/NexaFlow",
                intro="intro",
                changelog=["notes"],
                documents=[(apk, "caption")],
            )

        kinds = [call[0] for call in recorder.calls]
        self.assertEqual(
            kinds, ["message", "message", "document"] * 2
        )
        self.assertEqual(recorder.calls[3][3], 4)
        self.assertEqual(recorder.calls[5][2], "NexaFlow-v3.91.16.apk")


class AssetValidationTests(unittest.TestCase):
    def test_missing_apk_is_rejected(self):
        # The CLI refuses a missing or empty asset before touching Telegram.
        with tempfile.TemporaryDirectory() as tmp:
            empty = Path(tmp) / "NexaFlow-v3.91.16.apk"
            empty.write_bytes(b"")
            import subprocess
            import sys

            result = subprocess.run(
                [
                    sys.executable,
                    str(ROOT / "scripts" / "publish_telegram_release.py"),
                    "--tag",
                    "v3.91.16",
                    "--notes-file",
                    str(ROOT / "CHANGELOG.md"),
                    "--phone",
                    str(empty),
                    "--dry-run",
                ],
                capture_output=True,
                text=True,
                env={"TELEGRAM_CHANNEL_CHAT_ID": "@NexaFlowApp", "PATH": "/usr/bin:/bin"},
                check=False,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("missing or empty release asset", result.stderr)


if __name__ == "__main__":
    unittest.main()
