---
type: execution-prompt
version: 8.2.0.beta
milestone: M6
target_repo: foggy-data-mcp-bridge-python
target_module: foggy.dataset_model.engine.compose
req_id: M6-SQLCompilation-Python
parent_req: P0-ComposeQuery-QueryPlan派生查询与关系复用规范
status: ready-to-execute
drafted_at: 2026-04-22
python_baseline_before: 2709 passed / 1 skipped (M5 baseline)
python_new_tests_target: ≥ 80 (6.1 base+derived ~20 / 6.2 union ~12 / 6.3 join ~12 / 6.4 binding injection ~20 / 6.5 dialect fallback ~10 / 6.6 plan-hash dedup ~10)
python_new_source_files_target: ~7 (compile subpackage)
---

# Python M6 · Compose Query SQL 编译器 开工提示词

## 执行位置（读在最前）

- **目标仓库**：`foggy-data-mcp-bridge-python`（独立仓，非 worktree）
- **新建包**：`foggy.dataset_model.engine.compose.compile/`
- **已有姐妹子包**：`compose.authority/`（M5 Authority 绑定管线） / `compose.context/`（M1 ComposeQueryContext） / `compose.plan/`（M2 QueryPlan 对象模型） / `compose.sandbox/`（M3 Dialect + 沙箱错误） / `compose.schema/`（M4 Schema 推导） / `compose.security/`（M1 AuthorityResolver SPI）
- **pytest**：`pytest tests/compose/compile/` 聚焦；全仓 `pytest -q`

规范文档在 Java 仓 worktree：`D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge-wt-dev-compose/docs/8.2.0.beta/`。本提示词不在 Python 仓落盘（历史上 Python 侧 M1–M5 均无 per-milestone execution prompt，这次 M6 量级较大所以破例写一份；实现时 Python 仓 `docs/` 不变动）。

## 角色与语境

你是 `foggy-data-mcp-bridge-python` 的 engine 层维护者。M6 是 Compose Query 第一个**跨 base plan 组合 SQL** 的里程碑：把 M2 `QueryPlan` 树 + M5 `Map[str, ModelBinding]` 组合 → 方言感知的 CTE / 子查询 SQL。

**核心原则（2026-04-22 progress.md 决策记录已锁）**：

1. `deniedColumns` / `systemSlice` / `PhysicalColumnMapping` **完全复用 v1.3 既有链路**，不在 compose 层另起一套
2. `field_access` whitelist 在 M5 `apply_field_access_to_schema` 已覆盖声明 schema 层面；M6 只需把 `ModelBinding.field_access` 也注入 `SemanticQueryRequest.field_access`（v1.3 engine 会原样生效）
3. 底层 CTE / 子查询拼装复用已有的 `foggy.dataset_model.engine.compose.CteComposer`（`__init__.py` 里）—— **不重写**

## 必读前置

严格按顺序读完再动手：

1. **主需求**：`docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md`
   - §SQL 编译边界 / §错误模型规划 / §典型示例 1~3
2. **实现规划**：`docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-实现规划.md`
   - §SQL 编译边界（§1~§4）/ §交付顺序建议（第 6 条）
3. **progress.md 决策记录**：`docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-progress.md`
   - 2026-04-22 两条（M6 deniedColumns 复用 v1.3 / 节奏 Python 先落）
4. **M5 上游**：`foggy.dataset_model.engine.compose.authority.resolver.resolve_authority_for_plan` 返回 `Dict[str, ModelBinding]`，M6 的每个 BaseModelPlan 都查得到一条 binding
5. **v1.3 复用挂点**（看懂，不改写）：
   - `foggy.mcp_spi.semantic.SemanticQueryRequest` 的 `denied_columns / system_slice / field_access` 三个字段（第 389 / 440 / 446 / 452 行）
   - `foggy.dataset_model.semantic.physical_column_mapping` 的 `PhysicalColumnMapping` + `build_physical_column_mapping` + `to_denied_qm_fields`
   - `foggy.dataset_model.semantic.service.SemanticServiceImpl.get_physical_column_mapping(model_name)`（v1.3 mapping cache）
6. **已有 CteComposer**（M6 底层 SQL 组装器）：`foggy.dataset_model.engine.compose.__init__` 里 `CteComposer.compose(units, join_specs, use_cte=True|False)` + `CteUnit` / `JoinSpec` / `ComposedSql`

## 交付清单

### 新建子包 `foggy.dataset_model.engine.compose.compile/`

```
compile/
├── __init__.py               # 公开 API：compile_plan_to_sql, ComposeCompileError
├── errors.py                 # ComposeCompileError + 错误码常量
├── error_codes.py            # 本期新增的 6 个 code（见下表）
├── per_base.py               # _compile_base_model(plan, binding, context) 把 BaseModelPlan + ModelBinding → CteUnit (SQL + params + projection)
├── plan_hash.py              # plan-level 结构 hash，用于 M6.6 子树去重
├── compose_planner.py        # 把 QueryPlan 树转成 (List[CteUnit], List[JoinSpec])，按方言决定 use_cte
└── compiler.py               # compile_plan_to_sql(plan, context, *, bindings, model_info_provider=None) 入口
```

公开 API（`__init__.py`）：

```python
from .compiler import compile_plan_to_sql
from .errors import ComposeCompileError
from . import error_codes

__all__ = ["compile_plan_to_sql", "ComposeCompileError", "error_codes"]
```

### 核心入口签名

```python
def compile_plan_to_sql(
    plan: QueryPlan,
    context: ComposeQueryContext,
    *,
    bindings: Optional[Dict[str, ModelBinding]] = None,
    model_info_provider: Optional[ModelInfoProvider] = None,
    dialect: str = "mysql",            # "mysql" / "postgres" / "mssql" / "sqlite"
) -> ComposedSql:
    """Compile a QueryPlan tree to dialect-aware SQL + params.

    If ``bindings`` is not provided, internally invoke
    ``resolve_authority_for_plan(plan, context, model_info_provider=...)``
    to obtain them. This makes the two-step API optional: callers that
    already have bindings pass them in; one-shot callers skip the extra
    call.
    """
```

### 6 个新错误码（`error_codes.py`）

| 常量 | 字符串 | 触发 |
|---|---|---|
| `NAMESPACE` | `"compose-compile-error"` | 顶层 namespace |
| `UNSUPPORTED_PLAN_SHAPE` | `compose-compile-error/unsupported-plan-shape` | M6 本期未支持的 QueryPlan 子类或组合 |
| `CROSS_DATASOURCE_REJECTED` | `compose-compile-error/cross-datasource-rejected` | union/join 两侧来自不同数据源 |
| `MISSING_BINDING` | `compose-compile-error/missing-binding` | `bindings` 没有给定 BaseModelPlan.model 的 entry（上游 M5 应先失败；M6 作为二次防御） |
| `PER_BASE_COMPILE_FAILED` | `compose-compile-error/per-base-compile-failed` | 某个 BaseModelPlan 走 v1.3 engine 失败（wrap 下层异常） |
| `SANDBOX_REJECTED` | `compose-compile-error/sandbox-rejected` | 遇到内存聚合 / 窗口 / lateral / recursive 等本期不支持的语义 |

两个 phase：`"compile"` / `"plan-lower"`（后者用于在 CTE 组装前的 plan→SQL 下降步骤报错）。

**提醒**：M6 **不新增** `compose-authority-resolve/*` 或 `compose-schema-error/*` 的错误码 —— M5/M4 已冻结，compile 阶段依赖失败由它们抛出后原样透传。

### 6 阶段拆分（建议按顺序提交 / 审阅）

#### 6.1 · BaseModelPlan + DerivedQueryPlan 编译（链式派生）

入口：`_compile_base_model(plan: BaseModelPlan, binding: ModelBinding, context: ComposeQueryContext) → CteUnit`

实现路径：
1. 从 `plan` 读 `model / columns / slice / group_by / order_by / limit / start / distinct`
2. 从 `binding` 读 `field_access / system_slice / denied_columns`
3. 通过 `SemanticServiceImpl.get_physical_column_mapping(model_name)` 取到 mapping（或用 `NullModelInfoProvider` 路径）
4. 构造 `SemanticQueryRequest(..., field_access=binding.field_access, system_slice=binding.system_slice, denied_columns=binding.denied_columns)`
5. 调 v1.3 engine 产出 SQL + params（具体入口参考 `SemanticServiceImpl.query` 走的那条链路，但只要 SQL 字符串 + params，不要执行）
6. 包装成 `CteUnit(alias=f"cte_{idx}", sql=sql, params=params, select_columns=<aliases>)`

对 `DerivedQueryPlan`：不单独生成 SQL，而是把它作为 **outer SELECT** 包在 source 的 CteUnit 之外；多段链式派生递归嵌套。

测试聚焦（~20）：
- 单 base plan 基本查询
- derived(base) 选列 / group_by / order_by / limit / start
- 链式 derived 2/3/4 层
- `field_access` / `denied_columns` / `system_slice` 被 binding 注入后的 SQL 行为
- `distinct=True`
- 空 slice / 空 group_by / 空 order_by
- 错误：v1.3 engine 抛错时包装为 `PER_BASE_COMPILE_FAILED` 保留 `__cause__`

#### 6.2 · UnionPlan 编译

每个 side → `CteUnit`；用 SQL-level `UNION` / `UNION ALL`（不走 CteComposer 的 JoinSpec 路径 —— union 是列对齐不是 ON 条件）。

约束：
- 列数已在 M4 schema derive 时校验 —— M6 不重复
- 类型兼容性在本期 **不**校验（spec 显式推后）
- `union` 左右来自不同 dialect / datasource → `CROSS_DATASOURCE_REJECTED`（本期通过比对 `binding` 的某个 datasource identifier 或通过 `ModelInfoProvider` 返回的 tables 可选值判断 —— 最低实现可以先统一假设同数据源，抛占位错误，测试用 xfail 标记）

测试聚焦（~12）：union 基本 / `all=True` vs `all=False` / 双侧 derived / union 多路（先支持 2 元；>2 元可按左结合递归）/ 方言组合 SQL 输出形态

#### 6.3 · JoinPlan 编译

使用 `CteComposer.compose(units, join_specs, use_cte=<by dialect>)`。

`JoinSpec` 由 `JoinOn` 列表转换而来；多个 `JoinOn` 用 `AND` 连接成一个 `on_condition` 字符串。

约束：
- `on[*].left` / `on[*].right` 合法性在 M4 已校验 —— M6 不重复
- `type` ∈ `{inner, left, right, full}`；`full` 在 SQLite 不支持 → 抛 `UNSUPPORTED_PLAN_SHAPE`

测试聚焦（~12）：inner/left/right 基本 / 多 ON 条件 / 一侧 derived / join 后再 query / full outer join 在 SQLite 被拒绝 / join 后 columns 引用两侧字段

#### 6.4 · `Map[str, ModelBinding]` 按 BaseModelPlan 注入 v1.3 挂点 ★核心

这一条是 M6 的**权限正确性关键**。

实现要点：
- `compile_plan_to_sql` 收到 `bindings` 后，遍历 `BaseModelPlanCollector.collect(plan)`（或复用 M5 结果），对每个 base plan：
  - 在 `_compile_base_model(base_plan, binding=bindings[base_plan.model], context)` 里把 binding 三字段注入 `SemanticQueryRequest`
  - 不调用 M5 的 `apply_field_access_to_schema`（那是声明 schema 层面；M6 需要让 v1.3 engine 自己根据 `field_access` 生成 SELECT）
  - 对 `denied_columns` **一定不要** 在 compose 层再用 `PhysicalColumnMapping` 翻译一次 —— v1.3 engine 已经在 `physical_column_permission_step`（或等价的 Python hook）消费了
- `bindings` 为 `None` 时，内部调 `resolve_authority_for_plan(plan, context)` 得到 bindings 再继续

测试聚焦（~20）：
- 带 `field_access = ['a','b']` 的 binding → 生成的 SQL 只 SELECT a, b
- 带 `field_access = []` → 生成的 SQL 为空列 or 合理错误（参考 v1.3 `empty field_access` 行为；两种结果都需文档化）
- 带 `denied_columns = [phys_col]` → v1.3 engine 行为保持（物理列不出现在 SQL）
- 带 `system_slice = [{field,op,value}]` → WHERE 追加
- `bindings` 缺 key（M5 应先失败，但 M6 作为二次防御）→ `MISSING_BINDING`
- `bindings=None` 的单步 API → 内部自动 resolve → SQL 生成成功
- 同一 QM 在 plan 树出现两次（union）→ 两个 CteUnit 共享同一 binding（请求级去重已在 M5；这里验证 SQL 不出现两条不同的权限）
- denied_columns 已由 v1.3 链路生效 —— M6 不再 PhysicalColumnMapping 反查（确认路径不重复）

#### 6.5 · CTE vs 子查询方言回退

根据 `dialect` 决定 `use_cte`：
- MySQL 5.7 → `use_cte=False`（不支持 CTE）
- MySQL 8.0+ / PostgreSQL / SQL Server / SQLite 3.30+ → `use_cte=True`

`CteComposer` 已支持两种模式；M6 只需提供一个 `_dialect_supports_cte(dialect: str) → bool` 的小工具。

测试聚焦（~10）：
- 4 方言 × (single / union / join) 的 SQL 形态快照（用已有 `_sql_normalizer` parity infra 做归一化比对）
- `use_cte=True` 输出 `WITH cte_0 AS (...)` 片段
- `use_cte=False` 输出 `FROM (...) AS t0`
- 嵌套 derived 下方言是否影响外层 SELECT 语法（不应该；方言只影响 FROM 子句形态）

#### 6.6 · plan-hash 子树去重

同一个 BaseModelPlan 子树在 plan 树里出现多次时（典型：union 左右相同 QM，或 derived chain 里复用同一 base）：
- 用 `plan_hash(plan)` 把结构性等价的子树归一为同一 `CteUnit`
- 多次复用的子树优先变成一个 CTE，其他处用 `cte_{idx}` alias 引用
- 单次引用仍用 inline subquery

hash 算法：深度遍历 plan 节点 → 把 `(type, model, columns, slice, group_by, order_by, limit, start, distinct)` 的规范化表示 hash 成一个字符串 / tuple。**注意**：hash 必须跨实例相等（M2 plan 节点已是 frozen dataclass，`__hash__` 已由 dataclass 自动给出 —— 但 `slice` 是 `List[Any]` 不 hashable，需要先转成 tuple）。

测试聚焦（~10）：
- 同一 base 子树在 union 两侧 → 只产生一个 CTE
- 同一 base 子树在 join 两侧 → 只产生一个 CTE
- 结构性等价但实例不同的子树（两次 `from_("X", columns=...)` 写法一样）→ 合并
- 结构性不等价（columns 顺序不同 / limit 不同）→ 不合并
- 三次引用 → 仍只一个 CTE

## 非目标（禁止做）

- **不做**：跨数据源 union/join（直接抛 `CROSS_DATASOURCE_REJECTED`）
- **不做**：窗口函数 / `exists` / `lateral` / recursive CTE（抛 `SANDBOX_REJECTED`）
- **不做**：内存加工后再编排
- **不做**：`SemanticQueryRequest` / v1.3 `PhysicalColumnMapping` / v1.3 `denied_columns` 物理列拦截 的任何修改
- **不做**：`CteComposer` 的重写或扩展（现有 SQL 模板够用；不够用再单独立项）
- **不做**：`toSql()` / `execute()` 绑定到 QueryPlan（M7 scope）
- **不做**：任何 MCP 层面 / HTTP tool 入口（M7 scope）
- **不做**：M9 沙箱 Layer A/B/C 验证器实装（独立里程碑）
- **不新增**：`compose-authority-resolve/*` 或 `compose-schema-error/*` 错误码

## 验收硬门槛

1. `pytest tests/compose/compile/ -q` 全绿
2. `pytest -q` 全回归，从 **2709 baseline** 推进到 **2709 + N**（N ≥ 80），**0 failures**, **1 skipped**（M4 snapshot 占位，不动）
3. 6 个错误码字符串在 `error_codes.py` 与测试断言中逐字对齐
4. 4 方言（MySQL / PG / MSSQL / SQLite）至少各有 1 条 SQL snapshot 测试验证 CTE vs 子查询
5. `spec §典型示例 1`（两段聚合）+ `§典型示例 2`（union+aggregate）+ `§典型示例 3`（join+alias 消歧）3 个端到端 compile 成功 → `ComposedSql.sql` 可读（不要求 SQL 字符串完全稳定，允许 alias 命名 drift，但结构正确）
6. 完成后把 8.2.0.beta progress.md 的 M6 行：`not-started` → `python-ready-for-review / java-pending`，追加 Python 基线数字（2709 → 2709+N）
7. 本提示词 `status: ready-to-execute` → `status: done`，填写 `completed_at` + `python_baseline_after`
8. 追加 changelog 条目到 progress.md `## 变更日志`

## 停止条件

- 发现 v1.3 `denied_columns` / `system_slice` 链路**必须修改**才能满足 M6 需求（例如 compose SQL 层引入了跨 CTE 的语义让原有 step 失效）→ 立即停，升级为 blocker，升到 progress.md 决策记录与用户讨论，**不自己改 v1.3**
- 发现 M4 schema derive 的输出和 M6 compile 的输入对不齐（比如 OutputSchema 的列顺序和 SQL projection 顺序）→ 停下反推到 M4 修订
- 发现 `CteComposer` 在某条用例下产出的 SQL 在 4 方言的任一上语法错 → 把方言问题写成 `TODO: v1.3 engine dialect escape` 并加 xfail，不急着在 M6 里修 `CteComposer`
- 任何 M1–M5 的既有测试从绿变红 → 立即停，0 regression 是硬门槛

## 预估规模

- 源码：7 文件 · ~700 LOC（compile 子包主体）+ 少量 SemanticServiceImpl 调用胶水
- 测试：~1500 LOC · 80+ tests
- 总量：**2–3 人日**（比初版 3–5 的估算小，是因为 v1.3 复用约定明显收窄了范围）

## 完成后需要更新的文档

1. `docs/8.2.0.beta/P0-ComposeQuery-...-progress.md` 的 M6 行：`python-ready-for-review / java-pending`，追加 Python 基线数字
2. 本提示词 `status: ready-to-execute` → `status: done`，填写完成日期 + `python_baseline_after`
3. 新增 `docs/8.2.0.beta/` 下 Java 侧开工提示词（由镜像工程师后续补写）：`M6-SQLCompilation-Java-execution-prompt.md`
4. root `CLAUDE.md` 的 "Compose Query M5 Authority 绑定管线" 段之后新增 "Compose Query M6 SQL 编译器" 段（Python 侧一段式）
