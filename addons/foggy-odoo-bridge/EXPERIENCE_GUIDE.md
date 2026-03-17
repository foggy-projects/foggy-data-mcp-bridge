# Foggy Odoo Bridge — 真实体验手册

> 本手册帮你亲手走一遍完整链路：Odoo UI → 配置 LLM → 内置 AI Chat 对话 → 外部 AI 客户端接入。

## 前置条件

```
✅ Docker 环境已启动（3 个容器）
✅ Odoo 17 运行中：  http://localhost:8069
✅ Foggy MCP 运行中：http://localhost:7108
✅ 闭包表已初始化：  SELECT refresh_all_closures();
✅ litellm 已安装：   docker compose exec odoo pip install litellm
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
| Server URL | `http://foggy-mcp:8080` | Docker 内部通信地址 |
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
- `checks.tool_cache.tool_count: 6` — 工具已缓存
- `checks.models.mapped_count: 8` — 8 个模型映射（含 CRM）

---

## 第二步：Odoo 内置 AI Chat 体验

> 无需配置外部 AI 客户端，直接在 Odoo 内与你的业务数据对话。

### 2.1 配置 LLM

1. 进入 **Settings**（需 admin 权限）
2. 向下滚动到 **Foggy MCP → AI Chat (LLM Configuration)** 区域
3. 填写配置：

| 配置项 | 说明 | 示例值 |
|---|---|---|
| LLM Provider | AI 模型提供商 | `OpenAI` / `Anthropic (Claude)` / `DeepSeek` / `Ollama (Local)` |
| API Key | 提供商的 API 密钥（Ollama 不需要） | `sk-xxx...` |
| Model Name | 模型标识符 | `gpt-4o-mini` / `claude-3-5-sonnet-20241022` / `deepseek-chat` |
| API Base URL | 自定义端点（Ollama 必填） | `http://localhost:11434/v1` |
| Temperature | 随机性控制（0.0=精确, 1.0=创意） | `0.3`（推荐） |

**各提供商快速配置**：

**OpenAI**（推荐入门）：
- Provider: `OpenAI`
- API Key: 你的 OpenAI key
- Model: `gpt-4o-mini`（性价比高）或 `gpt-4o`（更强）

**DeepSeek**（性价比之选）：
- Provider: `DeepSeek`
- API Key: 你的 DeepSeek key
- Model: `deepseek-chat`

**Ollama**（完全本地，无需 API Key）：
- Provider: `Ollama (Local)`
- Model: `llama3` 或 `qwen2.5`
- API Base URL: `http://host.docker.internal:11434/v1`（Docker 环境用 `host.docker.internal`）

4. 点击 **Save** 保存设置

### 2.2 进入 AI Chat

1. 点击顶部菜单 **Foggy MCP → AI Chat**
2. 看到欢迎界面，包含 3 个建议问题按钮
3. 直接输入问题或点击建议按钮开始对话

### 2.3 对话示例

**基础查询**：
```
最近5笔销售订单是什么？显示订单号、客户、金额
```
→ AI 自动调用 Foggy MCP 工具查询 sale_order，返回结构化数据表格。

**聚合分析**：
```
哪个客户的销售总额最高？按金额排列前5
```
→ 按 partner 分组 SUM，排序返回。

**CRM 漏斗**：
```
当前各阶段分别有多少商机？各阶段的预期收入是多少？
```
→ 按 stage 分组统计 crm_lead 数据。

**多轮对话**：
```
用户: 列出所有在职员工
AI:  （返回员工列表）
用户: 其中研发部门有多少人？
AI:  （自动使用 selfAndDescendantsOf 查询层级数据）
```

### 2.4 AI Chat 工作原理

```
用户提问 → LLM 分析意图 → 调用 Foggy MCP 工具 → 获取数据 → LLM 整理回答
                ↑                    ↓
           工具调用循环（最多 5 轮，自动停止）
```

- AI 会根据你的问题自动选择合适的模型和查询参数
- 所有查询经过 Odoo 权限桥接，不同用户看到不同数据
- 工具调用过程对用户透明，你直接看到最终答案
- 对话历史按会话保存，可通过左侧边栏切换/管理会话

### 2.5 非管理员用户体验

非管理员用户（如 demo 用户）：
1. 无法看到 Settings 菜单，但可以通过 **Foggy MCP → AI Chat** 直接对话
2. 通过 **Foggy MCP → My API Key** 管理自己的 API Key
3. AI Chat 中看到的数据受 ir.rule 权限控制，仅能查看自己有权限的数据

---

## 第三步：外部 AI 客户端配置

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
      "url": "http://localhost:7108/mcp/analyst/stream",
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

## 第四步：对话体验（外部 AI 客户端）

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

## 第五步：验证权限桥接（通过 Gateway）

> 以下仅通过 Odoo Gateway（端口 8069）测试时有效。直连 Foggy（7108）没有权限过滤。

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

## 第六步：直接 API 测试（可选）

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
curl -s http://localhost:7108/mcp/admin/rpc \
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
| crm.lead | ~10+ | 商机/线索（CRM demo data） |
| res.company | 2 | 均无父公司 |

---

## 体验完成检查清单

**Odoo 后台**：
- [ ] 能看到 Foggy MCP 配置页（Settings → Foggy MCP）
- [ ] 能看到 AI Chat LLM 配置区域
- [ ] Admin 能看到 API Key 管理页
- [ ] 非 Admin 用户能看到 Foggy MCP → My API Key
- [ ] Health 端点返回全绿（8 个模型、6 个工具）

**AI Chat（内置对话）**：
- [ ] LLM 配置保存成功（Provider + API Key + Model）
- [ ] AI Chat 页面正常打开（Foggy MCP → AI Chat）
- [ ] 基础查询：能通过自然语言查到销售数据
- [ ] CRM 查询：能查询商机/漏斗数据
- [ ] 聚合分析：能按维度分组统计
- [ ] 多轮对话：上下文连续
- [ ] 会话管理：可新建/切换/删除会话

**外部 AI 客户端**：
- [ ] AI 客户端能列出工具（tools/list）
- [ ] 基础查询能返回数据
- [ ] 维度名称正确显示（非 JSON/非 ID）
- [ ] 聚合分组正确
- [ ] 层级查询正确（闭包表 selfAndDescendantsOf）
- [ ] DISTINCT 返回去重结果
- [ ] withSubtotals 返回小计行
