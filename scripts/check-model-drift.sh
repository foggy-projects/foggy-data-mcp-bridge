#!/usr/bin/env bash
# check-model-drift.sh — Verify Odoo model directory matches models.lock.json.
#
# Usage:
#   ./scripts/check-model-drift.sh
#
# Exit codes:
#   0  models match lock file
#   1  mismatch (drift detected) or missing lock/model files
#
# Algorithm:
#   Computes a content-level checksum (sorted relative paths + per-file sha256,
#   then sha256 of the combined output) and compares with content_checksum in
#   models.lock.json. This avoids tar metadata non-determinism.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

ADDON_DIR="$PROJECT_ROOT/addons/foggy-odoo-bridge-java"
MODEL_DIR="$ADDON_DIR/src/main/resources/foggy/templates/odoo"
LOCK_FILE="$ADDON_DIR/models.lock.json"

# ---------- pre-flight ----------
if [[ ! -f "$LOCK_FILE" ]]; then
  echo "FAIL: Lock file not found: $LOCK_FILE" >&2
  echo "  Run scripts/pull-odoo-models.sh first." >&2
  exit 1
fi

if [[ ! -d "$MODEL_DIR" ]]; then
  echo "FAIL: Model directory not found: $MODEL_DIR" >&2
  exit 1
fi

# ---------- read lock ----------
LOCK_INFO=$(python3 -c "
import json, sys
lock = json.load(open(sys.argv[1], encoding='utf-8'))
pkg = lock.get('package', '?')
ver = lock.get('version', '?')
cc = lock.get('content_checksum', '')
print(f'{pkg}@{ver}')
print(cc)
" "$LOCK_FILE")

PKG_VER=$(echo "$LOCK_INFO" | head -1)
LOCK_CONTENT_CHECKSUM=$(echo "$LOCK_INFO" | tail -1)

if [[ -z "$LOCK_CONTENT_CHECKSUM" ]]; then
  echo "FAIL: Lock file has no content_checksum field." >&2
  echo "  Re-run scripts/pull-odoo-models.sh to regenerate." >&2
  exit 1
fi

echo "Package:          $PKG_VER"
echo "Lock content_checksum: $LOCK_CONTENT_CHECKSUM"

# ---------- compute actual content checksum ----------
ACTUAL_CHECKSUM=$(
  cd "$MODEL_DIR" && \
  find . -type f ! -name "GENERATED.md" | LC_ALL=C sort | \
  while IFS= read -r f; do
    sha256sum "$f"
  done | sha256sum | awk '{print "sha256:" $1}'
)

echo "Dir  content_checksum: $ACTUAL_CHECKSUM"

# ---------- compare ----------
if [[ "$LOCK_CONTENT_CHECKSUM" == "$ACTUAL_CHECKSUM" ]]; then
  echo ""
  echo "OK: Model directory matches lock file."
  exit 0
else
  echo ""
  echo "FAIL: Model drift detected!" >&2
  echo "  Lock expects: $LOCK_CONTENT_CHECKSUM" >&2
  echo "  Directory is: $ACTUAL_CHECKSUM" >&2
  echo "" >&2
  echo "  Either the model files were manually modified, or the lock file is stale." >&2
  echo "  Run scripts/pull-odoo-models.sh to re-sync from registry." >&2
  exit 1
fi
