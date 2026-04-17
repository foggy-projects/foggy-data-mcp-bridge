# P0 - JDBC 条件聚合 IF 降级兼容 — Code Inventory

## 文档作用

- doc_type: `implementation-plan`
- intended_for: `execution-agent | reviewer`
- purpose: 识别本特性涉及的核心代码触点、职责和预期改动范围

## 基本信息

- 目标版本：`8.1.10.beta`
- 上游需求：`docs/8.1.10.beta/P0-JDBC条件聚合IF降级兼容-需求.md`
- 仓库：`foggy-data-mcp-bridge`

## Code Inventory

### 表达式函数白名单

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/expression/AllowedFunctions.java`
- role: JDBC 计算字段表达式函数白名单与运算符映射
- expected_change: `read-only-analysis`
- notes: 当前 `IF` 已在白名单中；需确认不新增主契约，仅复用现有能力

### JDBC 函数 lowering 主入口

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/expression/sql/SqlFunctionExp.java`
- role: JDBC 函数调用表达式 lowering
- expected_change: `update`
- notes: 本次主要改动点；为 `IF(...)` 增加统一 `CASE WHEN` lowering，确保聚合包裹场景跨方言稳定

### SQL 片段类型 / 聚合元信息

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/expression/SqlFragment.java`
- role: SQL 片段元信息承载（类型、聚合、窗口、引用列）
- expected_change: `read-only-analysis`
- notes: 需确认 `CASE WHEN` 结果类型、引用列、hasAggregate 传播是否满足新场景；必要时补最小修正

### 内联表达式聚合识别

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/InlineExpressionPreprocessStep.java`
- role: columns 内联表达式解析与 aggregate 识别
- expected_change: `read-only-analysis`
- notes: 若 `sum(if(...))` / `count(if(...))` 的顶层仍由聚合函数包裹，理论上无需主逻辑改动；需补回归验证

### SQL 表达式工厂

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/expression/SqlExpFactory.java`
- role: 将 FSScript 表达式编译为 SQL AST
- expected_change: `read-only-analysis`
- notes: 当前已支持 `IF(...)` 进入 `SqlFunctionExp`；本次不改 parser / factory 主契约

### Mongo IF 支持（对照）

- repo: `foggy-data-mcp-bridge`
- path: `addons/foggy-dataset-model-mongo/src/main/java/com/foggyframework/dataset/db/model/engine/expression/mongo/MongoFunctionExp.java`
- role: Mongo 表达式 `IF(...) -> $cond`
- expected_change: `read-only-analysis`
- notes: 作为对照实现；本次默认不改 Mongo 主逻辑

### JDBC 表达式单元测试

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/expression/SqlExpFactoryTest.java`
- role: 表达式工厂与运算符/函数语义单测
- expected_change: `update`
- notes: 补 `IF(...)` lowering、聚合包裹 `IF(...)` 的 SQL 断言

### 计算字段链路测试

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/expression/CalculatedFieldServiceTest.java`
- role: 计算字段编译 / SQL 片段生成回归测试
- expected_change: `update`
- notes: 补 `sum(if(...)) / avg(if(...)) / count(if(...))` 在 calculatedFields / inline expression 链路中的覆盖

### 多方言 / 真实查询集成测试

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/`
- role: JDBC 查询真实执行测试
- expected_change: `update`
- notes: 需要新增或扩展一组集成测试，覆盖 SQLite 基线与可选多方言 profile；优先放在现有 ecommerce / expression 相关测试域

### 文档手册

- repo: `foggy-data-mcp-bridge`
- path: `docs-site/zh/dataset-model/tm-qm/calculated-fields.md`
- role: 中文计算字段手册
- expected_change: `update`
- notes: 若本次实现落地并对外公布，需要补“聚合函数包裹 IF”示例和方言兼容说明

- repo: `foggy-data-mcp-bridge`
- path: `docs-site/en/dataset-model/tm-qm/calculated-fields.md`
- role: 英文计算字段手册
- expected_change: `update`
- notes: 与中文手册同步；若本轮仅落内部方案，可延后到功能合入阶段
