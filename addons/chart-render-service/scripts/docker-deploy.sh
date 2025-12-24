#!/bin/bash

# Docker部署脚本
# 支持本地构建和Harbor推送

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 配置变量
DEFAULT_IMAGE_NAME="chart-render-service"
DEFAULT_TAG="latest"
DEFAULT_HARBOR_REGISTRY="harbor.qlfloor.com"
DEFAULT_HARBOR_PROJECT="foggy-framework"

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

# 显示帮助信息
show_help() {
    cat << EOF
Docker部署脚本 - 图形渲染服务

用法: $0 <command> [options]

命令:
  build           构建Docker镜像
  run             运行Docker容器
  push            推送镜像到Harbor
  deploy          完整部署流程 (构建 + 推送)
  clean           清理本地镜像和容器
  logs            查看容器日志
  status          查看容器状态

选项:
  -t, --tag TAG                镜像标签 (默认: latest)
  -n, --name NAME              镜像名称 (默认: chart-render-service)
  -r, --registry REGISTRY      Harbor地址 (默认: harbor.qlfloor.com)
  -p, --project PROJECT        Harbor项目 (默认: foggy-framework)
  -e, --env ENV                环境配置文件路径
  --port PORT                  映射端口 (默认: 3000)
  --auth-token TOKEN           认证令牌
  --no-cache                   构建时不使用缓存
  --force                      强制操作
  -h, --help                   显示帮助信息

环境变量:
  HARBOR_USERNAME              Harbor用户名
  HARBOR_PASSWORD              Harbor密码
  RENDER_AUTH_TOKEN           渲染服务认证令牌

示例:
  $0 build                                    # 构建镜像
  $0 build -t v1.0.0 --no-cache             # 构建指定版本镜像且不使用缓存
  $0 run --port 3001 --auth-token my-token   # 运行容器
  $0 push -t v1.0.0                         # 推送镜像到Harbor
  $0 deploy -t production                    # 完整部署流程
  $0 clean --force                           # 强制清理所有相关资源

EOF
}

# 解析命令行参数
parse_args() {
    COMMAND=""
    IMAGE_NAME="$DEFAULT_IMAGE_NAME"
    TAG="$DEFAULT_TAG"
    HARBOR_REGISTRY="$DEFAULT_HARBOR_REGISTRY"
    HARBOR_PROJECT="$DEFAULT_HARBOR_PROJECT"
    PORT="3000"
    ENV_FILE=""
    AUTH_TOKEN="${RENDER_AUTH_TOKEN:-default-render-token}"
    NO_CACHE=false
    FORCE=false

    while [[ $# -gt 0 ]]; do
        case $1 in
            build|run|push|deploy|clean|logs|status)
                COMMAND="$1"
                shift
                ;;
            -t|--tag)
                TAG="$2"
                shift 2
                ;;
            -n|--name)
                IMAGE_NAME="$2"
                shift 2
                ;;
            -r|--registry)
                HARBOR_REGISTRY="$2"
                shift 2
                ;;
            -p|--project)
                HARBOR_PROJECT="$2"
                shift 2
                ;;
            -e|--env)
                ENV_FILE="$2"
                shift 2
                ;;
            --port)
                PORT="$2"
                shift 2
                ;;
            --auth-token)
                AUTH_TOKEN="$2"
                shift 2
                ;;
            --no-cache)
                NO_CACHE=true
                shift
                ;;
            --force)
                FORCE=true
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                log_error "未知选项: $1"
                show_help
                exit 1
                ;;
        esac
    done

    if [[ -z "$COMMAND" ]]; then
        log_error "必须指定一个命令"
        show_help
        exit 1
    fi

    # 构建完整镜像名称
    LOCAL_IMAGE="${IMAGE_NAME}:${TAG}"
    HARBOR_IMAGE="${HARBOR_REGISTRY}/${HARBOR_PROJECT}/${IMAGE_NAME}:${TAG}"
}

# 检查Docker环境
check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker未安装"
        exit 1
    fi

    if ! docker info &> /dev/null; then
        log_error "Docker daemon未运行"
        exit 1
    fi

    log_debug "Docker环境检查通过"
}

# 构建镜像
build_image() {
    log_info "构建Docker镜像: $LOCAL_IMAGE"

    local build_args=()

    if [[ "$NO_CACHE" == "true" ]]; then
        build_args+=(--no-cache)
    fi

    # 添加构建参数
    if [[ -n "$AUTH_TOKEN" ]]; then
        build_args+=(--build-arg RENDER_AUTH_TOKEN="$AUTH_TOKEN")
    fi

    # 执行构建
    docker build "${build_args[@]}" -t "$LOCAL_IMAGE" .

    log_info "镜像构建完成: $LOCAL_IMAGE"

    # 显示镜像信息
    local image_size=$(docker images --format "table {{.Size}}" "$LOCAL_IMAGE" | tail -1)
    log_info "镜像大小: $image_size"
}

# 运行容器
run_container() {
    local container_name="${IMAGE_NAME}-${TAG}"

    log_info "运行Docker容器: $container_name"

    # 检查容器是否已存在
    if docker ps -a --format '{{.Names}}' | grep -q "^${container_name}$"; then
        if [[ "$FORCE" == "true" ]]; then
            log_info "强制删除已存在的容器"
            docker rm -f "$container_name"
        else
            log_error "容器 $container_name 已存在，使用 --force 选项强制替换"
            exit 1
        fi
    fi

    # 构建docker run参数
    local run_args=(
        --name "$container_name"
        -p "${PORT}:3000"
        -e NODE_ENV=production
        -e RENDER_AUTH_TOKEN="$AUTH_TOKEN"
        --restart unless-stopped
        -d
    )

    # 添加环境文件
    if [[ -n "$ENV_FILE" && -f "$ENV_FILE" ]]; then
        run_args+=(--env-file "$ENV_FILE")
    fi

    # 添加日志配置
    run_args+=(
        --log-driver json-file
        --log-opt max-size=10m
        --log-opt max-file=3
    )

    # 运行容器
    local container_id=$(docker run "${run_args[@]}" "$LOCAL_IMAGE")

    log_info "容器启动成功: $container_id"

    # 等待服务启动
    log_info "等待服务启动..."
    local max_attempts=30
    local attempt=1

    while [[ $attempt -le $max_attempts ]]; do
        if curl -f -s "http://localhost:$PORT/healthz" >/dev/null 2>&1; then
            log_info "服务启动成功! (尝试 $attempt/$max_attempts)"
            break
        fi

        if [[ $attempt -eq $max_attempts ]]; then
            log_error "服务启动超时"
            docker logs "$container_name"
            exit 1
        fi

        sleep 2
        ((attempt++))
    done

    # 显示容器信息
    echo
    log_info "图形渲染服务容器已启动"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo -e "  🐳 容器名称:     ${GREEN}$container_name${NC}"
    echo -e "  🌐 服务地址:     ${GREEN}http://localhost:$PORT${NC}"
    echo -e "  🏥 健康检查:     ${GREEN}http://localhost:$PORT/healthz${NC}"
    echo -e "  📝 查看日志:     ${BLUE}docker logs -f $container_name${NC}"
    echo -e "  ⏹️  停止容器:     ${YELLOW}docker stop $container_name${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# 推送镜像到Harbor
push_image() {
    log_info "推送镜像到Harbor: $HARBOR_IMAGE"

    # 检查Harbor认证
    if [[ -z "$HARBOR_USERNAME" || -z "$HARBOR_PASSWORD" ]]; then
        log_error "请设置HARBOR_USERNAME和HARBOR_PASSWORD环境变量"
        exit 1
    fi

    # 登录Harbor
    log_info "登录Harbor: $HARBOR_REGISTRY"
    echo "$HARBOR_PASSWORD" | docker login "$HARBOR_REGISTRY" -u "$HARBOR_USERNAME" --password-stdin

    # 标记镜像
    log_info "标记镜像: $LOCAL_IMAGE -> $HARBOR_IMAGE"
    docker tag "$LOCAL_IMAGE" "$HARBOR_IMAGE"

    # 推送镜像
    log_info "推送镜像到Harbor..."
    docker push "$HARBOR_IMAGE"

    log_info "镜像推送完成: $HARBOR_IMAGE"

    # 清理本地Harbor标记
    docker rmi "$HARBOR_IMAGE" || true
}

# 完整部署流程
deploy() {
    log_info "开始完整部署流程..."

    build_image
    push_image

    log_info "部署完成!"
    log_info "可以使用以下命令拉取镜像:"
    echo "  docker pull $HARBOR_IMAGE"
}

# 清理资源
clean() {
    log_info "清理Docker资源..."

    local container_name="${IMAGE_NAME}-${TAG}"

    # 停止并删除容器
    if docker ps -a --format '{{.Names}}' | grep -q "^${container_name}$"; then
        log_info "删除容器: $container_name"
        docker rm -f "$container_name"
    fi

    # 删除本地镜像
    if docker images --format '{{.Repository}}:{{.Tag}}' | grep -q "^${LOCAL_IMAGE}$"; then
        if [[ "$FORCE" == "true" ]]; then
            log_info "删除镜像: $LOCAL_IMAGE"
            docker rmi "$LOCAL_IMAGE"
        else
            log_warn "镜像 $LOCAL_IMAGE 存在，使用 --force 选项删除"
        fi
    fi

    # 清理悬空镜像
    local dangling_images=$(docker images -f "dangling=true" -q)
    if [[ -n "$dangling_images" ]]; then
        log_info "清理悬空镜像..."
        docker rmi $dangling_images
    fi

    log_info "清理完成"
}

# 查看容器日志
show_logs() {
    local container_name="${IMAGE_NAME}-${TAG}"

    if ! docker ps -a --format '{{.Names}}' | grep -q "^${container_name}$"; then
        log_error "容器 $container_name 不存在"
        exit 1
    fi

    log_info "显示容器日志: $container_name"
    docker logs -f "$container_name"
}

# 查看容器状态
show_status() {
    local container_name="${IMAGE_NAME}-${TAG}"

    if ! docker ps -a --format '{{.Names}}' | grep -q "^${container_name}$"; then
        log_warn "容器 $container_name 不存在"
        return 1
    fi

    log_info "容器状态:"
    docker ps -a --filter "name=${container_name}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}\t{{.Image}}"

    # 如果容器在运行，显示健康状态
    if docker ps --filter "name=${container_name}" --format '{{.Names}}' | grep -q "^${container_name}$"; then
        echo
        log_info "服务健康状态:"
        if curl -f -s "http://localhost:$PORT/healthz" | jq . 2>/dev/null; then
            log_info "服务运行正常"
        else
            log_error "服务健康检查失败"
        fi
    fi
}

# 主函数
main() {
    parse_args "$@"
    check_docker

    case $COMMAND in
        build)
            build_image
            ;;
        run)
            run_container
            ;;
        push)
            push_image
            ;;
        deploy)
            deploy
            ;;
        clean)
            clean
            ;;
        logs)
            show_logs
            ;;
        status)
            show_status
            ;;
        *)
            log_error "未知命令: $COMMAND"
            show_help
            exit 1
            ;;
    esac
}

# 执行主函数
main "$@"