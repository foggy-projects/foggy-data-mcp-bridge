# Odoo MCP Bridge — 手动体验指南

## 架构与端口

```
AI Client ──MCP──→ Odoo MCP Gateway (:8069) ──HTTP──→ Foggy MCP Server (:7108) ──SQL──→ PostgreSQL (:5432)
```

| 服务 | 端口 | 说明 |
|---|---|---|
| **Odoo 17** | `8069` | Odoo Web + MCP Gateway（含权限桥接） |
| **Foggy MCP Java** | `7108` | 纯查询引擎（内部服务，本次体验直连此端口） |
| **PostgreSQL 15** | `5432` | Odoo 数据库 |

### 本次体验：直连 Foggy MCP Java（端口 7108）

跳过 Odoo Gateway，直接验证模型和查询能力。

| 项目 | 值 |
|---|---|
| MCP Admin 端点 | `http://localhost:7108/mcp/admin/rpc` |
| MCP Analyst 端点 | `http://localhost:7108/mcp/analyst/rpc` |
| MCP SSE 端点 | `http://localhost:7108/mcp/analyst/stream` |
| Health Check | `http://localhost:7108/actuator/health` |
| 必填 Header | `X-NS: odoo` |

**AI 客户端配置**（Claude Desktop / Cursor / Cherry Studio 等）：
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

> 以下问题均以自然语言形式给出，模拟真实用户提问。
> AI 应自动识别合适的模型、字段、过滤条件和聚合方式。

### 一、基础查询（验证各模型可用）

1. 最近 5 笔销售订单是什么？显示订单号、客户名称、金额
2. 各状态的采购订单有多少笔？总金额分别是多少？
3. 列出所有在职员工，包含姓名、部门、职位、工作地点
4. 列出所有客户的名称、邮箱、国家和城市
5. 最近的库存调拨记录有哪些？显示单号、操作类型、源位置、目标位置、状态

### 二、维度名称显示（验证 JSONB 翻译字段）

以下维度的名称应返回可读文本（如 "Experienced Developer"）而非 JSON：

6. 列出所有员工和他们的职位名称
7. 每个销售团队有多少销售订单？
8. 列出客户所在的国家
9. 列出采购订单对应的收货类型
10. 各日记账下的发票数量是多少？
11. 列出库存调拨的操作类型
12. 销售明细中用到了哪些计量单位？

### 三、聚合分析（验证度量与分组）

13. 哪个客户的销售额最高？列出客户和对应的订单数、总金额
14. 销售金额的月度趋势是怎样的？
15. 各部门分别有多少员工？
16. 合作伙伴主要分布在哪些国家？
17. 各种操作类型的库存调拨分别有多少？
18. 各日记账类型的发票总额是多少？

### 四、层级穿透查询（验证闭包表）

19. "Research & Development"部门及其所有子部门一共有多少员工？列出明细
20. 总公司及其所有子公司的销售订单有哪些？
21. "Management"部门体系下各子部门的员工人数分别是多少？

### 五、自引用维度

22. 列出所有公司，同时显示各自的母公司名称
23. 列出合作伙伴及其上级公司名称

### 六、过滤条件

24. 已确认的销售订单总金额是多少？
25. 已付款的发票按客户统计，各客户付了多少？
26. 供应商主要分布在哪些国家？
27. 2024 年的采购订单总共有多少笔？总金额多少？

### 七、综合分析

28. "Sales"团队过去一年的月度销售额趋势
29. "Research & Development"及其子部门的员工按职位分布情况
30. 已完成的库存调拨，按操作类型和月份交叉分析数量

### 八、排名与 TopN

31. 销售额最高的前 3 个客户是哪些？
32. 哪个产品的销售金额最高？列出前 5 名
33. 采购金额最大的供应商是谁？

### 九、产品与明细分析

34. 各产品的销售数量和金额分别是多少？
35. 各销售员分别卖了多少金额？谁业绩最好？

### 十、时间对比

36. 这个月的销售额比上个月增长了多少？
37. Sales 和 Pre-Sales 两个团队各月的业绩分别是多少？

### 十一、发票分析

38. 客户发票和供应商账单各有多少笔？金额分别是多少？
39. 有退款单吗？分别是客户退款还是供应商退款？

### 十二、多条件组合

40. 金额超过 3000 或者状态为草稿的销售订单有哪些？
41. 除了 Gemini Furniture 以外的客户总共贡献了多少销售额？

### 十三、统计指标

42. 平均每笔销售订单的金额是多少？
43. 一共有多少个不同的客户下过销售订单？
44. 每笔销售订单平均包含多少个明细行？

---

## 快速验证命令

```bash
# 自动化验证（25 项测试）
cd addons/foggy-odoo-bridge
python tests/e2e/verify_all_models.py http://localhost:7108 odoo

# Schema 验证（TM vs 数据库列类型）
python tests/e2e/verify_schema.py --docker foggy-odoo-postgres

# 手动 curl 测试（示例：查询销售订单）
curl -s -X POST http://localhost:7108/mcp/admin/rpc \
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
