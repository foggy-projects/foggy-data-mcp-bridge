#!/bin/bash
# ============================================
# Foggy Dataset Demo - 数据库初始化脚本
# ============================================
#
# 用法:
#   ./init-db.sh [mysql|postgres|sqlserver|all]
#
# 示例:
#   ./init-db.sh mysql      # 仅初始化 MySQL
#   ./init-db.sh all        # 初始化所有数据库
#   ./init-db.sh            # 默认初始化 MySQL
#
# 注意:
#   - MySQL 和 PostgreSQL 首次启动 Docker 时会自动初始化
#   - 此脚本用于重新初始化数据（会清空现有数据）
#   - SQL Server 首次也需要通过此脚本初始化
#
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# MySQL 初始化
init_mysql() {
    log_info "Initializing MySQL database..."

    # 检查容器是否运行
    if ! docker ps | grep -q foggy-demo-mysql; then
        log_warn "MySQL container not running. Starting..."
        docker-compose up -d mysql
        log_info "Waiting for MySQL to be ready..."
        sleep 30
    fi

    # 执行初始化脚本
    log_info "Executing MySQL init scripts..."

    docker exec -i foggy-demo-mysql mysql -ufoggy -pfoggy_test_123 foggy_test < mysql/init/01-schema.sql
    log_info "  - 01-schema.sql executed"

    docker exec -i foggy-demo-mysql mysql -ufoggy -pfoggy_test_123 foggy_test < mysql/init/02-dict-data.sql
    log_info "  - 02-dict-data.sql executed"

    docker exec -i foggy-demo-mysql mysql -ufoggy -pfoggy_test_123 foggy_test < mysql/init/03-test-data.sql
    log_info "  - 03-test-data.sql executed"

    log_info "MySQL initialization completed!"
}

# PostgreSQL 初始化
init_postgres() {
    log_info "Initializing PostgreSQL database..."

    # 检查容器是否运行
    if ! docker ps | grep -q foggy-demo-postgres; then
        log_warn "PostgreSQL container not running. Starting..."
        docker-compose up -d postgres
        log_info "Waiting for PostgreSQL to be ready..."
        sleep 15
    fi

    # 执行初始化脚本
    log_info "Executing PostgreSQL init scripts..."

    docker exec -i foggy-demo-postgres psql -U foggy -d foggy_test < postgres/init/01-schema.sql
    log_info "  - 01-schema.sql executed"

    docker exec -i foggy-demo-postgres psql -U foggy -d foggy_test < postgres/init/02-dict-data.sql
    log_info "  - 02-dict-data.sql executed"

    docker exec -i foggy-demo-postgres psql -U foggy -d foggy_test < postgres/init/03-test-data.sql
    log_info "  - 03-test-data.sql executed"

    log_info "PostgreSQL initialization completed!"
}

# SQL Server 初始化
init_sqlserver() {
    log_info "Initializing SQL Server database..."

    # 检查容器是否运行
    if ! docker ps | grep -q foggy-demo-sqlserver; then
        log_warn "SQL Server container not running. Starting..."
        docker-compose up -d sqlserver
        log_info "Waiting for SQL Server to be ready (this may take a while)..."
        sleep 60
    fi

    # 创建数据库
    log_info "Creating database foggy_test..."
    docker exec -i foggy-demo-sqlserver /opt/mssql-tools18/bin/sqlcmd \
        -S localhost -U sa -P "Foggy_Test_123!" -C \
        -Q "IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = 'foggy_test') CREATE DATABASE foggy_test"

    # 执行初始化脚本
    log_info "Executing SQL Server init scripts..."

    docker exec -i foggy-demo-sqlserver /opt/mssql-tools18/bin/sqlcmd \
        -S localhost -U sa -P "Foggy_Test_123!" -C -d foggy_test \
        -i /scripts/01-schema.sql
    log_info "  - 01-schema.sql executed"

    docker exec -i foggy-demo-sqlserver /opt/mssql-tools18/bin/sqlcmd \
        -S localhost -U sa -P "Foggy_Test_123!" -C -d foggy_test \
        -i /scripts/02-dict-data.sql
    log_info "  - 02-dict-data.sql executed"

    docker exec -i foggy-demo-sqlserver /opt/mssql-tools18/bin/sqlcmd \
        -S localhost -U sa -P "Foggy_Test_123!" -C -d foggy_test \
        -i /scripts/03-test-data.sql
    log_info "  - 03-test-data.sql executed"

    log_info "SQL Server initialization completed!"
}

# 显示帮助
show_help() {
    echo "Usage: $0 [mysql|postgres|sqlserver|all]"
    echo ""
    echo "Options:"
    echo "  mysql      Initialize MySQL database"
    echo "  postgres   Initialize PostgreSQL database"
    echo "  sqlserver  Initialize SQL Server database"
    echo "  all        Initialize all databases"
    echo ""
    echo "If no option is provided, defaults to 'mysql'"
}

# 主函数
main() {
    local target="${1:-mysql}"

    case "$target" in
        mysql)
            init_mysql
            ;;
        postgres)
            init_postgres
            ;;
        sqlserver)
            init_sqlserver
            ;;
        all)
            init_mysql
            init_postgres
            init_sqlserver
            ;;
        -h|--help|help)
            show_help
            ;;
        *)
            log_error "Unknown option: $target"
            show_help
            exit 1
            ;;
    esac
}

main "$@"
