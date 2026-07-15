---
doc_role: step-exit-evidence
doc_purpose: Record the exact same-commit authority that closes 9.3.4 Step 3.
version: 9.3.4
step: 3
status: passed
decision: step4-ready-not-started
tested_commit: ce3d70c391c7b8bd8046fe66dde0ad568d66601e
authority_run_id: step3-required-20260716-final-r4
recorded_at: 2026-07-16
---

# Step 3 Required Matrix Exit

## Scope and Decision

本记录只签收 9.3.4 Step 3 的五数据库与 required external correctness matrix。正式
父运行在同一已提交、干净 HEAD 上完成并返回 0；Step 3=`passed`，Step 4 入口=
`ready / not-started`。本记录不创建 JaCoCo coverage authority，不签收 9.3.4 版本，
也不把 9.3.5 标为 ready。

## Authority Identity

- tested commit：`ce3d70c391c7b8bd8046fe66dde0ad568d66601e`
- run：`step3-required-20260716-final-r4`
- execution window：`2026-07-15T20:18:24Z` → `2026-07-15T20:42:19Z`
- parent contract SHA-256：
  `f2bd52df7ed2829051ad263f97d560d3f8babe048d25864edb725eb671ba4d1b`
- deferred inventory SHA-256：
  `89190db9370fe3117d5316c72835efffa577d132d39cef9b5d9ae3558afa7601`
- parent source seal before/after：
  `350e671354f99c76237913619558255cde18131872cb0807ff227545614b9a60`
- outer marker SHA-256：
  `e3a5f1fdaa13299f8649f85fbdcb8709cc8efaa216aeae3e3ea70f0e2335710e`
- final manifest SHA-256：
  `9040bff263101ed7ad33dbc4681bbf37f48e39b00957d2bf8224fb506afa5282`
- candidate manifest SHA-256：
  `6b1e5f3502dd2e666e5546ecf3c6469e9b5060bdafa8e7d4e1068fd41688cfa4`

Canonical ignored run roots：

- `target/v934-step3-required-matrix/runs/step3-required-20260716-final-r4/`
- `target/v934-step3-database-matrix/runs/step3-required-20260716-final-r4/`
- `target/v934-step3-external-matrix/runs/step3-required-20260716-final-r4/`
- `target/v934-step3-preagg-addon/runs/step3-required-20260716-final-r4/`

父级 contract validation、final verifier 和 candidate verifier 均在运行结束后独立重放
通过；`source-before.tsv` 与 `source-after.tsv` 字节一致。

## Required Result

| Partition | Reports | Testcase | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| database matrix | 29 | 370 | 0 | 0 | 0 |
| required external | 16 | 76 | 0 | 0 | 0 |
| exact required union | 45 | 446 | 0 | 0 | 0 |

Union ledger：database keys=`29`、external keys=`16`、gap/overlap/extra=`0/0/0`。
45 个 required execution key 各由一个 child authority 消费，未跨 run 拼接。

## Five-Database Matrix

| Database cell | Variants | Reports | Testcase | Result |
|---|---:|---:|---:|---|
| SQLite | 1 | 5 | 50 | F0/E0/S0 |
| MySQL 5.7 | 1 | 5 | 50 | F0/E0/S0 |
| MySQL 8 | 2 | 6 | 105 | F0/E0/S0 |
| PostgreSQL 15 | 2 | 8 | 115 | F0/E0/S0 |
| SQL Server 2022 | 1 | 5 | 50 | F0/E0/S0 |

五库均使用 fresh/run-scoped storage，完成 product/version/physical coordinate、
catalog/schema、sentinel、fixture before/after、QueryFacade/native parity、capability
与 cleanup 校验。数据库状态负向=`18/18`，report/final-bundle 负向=`14/14`；数据库
final manifest SHA-256=
`366c0306b973419b6cbdaff6ba22ce56f230706a509428d9130f5a722a61f014`。

## Required External Matrix

| Lane | Reports | Testcase | Result |
|---|---:|---:|---|
| Redis | 2 | 3 | F0/E0/S0 |
| Mongo/DataViewer | 4 | 30 | F0/E0/S0 |
| MCP/MySQL 5.7 | 8 | 23 | F0/E0/S0 |
| Vector/Milvus | 2 | 20 | F0/E0/S0 |

External 总计=`16/76/F0E0S0`，统一 outer 下的 7 个 variant 全部重验通过；contract
SHA-256=
`bd6caec69adb3a9ca5a615c8bda4d4919e53dcdfd230b04c2eb6d1824e9526c7`，final
manifest SHA-256=
`53cf60cc26b8680fc1a49dd8b3a8a32090666a0c58471148bf6de383889d4210`，candidate
SHA-256=
`b2eac65e4452b206e640d746551e643929d41c71f30fc96ac46fa578964d2db4`。
External report negatives=`12/12`，sensitive negatives=`24/24`，Redis resource-state
negatives=`4/4`。

MySQL fixture 证明 69 tables / 69 primary keys，关键行数包含
`dim_date=1461`、`dim_product=500`、`dim_customer=1000`、`dim_store=50`、
`fact_sales=3088`、`fact_order=20005`、`fact_return=316`、`compose_join=10`；direct
report=`23/23`、business error=`0`，内容与只读 grants before/after 一致。

## Required Companion and Optional Execution

PreAgg Addon lifecycle 是 required companion，但按冻结契约不进入 45/446：

- SQLite=`1 report / 3 testcase`
- MySQL 5.7=`1 report / 3 testcase`
- total=`2 reports / 6 testcase / F0E0S0`
- resource residue=`0/0/0`
- contract SHA-256=
  `27d8abe4944c3df8694ed37a89388d44058a9fed71080855ab524ebde9ad54e3`
- candidate SHA-256=
  `2634a23e64aee150a574998e6af99084c8b7e5ff3064bda2b25c1c47ff5e1741`

Optional LLM execution disposition=`reviewed-optional-excluded`：owner=
`foggy-dataset-mcp`，execution=`AiToolsIT$AiModelCallTest`，未执行、未计为 passed、
不进入 45/446。原因是第三方模型质量非确定性且不属于 required direct-tool correctness
contract；next review=`2026-08-31`。

## Fail-Closed and Cleanup Evidence

- parent synthetic negatives=`17/17`
- database state negatives=`18/18`
- database report negatives=`14/14`
- external report negatives=`12/12`
- external sensitive negatives=`24/24`
- Redis resource-state negatives=`4/4`
- top-level sensitive scan=`passed`
- parent/Addon/external container-volume-network residue=`0/0/0`
- 原有四个 demo 数据库容器在父运行退出后恢复为相同 container ID、named volume 与
  `running/healthy` 状态；它们未被用于正式 fresh-storage identity。

同一 tested HEAD 另行补跑
`mvn -pl foggy-core -Dtest=MultiThreadExecutorTest test -q`，结果=`3/3/F0E0S0`，
作为 Step 4 启动前的 deterministic completion/error/shutdown 分支补强，不计入
Step 3 的 45/446。Companion provenance 见
`docs/9.3.4/evidence/step-3/step3-multithread-prerequisite-evidence-20260716.md`；raw
XML SHA-256=
`5d3ba9ff2778886160262b499999753f98a0b9251f5a76f6b9f86c45d723291a`。

## Excluded Runs

- `step3-required-20260716-final-r1`：MySQL health 早于业务用户 readiness；fail closed，
  无父 candidate，零残留。
- `step3-required-20260716-final-r2`：业务用户可登录但 final init fixture 尚未完成；
  fail closed，无父 candidate，零残留。
- `step3-required-20260716-final-r3`：原生 DATE/PreAgg fixture 已变化而 external MySQL
  旧 hash 拒绝；fail closed，无父 candidate，零残留。
- `external-mysql-content-hash-20260716-r1/r2`：只用于独立复算 canonical content
  hash，不是 authority。
- `external-mysql-native-date-20260716-r1`：runner hash 已更新但 report verifier 仍是
  旧 hash；无 candidate，零残留。
- `external-mysql-native-date-20260716-r2`：production standalone 绿色诊断，用于在
  final-r4 前验证新 contract；不是父级 45/446 authority。

上述 failed/diagnostic/superseded run 均未拼入正式结果。

## Evidence Boundary

- watermark 只在事务成功后发布到运行时对象；重载/重启后为空，首次刷新走 FULL；
  本 Step 不宣称 durable watermark persistence。
- 本证据不宣称刷新后缓存立即一致。
- Addon lifecycle 只证明 SQLite/MySQL 5.7 的 create/full/incremental/query parity，
  不扩大为五库、Controller 或 Cache 全生命周期。
- scheduler 证明当前结果/future 状态隔离，不宣称任意 `Object watermark` 深拷贝。
- Mongo loader 的生产 JDBC dialect 解耦仍是下游 follow-up。
- Step 3 correctness 未生成可接受的 JaCoCo exec；Step 4 必须带 agent 重跑全部
  required lanes。
- Step 5 immutable archive、Step 6 CI/branch protection 与 Step 7 version authority
  均未开始。

## Exit Decision

Step 3=`passed`。开启 Step 4 的 correctness、exact inventory、negative、resource
cleanup、optional disposition、Addon companion 与并发前置条件全部满足；Step 4=
`ready / not-started`。9.3.4 继续 `in-progress`，9.3.5 继续 `queued`。
