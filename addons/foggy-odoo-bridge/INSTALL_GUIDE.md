# Foggy MCP Gateway — 安装指南

> 连接 Odoo 与 AI 的数据桥梁，让 AI 安全地查询 Odoo 业务数据。

## 前置条件

- Odoo 17 已安装并正常运行
- 数据库为 PostgreSQL（MySQL 即将支持）

---

## 安装步骤

### 1. 下载插件

```bash
curl -LO https://github.com/foggy-projects/foggy-data-mcp-bridge/releases/download/main/foggy-odoo-addon.zip
unzip foggy-odoo-addon.zip   # 解压为 foggy_mcp/
```

### 2. 安装到 Odoo

**方式 A：Docker 部署**

```yaml
# docker-compose.yml 添加卷挂载
volumes:
  - ./foggy_mcp:/mnt/extra-addons/foggy_mcp:ro
```

**方式 B：本地安装**

```bash
cp -r foggy_mcp /path/to/odoo/addons/
```

### 3. 启用模块

1. Odoo → **Settings → Technical → Apps**
2. 点击 **Update Apps List**
3. 搜索 `foggy_mcp` → 点击 **Install**

---

## Setup Wizard 配置向导

安装完成后，进入 **Settings → Foggy MCP → Setup Wizard**，跟随向导完成配置：

### Step 1: 选择部署方式

| 方式 | 说明 |
|------|------|
| **Docker** | 推荐，一键启动 Foggy MCP Server |
| **Manual JAR** | 手动运行 JAR 文件 |

### Step 2: 服务器配置

向导自动检测 Odoo 数据库连接信息，生成配置：

- **Docker 方式**：复制生成的 `docker-compose.yml` 保存并执行 `docker compose up -d`
- **Manual 方式**：复制生成的 `java -jar` 命令执行

> **模型文件**：已内嵌在插件中，无需额外下载

### Step 3: 初始化闭包表

点击 **Initialize Closure Tables**，用于层级查询（公司树、部门树等）。

### Step 4: 测试连接

点击 **Test Connection**，验证 Foggy MCP Server 是否正常运行。

### Step 5: 创建 API Key

点击 **Finish**，跳转到 API Key 创建页面。

---

## 创建 API Key

1. **Foggy MCP → API Keys**
2. 点击 **Create** → 选择用户 → **Generate Key**
3. 保存 API Key（格式：`fmcp_xxxxxxxxxxxx`，仅显示一次）

---

## 验证查询

```bash
# 替换 YOUR_API_KEY
curl -s http://localhost:8069/foggy-mcp/rpc \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -d '{
    "jsonrpc": "2.0", "id": 1,
    "method": "tools/list",
    "params": {}
  }' | python3 -m json.tool
```

---

## 接入 AI 客户端

### Claude Desktop / Cursor

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

自然语言查询示例：
- "显示最近 10 笔销售订单"
- "按客户统计销售总额"
- "本月有多少张采购单"

---

## 架构说明

```
AI Client
    │ MCP Protocol
    ▼
Odoo (foggy_mcp 插件)
    │ 权限过滤 (ir.rule → DSL slice)
    ▼
Foggy MCP Server (Java, 独立进程)
    │ SQL
    ▼
PostgreSQL (Odoo 数据库)
```

**安全机制**：
- 用户只能查询有权限的数据
- 权限条件在服务端注入，无法绕过
- Fail-closed：权限错误时拒绝访问

---

## 故障排查

### Foggy MCP Server 连接失败

1. 确认服务运行：`curl http://localhost:7108/actuator/health`
2. 检查数据库连接配置是否正确
3. 检查防火墙规则

### 闭包表初始化失败

确保 Odoo 数据库用户有创建表的权限。

### 查询返回空数据

1. 确认用户有对应模型的访问权限
2. 确认数据库中有业务数据