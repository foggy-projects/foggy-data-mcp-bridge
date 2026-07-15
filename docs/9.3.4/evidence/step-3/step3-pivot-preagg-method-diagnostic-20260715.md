---
doc_role: implementation_evidence
doc_purpose: Record the targeted five-database diagnostic for the Pivot pre-aggregation method repair.
version: 9.3.4
step: 3
status: diagnostic-passed
authority_status: not-authority
scope: targeted-method
inventory_consumption: 0
created_at: 2026-07-15
---

# Step 3 Pivot PreAgg Targeted Method 诊断证据

## Decision

`PivotSqlParityIT#testPreAggHitWithSystemSliceAndLimitKeepsFinalParamOrder`
已在 SQLite、MySQL 5.7、MySQL 8、PostgreSQL 15、SQL Server 2022 五个
diagnostic lane 完成真实 SQL/oracle 核验；五份最终 XML 均为 `1/F0/E0/S0`，合计
`5/F0/E0/S0`。本批状态为 `diagnostic-passed`。

本记录明确为 `not-authority`、`targeted-method`，对 Step 3 required execution inventory
的消耗为 `0`。它不能替代 fresh-volume 数据库矩阵、正式 selector gate、exact-set report
collector、required negative probes 或完整 required skip=0 证明，Step 3 仍保持
`in-progress`。

## Failure Reproduction And Local Green

### Executed red

- run root：`foggy-dataset-model/target/v934-step3-pivot-red-r1/`
- console SHA-256：
  `f1877ac829ef2a23aa4569cf417f5a23fa3a96fe6797bda408ec4f99cd2f6e31`
- observed：`1/F0/E1/S0`；目标方法开始真实执行 planned SQL 后，SQLite 稳定报
  `no such column: pa.product$categoryName`。
- conclusion：旧测试只检查 SQL/参数形状时会放过不存在的物理列；该 red 证明问题不是
  文本推测，而是可执行的伪绿色缺口。

### Legacy fixture green

- run root：`foggy-dataset-model/target/v934-step3-pivot-legacy-green-r2/`
- console SHA-256：
  `945fdedd1dd703c1273e8f83523a3051baed44a88d425b8c497be0f3471ac39c`
- observed：目标方法在既有 `FactSalesPreAggQueryModel` / SQLite fixture 上成功执行；
  pre-aggregation SQL 使用物理 property/measure 列，并对 property grain 执行二次
  `SUM`/`GROUP BY`。
- disposition：该结果只作为既有模型兼容诊断，不是 Step 3 inventory execution。

## Implemented Query Contract

本批把 Pivot 命中的 query read path 收紧为可证明的物化契约：

- matcher、main rewriter、final-stage builder 与 hybrid watermark 共用明确的物化列证明；
  dimension、caption/id、时间属性或命名约定不能再反向证明物理列存在；
- select、group、slice、slice RHS、表达式 token 与 watermark 缺少明确物化映射时
  fail closed，回退源查询，不生成猜测物理列的 SQL；
- property group 必须识别二次 rollup；请求聚合函数、预聚合度量函数与 final-stage
  calculated aggregate 必须可证明一致；
- temporal grain 合并覆盖 natural week 与 calendar month/quarter/year 的非嵌套边界；
  粗粒度候选不得服务更细粒度请求；
- HAVING、非 slice access/query predicate、复杂 main predicate、hybrid+slice、null/stale
  watermark 与不可证明 post-aggregate 条件均 fail closed；
- typed/open range、LIKE、合法 `$field`/`$expr` 等可证明路径保留参数顺序和查询语义；
- `QueryStagePlan` 在 aggregate-stage 过滤存在时阻止不安全的 final-stage
  pre-aggregation shortcut。

该契约只覆盖 query matcher/rewriter/final-stage/hybrid read path 与本 Pivot 定点执行，
不声明 Addon DDL/refresh 生命周期已经统一。

## Deterministic Oracle

隔离的 `v934_preagg_*` fixture 固定为 `4` 行事实/pre-aggregation 输入：

| Date | Product | Category | Amount |
|---|---:|---|---:|
| 20930101 | 934001 | `V934_ALPHA` | 30.0000 |
| 20930102 | 934001 | `V934_ALPHA` | 20.0000 |
| 20930101 | 934002 | `V934_BETA` | 40.0000 |
| 20930101 | 934003 | `V934_GAMMA` | 10.0000 |

`[20930101, 20930103)` 的 native fact oracle 总额为 `100.0000`，完整分组结果为
`V934_ALPHA=50.0000`、`V934_BETA=40.0000`、`V934_GAMMA=10.0000`；确定性 Top 2 为
`V934_ALPHA=50.0000`、`V934_BETA=40.0000`。目标方法同时核验：

- pre-aggregation base relation 必须真实执行，并与完整 native grouped oracle 等价；
- system slice 参数必须位于 outer TopN 参数之前；
- 物理 SQL 不得出现 `pa.product$categoryName` 或 `pa.salesAmount`；
- SQLite、MySQL 8、PostgreSQL 15、SQL Server 2022 必须真实执行 TopN planned SQL，并与
  native Top 2 oracle 等价；
- MySQL 5.7 必须在 base relation/oracle 成功后断言明确的 CTE/window capability refusal，
  不得用 assumption skip 或无断言 return 退出。

## Final Five-Database Diagnostic Ledger

下列 run root 与 raw XML 位于本地 ignored `target/`，仅用于本批实现诊断。所有 XML
精确记录同一个 testcase，且 `tests=1, failures=0, errors=0, skipped=0`。

| DB kind | Profile | Run root | Tests/F/E/S | Outcome | XML SHA-256 | Console SHA-256 |
|---|---|---|---:|---|---|---|
| sqlite | `sqlite,v934-sqlite` | `foggy-dataset-model/target/v934-step3-pivot-sqlite-r4` | 1/0/0/0 | TopN 实跑 | `88fb648494fe9e13d31939a78e5c80cd3171de7dda42cc779fa9db5a463cf5e4` | `3e45836160fe244ca7caced6bb5f4e0c59f39cd0a3aaa21d703980d4731e146d` |
| mysql57 | `docker` | `foggy-dataset-model/target/v934-step3-pivot-mysql57-r5` | 1/0/0/0 | capability refusal 断言通过 | `4917b8b3b76b02f89f66b18c055dd55078a8784695a6461f1e721b2b8e85dd16` | `fc6cdb901190073c017dc64464110455b62d23836e11323e1c5a4486f4db4db1` |
| mysql8 | `mysql8` | `foggy-dataset-model/target/v934-step3-pivot-mysql8-r3` | 1/0/0/0 | TopN 实跑 | `6397096ae4d801b47d76c1a7b3171d01e6a0fcf3a3e2009f0128f944cb3857d9` | `05df370b61ccaba6f9d151ddd1de6d113160bd08f0bb4d6305cca5f229c7ca7b` |
| postgres15 | `postgres` | `foggy-dataset-model/target/v934-step3-pivot-postgres15-r3` | 1/0/0/0 | TopN 实跑 | `6c12712f4d5c290408346b71c157d83dc00bd3babf1f4dbf01248d736fec45da` | `2f9a1155fb34506c02f231ed3340c2ef67517b0566d9ab367f8886aa865d9f04` |
| sqlserver2022 | `sqlserver` | `foggy-dataset-model/target/v934-step3-pivot-sqlserver2022-r3` | 1/0/0/0 | TopN 实跑 | `f9785069e743f8b6ae9816f19f358c0e0e9df9e6d2c3d723bef6cbd2ac71efba` | `1903eb0fe97ad7b86619b1bb7a58bf8165b1de294bcc2389c11bad8078a0bb9e` |

这些结果证明目标方法在五个当前 diagnostic endpoint 上没有 skip，并证明 dialect-aware
identifier quoting 与分页 SQL 可执行；它们没有证明正式矩阵中的其他 Pivot、cascade、
MultiDatabase、Mongo、Redis、Vector、MCP 或 DataViewer execution。

## Fixture Apply And Cleanup

外库 fixture 由 `manage-database-foundation-fixtures.sh` 显式管理。最终 apply 日志对
MySQL 5.7、MySQL 8、PostgreSQL 15、SQL Server 2022 逐库报告
`parity_rows=2 preagg_rows=4 preagg_total=100.0000 status=verified`。清理后复核日志逐库报告
`sentinel_rows=0 fixture_tables=0 status=cleaned`。

| Artifact | SHA-256 | Meaning |
|---|---|---|
| `target/v934-step3-pivot-fixtures-final/apply-verified.log` | `7f68c1f0e54cb5f9ddbe1c2d02eee84fc054c8a2ea532f146a3c7eb86b9233e0` | 四外库 apply 后精确 fixture 验证 |
| `target/v934-step3-pivot-fixtures-final/clean-verified.log` | `46cf33075e6636d1bf981fd555e86a52810ab73000228220c1a541d4c4db9f39` | 清理复核，残留行/表均为 0 |

SQLite fixture 由显式 `sqlite,v934-sqlite` profile 在独立 Spring context 内初始化；以上
外库日志和 SQLite context 都不是 fresh-volume authority run。

## Related Regression Diagnostics

这些回归用于约束本批 pre-aggregation requirement、物化列、grain、stage、final-stage 与
cache 行为，仍然不消费 Step 3 inventory：

| Suite | Tests/F/E/S | Diagnostic log | SHA-256 |
|---|---:|---|---|
| pre-aggregation unit bundle | 57/0/0/0 | `target/v934-step3-preagg-unit-bundle-r12/console.log` | `dae73b9dcc55512ed92d908412a91ce42f6be70ea706a0a973585a099ea6641e` |
| `PreAggregationIT` | 29/0/0/0 | `foggy-dataset-model/target/v934-step3-preagg-regression-r12/console.log` | `8b01aef2922eec08ad6d06133919f380965fe33752d156c440dd937dff8bb2ff` |
| query-stage regression | 24/0/0/0 | `target/v934-step3-query-stage-r2/console.log` | `b8e47994b1da16b9eb768fff3dfc85df2d88edfcd64b40599b59e74274ee2f70` |
| `PreAggregationL2CacheIT` | 1/0/0/0 | `target/v934-step3-preagg-l2-r5/console.log` | `031e85e836c015881afadc6f8b9b300962a169e7797d1a68fdf35d849ff1ede9` |
| demo reactor install | build success | `target/v934-step3-pivot-demo-install-r3/console.log` | `738bace2f560b6e4eac9f43e8ea42a589c71663d4e19494ebd27dfe26c2fa72e` |

## Source Snapshot

本批 `34` 个实现、测试、模型与 fixture/tool 文件的逐文件 SHA-256 位于
`step3-pivot-preagg-method-source-sha256.txt`；manifest 自身 SHA-256 为
`e2db4bb0d3fec7dbcc3f052ee2278361254f01b1fa3d77c6c5432af71b7da7cf`。
`sha256sum -c` 对全部 `34` 项返回 `OK`。

manifest 有意不包含 tracking/evidence 文档，也不包含 `target/` 下的 console/XML。上述
runtime digest 只帮助复核当前 diagnostic，clean 后不可恢复，也不得作为提交内 authority
archive。

## Authority Boundary And Remaining Work

本批可支持的 diagnostic claim 仅限 query matcher/rewriter/final-stage/hybrid read path 与
Pivot 定点执行。Addon `PreAggSqlBuilder` 的 DDL/refresh 路径仍存在独立物理列猜测与映射绕过，
由
`docs/9.3.4/workitems/BUG-step3-preagg-addon-materialization-contract.md`
跟踪；因此本记录不声明预聚合生成、DDL、刷新与查询的全生命周期物化契约已经统一。

本证据不满足 Step 3 exit，原因至少包括：

- 五个 lane 使用当前本地 SQLite context 和长期运行的固定外库容器/volume，不是由正式
  runner 创建并拥有的 fresh/run-scoped volume；
- 仅运行一个 targeted method，没有通过版本化正式 selector gate 和 exact execution-set
  collector；
- 没有覆盖 Step 3 完整 required inventory、required negative probes、stale/missing XML
  probes 与全套 required skip=0；
- raw XML/log 仍在 ignored `target/`，没有正式 run-owned archive、summary、manifest 与
  collector 结论；
- 因此本批 `inventory_consumption` 必须保持 `0`，不得据此减少 required deferred
  execution，也不得签收 Step 3 或进入 Step 4。

下一步必须由正式 9.3.4 Step 3 runner 在 fresh volumes 上重放目标 selector 及其余 required
selectors，验证精确 report set、S0、fixture before/after/cleanup 和负向探针；Addon
DDL/refresh follow-up 也必须单独闭环，之后才能形成相应 authority evidence。
