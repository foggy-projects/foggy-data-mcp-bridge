# P2-S7 Stage 7 runtime execution plan

## 文档作用

- doc_type: implementation-plan
- intended_for: Java execution-agent / reviewer / signoff-owner
- purpose: 在 S7a stable relation POC 和 Python mirror 完成后，定义 Java 引擎 Stage 7 runtime 能力的开工顺序、边界和验收标准

## 基本信息

- version: 8.5.0.beta
- status: planned-wait-for-s7b-contract-freeze
- contract_ref: `foggy-data-mcp-bridge-python/docs/v1.5/S7b-stage7-runtime-contract-plan.md`
- s7a_progress: `docs/8.5.0.beta/P2-S7a-stable-relation-contract-progress.md`
- current_java_commit: `f3360b3 feat(compose): add stable relation contract model`
- python_mirror_commit: `8234006 feat(compose): mirror stable relation contract`

## 当前基线

S7a 已完成模型层 POC：

- `compose.relation` 包提供 `CompiledRelation` / `RelationSql` / `CteItem` / `RelationCapabilities`。
- `ColumnSpec` 已新增 semantic metadata，并排除在 `equals()` / `hashCode()` 之外。
- `TimeWindowExpander.getOutputSchema()` 已提供 timeWindow 输出 schema。
- `StableRelationSnapshotTest` 已生成 `target/parity/_stable_relation_schema_snapshot.json`。
- `supportsOuterAggregate=false` / `supportsOuterWindow=false`，Stage 7 runtime 能力尚未开放。

Python 已完成 mirror 并消费 Java snapshot。Java 下一步不应直接开放二次聚合/窗口，而应先把 POC 对象模型接入真实 runtime 编译入口。

## 目标

1. 将 S7a POC 模型升级为真实 Java compile-to-relation 入口。
2. 让 stable relation 可以作为外层 query source 被只读引用。
3. 分阶段开放 outer aggregate 和 outer window。
4. 保持 SQL Server / MySQL 5.7 / CTE wrapper 策略 fail-closed。
5. 每阶段都输出 Java snapshot，由 Python mirror 消费验证。

## 非目标

- 不新增无上下文 `QueryPlan.toRelation()`。
- 不在 S7c 开放 outer aggregate / outer window。
- 不实现数据库物理 `CREATE VIEW`。
- 不实现 named CTE / recursive CTE。
- 不放宽普通列 alias 契约。
- 不允许绕过 `referencePolicy` 使用派生列。

## Execution Plan

### S7b - Contract freeze

- status: pending
- owner: root-controller + Java/Python contract owners

Java responsibilities:

- 确认当前 Java relation model 与 Python mirror 完全一致。
- 决定 snapshot schema 是否继续使用 `S7a-1`，或升级为 `S7b-1`。
- 更新 `P2-S7a-stable-relation-contract-progress.md` 或新增 S7b progress。

No runtime code change expected.

Acceptance:

- `StableRelationSnapshotTest` pass。
- Python stable relation snapshot tests pass。
- `supportsOuterAggregate=false` / `supportsOuterWindow=false`。

### S7c - compileToRelation runtime entry

- status: wait-for-s7b
- owner: Java

Implementation boundary:

- 推荐新增 compiler/service 入口，例如 `ComposeRelationCompiler` 或等价命名。
- 输入必须包含 `QueryPlan` 和完整 compile context。
- 输出 `CompiledRelation`。
- 不把 dialect/datasource/permission/params 依赖藏进 `QueryPlan` 无参方法。

Required behavior:

- base plan / derived plan / timeWindow plan 可编译为 `CompiledRelation`。
- relation alias 稳定、可避免 CTE name collision。
- params flatten 顺序稳定。
- SQL Server 有 inner CTE 时输出 hoisted top-level CTE。
- MySQL 5.7 + inner CTE fail-closed。
- wrapper SQL 中不得出现 `FROM (WITH`。

Tests:

- compileToRelation model-level tests。
- SQL Server forbidden marker tests。
- MySQL 5.7 fail-closed tests。
- parity snapshot 增加 runtime relation SQL markers。

### S7d - relation-as-source read-only query

- status: wait-for-s7c
- owner: Java first

Implementation boundary:

- 新增或扩展外层 plan source，使 `CompiledRelation` 可被引用。
- 第一版只允许 read-only outer query。

Allowed:

- select readable columns
- orderBy orderable columns
- filter readable columns if existing expression dependency validation can support it
- limit / pagination

Not allowed:

- outer aggregate
- outer window
- relation join
- relation union

Tests:

- readable select pass。
- orderable orderBy pass。
- unknown column rejected。
- non-readable / non-orderable rejected。
- datasource identity preserved。
- output schema stable after wrapping。

### S7e - outer aggregate

- status: wait-for-s7d
- owner: Java first

Implementation boundary:

- 只允许对 `referencePolicy` 包含 `aggregatable` 的 relation columns 做外层聚合。
- ratio / percent 派生列默认不可聚合。
- 聚合后的 output schema 必须重新派生 semantic metadata。

Tests:

- aggregatable measure outer sum/count pass。
- ratio outer sum/avg rejected。
- unknown/non-aggregatable rejected。
- error code stable。
- Java snapshot 增加 positive/negative cases，Python mirror 消费。

### S7f - outer window

- status: wait-for-s7e
- owner: Java first

Implementation boundary:

- 只允许对 `referencePolicy` 包含 `windowable` 的列做窗口输入。
- partition/order 字段必须各自满足 reference policy。
- 内层 timeWindow 与外层 window 的 lineage 必须可解释。

Tests:

- legal outer window pass。
- non-windowable derived column rejected。
- invalid partition/order reference rejected。
- snapshot/parity 更新。

## Code Inventory

| Module | Path | Role | Expected Change | Notes |
|---|---|---|---|---|
| `foggy-dataset-model` | `src/main/java/.../engine/compose/relation/` | stable relation model | update | S7c 可能补 runtime helper，但保持对象模型兼容 |
| `foggy-dataset-model` | `src/main/java/.../engine/compose/compilation/` | compiler entry | create/update | 推荐放 compileToRelation 入口 |
| `foggy-dataset-model` | `src/main/java/.../engine/compose/plan/` | plan model/schema | update cautiously | 不增加无上下文 toRelation |
| `foggy-dataset-model` | `src/main/java/.../engine/compose/schema/` | OutputSchema/ColumnSpec | update cautiously | metadata equality/hash 不变 |
| `foggy-dataset-model` | `src/test/java/.../engine/compose/relation/` | relation tests | update | 扩 runtime behavior tests |
| `foggy-dataset-model` | `src/test/java/.../parity/` | snapshot producer | update | 每阶段输出 Python 可消费 fixture |
| `docs/8.5.0.beta/` | progress docs | tracking | update | 每阶段必须记录 tests/evidence |

## Verification Commands

Focused S7a/S7b baseline:

```powershell
mvn test -pl foggy-dataset-model "-Dtest=StableRelationSnapshotTest,RelationModelTest,ColumnSpecMetadataTest,TimeWindowOutputSchemaTest"
```

S7c and later should add stage-specific tests, then run compose lane:

```powershell
mvn test -pl foggy-dataset-model "-Dtest=*CompileTest,*CompilationTest,*Compose*Test,*Relation*Test,*SnapshotTest"
```

Before commit:

```powershell
git diff --check
```

## Completion Rules

每个子阶段完成后必须报告：

- changed files
- implemented stage and non-goals preserved
- test commands and results
- snapshot location and contract version
- SQL Server / MySQL 5.7 wrapper evidence
- whether Python mirror can start
- remaining risks

## Stop Conditions

遇到以下情况必须暂停进入下一阶段：

- Python mirror 无法消费 Java snapshot。
- relation schema 字段命名或 metadata 语义发生漂移。
- SQL Server 输出出现 `FROM (WITH`。
- MySQL 5.7 + inner CTE 没有 fail-closed。
- outer aggregate/window 被提前打开。
- `ColumnSpec.equals()` / `hashCode()` 行为被 metadata 字段改变。
