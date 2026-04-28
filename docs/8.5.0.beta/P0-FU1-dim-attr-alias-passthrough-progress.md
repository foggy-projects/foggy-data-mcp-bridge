# P0 · FU-1 · `dim$attr AS userAlias` 用户 alias 透传修复 Progress

## 文档作用

- doc_type: progress
- intended_for: implementation-agent / reviewer / acceptance-owner
- purpose: 记录 8.5.0.beta FU-1 Java 侧实现、测试证据与签收状态

## 元数据

- version: 8.5.0.beta
- target: P0-FU1-dim-attr-alias-passthrough
- status: `accepted`
- started_at: 2026-04-28
- completed_at: 2026-04-28
- acceptance_record: `docs/8.5.0.beta/acceptance/P0-FU1-dim-attr-alias-passthrough-acceptance.md`

## 执行摘要

Java 端已补齐 `dim$attr AS userAlias` 透传能力，对齐 Python `v1.7` T4 行为。修复点收敛在 `InlineExpressionPreprocessStep.trySynthesizePlainAlias`：删除对 `$` 维度属性 alias 的显式拒绝，允许 `product$id` / `product$caption` 这类单级 dim attr 引用进入既有 PLAIN_ALIAS calc-field 合成路径。

裸维度 alias 拒绝行为保持不变：`product AS p` 仍抛 `COLUMN_FIELD_NOT_FOUND`，并保留 `product$caption AS p` hint。

## 变更清单

| 文件 | 变更 |
|------|------|
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/InlineExpressionPreprocessStep.java` | 删除 `COLUMN_DIMENSION_ALIAS_NOT_SUPPORTED` 分支；新增 `isSimpleFieldOrDimAttrRef`，允许单级 `$` dim attr，拒绝多 `$` / `.` / 函数语法 |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/semantic/StrictBareDimensionRejectionTest.java` | 移除 T4 `@Disabled`；新增执行断言：`product$caption AS userAlias` 返回列名必须是 `userAlias` |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/compose/F4ColumnObjectIntegrationTest.java` | 将 F4 dim suffix alias 用例从 expect rejection 翻转为真实 SQL 数据对比：`{field:"product$id", as:"productId"}` |
| `docs/8.5.0.beta/P0-FU1-dim-attr-alias-passthrough-需求.md` | status 更新为 `accepted`，补签收标记 |

## 验收项进度

| ID | 内容 | 状态 | 证据 |
|----|------|------|------|
| A1 | `product$caption AS userAlias` semantic query 成功 + 响应列名 = `userAlias` | passed | `StrictBareDimensionRejectionTest.t4_userAliasOverridesTmCaptionOnDimAttr` |
| A2 | F4 `{field, as}` 同效 | passed | `F4ColumnObjectIntegrationTest.f4PlainAliasOnDimensionSuffixUsesUserAlias` |
| A3 | sqlite lane 全绿（baseline 1857 · 0 regression） | passed | `mvn -pl foggy-dataset-model test` → 1857 tests / 0 failures / 0 errors / 1 skipped |
| A4 | F4 dim suffix alias 测试翻转后通过 | passed | 定向测试 + 全量模块测试 |
| A5 | F4 `f4PlainAliasMetadataIsolation` 不退化 | passed | `F4ColumnObjectIntegrationTest` 整类通过 |
| A6 | 与 Python `v1.7` T4 行为对齐 | passed | Java T4 输入/输出契约与 Python T4 一致 |

## 测试记录

| 时间 | 命令 | 结果 |
|------|------|------|
| 2026-04-28 | `mvn -pl foggy-dataset-model "-Dtest=StrictBareDimensionRejectionTest,F4ColumnObjectIntegrationTest" test` | 23 tests / 0 failures / 0 errors / 0 skipped |
| 2026-04-28 | `mvn -pl foggy-dataset-model test` | 1857 tests / 0 failures / 0 errors / 1 skipped |

## 风险与结论

- blocking_items: none
- open_risks: none
- follow_up_required: no

结论：FU-1 已交付并通过功能级验收，可关闭 8.4.0.beta P0-v13 acceptance 中的 A1-3 / FU-1 缺口。
