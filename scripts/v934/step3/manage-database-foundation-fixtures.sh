#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
FIXTURE_ROOT="$ROOT_DIR/foggy-dataset-demo/docker/v934"

usage() {
  cat <<'USAGE'
Usage: manage-database-foundation-fixtures.sh <apply|clean> <mysql57|mysql8|postgres15|sqlserver2022|all>

Applies or removes only the test-owned 9.3.4 sentinel, parity, and isolated
pre-aggregation fixtures. The
containers must already be healthy and must match the frozen image IDs.
This is a diagnostic helper, not the final run-scoped authority. If an apply
fails partway, invoke clean after restoring the failed database; the final
matrix runner must own an EXIT cleanup trap and fresh storage.
USAGE
}

fail() {
  echo "[v934-db-foundation] ERROR: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command is missing: $1"
}

require_container() {
  local container="$1"
  local expected_image_id="$2"
  local container_port="$3"
  local expected_host_port="$4"
  local actual_image_id health mapped_port

  docker inspect "$container" >/dev/null 2>&1 || fail "container is missing: $container"
  actual_image_id="$(docker inspect -f '{{.Image}}' "$container")"
  health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container")"
  mapped_port="$(docker port "$container" "$container_port/tcp" | tail -n 1)"

  [[ "$actual_image_id" == "$expected_image_id" ]] ||
    fail "$container image ID is $actual_image_id, expected $expected_image_id"
  [[ "$health" == healthy ]] || fail "$container health is $health, expected healthy"
  [[ "$mapped_port" == *":$expected_host_port" ]] ||
    fail "$container mapped port is $mapped_port, expected host port $expected_host_port"
}

assert_fixture_output() {
  local database="$1"
  local output="$2"
  local expected=$'contract_version|9.3.4\nV934_PARITY_SENTINEL|1\nV934_PARITY_SENTINEL|2\nV934_ALPHA|50.0000\nV934_BETA|40.0000\nV934_GAMMA|10.0000'
  [[ "$output" == "$expected" ]] ||
    fail "$database fixture verification mismatch: $(printf '%q' "$output")"
  printf 'V934_DATABASE_FIXTURE database=%s sentinel_sha256=%s parity_rows=2 preagg_rows=4 preagg_total=100.0000 status=verified\n' \
    "$database" \
    cef04c4c1269e1293bf243e61e0a9672697bfd55b0bca48297943026bd82c191
}

mysql_query() {
  local container="$1"
  local sql="$2"
  docker exec -e MYSQL_PWD=foggy_test_123 "$container" \
    mysql --batch --raw --skip-column-names -ufoggy foggy_test -e "$sql"
}

apply_mysql() {
  local database="$1"
  local container="$2"
  local image_id="$3"
  local host_port="$4"
  local version_contract="$5"
  local version output

  require_container "$container" "$image_id" 3306 "$host_port"
  version="$(mysql_query "$container" 'SELECT VERSION();')"
  [[ "$version" == $version_contract ]] ||
    fail "$database version is $version, expected $version_contract"
  docker exec -i -e MYSQL_PWD=foggy_test_123 "$container" \
    mysql -ufoggy foggy_test < "$FIXTURE_ROOT/mysql/12-v934-sentinel.sql"
  docker exec -i -e MYSQL_PWD=foggy_test_123 "$container" \
    mysql -ufoggy foggy_test < "$FIXTURE_ROOT/mysql/13-v934-parity-fixture.sql"
  docker exec -i -e MYSQL_PWD=foggy_test_123 "$container" \
    mysql -ufoggy foggy_test < "$FIXTURE_ROOT/mysql/14-v934-preagg-fixture.sql"
  output="$(mysql_query "$container" \
    "SELECT CONCAT(sentinel_key, '|', sentinel_value) FROM v934_test_sentinel ORDER BY sentinel_key;
     SELECT CONCAT(order_id, '|', order_line_no) FROM fact_sales WHERE order_id = 'V934_PARITY_SENTINEL' ORDER BY order_line_no;
     SELECT CONCAT(category_name, '|', CAST(SUM(sales_amount_sum) AS CHAR))
       FROM v934_preagg_daily_product_sales GROUP BY category_name ORDER BY category_name;")"
  assert_fixture_output "$database" "$output"
}

clean_mysql() {
  local database="$1"
  local container="$2"
  local image_id="$3"
  local host_port="$4"

  require_container "$container" "$image_id" 3306 "$host_port"
  local output

  mysql_query "$container" \
    "DELETE FROM fact_sales WHERE order_id = 'V934_PARITY_SENTINEL';
     DROP TABLE IF EXISTS v934_preagg_daily_product_sales;
     DROP TABLE IF EXISTS v934_preagg_fact_sales;
     DROP TABLE IF EXISTS v934_preagg_dim_product;
     DROP TABLE IF EXISTS v934_preagg_dim_date;
     DROP TABLE IF EXISTS v934_test_sentinel;" \
    >/dev/null
  output="$(mysql_query "$container" \
    "SELECT CONCAT(COUNT(*), '|',
       (SELECT COUNT(*) FROM information_schema.tables
        WHERE table_schema = 'foggy_test'
          AND table_name IN ('v934_preagg_daily_product_sales', 'v934_preagg_fact_sales',
                             'v934_preagg_dim_product', 'v934_preagg_dim_date', 'v934_test_sentinel')))
     FROM fact_sales WHERE order_id = 'V934_PARITY_SENTINEL';")"
  [[ "$output" == '0|0' ]] || fail "$database cleanup verification mismatch: $output"
  printf 'V934_DATABASE_FIXTURE database=%s sentinel_rows=0 fixture_tables=0 status=cleaned\n' "$database"
}

postgres_query() {
  local sql="$1"
  docker exec foggy-demo-postgres psql -v ON_ERROR_STOP=1 -At -U foggy -d foggy_test -c "$sql"
}

apply_postgres() {
  local identity version output

  require_container foggy-demo-postgres \
    sha256:fceb6f86328c36f2438fae3b851b0cc57c4a7e69a58c866d9ce24281f2cf0c9c \
    5432 15432
  identity="$(postgres_query "SELECT current_database() || '|' || current_schema();")"
  [[ "$identity" == 'foggy_test|public' ]] ||
    fail "postgres15 identity is $identity, expected foggy_test|public"
  version="$(postgres_query 'SHOW server_version;')"
  [[ "$version" == 15.* ]] || fail "postgres15 version is $version, expected 15.x"
  docker exec -i foggy-demo-postgres psql -v ON_ERROR_STOP=1 -U foggy -d foggy_test \
    < "$FIXTURE_ROOT/postgres/12-v934-sentinel.sql" >/dev/null
  docker exec -i foggy-demo-postgres psql -v ON_ERROR_STOP=1 -U foggy -d foggy_test \
    < "$FIXTURE_ROOT/postgres/13-v934-parity-fixture.sql" >/dev/null
  docker exec -i foggy-demo-postgres psql -v ON_ERROR_STOP=1 -U foggy -d foggy_test \
    < "$FIXTURE_ROOT/postgres/14-v934-preagg-fixture.sql" >/dev/null
  output="$(postgres_query \
    "SELECT sentinel_key || '|' || sentinel_value FROM public.v934_test_sentinel ORDER BY sentinel_key;
     SELECT order_id || '|' || order_line_no FROM public.fact_sales WHERE order_id = 'V934_PARITY_SENTINEL' ORDER BY order_line_no;
     SELECT category_name || '|' || SUM(sales_amount_sum)::text
       FROM public.v934_preagg_daily_product_sales GROUP BY category_name ORDER BY category_name;")"
  assert_fixture_output postgres15 "$output"
}

clean_postgres() {
  local identity output

  require_container foggy-demo-postgres \
    sha256:fceb6f86328c36f2438fae3b851b0cc57c4a7e69a58c866d9ce24281f2cf0c9c \
    5432 15432
  identity="$(postgres_query "SELECT current_database() || '|' || current_schema();")"
  [[ "$identity" == 'foggy_test|public' ]] ||
    fail "postgres15 identity is $identity, expected foggy_test|public"
  postgres_query \
    "DELETE FROM public.fact_sales WHERE order_id = 'V934_PARITY_SENTINEL';
     DROP TABLE IF EXISTS public.v934_preagg_daily_product_sales;
     DROP TABLE IF EXISTS public.v934_preagg_fact_sales;
     DROP TABLE IF EXISTS public.v934_preagg_dim_product;
     DROP TABLE IF EXISTS public.v934_preagg_dim_date;
     DROP TABLE IF EXISTS public.v934_test_sentinel;" \
    >/dev/null
  output="$(postgres_query \
    "SELECT COUNT(*) || '|' ||
       (SELECT COUNT(*) FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name IN ('v934_preagg_daily_product_sales', 'v934_preagg_fact_sales',
                             'v934_preagg_dim_product', 'v934_preagg_dim_date', 'v934_test_sentinel'))
     FROM public.fact_sales WHERE order_id = 'V934_PARITY_SENTINEL';")"
  [[ "$output" == '0|0' ]] || fail "postgres15 cleanup verification mismatch: $output"
  printf 'V934_DATABASE_FIXTURE database=postgres15 sentinel_rows=0 fixture_tables=0 status=cleaned\n'
}

sqlserver_query() {
  local sql="$1"
  docker exec foggy-demo-sqlserver /opt/mssql-tools18/bin/sqlcmd \
    -S localhost -U sa -P 'Foggy_Test_123!' -C -b -h -1 -W -d foggy_test -Q "$sql" \
    | tr -d '\r' | sed '/^[[:space:]]*$/d'
}

apply_sqlserver() {
  local version output

  require_container foggy-demo-sqlserver \
    sha256:0ec7739e1c5ec2f57861facbe1f2b74f1d3e147c7c97edf91eeea920c5944d9c \
    1433 11433
  version="$(sqlserver_query "SET NOCOUNT ON; SELECT CAST(SERVERPROPERTY('ProductVersion') AS varchar(30));")"
  [[ "$version" == 16.0.* ]] ||
    fail "sqlserver2022 version is $version, expected 16.0.x"
  printf 'V934_DATABASE_IDENTITY database=sqlserver2022 version=%s\n' "$version"
  docker exec -i foggy-demo-sqlserver /opt/mssql-tools18/bin/sqlcmd \
    -S localhost -U sa -P 'Foggy_Test_123!' -C -b -d foggy_test \
    < "$FIXTURE_ROOT/sqlserver/12-v934-sentinel.sql" >/dev/null
  docker exec -i foggy-demo-sqlserver /opt/mssql-tools18/bin/sqlcmd \
    -S localhost -U sa -P 'Foggy_Test_123!' -C -b -d foggy_test \
    < "$FIXTURE_ROOT/sqlserver/13-v934-parity-fixture.sql" >/dev/null
  docker exec -i foggy-demo-sqlserver /opt/mssql-tools18/bin/sqlcmd \
    -S localhost -U sa -P 'Foggy_Test_123!' -C -b -d foggy_test \
    < "$FIXTURE_ROOT/sqlserver/14-v934-preagg-fixture.sql" >/dev/null
  output="$(sqlserver_query \
    "SET NOCOUNT ON;
     SELECT sentinel_key + '|' + sentinel_value FROM dbo.v934_test_sentinel ORDER BY sentinel_key;
     SELECT order_id + '|' + CONVERT(varchar(10), order_line_no) FROM dbo.fact_sales WHERE order_id = 'V934_PARITY_SENTINEL' ORDER BY order_line_no;
     SELECT category_name + '|' + CONVERT(varchar(40), CAST(SUM(sales_amount_sum) AS decimal(20,4)))
       FROM dbo.v934_preagg_daily_product_sales GROUP BY category_name ORDER BY category_name;")"
  assert_fixture_output sqlserver2022 "$output"
}

clean_sqlserver() {
  local output

  require_container foggy-demo-sqlserver \
    sha256:0ec7739e1c5ec2f57861facbe1f2b74f1d3e147c7c97edf91eeea920c5944d9c \
    1433 11433
  sqlserver_query \
    "SET NOCOUNT ON; DELETE FROM dbo.fact_sales WHERE order_id = 'V934_PARITY_SENTINEL';
     IF OBJECT_ID(N'dbo.v934_preagg_daily_product_sales', N'U') IS NOT NULL DROP TABLE dbo.v934_preagg_daily_product_sales;
     IF OBJECT_ID(N'dbo.v934_preagg_fact_sales', N'U') IS NOT NULL DROP TABLE dbo.v934_preagg_fact_sales;
     IF OBJECT_ID(N'dbo.v934_preagg_dim_product', N'U') IS NOT NULL DROP TABLE dbo.v934_preagg_dim_product;
     IF OBJECT_ID(N'dbo.v934_preagg_dim_date', N'U') IS NOT NULL DROP TABLE dbo.v934_preagg_dim_date;
     IF OBJECT_ID(N'dbo.v934_test_sentinel', N'U') IS NOT NULL DROP TABLE dbo.v934_test_sentinel;" \
    >/dev/null
  output="$(sqlserver_query \
    "SET NOCOUNT ON;
     SELECT CONVERT(varchar(10), COUNT(*)) + '|' +
       CONVERT(varchar(10), (SELECT COUNT(*) FROM sys.tables
         WHERE name IN ('v934_preagg_daily_product_sales', 'v934_preagg_fact_sales',
                        'v934_preagg_dim_product', 'v934_preagg_dim_date', 'v934_test_sentinel')))
     FROM dbo.fact_sales WHERE order_id = 'V934_PARITY_SENTINEL';")"
  [[ "$output" == '0|0' ]] || fail "sqlserver2022 cleanup verification mismatch: $output"
  printf 'V934_DATABASE_FIXTURE database=sqlserver2022 sentinel_rows=0 fixture_tables=0 status=cleaned\n'
}

manage_one() {
  local action="$1"
  local database="$2"
  case "$action:$database" in
    apply:mysql57)
      apply_mysql mysql57 foggy-demo-mysql \
        sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb \
        13306 5.7.44-log
      ;;
    apply:mysql8)
      apply_mysql mysql8 foggy-demo-mysql8 \
        sha256:f37951fc3753a6a22d6c7bf6978c5e5fefcf6f31814d98c582524f98eae52b21 \
        13308 '8.0.'\*
      ;;
    apply:postgres15) apply_postgres ;;
    apply:sqlserver2022) apply_sqlserver ;;
    clean:mysql57)
      clean_mysql mysql57 foggy-demo-mysql \
        sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb 13306
      ;;
    clean:mysql8)
      clean_mysql mysql8 foggy-demo-mysql8 \
        sha256:f37951fc3753a6a22d6c7bf6978c5e5fefcf6f31814d98c582524f98eae52b21 13308
      ;;
    clean:postgres15) clean_postgres ;;
    clean:sqlserver2022) clean_sqlserver ;;
    *) fail "unsupported action/database pair: $action/$database" ;;
  esac
}

main() {
  local action="${1:-}"
  local database="${2:-}"
  local item

  [[ "$action" == apply || "$action" == clean ]] || {
    usage >&2
    exit 2
  }
  [[ "$database" =~ ^(mysql57|mysql8|postgres15|sqlserver2022|all)$ ]] || {
    usage >&2
    exit 2
  }
  for command_name in docker sed tail tr; do
    require_command "$command_name"
  done
  if [[ "$database" == all ]]; then
    for item in mysql57 mysql8 postgres15 sqlserver2022; do
      manage_one "$action" "$item"
    done
  else
    manage_one "$action" "$database"
  fi
}

main "$@"
