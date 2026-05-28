#!/usr/bin/env bash
set -euo pipefail

RUN_ID=""
RUN_ATTEMPT=""
ARTIFACT_NAME=""
DEST_ROOT="build/v38-engine-evidence-artifacts"

usage() {
  cat <<'USAGE'
Usage: scripts/collect-v38-engine-evidence-artifact.sh --run-id <id> [options]

Downloads a v3.8 engine evidence artifact from GitHub Actions and writes a
local receipt that can be referenced by the v3.8 coverage audit refresh.

Options:
  --run-id <id>           GitHub Actions run id. Required.
  --run-attempt <n>       Run attempt used to derive the default artifact name.
  --artifact-name <name>  Artifact name. Defaults to v38-engine-evidence-<run-id>-<run-attempt>.
  --dest <path>           Destination root. Default: build/v38-engine-evidence-artifacts.
  -h, --help              Show this help.

Examples:
  scripts/collect-v38-engine-evidence-artifact.sh --run-id 123 --run-attempt 1
  scripts/collect-v38-engine-evidence-artifact.sh --run-id 123 --artifact-name v38-engine-evidence-123-1
USAGE
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --run-id)
      RUN_ID="${2:-}"
      shift 2
      ;;
    --run-attempt)
      RUN_ATTEMPT="${2:-}"
      shift 2
      ;;
    --artifact-name)
      ARTIFACT_NAME="${2:-}"
      shift 2
      ;;
    --dest)
      DEST_ROOT="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "$RUN_ID" ]]; then
  echo "--run-id is required." >&2
  usage >&2
  exit 2
fi

if [[ -z "$ARTIFACT_NAME" ]]; then
  if [[ -z "$RUN_ATTEMPT" ]]; then
    echo "--run-attempt is required when --artifact-name is not provided." >&2
    usage >&2
    exit 2
  fi
  ARTIFACT_NAME="v38-engine-evidence-${RUN_ID}-${RUN_ATTEMPT}"
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI 'gh' is required." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEST_DIR="$REPO_ROOT/$DEST_ROOT/$ARTIFACT_NAME"
RECEIPT="$DEST_DIR/receipt.md"

cd "$REPO_ROOT"
mkdir -p "$DEST_DIR"

echo "+ gh run download $RUN_ID -n $ARTIFACT_NAME -D $DEST_DIR"
gh run download "$RUN_ID" -n "$ARTIFACT_NAME" -D "$DEST_DIR"

{
  echo "# v3.8 Engine Evidence Artifact Receipt"
  echo
  echo "- run_id: $RUN_ID"
  if [[ -n "$RUN_ATTEMPT" ]]; then
    echo "- run_attempt: $RUN_ATTEMPT"
  fi
  echo "- artifact_name: $ARTIFACT_NAME"
  echo "- downloaded_at: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "- destination: $DEST_ROOT/$ARTIFACT_NAME"
  echo
  echo "## Files"
  echo
  find "$DEST_DIR" -maxdepth 2 -type f | sed "s#^$REPO_ROOT/##" | sort | while read -r file; do
    echo "- \`$file\`"
  done
} > "$RECEIPT"

echo
echo "Downloaded artifact to: $DEST_DIR"
echo "Receipt written to: $RECEIPT"
