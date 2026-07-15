#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_PATH="$ROOT_DIR/scripts/verify-v934-external-vector.sh"
STEP3_DIR="$ROOT_DIR/scripts/v934/step3"
CONTRACT="$STEP3_DIR/external-matrix-contract.json"
REPORT_TOOL="$STEP3_DIR/external_matrix_report_tool.py"
AUTHORITY_LIB="$ROOT_DIR/scripts/v934/authority_runner_lib.sh"
SHARED_CONTEXT_LIB="$STEP3_DIR/external_shared_context.sh"
DEFERRED_INVENTORY="$ROOT_DIR/scripts/v934/successor/step2/deferred-step3.tsv"
MODEL_REPORTS="$ROOT_DIR/addons/foggy-dataset-model-vector/target/failsafe-reports"
STORE_REPORTS="$ROOT_DIR/addons/foggy-dataset-vector/target/failsafe-reports"
CLEAN_MODULES="foggy-core,foggy-bean-copy,foggy-fsscript,foggy-dataset,foggy-dataset-demo,foggy-dataset-model,addons/foggy-dataset-vector,addons/foggy-dataset-model-vector"

RUNNER_NAME="failsafe"
LANE="external-vector"
CELL="milvus24"
VARIANT="milvus24-embedding"
MILVUS_IMAGE_REF="milvusdb/milvus@sha256:212ec3cd86e35ceda1892adae3bb718278b0e8187b4c797725d98270bc3dcfa7"
MILVUS_IMAGE_ID="sha256:212ec3cd86e35ceda1892adae3bb718278b0e8187b4c797725d98270bc3dcfa7"
ETCD_IMAGE_REF="quay.io/coreos/etcd@sha256:89b6debd43502d1088f3e02f39442fd3e951aa52bee846ed601cf4477114b89e"
ETCD_IMAGE_ID="sha256:89b6debd43502d1088f3e02f39442fd3e951aa52bee846ed601cf4477114b89e"
MINIO_IMAGE_REF="minio/minio@sha256:6d770d7f255cda1f18d841ffc4365cb7e0d237f6af6a15fcdb587480cd7c3b93"
MINIO_IMAGE_ID="sha256:6d770d7f255cda1f18d841ffc4365cb7e0d237f6af6a15fcdb587480cd7c3b93"
MILVUS_VERSION="v2.4.4"
MILVUS_GIT_COMMIT="8e7f36d9"
ETCD_VERSION="3.5.5"
ETCD_GIT_SHA="19002cfc6"
MINIO_VERSION="RELEASE.2023-03-20T20-16-18Z"
MINIO_COMMIT="05444a0f6af8389b9bb85280fc31337c556d4300"
STORE_COLLECTION="v934_vector_store"
MODEL_COLLECTION="foggy_test_documents"
VECTOR_DIMENSION="8"
DOCUMENT_COUNT="5"
NETWORK=""
MILVUS_CONTAINER=""
ETCD_CONTAINER=""
MINIO_CONTAINER=""
MILVUS_VOLUME=""
ETCD_VOLUME=""
MINIO_VOLUME=""
RUN_LOG_FIFO=""
RUN_LOG_TEE_PID=""
RUN_LOG_OPEN=false
EPHEMERAL_SECRET_COUNT=0
SIGNAL_PROBE_MODE="${V934_EXTERNAL_VECTOR_SIGNAL_PROBE:-false}"
SHARED_CHILD_MODE=false

SENSITIVE_PATTERNS=(
  '(?i)(?:MINIO_ROOT_USER|MINIO_ROOT_PASSWORD|MINIO_ACCESS_KEY_ID|MINIO_SECRET_ACCESS_KEY)[[:space:]]*[:=][[:space:]]*[^[:space:]]+'
  '(?i)"?(?:password|passwd|pwd|credential|credentials|api[-_]?key|access[-_]?token|refresh[-_]?token|auth[-_]?token|secret|authorization)"?[[:space:]]*[:=][[:space:]]*"?[^"[:space:],}]+'
  '(?i)(?:authorization[[:space:]]*[:=][[:space:]]*)?bearer[[:space:]]+[A-Za-z0-9._~+/-]+'
  '(?i)s3://[^/@:[:space:]]+:[^/@[:space:]]+@'
  '(?i)(?:--password|--passwd|--pwd)(?:=|[[:space:]])[^[:space:]]+'
)

fail() {
  echo "[v934-external-vector] ERROR: $*" >&2
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
  python3 "$REPORT_TOOL" check-worktree --lane "$LANE"
}

assert_clean_targets_absent() {
  local module
  local -a modules=(
    foggy-core foggy-bean-copy foggy-fsscript foggy-dataset foggy-dataset-demo
    foggy-dataset-model addons/foggy-dataset-vector addons/foggy-dataset-model-vector
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
  local index pattern
  local -a labels=(minio-env json-password api-key auth-header s3-uri cli-password)
  local -a fixtures=(
    'MINIO_ROOT_PASSWORD=x'
    '{"password": "x"}'
    'API_KEY=x'
    'Authorization: Bearer x'
    's3://u:p@127.0.0.1/bucket'
    '--password x'
  )
  local -a args=()
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

verify_ephemeral_secrets_absent() {
  local secret_index=0 secret scan_rc
  local matches="$RUN_ROOT/.ephemeral-secret.matches"
  for secret in "$MINIO_USER" "$MINIO_SECRET"; do
    secret_index=$((secret_index + 1))
    if rg -l -F --hidden -- "$secret" "$RUN_ROOT" > "$matches"; then
      fail "run-owned Vector evidence contains ephemeral credential $secret_index"
    else
      scan_rc=$?
      [[ "$scan_rc" -eq 1 ]] || \
        fail "ephemeral credential scan failed with rg exit code $scan_rc"
    fi
  done
  rm -f -- "$matches"
  EPHEMERAL_SECRET_COUNT="$secret_index"
  [[ "$EPHEMERAL_SECRET_COUNT" -eq 2 ]] || \
    fail "ephemeral credential scan count differs"
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
    "variant_key": "milvus24-embedding",
    "infra_kind": "vector",
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
      kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || break
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

vector_resources_absent() {
  local names volumes networks labelled_containers labelled_volumes labelled_networks
  docker info >/dev/null 2>&1 || return 2
  names="$(docker ps -a --format '{{.Names}}')" || return 2
  volumes="$(docker volume ls -q)" || return 2
  networks="$(docker network ls --format '{{.Name}}')" || return 2
  labelled_containers="$(docker ps -aq --filter "label=com.foggy.v934.external-run=$RUN_ID")" || return 2
  labelled_volumes="$(docker volume ls -q --filter "label=com.foggy.v934.external-run=$RUN_ID")" || return 2
  labelled_networks="$(docker network ls -q --filter "label=com.foggy.v934.external-run=$RUN_ID")" || return 2
  ! grep -Fxq "$MILVUS_CONTAINER" <<< "$names" \
    && ! grep -Fxq "$ETCD_CONTAINER" <<< "$names" \
    && ! grep -Fxq "$MINIO_CONTAINER" <<< "$names" \
    && ! grep -Fxq "$MILVUS_VOLUME" <<< "$volumes" \
    && ! grep -Fxq "$ETCD_VOLUME" <<< "$volumes" \
    && ! grep -Fxq "$MINIO_VOLUME" <<< "$volumes" \
    && ! grep -Fxq "$NETWORK" <<< "$networks" \
    && [[ -z "$labelled_containers" && -z "$labelled_volumes" && -z "$labelled_networks" ]]
}

cleanup_vector() {
  local cleanup_code=0 labelled=""
  local -a resources=()
  if [[ -n "$NETWORK" ]]; then
    labelled="$(docker ps -aq --filter "label=com.foggy.v934.external-run=$RUN_ID")" || cleanup_code=1
    if [[ -n "${labelled:-}" ]]; then
      mapfile -t resources <<< "$labelled"
      docker rm -f -- "${resources[@]}" >/dev/null 2>&1 || cleanup_code=1
    fi
    labelled="$(docker volume ls -q --filter "label=com.foggy.v934.external-run=$RUN_ID")" || cleanup_code=1
    if [[ -n "${labelled:-}" ]]; then
      mapfile -t resources <<< "$labelled"
      docker volume rm -f -- "${resources[@]}" >/dev/null 2>&1 || cleanup_code=1
    fi
    labelled="$(docker network ls -q --filter "label=com.foggy.v934.external-run=$RUN_ID")" || cleanup_code=1
    if [[ -n "${labelled:-}" ]]; then
      mapfile -t resources <<< "$labelled"
      docker network rm -- "${resources[@]}" >/dev/null 2>&1 || cleanup_code=1
    fi
    vector_resources_absent || cleanup_code=1
  fi
  if [[ -d "${CELL_ROOT:-}" ]]; then
    if [[ "$cleanup_code" -eq 0 ]]; then
      atomic_env "$CELL_ROOT/cleanup.env" \
        "cell=$CELL" \
        "network=$NETWORK" \
        "milvus_container=$MILVUS_CONTAINER" \
        "etcd_container=$ETCD_CONTAINER" \
        "minio_container=$MINIO_CONTAINER" \
        "milvus_volume=$MILVUS_VOLUME" \
        "etcd_volume=$ETCD_VOLUME" \
        "minio_volume=$MINIO_VOLUME" \
        "container_residue=0" \
        "volume_residue=0" \
        "network_residue=0" \
        "status=passed" || cleanup_code=1
    else
      atomic_env "$CELL_ROOT/cleanup.env" \
        "cell=$CELL" \
        "network=$NETWORK" \
        "milvus_container=$MILVUS_CONTAINER" \
        "etcd_container=$ETCD_CONTAINER" \
        "minio_container=$MINIO_CONTAINER" \
        "milvus_volume=$MILVUS_VOLUME" \
        "etcd_volume=$ETCD_VOLUME" \
        "minio_volume=$MINIO_VOLUME" \
        "container_residue=unknown" \
        "volume_residue=unknown" \
        "network_residue=unknown" \
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
  if ! cleanup_vector; then
    PHASE="vector-cleanup-failed"
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

post_json() {
  local path="$1" payload="$2"
  curl -fsS --connect-timeout 5 --max-time 30 \
    -X POST -H 'Content-Type: application/json' \
    "$MILVUS_BASE_URL$path" -d "$payload"
}

list_collections() {
  post_json /v2/vectordb/collections/list '{"dbName":"default"}'
}

if [[ "${1:-}" == --shared-child ]]; then
  [[ "$#" -eq 2 ]] || fail "usage: $SCRIPT_PATH --shared-child RUN_ID"
  SHARED_CHILD_MODE=true
  RUN_ID="$2"
else
  [[ "$#" -le 1 ]] || fail "usage: $SCRIPT_PATH [RUN_ID]"
  RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
fi
[[ "$SIGNAL_PROBE_MODE" == false || "$SIGNAL_PROBE_MODE" == true ]] || \
  fail "V934_EXTERNAL_VECTOR_SIGNAL_PROBE must be true or false"
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ && "$RUN_ID" != . && "$RUN_ID" != .. ]] || \
  fail "unsafe run id: $RUN_ID"

for command_name in cmp curl cut date docker flock git grep jq mkfifo mv mvn openssl python3 readlink rg sed seq sha256sum sleep tee; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command missing: $command_name"
done
for required_file in "$SCRIPT_PATH" "$CONTRACT" "$REPORT_TOOL" "$AUTHORITY_LIB" "$SHARED_CONTEXT_LIB" "$DEFERRED_INVENTORY"; do
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
# shellcheck source=scripts/v934/step3/external_shared_context.sh
source "$SHARED_CONTEXT_LIB"

if [[ "$SHARED_CHILD_MODE" == true ]]; then
  v934_external_prepare_shared_child "$ROOT_DIR" "$RUN_ID" "$LANE" || exit 1
  RUN_ROOT="$V934_EXTERNAL_SHARED_LANE_ROOT"
  OUTER_MARKER="$V934_EXTERNAL_SHARED_LANE_MARKER"
else
  v934_acquire_authority_lock "$ROOT_DIR" "v934-external-vector" || exit 1
  RUN_ROOT="$ROOT_DIR/target/v934-step3-external-matrix/runs/$RUN_ID"
  [[ ! -e "$RUN_ROOT" ]] || fail "run root already exists: $RUN_ROOT"
  mkdir -p "$RUN_ROOT"
  OUTER_MARKER="$RUN_ROOT/run-context.json"
fi
mkdir -p "$RUN_ROOT/variants/$VARIANT" "$RUN_ROOT/cells/$CELL" "$RUN_ROOT/negative"
CELL_ROOT="$RUN_ROOT/cells/$CELL"
VARIANT_ROOT="$RUN_ROOT/variants/$VARIANT"
MARKER="$VARIANT_ROOT/run-marker.json"
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
SCOPE_HASH="$(printf '%s\n' "$RUN_ID|$CELL" | sha256sum | cut -c1-12)"
BASE="v934ext-$CELL-$SCOPE_HASH"
NETWORK="$BASE-net"
MILVUS_CONTAINER="$BASE-milvus"
ETCD_CONTAINER="$BASE-etcd"
MINIO_CONTAINER="$BASE-minio"
MILVUS_VOLUME="$BASE-milvus-data"
ETCD_VOLUME="$BASE-etcd-data"
MINIO_VOLUME="$BASE-minio-data"
SELECTOR="$(jq -er --arg variant "$VARIANT" '.variants[] | select(.variant_key == $variant) | .selector' "$CONTRACT")"

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
if [[ "$SHARED_CHILD_MODE" != true ]]; then
  write_outer_marker
fi
OUTER_MARKER_SHA256="$(sha256_file "$OUTER_MARKER")"
python3 "$REPORT_TOOL" verify-outer --outer-marker "$OUTER_MARKER"

PHASE="image-preflight"
docker info >/dev/null
for image_binding in \
  "$MILVUS_IMAGE_REF|$MILVUS_IMAGE_ID" \
  "$ETCD_IMAGE_REF|$ETCD_IMAGE_ID" \
  "$MINIO_IMAGE_REF|$MINIO_IMAGE_ID"; do
  image_ref="${image_binding%%|*}"
  image_id="${image_binding#*|}"
  actual_image_id="$(docker image inspect "$image_ref" -f '{{.Id}}')" || \
    fail "frozen image is unavailable: $image_ref"
  [[ "$actual_image_id" == "$image_id" ]] || fail "local image id differs: $image_ref"
  repo_digests="$(docker image inspect "$image_ref" -f '{{json .RepoDigests}}')"
  grep -Fq "\"$image_ref\"" <<< "$repo_digests" || \
    fail "local image lacks frozen repo digest: $image_ref"
done
vector_resources_absent || fail "run-scoped Vector resource already exists"

PHASE="vector-start"
MINIO_USER="v934$(openssl rand -hex 8)"
MINIO_SECRET="$(openssl rand -hex 24)"
[[ -n "$MINIO_USER" && -n "$MINIO_SECRET" && "$MINIO_USER" != "$MINIO_SECRET" ]] || \
  fail "ephemeral MinIO credentials are invalid"
docker network create \
  --driver bridge \
  --label "com.foggy.v934.external-run=$RUN_ID" \
  --label "com.foggy.v934.external-cell=$CELL" \
  "$NETWORK" >/dev/null
for volume in "$ETCD_VOLUME" "$MINIO_VOLUME" "$MILVUS_VOLUME"; do
  docker volume create \
    --label "com.foggy.v934.external-run=$RUN_ID" \
    --label "com.foggy.v934.external-cell=$CELL" \
    "$volume" >/dev/null
done
docker run -d \
  --name "$ETCD_CONTAINER" \
  --label "com.foggy.v934.external-run=$RUN_ID" \
  --label "com.foggy.v934.external-cell=$CELL" \
  --network "$NETWORK" --network-alias etcd \
  --mount "type=volume,source=$ETCD_VOLUME,target=/etcd" \
  "$ETCD_IMAGE_REF" \
  /usr/local/bin/etcd \
  --name v934-etcd --data-dir /etcd \
  --listen-client-urls http://0.0.0.0:2379 \
  --advertise-client-urls http://etcd:2379 \
  --listen-peer-urls http://0.0.0.0:2380 \
  --initial-advertise-peer-urls http://etcd:2380 \
  --initial-cluster v934-etcd=http://etcd:2380 \
  --initial-cluster-state new >/dev/null
docker run -d \
  --name "$MINIO_CONTAINER" \
  --label "com.foggy.v934.external-run=$RUN_ID" \
  --label "com.foggy.v934.external-cell=$CELL" \
  --network "$NETWORK" --network-alias minio \
  --mount "type=volume,source=$MINIO_VOLUME,target=/data" \
  -e MINIO_ROOT_USER="$MINIO_USER" \
  -e MINIO_ROOT_PASSWORD="$MINIO_SECRET" \
  "$MINIO_IMAGE_REF" server /data --console-address ':9001' >/dev/null

etcd_ready=false
minio_ready=false
for _ in $(seq 1 120); do
  if docker exec "$ETCD_CONTAINER" /usr/local/bin/etcdctl \
      --endpoints=http://127.0.0.1:2379 endpoint health >/dev/null 2>&1; then
    etcd_ready=true
  fi
  if docker exec "$MINIO_CONTAINER" curl -fsS --connect-timeout 2 --max-time 5 \
      http://127.0.0.1:9000/minio/health/live >/dev/null 2>&1; then
    minio_ready=true
  fi
  [[ "$etcd_ready" == true && "$minio_ready" == true ]] && break
  sleep 0.5
done
[[ "$etcd_ready" == true && "$minio_ready" == true ]] || \
  fail "run-scoped etcd/MinIO did not become ready"

docker run -d \
  --name "$MILVUS_CONTAINER" \
  --label "com.foggy.v934.external-run=$RUN_ID" \
  --label "com.foggy.v934.external-cell=$CELL" \
  --network "$NETWORK" --network-alias milvus \
  --security-opt seccomp=unconfined \
  -p 127.0.0.1::19530/tcp \
  -p 127.0.0.1::9091/tcp \
  --mount "type=volume,source=$MILVUS_VOLUME,target=/var/lib/milvus" \
  -e ETCD_ENDPOINTS=etcd:2379 \
  -e MINIO_ADDRESS=minio:9000 \
  -e MINIO_ACCESS_KEY_ID="$MINIO_USER" \
  -e MINIO_SECRET_ACCESS_KEY="$MINIO_SECRET" \
  "$MILVUS_IMAGE_REF" milvus run standalone >/dev/null

grpc_mapping="$(docker port "$MILVUS_CONTAINER" 19530/tcp | tail -n 1)"
health_mapping="$(docker port "$MILVUS_CONTAINER" 9091/tcp | tail -n 1)"
[[ "$grpc_mapping" == 127.0.0.1:* && "$health_mapping" == 127.0.0.1:* ]] || \
  fail "Milvus ports must be dynamic loopback mappings"
GRPC_PORT="${grpc_mapping##*:}"
HEALTH_PORT="${health_mapping##*:}"
[[ "$GRPC_PORT" =~ ^[0-9]+$ && "$HEALTH_PORT" =~ ^[0-9]+$ && "$GRPC_PORT" != "$HEALTH_PORT" ]] || \
  fail "Milvus mapped ports are invalid"
MILVUS_BASE_URL="http://127.0.0.1:$GRPC_PORT"
milvus_ready=false
for _ in $(seq 1 240); do
  if [[ "$(curl -fsS --connect-timeout 2 --max-time 5 \
      "http://127.0.0.1:$HEALTH_PORT/healthz" 2>/dev/null || true)" == OK ]]; then
    milvus_ready=true
    break
  fi
  sleep 0.5
done
[[ "$milvus_ready" == true ]] || fail "run-scoped Milvus did not become ready"

PHASE="vector-identity"
network_json="$(docker network inspect "$NETWORK")"
network_created="$(jq -er '.[0].Created' <<< "$network_json")"
network_driver="$(jq -er '.[0].Driver' <<< "$network_json")"
network_internal="$(jq -r '.[0].Internal' <<< "$network_json")"
network_container_count="$(jq -er '.[0].Containers | length' <<< "$network_json")"
jq -e --arg e "$ETCD_CONTAINER" --arg i "$MINIO_CONTAINER" --arg m "$MILVUS_CONTAINER" '
  .[0] | .Driver == "bridge" and .Internal == false
  and ([.Containers[].Name] | sort) == ([$e, $i, $m] | sort)
' <<< "$network_json" >/dev/null || fail "Vector network topology differs"

for container_binding in \
  "$MILVUS_CONTAINER|$MILVUS_IMAGE_REF|$MILVUS_IMAGE_ID" \
  "$ETCD_CONTAINER|$ETCD_IMAGE_REF|$ETCD_IMAGE_ID" \
  "$MINIO_CONTAINER|$MINIO_IMAGE_REF|$MINIO_IMAGE_ID"; do
  container="${container_binding%%|*}"
  remainder="${container_binding#*|}"
  expected_ref="${remainder%%|*}"
  expected_id="${remainder#*|}"
  [[ "$(docker inspect -f '{{.Config.Image}}' "$container")" == "$expected_ref" ]] || \
    fail "container image reference differs: $container"
  [[ "$(docker inspect -f '{{.Image}}' "$container")" == "$expected_id" ]] || \
    fail "container image id differs: $container"
  [[ "$(docker inspect -f '{{index .Config.Labels "com.foggy.v934.external-run"}}' "$container")" == "$RUN_ID" ]] || \
    fail "container run label differs: $container"
  [[ "$(docker inspect -f '{{index .Config.Labels "com.foggy.v934.external-cell"}}' "$container")" == "$CELL" ]] || \
    fail "container cell label differs: $container"
done

milvus_mount_count="$(docker inspect -f '{{len .Mounts}}' "$MILVUS_CONTAINER")"
etcd_mount_count="$(docker inspect -f '{{len .Mounts}}' "$ETCD_CONTAINER")"
minio_mount_count="$(docker inspect -f '{{len .Mounts}}' "$MINIO_CONTAINER")"
milvus_mount_identity="$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/var/lib/milvus"}}{{.Name}}|{{.Destination}}|{{.Type}}{{end}}{{end}}' "$MILVUS_CONTAINER")"
etcd_mount_identity="$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/etcd"}}{{.Name}}|{{.Destination}}|{{.Type}}{{end}}{{end}}' "$ETCD_CONTAINER")"
minio_mount_identity="$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/data"}}{{.Name}}|{{.Destination}}|{{.Type}}{{end}}{{end}}' "$MINIO_CONTAINER")"
[[ "$milvus_mount_count/$etcd_mount_count/$minio_mount_count" == 1/1/1 ]] || \
  fail "Vector containers must each have exactly one mount"
[[ "$milvus_mount_identity" == "$MILVUS_VOLUME|/var/lib/milvus|volume" ]] || \
  fail "Milvus mount identity differs"
[[ "$etcd_mount_identity" == "$ETCD_VOLUME|/etcd|volume" ]] || fail "etcd mount identity differs"
[[ "$minio_mount_identity" == "$MINIO_VOLUME|/data|volume" ]] || fail "MinIO mount identity differs"

for volume in "$MILVUS_VOLUME" "$ETCD_VOLUME" "$MINIO_VOLUME"; do
  [[ "$(docker volume inspect -f '{{index .Labels "com.foggy.v934.external-run"}}' "$volume")" == "$RUN_ID" ]] || \
    fail "volume run label differs: $volume"
  [[ "$(docker volume inspect -f '{{index .Labels "com.foggy.v934.external-cell"}}' "$volume")" == "$CELL" ]] || \
    fail "volume cell label differs: $volume"
done
milvus_volume_created="$(docker volume inspect -f '{{.CreatedAt}}' "$MILVUS_VOLUME")"
etcd_volume_created="$(docker volume inspect -f '{{.CreatedAt}}' "$ETCD_VOLUME")"
minio_volume_created="$(docker volume inspect -f '{{.CreatedAt}}' "$MINIO_VOLUME")"
for created_at in "$network_created" "$milvus_volume_created" "$etcd_volume_created" "$minio_volume_created"; do
  [[ "$(date -d "$created_at" +%s)" -ge "$START_EPOCH" ]] || fail "Vector resource predates run marker"
done

etcd_identity="$(docker exec "$ETCD_CONTAINER" /usr/local/bin/etcd --version)"
actual_etcd_version="$(sed -n 's/^etcd Version: //p' <<< "$etcd_identity")"
actual_etcd_git_sha="$(sed -n 's/^Git SHA: //p' <<< "$etcd_identity")"
minio_identity="$(docker exec "$MINIO_CONTAINER" minio --version)"
actual_minio_version="$(sed -n 's/^minio version \([^ ]*\).*/\1/p' <<< "$minio_identity")"
actual_minio_commit="$(sed -n 's/.*commit-id=\([^)]*\)).*/\1/p' <<< "$minio_identity")"
milvus_metrics="$(curl -fsS --connect-timeout 5 --max-time 30 \
  "http://127.0.0.1:$HEALTH_PORT/metrics")"
actual_milvus_version="$(sed -n 's/^milvus_build_info{[^}]*version="\([^"]*\)"[^}]*}.*/\1/p' <<< "$milvus_metrics")"
actual_milvus_git_commit="$(sed -n 's/^milvus_build_info{[^}]*git_commit="\([^"]*\)"[^}]*}.*/\1/p' <<< "$milvus_metrics")"
[[ "$actual_etcd_version" == "$ETCD_VERSION" && "$actual_etcd_git_sha" == "$ETCD_GIT_SHA" ]] || \
  fail "etcd build identity differs"
[[ "$actual_minio_version" == "$MINIO_VERSION" && "$actual_minio_commit" == "$MINIO_COMMIT" ]] || \
  fail "MinIO build identity differs"
[[ "$actual_milvus_version" == "$MILVUS_VERSION" && "$actual_milvus_git_commit" == "$MILVUS_GIT_COMMIT" ]] || \
  fail "Milvus build identity differs"

initial_response="$(list_collections)"
jq -e '.code == 0 and .data == []' <<< "$initial_response" >/dev/null || \
  fail "fresh Milvus database is not empty"
atomic_env "$CELL_ROOT/fixture-before.tsv" $'collection_count\t0'
atomic_env "$CELL_ROOT/resource.env" \
  "run_id=$RUN_ID" \
  "cell=$CELL" \
  "network=$NETWORK" \
  "network_created=$network_created" \
  "network_driver=$network_driver" \
  "network_internal=$network_internal" \
  "network_container_count=$network_container_count" \
  "milvus_container=$MILVUS_CONTAINER" \
  "etcd_container=$ETCD_CONTAINER" \
  "minio_container=$MINIO_CONTAINER" \
  "milvus_image_ref=$MILVUS_IMAGE_REF" \
  "milvus_image_id=$MILVUS_IMAGE_ID" \
  "etcd_image_ref=$ETCD_IMAGE_REF" \
  "etcd_image_id=$ETCD_IMAGE_ID" \
  "minio_image_ref=$MINIO_IMAGE_REF" \
  "minio_image_id=$MINIO_IMAGE_ID" \
  "grpc_mapped_port=$grpc_mapping" \
  "health_mapped_port=$health_mapping" \
  "milvus_mount_count=$milvus_mount_count" \
  "etcd_mount_count=$etcd_mount_count" \
  "minio_mount_count=$minio_mount_count" \
  "milvus_mount_identity=$milvus_mount_identity" \
  "etcd_mount_identity=$etcd_mount_identity" \
  "minio_mount_identity=$minio_mount_identity" \
  "milvus_volume=$MILVUS_VOLUME" \
  "etcd_volume=$ETCD_VOLUME" \
  "minio_volume=$MINIO_VOLUME" \
  "milvus_volume_created=$milvus_volume_created" \
  "etcd_volume_created=$etcd_volume_created" \
  "minio_volume_created=$minio_volume_created" \
  "milvus_version=$actual_milvus_version" \
  "milvus_git_commit=$actual_milvus_git_commit" \
  "etcd_version=$actual_etcd_version" \
  "etcd_git_sha=$actual_etcd_git_sha" \
  "minio_version=$actual_minio_version" \
  "minio_commit=$actual_minio_commit" \
  "topology=standalone" \
  "auth_mode=ephemeral-minio-root" \
  "credentials_distinct=true" \
  "initial_collection_count=0" \
  "status=verified"

if [[ "$SIGNAL_PROBE_MODE" == true ]]; then
  PHASE="signal-probe-ready"
  atomic_env "$RUN_ROOT/signal-probe-ready.env" \
    "run_id=$RUN_ID" \
    "network=$NETWORK" \
    "milvus_container=$MILVUS_CONTAINER" \
    "etcd_container=$ETCD_CONTAINER" \
    "minio_container=$MINIO_CONTAINER" \
    "milvus_volume=$MILVUS_VOLUME" \
    "etcd_volume=$ETCD_VOLUME" \
    "minio_volume=$MINIO_VOLUME" \
    "status=ready"
  echo "[v934-external-vector] SIGNAL_PROBE_READY run=$RUN_ID"
  while :; do
    sleep 1
  done
fi

PHASE="variant-$VARIANT"
rm -rf -- "$MODEL_REPORTS" "$STORE_REPORTS"
write_variant_marker "$MARKER"
echo "[v934-external-vector] running variant=$VARIANT"
(cd "$ROOT_DIR" && \
  mvn -q \
    -P'!multi-db,!model-lifecycle,!query-cache-real-query' \
    -pl addons/foggy-dataset-model-vector,addons/foggy-dataset-vector -am \
    -Dit.test="$SELECTOR" \
    -Dv934.vector.milvus.host=127.0.0.1 \
    -Dv934.vector.milvus.port="$GRPC_PORT" \
    -DskipUnitTests=true \
    -DskipITs=false \
    -Dfailsafe.rerunFailingTestsCount=0 \
    -Dfailsafe.failIfNoTests=false \
    -Dfailsafe.failIfNoSpecifiedTests=false \
    -Dv934.external.run-id="$RUN_ID" \
    -Dv934.external.variant="$VARIANT" \
    verify)
python3 "$REPORT_TOOL" seal-bytecode \
  --lane "$LANE" --output "$VARIANT_ROOT/bytecode.tsv"
python3 "$REPORT_TOOL" collect \
  --variant "$VARIANT" \
  --outer-marker "$OUTER_MARKER" \
  --run-marker "$MARKER" \
  --report-root "addons/foggy-dataset-model-vector=$MODEL_REPORTS" \
  --report-root "addons/foggy-dataset-vector=$STORE_REPORTS" \
  --output "$VARIANT_ROOT/evidence"

PHASE="vector-fixture"
final_response="$(list_collections)"
jq -e --arg collection "$STORE_COLLECTION" '
  .code == 0 and (.data | sort) == [$collection]
' <<< "$final_response" >/dev/null || fail "final Milvus collection set differs"

collection_payload="$(jq -nc --arg c "$STORE_COLLECTION" '{dbName:"default",collectionName:$c}')"
describe_response="$(post_json /v2/vectordb/collections/describe "$collection_payload")"
field_names="$(jq -er '.data.fields | map(.name) | sort | join(",")' <<< "$describe_response")"
embedding_dimension="$(jq -er '
  .data.fields[] | select(.name == "embedding")
  | ([.params[]? | select(.key == "dim") | .value][0]
     // .elementTypeParams.dim // .dimension // empty) | tostring
' <<< "$describe_response")"
jq -e '
  .code == 0
  and (.data.fields | length) == 4
  and any(.data.fields[]; .name == "id" and .primaryKey == true)
  and any(.data.fields[]; .name == "content")
  and any(.data.fields[]; .name == "metadata")
  and any(.data.fields[]; .name == "embedding" and .type == "FloatVector")
' <<< "$describe_response" >/dev/null || fail "VectorStore collection schema differs"
[[ "$field_names" == content,embedding,id,metadata && "$embedding_dimension" == "$VECTOR_DIMENSION" ]] || \
  fail "VectorStore field identity differs"

embedding_index_name="$(jq -er '
  [.data.indexes[] | select(.fieldName == "embedding")] as $matches
  | if ($matches | length) == 1 then $matches[0].indexName else empty end
' <<< "$describe_response")"
[[ -n "$embedding_index_name" ]] || fail "VectorStore embedding index name is missing"
index_payload="$(jq -nc --arg c "$STORE_COLLECTION" --arg i "$embedding_index_name" \
  '{dbName:"default",collectionName:$c,indexName:$i}')"
index_response="$(post_json /v2/vectordb/indexes/describe "$index_payload")"
index_type="$(jq -er --arg index "$embedding_index_name" '
  (.data | if type == "array" then . else [.] end)
  | map(select(.fieldName == "embedding" and .indexName == $index))
  | if length == 1 then .[0].indexType else empty end' \
  <<< "$index_response")"
metric_type="$(jq -er --arg index "$embedding_index_name" '
  (.data | if type == "array" then . else [.] end)
  | map(select(.fieldName == "embedding" and .indexName == $index))
  | if length == 1 then .[0].metricType else empty end' \
  <<< "$index_response")"
[[ "$(jq -r '.code' <<< "$index_response")" == 0 && "$index_type" == FLAT && "$metric_type" == COSINE ]] || \
  fail "VectorStore index identity differs"

query_payload="$(jq -nc --arg c "$STORE_COLLECTION" '{
  dbName:"default", collectionName:$c, filter:"id != \"\"",
  outputFields:["id","content","metadata"], limit:100, consistencyLevel:"Strong"
}')"
query_response="$(post_json /v2/vectordb/entities/query "$query_payload")"
jq -e --argjson count "$DOCUMENT_COUNT" '.code == 0 and (.data | length) == $count' \
  <<< "$query_response" >/dev/null || fail "VectorStore Strong query count differs"
fixture_rows="$(jq -r '
  .data | sort_by(.id)[]
  | (.metadata | if type == "string" then (@base64d | fromjson) else . end) as $metadata
  | [.id, .content, $metadata.template_type, $metadata.model_name] | @tsv
' <<< "$query_response")"
expected_rows=$'qt1\t最近一周各品牌销售情况\tdsl\tFactSalesQueryModel\nqt2\t本月销售数据统计分析\tdsl\tFactSalesQueryModel\nqt3\t销售趋势分析指南\tguide\tFactSalesQueryModel\nqt4\t库存不足商品查询\tdsl\tFactInventoryQueryModel\nqt5\t客户购买行为分析\tguide\tFactOrderQueryModel'
[[ "$fixture_rows" == "$expected_rows" ]] || fail "VectorStore Strong query fixture differs"
fixture_after_tmp="$CELL_ROOT/fixture-after.tsv.$$.$RANDOM.tmp"
printf 'id\tcontent\ttemplate_type\tmodel_name\n%s\n' "$fixture_rows" > "$fixture_after_tmp"
mv -f -- "$fixture_after_tmp" "$CELL_ROOT/fixture-after.tsv"

stats_response="$(post_json /v2/vectordb/collections/get_stats "$collection_payload")"
jq -e '.code == 0 and (.data.rowCount | type) == "number" and .data.rowCount >= 0' \
  <<< "$stats_response" >/dev/null || fail "VectorStore stats response differs"
atomic_env "$CELL_ROOT/fixture.env" \
  "cell=$CELL" \
  "database=default" \
  "initial_collection_count=0" \
  "final_collection_count=1" \
  "final_collections=$STORE_COLLECTION" \
  "model_collection_present=false" \
  "store_collection=$STORE_COLLECTION" \
  "store_row_count=$DOCUMENT_COUNT" \
  "field_names=$field_names" \
  "embedding_dimension=$embedding_dimension" \
  "index_type=$index_type" \
  "metric_type=$metric_type" \
  "before_snapshot_sha256=$(sha256_file "$CELL_ROOT/fixture-before.tsv")" \
  "after_snapshot_sha256=$(sha256_file "$CELL_ROOT/fixture-after.tsv")" \
  "status=verified"

PHASE="vector-data-cleanup"
drop_response="$(post_json /v2/vectordb/collections/drop "$collection_payload")"
jq -e '.code == 0' <<< "$drop_response" >/dev/null || fail "VectorStore collection drop failed"
empty_after_drop=false
for _ in $(seq 1 60); do
  if list_collections | jq -e '.code == 0 and .data == []' >/dev/null 2>&1; then
    empty_after_drop=true
    break
  fi
  sleep 0.5
done
[[ "$empty_after_drop" == true ]] || fail "Milvus retained a collection after cleanup"

PHASE="vector-resource-cleanup"
cleanup_vector || fail "run-scoped Vector resource cleanup failed"

PHASE="source-after"
SOURCE_AFTER="$(create_source_seal "$RUN_ROOT/source-after.tsv" | tail -n 1)"
[[ "$SOURCE_BEFORE" == "$SOURCE_AFTER" ]] || fail "protected Vector lane source changed"
cmp -s "$RUN_ROOT/source-before.tsv" "$RUN_ROOT/source-after.tsv" || \
  fail "protected Vector lane source manifest changed"

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
verify_ephemeral_secrets_absent
SENSITIVE_SCAN_ARGS=()
for pattern in "${SENSITIVE_PATTERNS[@]}"; do
  SENSITIVE_SCAN_ARGS+=(-e "$pattern")
done
if rg -l --hidden \
  --glob '!run-status.env' \
  --glob '!sensitive-scan.matches' \
  "${SENSITIVE_SCAN_ARGS[@]}" \
  "$RUN_ROOT" > "$RUN_ROOT/sensitive-scan.matches"; then
  fail "run-owned Vector evidence contains credentials"
else
  sensitive_scan_rc=$?
  [[ "$sensitive_scan_rc" -eq 1 ]] || \
    fail "sensitive evidence scan failed with rg exit code $sensitive_scan_rc"
fi
rm -f -- "$RUN_ROOT/sensitive-scan.matches"
atomic_env "$RUN_ROOT/sensitive-scan.env" \
  "patterns=${#SENSITIVE_PATTERNS[@]}" \
  "ephemeral_secrets=$EPHEMERAL_SECRET_COUNT" \
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
  "reports=2" \
  "testcase_nodes=20" \
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
  "resource_residue=0/0/0" \
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
echo "[v934-external-vector] PASS run=$RUN_ID reports=2 testcase_nodes=20 F0/E0/S0 evidence=$RUN_ROOT"
