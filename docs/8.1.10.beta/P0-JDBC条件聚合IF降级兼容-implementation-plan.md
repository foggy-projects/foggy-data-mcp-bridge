# P0 - JDBC 条件聚合 IF 降级兼容 — Implementation Plan

## 文档作用

- doc_type: `implementation-plan`
- intended_for: `execution-agent | reviewer`
- purpose: 将 `聚合函数包裹 IF(...)` 的跨方言兼容能力拆成可执行开发步骤与测试步骤

## 基本信息

- 目标版本：`8.1.10.beta`
- 上游需求：`docs/8.1.10.beta/P0-JDBC条件聚合IF降级兼容-需求.md`
- 代码清单：`docs/8.1.10.beta/P0-JDBC条件聚合IF降级兼容-code-inventory.md`
- 仓库：`foggy-data-mcp-bridge`

## 实施原则

- 不修改 parser 主语法
- 不修改 semantic service 主流程
- 不修改 SQL builder 主逻辑
- 只在 JDBC 表达式 lowering 层补最小能力
- 先保证 `IF(...)` 的跨方言 lowering，再用聚合函数自然包裹

## 实施步骤

### Step 1. 明确 JDBC 端 `IF(...)` lowering 规则

在 `SqlFunctionExp` 中建立统一规则：

- `IF(cond, thenExpr, elseExpr)` 不直接保留为 `IF(...)`
- 统一输出为 `CASE WHEN cond THEN thenExpr ELSE elseExpr END`

关键要求：

- 保留引用列集合
- 保留返回类型推断
- 不误标 `hasAggregate`
- 不依赖数据库方言是否原生支持 `IF`

验收：

- SQL 片段生成阶段，`IF(...)` 输出已变为 `CASE WHEN ...`
- PostgreSQL / SQL Server / SQLite 不再收到原样 `IF(...)`

### Step 2. 验证聚合函数包裹 `IF(...)` 的组合正确性

重点验证以下模式：

- `SUM(IF(cond, amount, 0))`
- `AVG(IF(cond, amount, NULL))`
- `COUNT(IF(cond, 1, NULL))`
- `SUM(IF(cond1 && cond2, amount, 0))`

确认点：

- 顶层聚合函数仍被识别为 aggregate
- `InlineExpressionPreprocessStep` 不需要主逻辑改动
- `SqlFragment` 引用列 / 类型 / aggregate 信息正确

验收：

- 以上模式均可生成合法 SQL
- autoGroupBy / alias / orderBy 主链路不回归

### Step 3. 补 JDBC 单元测试

建议测试点：

- `IF(a == 1, b, 0)` 转成 `CASE WHEN a = 1 THEN b ELSE 0 END`
- `SUM(IF(a == 1, b, 0))` 的 SQL 断言
- `AVG(IF(a == 1, b, NULL))` 的 SQL 断言
- `COUNT(IF(a == 1, 1, NULL))` 的 SQL 断言
- `IF` 内部复合条件 `&& / || / ()`

验收：

- 新增单测全部通过
- 现有 `SqlExpFactoryTest` / `CalculatedFieldServiceTest` 不回归

### Step 4. 补真实查询集成测试

按现有集成测试规范，至少补一组真实数据比对：

- 使用 `queryFacade.queryModelData()` 执行真实查询
- 用等价原生 SQL 作为 baseline
- 比对返回值、排序、聚合结果

建议覆盖：

- `sum(if(orderStatus == 'COMPLETED', salesAmount, 0))`
- `count(if(orderStatus == 'COMPLETED', 1, null))`
- `avg(if(orderStatus == 'COMPLETED', salesAmount, null))`

推荐执行层次：

- 必跑：SQLite profile
- 可选增强：MySQL / PostgreSQL / SQL Server profile 至少各一条

验收：

- 真实查询结果与 baseline SQL 一致
- 多方言 profile 至少验证 SQL 生成或执行可用性

### Step 5. 回写文档与对外说明

若代码实现合入：

- 更新 `docs-site` 计算字段文档
- 明确该能力是“表达式兼容能力”，不是正式 `count_if/sum_if/avg_if` 契约
- 补示例与边界：
  - `SUM(IF(..., amount, 0))`
  - `AVG(IF(..., amount, NULL))`
  - `COUNT(IF(..., 1, NULL))`

验收：

- 中文手册完成回写
- 英文手册视版本节奏决定是否同步

## 测试方案

### 单元测试

目标：验证 lowering 与表达式元信息

建议用例：

- `IF` 单独使用时的 SQL 生成
- `SUM/AVG/COUNT` 包裹 `IF` 时的 SQL 生成
- 复合条件与括号优先级
- `NULL` / `0` / `1` 等典型 else 分支

### 集成测试

目标：验证真实查询结果而非仅 SQL 字符串

建议场景：

- 基于 `ecommerce` 模型准备可人工核算的数据
- 按维度分组后使用 `sum(if(...))`
- 同时验证 `columns + orderBy + groupBy`

### 回归测试

必须确认：

- 原有 `SUM/AVG/COUNT` 场景不回归
- 原有非聚合 `IF(...)` 场景不回归
- `fieldAccess / deniedColumns` 相关测试不被误伤

## 不做的事

- 不在本阶段引入 `count_if / sum_if / avg_if` 新函数
- 不开放 `CASE WHEN` 原生 DSL 语法
- 不修改 Mongo 主逻辑
- 不修改 Python Odoo 内嵌引擎
- 不在 planner 层同步做大规模 prompt 调整

## 风险清单

### 风险 1. `AVG(IF(...))` 的 else 分支被误写为 `0`

影响：

- 结果均值被拉低，业务含义错误

处理：

- 文档与测试中明确推荐 `AVG(IF(cond, amount, NULL))`

### 风险 2. `COUNT(IF(...))` 的调用约定不清楚

影响：

- 用户可能写出 `COUNT(IF(cond, 1, 0))`，导致 `0` 也被计数

处理：

- 文档明确推荐 `COUNT(IF(cond, 1, NULL))`
- 本阶段不额外做自动修正

### 风险 3. JDBC 与 Mongo 行为文档不一致

影响：

- 用户误以为该能力已在所有引擎同步完成

处理：

- 文档中明确本阶段是 JDBC 优先
- Mongo 仅说明已有 `$cond` 实现，不宣称同一特性完整交付

## 预估工作量

| Step | 预估 | 说明 |
|------|------|------|
| 1. `IF(...)` lowering | 0.5d | `SqlFunctionExp` 主改动 |
| 2. 聚合组合验证 | 0.25d | 表达式与元信息检查 |
| 3. 单元测试 | 0.5d | SQL 断言与回归 |
| 4. 集成测试 | 0.5d | SQLite 基线 + 可选多方言 |
| 5. 文档回写 | 0.25d | docs-site 与版本文档 |
| **合计** | **2.0d** | 不含后续 planner/prompt 联动 |
