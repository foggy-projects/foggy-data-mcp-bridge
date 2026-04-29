# P2-S7c compileToRelation progress

## 基本信息

- version: 8.5.0.beta
- status: complete
- contract_ref: `docs/8.5.0.beta/P2-S7-stage7-runtime-execution-plan.md`
- prerequisite: S7b stable relation contract frozen
- next_stage: S7d relation-as-source read-only outer query

## 交付内容

S7c 已将 S7a stable relation POC 模型接入真实 Java compile boundary：

```text
QueryPlan + ComposeQueryContext + RelationCompileOptions
  -> ComposeRelationCompiler.compileToRelation(...)
  -> CompiledRelation
```

## Changed Files

| File | Change |
|---|---|
| `ComposeRelationCompiler.java` | 新增 S7c runtime entry，编译 `QueryPlan` 为 `CompiledRelation` |
| `RelationCompileOptions.java` | 新增不可变 options bag，携带 dialect、bindings、datasource、permission、timeWindow schema enrichment hints |
| `ComposeRelationCompilerTest.java` | 新增 34 个测试，覆盖 plan 类型、方言不变量、SQL marker、fail-closed |
| `ComposeCompileErrorCodes.java` | 新增 `PHASE_RELATION_COMPILE` |
| `ComposeCompileErrorCodesTest.java` | phase registry 测试更新为 3 个阶段 |

## 覆盖能力

- base plan -> `CompiledRelation`
- derived plan -> `CompiledRelation`
- timeWindow yoy / rolling / cumulative -> enriched `OutputSchema`
- params flatten 从 `ComposedSql` 透传到 relation
- datasource id carried through
- permission state defaults to `unknown`
- relation alias 可由 options 指定
- `FROM (WITH` forbidden marker 检测
- CTE presence 检测

## Fail-Closed Invariants

- `supportsOuterAggregate=false`
- `supportsOuterWindow=false`
- MySQL 5.7 (`mysql` / `mysql57`) + CTE -> `RELATION_WRAP_UNSUPPORTED`
- SQL Server (`mssql` / `sqlserver`) 输出不得包含 `FROM (WITH`
- 如果非 MySQL 5.7 方言仍产生 `FROM (WITH`，抛出 `RELATION_CTE_HOIST_UNSUPPORTED`

## Scope Preserved

S7c 没有开放以下能力：

- relation-as-source outer query
- outer aggregate
- outer window
- relation join / union
- named / recursive CTE
- 无上下文 `QueryPlan.toRelation()`
- 普通列 alias 新契约

## 测试证据

Command:

```powershell
mvn test -pl foggy-dataset-model "-Dtest=ComposeRelationCompilerTest,StableRelationSnapshotTest,RelationModelTest,ColumnSpecMetadataTest,TimeWindowOutputSchemaTest,ComposeCompileErrorCodesTest"
```

Result:

- default lane: 86 tests, 0 failures, 0 errors, 0 skipped
- test-mysql lane: 86 tests, 0 failures, 0 errors, 0 skipped
- test-postgres lane: 86 tests, 0 failures, 0 errors, 0 skipped
- total executions: 258, 0 failures

`git diff --check` passed with Windows LF -> CRLF warnings only.

## 关键修正

补充了真正的 MySQL 5.7 + CTE 负例。该测试暴露出 fail-closed 顺序问题：原实现先检查 `FROM (WITH`，导致 MySQL 5.7 + CTE 报成 `RELATION_CTE_HOIST_UNSUPPORTED`。现已调整为先按方言能力判断 CTE 是否支持，再执行通用 `FROM (WITH` 安全检查，使 MySQL 5.7 正确报 `RELATION_WRAP_UNSUPPORTED`。

## 下一步

允许进入 S7d：relation-as-source read-only outer query。

S7d 必须仍保持：

- `supportsOuterAggregate=false`
- `supportsOuterWindow=false`
- 不开放 relation join / union
- 不开放 outer aggregate / outer window
- 外层引用必须受 `OutputSchema` 与 `referencePolicy` 约束
