from __future__ import annotations

import unittest

from scripts.check_canonical_typed_model import strip_kotlin_comments


class CanonicalTypedModelGateTest(unittest.TestCase):
    def test_kdoc_mentions_do_not_count_as_code_coupling(self) -> None:
        source = """
        /**
         * Legacy TriggerType and ActionType are compatibility concerns.
         */
        data class Clean(val value: String)
        """
        stripped = strip_kotlin_comments(source)
        self.assertNotIn("TriggerType", stripped)
        self.assertNotIn("ActionType", stripped)
        self.assertIn("data class Clean", stripped)

    def test_real_type_usage_survives_comment_stripping(self) -> None:
        source = """
        // TriggerType in a line comment must disappear.
        val type: TriggerType = TriggerType.TIME
        """
        stripped = strip_kotlin_comments(source)
        self.assertIn("TriggerType", stripped)


if __name__ == "__main__":
    unittest.main()
