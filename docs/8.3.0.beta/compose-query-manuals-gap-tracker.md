# Compose Query 双手册缺口追踪 (Gap Tracker)

> **状态**：active · 持续维护
> **创建于**：2026-04-26
> **最近更新**：2026-04-27（G10 实施完成 · `implemented · ready-with-gaps`）

## 目的

Compose Query 双手册采用"**先骨架、后填能力**"策略：

- **Manual A · DSL 配置式手册**（`docs-site/zh/dataset-model/compose-query/dsl-manual.md`）
- **Manual B · 链式 API 手册**（`docs-site/zh/dataset-model/compose-query/api-manual.md`）

骨架先行可以暴露能力 gap，避免在 spec 阶段反复改章节结构。本文档是**所有 gap 的单一真相源**，防止后续补齐过程中遗忘。

## 同步规则

1. **每个 gap 必须在两本手册的对应章节中以 `🚧 待补：参考 G-N` 占位**——读者在手册里看到 🚧 时，按编号跳回这里查上下文
2. **gap 关闭时**：
   - 更新本表 `status: open → in-progress → closed`
   - 在对应手册章节中删除 🚧 标记并填入正式内容
   - 在 `Closure log` 章节追加一行（日期 / 编号 / closing PR or commit）
3. **新发现 gap 随时追加**，使用递增编号 G7、G8…，**永不复用编号**
4. **关联代码改动 / 测试 / PR**：在 `Evidence` 字段引用具体路径

## 等价模型回顾（决策 1）

| Layer | 含义 | 对等要求 |
|-------|------|---------|
| Layer 1 · 底层原语 | CTE / 派生 / Join / Union / 窗口函数 / groupBy / having / orderBy / 计算字段 | **严格对等**：A 能写的 B 必须能写，反之亦然 |
| Layer 2 · 高层语义快捷方式 | `comparison: "yoy"` / 滚动窗口 helper / 输出后缀规约 | **功能镜像**：两边都有等价能力，但**形态可不同** |

任何 Layer 1 不对等都视作 bug 或文档缺口；任何 Layer 2 缺口本表必须登记。

## 状态总览

| ID | 标题 | 阻塞章节 | 优先级 | 风格归属 | 状态 | 目标版本 |
|----|------|---------|--------|---------|------|---------|
| G1 | 链式 API 缺时间窗口语义层 | B §9 | P1 | 仅 B | open | 8.3.0.beta |
| G2 | DSL 配置缺完整 CTE/派生/Join/Union 语法 | A §5-§8 | P0 | 仅 A | **in-review**（spec **v4** 2026-04-26 · model polymorphic + combinator-only + `.union([...])` 数组重载） | 8.3.0.beta |
| G3 | 双侧缺底层窗口原语暴露 (lag/lead/rolling/over) | A §10 / B §10 | P1 | 双 | open | 8.3.0.beta |
| G4 | 输出后缀规约链式侧未继承 | B §11 | P1 | 仅 B | open | 8.3.0.beta |
| G5 | DSL **columns** F4 `{field, agg, as}` + F5 `{plan, field, as}` 后置消歧（v2 缩窄） | A §2 / A §4 / A §3.6（F5 解锁后置路径） | **P0**（LLM 自我修复硬需求） | 仅 A | **engine-implemented · user-gated**（F4 ✅ + F5 ✅ Java/Python 双端实施完成 · spec **Final** 2026-04-28 · `g10Enabled()` 默认 OFF → 用户级仍未开放 · F5 集成测试 Java 5 + Python 5 = G10 FU-1 ≥3+≥2 全部交付） | 8.3.0.beta |
| G10 | Compose 引擎前置改造（join 歧义 schema + plan provenance + plan-aware 编译 + plan-aware 权限校验子层） | G5 F5 / G11 / G12 全部硬前置 | **P0** | Java + Python 引擎 | **accepted-with-risks**（spec v2 · 双仓 12 commits · 154 单元 + 双端 lane 全绿 · 集成测试 ≥3+≥2 deferred → FU-1 G5 Phase 2 同批次承接 · audit `coverage/G10-coverage-audit.md` · acceptance `acceptance/G10-ComposeEngine-PlanAware-acceptance.md` · 2026-04-27 user 签收） | 8.3.0.beta |
| G11 | DSL `slice` F4/F5 支持（含 SliceShape 字段强转修复） | A §3 (slice) | P1 | 仅 A | open（依赖 G5 v2 + G10 落地） | 8.3.0.beta or later |
| G12 | DSL `groupBy` / `orderBy` F4/F5 支持（含 `List<String>` → `List<Object>` 类型迁移） | A §3 (groupBy/orderBy) | P2 | 仅 A | open（依赖 G5 v2 + G10 落地） | 8.3.0.beta or later |
| G6 | 计算字段在 timeWindow 上下文里的语义 | A §4 + §9 / B §4 + §9 | P2 | 双 | **spec-ready**（契约文档 2026-04-28 · 实现目标 8.5.0.beta） | 8.4.0.beta |
| G7 | DSL 与链式 API 是否互不依赖（架构验证） | 全文契约 | P0 | 元层 | **closed**（2026-04-26 · 结论 Clean · Level 1） | 8.3.0.beta |
| G8 | 移除链式 API 时的级别选择（Level 1 vs Level 2） | 全文契约 | P3 | 元层 | deferred（待未来 deprecation 决策时启动） | TBD |
| G9 | `withSubtotals` 字段已声明但未在 DslQueryFunction 处理 | A §1 / §3 (分页旁) | P3 | 仅 A | open（小 bug） | 8.3.0.beta or later |

> **更新规约**：状态值仅可为 `open` / `in-progress` / `closed` / `deferred`。

## 缺口详情

### G1 · 链式 API 缺时间窗口语义层

- **Layer**: Layer 2（高层语义快捷方式）
- **现状**：DSL 侧在 `docs/8.3.0.beta/P1-SemanticDSL-时间窗口能力设计.md` 中完整规范了 `timeWindow.{grain, comparison, value, targetMetrics}`；链式 API 侧**没有镜像 helper**
- **需要做的事**：
  1. 设计链式 API 的 timeWindow helper 形态（候选：`.timeWindow({...})` / `.compareTo("yoy", grain="month")` / 其他）
  2. 写入 8.3.0.beta 现有 timeWindow spec 的"链式 API 等价表达"章节（或独立 spec）
  3. Manual B §9 落稿
- **形态约束**：不要求和 DSL 字面对仗（决策 1），只要功能等价
- **关联**：
  - DSL 侧 spec：`docs/8.3.0.beta/P1-SemanticDSL-时间窗口能力设计.md`
  - 实现总控：`docs/v1.5/P1-SemanticDSL-timeWindow-总控设计.md`
  - Java 实施：`docs/v1.5/P1-timeWindow-impl-java.md`（OverClause / WindowColumn 已就绪）
- **Evidence**：（待补 spec PR 后填）

### G2 · DSL 配置缺完整 CTE / 派生 / Join / Union 语法

- **Layer**: Layer 1（底层原语 · **严格对等**）
- **现状**：8.2.0.beta `P0-ComposeQuery-CTE使用参考手册.md` 已规范链式 API 的派生 / Join / Union；当前 DSL 配置式（query-dsl.md + 8.3.0 timeWindow）**只覆盖单层查询和时间窗口包装**，没有规范 `dsl({source: prev})` / `dsl({join: [...]})` / `dsl({union: [...]})`
- **2026-04-26 增补观察**（写 Manual A §1 时发现）：
  - 测试脚本 `real_sql_join_scenario.js` 已使用 `dsl({source: joined, columns: [...]})` 形态
  - 测试脚本 `real_sql_join_scenario.js` 已使用 `salesByProduct.join(returnsByProduct, "inner", [{left, op, right}])` 形态
  - 这两种"已经能跑"的形态需要在 G2 spec 中正式收口（要么承认为正式契约，要么改造）
  - **建议**：spec 时优先承认现有形态作为基线，再补充配置式（`dsl({join: {...}})`）作为可选写法；保持向后兼容
- **需要做的事**：
  1. 补 DSL 配置式的派生 / Join / Union JSON 形态 spec
  2. 选址：建议放在 `docs/8.3.0.beta/` 新增一份 `P1-SemanticDSL-CTE-派生-Join-Union-语法.md`，与时间窗口 spec 平级
  3. Manual A §5-§8 同步落稿
- **形态参考**（讨论中，待 spec 确认）：
  ```javascript
  // 派生
  const derived = dsl({ source: basePlan, columns: [...], where: [...] });
  // Join
  const joined = base.join(other, "inner", [{ left: "x", op: "=", right: "y" }]);
  // 或: dsl({ join: { left: basePlan, right: otherPlan, type: "inner", on: [...] } });
  // Union
  const u = dsl({ union: [planA, planB], by: "name" /* or "position" */ });
  ```
- **关联**：
  - 链式侧 spec：`docs/8.2.0.beta/P0-ComposeQuery-CTE使用参考手册.md`
  - 链式实现：QueryPlan / DerivedQueryPlan / JoinPlan / UnionPlan 已 frozen
- **Evidence**：（待补）

### G3 · 双侧缺底层窗口原语暴露 (lag / lead / rolling / over)

- **Layer**: Layer 1（底层原语 · **严格对等**）
- **现状**：v1.5 P1 已实现 `OverClause / WindowColumn / WindowFrame` 内部 IR，但**两本手册都没暴露给用户**。当前用户层只有 timeWindow 的高层语义（`comparison: "yoy"`），无法表达自定义 partition / order / frame / 自定义聚合窗口
- **需要做的事**：
  1. 决定窗口原语在 DSL 里的形态：`dsl({ window: {func, args, partitionBy, orderBy, frame} })`？或列项级别 `{field, agg: "sum", over: {...}, as: "..."}`？
  2. 决定窗口原语在链式 API 里的形态：`base.over({partition, order, frame}).agg("sum", "salesAmount").as("...")`？
  3. 写入 8.3.0.beta 或 8.4.0.beta spec
  4. Manual A §10 + Manual B §10 落稿
- **关联**：
  - 实现：v1.5 Java OverClause / WindowColumn / WindowFrame
  - 4 方言降级规则：MySQL / PG / MSSQL / SQLite
- **Evidence**：（待补）

### G4 · 输出后缀规约链式侧未继承

- **Layer**: Layer 2（高层语义快捷方式）
- **现状**：8.3.0 P1 timeWindow spec 已定义后缀规约（`__prior` / `__diff` / `__ratio` / `__ytd` / `__mtd` / `__rolling_{N}{unit}`），仅适用于 DSL；链式 API 在 G1 落地时需明确：是沿用同一套后缀，还是允许用户自定义
- **建议**：链式 API 沿用同一套后缀作为默认，并允许 `.as("custom_name")` override
- **需要做的事**：
  1. 在 G1 spec 中明确链式后缀策略
  2. Manual B §11 落稿
- **关联**：DSL spec `docs/8.3.0.beta/P1-SemanticDSL-时间窗口能力设计.md` §3
- **Evidence**：（待补）

<a id="g5"></a>
### G5 · DSL **columns** F4 + F5 后置消歧（v2 缩窄）

- **Layer**: Layer 1（底层原语）
- **范围**：仅限 `columns` 字段中的 F4 / F5 对象形态（v1 → v2 缩窄）
- **状态**：`engine-implemented · user-gated`（2026-04-28）
- **关键依赖** ⭐：
  - G2 spec §3.4 规定 join 重名消歧**必须**有后置路径
  - **LLM 生成 join 链时极可能在源 plan 构造期就引入冲突列**——回归源头改写代价大、上下文消耗高
  - G5 必须提供**派生层后置消歧**通道，保留上游代码不变让 LLM 自我修复
- **v2 范围与依赖**：
  - **Phase 1 (F4 columns)** · ✅ 已落地 · 无 AST 改动（columns 已是 `List<Object>`）
  - **Phase 2 (F5 columns)** · ✅ 已落地 · 依赖 [G10](#g10) PR2/PR3/PR4 已签收
  - **slice / groupBy / orderBy** 已移交 [G11](#g11) / [G12](#g12)
- **实施摘要**（PR-J1/J2/P1/P2 · 2026-04-28）：
  - Java 侧（worktree `dev-compose`）：`d667c52` PR-J1（normalizer F5 sentinel + visibility）+ `56a124e` PR-J2（`F5ColumnObjectIntegrationTest` 5 tests · sqlite lane **1809+ passed**）+ `3b7a9e7` simplify polish
  - Python 侧（main）：`9973fb8` PR-P1（normalizer F5 flatten + collect_visible_plans）+ `cf2ba9b` PR-P2（`test_f5_integration` 5 tests · pytest **3202 passed**）+ `4703ba8` simplify polish
- **G10 acceptance FU-1 交付**：≥3 plan-aware compile + ≥2 plan-routed permission 在双端均已落盘（Java 5 + Python 5），FU-1 关闭
- **关键产物**：F5 形态 `{plan: <ref>, field, agg?, as?}` —— AST 层等价链式 API `customers.name.as(...)`，但通过对象引用绕开 Proxy
- **架构差异留痕**：Java `BaseModelPlan.columns: List<Object>` 携带 `PlanColumnRef` 直至 SQL 编译；Python 在 `column_normalizer` 解析期 flatten 为 F4 string，`OutputSchema.plan_provenance` 由 PR5.4 承接路由——契约由 G10 PR5 双端 parity 锁住
- **用户级开放门**：`ComposeFeatureFlags.g10Enabled()` 默认 `false` —— 引擎已就位，但 F5 用户级**仍未开放**；翻转决策门见 [G10-flag-flip-rollout-playbook.md](G10-flag-flip-rollout-playbook.md) C1-C4
- **关联**：
  - G2 spec §3.4（重名消歧 · 后置路径硬需求）
  - G10（引擎前置 · 已签收 `accepted-with-risks`）
  - 现有 DSL 文档：`docs-site/zh/dataset-model/tm-qm/query-dsl.md` §columns（仅短写 · F5 用户文档落稿待 `g10Enabled()` 翻转默认 ON 后再补）
- **Evidence**：
  - G5 spec **Final**：`docs/8.3.0.beta/P0-SemanticDSL-列项对象语法-后置消歧设计.md`
  - 实施落盘：见 §4.3 实施完成度小结

<a id="g10"></a>
### G10 · Compose 引擎前置改造（G5 F5 + G11 + G12 硬阻塞）

- **Layer**: Layer 0/1（架构）
- **优先级**：**P0**（多个下游 gap 阻塞于此）
- **状态**：`accepted-with-risks`（2026-04-27 · user 签收）
- **背景**：G5 v1 评审过程中代码核实暴露的 5 项架构问题，G10 spec v2 拆为 4 项 all-or-nothing 改造覆盖
- **实施摘要**：
  - **Java 仓** `dev-compose` 7 commits：`0c60914` PR1 / `453cf03` PR2 / `c16136a` PR2 polish / `427bc09` PR3 / `c9be76e` PR3 polish / `be53fae` PR4 / `4f80b6d` PR4 polish · sqlite lane **1809 passed**
  - **Python 仓** `main` 5 commits：`e8fcc88` PR5.1 / `e15dba5` PR5.2 / `b391cbf` PR5.3 / `1b2e770` PR5.4 / `b54f0a6` PR5 polish · pytest **3176 passed**
  - 4 项改造全部由 `foggy.compose.g10.enabled` flag（默认 `false`）控制，flag-off 路径零行为变化
- **覆盖审计**：`docs/8.3.0.beta/coverage/G10-coverage-audit.md`（conclusion: `ready-with-gaps`）
  - **154 个新增单元** 跨双仓（Java 83 / Python 71）+ 双端 1:1 parity + flag 双状态显式覆盖
  - spec §9 中 **7/9 项 covered**；2 项 deferred：
    - **Gap-1**（critical）·真实 SQL 集成测试 ≥3 plan-aware 编译 + ≥2 plan-routed 权限——deferred 至 **G5 Phase 2 同批次**（公共入口 `validate(plan, schema, ctx)` 在 F5 落地前为 dead code path，集成测试**当前无法构造**）
    - **Gap-2**（major）·CI flag=true / false 双 lane 矩阵未配置（属 CI infra）
- **下游解锁**：
  - G5 Phase 2 (F5 columns) — **可立即启动**，但**强制承接** Gap-1 的 ≥3 + ≥2 集成测试要求
  - G11 (slice F5) / G12 (groupBy/orderBy F5) — 待 G5 Phase 2 落地后启动
- **签收记录**：`docs/8.3.0.beta/acceptance/G10-ComposeEngine-PlanAware-acceptance.md`（decision: `accepted-with-risks` · 2026-04-27 user 签收 · evidence_count 12）
- **Follow-up（非阻断 G10）**：
  - **FU-1**（critical · ✅ **已交付** 2026-04-28）：G5 Phase 2 (F5 columns) 实施批次承接的 ≥3 plan-aware compile + ≥2 plan-routed permission 真实 SQL 集成测试 —— Java `F5ColumnObjectIntegrationTest`（5 tests · `56a124e`）+ Python `test_f5_integration.py`（5 tests · `cf2ba9b`），双端均超额覆盖（≥3+≥2）
  - **FU-2**（major）：flag-flip rollout 前补 `flag=true` lane 单次 sweep
  - **FU-3**（minor · 已交付）：flag-flip rollout playbook → `docs/8.3.0.beta/G10-flag-flip-rollout-playbook.md`（draft · C1-C4 决策门均未满足，当前不可执行）

<a id="g11"></a>
### G11 · DSL `slice` F4/F5 支持

- **Layer**: Layer 1
- **优先级**：P1
- **依赖**：G5 v2 落地 + G10 落地
- **现状**：`SliceShape.java:22-66` 中 `field` 字段经 `String.valueOf()` 强制转字符串，`{plan, field}` 对象会被 `toString()` 成乱码
- **范围**：
  - 修复 `SliceShape` 接受 `field` 为字符串或对象（含可选 `plan` 键）
  - slice item 形态扩展：`{plan?: <ref>, field, op, value}`
  - 与 G10 的 plan-routed 权限校验集成
- **触发条件**：G5 v2 + G10 落地后启动；以上未完成时本 gap 不开工

<a id="g12"></a>
### G12 · DSL `groupBy` / `orderBy` F4/F5 支持

- **Layer**: Layer 1
- **优先级**：P2
- **依赖**：G5 v2 落地 + G10 落地
- **现状**：
  - Java：`BaseModelPlan.java:24-25` / `DerivedQueryPlan.java:24-25` 中 `groupBy: List<String>` / `orderBy: List<String>`，`QueryPlan.validateStringList()` 强制非字符串拒绝
  - Python：`plan.py:635-636` `Tuple[str, ...]`
- **范围**：
  - AST 类型迁移：`List<String>` → `List<Object>` （或 `List<Union<String, Map>>`）
  - `validateStringList()` 重写为 `validateColumnRefList()`
  - F4 / F5 在 groupBy / orderBy 中的解析、编译、权限校验
- **触发条件**：G5 v2 + G10 落地后启动；优先级低于 G11（slice 后置过滤场景价值更高）

### G6 · 计算字段在 timeWindow 上下文里的语义

- **Layer**: Layer 2（联动场景）
- **状态**：`spec-ready`（2026-04-28 · 契约文档落盘）
- **现状**：契约文档定义了执行顺序、允许/禁止矩阵、4 个错误码、可引用列清单、正反例 JSON；实现目标 8.5.0.beta
- **优先级**：P2
- **语义结论**：
  1. timeWindow 先完成 base aggregation / rolling / cumulative / comparative 展开
  2. 后置 calculatedFields 作用于 timeWindow 最终输出列（仅 scalar row-level）
  3. `targetMetrics` 不允许引用 `request.calculatedFields.name`（循环依赖）
  4. 后置 calc fields 不允许 `agg / windowFrame / partitionBy / windowOrderBy`（S16 决策约束）
  5. 后置 calc fields 可引用所有 timeWindow 派生列（`__prior / __diff / __ratio / __rolling_* / __ytd / __mtd`）及维度列
- **错误码**：
  - `TIMEWINDOW_TARGET_CALCULATED_FIELD_UNSUPPORTED`
  - `TIMEWINDOW_POST_CALCULATED_FIELD_NOT_FOUND`
  - `TIMEWINDOW_POST_CALCULATED_FIELD_AGG_UNSUPPORTED`
  - `TIMEWINDOW_POST_CALCULATED_FIELD_WINDOW_UNSUPPORTED`
- **Python 对齐**：Python 侧 `TIMEWINDOW_CALCULATED_FIELDS_NOT_IMPLEMENTED` 应细化为上述 4 个错误码
- **Evidence**：`docs/8.4.0.beta/P2-timeWindow-calculatedFields-interaction-contract.md`

### G7 · DSL 与链式 API 是否互不依赖（架构验证）✅ closed

- **Layer**: 元层（影响整套手册的契约表达）
- **结论**：**Clean (Level 1)** —— DSL 与链式 API 在 IR 层完全独立
- **关闭日期**：2026-04-26
- **关闭依据**：Phase 0 代码勘察（worktree 范围）

**核心发现**：

1. **DSL 解析路径独立** — Java `DslQueryFunction.buildRequest()` / Python `dsl.py` 直接构造 `SemanticQueryRequest` 或 `BaseModelPlan / DerivedQueryPlan`，**完全不调用** `Query.from(...)` / `QueryFactory`
2. **timeWindow `comparison: "yoy"` 展开独立** — `TimeWindowInterceptor` (`@Order(-22)`, beforeQuery 阶段) 调用 `TimeWindowExpander.expandComparative()` + `buildComparativePlan()`，直接构造 `JoinPlan + DerivedQueryPlan` AST 节点，**不依赖任何链式 API helper**
3. **Layer 0 IR 纯净** — DSL 侧与 Query 侧无任何交叉 import；两者只通过共享的 `QueryPlan` AST 通信
4. **fsscript 沙箱注册了 3 个独立全局**：`{"from", "dsl", "Query"}`，互相不依赖

**关键证据**：
- Java：`foggy-dataset-model/.../dsl/DslQueryFunction.java:113-134`（DSL 入口）
- Java：`foggy-dataset-model/.../timewindow/TimeWindowInterceptor.java:138-144`（timeWindow 展开入口）
- Java：`foggy-dataset-model/.../engine/compose/QueryFactory.java:30-56`（链式入口，独立路径）
- Python：`foggy/dataset_model/compose/dsl.py:46-127`
- Python：`foggy/dataset_model/compose/query_factory.py:41-51`
- 测试脚本清单：worktree `src/test/resources/scripts/` 下 6 份脚本，3 份链式 / 3 份 DSL，**无混合**

**移除链式 API 的机械化清单（Level 1）**：
1. 删除 `QueryFactory.java` / `query_factory.py`
2. 从 fsscript 沙箱 `ScriptRuntime.ALLOWED_SCRIPT_GLOBALS` 中去掉 `"Query"`（保留 `"from"` / `"dsl"`，注意 `from` 是 DSL 派生入口）
3. 删除链式风格的测试脚本（3 个 `*_scenario.js`）
4. **不需要改动**：`Dsl.java` / `dsl.py` / `DslQueryFunction` / `TimeWindowExpander` / `TimeWindowInterceptor` / 任何 DSL spec 文档

**手册契约表达**（基于本结论）：
- Manual A：可明确写"DSL 是 first-class 入口，QueryPlan AST 是中立 IR"
- Manual B：明确标注"本手册描述的是构造 QueryPlan 的另一条等价表面，移除时不影响 DSL"

**遗留风险**（移入 Hidden Risks 跟踪，不阻断 G7 关闭）：
- **R-G7-1**：sandbox 寿命 — `ScriptRuntime.buildEvaluator()` 中 `Query` 全局注册位置需确保和 `Query` 类一起删，否则脚本报 `Query is undefined`
- **R-G7-2**：Python 仓 frozen 状态可能落后 Java，移除时需对齐双端
- **R-G7-3**：`real_sql_*_scenario.js` 等 DSL 脚本里有 `dsl(...).join(...)` / `dsl(...).union(...)` —— 这些是 **plan-level 方法**（`QueryPlan` 自身的方法），不属于"链式 API 入口"。Level 1 移除不会影响它们。如未来要做更激进的 Level 2 移除（连 plan 方法也禁掉），见 G8

### G9 · `withSubtotals` 字段已声明但未在 DslQueryFunction 处理

- **Layer**: Layer 1（小漏洞）
- **现状**（写 Manual A §1 时发现）：
  - `SemanticQueryRequest` 类中声明了 `withSubtotals: boolean` 字段（用于追加小计 / 总计行，结果中以 `_rowType` 标记行类型）
  - `DslQueryFunction.buildRequest()` 在映射 `dsl({...})` 顶层字段到 `SemanticQueryRequest` 时 **未读取该参数**
  - 用户在 `dsl({withSubtotals: true})` 中传入此参数会被静默丢弃
- **优先级**：P3（不影响主能力，但用户期待和实际不符）
- **建议**：
  - 在 DslQueryFunction.buildRequest 中补充 `withSubtotals` 映射（一行代码）
  - 或在 DSL spec 中明确不支持，并在 schema 校验阶段拒绝
- **影响**：Manual A §1 的"顶层字段全集"中**未列入** `withSubtotals`，避免误导（修复后再补）
- **Evidence**：`DslQueryFunction.java:143-205` / `SemanticQueryRequest.java:66-67`

### G8 · 移除链式 API 时的级别选择（Level 1 vs Level 2）

- **Layer**: 元层
- **状态**：deferred（仅在用户决定 deprecate 链式 API 时启动）
- **背景**：Phase 0 勘察暴露出"链式 API"实际上有两种含义：

  | 级别 | 含义 | 举例 | Phase 0 验证情况 |
  |------|------|------|-----------------|
  | **Level 1** | 移除 `Query.from(...)` 这种**构造入口** | `Query.from("X").where(...).select(...)` | ✅ Clean，DSL 不受影响 |
  | **Level 2** | 同时移除 plan 对象上的**方法链能力** | `dsl({...}).join(other, ...).where(...)` | ❌ 未验证；DSL 用户大量使用 |

- **影响**：
  - Level 1 是温和的、机械化的移除
  - Level 2 会要求所有数据流都用纯 `dsl({...})` 配置表达（如派生必须用 `dsl({source: prev})` 而不是 `prev.where().select()`）—— 这一选择影响 DSL spec 的 G2 设计取向
- **建议**：本表其他 gap（特别是 G2 DSL CTE/Join/Union 语法）按 **Level 1 兼容**优先设计，即"`dsl({...})` 返回的 plan 对象保留 `.join()` / `.where()` 等方法"，让 DSL 用户既可纯配置写也可方法链写
- **触发条件**：用户明确表示要走 Level 2 路线时启动本 gap
- **Evidence**：（如启动则补 spec 与代码勘察）

## Closure Log

> 缺口关闭按时间倒序记录。每条：日期 · ID · 关闭原因 · 关联 PR / commit。

| 日期 | ID | 关闭原因 | 关联 |
|------|-----|---------|------|
| 2026-04-26 | G7 | Phase 0 代码勘察确认 DSL/链式/IR 三者独立（Clean · Level 1）；移除链式 API 不影响 DSL | 见 G7 详情 · 后续派生 G8 跟踪 Level 2 决策 |

## Spec 修订日志

> 重要 spec 修订记录（独立于 gap 关闭）。

| 日期 | spec | 版本 | 关键修订 |
|------|------|------|---------|
| 2026-04-26 | G2 spec | v1 → v4 | v2: plan-method 单 plan 操作示例移除；v3: model 多态 + combinator 唯一形态；v4: `.union()` 数组重载 |
| 2026-04-26 | G5 spec | v1 → v2 | v1 收到代码核实级 ❌ 评估（5 项代码事实推翻 v1 假设）；v2 缩窄至 **columns only**，拆 Phase 1 (F4) / Phase 2 (F5)，F5 显式依赖新增 G10；slice/groupBy/orderBy 移交 G11/G12；§5.4 权限协同表述修正；§9 追加真实 SQL 数据比对要求 |
| 2026-04-26 | G5 spec | v2 patch | v2 收到 ✅ 评估通过 + 4 项小修订：(a) §2.3 `agg` 白名单改为对齐 Compose AggregateColumn / SemanticQueryRequest（不再引用 REQ-FORMULA-EXTEND）+ `count_distinct` SQL lowering 规则；(b) §5.1 plan 谱系遍历追加"按对象身份判定"warning；(c) §5.2 plan === model 自引用补 schema 校验路径明示；(d) §6 错误码命名约定（`COLUMN_*` 完整对外 / `COMPOSE_DSL_VALIDATION_*` 内部归类）；(e) §10.2 Phase 1 工作量"极低 → 小到中等"重估 |
| 2026-04-26 | G10 entry | tracker patch | 验收标准追加 plan-aware 编译 + plan-routed 权限的真实 SQL 数据比对要求（≥3 + ≥2 集成测试用例） |
| 2026-04-26 | tracker | 锚点稳定化 | 给 G5 / G10 / G11 / G12 添加显式 `<a id="...">` HTML 锚点；G2 spec 中链接更新为短锚点 `#g5` / `#g10` / 等，避免 heading slug 漂移导致跳转失效 |
| 2026-04-27 | G10 spec | v1 | 创建 G10 spec draft v1：4 项改造（schema 歧义 / provenance / plan-aware 编译 / plan-routed 权限）+ feature flag (`foggy.compose.g10.enabled`) all-or-nothing 落地 + 真实 SQL 数据比对验收 + 8-12 工程日预估；触发 G5 Phase 2 / G11 / G12 全部下游解锁路径明确 |
| 2026-04-27 | G5 spec | v2-patch | F4 实施前代码勘察修正：(a) `count_distinct` lowering 已由 `AllowedFunctions.COUNT_DISTINCT` + `SqlFunctionExp` 自动支持，零 AST 改动；(b) F4 实施模式从"AggregateColumn IR 扩展"修正为 **normalize-at-entry**（入口归一化 Map → 字符串 `"AGG(field) AS alias"`）；§2.3 / §10.2 列出双端 5 个具体入口 + 工作量重估 |
| 2026-04-27 | G10 spec | v1 → v2 | v1 ⚠️ 条件通过 + 5 项 patch：(a) §4.3 PlanId 收紧 equals 契约（按 referent identity，identityHash 仅 hash 桶）；(b) §3.3 OutputSchema 补 lookup API 升级（`getAll` / `isAmbiguous` / `requireUnique`）+ 调用方迁移指南；(c) §6 改造 #4 重构落点（新增 `ComposePlanAwarePermissionValidator` Compose 层独立类，不改 `FieldAccessPermissionStep`）；(d) §6.4 bare field 规则重写（先 schema 唯一解析、再权限校验）；(e) §10.2 PR1 修正为真零行为变化；(f) §10.1 工程日 8-12 → 10-15 重估 |
| 2026-04-27 | G10 entry | implementation done | spec v2 4 项改造双端落盘：Java `dev-compose` 7 commits（PR1-PR4 + 3 polish · sqlite lane 1809 passed）+ Python `main` 5 commits（PR5.1-PR5.4 + 1 polish · pytest 3176 passed）；154 新单元（Java 83 / Python 71）+ 双端 parity + flag 双状态显式覆盖；coverage audit `coverage/G10-coverage-audit.md` 结论 `ready-with-gaps`，集成测试 ≥3 + ≥2 deferred 至 G5 Phase 2 同批次（spec §9 中 7/9 项 covered）；状态 `in-review → implemented · ready-with-gaps`，pending `foggy-acceptance-signoff` |
| 2026-04-27 | G10 entry | acceptance signed-off | user 签收 `accepted-with-risks` · acceptance doc `acceptance/G10-ComposeEngine-PlanAware-acceptance.md`（evidence_count 12）· FU-1（G5 Phase 2 集成测试 ≥3+≥2 强制承接）+ FU-2（lane sweep）+ FU-3（flag-flip playbook · 已落 `G10-flag-flip-rollout-playbook.md` draft）作为非阻断 follow-up；G5 Phase 2 / G11 / G12 全部解锁；状态 `implemented · ready-with-gaps → accepted-with-risks` |
| 2026-04-28 | G6 entry | spec-ready | 契约文档 `docs/8.4.0.beta/P2-timeWindow-calculatedFields-interaction-contract.md` 落盘；定义执行顺序 + 允许/禁止矩阵 + 4 错误码 + 正反例 JSON + Python 对齐指引；G6 状态 `open → spec-ready`；实现目标 8.5.0.beta |

## 相关文档

- 决策记录（决策 1 / 2 / 3）：本会话上下文（暂未独立成文，后续如需正式留档可建 `docs/8.3.0.beta/compose-query-manuals-decisions.md`）
- DSL 时间窗口 spec：`docs/8.3.0.beta/P1-SemanticDSL-时间窗口能力设计.md`
- 链式 API CTE spec：`docs/8.2.0.beta/P0-ComposeQuery-CTE使用参考手册.md`
- timeWindow 总控 / Java 实施：`docs/v1.5/P1-SemanticDSL-timeWindow-总控设计.md` / `docs/v1.5/P1-timeWindow-impl-java.md`
- 遗留参考：`docs-site/zh/dataset-model/tm-qm/query-dsl.md`（待 deprecation banner）
