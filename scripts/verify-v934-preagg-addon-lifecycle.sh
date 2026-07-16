#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
RUNNER_NAME="verify-v934-preagg-addon-lifecycle.sh"
LANE="preagg-addon-lifecycle"
CONTRACT="$ROOT_DIR/scripts/v934/step4/successor/preagg-addon-lifecycle-contract.json"
REPORT_TOOL="$ROOT_DIR/scripts/v934/step3/preagg_addon_lifecycle_report_tool.py"
AUTHORITY_LIB="$ROOT_DIR/scripts/v934/authority_runner_lib.sh"
STEP1_TOOL="$ROOT_DIR/scripts/v934/inventory_tool.py"
STEP1_FREEZE="$ROOT_DIR/scripts/v934/contract-freeze.json"
COVERAGE_LIB="$ROOT_DIR/scripts/v934/step4/coverage_runner_lib.sh"
SELECTOR="com.foggyframework.dataset.db.model.preagg.lifecycle.PreAggAddonLifecycleIT"
REPORT_NAME="TEST-${SELECTOR}.xml"
MYSQL_IMAGE="mysql@sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb"
MYSQL_IMAGE_ID_EXPECTED="sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb"
MYSQL_EXPECTED_VERSION="5.7.44"

RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
if [[ ! "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ || "$RUN_ID" == "." || "$RUN_ID" == ".." ]]; then
  echo "[v934-preagg-addon] ERROR E_RUN_ID: unsafe run id: $RUN_ID" >&2
  exit 1
fi

RUN_ROOT="$ROOT_DIR/target/v934-step3-preagg-addon/runs/$RUN_ID"
if [[ -e "$RUN_ROOT" || -L "$RUN_ROOT" ]]; then
  echo "[v934-preagg-addon] ERROR E_RUN_ROOT: run root already exists: $RUN_ROOT" >&2
  exit 1
fi
mkdir -p "$RUN_ROOT"

PHASE="bootstrap"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
GIT_HEAD="$(git -C "$ROOT_DIR" rev-parse HEAD)"
AUTHORITY_MODE="${V934_AUTHORITY_LOCK_MODE:-standalone}"
MYSQL_CONTAINER=""
MYSQL_VOLUME=""
MYSQL_NETWORK=""
MYSQL_CONTAINER_OWNED=false
MYSQL_VOLUME_OWNED=false
MYSQL_NETWORK_OWNED=false
MYSQL_ROOT_PASSWORD=""
MYSQL_APP_PASSWORD=""
RESOURCE_RESIDUE="unknown"
DERIVED_NAME_RESIDUE="unknown"

write_status() {
  local exit_code="$1" status="$2"
  local temporary="$RUN_ROOT/.run-status.env.$$.$RANDOM.tmp"
  {
    printf 'run_id=%s\n' "$RUN_ID"
    printf 'runner=%s\n' "$RUNNER_NAME"
    printf 'lane=%s\n' "$LANE"
    printf 'git_head=%s\n' "$GIT_HEAD"
    printf 'contract_sha256=%s\n' "${CONTRACT_SHA256:-unknown}"
    printf 'source_sha256=%s\n' "${SOURCE_SHA256:-unknown}"
    printf 'started_at=%s\n' "$STARTED_AT"
    printf 'finished_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'last_phase=%s\n' "$PHASE"
    printf 'exit_code=%s\n' "$exit_code"
    printf 'resource_residue=%s\n' "$RESOURCE_RESIDUE"
    printf 'status=%s\n' "$status"
  } > "$temporary"
  mv -f -- "$temporary" "$RUN_ROOT/run-status.env"
}

resource_count() {
  local kind="$1" label="com.foggy.v934.preagg-run=$RUN_ID"
  case "$kind" in
    container) docker ps -aq --filter "label=$label" | awk 'NF {count++} END {print count+0}' ;;
    volume) docker volume ls -q --filter "label=$label" | awk 'NF {count++} END {print count+0}' ;;
    network) docker network ls -q --filter "label=$label" | awk 'NF {count++} END {print count+0}' ;;
  esac
}

derived_name_count() {
  local count=0
  if [[ -n "$MYSQL_CONTAINER" ]] && docker container inspect "$MYSQL_CONTAINER" >/dev/null 2>&1; then
    count=$((count + 1))
  fi
  if [[ -n "$MYSQL_VOLUME" ]] && docker volume inspect "$MYSQL_VOLUME" >/dev/null 2>&1; then
    count=$((count + 1))
  fi
  if [[ -n "$MYSQL_NETWORK" ]] && docker network inspect "$MYSQL_NETWORK" >/dev/null 2>&1; then
    count=$((count + 1))
  fi
  printf '%s' "$count"
}

cleanup_resources() {
  set +e
  if [[ "$MYSQL_CONTAINER_OWNED" == true ]] \
      && [[ "$(docker inspect --format '{{index .Config.Labels "com.foggy.v934.preagg-run"}}' \
        "$MYSQL_CONTAINER" 2>/dev/null)" == "$RUN_ID" ]]; then
    docker rm -fv "$MYSQL_CONTAINER" >/dev/null 2>&1
  fi
  if [[ "$MYSQL_VOLUME_OWNED" == true ]] \
      && [[ "$(docker volume inspect --format '{{index .Labels "com.foggy.v934.preagg-run"}}' \
        "$MYSQL_VOLUME" 2>/dev/null)" == "$RUN_ID" ]]; then
    docker volume rm -f "$MYSQL_VOLUME" >/dev/null 2>&1
  fi
  if [[ "$MYSQL_NETWORK_OWNED" == true ]] \
      && [[ "$(docker network inspect --format '{{index .Labels "com.foggy.v934.preagg-run"}}' \
        "$MYSQL_NETWORK" 2>/dev/null)" == "$RUN_ID" ]]; then
    docker network rm "$MYSQL_NETWORK" >/dev/null 2>&1
  fi
  MYSQL_CONTAINER_OWNED=false
  MYSQL_VOLUME_OWNED=false
  MYSQL_NETWORK_OWNED=false
  local containers volumes networks
  containers="$(resource_count container 2>/dev/null || printf unknown)"
  volumes="$(resource_count volume 2>/dev/null || printf unknown)"
  networks="$(resource_count network 2>/dev/null || printf unknown)"
  RESOURCE_RESIDUE="$containers/$volumes/$networks"
  DERIVED_NAME_RESIDUE="$(derived_name_count 2>/dev/null || printf unknown)"
  python3 - "$RUN_ROOT/resource-evidence.json" "$RUN_ID" "$containers" "$volumes" "$networks" \
    "$DERIVED_NAME_RESIDUE" <<'PY'
import json
import os
import sys
from pathlib import Path

target = Path(sys.argv[1])
payload = {
    "containers": int(sys.argv[3]) if sys.argv[3].isdigit() else sys.argv[3],
    "networks": int(sys.argv[5]) if sys.argv[5].isdigit() else sys.argv[5],
    "volumes": int(sys.argv[4]) if sys.argv[4].isdigit() else sys.argv[4],
    "label": f"com.foggy.v934.preagg-run={sys.argv[2]}",
    "derived_name_residue": int(sys.argv[6]) if sys.argv[6].isdigit() else sys.argv[6],
}
temporary = target.with_name(f".{target.name}.{os.getpid()}.tmp")
temporary.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
os.replace(temporary, target)
PY
  set -e
}

on_exit() {
  local exit_code="$?"
  trap - EXIT INT TERM HUP
  set +e
  cleanup_resources
  rm -f -- "$RUN_ROOT/summary.env" "$RUN_ROOT/candidate-manifest.json"
  write_status "$exit_code" failed
  exit "$exit_code"
}

trap on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

for required_file in "$AUTHORITY_LIB" "$STEP1_TOOL" "$STEP1_FREEZE" "$COVERAGE_LIB"; do
  if [[ ! -f "$required_file" || -L "$required_file" ]]; then
    echo "[v934-preagg-addon] ERROR E_REQUIRED_FILE: required file is missing or not regular: $required_file" >&2
    exit 1
  fi
done
# shellcheck source=v934/authority_runner_lib.sh
source "$AUTHORITY_LIB"
# shellcheck source=v934/step4/coverage_runner_lib.sh
source "$COVERAGE_LIB"

mapfile -t REACTOR_MODULES < <(python3 - "$STEP1_TOOL" "$ROOT_DIR" "$STEP1_FREEZE" <<'PY'
import json
import runpy
import sys
from pathlib import Path

namespace = runpy.run_path(sys.argv[1])
active = namespace["active_reactor_modules"](Path(sys.argv[2]))
freeze = json.loads(Path(sys.argv[3]).read_text(encoding="utf-8"))
reactor = freeze.get("reactor", {})
production = reactor.get("modules", [])
reporter = "build-support/foggy-coverage-report"
if (
    reactor.get("module_count") != 24
    or len(production) != 24
    or len(set(production)) != 24
    or reporter in production
):
    raise SystemExit("Step 1 frozen production reactor is not an exact 24-module set")
expected = sorted([*production, reporter])
if active != expected:
    missing = sorted(set(expected) - set(active))
    unexpected = sorted(set(active) - set(expected))
    raise SystemExit(
        f"active reactor differs from frozen24+reporter: missing={missing} unexpected={unexpected}"
    )
print("\n".join(sorted(production)))
PY
)
if [[ "${#REACTOR_MODULES[@]}" -ne 24 ]]; then
  echo "[v934-preagg-addon] ERROR E_REACTOR: active reactor must equal the frozen 24 production modules plus the coverage reporter" >&2
  exit 1
fi

if [[ "$AUTHORITY_MODE" == "inherited" ]]; then
  EXPECTED_PARENT_MARKER="$ROOT_DIR/target/v934-step3-required-matrix/runs/$RUN_ID/run-context.json"
  if [[ ! -f "$EXPECTED_PARENT_MARKER" || -L "$EXPECTED_PARENT_MARKER" ]]; then
    echo "[v934-preagg-addon] ERROR E_PARENT_CONTEXT: canonical parent marker is missing" >&2
    exit 1
  fi
  if [[ "$(readlink -f "${V934_PARENT_OUTER_MARKER_PATH:-/missing}")" != "$(readlink -f "$EXPECTED_PARENT_MARKER")" ]]; then
    echo "[v934-preagg-addon] ERROR E_PARENT_CONTEXT: parent marker path is not canonical" >&2
    exit 1
  fi
elif [[ "$AUTHORITY_MODE" == "standalone" ]]; then
  if [[ -n "${V934_PARENT_AUTHORITY_KIND:-}${V934_PARENT_RUN_ID:-}${V934_PARENT_GIT_HEAD:-}${V934_PARENT_CONTRACT_SHA256:-}${V934_PARENT_SOURCE_SHA256:-}${V934_PARENT_OUTER_MARKER_SHA256:-}${V934_PARENT_OUTER_MARKER_PATH:-}" ]]; then
    echo "[v934-preagg-addon] ERROR E_PARENT_CONTEXT: standalone run received parent provenance" >&2
    exit 1
  fi
fi
v934_acquire_or_validate_authority_lock "$ROOT_DIR" v934-preagg-addon

PHASE="contract"
python3 "$REPORT_TOOL" --root "$ROOT_DIR" --contract "$CONTRACT" validate-contract
CONTRACT_SHA256="$(sha256sum "$CONTRACT" | cut -d' ' -f1)"
SOURCE_SHA256="$(python3 "$REPORT_TOOL" --root "$ROOT_DIR" --contract "$CONTRACT" source-sha)"

if [[ "$AUTHORITY_MODE" == "inherited" ]]; then
  PARENT_CONTEXT_TMP="$RUN_ROOT/.parent-context.env.$$.$RANDOM.tmp"
  {
    printf 'authority_kind=%s\n' "$V934_PARENT_AUTHORITY_KIND"
    printf 'run_id=%s\n' "$V934_PARENT_RUN_ID"
    printf 'git_head=%s\n' "$V934_PARENT_GIT_HEAD"
    printf 'contract_sha256=%s\n' "$V934_PARENT_CONTRACT_SHA256"
    printf 'source_sha256=%s\n' "$V934_PARENT_SOURCE_SHA256"
    printf 'outer_marker_sha256=%s\n' "$V934_PARENT_OUTER_MARKER_SHA256"
    printf 'outer_marker_path=%s\n' "$(readlink -f "$V934_PARENT_OUTER_MARKER_PATH")"
  } > "$PARENT_CONTEXT_TMP"
  mv -f -- "$PARENT_CONTEXT_TMP" "$RUN_ROOT/parent-context.env"
fi

parent_value() {
  local name="$1"
  if [[ "$AUTHORITY_MODE" == "inherited" ]]; then
    printf '%s' "${!name}"
  else
    printf none
  fi
}

python3 - "$RUN_ROOT/run-context.json" "$RUN_ID" "$GIT_HEAD" "$CONTRACT_SHA256" "$SOURCE_SHA256" "$STARTED_AT" "$AUTHORITY_MODE" \
  "$(parent_value V934_PARENT_AUTHORITY_KIND)" "$(parent_value V934_PARENT_RUN_ID)" \
  "$(parent_value V934_PARENT_GIT_HEAD)" "$(parent_value V934_PARENT_CONTRACT_SHA256)" \
  "$(parent_value V934_PARENT_SOURCE_SHA256)" "$(parent_value V934_PARENT_OUTER_MARKER_SHA256)" <<'PY'
import json
import os
import sys
from pathlib import Path

target = Path(sys.argv[1])
keys = [
    "run_id", "git_head", "contract_sha256", "source_sha256", "started_at",
    "authority_mode", "parent_authority_kind", "parent_run_id", "parent_git_head",
    "parent_contract_sha256", "parent_source_sha256", "parent_outer_marker_sha256",
]
values = dict(zip(keys, sys.argv[2:]))
payload = {
    "schema_version": 1,
    "kind": "v934-step3-preagg-addon-lifecycle-run",
    "runner": "failsafe",
    "lane": "preagg-addon-lifecycle",
    "status": "started",
    **values,
}
temporary = target.with_name(f".{target.name}.{os.getpid()}.tmp")
temporary.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
os.replace(temporary, target)
PY

run_variant() {
  local variant="$1"
  local reports="$RUN_ROOT/work/$variant/failsafe-reports"
  local marker="$RUN_ROOT/work/$variant/variant-marker.json"
  local marker_sha
  rm -rf -- "$RUN_ROOT/work/$variant"
  mkdir -p "$reports"
  python3 - "$marker" "$RUN_ID" "$variant" "$GIT_HEAD" "$CONTRACT_SHA256" "$SOURCE_SHA256" "$RUN_ROOT/run-context.json" <<'PY'
import hashlib
import json
import os
import sys
from pathlib import Path

target = Path(sys.argv[1])
context = Path(sys.argv[7])
payload = {
    "schema_version": 1,
    "kind": "v934-step3-preagg-addon-lifecycle-variant-run",
    "run_id": sys.argv[2],
    "variant_key": sys.argv[3],
    "git_head": sys.argv[4],
    "contract_sha256": sys.argv[5],
    "source_sha256": sys.argv[6],
    "run_context_sha256": hashlib.sha256(context.read_bytes()).hexdigest(),
    "status": "started",
}
target.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
  marker_sha="$(sha256sum "$marker" | awk '{print $1}')"
  PHASE="maven-$variant"
  v934_coverage_configure it "preagg-addon-$variant"
  if [[ "$variant" == "sqlite" ]]; then
    env V934_PREAGG_DATABASE=sqlite \
      V934_PREAGG_SQLITE_PATH="$RUN_ROOT/work/sqlite/preagg.sqlite" \
      mvn -q -pl addons/foggy-dataset-model-preagg -am \
        -Dit.test="$SELECTOR" -DskipUnitTests=true -DskipITs=false \
        -Dfailsafe.failIfNoTests=false -Dfailsafe.failIfNoSpecifiedTests=false \
        -Dv934.preagg.runId="$RUN_ID" -Dv934.preagg.variant="$variant" \
        -Dv934.preagg.variantMarkerSha256="$marker_sha" \
        -Dv934.preagg.failsafe.reportsDirectory="$reports" \
        -Dv934.preagg.failsafe.summaryFile="$reports/failsafe-summary.xml" \
        "${V934_COVERAGE_MAVEN_ARGS[@]}" verify \
        >"$RUN_ROOT/work/$variant/maven.log" 2>&1
  else
    env V934_PREAGG_DATABASE=mysql57 \
      V934_PREAGG_JDBC_URL="jdbc:mysql://127.0.0.1:${MYSQL_PORT}/foggy_preagg?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
      V934_PREAGG_JDBC_USER=foggy V934_PREAGG_JDBC_PASSWORD="$MYSQL_APP_PASSWORD" \
      mvn -q -pl addons/foggy-dataset-model-preagg -am \
        -Dit.test="$SELECTOR" -DskipUnitTests=true -DskipITs=false \
        -Dfailsafe.failIfNoTests=false -Dfailsafe.failIfNoSpecifiedTests=false \
        -Dv934.preagg.runId="$RUN_ID" -Dv934.preagg.variant="$variant" \
        -Dv934.preagg.variantMarkerSha256="$marker_sha" \
        -Dv934.preagg.failsafe.reportsDirectory="$reports" \
        -Dv934.preagg.failsafe.summaryFile="$reports/failsafe-summary.xml" \
        "${V934_COVERAGE_MAVEN_ARGS[@]}" verify \
        >"$RUN_ROOT/work/$variant/maven.log" 2>&1
  fi
  v934_coverage_verify_exec
  python3 "$REPORT_TOOL" --root "$ROOT_DIR" --contract "$CONTRACT" collect \
    --variant "$variant" --reports-dir "$reports" --marker "$marker" \
    --run-context "$RUN_ROOT/run-context.json" --output-dir "$RUN_ROOT/variants/$variant"
}

PHASE="sqlite"
run_variant sqlite
rm -f -- "$RUN_ROOT/work/sqlite/preagg.sqlite" "$RUN_ROOT/work/sqlite/preagg.sqlite-wal" "$RUN_ROOT/work/sqlite/preagg.sqlite-shm"

PHASE="collector-negative-probes"
PROBE_ROOT="$RUN_ROOT/work/negative-probes"
mkdir -p "$PROBE_ROOT"
printf 'probe\texpected_error\tresult\n' > "$RUN_ROOT/negative-probes.tsv"
expect_probe() {
  local probe="$1" expected="$2" reports="$3" marker="$4"
  local variant="${5:-sqlite}"
  local output="$PROBE_ROOT/$probe-output"
  local error_file="$PROBE_ROOT/$probe.stderr"
  if python3 "$REPORT_TOOL" --root "$ROOT_DIR" --contract "$CONTRACT" collect \
      --variant "$variant" --reports-dir "$reports" --marker "$marker" \
      --run-context "$RUN_ROOT/run-context.json" --output-dir "$output" 2>"$error_file"; then
    echo "[v934-preagg-addon] ERROR E_NEGATIVE_PROBE: $probe was accepted" >&2
    exit 1
  fi
  if ! grep -Fq "ERROR $expected:" "$error_file"; then
    echo "[v934-preagg-addon] ERROR E_NEGATIVE_PROBE: $probe returned a different error" >&2
    exit 1
  fi
  printf '%s\t%s\tpassed\n' "$probe" "$expected" >> "$RUN_ROOT/negative-probes.tsv"
}

mkdir -p "$PROBE_ROOT/missing"
expect_probe missing-report E_MISSING_REPORT "$PROBE_ROOT/missing" "$RUN_ROOT/work/sqlite/variant-marker.json"

mkdir -p "$PROBE_ROOT/failure"
cp "$RUN_ROOT/work/sqlite/failsafe-reports/$REPORT_NAME" "$PROBE_ROOT/failure/$REPORT_NAME"
python3 - "$PROBE_ROOT/failure/$REPORT_NAME" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
path = Path(sys.argv[1])
tree = ET.parse(path)
tree.getroot().set("failures", "1")
tree.write(path, encoding="utf-8", xml_declaration=True)
PY
touch "$PROBE_ROOT/failure/$REPORT_NAME"
expect_probe failure-outcome E_REPORT_OUTCOME "$PROBE_ROOT/failure" "$RUN_ROOT/work/sqlite/variant-marker.json"

mkdir -p "$PROBE_ROOT/stale"
cp "$RUN_ROOT/work/sqlite/failsafe-reports/$REPORT_NAME" "$PROBE_ROOT/stale/$REPORT_NAME"
cp "$RUN_ROOT/work/sqlite/variant-marker.json" "$PROBE_ROOT/stale-marker.json"
touch "$PROBE_ROOT/stale-marker.json"
expect_probe stale-report E_STALE_REPORT "$PROBE_ROOT/stale" "$PROBE_ROOT/stale-marker.json"

PHASE="mysql57-provision"
command -v docker >/dev/null 2>&1 || {
  echo "[v934-preagg-addon] ERROR E_DOCKER: Docker is required for MySQL 5.7 lifecycle evidence" >&2
  exit 1
}
SCOPE_HASH="$(printf '%s' "$RUN_ID" | sha256sum | cut -c1-12)"
MYSQL_CONTAINER="v934preagg-${SCOPE_HASH}-mysql57"
MYSQL_VOLUME="v934preagg-${SCOPE_HASH}-mysql57-data"
MYSQL_NETWORK="v934preagg-${SCOPE_HASH}-network"
LABEL="com.foggy.v934.preagg-run=$RUN_ID"
if [[ "$(resource_count container)" != 0 || "$(resource_count volume)" != 0 || "$(resource_count network)" != 0 ]]; then
  echo "[v934-preagg-addon] ERROR E_RESOURCE_STALE: labeled resources already exist" >&2
  exit 1
fi
if docker container inspect "$MYSQL_CONTAINER" >/dev/null 2>&1 \
    || docker volume inspect "$MYSQL_VOLUME" >/dev/null 2>&1 \
    || docker network inspect "$MYSQL_NETWORK" >/dev/null 2>&1; then
  echo "[v934-preagg-addon] ERROR E_RESOURCE_NAME_COLLISION: derived resource name already exists" >&2
  exit 1
fi
if ! MYSQL_IMAGE_ID="$(docker image inspect --format '{{.Id}}' "$MYSQL_IMAGE" 2>/dev/null)"; then
  echo "[v934-preagg-addon] ERROR E_MYSQL_IMAGE: frozen digest is not cached: $MYSQL_IMAGE" >&2
  exit 1
fi
if [[ "$MYSQL_IMAGE_ID" != "$MYSQL_IMAGE_ID_EXPECTED" ]]; then
  echo "[v934-preagg-addon] ERROR E_MYSQL_IMAGE: frozen image id differs: $MYSQL_IMAGE_ID" >&2
  exit 1
fi
MYSQL_ROOT_PASSWORD="$(openssl rand -hex 24)"
MYSQL_APP_PASSWORD="$(openssl rand -hex 24)"
docker volume create --label "$LABEL" "$MYSQL_VOLUME" >/dev/null
MYSQL_VOLUME_OWNED=true
if [[ "$(docker volume inspect --format '{{index .Labels "com.foggy.v934.preagg-run"}}' \
    "$MYSQL_VOLUME")" != "$RUN_ID" ]]; then
  echo "[v934-preagg-addon] ERROR E_RESOURCE_IDENTITY: MySQL volume label differs" >&2
  exit 1
fi
docker network create --label "$LABEL" "$MYSQL_NETWORK" >/dev/null
MYSQL_NETWORK_OWNED=true
if [[ "$(docker network inspect --format '{{index .Labels "com.foggy.v934.preagg-run"}}' \
    "$MYSQL_NETWORK")" != "$RUN_ID" ]]; then
  echo "[v934-preagg-addon] ERROR E_RESOURCE_IDENTITY: MySQL network label differs" >&2
  exit 1
fi
docker create --name "$MYSQL_CONTAINER" --label "$LABEL" --network "$MYSQL_NETWORK" \
  --mount "type=volume,src=$MYSQL_VOLUME,dst=/var/lib/mysql" \
  -p 127.0.0.1::3306 \
  -e MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" -e MYSQL_DATABASE=foggy_preagg \
  -e MYSQL_USER=foggy -e MYSQL_PASSWORD="$MYSQL_APP_PASSWORD" \
  "$MYSQL_IMAGE" --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci >/dev/null
MYSQL_CONTAINER_OWNED=true
docker start "$MYSQL_CONTAINER" >/dev/null

MYSQL_PORT="$(docker port "$MYSQL_CONTAINER" 3306/tcp | awk -F: 'NR==1 {print $NF}')"
if [[ ! "$MYSQL_PORT" =~ ^[0-9]+$ ]]; then
  echo "[v934-preagg-addon] ERROR E_MYSQL_PORT: dynamic loopback port is missing" >&2
  exit 1
fi
MYSQL_VERSION=""
for _ in $(seq 1 90); do
  if MYSQL_VERSION="$(docker exec -e MYSQL_PWD="$MYSQL_APP_PASSWORD" "$MYSQL_CONTAINER" \
      mysql -ufoggy -Nse 'SELECT VERSION()' foggy_preagg 2>/dev/null)"; then
    break
  fi
  sleep 1
done
if [[ "$MYSQL_VERSION" != "$MYSQL_EXPECTED_VERSION" ]]; then
  echo "[v934-preagg-addon] ERROR E_MYSQL_VERSION: expected $MYSQL_EXPECTED_VERSION, got $MYSQL_VERSION" >&2
  exit 1
fi
CONTAINER_IMAGE_REF="$(docker inspect --format '{{.Config.Image}}' "$MYSQL_CONTAINER")"
CONTAINER_IMAGE_ID="$(docker inspect --format '{{.Image}}' "$MYSQL_CONTAINER")"
CONTAINER_RUN_LABEL="$(docker inspect --format '{{index .Config.Labels "com.foggy.v934.preagg-run"}}' "$MYSQL_CONTAINER")"
MOUNT_COUNT="$(docker inspect --format '{{len .Mounts}}' "$MYSQL_CONTAINER")"
MOUNT_TYPE="$(docker inspect --format '{{(index .Mounts 0).Type}}' "$MYSQL_CONTAINER")"
MOUNT_NAME="$(docker inspect --format '{{(index .Mounts 0).Name}}' "$MYSQL_CONTAINER")"
MOUNT_DESTINATION="$(docker inspect --format '{{(index .Mounts 0).Destination}}' "$MYSQL_CONTAINER")"
PORT_HOST_IP="$(docker inspect --format '{{(index (index .NetworkSettings.Ports "3306/tcp") 0).HostIp}}' "$MYSQL_CONTAINER")"
PORT_HOST_VALUE="$(docker inspect --format '{{(index (index .NetworkSettings.Ports "3306/tcp") 0).HostPort}}' "$MYSQL_CONTAINER")"
NETWORK_COUNT="$(docker inspect --format '{{len .NetworkSettings.Networks}}' "$MYSQL_CONTAINER")"
if [[ "$CONTAINER_IMAGE_REF" != "$MYSQL_IMAGE" || "$CONTAINER_IMAGE_ID" != "$MYSQL_IMAGE_ID_EXPECTED" \
    || "$CONTAINER_RUN_LABEL" != "$RUN_ID" || "$MOUNT_COUNT" != 1 \
    || "$MOUNT_TYPE" != volume || "$MOUNT_NAME" != "$MYSQL_VOLUME" \
    || "$MOUNT_DESTINATION" != /var/lib/mysql || "$PORT_HOST_IP" != 127.0.0.1 \
    || "$PORT_HOST_VALUE" != "$MYSQL_PORT" || "$NETWORK_COUNT" != 1 ]]; then
  echo "[v934-preagg-addon] ERROR E_RESOURCE_IDENTITY: MySQL container identity differs" >&2
  exit 1
fi
if ! docker inspect --format '{{json .NetworkSettings.Networks}}' "$MYSQL_CONTAINER" \
    | python3 -c 'import json,sys; value=json.load(sys.stdin); expected=sys.argv[1]; raise SystemExit(0 if list(value)==[expected] else 1)' "$MYSQL_NETWORK"; then
  echo "[v934-preagg-addon] ERROR E_RESOURCE_IDENTITY: MySQL network identity differs" >&2
  exit 1
fi
python3 - "$RUN_ROOT/mysql57-runtime-evidence.json" "$MYSQL_IMAGE" "$MYSQL_IMAGE_ID" \
  "$MYSQL_VERSION" "$MYSQL_PORT" "$MYSQL_CONTAINER" "$CONTAINER_RUN_LABEL" \
  "$MOUNT_NAME" "$MYSQL_NETWORK" <<'PY'
import json
import os
import sys
from pathlib import Path
target = Path(sys.argv[1])
payload = {
    "catalog": "foggy_preagg",
    "container_image_id": sys.argv[3],
    "container_image_ref": sys.argv[2],
    "container_name": sys.argv[6],
    "image_id": sys.argv[3],
    "image_ref": sys.argv[2],
    "mount": {"count": 1, "destination": "/var/lib/mysql",
              "name": sys.argv[8], "type": "volume"},
    "network": {"count": 1, "name": sys.argv[9]},
    "published_port": {"container_port": "3306/tcp", "dynamic_port": int(sys.argv[5]),
                       "host": "127.0.0.1"},
    "resource_label": {"key": "com.foggy.v934.preagg-run", "value": sys.argv[7]},
    "version": sys.argv[4],
}
temporary = target.with_name(f".{target.name}.{os.getpid()}.tmp")
temporary.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
os.replace(temporary, target)
PY

run_variant mysql57

mkdir -p "$PROBE_ROOT/wrong-variant"
cp "$RUN_ROOT/work/sqlite/failsafe-reports/$REPORT_NAME" \
  "$PROBE_ROOT/wrong-variant/$REPORT_NAME"
touch "$PROBE_ROOT/wrong-variant/$REPORT_NAME"
expect_probe wrong-variant-report E_REPORT_IDENTITY \
  "$PROBE_ROOT/wrong-variant" "$RUN_ROOT/work/mysql57/variant-marker.json" mysql57

PHASE="cleanup"
cleanup_resources
if [[ "$RESOURCE_RESIDUE" != "0/0/0" || "$DERIVED_NAME_RESIDUE" != 0 ]]; then
  echo "[v934-preagg-addon] ERROR E_RESOURCE_RESIDUE: expected label=0/0/0 names=0, " \
    "got label=$RESOURCE_RESIDUE names=$DERIVED_NAME_RESIDUE" >&2
  exit 1
fi
MYSQL_CONTAINER=""
MYSQL_VOLUME=""
MYSQL_NETWORK=""
MYSQL_ROOT_PASSWORD=""
MYSQL_APP_PASSWORD=""

PHASE="completed"
rm -rf -- "$RUN_ROOT/work/negative-probes" "$RUN_ROOT/work/sqlite/failsafe-reports" "$RUN_ROOT/work/mysql57/failsafe-reports"
write_status 0 passed
python3 "$REPORT_TOOL" --root "$ROOT_DIR" --contract "$CONTRACT" finalize --run-root "$RUN_ROOT"
python3 "$REPORT_TOOL" --root "$ROOT_DIR" --contract "$CONTRACT" verify-candidate --run-root "$RUN_ROOT"

trap - EXIT INT TERM HUP
echo "[v934-preagg-addon] PASS run_id=$RUN_ID reports=2 testcase_nodes=6 resource_residue=0/0/0"
