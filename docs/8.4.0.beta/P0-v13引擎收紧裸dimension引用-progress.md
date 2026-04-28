---
type: progress
version: 8.4.0.beta
req_id: P0-v13-bare-dimension-tightening
status: ready-for-review
priority: P0
blocking_for: []
python_sync_required: yes  # 同步在 Python v1.7
python_side_status: ready-for-review  # Python `59176f2` 落盘 · 3223 passed
java_side_status: ready-for-review  # bare-dim rejection unified to COLUMN_FIELD_NOT_FOUND · 1855 passed / 0 regression · user-alias fix deferred
odoo_pro_side_status: not-started
acceptance_record: docs/8.4.0.beta/acceptance/P0-v13-bare-dimension-tightening-acceptance.md  # M11 落盘
accepted_at: null
---

# P0 v1.3 引擎收紧裸 dimension 引用 (Java) — Progress

> 状态口径：`not-started` / `in-design` / `in-progress` / `blocked` / `ready-for-review` / `accepted` / `rejected`

## 里程碑

| # | 阶段 | 状态 | 日期 | 备注 |
|---|------|------|------|------|
| M0 | 需求立项 | `completed` | 2026-04-28 | 本需求文档 + Python 同步需求落盘 |
| M1 | 跨端行为审计 + 行为对照表 | `completed` | 2026-04-28 | 实测发现 Java 已对 FK-style dim 严格（`schemaFields` 不含裸 dim 名 → 落入 `INVALID_QUERY_FIELD` "Field not found" 路径）；裸 dim 行为漏洞主要在 self-attr dim（demo 中无样本）；user-alias 透传（Python T4★）属 SQL 生成路径，跨端 Java 路径 deferred |
| M2 | Python 端实施（v1.7） | `completed` | 2026-04-28 | 跨仓追踪：`foggy-data-mcp-bridge-python/docs/v1.7/P0-v13引擎收紧裸dimension引用-progress.md` · pytest 3223 passed |
| M3 | Java 端 `findJdbcQueryColumnByNameStrict` 实装 | `partial-completed` | 2026-04-28 | **未新建 strict 方法**——改造方案改进为：在 `SchemaAwareFieldValidationStep.validateField` 入口先做 `isBareDimensionReference` 判定，命中即抛 `COLUMN_FIELD_NOT_FOUND` + hint。两条路径（schemaFields 命中 / 不命中）都被覆盖。User-alias 透传 deferred（FOLLOW-UP-1）|
| M4 | Java 端 `SemanticQueryServiceV3Impl` 列循环改造 | `not-required` | 2026-04-28 | M3 改造点位足以收口；不需要侵入主列循环 |
| M5 | 新增 Java 单测（T1-T10 镜像） | `partial-completed` | 2026-04-28 | `StrictBareDimensionRejectionTest.java`（3 tests · T1/T3/T5）· **3/3 passed**。T2 (`dim AS alias`) 由 Java inline parser 现有路径承接（已存在测试）；T4 (★ user-alias) deferred；T6/T7-T10 不需要 Java 镜像（Python 已覆盖跨端契约） |
| M6 | Java 历史 fixture / 测试 grep 全仓 + 批量迁移 | `not-required` | 2026-04-28 | **意外不需要**——FSScript-loaded demo 模型把 `orderStatus` 等定义为 properties（不是 dims），FK-style dim 在 Java schemaFields 中本就不暴露裸名。零 fixture 迁移；零 regression |
| M7 | Java sqlite lane 全量回归 | `completed` | 2026-04-28 | `mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite` · **1855 passed / 0 failures / 1 skipped**（高于文档基线 1809）·  零 regression |
| M8 | F5 / parity / dialect 系列零回归 | `completed` | 2026-04-28 | 全量 sqlite lane 已含 `F5ColumnObjectIntegrationTest`、`FormulaParitySnapshotTest`、`DialectAwareFunctionExpTest` 等系列；M7 结果已包含验证 |
| M9 | Odoo Pro vendored JAR 同步 + gateway lane 全绿 | `not-started` | — | infra 可用时 |
| M10 | 跨端 parity 双端核对（A2） | `not-started` | — | 同 input 双端等价错误 / SQL 输出 |
| M11 | 签收记录 `docs/8.4.0.beta/acceptance/` | `not-started` | — | 标准 acceptance 文档 + evidence 列表 |
| M12 | 通知 root `CLAUDE.md` + backlog `B-03` 关闭 | `not-started` | — | 升级到"已解决的问题"区块；backlog README 状态改 `resolved` |

## 前置条件检查

| 项 | 状态 | 备注 |
|----|------|------|
| G5 F5 双端实施完成 | ✅ | PR-J1/J2/P1/P2 已落盘 |
| backlog B-03 立项 | ✅ | 已抬升至 v1.7 / 8.4.0.beta |
| 用户决策 Path A 严格化 | ✅ | 2026-04-28 用户确认 |
| Python v1.7 同步需求文档 | ✅ | `foggy-data-mcp-bridge-python/docs/v1.7/P0-v13引擎收紧裸dimension引用-需求.md` |

## 测试覆盖要求

### 单测（Java）

- T1-T10 见需求文档"测试计划 · 新增 Java 单测"（demo 模型字段：`product` / `product$id` / `product$caption` / `salesAmount`）

### 集成测试回归

- `F5ColumnObjectIntegrationTest`（5 tests · PR-J2 落盘）零回归
- `FormulaParitySnapshotTest`（5 tests）零回归
- `DialectAwareFunctionExpTest`（14 tests · 四方言）零回归
- `EcommerceTestSupport` 系列零回归

### Experience 验证

- experience: **N/A**（引擎层错误处理收紧 · 纯后端）

## 跨仓影响清单

| 仓 | 路径 | 处理方式 |
|----|------|---------|
| `foggy-data-mcp-bridge-python` | 同步改造 | 详见 v1.7 progress |
| `foggy-data-mcp-bridge` (本仓 worktree `dev-compose`) | `foggy-dataset-model/src/main/java/.../service/impl/SemanticQueryServiceV3Impl.java` + `JdbcQueryModel` 等 | M3 + M4 + M5 + M6 + M7 |
| `foggy-odoo-bridge-pro` | gateway 模式 vendored java JAR | M9 |

## 风险记录

- R1 · 内部历史测试 / fixture 大量依赖裸 dim → M6 grep 全仓 + 批量重写
- R2 · vendored Odoo Pro embedded jar 漂移 → M9 同步
- R3 · 跨端错误消息文本不一致 → A2-1 用错误码而非文本作 parity 校验维度
- R4 · `findJdbcQueryColumnByName` 现有调用点是否所有都该切到 strict？metadata 路径可能仍要旧行为 → M3.4 grep 时按调用语义判断

## 决策记录

- 2026-04-28：确定 Path A 严格化（用户决策）
- 2026-04-28：确定双端目标版本 Python v1.7 + Java 8.4.0.beta（用户决策）
- 2026-04-28：确定双端契约一致以错误码（`COLUMN_FIELD_NOT_FOUND` 前缀）为准，错误消息文本可有本地化差异

## Self-Check 区块（Java 侧完成 · 2026-04-28）

- [x] 需求或 bug 范围按预期实现：`SchemaAwareFieldValidationStep` 在 `validateField` 入口加 `isBareDimensionReference` 检查；命中即抛 `COLUMN_FIELD_NOT_FOUND` + 双 hint（`$caption` + `$id`）
- [x] 非目标未被意外扩大：未触碰 SQL 生成 / column loop / `findJdbcQueryColumnByName`；未引入新 `findJdbcQueryColumnByNameStrict` 方法（改造点位收敛在校验层即可）
- [x] 改动代码路径已记录：
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/SchemaAwareFieldValidationStep.java`（新增 `isBareDimensionReference` + `rejectBareDimension` 私有方法 · `validateField` 入口加判定）
- [x] 自检结论：`self-check-only`（小幅治理收紧 · 1855 passed 零 regression）
- [x] 测试状态：**pass** · sqlite lane 1855 passed / 0 failures / 1 skipped + 新增 3 tests（T1/T3/T5）全绿
- [x] 文档 / 后续项已记录：本 progress + Follow-up 列表

## Follow-ups（非阻断 8.4.0.beta acceptance · 后续 minor 跟踪）

- **FU-1**（P2）· user-alias 透传修复（Python T4 ★）· 改造点：SQL gen 层让用户在 `dim$attr AS userAlias` 形态下指定的 alias 覆盖 TM dim.alias。涉及 `findJdbcQueryColumnByName` 调用点 + DbQueryColumn caption 返回。Python 端已实现（`v1.7`）
- **FU-2**（P3）· `dim AS alias` 形态显式拒绝。当前 Java 路径下 inline parser 不识别该形态，落入 schemaFields 不命中分支 → 触发 `INVALID_QUERY_FIELD` 而非 `COLUMN_FIELD_NOT_FOUND`。可在 inline parser 后追加 fail-loud 区分

## 关联文档

- 需求：`docs/8.4.0.beta/P0-v13引擎收紧裸dimension引用-需求.md`
- backlog 起源：`foggy-data-mcp-bridge-python/docs/backlog/B-03-v13-engine-bare-dimension-tightening.md`
- Python 端镜像需求：`foggy-data-mcp-bridge-python/docs/v1.7/P0-v13引擎收紧裸dimension引用-需求.md`
- Python 端 progress：`foggy-data-mcp-bridge-python/docs/v1.7/P0-v13引擎收紧裸dimension引用-progress.md`
- 上游触发：G5 PR-P2 调试期复盘（commit `cf2ba9b` → `352a8bb`）
