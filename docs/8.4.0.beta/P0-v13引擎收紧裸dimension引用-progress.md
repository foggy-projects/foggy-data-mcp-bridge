---
type: progress
version: 8.4.0.beta
req_id: P0-v13-bare-dimension-tightening
status: in-design
priority: P0
blocking_for: []
python_sync_required: yes  # 同步在 Python v1.7
python_side_status: not-started
java_side_status: not-started
odoo_pro_side_status: not-started
acceptance_record: docs/8.4.0.beta/acceptance/P0-v13-bare-dimension-tightening-acceptance.md  # M11 落盘
accepted_at: null
---

# P0 v1.3 引擎收紧裸 dimension 引用 (Java) — Progress

> 状态口径：`not-started` / `in-design` / `in-progress` / `blocked` / `ready-for-review` / `accepted` / `rejected`

## 里程碑

| # | 阶段 | 状态 | 日期 | 备注 |
|---|------|------|------|------|
| M0 | 需求立项 | `accepted` | 2026-04-28 | 本需求文档 + Python 同步需求落盘 |
| M1 | 跨端行为审计 + 行为对照表 | `not-started` | — | 写临时 Java 单测实测六种输入；产物：行为对照表完整版（Java 列） |
| M2 | Python 端实施（v1.7） | `not-started` | — | 跨仓追踪：`foggy-data-mcp-bridge-python/docs/v1.7/P0-v13引擎收紧裸dimension引用-progress.md` |
| M3 | Java 端 `findJdbcQueryColumnByNameStrict` 实装 | `not-started` | — | M3.2 |
| M4 | Java 端 `SemanticQueryServiceV3Impl` 列循环改造 | `not-started` | — | M3.3 + M3.4 调用点 |
| M5 | 新增 Java 单测（T1-T10 镜像） | `not-started` | — | `SemanticQueryServiceV3StrictColumnResolutionTest.java` |
| M6 | Java 历史 fixture / 测试 grep 全仓 + 批量迁移 | `not-started` | — | grep `columns.*"<bare_dim_name>"`；逐个判定 |
| M7 | Java sqlite lane 全量回归 | `not-started` | — | `mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite`；期望 1809+ passed |
| M8 | F5 / parity / dialect 系列零回归 | `not-started` | — | `F5ColumnObjectIntegrationTest`、`FormulaParitySnapshotTest`、`DialectAwareFunctionExpTest` |
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

## Self-Check 区块（M3-M4 完成后填写）

- [ ] 需求或 bug 范围按预期实现
- [ ] 非目标未被意外扩大
- [ ] 改动代码路径已记录
- [ ] 自检结论：`self-check-only` / `needs-formal-quality-gate`
- [ ] 测试状态：pass / fail / not-run / N/A
- [ ] 文档 / 后续项已记录

## 关联文档

- 需求：`docs/8.4.0.beta/P0-v13引擎收紧裸dimension引用-需求.md`
- backlog 起源：`foggy-data-mcp-bridge-python/docs/backlog/B-03-v13-engine-bare-dimension-tightening.md`
- Python 端镜像需求：`foggy-data-mcp-bridge-python/docs/v1.7/P0-v13引擎收紧裸dimension引用-需求.md`
- Python 端 progress：`foggy-data-mcp-bridge-python/docs/v1.7/P0-v13引擎收紧裸dimension引用-progress.md`
- 上游触发：G5 PR-P2 调试期复盘（commit `cf2ba9b` → `352a8bb`）
