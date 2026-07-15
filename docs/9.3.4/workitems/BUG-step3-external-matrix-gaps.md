---
type: bug
bug_source: acceptance-found
version: 9.3.4
ticket: BUG-934-STEP3-EXTERNAL-MATRIX-GAPS
severity: critical
status: in-progress
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: cross-module
---

# Step 3 外部 16 项缺少 fail-closed authority

## Background

Step 2 confirmed successor 将 `46` 个 execution 精确 deferred 到 Step 3：

- database matrix：`29 reports / 370 testcase`；
- required external：`16 reports / 76 testcase`；
- optional LLM：`1 report / 1 testcase`。

required external 的 16 项分布在 Redis、Mongo/DataViewer、MCP/MySQL57 与 Vector，
不是 16 次 Maven invocation；最小运行编排是 7 个 variant。Step 3 的最终 required
并集必须精确为 `45 reports / 446 testcase / F0/E0/S0`。

## Reproduction

权威输入：

```text
scripts/v934/successor/step2/deferred-step3.tsv
SHA-256=89190db9370fe3117d5316c72835efffa577d132d39cef9b5d9ae3558afa7601
```

逐项 join `deferred-step3.tsv` 与 `discovery-inventory.tsv` 可稳定得到：

| Variant | Required reports | Testcase | Owner |
|---|---:|---:|---|
| `redis7` | 1 | 1 | `addons/foggy-dataset-model-cache` |
| `redis7-sqlite` | 1 | 2 | `addons/foggy-dataset-model-cache` |
| `mongo6` | 4 | 30 | model-mongo + data-viewer |
| `mysql57-mcp` | 5 | 14 | `foggy-dataset-mcp` |
| `mysql57-direct` | 2 | 7 | `foggy-dataset-mcp` |
| `mysql57-compose` | 1 | 2 | `foggy-dataset-mcp` |
| `milvus24-embedding` | 2 | 20 | model-vector + dataset-vector |

## Expected vs Actual

- expected：每个 variant 使用 fresh/run-owned external service，绑定镜像/服务身份、
  raw Failsafe XML、exact report/testcase、F0/E0/S0、资源清理与 sensitive scan；全量
  collector 与 confirmed deferred inventory exact match。
- actual：7-variant contract/collector、四个 run-owned lanes 与 single-outer `16/76`
  authority 已完成；此前 Mongo/Vector assumption/disabled、MCP direct 汇总及 optional LLM
  selector 伪绿均已在 required external lane 关闭。当前 workitem 仍因 resource-state
  negatives、optional LLM reviewed disposition 和 database+external `45/446` union 保持开放。

## Confirmed False-green Paths

1. `MongoTestSupport` 在 Mongo 不可用时使用 assumption，裸 Maven 可得到 skipped XML。
2. `MongoListPresetStoreIT` 受环境变量门控，runner 漏传时会 skip。
3. `AiToolsIT` 的 direct report 只记录 `passedCount`，未强制其等于 case 总数。
4. `VectorIT` 缺 gitignored `test-config.properties` 时 assumption-skip 15 项。
5. `VectorStoreIT` 带 class-level `@Disabled`，5 个 required testcase 必然 skip。
6. Vector README 提到的 `application-test.yml` 当前不存在；embedding 仍依赖外部
   API/密钥，无法形成 deterministic authority。

## Test Strategy

1. 冻结 7 variants、16 reports、76 testcase，以及 deferred/discovery/source manifest
   SHA；collector 拒绝 missing/extra/duplicate/stale/F/E/S/cross-run。
2. 按 Redis → Mongo/DataViewer → MCP/MySQL57 → Vector 顺序建立 fresh cells。
3. 每个 Maven invocation 使用 exact `-Dit.test`，并让非 owner reactor module 的
   failIfNoSpecifiedTests 只在外层 collector exact-set 约束下放宽。
4. required lane 禁止 assumption、`@Disabled`、无断言 early return；unsupported
   capability 必须是精确断言的 refusal，不是 `<skipped>`。
5. `mysql57-direct` 使用 method/nested selector 排除 optional `AiModelCallTest`；optional
   LLM 单独保留 reviewed disposition。
6. Step 3 只产 correctness XML；JaCoCo external exec 必须到 Step 4 全 lane 重跑时生成。

## Fix Checklist

- [x] freeze exact external contract as 7 variants / 16 reports / 76 testcase
- [x] prove fresh Redis feasibility as 2 reports / 3 testcase / F0/E0/S0 / residue 0
- [x] land Redis run-scoped runner/collector and exact report/candidate negatives
- [x] prove real Redis INT/TERM/HUP durable cleanup as 130/143/129 with zero residue
- [ ] add Redis wrong identity/mount, dirty-state and cleanup-failure resource negatives
- [x] close Mongo unavailable assumption and DataViewer environment false green in the run-scoped Mongo runner
- [x] provision fresh Mongo 6 and execute exact 4/30/S0
- [x] prove real Mongo INT/TERM/HUP durable cleanup as 130/143/129 with zero residue
- [x] make MCP direct nodes fail when any required direct case fails or executes zero cases
- [x] eliminate MCP/Compose non-null and double-empty assertion false greens without changing nodes
- [x] implement deterministic MySQL57 time/RAND seed, exact 69-table content hash,
  distinct root/app credentials, SELECT-only grants and curated 32-QM bundle gates
- [x] provision fresh MySQL57 and execute exact 8/23/S0 without optional LLM
- [x] replace Vector assumption/disabled paths with a deterministic local embedding fixture
- [x] provision fresh Milvus/etcd/MinIO and execute exact 2/20/S0
- [x] prove real Vector INT/TERM/HUP durable cleanup as 130/143/129 with zero residue
- [x] replay and merge exact external 16/76 under one outer marker/HEAD/contract
- [x] prove shared outer INT/TERM/HUP parent+child durable cleanup as 130/143/129
- [x] finalize optional LLM reviewed disposition
- [ ] merge external 16/76 with database 29/370 as exact Step 3 45/446/F0/E0/S0

## Optional LLM Reviewed Disposition

- reviewed_at: `2026-07-16`
- decision: `remain-optional / not-executed / excluded-from-required-union`
- execution: `AiToolsIT$AiModelCallTest`
- owner: `foggy-dataset-mcp`
- rationale: third-party model quality is non-deterministic and is not part of the required
  direct-tool correctness contract
- next_review: `2026-08-31`

The reviewed tuple is bound by both `deferred-step3.tsv` and
`external-matrix-contract.json`. It contributes neither a required report nor a testcase to the
Step 3 `45/446` union and cannot be used to hide a required failure.

## Evidence Boundary

The first Redis diagnostic used a fresh digest-pinned Redis container on a random loopback port.
Because the image declares `/data` as a volume, Docker implicitly created an anonymous volume;
the diagnostic and its manually removed volume are not authority evidence. Formal attempt `r1`
failed at the mount identity check and was excluded. Later diagnostic candidates used one explicit,
run-labelled named volume and proved exact `2/3/F0/E0/S0`; only the post-commit candidate may be
referenced as the current Redis subset evidence.

The direct-tool fail-closed amendment intentionally exposed a real `META-001` catalog refresh
failure instead of signing `22/23` as green. Its diagnosis and curated ecommerce-bundle unblock
are tracked in `BUG-step3-mysql57-direct-default-catalog-assembly.md`. The independently observed
MCP/Compose assertion false greens are tracked in
`BUG-step3-mysql57-mcp-false-green-assertions.md`; no MySQL57 required result is inferred from
source or runner amendments.

Committed Redis, Mongo, MySQL and Vector subset candidates independently closed `2/3`, `4/30`,
`8/23` and `2/20`, but their four run-local `complete=false` manifests remain non-spliceable history.
Current complete external subset is `external-matrix-candidate-47d1afd7-r1`: one outer marker,
HEAD and contract binds all four children and seven variants, with exact
`16 reports / 76 testcase / F0/E0/S0` and `complete=true`. Its candidate and repaired
INT/TERM/HUP signal group are recorded in
`docs/9.3.4/evidence/step-3/step3-shared-external-matrix-candidate-20260716.md`.
Long-lived demo containers remain diagnostic-only。

MySQL formal attempt `external-mysql-candidate-664c8f21-r1` failed closed before JUnit because
MySQL 5.7 does not expose `information_schema.ROUTINE_PRIVILEGES`; it emitted no candidate and left
container/volume residue `0/0`. The compatible grant query was committed as `97f1cbfa`, and only
`external-mysql-candidate-97f1cbfa-r2` is accepted for the MySQL progress ledger.

Vector identity diagnostics first exposed a shell boolean-status bug; the first full variant then
exposed MySQL Connector selecting protobuf `3.11.4` against Milvus SDK `2.5.8`, followed by an
unrelated JDBC auto-configuration leak and the actual Base64-encoded metadata response shape. The
six failed/diagnostic attempts all wrote durable failed status, emitted no candidate and left
container/volume/network residue `0/0/0`. Only
`external-vector-candidate-dd7d8fc3-r1` is accepted for the Vector progress ledger.

## 2026-07-16 Formal MySQL Fixture Contract Propagation

正式父运行 `step3-required-20260716-final-r3` 已通过 Addon companion、DB 状态
`18/18` 和五库 `29/370/F0E0S0`，随后在 external MySQL `fixture-before` 被内容哈希门
拒绝。根因是本轮 PreAgg 修复把 `dim_date.full_date` 升级为原生 DATE，并补齐月粒度
`product_key`、日客户渠道 `full_date` 与 ISO DATE watermark；MySQL 69 表
`mysqldump-data-v1` 的旧冻结哈希因此不再代表当前 canonical fixture。

两个独立 run-owned 诊断均得到同一新哈希
`21919cc1f9c73fa05f80eb51ae86b939e7f7db4c7764b2841f1c4301188c256e`；失败运行和诊断
均无容器/卷残留，且不进入 authority。runner 现冻结该值，并在未来漂移时输出
actual/expected 便于审计。只有 production runner 在新 committed HEAD 上继续通过
69-table/69-primary-key、关键行数、before=after、read-only grants、`8/23/S0` 和父级
`16/76` 后，才可关闭此传播缺口。

首次 production rerun `external-mysql-native-date-20260716-r1` 随后通过 runner 的 raw
content gate，并生成 69 表、69 主键及全部关键行数均匹配的 canonical snapshot；它被
report tool 内仍冻结的旧 content hash 再次拒绝。该 run 同样无候选、零残留。第二个
冻结点已同步为同一新哈希，后续必须由 production runner 同时通过 snapshot verifier
与完整 variants 才能成为有效证据。

## References

- `docs/9.3.4/implementation-plan.md`, Step 3
- `docs/9.3.4/test/test-ci-evidence-chain-test-plan.md`, Step 3
- `scripts/v934/successor/step2/deferred-step3.tsv`
- `scripts/v934/step3/external-matrix-contract.json`
- `scripts/verify-v934-external-redis.sh`
- `scripts/verify-v934-external-mongo.sh`
- `scripts/verify-v934-external-mysql.sh`
- `scripts/verify-v934-external-vector.sh`
- `docs/9.3.4/workitems/BUG-step3-mysql57-direct-default-catalog-assembly.md`
- `docs/9.3.4/workitems/BUG-step3-mysql57-mcp-false-green-assertions.md`
- `docs/9.3.4/workitems/BUG-step3-mongo-loader-jdbc-dialect-dependency.md`
- `docs/9.3.4/evidence/step-3/step3-external-redis-runner-candidate-20260715.md`
- `docs/9.3.4/evidence/step-3/step3-external-mongo-runner-candidate-20260715.md`
- `docs/9.3.4/evidence/step-3/step3-external-mysql-runner-candidate-20260715.md`
- `docs/9.3.4/evidence/step-3/step3-external-vector-runner-candidate-20260715.md`
- `docs/9.3.4/evidence/step-3/step3-shared-external-matrix-candidate-20260716.md`
