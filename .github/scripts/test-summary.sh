#!/usr/bin/env bash
# Appends a Markdown table of JUnit XML results to the job summary, so a
# run's page shows how many tests ran on each leg without opening a log.
#
#   test-summary.sh "<heading>" <dir>...
#
# Every TEST-*.xml under the directories counts; nothing found is a row too.
set -u
heading="$1"; shift
{
    echo "### $heading"
    echo
    python3 - "$@" <<'PY'
import glob, os, sys
import xml.etree.ElementTree as ET
rows = []
for d in sys.argv[1:]:
    for path in sorted(glob.glob(os.path.join(d, "**", "TEST-*.xml"), recursive=True)):
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        if suite.tag != "testsuite":
            suite = suite.find("testsuite")
            if suite is None:
                continue
        name = suite.get("name", os.path.basename(path)).split(".")[-1]
        tests = int(suite.get("tests", 0)); fails = int(suite.get("failures", 0))
        errors = int(suite.get("errors", 0)); skipped = int(suite.get("skipped", 0))
        secs = float(suite.get("time", 0) or 0)
        ok = tests - fails - errors - skipped
        mark = "✅" if fails + errors == 0 else "❌"
        rows.append(f"| {mark} {name} | {tests} | {ok} | {fails + errors} | {skipped} | {secs:.1f} s |")
print("| Suite | Tests | Passed | Failed | Skipped | Time |")
print("|---|---:|---:|---:|---:|---:|")
print("\n".join(rows) if rows else "| (no results found) | | | | | |")
PY
    echo
} >> "${GITHUB_STEP_SUMMARY:-/dev/stdout}"
