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
        tests = ok = failed = skipped = 0
        for case in suite.iter("testcase"):
            tests += 1
            problem = case.find("failure") if case.find("failure") is not None else case.find("error")
            if case.find("skipped") is not None:
                skipped += 1
            elif problem is not None:
                # The Android runner files a test that bailed on an
                # assumption (assumeFalse) as a failure; it is a skip.
                if "AssumptionViolatedException" in (problem.text or "") + (problem.get("type") or ""):
                    skipped += 1
                else:
                    failed += 1
            else:
                ok += 1
        secs = float(suite.get("time", 0) or 0)
        mark = "✅" if failed == 0 else "❌"
        rows.append(f"| {mark} {name} | {tests} | {ok} | {failed} | {skipped} | {secs:.1f} s |")
print("| Suite | Tests | Passed | Failed | Skipped | Time |")
print("|---|---:|---:|---:|---:|---:|")
print("\n".join(rows) if rows else "| (no results found) | | | | | |")
PY
    echo
} >> "${GITHUB_STEP_SUMMARY:-/dev/stdout}"
