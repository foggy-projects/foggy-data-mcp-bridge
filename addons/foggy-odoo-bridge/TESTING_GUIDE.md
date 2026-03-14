# Odoo MCP Bridge — 手动体验指南

## 架构与端口

```
AI Client ──MCP──→ Odoo MCP Gateway (:8069) ──HTTP──→ Foggy MCP Server (:8080) ──SQL──→ PostgreSQL (:5432)
```

| 服务 | 端口 | 说明 |
|---|---|---|
| **Odoo 17** | `8069` | Odoo Web + MCP Gateway（含权限桥接） |
| **Foggy MCP Java** | `8080` | 纯查询引擎（内部服务，本次体验直连此端口） |
| **PostgreSQL 15** | `5432` | Odoo 数据库 |

### 本次体验：直连 Foggy MCP Java（端口 8080）

跳过 Odoo Gateway，直接验证模型和查询能力。

| 项目 | 值 |
|---|---|
| MCP Admin 端点 | `http://localhost:8080/mcp/admin/rpc` |
| MCP Analyst 端点 | `http://localhost:8080/mcp/analyst/rpc` |
| MCP SSE 端点 | `http://localhost:8080/mcp/analyst/stream` |
| Health Check | `http://localhost:8080/actuator/health` |
| 必填 Header | `X-NS: odoo` |

**AI 客户端配置**（Claude Desktop / Cursor / Cherry Studio 等）：
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

### 后续：通过 Odoo Gateway 测试（端口 8069）

验证权限桥接（ir.model.access + ir.rule → payload.slice 注入）。需 Odoo 安装 foggy_mcp 模块。

---

## 已注册模型 (8 个)

| # | 模型名 | 业务含义 | 基表 |
|---|---|---|---|
| 1 | OdooSaleOrderQueryModel | 销售订单分析 | sale_order |
| 2 | OdooSaleOrderLineQueryModel | 销售明细分析 | sale_order_line |
| 3 | OdooPurchaseOrderQueryModel | 采购订单分析 | purchase_order |
| 4 | OdooAccountMoveQueryModel | 发票/账单分析 | account_move |
| 5 | OdooStockPickingQueryModel | 库存调拨分析 | stock_picking |
| 6 | OdooHrEmployeeQueryModel | 员工花名册 | hr_employee |
| 7 | OdooResPartnerQueryModel | 客户/供应商目录 | res_partner |
| 8 | OdooResCompanyQueryModel | 公司组织架构 | res_company |

---

## 体验问题清单

### 一、基础查询（验证各模型可用）

1. 查询最近 5 笔销售订单，显示订单号、客户名称、金额
2. 查询所有采购订单，按状态分组统计数量和总金额
3. 查询所有活跃员工，显示姓名、部门、职位、工作地点
4. 查询客户列表（customer_rank > 0），显示名称、邮箱、国家、城市
5. 查询所有库存调拨，显示单号、操作类型、源/目标位置、状态

### 二、维度 Caption 查询（验证 JSONB 翻译字段正确提取）

以下维度的 `$caption` 应返回可读文本而非 JSON：

6. 查询员工的职位名称（job$caption），应显示如 "Experienced Developer"
7. 查询销售订单的销售团队（salesTeam$caption），应显示如 "Sales"
8. 查询客户的国家名称（country$caption），应显示如 "United States"
9. 查询采购订单的收货类型（pickingType$caption），应显示如 "Receipts"
10. 查询发票的日记账名称（journal$caption），应显示如 "Bank"
11. 查询库存调拨的操作类型（pickingType$caption），应显示如 "Delivery Orders"
12. 查询销售明细的计量单位（uom$caption），应显示如 "Units"

### 三、聚合分析（验证度量与分组）

13. 按客户分组统计销售订单数量和总金额，按金额降序排列
14. 按月份统计销售金额趋势（dateOrder 按月分组）
15. 按部门统计员工人数
16. 按国家统计合作伙伴数量
17. 按操作类型统计库存调拨数量
18. 按日记账类型统计发票金额

### 四、闭包表层级查询（验证树形结构穿透）

19. 查询"Research & Development"部门及其所有子部门的员工（department$id selfAndDescendantsOf）
20. 查询公司 ID=1 及其所有子公司的销售订单（company$id selfAndDescendantsOf）
21. 按部门分组统计员工数，同时限定部门层级在"Management"下

### 五、自引用维度（验证引擎修复）

22. 查询公司列表，同时显示母公司名称（parent$caption）
23. 查询合作伙伴，显示其上级公司名称（parentPartner$caption）

### 六、过滤与切片

24. 查询已确认的销售订单（state = 'sale'），显示金额
25. 查询已付款的发票（payment_state = 'paid'），按客户统计
26. 查询供应商列表（supplier_rank > 0），按国家分组
27. 查询 2024 年的采购订单（dateOrder 时间范围过滤）

### 七、组合查询（综合能力）

28. 查询"Sales"团队在过去一年的销售订单金额趋势（salesTeam + 时间范围 + 按月分组）
29. 查询"Research & Development"部门所有子部门的员工，按职位分组统计人数
30. 查询已完成的库存调拨，按操作类型和月份交叉分析数量

---

## 快速验证命令

```bash
# 自动化验证（25 项测试）
cd addons/foggy-odoo-bridge
python tests/e2e/verify_all_models.py http://localhost:8080 odoo

# Schema 验证（TM vs 数据库列类型）
python tests/e2e/verify_schema.py --docker foggy-odoo-postgres

# 手动 curl 测试（示例：查询销售订单）
curl -s -X POST http://localhost:8080/mcp/admin/rpc \
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
  }' | python -m json.tool
```

---

## 关键技术要点

| 特性 | 说明 |
|---|---|
| JSONB 翻译字段 | 7 个维度表的 name 列为 JSONB，通过 `jsonbCaption()` 自动 `->> 'en_US'` 提取 |
| 闭包表层级 | 4 张闭包表：res_company_closure、hr_department_closure、hr_employee_closure、res_partner_closure |
| 自引用维度 | res_company.parent → res_company, hr_employee.parent → hr_employee, res_partner.parent → res_partner |
| Namespace 隔离 | 通过 `X-NS: odoo` header 路由到 Odoo 模型 bundle |
| Lite 模式 | 无 MongoDB 依赖，仅 JDBC + MCP 核心能力 |
