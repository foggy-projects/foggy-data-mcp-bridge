# Foggy Odoo Bridge - 功能架构文档

## 1. 概述

**Foggy Odoo Bridge** 是一个 Odoo 插件，为 Odoo ERP 提供 AI 驱动的自然语言数据查询能力。通过 MCP (Model Context Protocol) 协议，让 Claude Desktop、Cursor 等 AI 客户端能够安全地查询 Odoo 业务数据。

### 核心价值

- **自然语言查询**: "本月销售额最高的客户是谁？" → 结构化数据
- **行级安全**: Odoo `ir.rule` 自动转换为查询过滤条件，AI 无法绕过
- **零配置权限**: 复用 Odoo 现有权限体系，无需额外配置

---

## 2. 系统架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              AI Client                                       │
│                   (Claude Desktop / Cursor / 自定义客户端)                    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ MCP JSON-RPC (Bearer Token)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Odoo MCP Gateway                                     │
│                        (foggy_mcp Python 插件)                               │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  McpController (/foggy-mcp/rpc)                                     │    │
│  │  ├── 认证: API Key / Session                                         │    │
│  │  ├── tools/list → ToolRegistry (ir.model.access 过滤)               │    │
│  │  └── tools/call → PermissionBridge + FoggyClient                    │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  PermissionBridge                                                    │    │
│  │  ├── 读取 ir.rule → domain_force                                     │    │
│  │  ├── 解析波兰表达式 AST                                              │    │
│  │  └── 转换为 DSL slice 条件 → 注入 payload.slice                      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ HTTP + payload.slice (权限条件已注入)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Foggy MCP Server                                     │
│                        (Java, TM/QM 引擎)                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  DSL Engine                                                          │    │
│  │  ├── 解析 payload.slice 条件                                         │    │
│  │  ├── 构建 SQL 查询                                                   │    │
│  │  └── 执行闭包表层级查询                                              │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ SQL (只读)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          PostgreSQL                                         │
│                         (Odoo 数据库)                                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. 核心模块

### 3.1 MCP Controller (`mcp_controller.py`)

**职责**: MCP JSON-RPC 2.0 端点，处理 AI 客户端请求

| 方法 | 功能 |
|------|------|
| `initialize` | 返回服务器能力 |
| `tools/list` | 返回可用工具列表（按用户权限过滤） |
| `tools/call` | 执行查询（注入权限条件后转发） |
| `ping` | 健康检查 |

**认证方式**:
- **API Key**: `Authorization: Bearer fmcp_xxx`
- **Session**: Odoo Cookie 会话

**关键代码路径**:
```
tools/call 请求
    → _authenticate() → 获取 user
    → _handle_tools_call()
        → 检查 ir.model.access (模型级权限)
        → compute_permission_slices() → 计算 ir.rule → DSL slice
        → payload.slice.extend(perm_slices) → 注入权限条件
        → FoggyClient.call_tools_call() → 转发到 Foggy
```

### 3.2 Permission Bridge (`permission_bridge.py`)

**职责**: 将 Odoo `ir.rule` 域表达式转换为 Foggy DSL slice 条件

**转换流程**:
```
ir.rule.domain_force (波兰表达式)
    ↓ safe_eval (解析 user.id, company_ids 等变量)
    ↓ _expand_hierarchy_operators (child_of → selfAndDescendantsOf)
    ↓ _parse_domain_ast (构建 AST)
    ↓ _flatten_to_dsl_slices (展平为 DSL 格式)
    ↓ payload.slice 条件
```

**支持的域操作符**:

| Odoo 操作符 | DSL 操作符 | 说明 |
|-------------|------------|------|
| `=` | `=` | 相等 |
| `!=` | `!=` | 不等 |
| `in` | `in` | 包含于 |
| `not in` | `not in` | 不包含于 |
| `like/ilike` | `like` | 模糊匹配 |
| `child_of` | `selfAndDescendantsOf` | 层级查询（闭包表） |
| `parent_of` | `selfAndAncestorsOf` | 层级查询（闭包表） |

**逻辑操作符处理**:
- `['&', A, B]` → `[A, B]` (AND 是顶层默认)
- `['|', A, B]` → `{"$or": [A, B]}`
- `['!', A]` → 应用德摩根定律取反

**失败关闭 (Fail-Closed)**:
- 权限计算失败 → 拒绝访问（注入不可能条件）
- 确保安全，不会意外放行

### 3.3 Tool Registry (`tool_registry.py`)

**职责**: 从 Foggy 加载工具列表，按用户权限过滤

**过滤逻辑**:
```
1. 调用 Foggy tools/list 获取全部工具
2. 查询用户的 ir.model.access 权限
3. 过滤: 用户有读权限的模型对应的工具才保留
4. 增强: 在工具描述中注入可用模型列表
```

**模型映射** (`MODEL_MAPPING`):
| Odoo 模型 | QM 模型 |
|-----------|---------|
| `sale.order` | OdooSaleOrderQueryModel |
| `sale.order.line` | OdooSaleOrderLineQueryModel |
| `purchase.order` | OdooPurchaseOrderQueryModel |
| `account.move` | OdooAccountMoveQueryModel |
| `stock.picking` | OdooStockPickingQueryModel |
| `hr.employee` | OdooHrEmployeeQueryModel |
| `res.partner` | OdooResPartnerQueryModel |
| `res.company` | OdooResCompanyQueryModel |
| `crm.lead` | OdooCrmLeadQueryModel |

### 3.4 Field Mapping Registry (`field_mapping_registry.py`)

**职责**: 动态加载 Foggy 元数据，构建字段映射

**两阶段加载**:
1. **Discovery**: 调用 `dataset.get_metadata` 发现所有 QM 模型
2. **Detail**: 调用 `dataset.describe_model_internal` 获取字段映射

**用途**:
- 动态解析 `ir.rule` 字段名到 QM 字段名
- 支持自定义模型扩展，无需硬编码映射

### 3.5 Foggy Client (`foggy_client.py`)

**职责**: HTTP 客户端，与 Foggy MCP Server 通信

**关键特性**:
- 支持 `X-NS` header 传递命名空间
- 支持 Bearer Token 认证
- 超时控制、错误处理

### 3.6 API Key Model (`foggy_api_key.py`)

**职责**: API Key 管理，用于 AI 客户端认证

**功能**:
- 生成 `fmcp_` 前缀的安全随机 Key
- 关联到 Odoo 用户，继承用户权限
- 支持按公司限制
- 自动生成 MCP 客户端配置 JSON

### 3.7 Setup Wizard (`foggy_setup_wizard.py`)

**职责**: 安装向导，简化部署流程

**步骤**:
1. **Welcome** - 欢迎页
2. **Deploy** - 生成 Docker 启动命令（自动检测网络环境）
3. **Connection** - 测试连接
4. **DataSource** - 配置数据库连接（自动填充 Odoo DB 信息）
5. **Closure Tables** - 初始化闭包表
6. **Done** - 完成，跳转创建 API Key

**智能检测**:
- 自动检测是否在 Docker 中运行
- 自动检测 Docker 网络配置
- 生成适配当前环境的 Docker 命令

### 3.8 LLM Service (`llm_service.py`)

**职责**: 内置 AI 聊天功能，直接在 Odoo 中与数据对话

**支持的 LLM 提供商** (via litellm):
- OpenAI (gpt-4o, gpt-4o-mini)
- Anthropic (claude-3.5-sonnet, claude-3-haiku)
- DeepSeek
- Ollama (本地部署)
- Azure / Groq / Together 等

**流程**:
```
用户消息 → 构建系统提示（可用模型列表）
         → 调用 LLM (litellm)
         → LLM 返回 tool_use → 执行 Foggy 工具（含权限注入）
         → 工具结果返回 LLM
         → 最终回复
```

---

## 4. 安全模型

### 4.1 认证层

| 方式 | 场景 | 权限范围 |
|------|------|----------|
| API Key | AI 客户端 (Claude Desktop) | 绑定的 Odoo 用户权限 |
| Session | Web 客户端 (Odoo 内置聊天) | 当前登录用户权限 |

### 4.2 授权层

```
┌─────────────────────────────────────────────────────────────┐
│                     请求流程                                 │
├─────────────────────────────────────────────────────────────┤
│  1. 认证 → 获取 uid                                         │
│  2. 模型级检查: ir.model.access.check(model, 'read')        │
│  3. 行级过滤: ir.rule → domain_force → DSL slice            │
│  4. 注入: payload.slice.extend(perm_slices)                 │
│  5. 转发: Foggy MCP Server 执行查询                         │
└─────────────────────────────────────────────────────────────┘
```

### 4.3 失败关闭原则

- **权限计算失败** → 拒绝访问
- **Foggy 服务不可用** → 返回错误
- **字段映射失败** → 使用静态映射兜底

---

## 5. 层级查询 (Closure Tables)

### 5.1 支持的层级维度

| Odoo 字段 | 闭包表 | 说明 |
|-----------|--------|------|
| `company_id` | `res_company_closure` | 公司层级 |
| `department_id` | `hr_department_closure` | 部门层级 |
| `parent_id` | `res_partner_closure` | 合作伙伴层级 |

### 5.2 操作符映射

| Odoo 操作符 | Foggy 操作符 | SQL 效果 |
|-------------|--------------|----------|
| `child_of` | `selfAndDescendantsOf` | `closure.parent_id = X` |
| `parent_of` | `selfAndAncestorsOf` | `closure.child_id = X` |

### 5.3 使用示例

**Odoo ir.rule**:
```python
[('company_id', 'child_of', user.company_id.id)]
```

**转换后的 DSL slice**:
```json
{"field": "company$id", "op": "selfAndDescendantsOf", "value": 1}
```

**生成的 SQL**:
```sql
SELECT ... FROM sale_order so
JOIN res_company_closure c ON c.child_id = so.company_id
WHERE c.parent_id = 1
```

---

## 6. 内置 AI 聊天

### 6.1 功能

- 直接在 Odoo Web 界面与数据对话
- 支持多轮对话、历史记录
- 自动权限注入

### 6.2 配置

| 参数 | 路径 | 说明 |
|------|------|------|
| LLM Provider | Settings → Foggy MCP → AI Chat | OpenAI / Anthropic / DeepSeek / Ollama |
| API Key | 同上 | LLM API Key |
| Model | 同上 | gpt-4o-mini, claude-3-haiku 等 |
| Base URL | 同上 | 自定义端点 (Ollama) |
| Temperature | 同上 | 生成随机性 |
| Custom Prompt | 同上 | 业务上下文提示 |

---

## 7. 配置参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `foggy_mcp.server_url` | `http://foggy-mcp:8080` | Foggy MCP Server URL |
| `foggy_mcp.endpoint_path` | `/mcp/analyst/rpc` | MCP 端点路径 |
| `foggy_mcp.request_timeout` | `30` | HTTP 超时 (秒) |
| `foggy_mcp.namespace` | `odoo` | 模型命名空间 |
| `foggy_mcp.cache_ttl` | `300` | 工具缓存 TTL (秒) |
| `foggy_mcp.auth_token` | - | Foggy Bearer Token |

---

## 8. 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/foggy-mcp/rpc` | POST | MCP JSON-RPC 主端点 |
| `/foggy-mcp/health` | GET | 健康检查、诊断信息 |

---

## 9. 扩展模型

### 9.1 方法一: 添加到 foggy-odoo-bridge-java 模块

在 `addons/foggy-odoo-bridge-java` 的 resources 中添加 TM/QM 文件，重建 Docker 镜像。

### 9.2 方法二: 外部 Bundle

```bash
java -jar foggy-mcp-launcher.jar \
  --spring.profiles.active=lite \
  --foggy.bundle.external.enabled=true \
  --foggy.bundle.external.bundles[0].name=custom-models \
  --foggy.bundle.external.bundles[0].path=/path/to/custom-models \
  --foggy.bundle.external.bundles[0].namespace=custom
```

### 9.3 更新模型映射

在 `tool_registry.py` 中添加:
```python
MODEL_MAPPING['my.custom.model'] = 'MyCustomQueryModel'
```

---

## 10. 依赖

### Odoo 模块
- `base`
- `sale`
- `purchase`
- `account`
- `stock`
- `hr`
- `crm`

### Python 包
- `litellm` - LLM 集成 (可选，用于内置聊天)
- `requests` - HTTP 客户端

---

## 11. 测试

```bash
# 单元测试 (无需 Odoo 运行时)
cd addons/foggy-odoo-bridge
python -m pytest tests/test_permission_bridge.py -v

# E2E 测试 (需 Foggy + Odoo 运行中)
python -m pytest tests/e2e/ -v
```

---

## 12. 数据隐私

- 数据仅在 Odoo ↔ Foggy MCP Server ↔ PostgreSQL 之间流转
- 不发送到任何第三方云服务
- 推荐生产环境使用 HTTPS

---

## 13. License

Apache License 2.0