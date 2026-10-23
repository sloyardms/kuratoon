#!/usr/bin/env python3
"""Markdown summary of the backend test reports, for $GITHUB_STEP_SUMMARY.

Reads every Surefire/Failsafe XML report (TEST-*.xml) under <module>/target/{surefire,failsafe}-reports
and prints tests run / failed / errors / skipped per module, then each skipped test by name, so a
skipped test is visible without opening the log.

Usage: python3 .github/scripts/test-summary.py <backend dir>
"""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    rows = []
    skipped = []
    for module in sorted(p for p in root.iterdir() if (p / "target").is_dir()):
        totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
        for kind in ("surefire-reports", "failsafe-reports"):
            for report in sorted((module / "target" / kind).glob("TEST-*.xml")):
                suite = ET.parse(report).getroot()
                for key in totals:
                    totals[key] += int(suite.get(key, "0"))
                for case in suite.iter("testcase"):
                    mark = case.find("skipped")
                    if mark is not None:
                        reason = (mark.get("message") or "").strip()
                        name = f"{case.get('classname', '').rsplit('.', 1)[-1]}.{case.get('name')}"
                        skipped.append((module.name, name, reason))
        if totals["tests"]:
            rows.append((module.name, totals))

    print("### Backend tests\n")
    if not rows:
        print("No test reports found.")
        return 0
    print("| Module | Run | Failed | Errors | Skipped |")
    print("|---|---:|---:|---:|---:|")
    for name, t in rows:
        print(f"| {name} | {t['tests']} | {t['failures']} | {t['errors']} | {t['skipped']} |")
    print()
    if skipped:
        print("**Skipped tests**\n")
        for module, name, reason in skipped:
            print(f"- `{module}` {name}" + (f": {reason}" if reason else ""))
    else:
        print("No skipped tests.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
