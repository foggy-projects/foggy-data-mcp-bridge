#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# Foggy MCP Server — Odoo Integration Launcher
#
# Starts the Java MCP server in lite mode with built-in Odoo models.
# Data source is configured via DataSource API after startup.
#
# Usage:
#   ./start-foggy-mcp.sh              # default port 7108
#   ./start-foggy-mcp.sh 9090         # custom port
#   ./start-foggy-mcp.sh --stop       # stop only
#
# Prerequisites:
#   - Java 17+
#   - foggy-mcp-launcher JAR built (mvn package -DskipTests)
# ═══════════════════════════════════════════════════════════════

set -euo pipefail

# ─── Configuration ──────────────────────────────────────────
PORT="${1:-7108}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOG_FILE="$SCRIPT_DIR/foggy-mcp.log"
PID_FILE="$SCRIPT_DIR/.foggy-mcp.pid"

# ─── Color helpers ──────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
err()   { echo -e "${RED}[ERROR]${NC} $*"; }

# ─── Kill process on port ──────────────────────────────────
kill_by_port() {
    local port=$1
    local pids=""

    # Windows (Git Bash / MSYS2)
    if command -v netstat.exe &>/dev/null; then
        pids=$(netstat.exe -ano 2>/dev/null \
            | grep ":${port} " | grep "LISTENING" \
            | awk '{print $NF}' | sort -u | tr -d '\r')
    # Linux / macOS
    elif command -v lsof &>/dev/null; then
        pids=$(lsof -ti :"$port" 2>/dev/null || true)
    elif command -v ss &>/dev/null; then
        pids=$(ss -tlnp "sport = :$port" 2>/dev/null \
            | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | sort -u || true)
    fi

    if [[ -z "$pids" ]]; then
        return 1
    fi

    for pid in $pids; do
        [[ "$pid" =~ ^[0-9]+$ ]] || continue
        [[ "$pid" -eq 0 ]] && continue

        info "Killing PID $pid on port $port..."
        if command -v taskkill.exe &>/dev/null; then
            taskkill.exe //PID "$pid" //F 2>/dev/null && ok "Killed $pid" || warn "Failed to kill $pid"
        else
            kill -9 "$pid" 2>/dev/null && ok "Killed $pid" || warn "Failed to kill $pid"
        fi
    done
    sleep 2
}

# ─── Stop mode ──────────────────────────────────────────────
if [[ "${1:-}" == "--stop" ]]; then
    info "Stopping Foggy MCP Server..."
    if [[ -f "$PID_FILE" ]]; then
        OLD_PID=$(cat "$PID_FILE")
        if kill -0 "$OLD_PID" 2>/dev/null; then
            kill "$OLD_PID" && ok "Process $OLD_PID stopped."
        else
            warn "PID $OLD_PID not running."
        fi
        rm -f "$PID_FILE"
    else
        warn "No PID file found. Trying port-based kill..."
        kill_by_port 7108 || warn "No process found."
    fi
    exit 0
fi

# ─── Find JAR ──────────────────────────────────────────────
find_jar() {
    local jar_dir="$PROJECT_ROOT/foggy-mcp-launcher/target"
    local jar
    jar=$(ls "$jar_dir"/foggy-mcp-launcher-*.jar 2>/dev/null | grep -v sources | head -1)
    if [[ -z "$jar" ]]; then
        err "JAR not found in $jar_dir"
        err "Run: cd $PROJECT_ROOT && mvn package -pl foggy-mcp-launcher -am -DskipTests"
        exit 1
    fi
    echo "$jar"
}

# ─── Pre-flight checks ─────────────────────────────────────
preflight() {
    # Java version check (compatible with Git Bash)
    if ! command -v java &>/dev/null; then
        err "Java not found. Install JDK 17+."
        exit 1
    fi
    local java_ver
    java_ver=$(java -version 2>&1 | head -1 | sed -n 's/.*"\([0-9][0-9]*\).*/\1/p')
    if [[ "${java_ver:-0}" -lt 17 ]]; then
        err "Java 17+ required, found: $java_ver"
        exit 1
    fi
    ok "Java $java_ver"
}

# ─── Main ───────────────────────────────────────────────────
main() {
    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║  Foggy MCP Server — Odoo Integration        ║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════╝${NC}"
    echo ""

    preflight

    JAR=$(find_jar)
    ok "JAR: $(basename "$JAR")"
    ok "Built-in Odoo models enabled"

    # Kill existing process on the port
    info "Checking port $PORT..."
    if kill_by_port "$PORT"; then
        ok "Port $PORT cleared."
    else
        ok "Port $PORT is free."
    fi

    info "Starting on port $PORT..."
    info "Log: $LOG_FILE"
    echo ""

    # Launch in background (models are built into JAR, datasource configured via API)
    java -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -jar "$JAR" \
        "--spring.profiles.active=lite,odoo" \
        "--server.port=${PORT}" \
        > "$LOG_FILE" 2>&1 &

    local pid=$!
    echo "$pid" > "$PID_FILE"
    ok "Started with PID $pid"

    # Wait for health check
    info "Waiting for health check..."
    local max_wait=40
    for i in $(seq 1 $max_wait); do
        sleep 1
        if ! kill -0 "$pid" 2>/dev/null; then
            err "Process exited unexpectedly. Last 30 lines:"
            echo ""
            tail -30 "$LOG_FILE"
            exit 1
        fi
        local http_code
        http_code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${PORT}/actuator/health" 2>/dev/null || echo "000")
        if [[ "$http_code" == "200" ]]; then
            echo ""
            ok "Foggy MCP Server is UP!"
            echo ""

            # Verify tools loaded
            local tool_count
            tool_count=$(curl -s -X POST "http://localhost:${PORT}/mcp/admin/rpc" \
                -H "Content-Type: application/json" \
                -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' 2>/dev/null \
                | grep -o '"name"' | wc -l || echo "?")

            echo -e "  ${CYAN}URL:${NC}       http://localhost:${PORT}"
            echo -e "  ${CYAN}Health:${NC}    http://localhost:${PORT}/actuator/health"
            echo -e "  ${CYAN}Admin RPC:${NC} http://localhost:${PORT}/mcp/admin/rpc"
            echo -e "  ${CYAN}Tools:${NC}     ${tool_count} loaded"
            echo -e "  ${CYAN}PID:${NC}       ${pid}"
            echo -e "  ${CYAN}Log:${NC}       ${LOG_FILE}"
            echo ""
            echo -e "  ${YELLOW}Next step:${NC} Configure data source via Setup Wizard or API"
            echo -e "  ${YELLOW}POST ${NC}http://localhost:${PORT}/api/v1/datasource"
            echo ""
            echo -e "  Stop: ${YELLOW}./start-foggy-mcp.sh --stop${NC}"
            echo ""
            return 0
        fi
        printf "\r  waiting... %d/%ds" "$i" "$max_wait"
    done

    echo ""
    warn "Health check timeout after ${max_wait}s. Server may still be starting."
    warn "Check log: tail -f $LOG_FILE"
}

main