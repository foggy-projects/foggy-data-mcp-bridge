#!/usr/bin/env bash
# pull-odoo-models.sh — Pull Odoo TM/QM models from foggy-model-registry.
#
# Usage:
#   ./scripts/pull-odoo-models.sh [OPTIONS]
#
# Options:
#   --registry <path|url>   Registry data path or HTTP URL (default: ../foggy-model-registry/data)
#   --channel  <name>       Channel: stable | beta (default: stable)
#   --edition  <name>       Edition: community | pro (default: community)
#   --package  <name>       Package name (default: foggy.odoo.<edition>)
#   --key      <value>      Bearer key (required for pro edition)
#   --dry-run               Show what would change without writing files
#
# Requires: Python 3.10+

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Defaults
REGISTRY="${PROJECT_ROOT}/../foggy-model-registry/data"
CHANNEL="stable"
EDITION="community"
PACKAGE=""
KEY=""
DRY_RUN=false

# Paths
ADDON_DIR="$PROJECT_ROOT/addons/foggy-odoo-bridge-java"
MODEL_DIR="$ADDON_DIR/src/main/resources/foggy/templates/odoo"
LOCK_FILE="$ADDON_DIR/models.lock.json"
REGISTRY_PULL_SCRIPT="$PROJECT_ROOT/../foggy-model-registry/scripts/pull.py"

# ---------- arg parsing ----------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --registry) REGISTRY="$2"; shift 2 ;;
    --channel)  CHANNEL="$2";  shift 2 ;;
    --edition)  EDITION="$2";  shift 2 ;;
    --package)  PACKAGE="$2";  shift 2 ;;
    --key)      KEY="$2";      shift 2 ;;
    --dry-run)  DRY_RUN=true;  shift ;;
    *)
      echo "ERROR: Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

# Derive package name if not explicit
if [[ -z "$PACKAGE" ]]; then
  PACKAGE="foggy.odoo.${EDITION}"
fi

echo "=== pull-odoo-models ==="
echo "  registry : $REGISTRY"
echo "  package  : $PACKAGE"
echo "  channel  : $CHANNEL"
echo "  edition  : $EDITION"

# ---------- pre-flight ----------
if [[ "$EDITION" == "pro" && -z "$KEY" ]]; then
  echo "ERROR: Pro edition requires --key" >&2
  exit 1
fi

if [[ ! -f "$REGISTRY_PULL_SCRIPT" ]]; then
  echo "ERROR: Registry pull script not found at $REGISTRY_PULL_SCRIPT" >&2
  echo "  Ensure foggy-model-registry is checked out alongside this repo." >&2
  exit 1
fi

# ---------- staging ----------
STAGING_DIR=$(mktemp -d)
trap 'rm -rf "$STAGING_DIR"' EXIT

echo ""
echo "Pulling bundle to staging..."

PULL_ARGS=(
  --registry "$REGISTRY"
  --package "$PACKAGE"
  --channel "$CHANNEL"
  --edition "$EDITION"
  --output "$STAGING_DIR"
)
if [[ -n "$KEY" ]]; then
  PULL_ARGS+=(--key "$KEY")
fi

python3 "$REGISTRY_PULL_SCRIPT" "${PULL_ARGS[@]}"

# pull.py writes models.lock.json in the output dir
STAGING_LOCK="$STAGING_DIR/models.lock.json"
if [[ ! -f "$STAGING_LOCK" ]]; then
  echo "ERROR: pull.py did not produce models.lock.json" >&2
  exit 1
fi

# ---------- diff / apply ----------
echo ""
if $DRY_RUN; then
  echo "[dry-run] Would update model directory: $MODEL_DIR"
  echo "[dry-run] Would update lock file:       $LOCK_FILE"
  echo ""
  echo "Staged lock content:"
  cat "$STAGING_LOCK"
  exit 0
fi

# Clear existing models and replace
echo "Syncing model directory..."
rm -rf "$MODEL_DIR"
mkdir -p "$MODEL_DIR"

# Copy model files (not lock file) from staging
find "$STAGING_DIR" -maxdepth 1 -mindepth 1 ! -name "models.lock.json" -exec cp -r {} "$MODEL_DIR/" \;

# Compute content-level checksum (deterministic, based on file contents)
# This is used by check-model-drift.sh instead of the bundle tarball checksum
CONTENT_CHECKSUM=$(
  cd "$MODEL_DIR" && \
  find . -type f ! -name "GENERATED.md" | LC_ALL=C sort | \
  while IFS= read -r f; do
    sha256sum "$f"
  done | sha256sum | awk '{print "sha256:" $1}'
)

# Augment lock file with content checksum
python3 -c "
import json, sys
lock = json.load(open(sys.argv[1], encoding='utf-8'))
lock['content_checksum'] = sys.argv[2]
with open(sys.argv[3], 'w', encoding='utf-8') as f:
    json.dump(lock, f, indent=2, ensure_ascii=False)
    f.write('\n')
" "$STAGING_LOCK" "$CONTENT_CHECKSUM" "$LOCK_FILE"

# Re-create GENERATED marker
cat > "$MODEL_DIR/GENERATED.md" << 'MARKER'
本目录由 foggy-model-registry 同步生成，禁止手工修改。
使用 scripts/pull-odoo-models.sh 更新。
MARKER

echo ""
echo "Pull complete."
echo "  Models: $MODEL_DIR"
echo "  Lock:   $LOCK_FILE"
echo ""
cat "$LOCK_FILE"
