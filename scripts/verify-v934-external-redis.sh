#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_PATH="$ROOT_DIR/scripts/verify-v934-external-redis.sh"
STEP3_DIR="$ROOT_DIR/scripts/v934/step3"
CONTRACT="$STEP3_DIR/external-matrix-contract.json"
REPORT_TOOL="$STEP3_DIR/external_matrix_report_tool.py"
AUTHORITY_LIB="$ROOT_DIR/scripts/v934/authority_runner_lib.sh"
SHARED_CONTEXT_LIB="$STEP3_DIR/external_shared_context.sh"
DEFERRED_INVENTORY="$ROOT_DIR/scripts/v934/successor/step2/deferred-step3.tsv"
REPORTS_DIR="$ROOT_DIR/addons/foggy-dataset-model-cache/target/failsafe-reports"
CLEAN_MODULES="foggy-bean-copy,foggy-core,foggy-fsscript,foggy-dataset,foggy-dataset-demo,foggy-dataset-model,addons/foggy-dataset-model-cache"

RUNNER_NAME="failsafe"
LANE="external-redis"
REDIS_IMAGE_REF="redis@sha256:3b73847e72874be07e6657b129a94761662b79bc0f679273757d4218573b2a98"
REDIS_IMAGE_ID="sha256:3b73847e72874be07e6657b129a94761662b79bc0f679273757d4218573b2a98"
REDIS_VERSION="7.4.6"
REDIS_CONTAINER=""
REDIS_VOLUME=""
REDIS_ARMED=false
RUN_LOG_FIFO=""
RUN_LOG_TEE_PID=""
RUN_LOG_OPEN=false
SIGNAL_PROBE_MODE="${V934_EXTERNAL_REDIS_SIGNAL_PROBE:-false}"
SHARED_CHILD_MODE=false

SENSITIVE_PATTERNS=(
  '(?i)(?:REDIS_PASSWORD|REDIS_USERNAME|REDIS_URI)'
  '(?i)"?(?:password|passwd|pwd|credential|credentials|api[-_]?key|access[-_]?token|refresh[-_]?token|auth[-_]?token|secret|authorization)"?[[:space:]]*[:=][[:space:]]*"?[^"[:space:],}]+'
  '(?i)(?:authorization[[:space:]]*[:=][[:space:]]*)?bearer[[:space:]]+[A-Za-z0-9._~+/-]{8,}'
  '(?i)redis://[^/@:[:space:]]+:[^/@[:space:]]+@'
  '(?i)(?:--password|--passwd|--pwd)(?:=|[[:space:]])[^[:space:]]+'
)

fail() {
  echo "[v934-external-redis] ERROR: $*" >&2
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

source_seal() {
  local output="$1"
  python3 - "$ROOT_DIR" "$output" <<'PY'
import hashlib
import os
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve(strict=True)
output = Path(sys.argv[2])
inputs = (
    "pom.xml",
    "foggy-bean-copy/pom.xml",
    "foggy-bean-copy/src/main",
    "foggy-core/pom.xml",
    "addons/foggy-dataset-model-cache/pom.xml",
    "addons/foggy-dataset-model-cache/src",
    "foggy-core/src/main",
    "foggy-dataset/pom.xml",
    "foggy-dataset/src/main",
    "foggy-dataset-demo/pom.xml",
    "foggy-dataset-demo/src/main",
    "foggy-dataset-model/pom.xml",
    "foggy-dataset-model/src/main",
    "foggy-fsscript/pom.xml",
    "foggy-fsscript/src/main",
)
paths: list[Path] = []
for value in inputs:
    candidate = root / value
    if candidate.is_symlink() or not candidate.exists():
        raise SystemExit(f"protected source path is missing or a symlink: {value}")
    if candidate.is_file():
        paths.append(candidate)
    else:
        for path in candidate.rglob("*"):
            if path.is_symlink():
                raise SystemExit(f"protected source tree contains a symlink: {path}")
            if path.is_file():
                paths.append(path)
rows: list[tuple[str, str, int]] = []
for path in sorted(set(paths), key=lambda item: item.relative_to(root).as_posix()):
    relative = path.relative_to(root).as_posix()
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    rows.append((relative, digest, path.stat().st_size))
combined = hashlib.sha256()
for relative, digest, size in rows:
    combined.update(relative.encode())
    combined.update(b"\0")
    combined.update(digest.encode())
    combined.update(b"\0")
    combined.update(str(size).encode())
    combined.update(b"\n")
content = "path\tsha256\tsize_bytes\n" + "".join(
    f"{relative}\t{digest}\t{size}\n" for relative, digest, size in rows
)
temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
temporary.write_text(content, encoding="utf-8")
os.replace(temporary, output)
print(combined.hexdigest())
PY
}

assert_protected_worktree_clean() {
  python3 "$REPORT_TOOL" check-worktree --lane "$LANE"
}

assert_clean_targets_absent() {
  local module
  local -a modules=(
    foggy-bean-copy foggy-core foggy-fsscript foggy-dataset
    foggy-dataset-demo foggy-dataset-model addons/foggy-dataset-model-cache
  )
  for module in "${modules[@]}"; do
    [[ ! -e "$ROOT_DIR/$module/target" ]] || \
      fail "explicit Maven clean left a module target: $module/target"
  done
}

seal_variant_bytecode() {
  local output="$1"
  python3 - "$ROOT_DIR" "$output" <<'PY'
import hashlib
import os
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve(strict=True)
output = Path(sys.argv[2])
modules = (
    "foggy-bean-copy", "foggy-core", "foggy-fsscript", "foggy-dataset",
    "foggy-dataset-demo", "foggy-dataset-model",
    "addons/foggy-dataset-model-cache",
)
rows = []
for module in modules:
    main_root = root / module / "target/classes"
    if main_root.is_symlink() or not main_root.is_dir():
        raise SystemExit(f"fresh main bytecode tree is missing: {module}")
    for tree_name in ("classes", "test-classes"):
        tree = root / module / "target" / tree_name
        if not tree.exists():
            continue
        if tree.is_symlink() or not tree.is_dir():
            raise SystemExit(f"bytecode tree is not a regular directory: {tree}")
        files = sorted(path for path in tree.rglob("*") if path.is_file() and not path.is_symlink())
        if tree_name == "classes" and not files:
            raise SystemExit(f"main bytecode tree is empty: {module}")
        digest = hashlib.sha256()
        for path in files:
            relative = path.relative_to(tree).as_posix()
            content_digest = hashlib.sha256(path.read_bytes()).hexdigest()
            for value in (relative, content_digest):
                encoded = value.encode("utf-8")
                digest.update(str(len(encoded)).encode("ascii"))
                digest.update(b":")
                digest.update(encoded)
                digest.update(b"\0")
        rows.append((module, tree_name, len(files), digest.hexdigest()))
if not any(module == "addons/foggy-dataset-model-cache" and tree == "test-classes" and count > 0
           for module, tree, count, _ in rows):
    raise SystemExit("cache test bytecode tree is empty")
content = "module\ttree\tfiles\tsha256\n" + "".join(
    f"{module}\t{tree}\t{count}\t{digest}\n"
    for module, tree, count, digest in rows
)
temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
temporary.write_text(content, encoding="utf-8")
os.replace(temporary, output)
PY
}

verify_sensitive_patterns() {
  local output="$1"
  local probe_file="$RUN_ROOT/.sensitive-pattern-probe"
  local temporary="${output}.$$.$RANDOM.tmp"
  local index
  local -a labels=(
    redis-env json-password api-key bearer redis-uri cli-password
  )
  local -a fixtures=(
    'REDIS_PASSWORD=fixture-secret'
    '{"password": "fixture-secret"}'
    'API_KEY=fixture-secret'
    'Authorization: Bearer fixture-token-123'
    'redis://fixture:fixture-password@127.0.0.1:6379'
    '--password fixture-secret'
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
  local variant="$1"
  local marker="$2"
  local selector="$3"
  python3 - "$marker" "$RUN_ID" "$GIT_HEAD" "$CONTRACT_SHA256" \
    "$variant" "$OUTER_MARKER_SHA256" "$selector" <<'PY'
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
    "variant_key": sys.argv[5],
    "infra_kind": "redis",
    "outer_marker_sha256": sys.argv[6],
    "selector": sys.argv[7],
}
temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
temporary.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
os.replace(temporary, output)
PY
}

close_run_log() {
  local attempt
  local tee_code=0

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
        if ! kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1; then
          break
        fi
        sleep 0.1
      done
      if kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1; then
        kill -KILL "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || true
      fi
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

redis_resource_absent() {
  local names labelled volume_names labelled_volumes
  docker info >/dev/null 2>&1 || return 2
  names="$(docker ps -a --format '{{.Names}}')" || return 2
  labelled="$(docker ps -aq --filter "label=com.foggy.v934.external-run=$RUN_ID")" || return 2
  volume_names="$(docker volume ls -q)" || return 2
  labelled_volumes="$(
    docker volume ls -q --filter "label=com.foggy.v934.external-run=$RUN_ID"
  )" || return 2
  ! grep -Fxq "$REDIS_CONTAINER" <<< "$names" \
    && ! grep -Fxq "$REDIS_VOLUME" <<< "$volume_names" \
    && [[ -z "$labelled" ]] \
    && [[ -z "$labelled_volumes" ]]
}

cleanup_redis() {
  local cleanup_code=0
  local labelled="" labelled_volumes=""
  local -a containers=()
  local -a volumes=()

  if [[ -n "$REDIS_CONTAINER" ]]; then
    labelled="$(docker ps -aq --filter "label=com.foggy.v934.external-run=$RUN_ID")" || cleanup_code=1
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
    REDIS_ARMED=false
  fi
  if [[ -n "$REDIS_CONTAINER" ]]; then
    redis_resource_absent || cleanup_code=1
  fi
  if [[ -d "${CELL_ROOT:-}" ]]; then
    if [[ "$cleanup_code" -eq 0 ]]; then
      atomic_env "$CELL_ROOT/cleanup.env" \
        "cell=redis7" \
        "container=$REDIS_CONTAINER" \
        "volume=$REDIS_VOLUME" \
        "container_residue=0" \
        "volume_residue=0" \
        "status=passed" || cleanup_code=1
    else
      atomic_env "$CELL_ROOT/cleanup.env" \
        "cell=redis7" \
        "container=$REDIS_CONTAINER" \
        "volume=$REDIS_VOLUME" \
        "status=failed" || true
    fi
  fi
  return "$cleanup_code"
}

record_run_status() {
  local exit_code="$1"
  local finalizer_code=0

  trap '' INT TERM HUP
  trap - EXIT
  set +e
  if ! cleanup_redis; then
    PHASE="redis-cleanup-failed"
    finalizer_code=1
  fi
  if ! close_run_log; then
    PHASE="run-log-flush-failed"
    finalizer_code=1
  fi
  if [[ "$finalizer_code" -ne 0 ]]; then
    exit_code=1
  fi
  if [[ "$exit_code" -eq 0 && "$PHASE" != completed ]]; then
    exit_code=1
  fi
  if [[ "$exit_code" -ne 0 ]]; then
    rm -f -- "$RUN_ROOT/summary.env" "$RUN_ROOT/candidate-manifest.json"
  fi
  v934_write_run_status "$exit_code" || exit_code=1
  exit "$exit_code"
}

run_variant() {
  local variant="$1"
  local selector="$2"
  local prefix="$3"
  local variant_root="$RUN_ROOT/variants/$variant"
  local marker="$variant_root/run-marker.json"

  [[ ! -e "$variant_root" ]] || fail "variant root already exists: $variant_root"
  rm -rf -- "$REPORTS_DIR"
  mkdir -p "$variant_root"
  write_variant_marker "$variant" "$marker" "$selector"
  echo "[v934-external-redis] running variant=$variant"
  (cd "$ROOT_DIR" && mvn -q \
    -P'!multi-db,!model-lifecycle,!query-cache-real-query' \
    -pl addons/foggy-dataset-model-cache -am \
    -Dit.test="$selector" \
    -Dv933.cache.provider=redis \
    -Dv933.redis.host=127.0.0.1 \
    -Dv933.redis.port="$REDIS_PORT" \
    -Dv933.redis.key-prefix="$prefix" \
    -DskipUnitTests=true \
    -DskipITs=false \
    -Dfailsafe.rerunFailingTestsCount=0 \
    -Dfailsafe.failIfNoTests=false \
    -Dfailsafe.failIfNoSpecifiedTests=false \
    -Dv934.external.run-id="$RUN_ID" \
    -Dv934.external.variant="$variant" \
    verify)
  seal_variant_bytecode "$variant_root/bytecode.tsv"
  python3 "$REPORT_TOOL" collect \
    --variant "$variant" \
    --outer-marker "$OUTER_MARKER" \
    --run-marker "$marker" \
    --report-root "addons/foggy-dataset-model-cache=$REPORTS_DIR" \
    --output "$variant_root/evidence"
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
  fail "V934_EXTERNAL_REDIS_SIGNAL_PROBE must be true or false"
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ && "$RUN_ID" != . && "$RUN_ID" != .. ]] || \
  fail "unsafe run id: $RUN_ID"

for command_name in cmp cut date docker flock git grep jq mkfifo mv mvn python3 readlink rg sed seq sha256sum sleep tee; do
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
  v934_acquire_authority_lock "$ROOT_DIR" "v934-external-redis" || exit 1
  RUN_ROOT="$ROOT_DIR/target/v934-step3-external-matrix/runs/$RUN_ID"
  [[ ! -e "$RUN_ROOT" ]] || fail "run root already exists: $RUN_ROOT"
  mkdir -p "$RUN_ROOT"
  OUTER_MARKER="$RUN_ROOT/run-context.json"
fi
mkdir -p "$RUN_ROOT/variants" "$RUN_ROOT/cells/redis7" "$RUN_ROOT/negative"
CELL_ROOT="$RUN_ROOT/cells/redis7"
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
SCOPE_HASH="$(printf '%s\n' "$RUN_ID|redis7" | sha256sum | cut -c1-12)"
REDIS_CONTAINER="v934ext-redis7-$SCOPE_HASH"
REDIS_VOLUME="v934ext-redis7-$SCOPE_HASH-data"

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
SOURCE_BEFORE="$(source_seal "$RUN_ROOT/source-before.tsv")"

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
actual_local_image_id="$(docker image inspect "$REDIS_IMAGE_REF" -f '{{.Id}}')" || \
  fail "frozen Redis image is unavailable"
[[ "$actual_local_image_id" == "$REDIS_IMAGE_ID" ]] || \
  fail "local Redis image id differs: $actual_local_image_id"
repo_digests="$(docker image inspect "$REDIS_IMAGE_REF" -f '{{json .RepoDigests}}')"
grep -Fq "\"$REDIS_IMAGE_REF\"" <<< "$repo_digests" || \
  fail "local Redis image lacks frozen repo digest"
redis_resource_absent || fail "run-scoped Redis resource already exists"

PHASE="redis-start"
REDIS_ARMED=true
docker volume create \
  --label "com.foggy.v934.external-run=$RUN_ID" \
  --label 'com.foggy.v934.external-cell=redis7' \
  "$REDIS_VOLUME" >/dev/null
docker run -d \
  --name "$REDIS_CONTAINER" \
  --label "com.foggy.v934.external-run=$RUN_ID" \
  --label 'com.foggy.v934.external-cell=redis7' \
  --network bridge \
  -p 127.0.0.1::6379 \
  --mount "type=volume,source=$REDIS_VOLUME,target=/data" \
  "$REDIS_IMAGE_REF" \
  redis-server --appendonly no --save '' >/dev/null

PHASE="redis-health"
redis_ping=""
for _ in $(seq 1 60); do
  redis_ping="$(docker exec "$REDIS_CONTAINER" redis-cli ping 2>/dev/null || true)"
  [[ "$redis_ping" == PONG ]] && break
  sleep 0.5
done
[[ "$redis_ping" == PONG ]] || fail "run-scoped Redis did not become ready"

PHASE="redis-identity"
actual_image_id="$(docker inspect -f '{{.Image}}' "$REDIS_CONTAINER")"
actual_image_ref="$(docker inspect -f '{{.Config.Image}}' "$REDIS_CONTAINER")"
actual_run_label="$(docker inspect -f '{{index .Config.Labels "com.foggy.v934.external-run"}}' "$REDIS_CONTAINER")"
actual_cell_label="$(docker inspect -f '{{index .Config.Labels "com.foggy.v934.external-cell"}}' "$REDIS_CONTAINER")"
mount_count="$(docker inspect -f '{{len .Mounts}}' "$REDIS_CONTAINER")"
mount_identity="$(docker inspect -f '{{range .Mounts}}{{.Name}}|{{.Destination}}|{{.Type}}{{end}}' "$REDIS_CONTAINER")"
volume_run_label="$(docker volume inspect -f '{{index .Labels "com.foggy.v934.external-run"}}' "$REDIS_VOLUME")"
volume_cell_label="$(docker volume inspect -f '{{index .Labels "com.foggy.v934.external-cell"}}' "$REDIS_VOLUME")"
volume_created="$(docker volume inspect -f '{{.CreatedAt}}' "$REDIS_VOLUME")"
volume_epoch="$(date -d "$volume_created" +%s)"
mapped_port="$(docker port "$REDIS_CONTAINER" 6379/tcp | tail -n 1)"
REDIS_PORT="$(sed -n 's/^127\.0\.0\.1://p' <<< "$mapped_port")"
redis_info="$(docker exec "$REDIS_CONTAINER" redis-cli INFO server | tr -d '\r')"
actual_version="$(sed -n 's/^redis_version://p' <<< "$redis_info")"
actual_mode="$(sed -n 's/^redis_mode://p' <<< "$redis_info")"
[[ "$actual_image_id" == "$REDIS_IMAGE_ID" ]] || fail "container Redis image id differs"
[[ "$actual_image_ref" == "$REDIS_IMAGE_REF" ]] || fail "container Redis image ref differs"
[[ "$actual_run_label" == "$RUN_ID" && "$actual_cell_label" == redis7 ]] || \
  fail "container ownership labels differ"
[[ "$mount_count" == 1 && "$mount_identity" == "$REDIS_VOLUME|/data|volume" ]] || \
  fail "fresh Redis cell must use exactly one run-owned data volume"
[[ "$volume_run_label" == "$RUN_ID" && "$volume_cell_label" == redis7 ]] || \
  fail "Redis volume ownership labels differ"
[[ "$volume_epoch" -ge "$START_EPOCH" ]] || fail "Redis volume predates the run marker"
[[ "$mapped_port" == 127.0.0.1:* && "$REDIS_PORT" =~ ^[0-9]+$ ]] || \
  fail "Redis must use one dynamic loopback port"
[[ "$actual_version" == "$REDIS_VERSION" && "$actual_mode" == standalone ]] || \
  fail "Redis runtime identity differs: version=$actual_version mode=$actual_mode"
[[ "$(docker exec "$REDIS_CONTAINER" redis-cli DBSIZE)" == 0 ]] || \
  fail "fresh Redis cell is not empty"
atomic_env "$CELL_ROOT/resource.env" \
  "run_id=$RUN_ID" \
  "cell=redis7" \
  "container=$REDIS_CONTAINER" \
  "image_ref=$actual_image_ref" \
  "image_id=$actual_image_id" \
  "mapped_port=$mapped_port" \
  "mount_count=$mount_count" \
  "mount_identity=$mount_identity" \
  "volume=$REDIS_VOLUME" \
  "volume_created=$volume_created" \
  "redis_version=$actual_version" \
  "redis_mode=$actual_mode" \
  "initial_dbsize=0" \
  "status=verified"

if [[ "$SIGNAL_PROBE_MODE" == true ]]; then
  PHASE="signal-probe-ready"
  atomic_env "$RUN_ROOT/signal-probe-ready.env" \
    "run_id=$RUN_ID" \
    "container=$REDIS_CONTAINER" \
    "volume=$REDIS_VOLUME" \
    "status=ready"
  echo "[v934-external-redis] SIGNAL_PROBE_READY run=$RUN_ID"
  while :; do
    sleep 1
  done
fi

PHASE="variant-redis7"
run_variant \
  redis7 \
  com.foggyframework.dataset.db.model.cache.provider.RedisCrossJvmCacheIT \
  "v934:$RUN_ID:redis7:"
redis7_key_count="$(docker exec "$REDIS_CONTAINER" redis-cli --scan --pattern "v934:$RUN_ID:redis7:*" | sed '/^$/d' | wc -l | tr -d ' ')"
[[ "$redis7_key_count" == 4 ]] || fail "Redis cross-JVM variant must leave exactly four run-owned keys"

PHASE="variant-redis7-sqlite"
run_variant \
  redis7-sqlite \
  com.foggyframework.dataset.db.model.cache.lifecycle.realquery.QueryCacheLifecycleRealQueryIT \
  "v934:$RUN_ID:redis7-sqlite:"
redis_lifecycle_key_count="$(docker exec "$REDIS_CONTAINER" redis-cli --scan --pattern "v934:$RUN_ID:redis7-sqlite:*" | sed '/^$/d' | wc -l | tr -d ' ')"
[[ "$redis_lifecycle_key_count" == 0 ]] || fail "Redis lifecycle variant did not evict its run-owned keys"
total_key_count="$(docker exec "$REDIS_CONTAINER" redis-cli DBSIZE)"
[[ "$total_key_count" == 4 ]] || fail "fresh Redis cell contains unexpected keys after variants"
atomic_env "$CELL_ROOT/fixture.env" \
  "cell=redis7" \
  "redis7_key_count=$redis7_key_count" \
  "redis7_sqlite_key_count=$redis_lifecycle_key_count" \
  "total_key_count=$total_key_count" \
  "foreign_key_count=0" \
  "status=verified"

PHASE="redis-data-cleanup"
docker exec "$REDIS_CONTAINER" redis-cli FLUSHDB >/dev/null
[[ "$(docker exec "$REDIS_CONTAINER" redis-cli DBSIZE)" == 0 ]] || \
  fail "Redis data cleanup did not reach an empty database"

PHASE="redis-resource-cleanup"
cleanup_redis || fail "run-scoped Redis resource cleanup failed"

PHASE="source-after"
SOURCE_AFTER="$(source_seal "$RUN_ROOT/source-after.tsv")"
[[ "$SOURCE_BEFORE" == "$SOURCE_AFTER" ]] || fail "protected Redis lane source changed during execution"
cmp -s "$RUN_ROOT/source-before.tsv" "$RUN_ROOT/source-after.tsv" || \
  fail "protected Redis lane source manifest changed during execution"

PHASE="negative-probes"
python3 "$REPORT_TOOL" negative --output "$RUN_ROOT/negative/probes.tsv"

PHASE="merge-subset"
python3 "$REPORT_TOOL" merge-subset \
  --lane external-redis \
  --outer-marker "$OUTER_MARKER" \
  --manifest "$RUN_ROOT/variants/redis7/evidence/report-manifest.json" \
  --manifest "$RUN_ROOT/variants/redis7-sqlite/evidence/report-manifest.json" \
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
  fail "run-owned Redis evidence contains credentials or credential variables"
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
python3 - \
  "$RUN_ROOT/final/report-manifest.json" "$RUN_ROOT/summary.env" \
  "$RUN_ID" "$GIT_HEAD" "$SOURCE_BEFORE" "$SOURCE_AFTER" \
  "$OUTER_MARKER_SHA256" "$CONTRACT_SHA256" "$FINAL_REPORT_MANIFEST_SHA256" \
  "$RUN_STATUS_SHA256" "$CELL_ROOT/resource.env" "$CELL_ROOT/fixture.env" \
  "$CELL_ROOT/cleanup.env" "$RUN_ROOT/negative/probes.tsv" \
  "$RUN_ROOT/negative/sensitive-probes.tsv" "$RUN_ROOT/sensitive-scan.env" <<'PY'
import csv
import hashlib
import json
import os
from pathlib import Path
import sys

def sha(path: Path) -> str:
    if path.is_symlink() or not path.is_file():
        raise SystemExit(f"required summary input is not regular: {path}")
    return hashlib.sha256(path.read_bytes()).hexdigest()

manifest = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
totals = manifest["totals"]
if (
    totals.get("variants"), totals.get("reports"), totals.get("testcase_nodes"),
    totals.get("failures"), totals.get("errors"), totals.get("skipped")
) != (2, 2, 3, 0, 0, 0):
    raise SystemExit(f"Redis subset totals differ: {totals}")
negative = Path(sys.argv[14])
with negative.open(encoding="utf-8", newline="") as stream:
    rows = list(csv.DictReader(stream, delimiter="\t"))
if len(rows) != 12 or any(
    row.get("status") != "passed" or row.get("expected_error") != row.get("actual_error")
    for row in rows
):
    raise SystemExit("external report negative probes are not exact 12/12 passed")
sensitive_negative = Path(sys.argv[15])
with sensitive_negative.open(encoding="utf-8", newline="") as stream:
    sensitive_rows = list(csv.DictReader(stream, delimiter="\t"))
if (
    len(sensitive_rows) != 6
    or {row.get("probe") for row in sensitive_rows}
    != {"redis-env", "json-password", "api-key", "bearer", "redis-uri", "cli-password"}
    or any(row.get("status") != "passed" for row in sensitive_rows)
):
    raise SystemExit("sensitive scan negative probes are not exact 6/6 passed")
sensitive = Path(sys.argv[16])
if sensitive.read_text(encoding="utf-8") != "patterns=5\nstatus=passed\n":
    raise SystemExit("sensitive scan evidence differs")
values = {
    "run_id": sys.argv[3],
    "runner": "failsafe",
    "lane": "external-redis",
    "git_head": sys.argv[4],
    "variants": "2",
    "reports": "2",
    "testcase_nodes": "3",
    "failures": "0",
    "errors": "0",
    "skipped": "0",
    "source_before": sys.argv[5],
    "source_after": sys.argv[6],
    "outer_marker_sha256": sys.argv[7],
    "contract_sha256": sys.argv[8],
    "final_report_manifest_sha256": sys.argv[9],
    "run_status_sha256": sys.argv[10],
    "resource_sha256": sha(Path(sys.argv[11])),
    "fixture_sha256": sha(Path(sys.argv[12])),
    "cleanup_sha256": sha(Path(sys.argv[13])),
    "negative_probes": "12/12",
    "negative_sha256": sha(negative),
    "sensitive_negative_probes": "6/6",
    "sensitive_negative_sha256": sha(sensitive_negative),
    "sensitive_scan_sha256": sha(sensitive),
    "resource_residue": "0/0",
    "status": "passed",
}
output = Path(sys.argv[2])
temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
temporary.write_text("".join(f"{key}={value}\n" for key, value in values.items()), encoding="utf-8")
os.replace(temporary, output)
PY

PHASE="candidate-manifest"
python3 "$REPORT_TOOL" create-candidate \
  --outer-marker "$OUTER_MARKER" \
  --run-root "$RUN_ROOT" \
  --output "$RUN_ROOT/candidate-manifest.json"
python3 "$REPORT_TOOL" verify-candidate \
  --candidate "$RUN_ROOT/candidate-manifest.json"
PHASE="completed"

v934_disarm_run_status_traps
echo "[v934-external-redis] PASS run=$RUN_ID reports=2 testcase_nodes=3 F0/E0/S0 evidence=$RUN_ROOT"
