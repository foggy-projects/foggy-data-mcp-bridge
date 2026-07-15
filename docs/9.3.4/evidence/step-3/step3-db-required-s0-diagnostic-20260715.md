---
doc_role: implementation_evidence
doc_purpose: Record the fixed-container diagnostic that removes required database false-green paths.
version: 9.3.4
step: 3
status: diagnostic-passed
authority_status: not-authority
scope: database-required-selectors
inventory_consumption: 0
created_at: 2026-07-15
---

# Step 3 数据库 required S0 诊断证据

## Decision

Step 3 冻结的数据库侧 required selector 已在 SQLite、MySQL 5.7、MySQL 8、
PostgreSQL 15、SQL Server 2022 上完成一次固定 endpoint 诊断重放。最终本地 raw XML 为：

```text
reports=29 tests=370 failures=0 errors=0 skipped=0
```

本批状态为 `diagnostic-passed`，但明确是 `not-authority`，对 frozen deferred inventory
的消耗仍为 `0`。数据库使用长期运行的 demo container/volume，SQLite 也不是由正式
runner 创建并拥有的 run-scoped artifact；因此结果不能替代 fresh-storage runner、
exact collector、identity/archive manifest 与负向探针。

## False-green Repairs

本批只修改 required database test contract，没有用能力分支缩小 required 集合：

- `PivotSqlParityIT` 移除 assumption；MySQL 5.7 单 TopN 明确断言 memory fallback 日志，
  per-group TopN 使用可移植 native base oracle；parent-share、baseline-ratio、grand-total
  均比较完整 cardinality、结构化 key set 与数值，不再用空集合或局部样例通过。
- `PivotCascadeGenerateSqlParityIT` 移除 assumption；MySQL 5.7 对不支持的 cascade SQL
  精确断言 typed `PIVOT_CASCADE_SQL_REQUIRED` refusal；支持能力的数据库必须执行正向
  request，并按完整结构化 tuple 比较结果，重复 key 直接失败。
- `MultiDatabaseQueryTest` 删除 empty fixture pass/log 与无断言 capability return；分页、
  offset、aggregate、join、subquery、null、CTE、window、LAG、moving average 均有确定性
  结果断言。MySQL 5.7 CTE/window refusal 校验 SQLState `42000`/vendor `1064`；SQLite
  `STDDEV` refusal 校验 SQLite error/code，而不是 skip。
- oracle key 不再使用字符串拼接或覆盖式 merge；数字先规范化后进入结构化 tuple，
  cardinality、key set、duplicate 与 value parity 分开校验。

## Diagnostic Ledger

五个标准 lane 各执行同一组 `5` reports / `50` testcases：

| Suite | Tests per DB |
|---|---:|
| `PivotSqlParityIT` | 23 |
| `MultiDatabaseQueryTest` | 18 |
| `RequiredDatabasePreflightIT` | 1 |
| `PivotCascadeGenerateSqlParityIT` | 7 |
| `RequiredDatabaseQueryFacadeParityIT` | 1 |

标准 lane 合计 `25 reports / 250 tests / F0/E0/S0`。冻结 inventory 另要求两个定向
variant：MySQL 8 的 `PivotIT` 为 `1 report / 55 tests / F0/E0/S0`；PostgreSQL 15 的
`PivotIT`、`DslCteResultStageWindowIT`、`DslCteRelationMetricFixtureIT` 为
`3 reports / 65 tests / F0/E0/S0`。最终精确合计：

| DB kind | Standard | Targeted | Total tests | F/E/S |
|---|---:|---:|---:|---:|
| sqlite | 5 reports / 50 | 0 | 50 | 0/0/0 |
| mysql57 | 5 reports / 50 | 0 | 50 | 0/0/0 |
| mysql8 | 5 reports / 50 | 1 report / 55 | 105 | 0/0/0 |
| postgres15 | 5 reports / 50 | 3 reports / 65 | 115 | 0/0/0 |
| sqlserver2022 | 5 reports / 50 | 0 | 50 | 0/0/0 |
| **total** | **25 / 250** | **4 / 120** | **370** | **0/0/0** |

本地 ignored run root 为 `target/v934-step3-db-s0-diagnostic-r1/`。按相对路径排序后，
29 份 XML 的逐文件 `sha256sum` ledger stream SHA-256 为
`4ec8df09d6d2429e52ce46934ac1cfafe385e0ecfff7397bf121086d218945f6`。
该 digest 只支持本地诊断复核，不是提交内 authority archive。

## Excluded Diagnostics

- PostgreSQL 第一轮完整 selector 在 baseline-ratio oracle 上失败：维度 JOIN 不匹配的
  fact 行形成 `month=NULL` 分组；数据库 `MIN/MAX` 自然忽略 NULL，而 Java oracle 错把
  NULL 当非法。修复后 baseline 选择与 SQL aggregate 语义一致，同时 NULL 分组仍参与
  完整 ratio parity；失败轮不得拼接进最终 ledger。
- 本批之前的 empty fixture、assumption 和 early-return 绿色均被判为 false green，不能
  作为 Step 3 证据。

## Fixture Lifecycle

诊断前，外库 helper 对四库逐一确认 sentinel、parity 与 preagg fixture；最终 apply 输出
均为 `sentinel_rows=1 parity_rows=2 preagg_rows=4 preagg_total=100.0000`。29 份 XML
冻结后立即执行 `clean all`，四库均返回：

```text
sentinel_rows=0 fixture_tables=0 status=cleaned
```

helper 只管理 test-owned fixture，并明确声明不是 fresh authority provisioner。

## Source Snapshot

三个 test contract 文件的 SHA-256 位于
`step3-db-required-s0-diagnostic-source-sha256.txt`。它不包含 Addon DDL/refresh 的当前
实验改动，也不包含 docs/target artifact。

## Remaining Boundary

本批尚未完成：

- fresh/run-scoped 五库 provision、image/JAR byte identity、run-owned cleanup trap；
- exact 46-execution Step 3 runner/report verifier 与 stale/missing/wrong-result negatives；
- 16 个 required external execution 和 1 个 optional LLM reviewed disposition；
- Addon create/full/incremental/query lifecycle。Addon 初始单测实现已被独立复核抓到
  COUNT、公式/semantic-scale、SQLite timestamp default、整数 date-key watermark 等真实
  兼容风险，继续由独立 workitem 跟踪，未进入本批提交声明。

因此 Step 3 保持 `in-progress`，不得进入 Step 4。
