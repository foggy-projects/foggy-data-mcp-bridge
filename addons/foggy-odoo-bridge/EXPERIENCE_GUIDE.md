# Foggy Odoo Bridge — 真实体验手册

> 本手册帮你亲手走一遍完整链路：Odoo UI → 创建 API Key → 配置 AI 客户端 → 对话查数据。

## 前置条件

```
✅ Docker 环境已启动（3 个容器）
✅ Odoo 17 运行中：  http://localhost:8069
✅ Foggy MCP 运行中：http://localhost:8080
✅ 闭包表已初始化：  SELECT refresh_all_closures();
```

验证命令：
```bash
curl -s http://localhost:8069/foggy-mcp/health | python -m json.tool
# 确认 status=ok, foggy_server.status=ok, tool_count>=5
```

---

## 第一步：Odoo 管理后台体验

### 1.1 登录 Odoo

浏览器打开 http://localhost:8069/web

| 字段 | 值 |
|---|---|
| Database | `odoo` |
| Email | `admin` |
| Password | `admin` |

### 1.2 查看 Foggy MCP 配置

1. 点击左上角 **Settings**（设置）
2. 向下滚动到 **Foggy MCP** 区域
3. 确认配置：

| 配置项 | 值 | 说明 |
|---|---|---|
| Server URL | `http://host.docker.internal:8080` | Docker 内部通信地址 |
| Endpoint Path | `/mcp/analyst/rpc` | MCP 端点路径 |
| Request Timeout | `30` | 超时秒数 |
| Namespace | `odoo` | 模型命名空间 |
| Tool Cache TTL | `300` | 工具缓存秒数 |

### 1.3 管理 API Key

1. 顶部菜单 **Settings → Foggy MCP → API Keys**
2. 已有一个 `Admin Test Key`（admin 用户）
3. 点击进入查看详情：
   - 可以复制 Key（`fmcp_` 开头，48 字符）
   - 看到 Claude Desktop 配置说明
   - 可以点击 **Regenerate Key** 重新生成

**创建新 Key**（可选）：
1. 点击 **New**
2. 输入名称：如 `Cursor IDE - 我的电脑`
3. 选择用户（不同用户看到不同数据，受 ir.rule 约束）
4. 保存，复制生成的 Key

### 1.4 验证健康端点

浏览器直接访问 http://localhost:8069/foggy-mcp/health

应看到 JSON 响应，包含：
- `status: "ok"` — 网关正常
- `checks.foggy_server.status: "ok"` — Java 引擎连通
- `checks.tool_cache.tool_count: 5` — 工具已缓存
- `checks.models.mapped_count: 7` — 7 个模型映射

---

## 第二步：AI 客户端配置

### 2.1 Claude Desktop 配置

编辑 `claude_desktop_config.json`：

**方式 A — 通过 Odoo Gateway（含权限桥接，推荐生产环境）**：
```json
{
  "mcpServers": {
    "odoo": {
      "url": "http://localhost:8069/foggy-mcp/rpc",
      "headers": {
        "Authorization": "Bearer fmcp_5f53bc59f34d59417a93994f2516e6ac353304429e438361"
      }
    }
  }
}
```

**方式 B — 直连 Foggy MCP（跳过权限，适合开发调试）**：
```json
{
  "mcpServers": {
    "foggy-odoo": {
      "url": "http://localhost:8080/mcp/analyst/stream",
      "headers": { "X-NS": "odoo" }
    }
  }
}
```

### 2.2 Cursor / VS Code 配置

在 `.cursor/mcp.json` 或 VS Code MCP 配置中添加：
```json
{
  "mcpServers": {
    "odoo": {
      "url": "http://localhost:8069/foggy-mcp/rpc",
      "headers": {
        "Authorization": "Bearer <your-api-key>"
      }
    }
  }
}
```

### 2.3 Cherry Studio 配置

MCP Server 设置 → 添加新连接：
- 类型：`Streamable HTTP`
- URL：`http://localhost:8069/foggy-mcp/rpc`
- Headers：`Authorization: Bearer <your-api-key>`

---

## 第三步：对话体验

> 配置好 AI 客户端后，直接用自然语言提问。AI 会自动调用 MCP 工具查询 Odoo 数据。

### 基础查询

```
最近的 5 笔销售订单是什么？显示订单号、客户、金额
```

预期：AI 调用 `dataset.query_model`，返回 sale_order 数据，客户名称自动解析。

```
列出所有在职员工，包含部门和职位
```

预期：查询 hr_employee，department 和 job 维度名称正确显示（JSONB 翻译已处理）。

### 聚合分析

```
哪个客户的销售总额最高？按金额从高到低排列
```

预期：按 partner 分组，SUM(amountTotal)，排序后返回。

```
各部门分别有多少员工？
```

预期：按 department 分组 COUNT。

### 层级穿透（闭包表）

```
Research & Development 部门及其所有子部门共有多少员工？
```

预期：使用 selfAndDescendantsOf 操作符，走闭包表查询。

```
总公司及其所有子公司的销售订单汇总
```

### 去重 + 小计

```
销售订单有哪些不同的状态？
```

预期：DISTINCT 查询，返回 draft, sent, sale 等。

```
按状态汇总销售额，并显示总计行
```

预期：withSubtotals=true，结果中包含 `_rowType: grandTotal` 行。

### 过滤查询

```
已确认的销售订单总金额是多少？
```

预期：slice 过滤 state = 'sale'，SUM amountTotal。

```
除了 Gemini Furniture 以外的客户总共贡献了多少销售额？
```

---

## 第四步：验证权限桥接（通过 Gateway）

> 以下仅通过 Odoo Gateway（端口 8069）测试时有效。直连 Foggy（8080）没有权限过滤。

### 4.1 理解权限模型

```
Admin 用户（uid=2）
  ├─ ir.model.access: 可读全部 7 个已映射模型
  ├─ ir.rule (global): company_id in user.company_ids
  └─ ir.rule (group): 按用户组过滤
```

Gateway 会自动将 ir.rule 解析为 DSL slice 条件注入查询，AI 客户端**无法绕过**。

### 4.2 验证工具过滤

用 admin API key 查询 tools/list，应看到 5 个工具（含 dataset.query_model）。

如果创建一个**无 sale.order 读权限**的用户，该用户的 tools/list 会相应减少。

### 4.3 验证行级过滤

admin 用户能看到所有公司数据。如果限制某用户只能看 company_id=1，则：
- 该用户的查询自动注入 `slice: [{field: "company$id", op: "=", value: 1}]`
- AI 看到的结果已过滤，无需额外处理

---

## 第五步：直接 API 测试（可选）

如果不想配置 AI 客户端，也可以直接 curl 体验：

### 通过 Odoo Gateway（需 API Key）

```bash
API_KEY="fmcp_5f53bc59f34d59417a93994f2516e6ac353304429e438361"

# 查询销售订单
curl -s http://localhost:8069/foggy-mcp/rpc \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $API_KEY" \
  -d '{
    "jsonrpc": "2.0", "id": 1,
    "method": "tools/call",
    "params": {
      "name": "dataset.query_model",
      "arguments": {
        "model": "OdooSaleOrderQueryModel",
        "payload": {
          "columns": ["name", "partner$caption", "amountTotal", "state"],
          "limit": 5
        }
      }
    }
  }' | python -m json.tool
```

### 直连 Foggy MCP（无需 Key，需 X-NS header）

```bash
# 员工列表
curl -s http://localhost:8080/mcp/admin/rpc \
  -H "Content-Type: application/json" \
  -H "X-NS: odoo" \
  -d '{
    "jsonrpc": "2.0", "id": 1,
    "method": "tools/call",
    "params": {
      "name": "dataset.query_model",
      "arguments": {
        "model": "OdooHrEmployeeQueryModel",
        "payload": {
          "columns": ["name", "department$caption", "job$caption", "workLocation$caption"],
          "limit": 10
        }
      }
    }
  }' | python -m json.tool
```

---

## 已知 Demo 数据量

| 模型 | 记录数 | 说明 |
|---|---|---|
| sale.order | 24 | 4 草稿、1 已发送、19 已确认 |
| sale.order.line | ~50+ | 多行明细 |
| purchase.order | 11 | |
| account.move | 24 | 含发票和账单 |
| stock.picking | 25 | |
| hr.employee | 20 | |
| res.partner | 61 | 含客户、供应商、联系人 |
| res.company | 2 | 均无父公司 |

---

## 体验完成检查清单

- [ ] Odoo 后台能看到 Foggy MCP 配置页
- [ ] 能看到 API Key 管理页
- [ ] Health 端点返回全绿
- [ ] AI 客户端能列出工具（tools/list）
- [ ] 基础查询能返回数据
- [ ] 维度名称正确显示（非 JSON/非 ID）
- [ ] 聚合分组正确
- [ ] 层级查询正确
- [ ] DISTINCT 返回去重结果
- [ ] withSubtotals 返回小计行
