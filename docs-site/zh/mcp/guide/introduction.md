# 简介

Foggy MCP 服务实现 [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) 协议，为 AI 助手提供数据查询能力。

## 核心特性

### DSL 查询语法：安全、可控、易理解

**这是 Foggy MCP 最重要的设计理念。**

#### 为什么不让 AI 直接生成 SQL？

传统方案让 AI 直接生成 SQL 存在两大问题：

**问题一：安全风险**

```sql
-- AI 生成的 SQL 可能造成：
DROP TABLE users;                    -- 数据删除
SELECT * FROM secrets;               -- 越权访问
SELECT * FROM orders; DROP TABLE--   -- SQL 注入
```

**问题二：难以控制和理解**

```sql
-- AI 生成的复杂 SQL 难以二次处理
SELECT c.name, SUM(o.amount) as total
FROM customers c
LEFT JOIN orders o ON c.id = o.customer_id
LEFT JOIN products p ON o.product_id = p.id
WHERE c.region_id IN (SELECT id FROM regions WHERE parent_id = 1)
  AND o.created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
GROUP BY c.id, c.name
HAVING total > 1000
ORDER BY total DESC;
```

- SQL 结构复杂，程序员难以在运行时注入额外的权限控制
- 复杂的 JOIN 和子查询让二次优化变得困难
- 不同数据库的 SQL 方言差异导致兼容性问题

#### DSL 查询语法的解决方案

Foggy MCP 采用 **JSON 结构化的 DSL 查询语法**：

```json
{
  "model": "FactSalesQueryModel",
  "payload": {
    "columns": ["customer$caption", "sum(totalAmount) as total"],
    "slice": [{"field": "salesDate$id", "op": ">=", "value": "20250101"}],
    "orderBy": [{"field": "total", "dir": "DESC"}],
    "limit": 10
  }
}
```

**优势一：从根本上保障安全**

| 风险类型 | DSL 防护机制 |
|----------|--------------|
| SQL 注入 | DSL 参数化处理，AI 永远不接触原始 SQL |
| 越权访问 | 只能查询已定义的模型和字段 |
| 数据篡改 | DSL 仅支持查询操作，无法执行 DDL/DML |
| 全表扫描 | 可配置强制过滤条件和行数限制 |

**优势二：程序员可完全掌控**

JSON 结构化的 DSL 让程序员可以在查询执行前进行二次处理：

```
AI 生成 DSL  →  程序拦截  →  注入权限/条件  →  执行查询
                   ↓
              - 自动添加数据权限过滤
              - 强制限制返回行数
              - 记录审计日志
              - 参数校验和清洗
```

**优势三：TM/QM 中间层预处理**

在 TM（表模型）和 QM（查询模型）层，程序员可以：

- **封装复杂关联** - 多表 JOIN 关系在 TM/QM 中预定义，AI 只需指定模型名
- **预处理特殊字段** - JSON 字段、加密字段在 TM 中转换为简单字段
- **语义化命名** - `customer$caption` 比 `c.name` 更易理解
- **控制可见范围** - QM 中定义哪些字段对 AI 可见

```
原始表结构 (复杂)          TM/QM 层 (简化)           AI 看到的
─────────────────         ─────────────────        ─────────────
orders.customer_id   →    customer$caption    →   "客户名称"
orders.data->>'addr' →    deliveryAddress     →   "收货地址"
多表JOIN关系          →    单一模型名称         →   "FactSalesQueryModel"
```

**优势四：跨数据库兼容**

同一份 DSL 自动适配不同数据库：

| 数据库 | 自动处理 |
|--------|----------|
| MySQL | `LIMIT 10` |
| SQL Server | `TOP 10` / `OFFSET FETCH` |
| PostgreSQL | `LIMIT 10` |
| SQLite | `LIMIT 10` |

### 多角色端点

根据用户角色提供不同的mcp工具集和权限：

| 角色 | 端点 | 工具范围 | 适用场景 |
|------|------|----------|----------|
| 管理员 | `/mcp/admin/rpc` | 全部工具 | 系统管理、调试 |
| 分析师 | `/mcp/analyst/rpc` | 元数据 + DSL 查询 | 数据分析、报表开发 |
| 业务用户 | `/mcp/business/rpc` | 仅自然语言查询 | 日常业务查询 |

### 丰富的查询工具

- **元数据查询** - 获取可用的数据模型和字段定义
- **DSL 结构化查询** - 安全、精确、可控的数据查询
- **自然语言查询** - AI 驱动的智能查询（内部转换为 DSL）
- **图表生成** - 自动生成趋势图、对比图、饼图

### 多客户端支持

- 任何支持mcp的客户端
- Claude Desktop - Anthropic 官方桌面客户端
- Cursor IDE - AI 编程助手
- 自定义 AI Agent - 通过 SDK 集成
- REST API - 直接调用 JSON-RPC 接口

## 快速体验

```bash
# 克隆项目
git clone https://github.com/nicecho/foggy-data-mcp-bridge.git
cd foggy-data-mcp-bridge/docker/demo

# 配置 API Key
cp .env.example .env
# 编辑 .env 设置 OPENAI_API_KEY (可选)

# 启动服务
docker-compose up -d

# 验证
curl http://localhost:7108/actuator/health
```

## 文档导航

### 入门指南

- [快速开始](./quick-start.md) - 5 分钟启动服务
- [架构概述](./architecture.md) - 了解系统架构

### 工具文档

- [工具概述](../tools/overview.md) - 所有可用工具
- [元数据工具](../tools/metadata.md) - 获取模型信息
- [查询工具](../tools/query.md) - 执行数据查询
- [自然语言查询](../tools/nl-query.md) - 智能数据查询

### 集成指南

- [Claude Desktop](../integration/claude-desktop.md) - 配置 Claude Desktop
- [Cursor](../integration/cursor.md) - 配置 Cursor IDE
- [API 调用](../integration/api.md) - 直接调用 API
