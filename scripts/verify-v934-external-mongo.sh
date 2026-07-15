#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_PATH="$ROOT_DIR/scripts/verify-v934-external-mongo.sh"
STEP3_DIR="$ROOT_DIR/scripts/v934/step3"
CONTRACT="$STEP3_DIR/external-matrix-contract.json"
REPORT_TOOL="$STEP3_DIR/external_matrix_report_tool.py"
AUTHORITY_LIB="$ROOT_DIR/scripts/v934/authority_runner_lib.sh"
DEFERRED_INVENTORY="$ROOT_DIR/scripts/v934/successor/step2/deferred-step3.tsv"
MODEL_REPORTS="$ROOT_DIR/addons/foggy-dataset-model-mongo/target/failsafe-reports"
VIEWER_REPORTS="$ROOT_DIR/addons/foggy-data-viewer/target/failsafe-reports"
CLEAN_MODULES="foggy-core,foggy-bean-copy,foggy-mcp-spi,foggy-fsscript,foggy-dataset,foggy-dataset-demo,foggy-dataset-model,addons/foggy-data-viewer,addons/foggy-dataset-model-mongo"

RUNNER_NAME="failsafe"
LANE="external-mongo"
MONGO_IMAGE_REF="mongo@sha256:03cda579c8caad6573cb98c2b3d5ff5ead452a6450561129b89595b4b9c18de2"
MONGO_IMAGE_ID="sha256:03cda579c8caad6573cb98c2b3d5ff5ead452a6450561129b89595b4b9c18de2"
MONGO_VERSION="6.0.27"
MONGO_GIT_VERSION="fc88ca137231d7457aed6265d4f32a361ae71716"
MONGO_CONTAINER=""
MONGO_DATA_VOLUME=""
MONGO_CONFIG_VOLUME=""
RUN_LOG_FIFO=""
RUN_LOG_TEE_PID=""
RUN_LOG_OPEN=false
SIGNAL_PROBE_MODE="${V934_EXTERNAL_MONGO_SIGNAL_PROBE:-false}"

SENSITIVE_PATTERNS=(
  '(?i)(?:MONGO_PASSWORD|MONGO_USERNAME)'
  '(?i)"?(?:password|passwd|pwd|credential|credentials|api[-_]?key|access[-_]?token|refresh[-_]?token|auth[-_]?token|secret|authorization)"?[[:space:]]*[:=][[:space:]]*"?[^"[:space:],}]+'
  '(?i)(?:authorization[[:space:]]*[:=][[:space:]]*)?bearer[[:space:]]+[A-Za-z0-9._~+/-]+'
  '(?i)mongodb(?:\+srv)?://[^/@:[:space:]]+:[^/@[:space:]]+@'
  '(?i)(?:--password|--passwd|--pwd)(?:=|[[:space:]])[^[:space:]]+'
)

fail() {
  echo "[v934-external-mongo] ERROR: $*" >&2
  exit 1
}

sha256_file() {
  sha256sum "$1" | cut -d' ' -f1
}

atomic_env() {
  local output="$1"
  shift
  local temporary="${output}.$$.$RANDOM.tmp"
  printf '%s\n' "$@" > "$temporary"
  mv -f -- "$temporary" "$output"
}

assert_protected_worktree_clean() {
  local status
  status="$(git -C "$ROOT_DIR" status --porcelain=v1 --untracked-files=all -- \
    pom.xml \
    foggy-core/pom.xml foggy-core/src/main \
    foggy-bean-copy/pom.xml foggy-bean-copy/src/main \
    foggy-mcp-spi/pom.xml foggy-mcp-spi/src/main \
    foggy-fsscript/pom.xml foggy-fsscript/src/main \
    foggy-dataset/pom.xml foggy-dataset/src/main \
    foggy-dataset-demo/pom.xml foggy-dataset-demo/src/main \
    foggy-dataset-model/pom.xml foggy-dataset-model/src/main \
    addons/foggy-data-viewer/pom.xml addons/foggy-data-viewer/src \
    addons/foggy-dataset-model-mongo/pom.xml addons/foggy-dataset-model-mongo/src)"
  [[ -z "$status" ]] || fail "protected Mongo lane worktree is dirty"
}

assert_clean_targets_absent() {
  local module
  local -a modules=(
    foggy-core foggy-bean-copy foggy-mcp-spi foggy-fsscript foggy-dataset
    foggy-dataset-demo foggy-dataset-model addons/foggy-data-viewer
    addons/foggy-dataset-model-mongo
  )
  for module in "${modules[@]}"; do
    [[ ! -e "$ROOT_DIR/$module/target" ]] || \
      fail "explicit Maven clean left a module target: $module/target"
  done
}

create_source_seal() {
  local output="$1" result
  result="$(python3 "$REPORT_TOOL" seal-source --lane "$LANE" --output "$output")"
  echo "$result"
  sed -n 's/.*sha256=//p' <<< "$result"
}

verify_sensitive_patterns() {
  local output="$1"
  local probe_file="$RUN_ROOT/.sensitive-pattern-probe"
  local temporary="${output}.$$.$RANDOM.tmp"
  local index
  local -a labels=(mongo-env json-password api-key bearer mongo-uri cli-password)
  local -a fixtures=(
    'MONGO_PASSWORD=x'
    '{"password": "x"}'
    'API_KEY=x'
    'Authorization: Bearer x'
    'mongodb://u:p@127.0.0.1:27017/example'
    '--password x'
  )
  local -a args=()
  local pattern
  for pattern in "${SENSITIVE_PATTERNS[@]}"; do
    args+=(-e "$pattern")
  done
  printf 'probe\tstatus\n' > "$temporary"
  for index in "${!labels[@]}"; do
    printf '%s\n' "${fixtures[$index]}" > "$probe_file"
    rg -q "${args[@]}" "$probe_file" || \
      fail "sensitive scan failed to detect probe: ${labels[$index]}"
    printf '%s\tpassed\n' "${labels[$index]}" >> "$temporary"
  done
  rm -f -- "$probe_file"
  mv -f -- "$temporary" "$output"
}

write_outer_marker() {
  python3 - "$OUTER_MARKER" "$RUN_ID" "$GIT_HEAD" "$CONTRACT_SHA256" "$STARTED_AT" <<'PY'
import json
import os
from pathlib import Path
import sys

output = Path(sys.argv[1])
payload = {
    "schema_version": 1,
    "kind": "v934-step3-external-matrix-outer-run",
    "run_id": sys.argv[2],
    "lane": "external-matrix",
    "runner": "failsafe",
    "git_head": sys.argv[3],
    "contract_sha256": sys.argv[4],
    "started_at": sys.argv[5],
    "status": "started",
}
temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
temporary.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
os.replace(temporary, output)
PY
}

write_variant_marker() {
  local marker="$1"
  python3 - "$marker" "$RUN_ID" "$GIT_HEAD" "$CONTRACT_SHA256" \
    "$OUTER_MARKER_SHA256" "$SELECTOR" <<'PY'
import datetime as dt
import json
import os
from pathlib import Path
import sys

output = Path(sys.argv[1])
payload = {
    "schema_version": 1,
    "kind": "v934-step3-external-matrix-variant-run",
    "run_id": sys.argv[2],
    "lane": "external-matrix",
    "runner": "failsafe",
    "git_head": sys.argv[3],
    "contract_sha256": sys.argv[4],
    "started_at": dt.datetime.now(dt.timezone.utc).isoformat(),
    "status": "started",
    "variant_key": "mongo6",
    "infra_kind": "mongodb",
    "outer_marker_sha256": sys.argv[5],
    "selector": sys.argv[6],
}
temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
temporary.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
os.replace(temporary, output)
PY
}

close_run_log() {
  local attempt tee_code=0
  if [[ "$RUN_LOG_OPEN" == true ]]; then
    exec 1>&3 2>&4
    RUN_LOG_OPEN=false
    exec 3>&- 4>&-
  fi
  if [[ -n "$RUN_LOG_TEE_PID" ]]; then
    for ((attempt = 0; attempt < 50; attempt++)); do
      if ! kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1; then
        break
      fi
      sleep 0.1
    done
    if kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1; then
      tee_code=124
      kill -TERM "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || true
      for ((attempt = 0; attempt < 10; attempt++)); do
        kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || break
        sleep 0.1
      done
      kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1 && \
        kill -KILL "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || true
      wait "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || true
    elif wait "$RUN_LOG_TEE_PID"; then
      tee_code=0
    else
      tee_code=$?
    fi
    RUN_LOG_TEE_PID=""
  fi
  [[ -z "$RUN_LOG_FIFO" ]] || rm -f -- "$RUN_LOG_FIFO"
  return "$tee_code"
}

mongo_resource_absent() {
  local names labelled volume_names labelled_volumes
  docker info >/dev/null 2>&1 || return 2
  names="$(docker ps -a --format '{{.Names}}')" || return 2
  labelled="$(docker ps -aq --filter "label=com.foggy.v934.external-run=$RUN_ID")" || return 2
  volume_names="$(docker volume ls -q)" || return 2
  labelled_volumes="$(
    docker volume ls -q --filter "label=com.foggy.v934.external-run=$RUN_ID"
  )" || return 2
  ! grep -Fxq "$MONGO_CONTAINER" <<< "$names" \
    && ! grep -Fxq "$MONGO_DATA_VOLUME" <<< "$volume_names" \
    && ! grep -Fxq "$MONGO_CONFIG_VOLUME" <<< "$volume_names" \
    && [[ -z "$labelled" ]] \
    && [[ -z "$labelled_volumes" ]]
}

cleanup_mongo() {
  local cleanup_code=0 labelled="" labelled_volumes=""
  local -a containers=() volumes=()
  if [[ -n "$MONGO_CONTAINER" ]]; then
    labelled="$(docker ps -aq --filter "label=com.foggy.v934.external-run=$RUN_ID")" || \
      cleanup_code=1
    if [[ -n "${labelled:-}" ]]; then
      mapfile -t containers <<< "$labelled"
      docker rm -fv -- "${containers[@]}" >/dev/null 2>&1 || cleanup_code=1
    fi
    labelled_volumes="$(
      docker volume ls -q --filter "label=com.foggy.v934.external-run=$RUN_ID"
    )" || cleanup_code=1
    if [[ -n "${labelled_volumes:-}" ]]; then
      mapfile -t volumes <<< "$labelled_volumes"
      docker volume rm -- "${volumes[@]}" >/dev/null 2>&1 || cleanup_code=1
    fi
    mongo_resource_absent || cleanup_code=1
  fi
  if [[ -d "${CELL_ROOT:-}" ]]; then
    if [[ "$cleanup_code" -eq 0 ]]; then
      atomic_env "$CELL_ROOT/cleanup.env" \
        "cell=mongo6" \
        "container=$MONGO_CONTAINER" \
        "data_volume=$MONGO_DATA_VOLUME" \
        "config_volume=$MONGO_CONFIG_VOLUME" \
        "container_residue=0" \
        "volume_residue=0" \
        "status=passed" || cleanup_code=1
    else
      atomic_env "$CELL_ROOT/cleanup.env" \
        "cell=mongo6" \
        "container=$MONGO_CONTAINER" \
        "data_volume=$MONGO_DATA_VOLUME" \
        "config_volume=$MONGO_CONFIG_VOLUME" \
        "status=failed" || true
    fi
  fi
  return "$cleanup_code"
}

record_run_status() {
  local exit_code="$1" finalizer_code=0
  trap '' INT TERM HUP
  trap - EXIT
  set +e
  if ! cleanup_mongo; then
    PHASE="mongo-cleanup-failed"
    finalizer_code=1
  fi
  if ! close_run_log; then
    PHASE="run-log-flush-failed"
    finalizer_code=1
  fi
  [[ "$finalizer_code" -eq 0 ]] || exit_code=1
  if [[ "$exit_code" -eq 0 && "$PHASE" != completed ]]; then
    exit_code=1
  fi
  if [[ "$exit_code" -ne 0 ]]; then
    rm -f -- "$RUN_ROOT/summary.env" "$RUN_ROOT/candidate-manifest.json"
  fi
  v934_write_run_status "$exit_code" || exit_code=1
  exit "$exit_code"
}

mongo_eval() {
  local expression="$1"
  docker exec "$MONGO_CONTAINER" mongosh --quiet --norc --eval "$expression" | tail -n 1 | tr -d '\r'
}

[[ "$#" -le 1 ]] || fail "usage: $SCRIPT_PATH [RUN_ID]"
[[ "$SIGNAL_PROBE_MODE" == false || "$SIGNAL_PROBE_MODE" == true ]] || \
  fail "V934_EXTERNAL_MONGO_SIGNAL_PROBE must be true or false"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ && "$RUN_ID" != . && "$RUN_ID" != .. ]] || \
  fail "unsafe run id: $RUN_ID"

for command_name in cmp cut date docker flock git grep jq mkfifo mv mvn python3 rg sed seq sha256sum sleep tee; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command missing: $command_name"
done
for required_file in "$SCRIPT_PATH" "$CONTRACT" "$REPORT_TOOL" "$AUTHORITY_LIB" "$DEFERRED_INVENTORY"; do
  [[ -f "$required_file" ]] || fail "required file missing: $required_file"
done
for variable_name in MAVEN_ARGS MAVEN_CONFIG MAVEN_OPTS; do
  variable_value="${!variable_name:-}"
  if [[ "$variable_value" =~ (skipTests|skipITs|skipUnitTests|multi-db|model-lifecycle|query-cache-real-query|failIfNo[A-Za-z0-9._-]*) ]]; then
    fail "$variable_name contains a forbidden lane override"
  fi
done

# shellcheck source=scripts/v934/authority_runner_lib.sh
source "$AUTHORITY_LIB"
v934_acquire_authority_lock "$ROOT_DIR" "v934-external-mongo" || exit 1

RUN_ROOT="$ROOT_DIR/target/v934-step3-external-matrix/runs/$RUN_ID"
[[ ! -e "$RUN_ROOT" ]] || fail "run root already exists: $RUN_ROOT"
mkdir -p "$RUN_ROOT/variants" "$RUN_ROOT/cells/mongo6" "$RUN_ROOT/negative"
CELL_ROOT="$RUN_ROOT/cells/mongo6"
OUTER_MARKER="$RUN_ROOT/run-context.json"
RUN_LOG_FIFO="$RUN_ROOT/.run-log.fifo"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
START_EPOCH="$(date -u +%s)"
GIT_HEAD="$(git -C "$ROOT_DIR" rev-parse HEAD)"
CONTRACT_SHA256="$(sha256_file "$CONTRACT")"
SUCCESSOR_MANIFEST_SHA256="$(sha256_file "$DEFERRED_INVENTORY")"
FINAL_REPORT_MANIFEST_SHA256=""
OUTER_MARKER_SHA256=""
SOURCE_BEFORE=""
SOURCE_AFTER=""
PHASE="bootstrap"
SCOPE_HASH="$(printf '%s\n' "$RUN_ID|mongo6" | sha256sum | cut -c1-12)"
MONGO_CONTAINER="v934ext-mongo6-$SCOPE_HASH"
MONGO_DATA_VOLUME="$MONGO_CONTAINER-data"
MONGO_CONFIG_VOLUME="$MONGO_CONTAINER-config"
MODEL_DATABASE="v934_${SCOPE_HASH}_model"
VIEWER_DATABASE="v934_${SCOPE_HASH}_viewer"
SELECTOR="$(jq -er '.variants[] | select(.variant_key == "mongo6") | .selector' "$CONTRACT")"

trap 'record_run_status "$?"' EXIT
trap 'v934_exit_on_signal 130' INT
trap 'v934_exit_on_signal 143' TERM
trap 'v934_exit_on_signal 129' HUP

exec 3>&1 4>&2
mkfifo "$RUN_LOG_FIFO"
(
  trap '' INT TERM HUP
  exec tee -a "$RUN_ROOT/run.log"
) < "$RUN_LOG_FIFO" >&3 2>&4 &
RUN_LOG_TEE_PID=$!
RUN_LOG_OPEN=true
exec > "$RUN_LOG_FIFO" 2>&1
rm -f -- "$RUN_LOG_FIFO"

PHASE="contract-validate"
python3 "$REPORT_TOOL" validate
assert_protected_worktree_clean
SOURCE_BEFORE="$(create_source_seal "$RUN_ROOT/source-before.tsv" | tail -n 1)"

PHASE="explicit-module-clean"
(cd "$ROOT_DIR" && mvn -q -pl "$CLEAN_MODULES" clean)
assert_clean_targets_absent
atomic_env "$RUN_ROOT/preclean.env" \
  "modules=$CLEAN_MODULES" \
  "root_target_preserved=true" \
  "status=passed"

PHASE="outer-marker"
write_outer_marker
OUTER_MARKER_SHA256="$(sha256_file "$OUTER_MARKER")"

PHASE="image-preflight"
docker info >/dev/null
actual_local_image_id="$(docker image inspect "$MONGO_IMAGE_REF" -f '{{.Id}}')" || \
  fail "frozen Mongo image is unavailable"
[[ "$actual_local_image_id" == "$MONGO_IMAGE_ID" ]] || \
  fail "local Mongo image id differs: $actual_local_image_id"
repo_digests="$(docker image inspect "$MONGO_IMAGE_REF" -f '{{json .RepoDigests}}')"
grep -Fq "\"$MONGO_IMAGE_REF\"" <<< "$repo_digests" || \
  fail "local Mongo image lacks frozen repo digest"
mongo_resource_absent || fail "run-scoped Mongo resource already exists"

PHASE="mongo-start"
docker volume create \
  --label "com.foggy.v934.external-run=$RUN_ID" \
  --label 'com.foggy.v934.external-cell=mongo6' \
  "$MONGO_DATA_VOLUME" >/dev/null
docker volume create \
  --label "com.foggy.v934.external-run=$RUN_ID" \
  --label 'com.foggy.v934.external-cell=mongo6' \
  "$MONGO_CONFIG_VOLUME" >/dev/null
docker run -d \
  --name "$MONGO_CONTAINER" \
  --label "com.foggy.v934.external-run=$RUN_ID" \
  --label 'com.foggy.v934.external-cell=mongo6' \
  --network bridge \
  -p 127.0.0.1::27017 \
  --mount "type=volume,source=$MONGO_DATA_VOLUME,target=/data/db" \
  --mount "type=volume,source=$MONGO_CONFIG_VOLUME,target=/data/configdb" \
  "$MONGO_IMAGE_REF" --bind_ip_all >/dev/null

PHASE="mongo-health"
mongo_ping=""
for _ in $(seq 1 120); do
  mongo_ping="$(mongo_eval 'print(db.adminCommand({ping:1}).ok)' 2>/dev/null || true)"
  [[ "$mongo_ping" == 1 ]] && break
  sleep 0.5
done
[[ "$mongo_ping" == 1 ]] || fail "run-scoped Mongo did not become ready"

PHASE="mongo-identity"
actual_image_id="$(docker inspect -f '{{.Image}}' "$MONGO_CONTAINER")"
actual_image_ref="$(docker inspect -f '{{.Config.Image}}' "$MONGO_CONTAINER")"
actual_run_label="$(docker inspect -f '{{index .Config.Labels "com.foggy.v934.external-run"}}' "$MONGO_CONTAINER")"
actual_cell_label="$(docker inspect -f '{{index .Config.Labels "com.foggy.v934.external-cell"}}' "$MONGO_CONTAINER")"
mount_count="$(docker inspect -f '{{len .Mounts}}' "$MONGO_CONTAINER")"
data_mount_identity="$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/data/db"}}{{.Name}}|{{.Destination}}|{{.Type}}{{end}}{{end}}' "$MONGO_CONTAINER")"
config_mount_identity="$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/data/configdb"}}{{.Name}}|{{.Destination}}|{{.Type}}{{end}}{{end}}' "$MONGO_CONTAINER")"
data_volume_run_label="$(docker volume inspect -f '{{index .Labels "com.foggy.v934.external-run"}}' "$MONGO_DATA_VOLUME")"
config_volume_run_label="$(docker volume inspect -f '{{index .Labels "com.foggy.v934.external-run"}}' "$MONGO_CONFIG_VOLUME")"
data_volume_cell_label="$(docker volume inspect -f '{{index .Labels "com.foggy.v934.external-cell"}}' "$MONGO_DATA_VOLUME")"
config_volume_cell_label="$(docker volume inspect -f '{{index .Labels "com.foggy.v934.external-cell"}}' "$MONGO_CONFIG_VOLUME")"
data_volume_created="$(docker volume inspect -f '{{.CreatedAt}}' "$MONGO_DATA_VOLUME")"
config_volume_created="$(docker volume inspect -f '{{.CreatedAt}}' "$MONGO_CONFIG_VOLUME")"
data_volume_epoch="$(date -d "$data_volume_created" +%s)"
config_volume_epoch="$(date -d "$config_volume_created" +%s)"
mapped_port="$(docker port "$MONGO_CONTAINER" 27017/tcp | tail -n 1)"
MONGO_PORT="$(sed -n 's/^127\.0\.0\.1://p' <<< "$mapped_port")"
build_identity="$(mongo_eval 'const b=db.runCommand({buildInfo:1}); print(b.version+"|"+b.gitVersion)')"
actual_version="${build_identity%%|*}"
actual_git_version="${build_identity#*|}"
actual_topology="$(mongo_eval 'const h=db.hello(); print(h.setName || "standalone")')"
actual_authorization="$(mongo_eval 'const o=db.adminCommand({getCmdLineOpts:1}); print(((o.parsed.security||{}).authorization)||"disabled")')"
actual_process="$(mongo_eval 'const o=db.adminCommand({getCmdLineOpts:1}); print((o.argv[0]||"").split("/").pop())')"
actual_storage_engine="$(mongo_eval 'print(db.serverStatus().storageEngine.name)')"
initial_model_collections="$(mongo_eval "print(db.getSiblingDB('$MODEL_DATABASE').getCollectionNames().length)")"
initial_viewer_collections="$(mongo_eval "print(db.getSiblingDB('$VIEWER_DATABASE').getCollectionNames().length)")"
initial_foreign_databases="$(mongo_eval "const keep=new Set(['admin','config','local','$MODEL_DATABASE','$VIEWER_DATABASE']); print(db.adminCommand({listDatabases:1,nameOnly:true}).databases.filter(x=>!keep.has(x.name)).length)")"
[[ "$actual_image_id" == "$MONGO_IMAGE_ID" && "$actual_image_ref" == "$MONGO_IMAGE_REF" ]] || \
  fail "container Mongo image identity differs"
[[ "$actual_run_label" == "$RUN_ID" && "$actual_cell_label" == mongo6 ]] || \
  fail "container ownership labels differ"
[[ "$mount_count" == 2 ]] || fail "fresh Mongo cell must have exactly two mounts"
[[ "$data_mount_identity" == "$MONGO_DATA_VOLUME|/data/db|volume" ]] || \
  fail "Mongo data mount identity differs"
[[ "$config_mount_identity" == "$MONGO_CONFIG_VOLUME|/data/configdb|volume" ]] || \
  fail "Mongo config mount identity differs"
[[ "$data_volume_run_label" == "$RUN_ID" && "$config_volume_run_label" == "$RUN_ID" \
   && "$data_volume_cell_label" == mongo6 && "$config_volume_cell_label" == mongo6 ]] || \
  fail "Mongo volume ownership labels differ"
[[ "$data_volume_epoch" -ge "$START_EPOCH" && "$config_volume_epoch" -ge "$START_EPOCH" ]] || \
  fail "Mongo volumes predate the run marker"
[[ "$mapped_port" == 127.0.0.1:* && "$MONGO_PORT" =~ ^[0-9]+$ ]] || \
  fail "Mongo must use one dynamic loopback port"
[[ "$actual_version" == "$MONGO_VERSION" && "$actual_git_version" == "$MONGO_GIT_VERSION" ]] || \
  fail "Mongo build identity differs: $build_identity"
[[ "$actual_topology" == standalone && "$actual_authorization" == disabled \
   && "$actual_process" == mongod && "$actual_storage_engine" == wiredTiger ]] || \
  fail "Mongo runtime identity differs"
[[ "$initial_model_collections" == 0 && "$initial_viewer_collections" == 0 \
   && "$initial_foreign_databases" == 0 ]] || fail "fresh Mongo databases are not empty"
atomic_env "$CELL_ROOT/resource.env" \
  "run_id=$RUN_ID" \
  "cell=mongo6" \
  "container=$MONGO_CONTAINER" \
  "image_ref=$actual_image_ref" \
  "image_id=$actual_image_id" \
  "mapped_port=$mapped_port" \
  "mount_count=$mount_count" \
  "data_mount_identity=$data_mount_identity" \
  "config_mount_identity=$config_mount_identity" \
  "data_volume=$MONGO_DATA_VOLUME" \
  "config_volume=$MONGO_CONFIG_VOLUME" \
  "data_volume_created=$data_volume_created" \
  "config_volume_created=$config_volume_created" \
  "mongo_version=$actual_version" \
  "mongo_git_version=$actual_git_version" \
  "topology=$actual_topology" \
  "auth_mode=$actual_authorization" \
  "server_process=$actual_process" \
  "storage_engine=$actual_storage_engine" \
  "model_database=$MODEL_DATABASE" \
  "viewer_database=$VIEWER_DATABASE" \
  "initial_model_collections=0" \
  "initial_viewer_collections=0" \
  "initial_foreign_databases=0" \
  "status=verified"

if [[ "$SIGNAL_PROBE_MODE" == true ]]; then
  PHASE="signal-probe-ready"
  atomic_env "$RUN_ROOT/signal-probe-ready.env" \
    "run_id=$RUN_ID" \
    "container=$MONGO_CONTAINER" \
    "data_volume=$MONGO_DATA_VOLUME" \
    "config_volume=$MONGO_CONFIG_VOLUME" \
    "status=ready"
  echo "[v934-external-mongo] SIGNAL_PROBE_READY run=$RUN_ID"
  while :; do
    sleep 1
  done
fi

PHASE="variant-mongo6"
VARIANT_ROOT="$RUN_ROOT/variants/mongo6"
MARKER="$VARIANT_ROOT/run-marker.json"
rm -rf -- "$MODEL_REPORTS" "$VIEWER_REPORTS"
mkdir -p "$VARIANT_ROOT"
write_variant_marker "$MARKER"
echo "[v934-external-mongo] running variant=mongo6"
(cd "$ROOT_DIR" && \
  FOGGY_DATA_VIEWER_MONGO_IT=true \
  FOGGY_DATA_VIEWER_MONGO_URI="mongodb://127.0.0.1:$MONGO_PORT/$VIEWER_DATABASE?directConnection=true" \
  mvn -q \
    -P'!multi-db,!model-lifecycle,!query-cache-real-query' \
    -pl addons/foggy-dataset-model-mongo,addons/foggy-data-viewer -am \
    -Dit.test="$SELECTOR" \
    -Dspring.data.mongodb.uri="mongodb://127.0.0.1:$MONGO_PORT/$MODEL_DATABASE?directConnection=true" \
    -Dfoggy.demo.enabled=false \
    -Dfoggy.bundle.external.enabled=true \
    '-Dfoggy.bundle.external.bundles[0].name=v934-mongo-mcp-audit' \
    "-Dfoggy.bundle.external.bundles[0].path=file:$ROOT_DIR/foggy-dataset-demo/src/main/resources/foggy/templates/mcp_audit" \
    '-Dfoggy.bundle.external.bundles[0].watch=false' \
    "-Dspring.datasource.url=jdbc:sqlite:file:v934_${SCOPE_HASH}?mode=memory&cache=shared" \
    -Dspring.datasource.driver-class-name=org.sqlite.JDBC \
    -Dspring.datasource.hikari.minimum-idle=0 \
    -Dspring.datasource.hikari.maximum-pool-size=1 \
    -Dspring.datasource.hikari.connection-timeout=2000 \
    -Dspring.datasource.hikari.initialization-fail-timeout=1 \
    '-Dspring.datasource.hikari.connection-test-query=SELECT 1' \
    '-Dspring.datasource.hikari.connection-init-sql=CREATE TABLE IF NOT EXISTS dual(dummy INTEGER)' \
    -Dlogging.level.org.mongodb.driver=WARN \
    -DskipUnitTests=true \
    -DskipITs=false \
    -Dfailsafe.rerunFailingTestsCount=0 \
    -Dfailsafe.failIfNoTests=false \
    -Dfailsafe.failIfNoSpecifiedTests=false \
    -Dv934.external.run-id="$RUN_ID" \
    -Dv934.external.variant=mongo6 \
    verify)
python3 "$REPORT_TOOL" seal-bytecode \
  --lane "$LANE" --output "$VARIANT_ROOT/bytecode.tsv"
python3 "$REPORT_TOOL" collect \
  --variant mongo6 \
  --outer-marker "$OUTER_MARKER" \
  --run-marker "$MARKER" \
  --report-root "addons/foggy-dataset-model-mongo=$MODEL_REPORTS" \
  --report-root "addons/foggy-data-viewer=$VIEWER_REPORTS" \
  --output "$VARIANT_ROOT/evidence"

PHASE="mongo-fixture"
model_collections="$(mongo_eval "print(db.getSiblingDB('$MODEL_DATABASE').getCollectionNames().sort().join(','))")"
mcp_audit_count="$(mongo_eval "print(db.getSiblingDB('$MODEL_DATABASE').getCollection('mcp_tool_audit_log').countDocuments({}))")"
sales_order_count="$(mongo_eval "print(db.getSiblingDB('$MODEL_DATABASE').getCollection('sales_order_test').countDocuments({}))")"
geo_station_count="$(mongo_eval "print(db.getSiblingDB('$MODEL_DATABASE').getCollection('geo_station_test').countDocuments({}))")"
viewer_collections="$(mongo_eval "print(db.getSiblingDB('$VIEWER_DATABASE').getCollectionNames().sort().join(','))")"
list_presets_count="$(mongo_eval "print(db.getSiblingDB('$VIEWER_DATABASE').getCollection('list_presets').countDocuments({}))")"
foreign_database_count="$(mongo_eval "const keep=new Set(['admin','config','local','$MODEL_DATABASE','$VIEWER_DATABASE']); print(db.adminCommand({listDatabases:1,nameOnly:true}).databases.filter(x=>!keep.has(x.name)).length)")"
[[ "$model_collections" == mcp_tool_audit_log,sales_order_test ]] || \
  fail "Mongo model collection set differs: $model_collections"
[[ "$mcp_audit_count" == 25 && "$sales_order_count" == 20 && "$geo_station_count" == 0 ]] || \
  fail "Mongo model fixture counts differ"
[[ "$viewer_collections" == list_presets && "$list_presets_count" == 0 ]] || \
  fail "Mongo viewer fixture differs: collections=$viewer_collections list_presets_count=$list_presets_count"
[[ "$foreign_database_count" == 0 ]] || fail "Mongo cell contains foreign databases"
atomic_env "$CELL_ROOT/fixture.env" \
  "cell=mongo6" \
  "model_database=$MODEL_DATABASE" \
  "viewer_database=$VIEWER_DATABASE" \
  "model_collections=$model_collections" \
  "mcp_audit_count=$mcp_audit_count" \
  "sales_order_count=$sales_order_count" \
  "geo_station_count=$geo_station_count" \
  "viewer_collections=$viewer_collections" \
  "list_presets_count=$list_presets_count" \
  "foreign_database_count=$foreign_database_count" \
  "status=verified"

PHASE="mongo-data-cleanup"
[[ "$(mongo_eval "print(db.getSiblingDB('$MODEL_DATABASE').dropDatabase().ok)")" == 1 ]] || \
  fail "model database cleanup failed"
[[ "$(mongo_eval "print(db.getSiblingDB('$VIEWER_DATABASE').dropDatabase().ok)")" == 1 ]] || \
  fail "viewer database cleanup failed"
remaining_foreign_databases="$(mongo_eval "const keep=new Set(['admin','config','local']); print(db.adminCommand({listDatabases:1,nameOnly:true}).databases.filter(x=>!keep.has(x.name)).length)")"
[[ "$remaining_foreign_databases" == 0 ]] || fail "Mongo data cleanup retained a user database"

PHASE="mongo-resource-cleanup"
cleanup_mongo || fail "run-scoped Mongo resource cleanup failed"

PHASE="source-after"
SOURCE_AFTER="$(create_source_seal "$RUN_ROOT/source-after.tsv" | tail -n 1)"
[[ "$SOURCE_BEFORE" == "$SOURCE_AFTER" ]] || fail "protected Mongo lane source changed"
cmp -s "$RUN_ROOT/source-before.tsv" "$RUN_ROOT/source-after.tsv" || \
  fail "protected Mongo lane source manifest changed"

PHASE="negative-probes"
python3 "$REPORT_TOOL" negative --output "$RUN_ROOT/negative/probes.tsv"

PHASE="merge-subset"
python3 "$REPORT_TOOL" merge-subset \
  --lane "$LANE" \
  --outer-marker "$OUTER_MARKER" \
  --manifest "$VARIANT_ROOT/evidence/report-manifest.json" \
  --output "$RUN_ROOT/final"
python3 "$REPORT_TOOL" verify-manifest \
  --outer-marker "$OUTER_MARKER" \
  --manifest "$RUN_ROOT/final/report-manifest.json"
FINAL_REPORT_MANIFEST_SHA256="$(sha256_file "$RUN_ROOT/final/report-manifest.json")"

PHASE="run-log-flush"
close_run_log || fail "run log tee did not flush successfully"

PHASE="sensitive-pattern-negatives"
verify_sensitive_patterns "$RUN_ROOT/negative/sensitive-probes.tsv"

PHASE="sensitive-scan"
SENSITIVE_SCAN_ARGS=()
for pattern in "${SENSITIVE_PATTERNS[@]}"; do
  SENSITIVE_SCAN_ARGS+=(-e "$pattern")
done
if rg -l --hidden \
  --glob '!run-status.env' \
  --glob '!sensitive-scan.matches' \
  "${SENSITIVE_SCAN_ARGS[@]}" \
  "$RUN_ROOT" > "$RUN_ROOT/sensitive-scan.matches"; then
  fail "run-owned Mongo evidence contains credentials"
else
  sensitive_scan_rc=$?
  [[ "$sensitive_scan_rc" -eq 1 ]] || \
    fail "sensitive evidence scan failed with rg exit code $sensitive_scan_rc"
fi
rm -f -- "$RUN_ROOT/sensitive-scan.matches"
atomic_env "$RUN_ROOT/sensitive-scan.env" \
  "patterns=${#SENSITIVE_PATTERNS[@]}" \
  "status=passed"

PHASE="completed"
v934_write_run_status 0
RUN_STATUS_SHA256="$(sha256_file "$RUN_ROOT/run-status.env")"
atomic_env "$RUN_ROOT/summary.env" \
  "run_id=$RUN_ID" \
  "runner=failsafe" \
  "lane=$LANE" \
  "git_head=$GIT_HEAD" \
  "variants=1" \
  "reports=4" \
  "testcase_nodes=30" \
  "failures=0" \
  "errors=0" \
  "skipped=0" \
  "source_before=$SOURCE_BEFORE" \
  "source_after=$SOURCE_AFTER" \
  "outer_marker_sha256=$OUTER_MARKER_SHA256" \
  "contract_sha256=$CONTRACT_SHA256" \
  "final_report_manifest_sha256=$FINAL_REPORT_MANIFEST_SHA256" \
  "run_status_sha256=$RUN_STATUS_SHA256" \
  "resource_sha256=$(sha256_file "$CELL_ROOT/resource.env")" \
  "fixture_sha256=$(sha256_file "$CELL_ROOT/fixture.env")" \
  "cleanup_sha256=$(sha256_file "$CELL_ROOT/cleanup.env")" \
  "negative_probes=12/12" \
  "negative_sha256=$(sha256_file "$RUN_ROOT/negative/probes.tsv")" \
  "sensitive_negative_probes=6/6" \
  "sensitive_negative_sha256=$(sha256_file "$RUN_ROOT/negative/sensitive-probes.tsv")" \
  "sensitive_scan_sha256=$(sha256_file "$RUN_ROOT/sensitive-scan.env")" \
  "resource_residue=0/0" \
  "status=passed"

PHASE="candidate-manifest"
python3 "$REPORT_TOOL" create-candidate \
  --outer-marker "$OUTER_MARKER" \
  --run-root "$RUN_ROOT" \
  --output "$RUN_ROOT/candidate-manifest.json"
python3 "$REPORT_TOOL" verify-candidate \
  --candidate "$RUN_ROOT/candidate-manifest.json"
PHASE="completed"

v934_disarm_run_status_traps
echo "[v934-external-mongo] PASS run=$RUN_ID reports=4 testcase_nodes=30 F0/E0/S0 evidence=$RUN_ROOT"
