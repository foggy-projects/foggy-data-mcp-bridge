#!/bin/bash
# ============================================
# Initialize closure tables for E2E testing
# Usage:
#   Docker:  ./init_closure_tables.sh docker
#   Local:   ./init_closure_tables.sh local [dbname] [user]
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SQL_FILE="$SCRIPT_DIR/../../sql/refresh_closure_tables.sql"

if [ ! -f "$SQL_FILE" ]; then
    echo "ERROR: refresh_closure_tables.sql not found at $SQL_FILE"
    exit 1
fi

MODE="${1:-docker}"
DB="${2:-odoo}"
USER="${3:-odoo}"

case "$MODE" in
    docker)
        echo "==> Creating closure table functions in Docker PostgreSQL..."
        docker exec -i foggy-odoo-postgres psql -U "$USER" -d "$DB" < "$SQL_FILE"
        echo "==> Refreshing all closure tables..."
        docker exec -i foggy-odoo-postgres psql -U "$USER" -d "$DB" -c "SELECT refresh_all_closures();"
        echo "==> Verifying closure table counts..."
        docker exec -i foggy-odoo-postgres psql -U "$USER" -d "$DB" -c "
            SELECT 'res_company_closure' AS table_name, COUNT(*) AS rows FROM res_company_closure
            UNION ALL
            SELECT 'hr_department_closure', COUNT(*) FROM hr_department_closure
            UNION ALL
            SELECT 'hr_employee_closure', COUNT(*) FROM hr_employee_closure
            UNION ALL
            SELECT 'res_partner_closure', COUNT(*) FROM res_partner_closure;
        "
        ;;
    local)
        echo "==> Creating closure table functions in local PostgreSQL..."
        psql -U "$USER" -d "$DB" < "$SQL_FILE"
        echo "==> Refreshing all closure tables..."
        psql -U "$USER" -d "$DB" -c "SELECT refresh_all_closures();"
        echo "==> Verifying closure table counts..."
        psql -U "$USER" -d "$DB" -c "
            SELECT 'res_company_closure' AS table_name, COUNT(*) AS rows FROM res_company_closure
            UNION ALL
            SELECT 'hr_department_closure', COUNT(*) FROM hr_department_closure
            UNION ALL
            SELECT 'hr_employee_closure', COUNT(*) FROM hr_employee_closure
            UNION ALL
            SELECT 'res_partner_closure', COUNT(*) FROM res_partner_closure;
        "
        ;;
    *)
        echo "Usage: $0 {docker|local} [dbname] [user]"
        exit 1
        ;;
esac

echo "==> Done! Closure tables are ready for E2E testing."
