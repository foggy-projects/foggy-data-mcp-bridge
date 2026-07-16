#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_PATH="$ROOT_DIR/scripts/verify-v934-external-mysql.sh"
STEP3_DIR="$ROOT_DIR/scripts/v934/step3"
STEP4_SUCCESSOR_DIR="$ROOT_DIR/scripts/v934/step4/successor"
CONTRACT="$STEP4_SUCCESSOR_DIR/external-matrix-contract.json"
REPORT_TOOL="$STEP4_SUCCESSOR_DIR/external_matrix_report_tool.py"
AUTHORITY_LIB="$ROOT_DIR/scripts/v934/authority_runner_lib.sh"
SHARED_CONTEXT_LIB="$STEP3_DIR/external_shared_context.sh"
COVERAGE_RUNNER_LIB="$ROOT_DIR/scripts/v934/step4/coverage_runner_lib.sh"
DEFERRED_INVENTORY="$ROOT_DIR/scripts/v934/successor/step2/deferred-step3.tsv"
REPORTS="$ROOT_DIR/foggy-dataset-mcp/target/failsafe-reports"
DIRECT_REPORT="$ROOT_DIR/foggy-dataset-mcp/target/ai-test-report-summary.json"
DIRECT_CASES="$ROOT_DIR/foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json"
INIT_ROOT="$ROOT_DIR/foggy-dataset-demo/docker/mysql/init"
ECOMMERCE_SOURCE="$ROOT_DIR/foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce"
CLEAN_MODULES="foggy-core,foggy-bean-copy,foggy-mcp-spi,foggy-fsscript,foggy-dataset,foggy-dataset-demo,foggy-dataset-model,foggy-dataset-mcp"

RUNNER_NAME="failsafe"
LANE="external-mysql"
MYSQL_IMAGE_REF="mysql@sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb"
MYSQL_IMAGE_ID="sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb"
MYSQL_VERSION="5.7.44-log"
MYSQL_FIXTURE_CONTENT_SHA256="21919cc1f9c73fa05f80eb51ae86b939e7f7db4c7764b2841f1c4301188c256e"
MYSQL_USER="v934_runner"
MYSQL_CONTAINER=""
MYSQL_VOLUME=""
MYSQL_DATABASE=""
MYSQL_ROOT_SECRET=""
MYSQL_APP_SECRET=""
CURATED_BUNDLE=""
RUN_LOG_FIFO=""
RUN_LOG_TEE_PID=""
RUN_LOG_OPEN=false
SIGNAL_PROBE_MODE="${V934_EXTERNAL_MYSQL_SIGNAL_PROBE:-false}"
SHARED_CHILD_MODE=false
EXACT_SECRET_SCAN_COUNT=0

SENSITIVE_PATTERNS=(
  '(?i)(?:MYSQL_PASSWORD|MYSQL_ROOT_PASSWORD|SPRING_DATASOURCE_PASSWORD)[[:space:]]*[:=][[:space:]]*(?!"?null"?(?:[[:space:],}\]]|$))"?[^"[:space:],}\]]+'
  '(?i)"?(?:password|passwd|pwd|credential|credentials|api[-_]?key|access[-_]?token|refresh[-_]?token|auth[-_]?token|secret|authorization)"?[[:space:]]*[:=][[:space:]]*(?!"?null"?(?:[[:space:],}\]]|$))"?[^"[:space:],}\]]+'
  '(?i)(?:authorization[[:space:]]*[:=][[:space:]]*)?bearer[[:space:]]+[A-Za-z0-9._~+/-]+'
  '(?i)(?:jdbc:)?mysql://[^/@:[:space:]]+:[^/@[:space:]]+@'
  '(?i)(?:^|[[:space:]"=])(?:--password|--passwd|--pwd)(?:=|[[:space:]])[^[:space:]]+|(?:^|[[:space:]"=/])mysql(?:admin|binlog|check|dump|import|pump|show|slap|sh)?(?:\.exe)?(?=["[:space:]])[^\r\n]*?(?:^|[[:space:]"=])-p(?:=|[[:space:]])?[^[:space:]]+'
)

fail() {
  echo "[v934-external-mysql] ERROR: $*" >&2
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
    foggy-core foggy-bean-copy foggy-mcp-spi foggy-fsscript foggy-dataset
    foggy-dataset-demo foggy-dataset-model foggy-dataset-mcp
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
  local index pattern scan_rc
  local -a labels=(mysql-env json-password api-key auth-header mysql-uri cli-password)
  local -a fixtures=(
    'MYSQL_PASSWORD=x'
    '{"password": "x"}'
    'API_KEY=x'
    'Authorization: Bearer x'
    'mysql://u:p@127.0.0.1:3306/example'
    '--password x'
  )
  local -a additional_positive_fixtures=(
    '--password=x'
    'mysql -px'
    'mysqldump -p=x example'
  )
  local -a safe_fixtures=(
    'credential=null'
    'password=null'
    '{"credential": null, "password": "null"}'
    'auto-proxying'
    'junit-platform-engine'
    'auth_mode=ephemeral-password'
    'mvn -pl foggy-dataset-mcp'
    'java -parameters'
    "'value-pg'"
  )
  local -a args=(--pcre2)
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
  for index in "${!additional_positive_fixtures[@]}"; do
    printf '%s\n' "${additional_positive_fixtures[$index]}" > "$probe_file"
    rg -q "${args[@]}" "$probe_file" || \
      fail "sensitive scan failed to detect additional positive probe: $index"
  done
  for index in "${!safe_fixtures[@]}"; do
    printf '%s\n' "${safe_fixtures[$index]}" > "$probe_file"
    if rg -q "${args[@]}" "$probe_file"; then
      fail "sensitive scan rejected safe probe: $index"
    else
      scan_rc=$?
      [[ "$scan_rc" -eq 1 ]] || \
        fail "sensitive safe probe scan failed with rg exit code $scan_rc"
    fi
  done
  rm -f -- "$probe_file"
  mv -f -- "$temporary" "$output"
}

verify_ephemeral_secrets_absent() {
  local matches="$RUN_ROOT/.ephemeral-secret.matches" scan_rc
  [[ -n "$MYSQL_ROOT_SECRET" && -n "$MYSQL_APP_SECRET" \
     && "$MYSQL_ROOT_SECRET" != "$MYSQL_APP_SECRET" ]] || \
    fail "ephemeral MySQL secrets are missing or reused"
  if rg -l --hidden -F \
    -e "$MYSQL_ROOT_SECRET" -e "$MYSQL_APP_SECRET" -- \
    "$RUN_ROOT" > "$matches"; then
    fail "run-owned MySQL evidence contains an exact ephemeral secret"
  else
    scan_rc=$?
    [[ "$scan_rc" -eq 1 ]] || \
      fail "exact ephemeral secret scan failed with rg exit code $scan_rc"
  fi
  rm -f -- "$matches"
  EXACT_SECRET_SCAN_COUNT=2
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
  local marker="$1" variant="$2" selector="$3"
  python3 - "$marker" "$RUN_ID" "$GIT_HEAD" "$CONTRACT_SHA256" \
    "$OUTER_MARKER_SHA256" "$variant" "$selector" <<'PY'
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
    "variant_key": sys.argv[6],
    "infra_kind": "database",
    "outer_marker_sha256": sys.argv[5],
    "selector": sys.argv[7],
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

mysql_resource_absent() {
  local names labelled volume_names labelled_volumes
  docker info >/dev/null 2>&1 || return 2
  names="$(docker ps -a --format '{{.Names}}')" || return 2
  labelled="$(docker ps -aq --filter "label=com.foggy.v934.external-run=$RUN_ID")" || return 2
  volume_names="$(docker volume ls -q)" || return 2
  labelled_volumes="$(
    docker volume ls -q --filter "label=com.foggy.v934.external-run=$RUN_ID"
  )" || return 2
  ! grep -Fxq "$MYSQL_CONTAINER" <<< "$names" \
    && ! grep -Fxq "$MYSQL_VOLUME" <<< "$volume_names" \
    && [[ -z "$labelled" ]] \
    && [[ -z "$labelled_volumes" ]]
}

cleanup_mysql() {
  local cleanup_code=0 labelled="" labelled_volumes=""
  local -a containers=() volumes=()
  if [[ -n "$MYSQL_CONTAINER" ]]; then
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
    mysql_resource_absent || cleanup_code=1
  fi
  MYSQL_ROOT_SECRET=""
  MYSQL_APP_SECRET=""
  if [[ -d "${CELL_ROOT:-}" ]]; then
    if [[ "$cleanup_code" -eq 0 ]]; then
      atomic_env "$CELL_ROOT/cleanup.env" \
        "cell=mysql57" \
        "container=$MYSQL_CONTAINER" \
        "volume=$MYSQL_VOLUME" \
        "container_residue=0" \
        "volume_residue=0" \
        "status=passed" || cleanup_code=1
    else
      atomic_env "$CELL_ROOT/cleanup.env" \
        "cell=mysql57" \
        "container=$MYSQL_CONTAINER" \
        "volume=$MYSQL_VOLUME" \
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
  if ! cleanup_mysql; then
    PHASE="mysql-cleanup-failed"
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

mysql_query() {
  local query="$1"
  MYSQL_PWD="$MYSQL_APP_SECRET" docker exec --env MYSQL_PWD "$MYSQL_CONTAINER" \
    mysql --batch --raw --skip-column-names --default-character-set=utf8mb4 \
      -u"$MYSQL_USER" "$MYSQL_DATABASE" -e "$query"
}

mysql_root_query() {
  local query="$1"
  MYSQL_PWD="$MYSQL_ROOT_SECRET" docker exec --env MYSQL_PWD "$MYSQL_CONTAINER" \
    mysql --batch --raw --skip-column-names --default-character-set=utf8mb4 \
      -uroot "$MYSQL_DATABASE" -e "$query"
}

freeze_mysql_app_grants() {
  local escaped_database="${MYSQL_DATABASE//_/\\_}"
  mysql_root_query "
    REVOKE ALL PRIVILEGES, GRANT OPTION FROM '$MYSQL_USER'@'%';
    GRANT SELECT ON \`$escaped_database\`.* TO '$MYSQL_USER'@'%';
    FLUSH PRIVILEGES;
  " >/dev/null
}

write_mysql_grants_evidence() {
  local output="$1" global_privileges schema_database schema_pattern schema_privileges
  local schema_rows table_rows column_rows routine_rows proxy_rows
  local grantee="'''${MYSQL_USER}''@''%'''"
  local escaped_database="${MYSQL_DATABASE//_/\\_}"
  global_privileges="$(mysql_root_query "
    SELECT GROUP_CONCAT(CONCAT(PRIVILEGE_TYPE, ':', IS_GRANTABLE)
                        ORDER BY PRIVILEGE_TYPE SEPARATOR ',')
      FROM information_schema.USER_PRIVILEGES
      WHERE GRANTEE = $grantee;
  ")"
  schema_privileges="$(mysql_root_query "
    SELECT GROUP_CONCAT(CONCAT(PRIVILEGE_TYPE, ':', IS_GRANTABLE)
                        ORDER BY PRIVILEGE_TYPE SEPARATOR ',')
      FROM information_schema.SCHEMA_PRIVILEGES
      WHERE GRANTEE = $grantee;
  ")"
  schema_database="$(mysql_root_query "
    SELECT GROUP_CONCAT(REPLACE(TABLE_SCHEMA, CHAR(92), '')
                        ORDER BY TABLE_SCHEMA SEPARATOR ',')
      FROM information_schema.SCHEMA_PRIVILEGES
      WHERE GRANTEE = $grantee;
  ")"
  schema_pattern="$(mysql_root_query "
    SELECT GROUP_CONCAT(TABLE_SCHEMA ORDER BY TABLE_SCHEMA SEPARATOR ',')
      FROM information_schema.SCHEMA_PRIVILEGES
      WHERE GRANTEE = $grantee;
  ")"
  schema_rows="$(mysql_root_query "
    SELECT COUNT(*) FROM information_schema.SCHEMA_PRIVILEGES WHERE GRANTEE = $grantee;
  ")"
  table_rows="$(mysql_root_query "
    SELECT COUNT(*) FROM information_schema.TABLE_PRIVILEGES WHERE GRANTEE = $grantee;
  ")"
  column_rows="$(mysql_root_query "
    SELECT COUNT(*) FROM information_schema.COLUMN_PRIVILEGES WHERE GRANTEE = $grantee;
  ")"
  routine_rows="$(mysql_root_query "
    SELECT COUNT(*) FROM mysql.procs_priv
      WHERE User = '$MYSQL_USER' AND Host = '%';
  ")"
  proxy_rows="$(mysql_root_query "
    SELECT COUNT(*) FROM mysql.proxies_priv
      WHERE User = '$MYSQL_USER' AND Host = '%';
  ")"
  [[ "$global_privileges" == 'USAGE:NO' \
     && "$schema_database" == "$MYSQL_DATABASE" \
     && "$schema_pattern" == "$escaped_database" \
     && "$schema_privileges" == 'SELECT:NO' \
     && "$schema_rows" == 1 && "$table_rows" == 0 && "$column_rows" == 0 \
     && "$routine_rows" == 0 && "$proxy_rows" == 0 ]] || \
    fail "MySQL app grants are broader than the read-only contract"
  atomic_env "$output" \
    "cell=mysql57" \
    "principal=$MYSQL_USER@%" \
    "database=$MYSQL_DATABASE" \
    "global_privileges=$global_privileges" \
    "schema_database=$schema_database" \
    "schema_pattern=$schema_pattern" \
    "schema_privileges=$schema_privileges" \
    "schema_privilege_rows=$schema_rows" \
    "table_privilege_rows=$table_rows" \
    "column_privilege_rows=$column_rows" \
    "routine_privilege_rows=$routine_rows" \
    "proxy_privilege_rows=$proxy_rows" \
    "status=verified"
}

assert_mysql_app_read_only() {
  local probe_rc
  [[ "$(mysql_query 'SELECT COUNT(*) FROM dim_product;')" == 500 ]] || \
    fail "MySQL read-only app user cannot read the deterministic fixture"
  if MYSQL_PWD="$MYSQL_APP_SECRET" docker exec --env MYSQL_PWD "$MYSQL_CONTAINER" \
    mysql --batch --raw --skip-column-names --default-character-set=utf8mb4 \
      -u"$MYSQL_USER" "$MYSQL_DATABASE" \
      -e 'UPDATE dim_product SET product_name = product_name WHERE 1 = 0;' \
      >/dev/null 2>&1; then
    fail "MySQL app user unexpectedly retained UPDATE privilege"
  else
    probe_rc=$?
    [[ "$probe_rc" -gt 0 ]] || fail "MySQL write-denial probe returned an invalid status"
  fi
}

fixture_content_sha256() {
  local primary_key_tables
  local -a tables=()
  mapfile -t tables < <(mysql_query "
    SELECT table_name
      FROM information_schema.tables
      WHERE table_schema = DATABASE()
      ORDER BY BINARY table_name;
  ")
  [[ "${#tables[@]}" -eq 69 ]] || fail "MySQL content digest table set differs"
  primary_key_tables="$(mysql_query "
    SELECT COUNT(DISTINCT table_name)
      FROM information_schema.table_constraints
      WHERE table_schema = DATABASE() AND constraint_type = 'PRIMARY KEY';
  ")"
  [[ "$primary_key_tables" == 69 ]] || \
    fail "MySQL content digest requires a primary key on every fixture table"
  MYSQL_PWD="$MYSQL_APP_SECRET" docker exec --env MYSQL_PWD "$MYSQL_CONTAINER" \
    mysqldump \
      --user="$MYSQL_USER" \
      --default-character-set=utf8mb4 \
      --skip-opt \
      --single-transaction \
      --quick \
      --skip-lock-tables \
      --no-create-info \
      --skip-triggers \
      --skip-comments \
      --skip-set-charset \
      --skip-tz-utc \
      --no-tablespaces \
      --skip-extended-insert \
      --complete-insert \
      --hex-blob \
      --quote-names \
      --order-by-primary \
      --set-gtid-purged=OFF \
      "$MYSQL_DATABASE" "${tables[@]}" \
    | sha256sum | cut -d' ' -f1
}

write_init_manifest() {
  local output="$1" temporary script
  temporary="${output}.$$.$RANDOM.tmp"
  printf 'path\tsha256\tsize_bytes\n' > "$temporary"
  while IFS= read -r script; do
    printf '%s\t%s\t%s\n' \
      "${script##*/}" "$(sha256_file "$script")" "$(stat -c '%s' "$script")" \
      >> "$temporary"
  done < <(find "$INIT_ROOT" -maxdepth 1 -type f -name '*.sql' | sort)
  [[ "$(wc -l < "$temporary")" -eq 11 ]] || fail "MySQL init manifest must contain ten scripts"
  mv -f -- "$temporary" "$output"
}

create_curated_ecommerce_bundle() {
  local output="$1" manifest="$2" temporary source relative destination
  local files=0 qm_files=0 tm_files=0 fsscript_files=0
  [[ -d "$ECOMMERCE_SOURCE" ]] || fail "ecommerce source bundle is missing"
  [[ ! -e "$output" ]] || fail "curated ecommerce bundle already exists"
  [[ -z "$(find "$ECOMMERCE_SOURCE" -type l -print -quit)" ]] || \
    fail "ecommerce source bundle contains a symlink"
  mkdir -p -- "$output"
  temporary="${manifest}.$$.$RANDOM.tmp"
  printf 'path\tsha256\tsize_bytes\n' > "$temporary"
  while IFS= read -r -d '' source; do
    relative="${source#"$ECOMMERCE_SOURCE"/}"
    [[ "$relative" != demo/* ]] || continue
    destination="$output/$relative"
    mkdir -p -- "${destination%/*}"
    cp -- "$source" "$destination"
    cmp -s "$source" "$destination" || fail "curated bundle copy differs: $relative"
    printf '%s\t%s\t%s\n' \
      "$relative" "$(sha256_file "$source")" "$(stat -c '%s' "$source")" \
      >> "$temporary"
    files=$((files + 1))
    case "$relative" in
      *.qm) qm_files=$((qm_files + 1)) ;;
      *.tm) tm_files=$((tm_files + 1)) ;;
      *.fsscript) fsscript_files=$((fsscript_files + 1)) ;;
      *) fail "unexpected ecommerce bundle resource type: $relative" ;;
    esac
  done < <(find "$ECOMMERCE_SOURCE" -type f -print0 | sort -z)
  [[ "$files" -eq 59 && "$qm_files" -eq 32 && "$tm_files" -eq 25 \
     && "$fsscript_files" -eq 2 ]] || \
    fail "curated ecommerce bundle cardinality differs"
  mv -f -- "$temporary" "$manifest"
  atomic_env "$CELL_ROOT/bundle.env" \
    "cell=mysql57" \
    "source=foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce" \
    "excluded_prefix=demo/" \
    "files=59" \
    "qm_files=32" \
    "tm_files=25" \
    "fsscript_files=2" \
    "manifest_sha256=$(sha256_file "$manifest")" \
    "status=verified"
}

snapshot_fixture() {
  local output="$1" content_sha256="$2" temporary
  [[ "$content_sha256" =~ ^[0-9a-f]{64}$ ]] || \
    fail "MySQL fixture content digest is not SHA-256"
  temporary="${output}.$$.$RANDOM.tmp"
  mysql_query "
    SELECT 'identity', 'database', DATABASE();
    SELECT 'table', table_name, 'present'
      FROM information_schema.tables
      WHERE table_schema = DATABASE()
      ORDER BY table_name;
    SELECT 'metric', 'table_count', COUNT(*)
      FROM information_schema.tables WHERE table_schema = DATABASE();
    SELECT 'metric', 'primary_key_table_count', COUNT(DISTINCT table_name)
      FROM information_schema.table_constraints
      WHERE table_schema = DATABASE() AND constraint_type = 'PRIMARY KEY';
    SELECT 'metric', 'dim_date_count', COUNT(*) FROM dim_date;
    SELECT 'metric', 'dim_product_count', COUNT(*) FROM dim_product;
    SELECT 'metric', 'dim_customer_count', COUNT(*) FROM dim_customer;
    SELECT 'metric', 'dim_store_count', COUNT(*) FROM dim_store;
    SELECT 'metric', 'fact_sales_count', COUNT(*) FROM fact_sales;
    SELECT 'metric', 'fact_order_count', COUNT(*) FROM fact_order;
    SELECT 'metric', 'fact_return_count', COUNT(*) FROM fact_return;
    SELECT 'metric', 'compose_join_count', COUNT(*) FROM (
      SELECT fs.product_key
        FROM fact_sales fs
        INNER JOIN fact_return fr ON fr.product_key = fs.product_key
        GROUP BY fs.product_key
    ) joined_products;
    SELECT 'metric', 'foreign_database_count', COUNT(*)
      FROM information_schema.schemata
      WHERE schema_name NOT IN (
        'information_schema', 'mysql', 'performance_schema', 'sys', DATABASE()
      );
  " > "$temporary"
  printf 'digest\tcontent_sha256\t%s\n' "$content_sha256" >> "$temporary"
  mv -f -- "$temporary" "$output"
}

snapshot_metric() {
  local snapshot="$1" metric="$2"
  awk -F '\t' -v metric="$metric" '$1 == "metric" && $2 == metric { print $3 }' "$snapshot"
}

run_variant() {
  local variant="$1" selector variant_root marker
  selector="$(jq -er --arg variant "$variant" \
    '.variants[] | select(.variant_key == $variant) | .selector' "$CONTRACT")"
  variant_root="$RUN_ROOT/variants/$variant"
  marker="$variant_root/run-marker.json"
  rm -rf -- "$REPORTS"
  if [[ "$variant" == mysql57-direct ]]; then
    rm -f -- "$DIRECT_REPORT"
  fi
  mkdir -p "$variant_root"
  write_variant_marker "$marker" "$variant" "$selector"
  echo "[v934-external-mysql] running variant=$variant"
  v934_coverage_configure it "$variant"
  (cd "$ROOT_DIR" && \
    SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:$MYSQL_PORT/$MYSQL_DATABASE?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai" \
    SPRING_DATASOURCE_USERNAME="$MYSQL_USER" \
    SPRING_DATASOURCE_PASSWORD="$MYSQL_APP_SECRET" \
    SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver \
    FOGGY_MCP_AUDIT_ENABLED=false \
    SPRING_DATA_MONGODB_URI="mongodb://127.0.0.1:1/v934_${SCOPE_HASH}_disabled?serverSelectionTimeoutMS=250&connectTimeoutMS=250" \
    AI_TEST_LLM_ENABLED=false \
    AI_TEST_OPENAI_API_KEY=disabled \
    AI_TEST_OPENAI_BASE_URL=http://127.0.0.1:65535 \
    AI_TEST_OPENAI_MODEL=test-model-disabled \
    AI_TEST_CASE_FILES= \
    AI_TEST_CASE_IDS= \
    AI_TEST_CATEGORIES= \
    AI_TEST_MAX_CASES=0 \
    AI_TEST_LLM_FAIL_ON_MISMATCH=false \
    mvn -q \
      -P'!multi-db,!model-lifecycle,!query-cache-real-query' \
      -pl foggy-dataset-mcp -am \
      -Dit.test="$selector" \
      -Dfoggy.demo.enabled=false \
      -Dfoggy.bundle.external.enabled=true \
      '-Dfoggy.bundle.external.bundles[0].name=v934-mysql57-ecommerce' \
      "-Dfoggy.bundle.external.bundles[0].path=file:$CURATED_BUNDLE" \
      '-Dfoggy.bundle.external.bundles[0].watch=false' \
      -Dfoggy.mcp.semantic.use-all-models=true \
      -DskipUnitTests=true \
      -DskipITs=false \
      -Dfailsafe.rerunFailingTestsCount=0 \
      -Dfailsafe.failIfNoTests=false \
      -Dfailsafe.failIfNoSpecifiedTests=false \
      -Dv934.external.run-id="$RUN_ID" \
      -Dv934.external.variant="$variant" \
      "${V934_COVERAGE_MAVEN_ARGS[@]}" \
      verify)
  v934_coverage_verify_exec
  python3 "$REPORT_TOOL" seal-bytecode \
    --lane "$LANE" --output "$variant_root/bytecode.tsv"
  python3 "$REPORT_TOOL" collect \
    --variant "$variant" \
    --outer-marker "$OUTER_MARKER" \
    --run-marker "$marker" \
    --report-root "foggy-dataset-mcp=$REPORTS" \
    --output "$variant_root/evidence"
  if [[ "$variant" == mysql57-direct ]]; then
    [[ -f "$DIRECT_REPORT" ]] || fail "direct structured report is missing"
    python3 "$REPORT_TOOL" verify-mysql-direct-report --report "$DIRECT_REPORT"
    cp -- "$DIRECT_REPORT" "$variant_root/direct-report.json"
  fi
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
  fail "V934_EXTERNAL_MYSQL_SIGNAL_PROBE must be true or false"
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ && "$RUN_ID" != . && "$RUN_ID" != .. ]] || \
  fail "unsafe run id: $RUN_ID"

for command_name in awk cmp cp cut date docker find flock git grep jq mkfifo mv mvn python3 readlink rg sed seq sha256sum sleep sort stat tee wc; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command missing: $command_name"
done
for required_file in "$SCRIPT_PATH" "$CONTRACT" "$REPORT_TOOL" "$AUTHORITY_LIB" \
  "$SHARED_CONTEXT_LIB" "$COVERAGE_RUNNER_LIB" "$DEFERRED_INVENTORY" "$DIRECT_CASES"; do
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
# shellcheck source=scripts/v934/step4/coverage_runner_lib.sh
source "$COVERAGE_RUNNER_LIB"

if [[ "$SHARED_CHILD_MODE" == true ]]; then
  v934_external_prepare_shared_child "$ROOT_DIR" "$RUN_ID" "$LANE" || exit 1
  RUN_ROOT="$V934_EXTERNAL_SHARED_LANE_ROOT"
  OUTER_MARKER="$V934_EXTERNAL_SHARED_LANE_MARKER"
else
  v934_acquire_authority_lock "$ROOT_DIR" "v934-external-mysql" || exit 1
  RUN_ROOT="$ROOT_DIR/target/v934-step3-external-matrix/runs/$RUN_ID"
  [[ ! -e "$RUN_ROOT" ]] || fail "run root already exists: $RUN_ROOT"
  mkdir -p "$RUN_ROOT"
  OUTER_MARKER="$RUN_ROOT/run-context.json"
fi
mkdir -p "$RUN_ROOT/variants" "$RUN_ROOT/cells/mysql57" "$RUN_ROOT/negative"
CELL_ROOT="$RUN_ROOT/cells/mysql57"
CURATED_BUNDLE="$CELL_ROOT/ecommerce-bundle"
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
SCOPE_HASH="$(printf '%s\n' "$RUN_ID|mysql57" | sha256sum | cut -c1-12)"
MYSQL_CONTAINER="v934ext-mysql57-$SCOPE_HASH"
MYSQL_VOLUME="$MYSQL_CONTAINER-data"
MYSQL_DATABASE="v934_${SCOPE_HASH}_mcp"
MYSQL_ROOT_SECRET="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')"
MYSQL_APP_SECRET="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')"
[[ -n "$MYSQL_ROOT_SECRET" && -n "$MYSQL_APP_SECRET" \
   && "$MYSQL_ROOT_SECRET" != "$MYSQL_APP_SECRET" ]] || \
  fail "failed to create distinct ephemeral MySQL secrets"

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

PHASE="curated-ecommerce-bundle"
create_curated_ecommerce_bundle \
  "$CURATED_BUNDLE" "$CELL_ROOT/bundle-manifest.tsv"

PHASE="outer-marker"
if [[ "$SHARED_CHILD_MODE" != true ]]; then
  write_outer_marker
fi
OUTER_MARKER_SHA256="$(sha256_file "$OUTER_MARKER")"
python3 "$REPORT_TOOL" verify-outer --outer-marker "$OUTER_MARKER"

PHASE="image-preflight"
docker info >/dev/null
actual_local_image_id="$(docker image inspect "$MYSQL_IMAGE_REF" -f '{{.Id}}')" || \
  fail "frozen MySQL image is unavailable"
[[ "$actual_local_image_id" == "$MYSQL_IMAGE_ID" ]] || \
  fail "local MySQL image id differs: $actual_local_image_id"
repo_digests="$(docker image inspect "$MYSQL_IMAGE_REF" -f '{{json .RepoDigests}}')"
grep -Fq "\"$MYSQL_IMAGE_REF\"" <<< "$repo_digests" || \
  fail "local MySQL image lacks frozen repo digest"
mysql_resource_absent || fail "run-scoped MySQL resource already exists"

PHASE="mysql-start"
docker volume create \
  --label "com.foggy.v934.external-run=$RUN_ID" \
  --label 'com.foggy.v934.external-cell=mysql57' \
  "$MYSQL_VOLUME" >/dev/null
MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_SECRET" \
MYSQL_PASSWORD="$MYSQL_APP_SECRET" \
MYSQL_DATABASE="$MYSQL_DATABASE" \
MYSQL_USER="$MYSQL_USER" \
docker run -d \
  --name "$MYSQL_CONTAINER" \
  --label "com.foggy.v934.external-run=$RUN_ID" \
  --label 'com.foggy.v934.external-cell=mysql57' \
  --network bridge \
  -e MYSQL_ROOT_PASSWORD \
  -e MYSQL_DATABASE \
  -e MYSQL_USER \
  -e MYSQL_PASSWORD \
  -p 127.0.0.1::3306 \
  --mount "type=volume,source=$MYSQL_VOLUME,target=/var/lib/mysql" \
  "$MYSQL_IMAGE_REF" \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci \
  --default-time-zone=+08:00 \
  --lower-case-table-names=1 \
  --innodb-buffer-pool-size=256M \
  --innodb-log-file-size=64M \
  --innodb-flush-log-at-trx-commit=2 \
  --innodb-flush-method=O_DIRECT \
  --slow-query-log=ON \
  --slow-query-log-file=/var/lib/mysql/slow.log \
  --long-query-time=2 \
  --max-connections=500 >/dev/null

PHASE="mysql-health"
mysql_ping=""
for _ in $(seq 1 180); do
  container_state="$(docker inspect -f '{{.State.Status}}' "$MYSQL_CONTAINER" 2>/dev/null || true)"
  [[ "$container_state" != exited && "$container_state" != dead ]] || \
    fail "run-scoped MySQL reached terminal state: $container_state"
  mysql_ping="$(MYSQL_PWD="$MYSQL_ROOT_SECRET" docker exec --env MYSQL_PWD "$MYSQL_CONTAINER" \
    mysqladmin ping -h 127.0.0.1 -uroot --silent 2>/dev/null || true)"
  [[ "$mysql_ping" == 'mysqld is alive' ]] && break
  sleep 1
done
[[ "$mysql_ping" == 'mysqld is alive' ]] || fail "run-scoped MySQL did not become ready"

PHASE="mysql-identity"
actual_image_id="$(docker inspect -f '{{.Image}}' "$MYSQL_CONTAINER")"
actual_image_ref="$(docker inspect -f '{{.Config.Image}}' "$MYSQL_CONTAINER")"
actual_run_label="$(docker inspect -f '{{index .Config.Labels "com.foggy.v934.external-run"}}' "$MYSQL_CONTAINER")"
actual_cell_label="$(docker inspect -f '{{index .Config.Labels "com.foggy.v934.external-cell"}}' "$MYSQL_CONTAINER")"
mount_count="$(docker inspect -f '{{len .Mounts}}' "$MYSQL_CONTAINER")"
mount_identity="$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Name}}|{{.Destination}}|{{.Type}}{{end}}{{end}}' "$MYSQL_CONTAINER")"
volume_run_label="$(docker volume inspect -f '{{index .Labels "com.foggy.v934.external-run"}}' "$MYSQL_VOLUME")"
volume_cell_label="$(docker volume inspect -f '{{index .Labels "com.foggy.v934.external-cell"}}' "$MYSQL_VOLUME")"
volume_created="$(docker volume inspect -f '{{.CreatedAt}}' "$MYSQL_VOLUME")"
volume_epoch="$(date -d "$volume_created" +%s)"
mapped_port="$(docker port "$MYSQL_CONTAINER" 3306/tcp | tail -n 1)"
MYSQL_PORT="$(sed -n 's/^127\.0\.0\.1://p' <<< "$mapped_port")"
runtime_identity="$(mysql_root_query "SELECT CONCAT(@@version, '|', @@default_storage_engine, '|', @@character_set_server, '|', @@collation_server, '|', @@time_zone, '|', @@lower_case_table_names);")"
IFS='|' read -r actual_version storage_engine character_set_server collation_server time_zone lower_case_table_names <<< "$runtime_identity"
server_process="$(docker exec "$MYSQL_CONTAINER" sh -c 'tr -d "\n" < /proc/1/comm')"
initial_table_count="$(mysql_root_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE();")"
initial_foreign_database_count="$(mysql_root_query "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name NOT IN ('information_schema','mysql','performance_schema','sys',DATABASE());")"
[[ "$actual_image_id" == "$MYSQL_IMAGE_ID" && "$actual_image_ref" == "$MYSQL_IMAGE_REF" ]] || \
  fail "container MySQL image identity differs"
[[ "$actual_run_label" == "$RUN_ID" && "$actual_cell_label" == mysql57 ]] || \
  fail "container ownership labels differ"
[[ "$mount_count" == 1 && "$mount_identity" == "$MYSQL_VOLUME|/var/lib/mysql|volume" ]] || \
  fail "MySQL mount identity differs"
[[ "$volume_run_label" == "$RUN_ID" && "$volume_cell_label" == mysql57 ]] || \
  fail "MySQL volume ownership labels differ"
[[ "$volume_epoch" -ge "$START_EPOCH" ]] || fail "MySQL volume predates the run marker"
[[ "$mapped_port" == 127.0.0.1:* && "$MYSQL_PORT" =~ ^[0-9]+$ ]] || \
  fail "MySQL must use one dynamic loopback port"
[[ "$actual_version" == "$MYSQL_VERSION" && "$server_process" == mysqld \
   && "$storage_engine" == InnoDB && "$character_set_server" == utf8mb4 \
   && "$collation_server" == utf8mb4_unicode_ci && "$time_zone" == +08:00 \
   && "$lower_case_table_names" == 1 ]] || fail "MySQL runtime identity differs: $runtime_identity/$server_process"
[[ "$initial_table_count" == 0 && "$initial_foreign_database_count" == 0 ]] || \
  fail "fresh MySQL database is not empty"
atomic_env "$CELL_ROOT/resource.env" \
  "run_id=$RUN_ID" \
  "cell=mysql57" \
  "container=$MYSQL_CONTAINER" \
  "image_ref=$actual_image_ref" \
  "image_id=$actual_image_id" \
  "mapped_port=$mapped_port" \
  "mount_count=$mount_count" \
  "mount_identity=$mount_identity" \
  "volume=$MYSQL_VOLUME" \
  "volume_created=$volume_created" \
  "mysql_version=$actual_version" \
  "server_process=$server_process" \
  "storage_engine=$storage_engine" \
  "character_set_server=$character_set_server" \
  "collation_server=$collation_server" \
  "time_zone=$time_zone" \
  "lower_case_table_names=$lower_case_table_names" \
  "auth_mode=distinct-ephemeral-root-app-passwords" \
  "app_user=$MYSQL_USER" \
  "app_host=%" \
  "app_schema_privilege=SELECT" \
  "credentials_distinct=true" \
  "database=$MYSQL_DATABASE" \
  "initial_table_count=0" \
  "initial_foreign_database_count=0" \
  "status=verified"

if [[ "$SIGNAL_PROBE_MODE" == true ]]; then
  PHASE="signal-probe-ready"
  atomic_env "$RUN_ROOT/signal-probe-ready.env" \
    "run_id=$RUN_ID" \
    "container=$MYSQL_CONTAINER" \
    "volume=$MYSQL_VOLUME" \
    "status=ready"
  echo "[v934-external-mysql] SIGNAL_PROBE_READY run=$RUN_ID"
  while :; do
    sleep 1
  done
fi

PHASE="mysql-init-manifest"
write_init_manifest "$CELL_ROOT/init-manifest.tsv"

PHASE="mysql-fixture-init"
{
  # Freeze all session-dependent fixture inputs used by the ten scripts.
  printf "SET SESSION time_zone='+08:00';\n"
  printf 'SET SESSION timestamp=1710864000;\n'
  printf 'SET SESSION rand_seed1=934, SESSION rand_seed2=934;\n'
  printf 'SET SESSION autocommit=0;\n'
  while IFS= read -r script; do
    sed -n '1,$p' "$script"
    printf '\n'
  done < <(find "$INIT_ROOT" -maxdepth 1 -type f -name '*.sql' | sort)
  printf 'COMMIT;\n'
} | MYSQL_PWD="$MYSQL_ROOT_SECRET" docker exec -i --env MYSQL_PWD "$MYSQL_CONTAINER" \
  mysql --binary-mode=1 --default-character-set=utf8mb4 -uroot "$MYSQL_DATABASE" \
  >/dev/null

PHASE="mysql-read-only-grants"
freeze_mysql_app_grants
write_mysql_grants_evidence "$CELL_ROOT/grants-before.env"
assert_mysql_app_read_only

PHASE="fixture-before"
content_before_sha256="$(fixture_content_sha256)"
[[ "$content_before_sha256" == "$MYSQL_FIXTURE_CONTENT_SHA256" ]] || \
  fail "MySQL deterministic fixture content SHA-256 differs: actual=$content_before_sha256 expected=$MYSQL_FIXTURE_CONTENT_SHA256"
snapshot_fixture "$CELL_ROOT/fixture-before.tsv" "$content_before_sha256"
python3 "$REPORT_TOOL" verify-mysql-snapshot \
  --snapshot "$CELL_ROOT/fixture-before.tsv" \
  --database "$MYSQL_DATABASE"
table_count="$(snapshot_metric "$CELL_ROOT/fixture-before.tsv" table_count)"
primary_key_table_count="$(snapshot_metric "$CELL_ROOT/fixture-before.tsv" primary_key_table_count)"
dim_date_count="$(snapshot_metric "$CELL_ROOT/fixture-before.tsv" dim_date_count)"
dim_product_count="$(snapshot_metric "$CELL_ROOT/fixture-before.tsv" dim_product_count)"
dim_customer_count="$(snapshot_metric "$CELL_ROOT/fixture-before.tsv" dim_customer_count)"
dim_store_count="$(snapshot_metric "$CELL_ROOT/fixture-before.tsv" dim_store_count)"
fact_sales_count="$(snapshot_metric "$CELL_ROOT/fixture-before.tsv" fact_sales_count)"
fact_order_count="$(snapshot_metric "$CELL_ROOT/fixture-before.tsv" fact_order_count)"
fact_return_count="$(snapshot_metric "$CELL_ROOT/fixture-before.tsv" fact_return_count)"
compose_join_count="$(snapshot_metric "$CELL_ROOT/fixture-before.tsv" compose_join_count)"
foreign_database_count="$(snapshot_metric "$CELL_ROOT/fixture-before.tsv" foreign_database_count)"
table_rows="$(awk -F '\t' '$1 == "table" { count++ } END { print count + 0 }' "$CELL_ROOT/fixture-before.tsv")"
for count in "$table_count" "$primary_key_table_count" "$dim_date_count" "$dim_product_count" "$dim_customer_count" \
  "$dim_store_count" "$fact_sales_count" "$fact_order_count" "$fact_return_count" \
  "$compose_join_count"; do
  [[ "$count" =~ ^[0-9]+$ && "$count" -gt 0 ]] || fail "MySQL fixture contains a non-positive required count"
done
[[ "$table_rows" == "$table_count" && "$foreign_database_count" == 0 ]] || \
  fail "MySQL fixture table/foreign database identity differs"
table_set_sha256="$(sed -n $'s/^table\\t\\([^\\t]*\\)\\tpresent$/\\1/p' \
  "$CELL_ROOT/fixture-before.tsv" | sha256sum | cut -d' ' -f1)"
[[ "$table_count" == 69 \
   && "$primary_key_table_count" == 69 \
   && "$dim_date_count" == 1461 \
   && "$dim_product_count" == 500 \
   && "$dim_customer_count" == 1000 \
   && "$dim_store_count" == 50 \
   && "$fact_sales_count" == 3088 \
   && "$fact_order_count" == 20005 \
   && "$fact_return_count" == 316 \
   && "$compose_join_count" == 10 \
   && "$foreign_database_count" == 0 \
   && "$table_rows" == 69 \
   && "$table_set_sha256" == 6c3356917e89c46c5e37851226a40b3d28e07f1db02bb2fe5fbecec8183591b7 ]] || \
  fail "MySQL deterministic fixture contract differs"

PHASE="variant-mysql57-mcp"
run_variant mysql57-mcp
PHASE="variant-mysql57-direct"
run_variant mysql57-direct
PHASE="variant-mysql57-compose"
run_variant mysql57-compose

PHASE="fixture-after"
content_after_sha256="$(fixture_content_sha256)"
snapshot_fixture "$CELL_ROOT/fixture-after.tsv" "$content_after_sha256"
write_mysql_grants_evidence "$CELL_ROOT/grants-after.env"
assert_mysql_app_read_only
cmp -s "$CELL_ROOT/fixture-before.tsv" "$CELL_ROOT/fixture-after.tsv" || \
  fail "MySQL fixture changed during external variants"
[[ "$content_before_sha256" == "$content_after_sha256" ]] || \
  fail "MySQL fixture content changed during external variants"
cmp -s "$CELL_ROOT/grants-before.env" "$CELL_ROOT/grants-after.env" || \
  fail "MySQL app grants changed during external variants"
before_snapshot_sha256="$(sha256_file "$CELL_ROOT/fixture-before.tsv")"
after_snapshot_sha256="$(sha256_file "$CELL_ROOT/fixture-after.tsv")"
atomic_env "$CELL_ROOT/fixture.env" \
  "cell=mysql57" \
  "database=$MYSQL_DATABASE" \
  "fixture_timestamp_epoch=1710864000" \
  "fixture_time_zone=+08:00" \
  "fixture_transaction_mode=single-session-commit" \
  "rand_seed1=934" \
  "rand_seed2=934" \
  "content_hash_format=mysqldump-data-v1" \
  "content_before_sha256=$content_before_sha256" \
  "content_after_sha256=$content_after_sha256" \
  "grants_before_sha256=$(sha256_file "$CELL_ROOT/grants-before.env")" \
  "grants_after_sha256=$(sha256_file "$CELL_ROOT/grants-after.env")" \
  "init_script_count=10" \
  "init_manifest_sha256=$(sha256_file "$CELL_ROOT/init-manifest.tsv")" \
  "table_count=$table_count" \
  "primary_key_table_count=$primary_key_table_count" \
  "table_set_sha256=$table_set_sha256" \
  "dim_date_count=$dim_date_count" \
  "dim_product_count=$dim_product_count" \
  "dim_customer_count=$dim_customer_count" \
  "dim_store_count=$dim_store_count" \
  "fact_sales_count=$fact_sales_count" \
  "fact_order_count=$fact_order_count" \
  "fact_return_count=$fact_return_count" \
  "compose_join_count=$compose_join_count" \
  "before_snapshot_sha256=$before_snapshot_sha256" \
  "after_snapshot_sha256=$after_snapshot_sha256" \
  "foreign_database_count=0" \
  "status=verified"

PHASE="exact-secret-scan"
verify_ephemeral_secrets_absent

PHASE="mysql-resource-cleanup"
cleanup_mysql || fail "run-scoped MySQL resource cleanup failed"

PHASE="source-after"
SOURCE_AFTER="$(create_source_seal "$RUN_ROOT/source-after.tsv" | tail -n 1)"
[[ "$SOURCE_BEFORE" == "$SOURCE_AFTER" ]] || fail "protected MySQL lane source changed"
cmp -s "$RUN_ROOT/source-before.tsv" "$RUN_ROOT/source-after.tsv" || \
  fail "protected MySQL lane source manifest changed"

PHASE="negative-probes"
python3 "$REPORT_TOOL" negative --output "$RUN_ROOT/negative/probes.tsv"

PHASE="merge-subset"
python3 "$REPORT_TOOL" merge-subset \
  --lane "$LANE" \
  --outer-marker "$OUTER_MARKER" \
  --manifest "$RUN_ROOT/variants/mysql57-mcp/evidence/report-manifest.json" \
  --manifest "$RUN_ROOT/variants/mysql57-direct/evidence/report-manifest.json" \
  --manifest "$RUN_ROOT/variants/mysql57-compose/evidence/report-manifest.json" \
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
SENSITIVE_SCAN_ARGS=(--pcre2)
for pattern in "${SENSITIVE_PATTERNS[@]}"; do
  SENSITIVE_SCAN_ARGS+=(-e "$pattern")
done
if rg -l --hidden \
  --glob '!run-status.env' \
  --glob '!sensitive-scan.matches' \
  "${SENSITIVE_SCAN_ARGS[@]}" \
  "$RUN_ROOT" > "$RUN_ROOT/sensitive-scan.matches"; then
  fail "run-owned MySQL evidence contains credentials"
else
  sensitive_scan_rc=$?
  [[ "$sensitive_scan_rc" -eq 1 ]] || \
    fail "sensitive evidence scan failed with rg exit code $sensitive_scan_rc"
fi
rm -f -- "$RUN_ROOT/sensitive-scan.matches"
atomic_env "$RUN_ROOT/sensitive-scan.env" \
  "patterns=${#SENSITIVE_PATTERNS[@]}" \
  "ephemeral_secrets=$EXACT_SECRET_SCAN_COUNT" \
  "status=passed"

PHASE="completed"
v934_write_run_status 0
RUN_STATUS_SHA256="$(sha256_file "$RUN_ROOT/run-status.env")"
atomic_env "$RUN_ROOT/summary.env" \
  "run_id=$RUN_ID" \
  "runner=failsafe" \
  "lane=$LANE" \
  "git_head=$GIT_HEAD" \
  "variants=3" \
  "reports=8" \
  "testcase_nodes=23" \
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
echo "[v934-external-mysql] PASS run=$RUN_ID reports=8 testcase_nodes=23 F0/E0/S0 evidence=$RUN_ROOT"
