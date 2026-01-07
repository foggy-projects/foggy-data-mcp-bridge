# Docker 镜像发布指南

## 镜像信息

- 仓库: `foggysource/chart-render-service`
- Docker Hub: https://hub.docker.com/r/foggysource/chart-render-service

## 发布步骤

### 1. 登录 Docker Hub

```bash
docker login
```

### 2. 构建镜像

```bash
cd addons/chart-render-service

# 构建并打 latest tag
docker build -t foggysource/chart-render-service:latest .
```

### 3. 推送到 Docker Hub

```bash
docker push foggysource/chart-render-service:latest
```

### 4. (可选) 打版本号 tag

```bash
# 打版本号 tag
docker tag foggysource/chart-render-service:latest foggysource/chart-render-service:1.x.x

# 推送版本号 tag
docker push foggysource/chart-render-service:1.x.x
```

## 一键发布脚本

```bash
# 构建 + 推送 latest
docker build -t foggysource/chart-render-service:latest . && docker push foggysource/chart-render-service:latest
```

## 本地测试

发布前建议先本地测试：

```bash
# 重新构建并启动
docker-compose up -d --build

# 查看日志
docker-compose logs -f

# 测试健康检查
curl http://localhost:3000/healthz
```

## 用户使用

用户可通过以下方式使用官方镜像：

```bash
docker compose -f docker-compose.hub.yml up -d
```
