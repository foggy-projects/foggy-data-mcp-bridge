#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/assert-v933-red-report.sh REPORT_DIR EXPECTED_FQCN EXPECTED_TESTS [EXPECTED_PATTERN ...]

Accept a pre-fix red baseline only when REPORT_DIR contains exactly one fresh
TEST-*.xml suite owned by EXPECTED_FQCN, with the exact test count, every case
failing by assertion, and zero errors/skips. Every EXPECTED_PATTERN, when
present, must occur in that report. Set V933_RUN_MARKER to reject stale XML.

This script proves that the frozen contract currently fails. It is not a green
product gate and must never be used to turn a normal Maven test lane green.
USAGE
}

fail() {
  echo "[v933-red-report] ERROR: $*" >&2
  exit 1
}

[[ "$#" -ge 3 ]] || {
  usage >&2
  exit 2
}

REPORT_DIR="$1"
EXPECTED_FQCN="$2"
EXPECTED_TESTS="$3"
shift 3
EXPECTED_PATTERNS=("$@")
RUN_MARKER="${V933_RUN_MARKER:-}"

[[ "$EXPECTED_TESTS" =~ ^[1-9][0-9]*$ ]] || fail "EXPECTED_TESTS must be a positive integer"
[[ -z "$RUN_MARKER" || -f "$RUN_MARKER" ]] || fail "run marker does not exist: $RUN_MARKER"
[[ -d "$REPORT_DIR" ]] || fail "report directory does not exist: $REPORT_DIR"

mapfile -d '' REPORTS < <(find "$REPORT_DIR" -maxdepth 1 -type f -name 'TEST-*.xml' -print0 | sort -z)
[[ "${#REPORTS[@]}" -eq 1 ]] || fail "expected exactly one TEST-*.xml, found ${#REPORTS[@]}"

REPORT="${REPORTS[0]}"
[[ -z "$RUN_MARKER" || "$REPORT" -nt "$RUN_MARKER" ]] || \
  fail "report is not newer than run marker: $REPORT"
EXPECTED_FILE="TEST-${EXPECTED_FQCN}.xml"
[[ "$(basename "$REPORT")" == "$EXPECTED_FILE" ]] || \
  fail "expected $EXPECTED_FILE, found $(basename "$REPORT")"

SUITE_TAG="$(grep -o -m1 '<testsuite[^>]*>' "$REPORT" || true)"
[[ -n "$SUITE_TAG" ]] || fail "testsuite element missing: $REPORT"

attribute() {
  local name="$1"
  sed -n "s/.* ${name}=\"\([^\"]*\)\".*/\1/p" <<<"$SUITE_TAG"
}

TESTS="$(attribute tests)"
FAILURES="$(attribute failures)"
ERRORS="$(attribute errors)"
SKIPPED="$(attribute skipped)"

[[ "$TESTS" == "$EXPECTED_TESTS" ]] || fail "tests=$TESTS, expected $EXPECTED_TESTS"
[[ "$FAILURES" == "$EXPECTED_TESTS" ]] || \
  fail "failures=$FAILURES, expected every one of $EXPECTED_TESTS case(s) to fail"
[[ "$ERRORS" == "0" ]] || fail "errors=$ERRORS; fixture/infrastructure errors are not an accepted red baseline"
[[ "$SKIPPED" == "0" ]] || fail "skipped=$SKIPPED"

TESTCASE_COUNT="$(grep -o "classname=\"${EXPECTED_FQCN}\"" "$REPORT" | wc -l)"
[[ "$TESTCASE_COUNT" == "$EXPECTED_TESTS" ]] || \
  fail "expected $EXPECTED_TESTS testcase(s) for $EXPECTED_FQCN, found $TESTCASE_COUNT"

for expected_pattern in "${EXPECTED_PATTERNS[@]}"; do
  grep -Fq -- "$expected_pattern" "$REPORT" || \
    fail "expected report pattern not found: $expected_pattern"
done

echo "[v933-red-report] EXPECTED_RED class=$EXPECTED_FQCN tests=$TESTS failures=$FAILURES errors=0 skipped=0"
