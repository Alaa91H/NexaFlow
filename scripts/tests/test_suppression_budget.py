import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "check_suppression_budget.py"
spec = importlib.util.spec_from_file_location("suppression_budget", SCRIPT)
budget = importlib.util.module_from_spec(spec)
spec.loader.exec_module(budget)


class SuppressionBudgetTest(unittest.TestCase):
    def test_counts_suppress_and_suppress_lint_separately(self):
        source = '''
@Suppress("ComplexMethod")
@SuppressLint(
    "MissingPermission"
)
class Example
'''
        self.assertEqual(
            budget.count_annotations(source),
            {"Suppress": 1, "SuppressLint": 1},
        )

    def test_budget_rejects_any_increase_and_allows_equal_or_lower(self):
        baseline = {"Suppress": 10, "SuppressLint": 4}
        self.assertEqual(budget.budget_violations({"Suppress": 10, "SuppressLint": 3}, baseline), [])
        self.assertEqual(
            budget.budget_violations({"Suppress": 11, "SuppressLint": 4}, baseline),
            ["Suppress count 11 exceeds budget 10"],
        )


if __name__ == "__main__":
    unittest.main()
