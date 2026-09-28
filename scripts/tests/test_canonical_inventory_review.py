from __future__ import annotations

import pathlib
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))

from canonical_inventory_review import ACTION_REVIEWS, TRIGGER_REVIEWS  # noqa: E402


class CanonicalInventoryReviewTest(unittest.TestCase):
    def test_frozen_legacy_counts_are_fully_reviewed(self) -> None:
        self.assertEqual(57, len(TRIGGER_REVIEWS))
        self.assertEqual(176, len(ACTION_REVIEWS))

    def test_review_vocabulary_is_closed(self) -> None:
        selections = {"SINGLE", "MULTI"}
        combinations = {"N/A", "ANY", "ALL", "ANY_OF", "BATCH", "ORDERED"}
        side_effects = {"NONE", "REVERSIBLE", "IRREVERSIBLE", "EXTERNAL"}
        idempotency = {"N/A", "IDEMPOTENT", "NON_IDEMPOTENT", "CONDITIONAL"}
        retry = {"N/A", "SAFE", "UNSAFE", "CONDITIONAL"}

        for name, review in {**TRIGGER_REVIEWS, **ACTION_REVIEWS}.items():
            with self.subTest(name=name):
                self.assertIn(review.selectionMode, selections)
                self.assertIn(review.combinationMode, combinations)
                self.assertIn(review.sideEffect, side_effects)
                self.assertIn(review.idempotency, idempotency)
                self.assertIn(review.retrySafety, retry)
                self.assertTrue(review.canonicalTarget.startswith(("core.", "plugin.")))
                self.assertTrue(review.canonicalOperation)
                self.assertEqual("REVIEWED", review.reviewStatus)

    def test_multi_selection_always_has_explicit_combination_semantics(self) -> None:
        for name, review in {**TRIGGER_REVIEWS, **ACTION_REVIEWS}.items():
            if review.selectionMode == "MULTI":
                with self.subTest(name=name):
                    self.assertNotEqual("N/A", review.combinationMode)

    def test_triggers_never_claim_execution_side_effects(self) -> None:
        for name, review in TRIGGER_REVIEWS.items():
            with self.subTest(name=name):
                self.assertEqual("NONE", review.sideEffect)
                self.assertEqual("N/A", review.idempotency)
                self.assertEqual("N/A", review.retrySafety)

    def test_settings_aliases_converge_on_one_canonical_operation(self) -> None:
        # Classify by reviewed semantics, never by legacy-name substrings:
        # SYSTEM_OPEN_QUICK_SETTINGS is navigation, not an Android Settings page.
        settings = {
            name: review
            for name, review in ACTION_REVIEWS.items()
            if review.canonicalTarget == "core.system.settings"
        }
        self.assertGreaterEqual(len(settings), 20)
        for name, review in settings.items():
            with self.subTest(name=name):
                self.assertEqual("OPEN", review.canonicalOperation)
                self.assertIn("page=", review.migrationNotes)

    def test_existing_semantic_router_actions_keep_the_same_intent(self) -> None:
        expected = {
            "SYSTEM_WIFI": ("core.connectivity.wifi", "SET_STATE"),
            "SYSTEM_BLUETOOTH": ("core.connectivity.bluetooth", "SET_STATE"),
            "SYSTEM_LOCATION": ("core.location.service", "SET_STATE"),
            "SYSTEM_AIRPLANE_MODE": ("core.connectivity.airplane_mode", "SET_STATE"),
            "SYSTEM_SCREEN_ROTATION": ("core.display.auto_rotate", "SET_STATE"),
            "SYSTEM_BRIGHTNESS": ("core.display.brightness", "SET_VALUE"),
            "SYSTEM_SCREEN_TIMEOUT": ("core.display.screen_timeout", "SET_VALUE"),
            "SYSTEM_DND": ("core.audio.dnd", "SET_STATE"),
            "SYSTEM_NFC": ("core.connectivity.nfc", "SET_STATE"),
            "SYSTEM_HOTSPOT": ("core.connectivity.hotspot", "SET_STATE"),
            "SYSTEM_MOBILE_DATA": ("core.connectivity.mobile_data", "SET_STATE"),
            "SYSTEM_DATA_SAVER": ("core.connectivity.data_saver", "SET_STATE"),
            "APPLICATION_CLOSE_APP": ("core.application.package", "FORCE_STOP"),
            "SYSTEM_FORCE_STOP_APP": ("core.application.package", "FORCE_STOP"),
            "SYSTEM_CLEAR_APP_DATA": ("core.application.package", "CLEAR_DATA"),
            "SYSTEM_DISABLE_APP": ("core.application.package", "SET_ENABLED"),
            "SYSTEM_ENABLE_APP": ("core.application.package", "SET_ENABLED"),
        }
        self.assertEqual(17, len(expected))
        for name, (target, operation) in expected.items():
            with self.subTest(name=name):
                review = ACTION_REVIEWS[name]
                self.assertEqual(target, review.canonicalTarget)
                self.assertEqual(operation, review.canonicalOperation)


if __name__ == "__main__":
    unittest.main()
