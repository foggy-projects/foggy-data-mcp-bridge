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
- actual：此前只有长期 demo service 上的零散诊断，没有 7-variant contract/collector；
  Mongo 与 Vector 存在 assumption/disabled 伪绿，MCP direct 汇总没有断言所有 case
  均成功，optional LLM 也可能被宽泛 selector 意外带入 required lane。

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
- [ ] provision fresh MySQL57 and execute exact 8/23/S0 without optional LLM
- [ ] replace Vector assumption/disabled paths with a deterministic local embedding fixture
- [ ] provision fresh Milvus/etcd/MinIO and execute exact 2/20/S0
- [ ] finalize optional LLM reviewed disposition
- [ ] merge external 16/76 with database 29/370 as exact Step 3 45/446/F0/E0/S0

## Evidence Boundary

The first Redis diagnostic used a fresh digest-pinned Redis container on a random loopback port.
Because the image declares `/data` as a volume, Docker implicitly created an anonymous volume;
the diagnostic and its manually removed volume are not authority evidence. Formal attempt `r1`
failed at the mount identity check and was excluded. Later diagnostic candidates used one explicit,
run-labelled named volume and proved exact `2/3/F0/E0/S0`; only the post-commit candidate may be
referenced as the current Redis subset evidence.

The direct-tool fail-closed amendment intentionally exposed a real `META-001` catalog refresh
failure instead of signing `22/23` as green. Its diagnosis and the isolated ecommerce-bundle
unblock are tracked in `BUG-step3-mysql57-direct-default-catalog-assembly.md`; no MySQL57 required
result is inferred from the source amendment.

Committed Redis and Mongo candidates independently close `2/3` and `4/30`; their two run-local
manifests cannot be spliced into a full external authority. No MySQL57/Vector result may be inferred
from either subset. Long-lived demo containers remain diagnostic-only.

## References

- `docs/9.3.4/implementation-plan.md`, Step 3
- `docs/9.3.4/test/test-ci-evidence-chain-test-plan.md`, Step 3
- `scripts/v934/successor/step2/deferred-step3.tsv`
- `scripts/v934/step3/external-matrix-contract.json`
- `scripts/verify-v934-external-redis.sh`
- `scripts/verify-v934-external-mongo.sh`
- `docs/9.3.4/workitems/BUG-step3-mysql57-direct-default-catalog-assembly.md`
- `docs/9.3.4/workitems/BUG-step3-mongo-loader-jdbc-dialect-dependency.md`
- `docs/9.3.4/evidence/step-3/step3-external-redis-runner-candidate-20260715.md`
- `docs/9.3.4/evidence/step-3/step3-external-mongo-runner-candidate-20260715.md`
