---
acceptance_scope: feature
version: 8.3.0.beta
target: G10 · Compose 引擎前置改造（plan-aware 架构）
doc_role: acceptance-record
doc_purpose: 记录 G10 4 项 all-or-nothing 架构改造（schema 歧义 / plan provenance / plan-aware 编译 / plan-aware 权限校验）跨双仓的功能级正式验收结论与证据摘要
status: signed-off
decision: accepted-with-risks
signed_off_by: user
signed_off_at: 2026-04-27
reviewed_by: execution-agent
blocking_items: []
follow_up_required: yes
evidence_count: 12
---

# Feature Acceptance · G10

## Document Purpose

- doc_type: acceptance
- intended_for: signoff-owner / reviewer / G5 Phase 2 实施 owner
- purpose: 记录 G10（Compose 引擎 plan-aware 4 项前置改造）的正式验收结论 + 证据摘要 + Gap-1/2 follow-up 承接

## Background

- **Version**: 8.3.0.beta
- **Target**: G10（gap tracker `<a id="g10">`）
- **Cross-repo Owners**:
  - Java: `foggy-data-mcp-bridge-wt-dev-compose` 工作树（branch `dev-compose`）
  - Python: `foggy-data-mcp-bridge-python` 仓（branch `main`）
- **Goal**: 解锁 G5 Phase 2 (F5 columns) / G11 (slice F5) / G12 (groupBy/orderBy F5) 的硬阻塞——为 Compose 引擎补 4 项 plan-aware 架构能力（schema 派生 / OutputSchema 查询 / SQL 编译 / 权限路由）；feature flag `foggy.compose.g10.enabled` all-or-nothing 落地，默认 `false`，flag-off 路径零行为变化

## Acceptance Basis

| 类别 | 路径 |
|------|------|
| Spec v2 | `docs/8.3.0.beta/P0-Compose-引擎前置改造-plan-aware架构设计.md` |
| Gap tracker G10 行 | `docs/8.3.0.beta/compose-query-manuals-gap-tracker.md` (`<a id="g10">`) |
| Coverage audit | `docs/8.3.0.beta/coverage/G10-coverage-audit.md`（conclusion `ready-with-gaps`） |
| Java commits | `0c60914` PR1 / `453cf03` PR2 / `c16136a` PR2 polish / `427bc09` PR3 / `c9be76e` PR3 polish / `be53fae` PR4 / `4f80b6d` PR4 polish |
| Python commits | `e8fcc88` PR5.1 / `e15dba5` PR5.2 / `b391cbf` PR5.3 / `1b2e770` PR5.4 / `b54f0a6` PR5 polish |
| Java lane | `mvn test -pl foggy-dataset-model -P!multi-db` · sqlite lane **1809 passed / 0 failures** · surefire-reports `2026-04-27T14:41` |
| Python lane | `pytest -q` 全仓 · **3176 passed / 0 failures** |
| Spec §9 验收对照 | 见下方 Checklist |

## Checklist · spec §9 验收 9 项

- [x] **§9.1 现有功能零回归**：双端 lane 全绿（Java 1809 / Python 3176），所有原有 ComposePlanner / SchemaDerivation / OutputSchema 测试在 `g10_enabled=false` 默认路径下通过；G10 unit 显式覆盖 flag 双状态
- [x] **§9.2 改造 #1 单元** · `SchemaDerivation` 歧义列 + `OutputSchema` lookup API：Java `SchemaDerivationG10JoinTest`(8) + `OutputSchemaLookupApiTest`(16) = 24 / Python `test_schema_derivation_g10_join.py`(8) + `test_output_schema_lookup_api.py`(16) = 24
- [x] **§9.3 改造 #2 单元** · `PlanId` + `ColumnSpec.planProvenance`：Java `PlanIdTest`(15) + `ColumnSpecPlanProvenanceTest`(9) = 24 / Python `test_plan_id.py`(14) + `test_column_spec_plan_provenance.py`(8) = 22
- [x] **§9.4 改造 #3 单元** · `ComposePlanner` plan-aware 编译：Java `PlanAwareCompileExpressionTest`(10) + `PlanAwareLoweringTest`(5) = 15 / Python `test_plan_alias_map.py`(4)
- [x] **§9.5 改造 #4 单元** · `ComposePlanAwarePermissionValidator`：Java `PlanFieldAccessContextTest`(7) + `ComposePlanAwarePermissionValidatorTest`(13) = 20 / Python `test_plan_field_access_context.py`(7) + `test_plan_aware_permission_validator.py`(14) = 21
- [ ] **§9.6 plan-aware 编译真实 SQL 数据比对 ≥3 集成** · **deferred** → Gap-1（见 Risks）
- [ ] **§9.7 plan-routed 权限真实 SQL 数据比对 ≥2 集成** · **deferred** → Gap-1（见 Risks）
- [x] **§9.8 双端 parity**：测试文件 1:1 镜像（PlanId / ColumnSpec / OutputSchema lookup / SchemaDerivation join / ComposePlanner alias map / PlanFieldAccessContext / Validator）；契约语义双端一致；Python `tests/compose/conftest.py` 提供 autouse `_clear_g10_override` fixture
- [~] **§9.9 Feature flag 行为** · partially-covered：单元层显式覆盖 flag=true / false 两种状态；CI 配置层未配置第二条 lane 用 `-Dfoggy.compose.g10.enabled=true` 跑全量 → Gap-2（见 Risks）

## Evidence

1. **Spec v2** — `docs/8.3.0.beta/P0-Compose-引擎前置改造-plan-aware架构设计.md`
2. **Coverage audit** — `docs/8.3.0.beta/coverage/G10-coverage-audit.md`（154 单元 / 7 of 9 项 covered / 2 项 deferred-with-justification）
3. **Java 单元** · 8 文件 / 83 @Test：
   - `engine/compose/plan/PlanIdTest.java` (15)
   - `engine/compose/schema/ColumnSpecPlanProvenanceTest.java` (9)
   - `engine/compose/schema/SchemaDerivationG10JoinTest.java` (8)
   - `engine/compose/schema/OutputSchemaLookupApiTest.java` (16)
   - `engine/compose/compilation/PlanAwareCompileExpressionTest.java` (10)
   - `engine/compose/compilation/PlanAwareLoweringTest.java` (5)
   - `engine/compose/security/PlanFieldAccessContextTest.java` (7)
   - `engine/compose/security/ComposePlanAwarePermissionValidatorTest.java` (13)
4. **Python 单元** · 7 文件 + 1 conftest / 71 测试函数：
   - `tests/compose/plan/test_plan_id.py` (14)
   - `tests/compose/schema/test_column_spec_plan_provenance.py` (8)
   - `tests/compose/schema/test_output_schema_lookup_api.py` (16)
   - `tests/compose/schema/test_schema_derivation_g10_join.py` (8)
   - `tests/compose/compilation/test_plan_alias_map.py` (4)
   - `tests/compose/security/test_plan_field_access_context.py` (7)
   - `tests/compose/security/test_plan_aware_permission_validator.py` (14)
   - `tests/compose/conftest.py`（autouse `_clear_g10_override`）
5. **Java rerun command**：`mvn test -pl foggy-dataset-model -P!multi-db -Dtest='*PlanId*,*PlanProvenance*,*Lookup*,*G10Join*,*PlanAware*,*PlanField*'`
6. **Python rerun command**：`pytest -q tests/compose/`
7. **Java sqlite lane** · 1809 passed / 0 failures
8. **Python lane** · 3176 passed / 0 failures
9. **Cross-repo parity** · 双端测试 1:1 镜像（命名差异限于语言习惯）
10. **Flag 默认值** · `ComposeFeatureFlags.g10Enabled()` Java / `feature_flags.g10_enabled()` Python · 默认 `false` 已通过单元验证
11. **Zero behavior change**（flag-off 路径）· spec §10.2 PR1 真零行为变化原则 + simplify polish 后的 reuse/quality/efficiency 三方审查
12. **Gap tracker 维护记录** · `2026-04-27` 行 `implementation done` 已落

## Failed Items

无 critical 失败项。spec §9 中的两项 deferred 均属"系统结构使然"而非"开发遗漏"——见 Risks 章节。

## Risks / Open Items

### Gap-1（critical · deferred-with-justification）真实 SQL 集成测试 ≥3 + ≥2

**对应 spec**：§9 第 6-7 行 + CLAUDE.md 「集成测试规范：真实 SQL 数据比对」强制条款

**风险**：spec 要求 ≥3 plan-aware 编译 + ≥2 plan-routed 权限的真实 SQL 数据比对集成测试用例；当前仅 unit 级覆盖。

**deferred 理由**（已在 PR3/PR4 commit message 显式记录）：

> 真实 SQL 集成测试需要 `from() → derive_schema → ComposePlanner → execute_sql` 全链路 flag=true 端到端可观察。`ComposePlanAwarePermissionValidator.validate(plan, schema, ctx)` 公共入口在 G5 Phase 2 (F5 columns) 落地前**根本无法被业务路径触达**——属于 dead code path。集成测试 moves to follow-up when full pipeline observable through dsl/from end-to-end with the flag on。

**为何可以 `accepted-with-risks` 通过**：

1. flag 默认 `false` → 生产路径**零暴露**
2. 154 单元 + 双端 parity + flag 双状态显式覆盖 → 4 项改造的契约边界**已锁**
3. spec §9.1 零回归由双端 lane 1809 / 3176 全绿背书
4. `accepted-with-risks` 与 REQ-FORMULA-EXTEND 同源（参 `docs/v1.4/acceptance/REQ-FORMULA-EXTEND-non-aggregation-functions-acceptance.md`），均属"主体证据齐 + 集成补在下游批次"模式

**强制 follow-up**（非阻断 G10，但**阻断 G5 Phase 2 单独签收**）：

- **FU-1**（critical）：G5 Phase 2 (F5 columns) 实施 PR 必须**显式继承** Gap-1 的 ≥3 + ≥2 集成测试要求作为该批次的验收前置；不允许 G5 Phase 2 自行 deferred
- 集成测试落点建议：
  - Java：`foggy-dataset-model/src/test/java/.../engine/compose/integration/G10PlanAwareIntegrationTest.java`（继承 `EcommerceTestSupport`，sqlite profile）
  - Python：`tests/integration/test_g10_plan_aware_integration.py`（query_facade fixture）
- 集成测试用例 ≥3 plan-aware：
  - (a) join 歧义列经 `{plan, field, as}` 消歧后真实查询 vs 等价原生 `SELECT a.name, b.name` SQL
  - (b) 派生层 plan-qualified 引用真实结果
  - (c) 多 join 嵌套场景
- 集成测试用例 ≥2 plan-routed 权限：
  - (a) customers.name (whitelisted) + orders.name (denied) → 反向拒绝真实查询
  - (b) bare field 跨 plan 唯一解析路由 binding 校验

### Gap-2（major · CI infra）flag=true / false 双 lane 矩阵未配置

**对应 spec**：§9 第 9 行（feature flag CI 矩阵）

**风险**：CI lane 默认 `g10_enabled=false`；flag=true lane 由测试代码内显式 override 覆盖；CI 配置层未配置第二条 lane 用 `-Dfoggy.compose.g10.enabled=true` 跑全量。

**为何可以 `accepted-with-risks` 通过**：单元层已显式两种 flag 都覆盖，functional regression 已被锁住；lane 矩阵的边际价值是**未被显式 override 的隐式 false 路径在 flag=true 下的回归**——这部分目前确实未强制 sweep 保护，但 flag 默认 `false` 限制了暴露面。

**强制 follow-up**：

- **FU-2**（major）：flag-flip rollout 前补一次性 lane 矩阵跑：
  - Java：`mvn test -Dfoggy.compose.g10.enabled=true && mvn test`（双 sweep，记录 diff）
  - Python：`FOGGY_COMPOSE_G10_ENABLED=true pytest -q && pytest -q`（同上）
- 不强制纳入持续 CI（成本 vs 收益）；纳入 `flag-flip-rollout-playbook.md`

### Open Items（非 G10 阻断）

- **OI-1**：spec §9.4（改造 #3）Python 端单元数（4）显著少于 Java（15）。原因是 Python 架构上 `PlanColumnRef` 在 `select()` 时已经被 `to_column_expr()` 扁平化为 bare 字符串（参 `tests/compose/compilation/test_plan_alias_map.py` 文件级 docstring），SQL 编译路径在 G5 Phase 2 (F5) 之前 plan-aware 不会被触达；Python 仅需验证 `_register_plan_alias` 基础设施 + flag 短路即可。文档化在测试文件 docstring 中。
- **OI-2**：Java `ComposePlanAwarePermissionValidator.validate(...)` 在 F5 入口落地前为 dead code path；公共入口仅由单元直调测试（13 个）覆盖。Python 同。属预期，与 OI-1 同源。

## Final Decision

**`accepted-with-risks`**。

理由：
1. spec §9 验收 9 项中 7 项 covered（含全部 critical 单元 + 双端 parity + flag 行为）
2. 2 项 deferred（§9.6 + §9.7 集成测试）属系统使然——公共入口在 G5 Phase 2 落地前为 dead code path
3. flag 默认 `false`，G10 路径生产暴露面为零
4. Gap-1 的 ≥3 + ≥2 集成测试要求**强制承接**至 G5 Phase 2 同批次（FU-1）作为该批次的验收前置
5. Gap-2 通过 flag-flip rollout playbook 单次 lane 矩阵 sweep 缓解（FU-2）
6. 与 REQ-FORMULA-EXTEND `accepted-with-risks` 模式同源

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: user
- signed_off_at: 2026-04-27
- acceptance_record: `docs/8.3.0.beta/acceptance/G10-ComposeEngine-PlanAware-acceptance.md`
- blocking_items: none
- follow_up_required: yes（FU-1 / FU-2 见 Risks）

## Follow-up Tracking

| ID | 优先级 | 范围 | 承接批次 | 阻断关系 |
|----|--------|------|---------|---------|
| FU-1 | critical | spec §9.6 + §9.7 真实 SQL 集成测试 ≥3 + ≥2 | **G5 Phase 2** (F5 columns) 实施批次 | ✅ **已交付** 2026-04-28 — Java `F5ColumnObjectIntegrationTest` 5 tests（`56a124e`）+ Python `tests/compose/compilation/test_f5_integration.py` 5 tests（`cf2ba9b`）；双端均超额覆盖（≥3+≥2） |
| FU-2 | major | flag=true / false lane 矩阵单次 sweep | flag-flip rollout 前 | 不阻断 G10 acceptance；**阻断 flag 默认翻转** |
| FU-3 | minor | flag-flip rollout playbook 起草 | 本验收同批 | 不阻断（本验收同批产出） |

## 维护记录

| 日期 | 操作 | 备注 |
|------|------|------|
| 2026-04-27 | 创建 ready-for-signoff 草稿 | 基于 coverage audit 结论 `ready-with-gaps`；recommended decision `accepted-with-risks`；待用户最终批复 |
| 2026-04-27 | 用户签收 → `signed-off` | decision `accepted-with-risks`；FU-1（G5 Phase 2 集成测试 ≥3+≥2）+ FU-2（lane sweep）+ FU-3（playbook）作为非阻断 follow-up 跟踪；G5 Phase 2 / G11 / G12 全部解锁 |
| 2026-04-28 | FU-1 关闭 | G5 Phase 2 (F5 columns) PR-J1 / PR-J2 / PR-P1 / PR-P2 双端落盘；Java `F5ColumnObjectIntegrationTest` 5 tests + Python `test_f5_integration.py` 5 tests；spec §9.6（plan-aware compile ≥3）+ §9.7（plan-routed permission ≥2）跨端均交付。FU-2 / FU-3 仍跟踪中 |
