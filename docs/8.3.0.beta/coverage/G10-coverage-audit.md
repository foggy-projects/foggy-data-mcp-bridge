---
audit_scope: feature
audit_mode: pre-acceptance-check
version: 8.3.0.beta
target: G10 · Compose 引擎前置改造（plan-aware 架构）
status: reviewed
conclusion: ready-with-gaps
reviewed_by: execution-agent
reviewed_at: 2026-04-27
follow_up_required: yes
---

# Test Coverage Audit · G10

## Background

G10（Compose 引擎前置改造）是 G5 Phase 2 / G11 / G12 三个下游 gap 的**单一硬前置**。spec v2 拆为 4 项 all-or-nothing 改造，由 `foggy.compose.g10.enabled` feature flag（默认 `false`）控制：

1. `SchemaDerivation` 允许 join 输出携带歧义列
2. `OutputSchema` / `ColumnSpec` 保留 plan provenance
3. `ComposePlanner` plan-aware 编译
4. Compose 层独立 `ComposePlanAwarePermissionValidator`

实施跨双仓共 12 个 commit（Java 7 / Python 5），含每 PR 后的 simplify polish。本审计在 `foggy-acceptance-signoff` 之前执行，盘点测试证据是否承接 spec §9 的 6 项验收标准。

## Audit Basis

| 类别 | 路径 |
|------|------|
| Spec v2 | `docs/8.3.0.beta/P0-Compose-引擎前置改造-plan-aware架构设计.md` |
| Gap tracker G10 行 | `docs/8.3.0.beta/compose-query-manuals-gap-tracker.md` (line 44) |
| Java commits | `0c60914` PR1 · `453cf03` PR2 · `c16136a` PR2 polish · `427bc09` PR3 · `c9be76e` PR3 polish · `be53fae` PR4 · `4f80b6d` PR4 polish |
| Python commits | `e8fcc88` PR5.1 · `e15dba5` PR5.2 · `b391cbf` PR5.3 · `1b2e770` PR5.4 · `b54f0a6` PR5 polish |
| Java lane | `foggy-dataset-model` sqlite profile · 1809 passed / 0 failures（surefire reports `2026-04-27T14:41`） |
| Python lane | `pytest -q` 全仓 · 3176 passed / 0 failures |

## Coverage Matrix

按 spec §9 的 6 行验收标准 × 双端测试覆盖逐项映射。

| § | 验收项 | 风险 | unit | integration | manual | 证据位置 | 结论 |
|---|--------|------|------|-------------|--------|----------|------|
| §9.1 | **现有功能零回归** | critical | ✅ | ✅ | — | Java sqlite lane 1809 passed（含原 ComposePlanner / SchemaDerivation 既有用例）/ Python 3176 passed · 所有 G10 unit 显式覆盖 `g10_enabled=false` 分支 | covered |
| §9.2 | **改造 #1 单元** · SchemaDerivation 歧义 + OutputSchema lookup API | critical | ✅ | — | — | Java: `SchemaDerivationG10JoinTest` (8) + `OutputSchemaLookupApiTest` (16) = **24** · Python: `test_schema_derivation_g10_join.py` (8) + `test_output_schema_lookup_api.py` (16) = **24** | covered |
| §9.3 | **改造 #2 单元** · PlanId + ColumnSpec.planProvenance | critical | ✅ | — | — | Java: `PlanIdTest` (15) + `ColumnSpecPlanProvenanceTest` (9) = **24** · Python: `test_plan_id.py` (14) + `test_column_spec_plan_provenance.py` (8) = **22** | covered |
| §9.4 | **改造 #3 单元** · ComposePlanner plan-aware 编译 | major | ✅ | — | — | Java: `PlanAwareCompileExpressionTest` (10) + `PlanAwareLoweringTest` (5) = **15** · Python: `test_plan_alias_map.py` (4) | covered |
| §9.5 | **改造 #4 单元** · `ComposePlanAwarePermissionValidator` | critical | ✅ | — | — | Java: `PlanFieldAccessContextTest` (7) + `ComposePlanAwarePermissionValidatorTest` (13) = **20** · Python: `test_plan_field_access_context.py` (7) + `test_plan_aware_permission_validator.py` (14) = **21** | covered |
| §9.6 | **plan-aware 编译真实 SQL 数据比对 ≥3 集成** | critical | — | ❌ | — | 无：grep `queryFacade.queryModelData` × `g10` 在 `src/test/java/` 与 `tests/integration/` 均无匹配 | **not-covered** |
| §9.7 | **plan-routed 权限真实 SQL 数据比对 ≥2 集成** | critical | — | ❌ | — | 同上 | **not-covered** |
| §9.8 | **双端 parity** | major | ✅ | — | — | Java/Python 测试文件结构 1:1 镜像（PlanId / ColumnSpec / OutputSchema lookup / SchemaDerivation join / ComposePlanner alias map / PlanFieldAccessContext / Validator）；命名差异限于语言习惯（Java `SchemaDerivationG10JoinTest` ↔ Python `test_schema_derivation_g10_join.py`） | covered |
| §9.9 | **Feature flag 行为** · CI 矩阵覆盖 | major | ✅ | — | — | 双端 unit 中所有 G10 测试类显式 `override_g10_enabled(True/False)` / `setG10Enabled(true/false)`；Python 端 `tests/compose/conftest.py` autouse `_clear_g10_override` fixture 保证默认状态隔离 | partially-covered |

> §9.9 标 partially-covered 而非 covered：spec 要求"CI 矩阵同时跑 flag=true / false 两个矩阵"。当前 unit 内显式覆盖了两种 flag 状态，但 CI 配置层没有真正跑两次 lane（lane 配置默认 flag=false，flag=true 的 lane 矩阵未配置）。这属于 CI infrastructure gap，非测试代码 gap。

## Evidence Summary

**Java 端**（8 个新测试文件 · 83 @Test）：
- `foggy-dataset-model/src/test/java/.../engine/compose/plan/PlanIdTest.java` (15)
- `foggy-dataset-model/src/test/java/.../engine/compose/schema/ColumnSpecPlanProvenanceTest.java` (9)
- `foggy-dataset-model/src/test/java/.../engine/compose/schema/SchemaDerivationG10JoinTest.java` (8)
- `foggy-dataset-model/src/test/java/.../engine/compose/schema/OutputSchemaLookupApiTest.java` (16)
- `foggy-dataset-model/src/test/java/.../engine/compose/compilation/PlanAwareCompileExpressionTest.java` (10)
- `foggy-dataset-model/src/test/java/.../engine/compose/compilation/PlanAwareLoweringTest.java` (5)
- `foggy-dataset-model/src/test/java/.../engine/compose/security/PlanFieldAccessContextTest.java` (7)
- `foggy-dataset-model/src/test/java/.../engine/compose/security/ComposePlanAwarePermissionValidatorTest.java` (13)

**Python 端**（7 个新测试文件 + 1 conftest · 71 测试函数）：
- `tests/compose/plan/test_plan_id.py` (14)
- `tests/compose/schema/test_column_spec_plan_provenance.py` (8)
- `tests/compose/schema/test_output_schema_lookup_api.py` (16)
- `tests/compose/schema/test_schema_derivation_g10_join.py` (8)
- `tests/compose/compilation/test_plan_alias_map.py` (4)
- `tests/compose/security/test_plan_field_access_context.py` (7)
- `tests/compose/security/test_plan_aware_permission_validator.py` (14)
- `tests/compose/conftest.py` (autouse fixture · 不计 test count)

**总计**：Java 83 @Test + Python 71 test fn = **154 个新增 G10 测试用例**，覆盖 spec §9 第 1-5 + 8-9 行；第 6-7 行（真实 SQL 集成测试）零覆盖。

## Gaps

### Gap-1（critical · 显式 deferred）· 真实 SQL 集成测试 ≥3 + ≥2

**对应 spec**：§9 第 6-7 行 + CLAUDE.md "集成测试规范：真实 SQL 数据比对" 强制条款

**现状**：
- spec §9 要求 plan-aware 编译有 ≥3 个真实 SQL 数据比对集成测试用例（join 歧义列消歧 / 派生层 plan-qualified / 多 join 嵌套）
- spec §9 要求 plan-routed 权限有 ≥2 个真实 SQL 数据比对集成测试用例（正向 + 反向 + bare field 跨 plan）
- 现有测试均为 unit 级（schema / 编译产物字符串 / 验证器调用），未通过 `queryFacade.queryModelData()` / Python `query_facade` 走真实查询比对

**deferred 原因**（PR3 / PR4 commit message 显式说明）：

> 真实 SQL 集成测试需要 `from() → derive_schema → ComposePlanner → execute_sql` 全链路 flag=true 端到端可观察。当前 `dsl()` / `from()` 的 F5 plan-qualified 列入口尚未落地（依赖 G5 Phase 2，Phase 2 反过来阻塞于 G10 完成）。Validator 的 public `validate(plan, schema, ctx)` 入口在 F5 落地前**根本无法被业务路径触达** —— 只能用单元直调测试覆盖。集成测试 moves to follow-up when full pipeline observable through dsl/from end-to-end with the flag on。

**风险评估**：
- 生产暴露面：flag 默认 `false`，G10 路径在生产为零暴露 → 风险**有限**
- Validator 公共入口在 F5 落地前为 dead code path → 集成测试**当前无法构造**（构造需要的入口尚未存在）
- 单元测试覆盖了所有 4 项改造的契约边界 + flag 双状态 → 核心契约**已锁**
- spec §9 第 1 行（零回归）由 1809 + 3176 测试基线背书 → 现有功能**已保护**

**缓解**：在 G5 Phase 2 (F5 columns) 实施时**强制**捎带补 ≥3 + ≥2 集成测试作为该批次的验收前置 → 见后续"Recommended Next Skills"。

### Gap-2（major · CI infra）· flag=true / false 两个矩阵 lane 未配置

**对应 spec**：§9 第 9 行（feature flag CI 矩阵）

**现状**：当前 CI lane 默认 `g10_enabled=false`。flag=true lane 由测试代码内通过 `override_g10_enabled(true)` 覆盖；CI 配置层未配置第二条 lane 用 `-Dfoggy.compose.g10.enabled=true` 跑全量。

**风险评估**：单元层已显式两种 flag 都覆盖，functional regression 已被锁住。lane 矩阵真正的价值是**未被显式 override 的隐式 false 路径在 flag=true 下的回归**——这部分目前确实没有强制 sweep 保护。

**缓解**：flag-flip rollout 前补一次性 lane 矩阵跑（`mvn test -Dg10=true && mvn test -Dg10=false`），不强制纳入持续 CI（成本 vs 收益）。

### Gap-3（minor · 文档）· G10 acceptance / progress 文档缺失

**现状**：本批 4 PR 实施未走传统的 progress doc + acceptance doc 流程，依赖 commit message + spec v2 + 本 audit 的链条。后续 spec 演进若有人回查"G10 是怎么交付的"，需要从 git log 反推。

**缓解**：补一份 `docs/8.3.0.beta/acceptance/G10-acceptance.md` 作为正式签收记录（本审计后由 `foggy-acceptance-signoff` 产出）。

## Recommended Next Skills

按以下顺序：

1. **`foggy-acceptance-signoff`**（立即）· 输出 `docs/8.3.0.beta/acceptance/G10-acceptance.md` · 推荐 decision 为 **`accepted-with-risks`**：
   - 单元 + parity + flag 双状态覆盖已达标
   - 集成测试 gap 属"系统结构使然"（公共入口尚未存在）非"开发遗漏"
   - 下游 G5 Phase 2 强制承接补集成测试
   - 与 REQ-FORMULA-EXTEND `accepted-with-risks` 模式同源
2. **`integration-test`**（与 G5 Phase 2 同批次）· 在 F5 plan-qualified columns 入口落地后，按 spec §9 补 ≥3 plan-aware compile + ≥2 plan-routed permission 真实 SQL 用例；不作为 G10 阻断
3. **`foggy-versioned-doc-tracking`**（可选）· 同步把 G10 在 gap tracker 中的状态从 `in-review` 翻到 `closed-with-followup`

如果用户希望**不带风险地签收**（decision `accepted` 而非 `accepted-with-risks`），则返回 **`needs-more-tests`** 路径：
- 先实现 G5 Phase 2 (F5 columns) 最小可行入口
- 再补 ≥3 + ≥2 集成测试
- 然后才走 `foggy-acceptance-signoff`
- 工程日重估：+5-7 工程日（G5 Phase 2 v2 spec 的 §10.1 已估）

## Conclusion

**`ready-with-gaps`** —— 主体证据齐备，单元层 154 个 G10 测试用例 + 双端 lane 全绿（1809 + 3176）+ 双端 parity + flag 双状态显式覆盖，spec §9 中 7/9 项 covered。

**两类显式 gap**：

- **Gap-1**（critical）真实 SQL 集成测试 ≥3 + ≥2 因公共入口未存在而 deferred（属系统使然，非开发遗漏）
- **Gap-2**（major）CI flag=true 矩阵 lane 未配置（属 CI infra，非测试代码缺失）
- **Gap-3**（minor）正式 acceptance doc 由后续步骤承接

**判定路径**：

- 若后续按 `accepted-with-risks` 签收（推荐）→ 触发 `foggy-acceptance-signoff` 立即
- 若坚持 `accepted` 无风险签收 → 退回 `needs-more-tests`，先实施 G5 Phase 2 + 集成测试

**强制 follow-up**：G5 Phase 2 实施 PR 必须在 acceptance basis 中**显式继承** Gap-1 的 ≥3 + ≥2 集成测试要求；不允许 G5 Phase 2 自行 deferred。
