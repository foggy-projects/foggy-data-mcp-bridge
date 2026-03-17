# Foggy MCP Gateway — 全新安装指南

> 本文档指导你在一台全新机器上，从零搭建 Odoo 17 + Foggy MCP Server 并完成首次数据查询。

## 前置条件

- Docker & Docker Compose
- Git（用于 clone 仓库）
- 至少 4GB 可用内存

## 步骤总览

```
1. 获取项目文件
2. 启动 PostgreSQL + Odoo
3. 安装 Odoo 模块
4. 启动 Foggy MCP Server（Docker 镜像）
5. 初始化闭包表
6. 创建 API Key
7. 验证端到端查询
```

---

## 1. 获取项目文件

```bash
git clone https://github.com/foggy-projects/foggy-data-mcp-bridge.git
cd foggy-data-mcp-bridge/addons/foggy-odoo-bridge/docker
```

## 2. 启动 PostgreSQL + Odoo

```bash
# 启动数据库
docker compose up -d postgres

# 等待 PG 健康检查通过
docker compose ps  # 确认 postgres 状态为 healthy
```

```bash
# 首次安装 Odoo 基础模块 + foggy_mcp（约 2-3 分钟）
docker compose run --rm odoo odoo \
  -i sale,purchase,account,stock,hr,crm,foggy_mcp \
  -d odoo --stop-after-init
```

> **说明**：`-i` 会安装所有依赖模块并初始化 foggy_mcp。
> 如果看到 `WARNING ... module foggy_mcp: external dependency 'litellm' not installed`，
> AI Chat 功能不可用，但 **MCP Gateway 核心功能不受影响**（litellm 仅用于内置聊天）。

```bash
# 正常启动 Odoo
docker compose up -d odoo
```

打开浏览器访问 **http://localhost:8069**

- 账号：`admin`
- 密码：`admin`

### 验证模块安装

1. 进入 **Settings → Technical → Modules → Installed Modules**
2. 搜索 `foggy` → 应看到 **Foggy MCP Gateway** 已安装
3. 左侧菜单应出现 **Foggy MCP** 顶级菜单

## 3. 启动 Foggy MCP Server

```bash
# 拉取镜像并启动（会自动等待 PG 就绪）
docker compose up -d foggy-mcp
```

```bash
# 确认启动成功
docker compose logs -f foggy-mcp
# 等待看到：Started McpLauncherApplication in X seconds
# Ctrl+C 退出日志
```

```bash
# 健康检查
curl http://localhost:7108/actuator/health
# 期望输出：{"status":"UP"}
```

> **注意**：如果健康检查失败，检查 Foggy MCP 日志中是否有数据库连接错误。

## 4. 初始化闭包表

闭包表用于层级查询（公司树、部门树等），需要在 PostgreSQL 中执行一次。

### 方式 A：通过 Odoo Setup Wizard（推荐）

1. 进入 **Settings → Foggy MCP**
2. 点击 **Launch Setup Wizard**
3. 在 **Closure Tables** 步骤点击 **Initialize Closure Tables**
4. 看到成功提示后继续

### 方式 B：手动执行 SQL

```bash
# 获取 SQL 文件路径（在宿主机上）
docker compose exec postgres psql -U odoo -d odoo \
  -f /dev/stdin < ../foggy_mcp/setup/sql/refresh_closure_tables.sql
```

或者进入 PostgreSQL 容器手动执行：

```bash
docker compose exec postgres psql -U odoo -d odoo
```

```sql
-- 粘贴 sql/refresh_closure_tables.sql 的全部内容，然后执行：
SELECT refresh_company_closure();
SELECT refresh_department_closure();
SELECT refresh_employee_closure();
SELECT refresh_partner_closure();
```

验证闭包表：

```sql
SELECT count(*) FROM res_company_closure;    -- 应 > 0
SELECT count(*) FROM hr_department_closure;  -- 应 > 0
```

## 5. 创建 API Key

1. 进入 **Foggy MCP → API Keys**
2. 点击 **Create**
3. 选择用户（如 `Administrator`）
4. 点击 **Generate Key**
5. **复制并保存 API Key**（格式：`fmcp_xxxxxxxxxxxx`，仅显示一次）

## 6. 验证端到端查询

### 6.1 测试 Foggy MCP 直连（绕过 Odoo 权限）

```bash
curl -s http://localhost:7108/mcp/admin/rpc \
  -H "Content-Type: application/json" \
  -H "X-NS: odoo" \
  -d '{
    "jsonrpc": "2.0", "id": 1,
    "method": "tools/call",
    "params": {
      "name": "dataset.query_model",
      "arguments": {
        "model": "OdooSaleOrderQueryModel",
        "payload": {
          "columns": ["name", "partner$caption", "amountTotal"],
          "limit": 5
        }
      }
    }
  }' | python3 -m json.tool
```

> **期望**：返回 JSON，`result.content[0].text` 中包含销售订单数据。
> 如果 Odoo 是全新安装没有演示数据，可能返回空结果（这是正常的）。

### 6.2 测试 Odoo MCP Gateway（带权限过滤）

```bash
# 替换 YOUR_API_KEY 为步骤 5 中获得的 key
curl -s http://localhost:8069/foggy-mcp/rpc \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -d '{
    "jsonrpc": "2.0", "id": 1,
    "method": "tools/list",
    "params": {}
  }' | python3 -m json.tool
```

> **期望**：返回当前用户可用的工具列表（dataset.query_model 等）。

```bash
# 查询销售订单（通过 Odoo 权限桥）
curl -s http://localhost:8069/foggy-mcp/rpc \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -d '{
    "jsonrpc": "2.0", "id": 1,
    "method": "tools/call",
    "params": {
      "name": "dataset.query_model",
      "arguments": {
        "model": "OdooSaleOrderQueryModel",
        "payload": {
          "columns": ["name", "partner$caption", "amountTotal"],
          "limit": 5
        }
      }
    }
  }' | python3 -m json.tool
```

### 6.3 测试健康诊断端点

```bash
curl -s http://localhost:8069/foggy-mcp/health | python3 -m json.tool
```

> **期望**：返回 Foggy MCP Server 连通状态、已加载工具数等。

## 7. （可选）接入 AI 客户端

### Claude Desktop / Cursor

在 AI 客户端的 MCP 配置中添加：

```json
{
  "mcpServers": {
    "foggy-odoo": {
      "url": "http://localhost:8069/foggy-mcp/rpc",
      "headers": {
        "Authorization": "Bearer YOUR_API_KEY"
      }
    }
  }
}
```

然后你可以直接用自然语言提问，如：

- "显示最近 10 笔销售订单"
- "按客户统计销售总额"
- "本月有多少张采购单"

---

## 创建演示数据（全新 Odoo 无数据时）

如果 Odoo 是全新安装没有业务数据，可以安装 Odoo 演示数据：

```bash
# 停止 Odoo
docker compose stop odoo

# 删除数据库并重装（带演示数据）
docker compose exec postgres psql -U odoo -c "DROP DATABASE odoo;"
docker compose exec postgres psql -U odoo -c "CREATE DATABASE odoo OWNER odoo;"

# 重新安装模块（加 --without-demo=False 以加载演示数据）
docker compose run --rm odoo odoo \
  -i sale,purchase,account,stock,hr,crm,foggy_mcp \
  -d odoo --stop-after-init --without-demo=False

# 重启
docker compose up -d odoo
```

> 安装演示数据后需要重新初始化闭包表（步骤 4）和重新创建 API Key（步骤 5）。

---

## 故障排查

### Foggy MCP Server 启动失败

```bash
docker compose logs foggy-mcp | tail -50
```

常见原因：
- 数据库连接失败 → 检查 PG 是否 healthy
- 端口被占用 → `netstat -tlnp | grep 7108`

### Odoo 模块安装失败

```bash
docker compose logs odoo | tail -50
```

常见原因：
- 依赖模块未安装 → 确保 `-i` 包含所有依赖
- `litellm` 缺失 → 不影响核心功能，可忽略

### API 查询返回空或报错

1. 确认 Foggy 健康：`curl http://localhost:7108/actuator/health`
2. 确认 Odoo 健康：`curl http://localhost:8069/foggy-mcp/health`
3. 确认 API Key 有效：检查是否以 `fmcp_` 开头
4. 确认 foggy-models 挂载：`docker compose exec foggy-mcp ls /foggy-models/`

### 闭包表未初始化

```bash
docker compose exec postgres psql -U odoo -d odoo \
  -c "SELECT tablename FROM pg_tables WHERE tablename LIKE '%closure%';"
```

如果没有结果，需要执行步骤 4。

---

## 文件结构速查

```
addons/foggy-odoo-bridge/
├── docker/
│   ├── docker-compose.yml    ← 主编排文件（PG + Odoo + Foggy MCP）
│   └── odoo.conf             ← Odoo 配置
├── foggy_mcp/                ← Odoo 模块（安装到 Odoo）
│   ├── __manifest__.py
│   ├── controllers/          ← MCP 端点
│   ├── models/               ← 配置 & API Key 模型
│   ├── services/             ← 权限桥、Foggy 客户端
│   ├── wizard/               ← Setup Wizard
│   ├── setup/                ← 内嵌资源（SQL、模型文件）
│   └── views/                ← Odoo 界面
└── foggy-models/             ← 语义模型（TM/QM，挂载到 Foggy MCP）
    ├── model/*.tm
    └── query/*.qm
```
