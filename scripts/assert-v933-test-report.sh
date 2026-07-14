#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/assert-v933-test-report.sh REPORT_DIR EXPECTED_FQCN [EXPECTED_TESTS]

Fail closed unless REPORT_DIR contains exactly one TEST-*.xml suite for the
expected owning-module class, with the exact test count and zero failures,
errors, or skipped tests. If V933_RUN_MARKER is set, the report must be newer
than that marker so a stale XML cannot satisfy the assertion.
USAGE
}

fail() {
  echo "[v933-report] ERROR: $*" >&2
  exit 1
}

[[ "$#" -ge 2 && "$#" -le 3 ]] || {
  usage >&2
  exit 2
}

REPORT_DIR="$1"
EXPECTED_FQCN="$2"
EXPECTED_TESTS="${3:-1}"

[[ "$EXPECTED_TESTS" =~ ^[1-9][0-9]*$ ]] || fail "EXPECTED_TESTS must be a positive integer"
WAIT_SECONDS="${V933_REPORT_WAIT_SECONDS:-10}"
[[ "$WAIT_SECONDS" =~ ^[0-9]+$ ]] || fail "V933_REPORT_WAIT_SECONDS must be a non-negative integer"
RUN_MARKER="${V933_RUN_MARKER:-}"
[[ -z "$RUN_MARKER" || -f "$RUN_MARKER" ]] || fail "run marker does not exist: $RUN_MARKER"

deadline=$((SECONDS + WAIT_SECONDS))
while [[ ! -d "$REPORT_DIR" ]] || ! find "$REPORT_DIR" -maxdepth 1 -type f -name 'TEST-*.xml' -print -quit 2>/dev/null | grep -q .; do
  (( SECONDS >= deadline )) && break
  sleep 1
done

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
[[ "$FAILURES" == "0" ]] || fail "failures=$FAILURES"
[[ "$ERRORS" == "0" ]] || fail "errors=$ERRORS"
[[ "$SKIPPED" == "0" ]] || fail "skipped=$SKIPPED"

TESTCASE_COUNT="$(grep -o "classname=\"${EXPECTED_FQCN}\"" "$REPORT" | wc -l)"
[[ "$TESTCASE_COUNT" == "$EXPECTED_TESTS" ]] || \
  fail "expected $EXPECTED_TESTS testcase(s) for $EXPECTED_FQCN, found $TESTCASE_COUNT"

echo "[v933-report] PASS class=$EXPECTED_FQCN tests=$TESTS failures=0 errors=0 skipped=0"
