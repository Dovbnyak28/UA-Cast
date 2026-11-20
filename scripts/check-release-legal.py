#!/usr/bin/env python3
"""Fail closed on known release compliance blockers; not a legal certification."""

import argparse
import json
from pathlib import Path
import re
import sys

REQUIRED = {"decoder_license_migration", "native_corresponding_source", "raster_asset_rights",
            "epg_source_permissions", "public_privacy_deployment", "cast_entry_point", "historical_gpl_distribution"}
FORBIDDEN_RUNTIME = {"io.github.anilbeesetti:nextlib-media3ext",
                     "io.github.anilbeesetti:nextlib-mediainfo"}


def assess(readiness, dependencies, store_live=False):
    failures = []
    if not isinstance(readiness, dict) or not isinstance(dependencies, str):
        return ["Invalid legal-readiness or dependency-report type"]
    if readiness.get("schemaVersion") != 1:
        failures.append("Unsupported legal-readiness schema")
    checks = readiness.get("checks", {})
    if not isinstance(checks, dict):
        checks = {}
    for name in sorted(REQUIRED):
        item = checks.get(name, {})
        if (not isinstance(item, dict) or item.get("approved") is not True or
                not isinstance(item.get("evidence"), str) or not item["evidence"].strip()):
            failures.append(f"Legal check not approved with evidence: {name}")
    if "RuntimeClasspath" not in dependencies or "BUILD SUCCESSFUL" not in dependencies:
        failures.append("Dependency report is missing or incomplete")
    for coordinate in sorted(FORBIDDEN_RUNTIME):
        if re.search(re.escape(coordinate) + r":", dependencies):
            failures.append(f"GPL wrapper remains runtime-linked: {coordinate}")
    commerce = readiness.get("commerce", {})
    if store_live and (not isinstance(commerce, dict) or commerce.get("enabled") is not True or
                       commerce.get("sellerReviewApproved") is not True or
                       not isinstance(commerce.get("evidence"), str) or
                       not commerce["evidence"].strip()):
        failures.append("Checkout is live without approved seller/market review")
    return failures


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dependency-report", required=True, type=Path)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    try:
        readiness = json.loads((args.root / "config/legal/release-readiness.json").read_text(encoding="utf-8"))
        dependencies = args.dependency_report.read_text(encoding="utf-8-sig")
        store_source = (args.root / "app/src/main/kotlin/com/uacastplayer/premium/PremiumAvailability.kt")
        store_live = bool(re.search(r"STORE_IS_LIVE\s*=\s*true", store_source.read_text(encoding="utf-8")))
        failures = assess(readiness, dependencies, store_live)
    except (OSError, ValueError, TypeError) as error:
        failures = [f"Cannot verify legal release inputs: {type(error).__name__}"]
    for failure in failures:
        print(failure, file=sys.stderr)
    if failures:
        print("Release blocked; do not publish these artifacts", file=sys.stderr)
        return 1
    print("Release compliance checks passed; this is not a legal opinion")
    return 0


if __name__ == "__main__":
    sys.exit(main())
