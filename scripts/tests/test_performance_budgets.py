import importlib.util
import json
from pathlib import Path
import unittest

MODULE = Path(__file__).resolve().parents[1] / "check-performance-budgets.py"
SPEC = importlib.util.spec_from_file_location("performance_gate", MODULE)
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class PerformanceBudgetTest(unittest.TestCase):
    def setUp(self):
        self.budgets = {"cases": [{"class": "Sample", "test": "journey", "minIterations": 2,
                                  "metrics": [{"name": "memory", "statistic": "max", "limit": 100}]}]}
        self.doc = {"benchmarks": [{"className": "com.example.Sample", "name": "journey",
                                    "metrics": {"memory": {"runs": [10, 90]}}}]}

    def test_success(self):
        self.assertFalse(GATE.evaluate([self.doc], self.budgets)[1])

    def test_peak_not_hidden_by_median(self):
        self.doc["benchmarks"][0]["metrics"]["memory"]["runs"] = [1, 200]
        self.assertTrue(GATE.evaluate([self.doc], self.budgets)[1])

    def test_missing_or_interrupted_runs_fail(self):
        for docs in ([], [{"benchmarks": []}]):
            with self.assertRaises(ValueError):
                GATE.evaluate(docs, self.budgets)
        self.doc["benchmarks"][0]["metrics"]["memory"]["runs"] = [10]
        with self.assertRaises(ValueError):
            GATE.evaluate([self.doc], self.budgets)

    def test_duplicate_result_is_not_silently_selected(self):
        with self.assertRaises(ValueError):
            GATE.evaluate([self.doc, self.doc], self.budgets)

    def test_nonfinite_and_boolean_numbers_fail(self):
        for value in (float("nan"), float("inf"), True, "20", -1, 0):
            self.doc["benchmarks"][0]["metrics"]["memory"]["runs"] = [value, value]
            with self.assertRaises(ValueError):
                GATE.evaluate([self.doc], self.budgets)

    def test_p95_and_frame_runs_are_required(self):
        rule = self.budgets["cases"][0]["metrics"][0]
        rule.update(name="frames", statistic="p95")
        self.doc["benchmarks"][0]["sampledMetrics"] = {"frames": {"P95": 99, "runs": [[1], [99]]}}
        self.assertFalse(GATE.evaluate([self.doc], self.budgets)[1])
        del self.doc["benchmarks"][0]["sampledMetrics"]["frames"]["P95"]
        with self.assertRaises(ValueError):
            GATE.evaluate([self.doc], self.budgets)

    def test_one_negative_run_cannot_be_hidden_by_a_positive_peak(self):
        self.doc["benchmarks"][0]["metrics"]["memory"]["runs"] = [-1, 90]
        with self.assertRaises(ValueError):
            GATE.evaluate([self.doc], self.budgets)

    def test_all_eight_configured_journeys_require_real_metric_names(self):
        # Synthetic schema contract only: these are not measured device results.
        config = MODULE.parent.parent / "config/performance/ci-api35.json"
        budgets = json.loads(config.read_text(encoding="utf-8"))
        rows = []
        for case in budgets["cases"]:
            row = {"className": case["class"], "name": case["test"], "metrics": {}, "sampledMetrics": {}}
            for rule in case["metrics"]:
                if rule["statistic"] == "p95":
                    row["sampledMetrics"][rule["name"]] = {"P95": 1, "runs": [[1]] * case["minIterations"]}
                else:
                    row["metrics"][rule["name"]] = {"runs": [1] * case["minIterations"]}
            rows.append(row)
        self.assertEqual(8, len(rows))
        self.assertFalse(GATE.evaluate([{"benchmarks": rows}], budgets)[1])
        rows[-1]["metrics"].pop("UaCastEpgParseAndIndexSumMs")
        with self.assertRaises(ValueError):
            GATE.evaluate([{"benchmarks": rows}], budgets)


if __name__ == "__main__":
    unittest.main()
