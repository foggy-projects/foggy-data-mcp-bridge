# 快速开始

本指南帮助你在 5 分钟内启动 Foggy MCP 服务并连接 AI 客户端。

## 前置条件

- Docker 20.10+
- Docker Compose 2.0+
- AI 服务 API Key (可选)（OpenAI / 阿里云百炼 / Ollama）

## Docker 快速启动

### 1. 克隆项目

```bash
git clone https://github.com/nicecho/foggy-data-mcp-bridge.git
cd foggy-data-mcp-bridge/docker/demo
```

### 2. 配置 AI 服务

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env 设置 API Key
```

最小配置只需设置一个变量：

```bash
# .env
OPENAI_API_KEY=sk-your-api-key-here
```

**支持的 AI 服务：**

| 服务 | BASE_URL | 模型示例 |
|------|----------|----------|
| OpenAI | `https://api.openai.com` | gpt-4o-mini |
| 阿里云百炼 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | qwen-plus |
| Ollama | `http://host.docker.internal:11434/v1` | qwen2:7b |

### 3. 启动服务

```bash
# 一键启动（首次需要构建镜像，约 3-5 分钟）
docker-compose up -d

# 查看启动日志
docker-compose logs -f mcp
```

### 4. 验证服务

```bash
# 健康检查
curl http://localhost:7108/actuator/health

# 获取可用工具列表
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}'
```

## 连接 AI 客户端

### Claude Desktop

编辑 Claude Desktop 配置文件：

- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

重启 Claude Desktop 后即可使用。

### Cursor

在 Cursor 设置中添加 MCP 服务器：

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

## 测试查询

连接成功后，可以在 AI 客户端中尝试：

```
"查询最近一周的销售数据"
"按商品分类统计销售额"
"上个月销售额前 10 的商品"
"各门店的订单数量对比"
```

## 预置数据模型

演示环境包含电商场景的数据模型：

| 查询模型 | 说明 | 主要字段 |
|---------|------|----------|
| FactSalesQueryModel | 销售分析 | 商品、分类、销售额、数量 |
| FactOrderQueryModel | 订单分析 | 订单号、客户、日期、金额 |
| FactPaymentQueryModel | 支付分析 | 支付方式、金额、状态 |
| FactReturnQueryModel | 退货分析 | 退货原因、金额、处理状态 |
| FactInventorySnapshotQueryModel | 库存快照 | 商品、仓库、库存量 |

## 常用命令

```bash
# 停止服务
docker-compose down

# 重启 MCP 服务
docker-compose restart mcp

# 查看实时日志
docker-compose logs -f mcp

# 清空数据重新开始
docker-compose down -v
docker-compose up -d
```

## 访问数据库

如需查看演示数据：

```bash
# 启动 Adminer（数据库管理工具）
docker-compose --profile tools up -d adminer
```

访问 http://localhost:18080：
- **系统**: MySQL
- **服务器**: mysql
- **用户名**: foggy
- **密码**: foggy_test_123
- **数据库**: foggy_test

## 配置参考

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `OPENAI_API_KEY` | AI API Key | (必填) |
| `OPENAI_BASE_URL` | AI 服务地址 | https://api.openai.com |
| `OPENAI_MODEL` | 模型名称 | gpt-4o-mini |
| `MCP_PORT` | MCP 服务端口 | 7108 |
| `LOG_LEVEL` | 日志级别 | INFO |

### 端点说明

| 端点 | 用途 |
|------|------|
| `/mcp/admin/rpc` | 管理员端点（全部工具） |
| `/mcp/analyst/rpc` | 分析师端点（专业工具） |
| `/mcp/business/rpc` | 业务端点（自然语言查询） |
| `/actuator/health` | 健康检查 |

## 故障排查

### 服务启动失败

```bash
# 检查各服务状态
docker-compose ps

# 查看详细日志
docker-compose logs mcp
```

### 无法连接 AI 服务

1. 检查 `OPENAI_API_KEY` 是否正确
2. 检查网络连接
3. 如使用阿里云，确认 `OPENAI_BASE_URL` 包含 `/v1`

### Claude Desktop 无法连接

1. 确认配置文件路径正确
2. 检查 JSON 格式是否有效
3. 完全退出并重启 Claude Desktop

## 下一步

- [架构概述](./architecture.md) - 了解 MCP 服务架构
- [工具列表](../tools/overview.md) - 查看所有可用工具
- [Claude Desktop 集成](../integration/claude-desktop.md) - 详细配置指南
