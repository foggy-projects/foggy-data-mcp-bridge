#!/bin/bash

# 图形渲染服务启动脚本
# 用于本地开发和生产环境启动

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_debug() {
    if [[ "${DEBUG:-false}" == "true" ]]; then
        echo -e "${BLUE}[DEBUG]${NC} $1"
    fi
}

# 检查环境
check_environment() {
    log_info "检查运行环境..."

    # 检查Node.js版本
    if ! command -v node &> /dev/null; then
        log_error "Node.js未安装"
        exit 1
    fi

    local node_version=$(node --version | cut -d'.' -f1 | sed 's/v//')
    if [[ $node_version -lt 18 ]]; then
        log_error "Node.js版本过低，需要18.0.0或更高版本"
        exit 1
    fi

    log_info "Node.js版本: $(node --version)"

    # 检查npm
    if ! command -v npm &> /dev/null; then
        log_error "npm未安装"
        exit 1
    fi

    log_info "npm版本: $(npm --version)"
}

# 安装依赖
install_dependencies() {
    log_info "检查依赖..."

    if [[ ! -d "node_modules" ]] || [[ "package.json" -nt "node_modules" ]]; then
        log_info "安装依赖..."
        npm ci
    else
        log_info "依赖已是最新"
    fi
}

# 创建必要目录
create_directories() {
    log_info "创建必要目录..."

    mkdir -p logs
    mkdir -p temp

    # 设置日志目录权限
    chmod 755 logs
    chmod 755 temp
}

# 检查端口
check_port() {
    local port=${1:-3000}

    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null; then
        log_warn "端口 $port 已被占用"

        if [[ "${FORCE_KILL:-false}" == "true" ]]; then
            log_info "强制终止占用端口的进程..."
            lsof -ti:$port | xargs kill -9 2>/dev/null || true
            sleep 2
        else
            log_error "请先停止占用端口 $port 的进程，或使用 FORCE_KILL=true 选项"
            exit 1
        fi
    fi
}

# 健康检查
health_check() {
    local port=${PORT:-3000}
    local max_attempts=30
    local attempt=1

    log_info "等待服务启动..."

    while [[ $attempt -le $max_attempts ]]; do
        if curl -f -s "http://localhost:$port/healthz" >/dev/null; then
            log_info "服务启动成功! (尝试 $attempt/$max_attempts)"
            return 0
        fi

        if [[ $attempt -le 5 ]]; then
            sleep 1
        else
            sleep 2
        fi

        ((attempt++))
    done

    log_error "服务启动失败或健康检查超时"
    return 1
}

# 显示服务信息
show_service_info() {
    local port=${PORT:-3000}

    echo
    log_info "图形渲染服务已启动"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo -e "  🌐 服务地址:     ${GREEN}http://localhost:$port${NC}"
    echo -e "  🏥 健康检查:     ${GREEN}http://localhost:$port/healthz${NC}"
    echo -e "  📊 统一渲染:     ${BLUE}POST http://localhost:$port/render/unified${NC}"
    echo -e "  🎨 原生渲染:     ${BLUE}POST http://localhost:$port/render/native${NC}"
    echo -e "  📈 队列状态:     ${BLUE}GET http://localhost:$port/render/queue/status${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo -e "  📝 日志目录:     ${YELLOW}./logs/${NC}"
    echo -e "  🔧 临时文件:     ${YELLOW}./temp/${NC}"
    echo -e "  ⚙️  环境:        ${YELLOW}${NODE_ENV:-development}${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo
}

# 主函数
main() {
    local command=${1:-start}

    case $command in
        "start")
            log_info "启动图形渲染服务..."

            # 环境检查
            check_environment
            install_dependencies
            create_directories

            # 端口检查
            local port=${PORT:-3000}
            check_port $port

            # 启动服务
            if [[ "${NODE_ENV:-development}" == "development" ]]; then
                npm run dev &
            else
                npm start &
            fi

            local service_pid=$!
            echo $service_pid > .service.pid

            # 健康检查
            if health_check; then
                show_service_info

                # 前台运行时等待进程
                if [[ "${BACKGROUND:-false}" != "true" ]]; then
                    log_info "按 Ctrl+C 停止服务"
                    wait $service_pid
                fi
            else
                log_error "服务启动失败"
                kill $service_pid 2>/dev/null || true
                exit 1
            fi
            ;;

        "stop")
            log_info "停止图形渲染服务..."

            if [[ -f ".service.pid" ]]; then
                local pid=$(cat .service.pid)
                if kill -0 $pid 2>/dev/null; then
                    kill $pid
                    log_info "服务已停止 (PID: $pid)"
                else
                    log_warn "服务进程已不存在"
                fi
                rm -f .service.pid
            else
                log_warn "未找到服务PID文件"
            fi
            ;;

        "restart")
            $0 stop
            sleep 2
            $0 start
            ;;

        "status")
            local port=${PORT:-3000}

            if curl -f -s "http://localhost:$port/healthz" >/dev/null; then
                log_info "服务运行正常"
                curl -s "http://localhost:$port/healthz" | jq . 2>/dev/null || echo "健康检查响应成功"
            else
                log_error "服务未运行或健康检查失败"
                exit 1
            fi
            ;;

        "logs")
            if [[ -f "logs/combined.log" ]]; then
                tail -f logs/combined.log
            else
                log_warn "日志文件不存在"
            fi
            ;;

        "test")
            log_info "运行测试渲染..."

            local port=${PORT:-3000}
            local test_payload='{"engine_spec":{"title":{"text":"测试图表"},"xAxis":{"type":"category","data":["Mon","Tue","Wed","Thu","Fri","Sat","Sun"]},"yAxis":{"type":"value"},"series":[{"data":[120,200,150,80,70,110,130],"type":"bar"}]},"image":{"format":"png","width":800,"height":600}}'

            if [[ "${RENDER_AUTH_TOKEN:-default-render-token}" != "default-render-token" ]]; then
                local auth_header="Authorization: ${RENDER_AUTH_TOKEN}"
            else
                local auth_header="Authorization: default-render-token"
            fi

            local response=$(curl -s -w "%{http_code}" -H "Content-Type: application/json" -H "$auth_header" -X POST -d "$test_payload" "http://localhost:$port/render/native")
            local http_code="${response: -3}"
            local body="${response%???}"

            if [[ "$http_code" == "200" ]]; then
                log_info "测试渲染成功"
                echo "$body" | jq . 2>/dev/null || echo "$body"
            else
                log_error "测试渲染失败 (HTTP $http_code)"
                echo "$body"
                exit 1
            fi
            ;;

        *)
            echo "用法: $0 {start|stop|restart|status|logs|test}"
            echo
            echo "命令说明:"
            echo "  start   - 启动服务"
            echo "  stop    - 停止服务"
            echo "  restart - 重启服务"
            echo "  status  - 检查服务状态"
            echo "  logs    - 查看实时日志"
            echo "  test    - 运行测试渲染"
            echo
            echo "环境变量:"
            echo "  PORT=3000              - 服务端口"
            echo "  NODE_ENV=development   - 运行环境"
            echo "  BACKGROUND=false       - 后台运行"
            echo "  FORCE_KILL=false       - 强制终止占用端口的进程"
            echo "  DEBUG=false            - 启用调试输出"
            exit 1
            ;;
    esac
}

# 信号处理
trap 'log_info "收到停止信号，正在关闭服务..."; $0 stop; exit 0' SIGTERM SIGINT

# 执行主函数
main "$@"