import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "check_readme_stale_counts.py"
spec = importlib.util.spec_from_file_location("readme_stale_counts", SCRIPT)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class ReadmeStaleCountsTest(unittest.TestCase):
    def test_fixed_catalog_and_schema_counts_are_rejected(self):
        self.assertEqual(
            audit.find_stale_count_claims("There are 57 trigger entries and 180 action entries."),
            ["There are 57 trigger entries and 180 action entries."],
        )
        self.assertEqual(
            audit.find_stale_count_claims("Room database schema 25."),
            ["Room database schema 25."],
        )

    def test_system_requirements_are_not_count_claims(self):
        self.assertEqual(audit.find_stale_count_claims("Android 8.0 / API 26 minimum; Java 17 or newer."), [])


if __name__ == "__main__":
    unittest.main()
