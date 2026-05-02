# P0 · v1.3 引擎收紧裸 dimension 引用 + 修复 dimension AS alias 静默丢列（Java 端）

## 文档作用

- doc_type: workitem
- intended_for: execution-agent / reviewer / signoff-owner
- purpose: 把 backlog `B-03` Java 侧改造抬升为 8.4.0.beta 正式需求；与 Python `v1.7` 同步交付

## 元数据

- 优先级：**P0**（治理项 · 影响 LLM 公开契约一致性 + 测试基线稳定性）
- 状态：`accepted`（待启动）
- 目标版本：`8.4.0.beta`（Java 端）· 与 Python `v1.7` 同步
- 改造路径：**Path A · 严格化**
- 来源：G5 PR-P2 调试期复盘（commit `cf2ba9b` → `352a8bb`）
- 关联 backlog：`foggy-data-mcp-bridge-python/docs/backlog/B-03-v13-engine-bare-dimension-tightening.md`（已抬升）
- 关联 Python 需求：`foggy-data-mcp-bridge-python/docs/v1.7/P0-v13引擎收紧裸dimension引用-需求.md`

## 背景

Foggy QM 公开契约规定：**dimension 不是直接可投影的列**，必须通过 `$id` / `$caption` / `$<custom_attr>` 引用其属性。LLM 通过 `dataset.get_metadata` / `dataset.describe_model_internal` 看到的元数据**从不暴露裸 dimension** 作为可投影字段。

但 Java `SemanticQueryServiceV3Impl` 当前列解析路径存在与 Python 同源的三类不符合公开契约的行为，是 G5 F5 集成测试（`F5ColumnObjectIntegrationTest`）落地期暴露的长期遗留问题。

## 当前行为参考（Python 实测 · 详见 Python v1.7 需求文档）

Java 端的具体行为细节由本需求 **M3.1（跨端审计）** step 实测落档。基于 Python 等价路径的实测结果，Java 端预期存在：

1. **裸 `dimension` fallback** —— `findJdbcQueryColumnByName(name, false)` 命中 dim 主列，输出 `t.<col> AS "<TM caption>"`
2. **`dimension AS alias` 静默丢列** —— SQL 输出 `SELECT *` 或类似空 SELECT
3. **`dimension$attr AS alias` 用户 alias 被吞** —— SQL 仍用 TM 声明的 `dimension.alias`

跨端对照表（Python 实测） · 待 M3.1 用 Java 单测补完 Java 行为列：

| 输入 | Python 实际 | Java 预期 | Java 实测（M3.1）|
|------|------------|----------|-----------------|
| `["orderStatus"]` | `t.order_status AS "订单状态"` (fallback) | 等价行为 | 待补 |
| `["orderStatus AS s"]` | silent-drop + warning | 等价行为 | 待补 |
| `["orderStatus$caption"]` | `t.order_status AS "订单状态"` | 等价行为 | 待补 |
| `["orderStatus$caption AS s"]` | `t.order_status AS "订单状态"` (alias 被吞) | 等价行为 | 待补 |
| `["unknownField"]` | silent-drop + warning | 等价行为 | 待补 |
| `["SUM(salesAmount) AS total"]` | `SUM(t.sales_amount) AS "total"` | 等价行为 | 待补 |

## 目标（Path A · 严格化）

跨双端契约一致 —— 与 Python `v1.7` 需求文档"目标"小节字面相同。Java 端按相同语义落地：

- 接受 `dimension$id` / `$caption` / `$<custom_attr>` / 同 + AS / `measureName` / `propertyName` / `AGG(...) AS alias` / F4 F5 dict
- 拒绝裸 `dimension` / `dimension AS alias` / 未识别字符串（fail-loud + hint）
- 修复 `dimension$attr AS alias` user alias 透传

## 改造方案（Java）

### M3.1 · 跨端行为实测对比

写一组临时 `@SpringBootTest` 单测覆盖六种输入（同 Python 测试矩阵），实测当前 SQL 输出 + warning 列表。补完上方"跨端对照表"。

**预期产物**：行为对照表 markdown 完整版 + 影响差异点列表。

### M3.2 · `findJdbcQueryColumnByName` 收紧

提案：新增 `findJdbcQueryColumnByNameStrict(String columnName, String userAlias)` 方法，**不走** 当前的"宽容 fallback"分支：

- 不再接受裸 `dimension`
- 不再忽略 `$<attr>` suffix
- 严格匹配 `id` / `caption` / `<custom_attr>`
- 接受可选 `userAlias`，覆盖 `JdbcQueryColumn.caption`

旧 `findJdbcQueryColumnByName` 标 `@Deprecated` 但保留（其他非 SQL-gen 路径继续用，例如 metadata enrichment）；SQL gen 路径切换到 strict 版本。

### M3.3 · `SemanticQueryServiceV3Impl` 列循环改造

伪代码：

```java
for (String colName : request.getColumns()) {
    // Step A: 解析 AS alias
    ColumnAliasParts parts = ColumnAliasParser.parse(colName);
    String baseExpr = parts.expression();
    String userAlias = parts.userAlias();  // nullable

    // Step B: inline aggregate（保持原行为，但 user alias 显式透传）
    InlineAggregateMatch inlineMatch = parseInlineAggregate(baseExpr, model);
    if (inlineMatch != null) {
        // ... user alias / inlineMatch.alias / model fallback 三选一
        continue;
    }

    // Step C: strict resolve
    JdbcQueryColumn resolved = model.findJdbcQueryColumnByNameStrict(baseExpr, userAlias);
    if (resolved != null) {
        // 加入 SELECT，user alias 已经透传到 column.caption
        continue;
    }

    // Step D: 裸 dimension fail-loud（hint）
    if (model.isDimensionRoot(baseExpr)) {
        throw new IllegalArgumentException(
            "COLUMN_FIELD_NOT_FOUND: '" + colName + "' references dimension '" + baseExpr +
            "' directly. Dimensions are not projectable; reference an attribute " +
            "(e.g. '" + baseExpr + "$caption' or '" + baseExpr + "$id'). " +
            "Hint: did you mean '" + baseExpr + "$caption" +
            (userAlias != null ? " AS " + userAlias : "") + "'?");
    }

    // Step E: 全失败 fail-loud
    throw new IllegalArgumentException(
        "COLUMN_FIELD_NOT_FOUND: '" + colName + "' is not a recognized column");
}
```

### M3.4 · 调用点同步审计

grep 全 Java 仓 `findJdbcQueryColumnByName(`：

- SQL gen 路径切到 `*Strict`
- metadata 路径保留旧调用（不影响契约）
- 文档 / report 路径根据语义判断

## 影响面评估

### 主改动

| 模块 | 路径 | 风险 |
|------|------|------|
| `SemanticQueryServiceV3Impl` 列循环 | `foggy-dataset-model/src/main/java/.../service/impl/SemanticQueryServiceV3Impl.java:200-360` | 主改造点 |
| `JdbcQueryModel.findJdbcQueryColumnByName` | grep 实际位置 | 需要新增 strict 方法 |
| `ColumnAliasParser`（如不存在则新建） | `foggy-dataset-model/src/main/java/.../engine/compose/schema/ColumnAliasParts.java` 已有 | 复用现有 schema 模块 |
| `findJdbcQueryColumnByName` 调用点 | grep 全仓 | 同步切换 |

### 现有兼容路径影响

- F5 `ColumnObjectNormalizer.normalize`（已落盘）—— 不需要改，flatten 出来的字符串经新引擎仍能正确路由
- `FluentApiCompileTest` 等 chained API 测试 —— 走的是 `PlanColumnRef` / `AggregateColumn` 对象路径，不通过字符串解析，不受影响
- `EcommerceTestSupport` 系列 —— 大部分用 QM `#columnGroup` + `columns: List.of("dim$id", ...)` 已是契约形态，需 grep 确认

### 跨仓影响

| 仓 | 路径 | 处理方式 |
|----|------|---------|
| `foggy-data-mcp-bridge-python` | 同步改造 | 详见 v1.7 需求 |
| `foggy-odoo-bridge-pro` vendored Java JAR | gateway 模式打包 | Java 落盘后 Odoo Pro 同步 jar |

## 验收标准

### A1 · 行为契约对齐（Java）

- A1-1 · 裸 `["dimension"]` 抛 `IllegalArgumentException` 含 hint `"did you mean 'dim$caption'"`
- A1-2 · `["dimension AS alias"]` 抛 `IllegalArgumentException` 含 hint `"did you mean 'dim$caption AS alias'"`
- A1-3 · `["dimension$caption AS userAlias"]` SQL 输出 `... AS "userAlias"`（不再用 TM dimension.alias）
- A1-4 · `["dimension$id"]` / `["dimension$caption"]` / `["dimension$<custom_attr>"]` 行为不变
- A1-5 · `["measureName"]` / `["propertyName"]` / `["AGG(measure) AS alias"]` 行为不变

### A2 · 跨端 parity（Python `v1.7` 同步）

- A2-1 · 同 input Python `ValueError` ↔ Java `IllegalArgumentException` 错误码一致（`COLUMN_FIELD_NOT_FOUND` 前缀）
- A2-2 · F4/F5 normalizer flatten 出来的字符串经新 strict 引擎仍能正确路由

### A3 · 回归零退化

- A3-1 · `foggy-dataset-model` sqlite lane 维持 1809+ passed
- A3-2 · `F5ColumnObjectIntegrationTest`（5 tests）零回归
- A3-3 · `FormulaParitySnapshotTest` / `DialectAwareFunctionExpTest` 零回归

### A4 · 影响面清理

- A4-1 · 历史 fixture grep 报告 + 必要的迁移已落盘
- A4-2 · Odoo Pro vendored java JAR 同步 + gateway lane 全绿（如 infra 可用）

## 测试计划

### 新增 Java 单测

`SemanticQueryServiceV3StrictColumnResolutionTest.java`（建议位置 `foggy-dataset-model/src/test/java/.../semantic/`）：

| # | 用例 | 期望 |
|---|------|------|
| T1 | `columns=["product"]` | `IllegalArgumentException` + hint |
| T2 | `columns=["product AS p"]` | `IllegalArgumentException` + hint |
| T3 | `columns=["product$id"]` | SQL 走 join id 路径 |
| T4 | `columns=["product$id AS productKey"]` | SQL alias `productKey` 覆盖 TM caption ★关键 |
| T5 | `columns=["product$caption"]` | SQL 走 join caption 路径 |
| T6 | `columns=["product$caption AS productName"]` | SQL alias `productName` ★关键 |
| T7 | `columns=["unknownField"]` | `IllegalArgumentException("COLUMN_FIELD_NOT_FOUND: ...")` |
| T8 | `columns=["salesAmount"]` | SQL 走 measure aggregation |
| T9 | `columns=["SUM(salesAmount) AS total"]` | 行为不变（inline aggregate） |
| T10 | F5 dict `[{plan, field: "product$id"}]` | flatten 后行为同 T3 |

### 集成测试回归

- `F5ColumnObjectIntegrationTest`（PR-J2 落盘的 5 tests）零回归
- `EcommerceTestSupport` + 已有 sqlite lane 全量回归

## 非目标

- ❌ 不改 F4 / F5 normalizer 的 flatten 规则
- ❌ 不改 `parse_inline_aggregate` 的聚合白名单
- ❌ 不重构 metadata 暴露的字段形态
- ❌ 不引入 deprecation warning compat layer

## 工作量估算

| 阶段 | 时长 | 备注 |
|------|------|------|
| 跨端审计（M3.1） | 0.5d | 写临时单测实测 |
| 主改造（M3.2 + M3.3） | 1d | strict 方法 + 列循环 |
| 调用点同步（M3.4） | 0.5d | grep + 切换 |
| 单测补完（T1-T10） | 0.5d | |
| 全量回归 + Odoo Pro JAR 同步 | 0.5d | |
| 文档 + 验收 | 0.5d | |
| **合计** | ~3d | 与 Python 并行可压缩 |

## 后续衔接

- 完成后通知 root `CLAUDE.md` "已解决的问题" 区块新增条目
- backlog `B-03` 状态置 `resolved`
- G5 F5 spec §4.2 "F5 用户级开放门" 同步更新（治理项落盘后 flag-flip rollout C1-C4 决策门更接近成立）
- gap tracker 同步更新（如有相关 gap row）
