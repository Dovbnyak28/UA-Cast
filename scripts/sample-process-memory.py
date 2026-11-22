"""Sample Android process PSS alongside benchmarks, including Java/native/graphics PSS.

Sampled maxima are lower bounds, not allocation counts or proof of a true instantaneous peak.
Only numeric memory data is saved; no window hierarchy, providers, URLs or application files.
"""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import signal
import subprocess
import time


def parse_meminfo(output):
    if "No process found" in output:
        return None
    summary = output.split("App Summary", 1)
    if len(summary) != 2:
        raise ValueError("Missing App Summary")
    values = {}
    for label, key in [("Java Heap", "javaPssKb"), ("Native Heap", "nativePssKb"), ("Graphics", "graphicsPssKb")]:
        match = re.search(r"^\s*" + label + r":\s*(\d+)\b", summary[1], re.MULTILINE)
        if not match:
            raise ValueError("Missing " + label)
        values[key] = int(match[1])
    total = re.search(r"\bTOTAL(?: PSS)?:\s*(\d+)\b", summary[1])
    if not total or int(total[1]) <= 0:
        raise ValueError("Missing/zero total PSS")
    values["totalPssKb"] = int(total[1])
    return values


def summarize(samples):
    if not samples:
        raise ValueError("No valid process memory samples")
    return {key: max(row[key] for row in samples) for key in samples[0]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default="app.zorik.player")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--interval-ms", type=int, default=1000)
    parser.add_argument("--duration", type=int, default=2700)
    parser.add_argument("--min-samples", type=int, default=10)
    parser.add_argument("--stop-file", type=Path, help="Optional cross-platform stop signal owned by the caller")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_.]+", args.package):
        parser.error("Invalid package name")
    if args.interval_ms < 500 or not 1 <= args.duration <= 7200 or args.min_samples < 1:
        parser.error("Invalid sampling bounds")
    if args.stop_file is not None and args.stop_file.exists():
        parser.error("Stop file already exists")
    stopped = False

    def stop(_signal, _frame):
        nonlocal stopped
        stopped = True

    for signum in (signal.SIGINT, signal.SIGTERM):
        signal.signal(signum, stop)
    started = time.monotonic()
    samples, unavailable, errors = [], 0, 0
    while not stopped and time.monotonic() - started < args.duration:
        if args.stop_file is not None and args.stop_file.exists():
            break
        try:
            result = subprocess.run(
                [args.adb, "-s", args.serial, "shell", "dumpsys", "meminfo", args.package],
                check=True, capture_output=True, text=True, timeout=5,
            )
            sample = parse_meminfo(result.stdout)
            if sample is None:
                unavailable += 1
            else:
                samples.append(sample)
        except (subprocess.SubprocessError, OSError, ValueError):
            errors += 1
        time.sleep(args.interval_ms / 1000)
    document = {
        "createdUtc": datetime.now(timezone.utc).isoformat(),
        "package": args.package, "intervalMs": args.interval_ms,
        "sampleCount": len(samples), "processUnavailable": unavailable, "readErrors": errors,
        "sampledMaxima": summarize(samples) if samples else {},
        "samples": samples,
        "limitation": "Sampled maxima are lower bounds; native/graphics values are PSS, not malloc or allocation counts.",
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
    if len(samples) < args.min_samples:
        raise SystemExit("Incomplete process memory evidence")
    print(json.dumps(document["sampledMaxima"]))


if __name__ == "__main__":
    main()
