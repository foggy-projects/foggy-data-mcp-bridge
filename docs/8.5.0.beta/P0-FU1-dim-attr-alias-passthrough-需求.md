# P0 · 8.5.0.beta · FU-1 · `dim$attr AS userAlias` 用户 alias 透传修复（Java）

## 文档作用

- doc_type: requirement
- intended_for: implementation-agent / reviewer
- purpose: Java 端补齐 `dim$attr AS userAlias` 用户 alias 透传，对齐 Python `v1.7` T4 ★ 行为；关闭 8.4.0.beta P0-v13 acceptance FU-1
- delivery_mode: 轻量（2-piece：需求 + progress）

## 元数据

- version: 8.5.0.beta
- priority: P0（B-03 acceptance FU-1 直接承接）
- status: `accepted`
- target_repo: `foggy-data-mcp-bridge`（worktree `dev-compose`）
- estimated_effort: 0.5-1 天（实现集中在 1 个文件 + 测试翻转 1 处 + 新测试 1 个）
- python_sync_required: no（Python `v1.7` 已落盘 `59176f2`）
- progress_record: `docs/8.5.0.beta/P0-FU1-dim-attr-alias-passthrough-progress.md`
- acceptance_record: `docs/8.5.0.beta/acceptance/P0-FU1-dim-attr-alias-passthrough-acceptance.md`
- upstream:
  - 8.4.0.beta `accepted-with-risks`（commit `4f2f48c` + FU-2 closure `c021c88`）
  - Python parity `v1.7` `accepted`（commit `59176f2`）

## 背景

8.4.0.beta P0-v13（裸 dim 收紧）签收 `accepted-with-risks` 时，A1-3 验收项被 deferred 为本批 FU-1：

> A1-3 | `["dimension$caption AS userAlias"]` SQL 输出 `... AS "userAlias"` | **deferred → FU-1**（Java SQL gen alias 路径需另行改造）| **partial**

Python `v1.7` 同步需求中 T4 ★ 关键修复已交付：用户在 `dim$attr AS userAlias` 形态下指定的 alias 应覆盖 TM `dimension.alias`（默认是 dim caption），让 SQL `SELECT` 中的列别名是用户写的 `userAlias`。Java 端**完全反向**——当前不仅没有 user-alias 透传，还会**显式拒绝** `dim$attr AS alias` 形态：

```
COLUMN_DIMENSION_ALIAS_NOT_SUPPORTED: column 'product$caption AS p' references
dimension-suffixed field 'product$caption' with alias 'p'. Plain-field alias is
not supported on dimension members ($id/$caption); drop the alias or use the
base dimension field directly.
```

抛错点：`InlineExpressionPreprocessStep.trySynthesizePlainAlias`（line 651-659 · `8.5.0.beta` 修复对象）

这不仅与 Python parity 相悖，也违反了 LLM 元数据公开契约——元数据告诉 LLM 列可以"AS 重命名"，结果 LLM 写出来的 `dim$attr AS alias` 在 Java 端反而被拒绝。

## 目标

让 Java semantic query / Compose F4 两条路径都接受 `dim$attr AS userAlias`，并在最终 SQL 中输出用户的 alias，不再受 TM dimension caption/alias 影响。

### 行为契约

| Input | 当前 Java | 期望 Java | Python 对应 |
|-------|----------|----------|-----------|
| `columns: ["product$caption AS myAlias"]` | 抛 `COLUMN_DIMENSION_ALIAS_NOT_SUPPORTED` | SQL 输出 `... AS "myAlias"` | T4 ★ 已支持 |
| `columns: [{"field": "product$id", "as": "productId"}]` (F4) | 同上 | SQL 输出 `... AS "productId"` | F4 mirror 已支持 |
| `columns: ["product$caption"]`（无 alias） | SQL 输出 TM caption | 行为不变（保持 TM caption） | 行为不变 |
| `columns: ["product"]`（裸 dim · 无 alias） | T1 已 fail-loud（B-03） | 行为不变 | 行为不变 |
| `columns: ["product AS p"]`（裸 dim + alias · FU-2） | T2 fail-loud + dim-aware hint（FU-2 已交付） | 行为不变 | 行为不变 |

### 非目标

- 不改 SQL gen 主路径（`PreAggQueryRewriter` / `JdbcModelQueryEngine`）—— 改造收敛在 `InlineExpressionPreprocessStep`
- 不引入新的 `findJdbcQueryColumnByNameStrict` API（B-03 改造时已确认不需要）
- 不变更 `DbQueryColumnImpl.getAlias()` / `getCaption()` 的语义（user alias 已优先）

## 实施方案

**单点改动**：`InlineExpressionPreprocessStep.trySynthesizePlainAlias` 删除 `$` 拒绝分支 + 放开 `isSimpleFieldRef` 校验，让 `dim$attr AS alias` 走和 `field AS alias` 一样的 PLAIN_ALIAS calc-field 合成路径。

### 关键观察

1. 现有 PLAIN_ALIAS 合成路径（line 786-808）已经做对了：
   - `synth.setName(aliasName)` → calc field 名 = userAlias
   - `synth.setExpression(baseField)` → 表达式 = `product$caption`
   - 下游 calc-field SQL 编译器把 `product$caption` 解析为列引用 → SQL 注入 `dim_product.caption AS "userAlias"`

2. F4 集成测试 `f4PlainAliasOnDimensionSuffixRejected`（`F4ColumnObjectIntegrationTest.java` line 268-280）需要从"expect rejection" 翻转为 "expect success + user alias 出现在 SQL 中"

3. 元数据 `aliasOf`/`sourceField` 已就绪（`SemanticServiceV3Impl` line 1732-1738）—— 自动适配 `dim$attr` baseField

### 改动清单（4 处）

| # | 文件 | 行 | 操作 |
|---|------|----|------|
| 1 | `InlineExpressionPreprocessStep.java` | 651-659 | **删除** `if (baseField.indexOf('$') >= 0) throw ...` 分支 |
| 2 | `InlineExpressionPreprocessStep.java` | 661-664 + `isSimpleFieldRef` 818-834 | **新增** helper `isSimpleFieldOrDimAttrRef`（允许单个 `$` · 拒绝多 `$` / `.` / 函数语法）；调用点改用新 helper |
| 3 | `F4ColumnObjectIntegrationTest.java` | 268-280 | 翻转测试：从 `assertThrows(COLUMN_DIMENSION_ALIAS_NOT_SUPPORTED)` → `assertSuccess + SQL alias` 断言 |
| 4 | `StrictBareDimensionRejectionTest.java` | 156-164 | 移除 `@Disabled` + 实装 T4 测试体（参考 Python `test_t4_user_alias_overrides_tm_caption`） |

### 测试用例预期

新增 / 翻转后的测试需覆盖：

- **T4-a**（StrictBareDimensionRejectionTest · 主验收）：`product$caption AS userAlias` → 查询成功 + 响应列名是 `userAlias` 而非 dim caption
- **T4-b**（F4 form mirror）：`{field: "product$id", as: "productId"}` → 查询成功 + `productId` 出现在响应列
- **T4-c**（PLAIN_ALIAS metadata）：F4 path 合成的 calc field `aliasOf=product$caption` 出现在 metadata（既有 `f4PlainAliasMetadataIsolation` 自动覆盖）
- **T4-d**（regression · `field AS alias` 旧路径不变）：`salesAmount AS revenue` → 行为不变

## 验收标准

| ID | 内容 | 来源 |
|----|------|------|
| A1 | `product$caption AS userAlias` semantic query 成功 + 响应列名 = `userAlias` | T4 主用例 |
| A2 | F4 `{field, as}` 同效 | F4 mirror |
| A3 | sqlite lane 全绿（≥ 1857 baseline · 0 regression） | M3 |
| A4 | F4 `f4PlainAliasOnDimensionSuffixRejected` 测试翻转后通过 | M3 |
| A5 | F4 `f4PlainAliasMetadataIsolation` 测试不退化（PLAIN_ALIAS metadata 隔离仍正确） | M3 |
| A6 | 与 Python `v1.7` T4 ★ 行为对齐（同 input 同 SQL 输出形态） | M4 |

## 里程碑

| # | 阶段 | 备注 |
|---|------|------|
| M0 | 立项 | 本文档（2026-04-28）|
| M1 | 单点改动落盘 | `InlineExpressionPreprocessStep` |
| M2 | 测试翻转 | `f4PlainAliasOnDimensionSuffixRejected` |
| M3 | T4 测试激活 + 全量回归 | sqlite lane |
| M4 | progress + acceptance addendum | 关闭 B-03 FU-1 |

## 风险

- R1 · `expression="product$caption"` 在 calc-field SQL 编译器中是否能正确解析为带 JOIN 的 dim attr 引用？ → 现有 `salesAmount AS revenue` 路径已证明 bare-column-ref expression 能编译；`product$caption` 只是多了 dim JOIN，由 `findJdbcColumnForSelectByName` 返回的 `DbQueryColumn` 自带 selectColumn JOIN 语义，should work
- R2 · F4 `f4PlainAliasOnDimensionSuffixRejected` 测试 spec §3.1.2 历史决策：当时 G5 v2-patch-2 决定不支持 `dim$attr AS alias`。本批次基于 Python parity（T4 ★）+ LLM 契约一致性（公开元数据允许 AS 重命名）覆盖该旧决策，不需要 spec 改动 —— 直接在 acceptance 中标注本批 supersedes 该 §3.1.2 限制

## 关联

- 上游 8.4.0.beta acceptance: `docs/8.4.0.beta/acceptance/P0-v13-bare-dimension-tightening-acceptance.md`（FU-1 关闭依赖本批）
- Python parity: `foggy-data-mcp-bridge-python/docs/v1.7/P0-v13引擎收紧裸dimension引用-需求.md`（T4 ★）
- B-03 backlog（已 resolved）: `foggy-data-mcp-bridge-python/docs/backlog/B-03-v13-engine-bare-dimension-tightening.md`

## 维护记录

| 日期 | 操作 | 备注 |
|------|------|------|
| 2026-04-28 | 立项 | 8.4.0.beta P0-v13 acceptance FU-1 抬升至 8.5.0.beta P0；预算 0.5-1 天 |
| 2026-04-28 | 交付 + 签收 | Java 单点修复落盘；T4 激活 + F4 测试翻转；`mvn -pl foggy-dataset-model test` → 1857 tests / 0 failures / 0 errors / 1 skipped；acceptance decision `accepted` |

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex
- signed_off_at: 2026-04-28
- acceptance_record: `docs/8.5.0.beta/acceptance/P0-FU1-dim-attr-alias-passthrough-acceptance.md`
- blocking_items: none
- follow_up_required: no
