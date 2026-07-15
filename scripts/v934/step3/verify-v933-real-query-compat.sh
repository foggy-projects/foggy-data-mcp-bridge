#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
LEGACY_RUNNER="$ROOT_DIR/scripts/verify-v933-batch6-real-query.sh"
RUN_ID="${1:-v934-step3-v933-compat-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
REAL_MVN="$(command -v mvn)"

[[ -x "$LEGACY_RUNNER" ]] || {
  echo "[v934-v933-compat] ERROR: legacy runner is not executable: $LEGACY_RUNNER" >&2
  exit 1
}
[[ -x "$REAL_MVN" ]] || {
  echo "[v934-v933-compat] ERROR: Maven is not executable: $REAL_MVN" >&2
  exit 1
}
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || {
  echo "[v934-v933-compat] ERROR: invalid run id: $RUN_ID" >&2
  exit 1
}

mvn() {
  "$V934_REAL_MVN" \
    -Dfailsafe.failIfNoTests=false \
    -Dfailsafe.failIfNoSpecifiedTests=false \
    "$@"
}
export -f mvn
export V934_REAL_MVN="$REAL_MVN"

printf 'V934_V933_COMPAT run_id=%s legacy_runner_sha256=%s mode=upstream-selector-bridge\n' \
  "$RUN_ID" "$(sha256sum "$LEGACY_RUNNER" | cut -d' ' -f1)"
"$LEGACY_RUNNER" "$RUN_ID"
