#!/bin/bash
# ============================================
# Foggy Dataset Demo - Docker init smoke probe
# ============================================
#
# Usage:
#   ./smoke-demo-init.sh [mysql|postgres|sqlserver|all] [--static] [--start] [--init]
#
# Defaults:
#   - target: all
#   - no container startup
#   - no destructive reinitialization
#
# Notes:
#   --start starts the selected database service before probing.
#   --init reruns init-db.sh for the selected target before probing.
#   --static checks SQL script shape without requiring Docker.
#
# ============================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

usage() {
    cat <<'EOF'
Usage: ./smoke-demo-init.sh [mysql|postgres|sqlserver|all] [--static] [--start] [--init] [--timeout N]

Targets:
  mysql       Probe MySQL docker demo fixture
  postgres    Probe PostgreSQL docker demo fixture
  sqlserver   Probe SQL Server docker demo fixture
  all         Probe MySQL, PostgreSQL, and SQL Server

Options:
  --static     Check init SQL files only; Docker is not required
  --start      Start the selected docker-compose service before live probing
  --init       Run ./init-db.sh for the selected target before live probing
  --timeout N  Seconds to wait for container health when --start is used (default: 120)
  -h, --help   Show this help

Without --start, a missing container is skipped instead of treated as failure.
If a selected container is running, SQL probe failures are treated as failures.
EOF
}

target="all"
static_only=false
start_containers=false
run_init=false
timeout_seconds=120

while [[ $# -gt 0 ]]; do
    case "$1" in
        mysql|postgres|sqlserver|all)
            target="$1"
            ;;
        --static)
            static_only=true
            ;;
        --start)
            start_containers=true
            ;;
        --init)
            run_init=true
            ;;
        --timeout)
            shift
            if [[ $# -eq 0 || ! "$1" =~ ^[0-9]+$ ]]; then
                log_error "--timeout requires a numeric value"
                exit 1
            fi
            timeout_seconds="$1"
            ;;
        -h|--help|help)
            usage
            exit 0
            ;;
        *)
            log_error "Unknown argument: $1"
            usage
            exit 1
            ;;
    esac
    shift
done

compose_cmd() {
    if command -v docker-compose >/dev/null 2>&1; then
        docker-compose "$@"
    else
        docker compose "$@"
    fi
}

require_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        log_error "Docker is not installed or not on PATH"
        exit 1
    fi
}

docker_available() {
    command -v docker >/dev/null 2>&1
}

container_running() {
    local container="$1"
    docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null | grep -q '^true$'
}

wait_healthy() {
    local container="$1"
    local deadline=$((SECONDS + timeout_seconds))
    local status

    while (( SECONDS < deadline )); do
        status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
        if [[ "$status" == "healthy" || "$status" == "running" ]]; then
            return 0
        fi
        sleep 3
    done

    log_error "$container did not become healthy within ${timeout_seconds}s"
    docker inspect -f '{{json .State.Health}}' "$container" 2>/dev/null || true
    return 1
}

maybe_prepare_service() {
    local service="$1"
    local container="$2"

    if ! docker_available; then
        if [[ "$start_containers" == true || "$run_init" == true ]]; then
            require_docker
        fi
        log_warn "Docker is not installed or not on PATH; skipping live probe for $service"
        return 1
    fi

    if [[ "$start_containers" == true ]]; then
        log_info "Starting $service..."
        compose_cmd up -d "$service"
        wait_healthy "$container"
    fi

    if [[ "$run_init" == true ]]; then
        log_info "Reinitializing $service through init-db.sh..."
        ./init-db.sh "$service"
        wait_healthy "$container"
    elif ! container_running "$container"; then
        log_warn "$container is not running; skipping live probe for $service"
        return 1
    fi

    return 0
}

run_static_probe() {
    local missing=0

    log_info "Checking docker init SQL files for sales-team fixture parity..."

    for dialect in mysql postgres sqlserver; do
        local schema="$dialect/init/01-schema.sql"
        local data="$dialect/init/03-test-data.sql"

        if [[ ! -f "$schema" || ! -f "$data" ]]; then
            log_error "$dialect init files are missing"
            missing=1
            continue
        fi

        for pattern in "dim_sales_team" "sales_team_key"; do
            if ! grep -q "$pattern" "$schema"; then
                log_error "$schema missing $pattern"
                missing=1
            fi
            if ! grep -q "$pattern" "$data"; then
                log_error "$data missing $pattern"
                missing=1
            fi
        done

        if ! grep -q "INSERT INTO .*fact_order" "$data"; then
            log_error "$data missing fact_order insert"
            missing=1
        fi
    done

    if [[ "$missing" -ne 0 ]]; then
        return 1
    fi

    log_info "Static init SQL parity probe passed"
}

probe_mysql() {
    if ! maybe_prepare_service mysql foggy-demo-mysql; then
        return 0
    fi

    log_info "Running MySQL sales-team smoke query..."
    docker exec -i foggy-demo-mysql mysql -ufoggy -pfoggy_test_123 --batch --raw foggy_test <<'SQL'
SELECT 'dim_sales_team' AS probe, COUNT(*) AS cnt FROM dim_sales_team;
SELECT 'fact_order_total' AS probe, COUNT(*) AS cnt FROM fact_order;
SELECT 'fact_order_mapped' AS probe, COUNT(*) AS cnt FROM fact_order WHERE sales_team_key IS NOT NULL;
SELECT st.team_id, st.team_name, COUNT(*) AS order_count, ROUND(SUM(o.pay_amount), 2) AS pay_amount
FROM fact_order o
JOIN dim_sales_team st ON o.sales_team_key = st.sales_team_key
GROUP BY st.team_id, st.team_name
ORDER BY order_count DESC, st.team_id
LIMIT 5;
SQL
}

probe_postgres() {
    if ! maybe_prepare_service postgres foggy-demo-postgres; then
        return 0
    fi

    log_info "Running PostgreSQL sales-team smoke query..."
    docker exec -i foggy-demo-postgres psql -U foggy -d foggy_test -v ON_ERROR_STOP=1 <<'SQL'
SELECT 'dim_sales_team' AS probe, COUNT(*) AS cnt FROM dim_sales_team;
SELECT 'fact_order_total' AS probe, COUNT(*) AS cnt FROM fact_order;
SELECT 'fact_order_mapped' AS probe, COUNT(*) AS cnt FROM fact_order WHERE sales_team_key IS NOT NULL;
SELECT st.team_id, st.team_name, COUNT(*) AS order_count, ROUND(SUM(o.pay_amount), 2) AS pay_amount
FROM fact_order o
JOIN dim_sales_team st ON o.sales_team_key = st.sales_team_key
GROUP BY st.team_id, st.team_name
ORDER BY order_count DESC, st.team_id
LIMIT 5;
SQL
}

probe_sqlserver() {
    if ! maybe_prepare_service sqlserver foggy-demo-sqlserver; then
        return 0
    fi

    log_info "Running SQL Server sales-team smoke query..."
    docker exec -i foggy-demo-sqlserver /opt/mssql-tools18/bin/sqlcmd \
        -S localhost -U sa -P "Foggy_Test_123!" -C -d foggy_test -b <<'SQL'
SELECT 'dim_sales_team' AS probe, COUNT(*) AS cnt FROM dim_sales_team;
SELECT 'fact_order_total' AS probe, COUNT(*) AS cnt FROM fact_order;
SELECT 'fact_order_mapped' AS probe, COUNT(*) AS cnt FROM fact_order WHERE sales_team_key IS NOT NULL;
SELECT TOP 5 st.team_id, st.team_name, COUNT(*) AS order_count, ROUND(SUM(o.pay_amount), 2) AS pay_amount
FROM fact_order o
JOIN dim_sales_team st ON o.sales_team_key = st.sales_team_key
GROUP BY st.team_id, st.team_name
ORDER BY order_count DESC, st.team_id;
SQL
}

main() {
    if [[ "$static_only" == true ]]; then
        run_static_probe
        return
    fi

    local failed=0
    local selected=()
    case "$target" in
        mysql) selected=(mysql) ;;
        postgres) selected=(postgres) ;;
        sqlserver) selected=(sqlserver) ;;
        all) selected=(mysql postgres sqlserver) ;;
    esac

    for service in "${selected[@]}"; do
        if ! "probe_$service"; then
            failed=1
        fi
    done

    if [[ "$failed" -ne 0 ]]; then
        log_error "One or more docker demo smoke probes failed"
        exit 1
    fi

    log_info "Docker demo sales-team smoke completed"
}

main
