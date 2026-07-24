#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
SCRIPT_PATH="$ROOT_DIR/scripts/v950/verify-release-authority.sh"
TOOL="$ROOT_DIR/scripts/v950/release_authority_tool.py"
CONTRACT="$ROOT_DIR/scripts/v950/release-authority-contract.json"
PROVISIONER="$ROOT_DIR/scripts/v934/step3/provision-database-cell.sh"
REPORTS_DIR="$ROOT_DIR/foggy-dataset-model-engine/target/failsafe-reports"
TARGET_ROOT="$ROOT_DIR/target/v950-release-authority"

STANDARD_SELECTORS="com.foggyframework.dataset.model.engine.pivot.PivotSqlParityIT,com.foggyframework.dataset.model.multidb.MultiDatabaseQueryTest,com.foggyframework.dataset.model.lifecycle.gate.RequiredDatabasePreflightIT,com.foggyframework.dataset.model.engine.pivot.PivotCascadeGenerateSqlParityIT,com.foggyframework.dataset.model.lifecycle.realquery.RequiredDatabaseQueryFacadeParityIT"
MYSQL8_TARGETED_SELECTORS="com.foggyframework.dataset.model.engine.pivot.PivotIT"
POSTGRES15_TARGETED_SELECTORS="com.foggyframework.dataset.model.engine.pivot.PivotIT,com.foggyframework.dataset.model.semantic.DslCteResultStageWindowIT,com.foggyframework.dataset.model.semantic.DslCteRelationMetricFixtureIT"
SEMANTIC_SELECTORS="com.foggyframework.dataset.model.semantic.ComparativeExecutionIT,com.foggyframework.dataset.model.semantic.TimeWindowExecutionIT,com.foggyframework.dataset.model.semantic.DslCteCrmFunnelFixtureIT,com.foggyframework.dataset.model.semantic.DslCteRelationMetricFixtureIT,com.foggyframework.dataset.model.semantic.DslCteResultStageWindowIT,com.foggyframework.dataset.model.semantic.DslCteSlaFixtureIT,com.foggyframework.dataset.model.semantic.member.permission.SyntheticMemberPermissionIT"

fail() {
  echo "[v950-release-authority] ERROR: $*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  scripts/v950/verify-release-authority.sh \
    [--resume-from PRIOR_FAILED_RUN_ROOT | --reuse-product-from PRIOR_PASSED_RUN_ROOT] \
    [RUN_ID]

Runs one canonical 9.5.0 release-authority attempt. The candidate must be a
clean committed HEAD based on current origin/main. This command never installs,
deploys, tags, publishes, or changes a remote.

--resume-from is fail-closed recovery from a failed database-phase attempt. It
reuses only passed root, semantic, and SQLite JSON receipts.

--reuse-product-from reuses only passed product JSON receipts (root, semantic,
seven database variants, and four database cells) from a passed authority.
Archive, extraction, portable replay, source seals, sensitive scan, and final
manifest are always regenerated. Reuse requires an ancestor source candidate
and a governance-only committed delta that leaves the frozen contract intact.
EOF
}

[[ "${1:-}" != "-h" && "${1:-}" != "--help" ]] || {
  usage
  exit 0
}

for command_name in bash cp date dirname docker find git jq mkdir mktemp mv mvn python3 rm sed sha256sum unzip; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command missing: $command_name"
done

require_clean_build_environment() {
  local name value
  for name in \
    MAVEN_ARGS MAVEN_BASEDIR MAVEN_CONFIG MAVEN_OPTS MAVEN_SKIP_RC \
    JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS; do
    value="${!name-}"
    [[ -z "$value" ]] || fail "ambient Maven/JVM control is forbidden: $name"
  done
}

copy_reports() {
  local destination="$1"
  [[ -d "$REPORTS_DIR" && ! -L "$REPORTS_DIR" ]] || fail "failsafe reports missing"
  mkdir -p "$destination"
  local copied=0 report
  shopt -s nullglob
  for report in "$REPORTS_DIR"/TEST-*.xml; do
    cp -p -- "$report" "$destination/"
    copied=$((copied + 1))
  done
  shopt -u nullglob
  [[ "$copied" -gt 0 ]] || fail "no JUnit XML reports were produced"
}

run_variant() {
  local run_root="$1" candidate="$2" lane="$3" database="$4" profile="$5" selectors="$6"
  local lane_root="$run_root/database/variants/$lane"
  [[ "$lane" =~ ^[A-Za-z0-9._-]+$ ]] || fail "unsafe lane: $lane"
  [[ ! -e "$lane_root" && ! -L "$lane_root" ]] || fail "lane already exists: $lane"
  mkdir -p "$lane_root"
  rm -rf -- "$REPORTS_DIR"
  : > "$lane_root/marker"
  echo "[v950-release-authority] database lane=$lane profile=$profile"
  (
    cd "$ROOT_DIR"
    mvn -B -ntp \
      -P'!multi-db,!model-lifecycle,!query-cache-real-query' \
      -pl foggy-dataset-model-engine -am \
      -Dit.test="$selectors" \
      -Dspring.profiles.active="$profile" \
      -Dv934.expectedDatabase="$database" \
      -DskipUnitTests=true \
      -DskipITs=false \
      -Dfailsafe.failIfNoTests=false \
      -Dfailsafe.failIfNoSpecifiedTests=false \
      verify
  ) > "$lane_root/maven.log" 2>&1
  copy_reports "$lane_root/raw-reports"
  python3 "$TOOL" junit-summary \
    --reports-dir "$lane_root/raw-reports" \
    --lane "$lane" \
    --candidate "$candidate" \
    --marker "$lane_root/marker" \
    --output "$lane_root/receipt.json"
}

run_external_callback() {
  local run_id="$1" database="$2" run_root="$3" candidate="$4" compat_root="$5"
  local expected_profile
  case "$database" in
    mysql57) expected_profile=docker ;;
    mysql8) expected_profile=mysql8 ;;
    postgres15) expected_profile=postgres ;;
    sqlserver2022) expected_profile=sqlserver ;;
    *) fail "unsupported callback database: $database" ;;
  esac
  [[ "${V934_DB_KIND:-}" == "$database" ]] || fail "callback database identity differs"
  [[ "${V934_DB_PROFILE:-}" == "$expected_profile" ]] || fail "callback profile differs"
  [[ "${V934_DB_CELL_ROOT:-}" == "$compat_root/cells/$database" ]] || \
    fail "callback cell root differs"
  run_variant "$run_root" "$candidate" "db-$database" "$database" "$expected_profile" "$STANDARD_SELECTORS"
  case "$database" in
    mysql8)
      run_variant "$run_root" "$candidate" mysql8-targeted "$database" "$expected_profile" "$MYSQL8_TARGETED_SELECTORS"
      ;;
    postgres15)
      run_variant "$run_root" "$candidate" postgres15-targeted "$database" "$expected_profile" "$POSTGRES15_TARGETED_SELECTORS"
      ;;
  esac
}

if [[ "${1:-}" == "--internal-database-callback" ]]; then
  [[ "$#" -eq 6 ]] || fail "invalid internal callback arguments"
  run_external_callback "$2" "$3" "$4" "$5" "$6"
  exit 0
fi

RESUME_FROM=""
REUSE_PRODUCT_FROM=""
RUN_ID=""
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --resume-from)
      [[ -z "$RESUME_FROM" && "$#" -ge 2 ]] || fail "invalid --resume-from"
      [[ -d "$2" && ! -L "$2" ]] || fail "prior run root missing or unsafe: $2"
      RESUME_FROM="$(cd "$2" && pwd -P)"
      shift 2
      ;;
    --reuse-product-from)
      [[ -z "$REUSE_PRODUCT_FROM" && "$#" -ge 2 ]] || \
        fail "invalid --reuse-product-from"
      [[ -d "$2" && ! -L "$2" ]] || fail "prior run root missing or unsafe: $2"
      REUSE_PRODUCT_FROM="$(cd "$2" && pwd -P)"
      shift 2
      ;;
    -*)
      fail "unknown option: $1"
      ;;
    *)
      [[ -z "$RUN_ID" ]] || fail "too many run ids"
      RUN_ID="$1"
      shift
      ;;
  esac
done
[[ -z "$RESUME_FROM" || -z "$REUSE_PRODUCT_FROM" ]] || \
  fail "--resume-from and --reuse-product-from are mutually exclusive"
RUN_ID="${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ && "$RUN_ID" != "." && "$RUN_ID" != ".." ]] || \
  fail "unsafe run id: $RUN_ID"

require_clean_build_environment
python3 "$TOOL" validate-contract --repo-root "$ROOT_DIR"

git -C "$ROOT_DIR" diff --quiet || fail "candidate has tracked worktree changes"
git -C "$ROOT_DIR" diff --cached --quiet || fail "candidate has staged changes"
[[ -z "$(git -C "$ROOT_DIR" status --porcelain --untracked-files=normal)" ]] || \
  fail "candidate worktree must be completely clean"
git -C "$ROOT_DIR" merge-base --is-ancestor origin/main HEAD || \
  fail "origin/main is not an ancestor of candidate"

CANDIDATE="$(git -C "$ROOT_DIR" rev-parse HEAD)"
[[ "$CANDIDATE" =~ ^[0-9a-f]{40}$ ]] || fail "invalid candidate SHA"

reuse_receipt_from_run() {
  local prior_root="$1" evidence_key="$2" relative="$3"
  local prior_receipt="$prior_root/$relative"
  local output="$RUN_ROOT/$relative"
  local source_output="${output%.json}.reused-source.json"
  local source_receipt="$prior_receipt"
  local source_name
  [[ -f "$prior_receipt" && ! -L "$prior_receipt" ]] || \
    fail "prior receipt missing or unsafe: $relative"
  if [[ "$(jq -r '.kind' "$prior_receipt")" == "v950-reused-authority-receipt" ]]; then
    source_name="$(jq -r '.source_receipt' "$prior_receipt")"
    [[ "$source_name" =~ ^[A-Za-z0-9._-]+$ ]] || \
      fail "unsafe prior source receipt name: $relative"
    source_receipt="$(dirname "$prior_receipt")/$source_name"
  fi
  [[ -f "$source_receipt" && ! -L "$source_receipt" ]] || \
    fail "prior source receipt missing or unsafe: $relative"
  mkdir -p "$(dirname "$output")"
  cp -p -- "$source_receipt" "$source_output"
  python3 "$TOOL" reuse-receipt \
    --repo-root "$ROOT_DIR" \
    --source-receipt "$source_output" \
    --evidence-key "$evidence_key" \
    --candidate "$CANDIDATE" \
    --output "$output"
}

STAGING_ROOT="$(mktemp -d "/tmp/foggy-v950-authority-${RUN_ID}.XXXXXX")"
PORTABLE_PARENT=""
RUN_ROOT="$TARGET_ROOT/runs/$RUN_ID"
V934_COMPAT_ROOT="$ROOT_DIR/target/v934-step3-database-matrix/runs/$RUN_ID"
TRACKED_TARGET_BACKUP="$STAGING_ROOT/tracked-target"
RESTORE_REQUIRED=false
PHASE=bootstrap
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

restore_tracked_target() {
  local manifest relative
  if [[ "$RESTORE_REQUIRED" == true ]]; then
    while IFS= read -r -d '' manifest; do
      relative="${manifest#"$TRACKED_TARGET_BACKUP/"}"
      mkdir -p "$(dirname "$ROOT_DIR/$relative")"
      cp -p -- "$manifest" "$ROOT_DIR/$relative"
    done < <(find "$TRACKED_TARGET_BACKUP" -type f -print0)
    RESTORE_REQUIRED=false
  fi
}

record_failure() {
  local exit_code="$1"
  trap - EXIT INT TERM HUP
  set +e
  restore_tracked_target
  if [[ -n "$PORTABLE_PARENT" && -d "$PORTABLE_PARENT" ]]; then
    rm -rf -- "$PORTABLE_PARENT"
  fi
  if [[ -d "$RUN_ROOT" ]]; then
    printf 'run_id=%s\ncandidate=%s\nphase=%s\nexit_code=%s\nstatus=failed\n' \
      "$RUN_ID" "$CANDIDATE" "$PHASE" "$exit_code" > "$RUN_ROOT/run-status.env"
  elif [[ -d "$STAGING_ROOT" ]]; then
    printf 'run_id=%s\ncandidate=%s\nphase=%s\nexit_code=%s\nstatus=failed\n' \
      "$RUN_ID" "$CANDIDATE" "$PHASE" "$exit_code" > "$STAGING_ROOT/run-status.env"
  fi
  exit "$exit_code"
}
trap 'record_failure "$?"' EXIT
trap 'record_failure 130' INT
trap 'record_failure 143' TERM
trap 'record_failure 129' HUP

mkdir -p "$TRACKED_TARGET_BACKUP"
while IFS= read -r -d '' relative; do
  [[ -f "$ROOT_DIR/$relative" && ! -L "$ROOT_DIR/$relative" ]] || \
    fail "tracked target manifest is unsafe: $relative"
  mkdir -p "$TRACKED_TARGET_BACKUP/$(dirname "$relative")"
  cp -p -- "$ROOT_DIR/$relative" "$TRACKED_TARGET_BACKUP/$relative"
done < <(git -C "$ROOT_DIR" ls-files -z 'target/**')
RESTORE_REQUIRED=true

PHASE=source-before
python3 "$TOOL" source-seal \
  --repo-root "$ROOT_DIR" \
  --output "$STAGING_ROOT/source-before.tsv" \
  > "$STAGING_ROOT/source-before.json"

if [[ -n "$RESUME_FROM" || -n "$REUSE_PRODUCT_FROM" ]]; then
  PHASE=reuse-validation
  [[ ! -e "$RUN_ROOT" && ! -L "$RUN_ROOT" ]] || fail "run root already exists: $RUN_ROOT"
  mkdir -p "$RUN_ROOT"
  mv -- "$STAGING_ROOT/source-before.tsv" "$RUN_ROOT/source-before.tsv"
  mv -- "$STAGING_ROOT/source-before.json" "$RUN_ROOT/source-before.json"
  if [[ -n "$RESUME_FROM" ]]; then
    [[ "$(jq -r '.candidate' "$RESUME_FROM/source-before.json")" == \
      "$(jq -r '.candidate' "$RESUME_FROM/root-receipt.json")" ]] || \
      fail "prior run candidate bindings differ"
    [[ "$(sed -n 's/^status=//p' "$RESUME_FROM/run-status.env")" == "failed" ]] || \
      fail "prior run is not a recorded failed attempt"
    [[ "$(sed -n 's/^phase=//p' "$RESUME_FROM/run-status.env")" == database-* ]] || \
      fail "prior run did not reach database execution"
    reuse_receipt_from_run "$RESUME_FROM" root root-receipt.json
    reuse_receipt_from_run "$RESUME_FROM" semantic semantic/receipt.json
    reuse_receipt_from_run \
      "$RESUME_FROM" db-sqlite database/variants/db-sqlite/receipt.json
  else
    [[ -f "$REUSE_PRODUCT_FROM/final-manifest.json" && \
      ! -L "$REUSE_PRODUCT_FROM/final-manifest.json" ]] || \
      fail "prior passed final manifest missing or unsafe"
    [[ "$(jq -r '.status' "$REUSE_PRODUCT_FROM/final-manifest.json")" == "passed" ]] || \
      fail "prior final manifest is not passed"
    [[ "$(jq -r '.contract_sha256' "$REUSE_PRODUCT_FROM/final-manifest.json")" == \
      "$(sha256sum "$CONTRACT" | sed 's/ .*//')" ]] || \
      fail "prior final manifest contract differs"
    [[ "$(sed -n 's/^status=//p' "$REUSE_PRODUCT_FROM/run-status.env")" == "passed" ]] || \
      fail "prior run is not recorded as passed"
    [[ "$(sed -n 's/^candidate=//p' "$REUSE_PRODUCT_FROM/run-status.env")" == \
      "$(jq -r '.candidate' "$REUSE_PRODUCT_FROM/final-manifest.json")" ]] || \
      fail "prior passed candidate bindings differ"
    [[ "$(jq '.receipts | length' "$REUSE_PRODUCT_FROM/final-manifest.json")" == "17" ]] || \
      fail "prior final manifest receipt set differs"
    reuse_receipt_from_run "$REUSE_PRODUCT_FROM" root root-receipt.json
    reuse_receipt_from_run "$REUSE_PRODUCT_FROM" semantic semantic/receipt.json
    reuse_receipt_from_run \
      "$REUSE_PRODUCT_FROM" db-sqlite database/variants/db-sqlite/receipt.json
    reuse_receipt_from_run \
      "$REUSE_PRODUCT_FROM" db-mysql57 database/variants/db-mysql57/receipt.json
    reuse_receipt_from_run \
      "$REUSE_PRODUCT_FROM" db-mysql8 database/variants/db-mysql8/receipt.json
    reuse_receipt_from_run \
      "$REUSE_PRODUCT_FROM" mysql8-targeted database/variants/mysql8-targeted/receipt.json
    reuse_receipt_from_run \
      "$REUSE_PRODUCT_FROM" db-postgres15 database/variants/db-postgres15/receipt.json
    reuse_receipt_from_run \
      "$REUSE_PRODUCT_FROM" postgres15-targeted database/variants/postgres15-targeted/receipt.json
    reuse_receipt_from_run \
      "$REUSE_PRODUCT_FROM" db-sqlserver2022 database/variants/db-sqlserver2022/receipt.json
    reuse_receipt_from_run \
      "$REUSE_PRODUCT_FROM" mysql57-cell database/cells/mysql57/receipt.json
    reuse_receipt_from_run \
      "$REUSE_PRODUCT_FROM" mysql8-cell database/cells/mysql8/receipt.json
    reuse_receipt_from_run \
      "$REUSE_PRODUCT_FROM" postgres15-cell database/cells/postgres15/receipt.json
    reuse_receipt_from_run \
      "$REUSE_PRODUCT_FROM" sqlserver2022-cell database/cells/sqlserver2022/receipt.json
  fi
  restore_tracked_target
else
  PHASE=root-clean-verify
  echo "[v950-release-authority] root clean verify candidate=$CANDIDATE"
  (
    cd "$ROOT_DIR"
    mvn -B -ntp clean verify -DskipITs \
      -Dsurefire.failIfNoTests=false \
      -Dfailsafe.failIfNoTests=false
  ) > "$STAGING_ROOT/root-clean-verify.log" 2>&1
  restore_tracked_target
  [[ ! -e "$RUN_ROOT" && ! -L "$RUN_ROOT" ]] || fail "run root already exists: $RUN_ROOT"
  mkdir -p "$RUN_ROOT"
  mv -- "$STAGING_ROOT/source-before.tsv" "$RUN_ROOT/source-before.tsv"
  mv -- "$STAGING_ROOT/source-before.json" "$RUN_ROOT/source-before.json"
  mv -- "$STAGING_ROOT/root-clean-verify.log" "$RUN_ROOT/root-clean-verify.log"

  PHASE=root-summary
  python3 "$TOOL" root-summary \
    --log "$RUN_ROOT/root-clean-verify.log" \
    --jar "$ROOT_DIR/$(jq -r '.root_verify.launcher_jar' "$CONTRACT")" \
    --candidate "$CANDIDATE" \
    --output "$RUN_ROOT/root-receipt.json"

  PHASE=semantic-replay
  mkdir -p "$RUN_ROOT/semantic"
  rm -rf -- "$REPORTS_DIR"
  : > "$RUN_ROOT/semantic/marker"
  echo "[v950-release-authority] SQLite semantic replay"
  (
    cd "$ROOT_DIR"
    mvn -B -ntp \
      -P'!multi-db,!model-lifecycle,!query-cache-real-query' \
      -pl foggy-dataset-model-engine -am \
      -Dit.test="$SEMANTIC_SELECTORS" \
      -Dspring.profiles.active=sqlite,v934-sqlite \
      -DskipUnitTests=true \
      -DskipITs=false \
      -Dfailsafe.failIfNoTests=false \
      -Dfailsafe.failIfNoSpecifiedTests=false \
      verify
  ) > "$RUN_ROOT/semantic/maven.log" 2>&1
  copy_reports "$RUN_ROOT/semantic/raw-reports"
  python3 "$TOOL" junit-summary \
    --reports-dir "$RUN_ROOT/semantic/raw-reports" \
    --lane semantic \
    --candidate "$CANDIDATE" \
    --marker "$RUN_ROOT/semantic/marker" \
    --output "$RUN_ROOT/semantic/receipt.json"

  PHASE=database-sqlite
  mkdir -p "$RUN_ROOT/database/cells/sqlite"
  run_variant "$RUN_ROOT" "$CANDIDATE" db-sqlite sqlite sqlite,v934-sqlite "$STANDARD_SELECTORS"
  printf 'database=sqlite\nstatus=passed\n' > "$RUN_ROOT/database/cells/sqlite/status.env"
fi

if [[ -z "$REUSE_PRODUCT_FROM" ]]; then
  [[ ! -e "$V934_COMPAT_ROOT" && ! -L "$V934_COMPAT_ROOT" ]] || \
    fail "v934 compatibility root already exists: $V934_COMPAT_ROOT"
  mkdir -p "$V934_COMPAT_ROOT/preflight" "$V934_COMPAT_ROOT/cells"
  for database in mysql57 mysql8 postgres15 sqlserver2022; do
    PHASE="database-preflight-$database"
    "$PROVISIONER" check "$database" "$RUN_ID" "$V934_COMPAT_ROOT/preflight/$database"
  done

  for database in mysql57 mysql8 postgres15 sqlserver2022; do
    PHASE="database-$database"
    echo "[v950-release-authority] provision database=$database"
    "$PROVISIONER" run "$database" "$RUN_ID" "$V934_COMPAT_ROOT/cells/$database" -- \
      "$SCRIPT_PATH" --internal-database-callback \
      "$RUN_ID" "$database" "$RUN_ROOT" "$CANDIDATE" "$V934_COMPAT_ROOT"
    cp -a -- "$V934_COMPAT_ROOT/cells/$database" "$RUN_ROOT/database/cells/$database"
    python3 "$TOOL" cell-summary \
      --cell-root "$RUN_ROOT/database/cells/$database" \
      --database "$database" \
      --candidate "$CANDIDATE" \
      --output "$RUN_ROOT/database/cells/$database/receipt.json"
  done
fi

PHASE=create-archive
mkdir -p "$RUN_ROOT/portable"
ARCHIVE="$RUN_ROOT/portable/$(jq -r '.portable_replay.archive_name' "$CONTRACT")"
python3 "$TOOL" create-archive \
  --repo-root "$ROOT_DIR" \
  --candidate "$CANDIDATE" \
  --output "$ARCHIVE" \
  --receipt "$RUN_ROOT/portable/archive-receipt.json"

PHASE=extract-archive
PORTABLE_PARENT="$(mktemp -d /dev/shm/foggy-v950-portable.XXXXXX)"
PORTABLE_ROOT="$PORTABLE_PARENT/source"
python3 "$TOOL" extract-archive \
  --archive "$ARCHIVE" \
  --destination "$PORTABLE_ROOT" \
  --candidate "$CANDIDATE" \
  --receipt "$RUN_ROOT/portable/extraction-receipt.json"

PHASE=portable-replay
PORTABLE_REPORTS="$PORTABLE_ROOT/foggy-dataset-model-engine/target/failsafe-reports"
: > "$RUN_ROOT/portable/marker"
echo "[v950-release-authority] cross-filesystem portable archive replay"
(
  cd "$PORTABLE_ROOT"
  mvn -B -ntp \
    -P'!multi-db,!model-lifecycle,!query-cache-real-query' \
    -pl foggy-dataset-model-engine -am \
    -Dit.test="$SEMANTIC_SELECTORS" \
    -Dspring.profiles.active=sqlite,v934-sqlite \
    -DskipUnitTests=true \
    -DskipITs=false \
    -Dfailsafe.failIfNoTests=false \
    -Dfailsafe.failIfNoSpecifiedTests=false \
    verify
) > "$RUN_ROOT/portable/maven.log" 2>&1
[[ -d "$PORTABLE_REPORTS" ]] || fail "portable reports missing"
mkdir -p "$RUN_ROOT/portable/raw-reports"
shopt -s nullglob
portable_reports=("$PORTABLE_REPORTS"/TEST-*.xml)
shopt -u nullglob
[[ "${#portable_reports[@]}" -gt 0 ]] || fail "portable replay produced no reports"
cp -p -- "${portable_reports[@]}" "$RUN_ROOT/portable/raw-reports/"
python3 "$TOOL" junit-summary \
  --reports-dir "$RUN_ROOT/portable/raw-reports" \
  --lane portable \
  --candidate "$CANDIDATE" \
  --marker "$RUN_ROOT/portable/marker" \
  --output "$RUN_ROOT/portable/receipt.json"
rm -rf -- "$PORTABLE_PARENT"
PORTABLE_PARENT=""

PHASE=source-after
python3 "$TOOL" source-seal \
  --repo-root "$ROOT_DIR" \
  --output "$RUN_ROOT/source-after.tsv" \
  > "$RUN_ROOT/source-after.json"

PHASE=sensitive-scan
python3 "$TOOL" scan-evidence \
  --root "$RUN_ROOT" \
  --output "$RUN_ROOT/sensitive-scan.json"

PHASE=finalize
python3 "$TOOL" finalize \
  --repo-root "$ROOT_DIR" \
  --candidate "$CANDIDATE" \
  --source-before "$RUN_ROOT/source-before.json" \
  --source-after "$RUN_ROOT/source-after.json" \
  --receipt "root=$RUN_ROOT/root-receipt.json" \
  --receipt "semantic=$RUN_ROOT/semantic/receipt.json" \
  --receipt "archive=$RUN_ROOT/portable/archive-receipt.json" \
  --receipt "archive-extraction=$RUN_ROOT/portable/extraction-receipt.json" \
  --receipt "portable=$RUN_ROOT/portable/receipt.json" \
  --receipt "db-sqlite=$RUN_ROOT/database/variants/db-sqlite/receipt.json" \
  --receipt "db-mysql57=$RUN_ROOT/database/variants/db-mysql57/receipt.json" \
  --receipt "db-mysql8=$RUN_ROOT/database/variants/db-mysql8/receipt.json" \
  --receipt "mysql8-targeted=$RUN_ROOT/database/variants/mysql8-targeted/receipt.json" \
  --receipt "db-postgres15=$RUN_ROOT/database/variants/db-postgres15/receipt.json" \
  --receipt "postgres15-targeted=$RUN_ROOT/database/variants/postgres15-targeted/receipt.json" \
  --receipt "db-sqlserver2022=$RUN_ROOT/database/variants/db-sqlserver2022/receipt.json" \
  --receipt "mysql57-cell=$RUN_ROOT/database/cells/mysql57/receipt.json" \
  --receipt "mysql8-cell=$RUN_ROOT/database/cells/mysql8/receipt.json" \
  --receipt "postgres15-cell=$RUN_ROOT/database/cells/postgres15/receipt.json" \
  --receipt "sqlserver2022-cell=$RUN_ROOT/database/cells/sqlserver2022/receipt.json" \
  --receipt "sensitive-scan=$RUN_ROOT/sensitive-scan.json" \
  --output "$RUN_ROOT/final-manifest.json"

PHASE=clean-state
[[ -z "$(git -C "$ROOT_DIR" status --porcelain --untracked-files=normal)" ]] || \
  fail "candidate worktree changed during authority"

PHASE=completed
printf 'run_id=%s\ncandidate=%s\nstarted_at=%s\nfinished_at=%s\nphase=%s\nexit_code=0\nstatus=passed\n' \
  "$RUN_ID" "$CANDIDATE" "$STARTED_AT" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$PHASE" \
  > "$RUN_ROOT/run-status.env"

trap - EXIT INT TERM HUP
rm -rf -- "$STAGING_ROOT"
echo "[v950-release-authority] PASSED run=$RUN_ID candidate=$CANDIDATE"
echo "[v950-release-authority] manifest=$RUN_ROOT/final-manifest.json"
