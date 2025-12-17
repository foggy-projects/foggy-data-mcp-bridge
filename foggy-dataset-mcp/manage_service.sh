#!/bin/bash
# MCP Data Model Java - Service Management Script

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_NAME="mcp-data-model-java-1.0.0-SNAPSHOT.jar"
JAR_PATH="$SCRIPT_DIR/target/$JAR_NAME"
LOG_DIR="$SCRIPT_DIR/logs"

# Load environment variables
if [ -f "$SCRIPT_DIR/.env" ]; then
    export $(grep -v '^#' "$SCRIPT_DIR/.env" | xargs)
fi

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_title() {
    echo -e "${BLUE}=== $1 ===${NC}"
}

get_pid_file() {
    local profile=$1
    echo "$SCRIPT_DIR/.service_${profile}.pid"
}

get_log_file() {
    local profile=$1
    echo "$LOG_DIR/${profile}.log"
}

build() {
    print_title "Building Project"
    cd "$SCRIPT_DIR"
    mvn clean package -DskipTests
    print_status "Build completed: $JAR_PATH"
}

start_service() {
    local profile=${1:-dev}
    local pid_file=$(get_pid_file $profile)
    local log_file=$(get_log_file $profile)

    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if ps -p $pid > /dev/null 2>&1; then
            print_warning "Service [$profile] already running with PID $pid"
            return
        fi
    fi

    if [ ! -f "$JAR_PATH" ]; then
        print_warning "JAR not found, building..."
        build
    fi

    mkdir -p "$LOG_DIR"

    print_status "Starting MCP Data Model [$profile]..."

    local port
    case $profile in
        m1)
            port=${MCP_M1_PORT:-7108}
            ;;
        m2)
            port=${MCP_M2_PORT:-7109}
            ;;
        *)
            port=${SERVER_PORT:-7108}
            ;;
    esac

    nohup java -jar "$JAR_PATH" \
        --spring.profiles.active=$profile \
        > "$log_file" 2>&1 &

    echo $! > "$pid_file"

    sleep 3

    if ps -p $(cat "$pid_file") > /dev/null 2>&1; then
        print_status "Service [$profile] started with PID $(cat $pid_file)"
        print_status "Port: $port"
        print_status "Logs: $log_file"
        print_status "Health: curl http://localhost:$port/healthz"
    else
        print_error "Failed to start service [$profile]. Check logs: $log_file"
        rm -f "$pid_file"
        exit 1
    fi
}

stop_service() {
    local profile=${1:-dev}
    local pid_file=$(get_pid_file $profile)

    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if ps -p $pid > /dev/null 2>&1; then
            print_status "Stopping service [$profile] (PID $pid)..."
            kill $pid
            sleep 2
            if ps -p $pid > /dev/null 2>&1; then
                print_warning "Force killing..."
                kill -9 $pid
            fi
            rm -f "$pid_file"
            print_status "Service [$profile] stopped"
        else
            print_warning "Service [$profile] not running"
            rm -f "$pid_file"
        fi
    else
        print_warning "PID file not found for [$profile]"
    fi
}

start_all() {
    print_title "Starting M1 and M2 Services"
    start_service m1
    start_service m2
    print_status "Both services started"
}

stop_all() {
    print_title "Stopping All Services"
    stop_service m1
    stop_service m2
    stop_service dev
    print_status "All services stopped"
}

restart_service() {
    local profile=${1:-dev}
    stop_service $profile
    sleep 2
    start_service $profile
}

status() {
    print_title "Service Status"

    for profile in dev m1 m2; do
        local pid_file=$(get_pid_file $profile)
        local port
        case $profile in
            m1) port=${MCP_M1_PORT:-7108} ;;
            m2) port=${MCP_M2_PORT:-7109} ;;
            *) port=${SERVER_PORT:-7108} ;;
        esac

        if [ -f "$pid_file" ]; then
            local pid=$(cat "$pid_file")
            if ps -p $pid > /dev/null 2>&1; then
                echo -e "[$profile] ${GREEN}RUNNING${NC} (PID $pid, Port $port)"
                # Health check
                if curl -s "http://localhost:$port/healthz" > /dev/null 2>&1; then
                    echo -e "        Health: ${GREEN}OK${NC}"
                else
                    echo -e "        Health: ${YELLOW}PENDING${NC}"
                fi
            else
                echo -e "[$profile] ${YELLOW}STALE PID${NC}"
            fi
        else
            echo -e "[$profile] ${RED}STOPPED${NC}"
        fi
    done
}

logs() {
    local profile=${1:-dev}
    local log_file=$(get_log_file $profile)
    if [ -f "$log_file" ]; then
        tail -f "$log_file"
    else
        print_error "Log file not found: $log_file"
    fi
}

clean() {
    print_status "Cleaning build artifacts..."
    cd "$SCRIPT_DIR"
    mvn clean
    rm -rf "$LOG_DIR"
    rm -f "$SCRIPT_DIR"/.service_*.pid
    print_status "Cleaned"
}

usage() {
    echo "Usage: $0 <command> [profile]"
    echo ""
    echo "Commands:"
    echo "  build              - Build the project"
    echo "  start [profile]    - Start service (profile: dev/m1/m2, default: dev)"
    echo "  stop [profile]     - Stop service"
    echo "  restart [profile]  - Restart service"
    echo "  start-all          - Start both M1 and M2 services"
    echo "  stop-all           - Stop all services"
    echo "  status             - Check all services status"
    echo "  logs [profile]     - Tail the log file"
    echo "  clean              - Clean build artifacts"
    echo ""
    echo "Profiles:"
    echo "  dev    - Development mode (port 7108)"
    echo "  m1     - M1 Agent interface (port 7108)"
    echo "  m2     - M2 Data analyst interface (port 7109)"
    echo ""
    echo "Examples:"
    echo "  $0 start-all       - Start M1 (7108) and M2 (7109)"
    echo "  $0 start m1        - Start only M1 service"
    echo "  $0 logs m2         - View M2 logs"
}

# Main
case "$1" in
    build)
        build
        ;;
    start)
        start_service ${2:-dev}
        ;;
    stop)
        stop_service ${2:-dev}
        ;;
    restart)
        restart_service ${2:-dev}
        ;;
    start-all)
        start_all
        ;;
    stop-all)
        stop_all
        ;;
    status)
        status
        ;;
    logs)
        logs ${2:-dev}
        ;;
    clean)
        clean
        ;;
    *)
        usage
        exit 1
        ;;
esac
