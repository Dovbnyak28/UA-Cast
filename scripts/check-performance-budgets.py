"""Fail closed on incomplete/non-finite AndroidX Macrobenchmark output; stdlib only.

Absolute budgets apply to the fixed API-30 CI emulator, not to every physical device.
Per-iteration peak memory is checked at its worst run, durations by median, frames by P95.
"""
import argparse
import json
import math
from pathlib import Path
import statistics
import sys


def checked_number(value):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ValueError("Missing/non-finite numeric measurement")
    if value < 0:
        raise ValueError("Negative measurement")
    return float(value)


def evaluate(documents, budgets):
    records = {}
    expected_api = budgets.get("apiLevel")
    if expected_api is not None and (type(expected_api) is not int or expected_api < 1):
        raise ValueError("Invalid configured API level")
    for document in documents:
        if expected_api is not None:
            try:
                actual_api = document["context"]["build"]["version"]["sdk"]
            except (KeyError, TypeError):
                raise ValueError("Benchmark evidence is missing its API level") from None
            if type(actual_api) is not int or actual_api != expected_api:
                raise ValueError(f"Benchmark API mismatch: expected {expected_api}, got {actual_api}")
        for row in document.get("benchmarks", []):
            identity = (row.get("className", "").split(".")[-1], row.get("name"))
            if identity in records:
                raise ValueError(f"Duplicate benchmark: {identity}")
            records[identity] = row
    report = []
    failures = []
    for case in budgets["cases"]:
        identity = (case["class"], case["test"])
        row = records.get(identity)
        if row is None:
            raise ValueError(f"Required benchmark absent: {identity}")
        for rule in case["metrics"]:
            name = rule["name"]
            if rule["statistic"] not in ("median", "max", "p95"):
                raise ValueError(f"Unknown statistic: {rule['statistic']}")
            if rule["statistic"] == "p95":
                metric = row.get("sampledMetrics", {}).get(name, {})
                # AndroidX Benchmark 1.4.1 SampledMetricResult serializes P50/P90/P95/P99.
                # Do not substitute a mean or silently accept another JSON schema.
                value = checked_number(metric.get("P95"))
                runs = metric.get("runs", [])
                if len(runs) < case["minIterations"] or any(not run for run in runs):
                    raise ValueError(f"Incomplete frame samples: {identity} {name}")
                for run in runs:
                    for sample in run:
                        checked_number(sample)
            else:
                runs = row.get("metrics", {}).get(name, {}).get("runs", [])
                if len(runs) < case["minIterations"]:
                    raise ValueError(f"Incomplete runs: {identity} {name}")
                values = [checked_number(run) for run in runs]
                value = max(values) if rule["statistic"] == "max" else statistics.median(values)
            if value == 0:
                raise ValueError(f"Empty/zero measurement: {identity} {name}")
            limit = checked_number(rule["limit"])
            line = f"{identity[0]}.{identity[1]} {name}/{rule['statistic']}={value:.3f}; budget={limit:.3f}"
            report.append(line)
            if value > limit:
                failures.append(line)
    return report, failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("results", type=Path)
    parser.add_argument("--budgets", type=Path, default=Path("config/performance/ci-api30.json"))
    args = parser.parse_args()
    try:
        paths = list(args.results.rglob("*-benchmarkData.json"))
        if not paths:
            raise ValueError("No benchmark JSON found; an interrupted run is not a pass")
        documents = [json.loads(path.read_text(encoding="utf-8")) for path in paths]
        report, failures = evaluate(documents, json.loads(args.budgets.read_text(encoding="utf-8")))
        print("\n".join(report))
        if failures:
            print("PERFORMANCE BUDGET EXCEEDED", file=sys.stderr)
            return 1
        print("All required performance budgets passed")
        return 0
    except (OSError, ValueError, TypeError, KeyError) as error:
        print(f"Invalid/incomplete performance evidence: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
