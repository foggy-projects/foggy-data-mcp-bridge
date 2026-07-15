---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-AGGREGATE-JOIN-SNAPSHOT-DEFAULT-SKIP
severity: major
status: closed
reproduction_status: confirmed
test_strategy: unit-test
automation_decision: required
owner: foggy-dataset-model
---

# Aggregate Join snapshot 测试默认跳过全部契约断言

## Background

9.3.4 Step 2 r5 Surefire authority 的完整 fresh XML 取证发现，
`JavaQueryModelAggregateJoinSnapshotTest#shouldProduceSnapshot` 在未传
`-Dfoggy.parity.snapshot=true` 时通过 JUnit assumption 跳过。report gate 首先报告
Embedding skip，因此该第二项只在全量扫描 732 份 XML 后显现。

## Reproduction

```text
report: foggy-dataset-model/target/surefire-reports/
        TEST-com.foggyframework.dataset.db.model.parity.JavaQueryModelAggregateJoinSnapshotTest.xml
Tests run: 1, Failures: 0, Errors: 0, Skipped: 1
reason: Assumption failed: set -Dfoggy.parity.snapshot=true to export aggregate join parity snapshot
report SHA-256: 6593fee5fc67e718811178510bca9ebdee45dc752becb6ff2d9b03c2b47a40e1
```

r5 unit 运行的完整原始集合为 `677 positive + 55 structural = 732 raw`，无
missing/extra/failure/error，但 Embedding 与本节点合计 `Skipped: 2`，因此整个 run
不得作为 authority，也不得与后续结果拼接。

## Expected vs Actual

- 期望：普通 unit lane 始终执行 29 个 aggregate-join case 的构造与断言；只有将
  JSON 写到 `target/parity` 的副作用保持显式 opt-in。
- 实际：export 开关放在方法首行 assumption，关闭写文件的同时也跳过所有查询、权限、
  SQL shape、diagnostic 与结果断言。

## Impact Scope

- 仅调整既有 test method：默认执行全部断言，只有 snapshot 文件写入受
  `foggy.parity.snapshot` 控制。
- 保持 1 个 discovery node、Surefire ownership 与 execution key 不变；不改生产代码，
  不在默认构建刷新 parity artifact。

## Test Strategy

1. 保留 r5 authority `1/0/0/1` 作为 RED。
2. 默认 focused 运行要求 `1/0/0/0`，并验证不会创建新的 snapshot 文件。
3. 显式 `-Dfoggy.parity.snapshot=true` 仍要求 `1/0/0/0` 且生成 29-case JSON。
4. 由 successor amendment 封存源码/test-classes hash，最终重跑完整 Surefire authority。

## Code Inventory

- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/parity/JavaQueryModelAggregateJoinSnapshotTest.java`
  - 移除方法级 assumption；只对 JSON export 分支读取 opt-in property。

## Fix Checklist

- [x] 全量 fresh XML 取证确认第二个 skip。
- [x] 确认默认 skip 同时跳过 29-case 契约断言。
- [x] 默认执行断言且不刷新 snapshot artifact。
- [x] 默认/显式 export focused 运行均零 skip GREEN。
- [x] successor amendment 完成 hash-sealed 双审。
- [x] Step 2 Surefire authority exact-set GREEN。

## Verification

默认 focused 首轮在未写出 snapshot 的前提下执行全部 29 个 case；它先暴露并推动修复
不可变 calculated-fields list 的生产 BUG。生产修复后，默认与显式 export 两轮均为
`1/0/0/0`；显式轮生成 `querymodel-aggregate-join-3`、29 cases。最终 XML SHA-256：

```text
44dc1a41796c74c1cf91dd1c160ef44cfa98cb9b9294c83197629525204bf828
```

## References

- `docs/9.2.0/workitems/query-model-aggregate-join.md`
- `target/v934-step2-unit/runs/step2-unit-r5-20260715/run.marker`
- `scripts/v934/step2_report_tool.py`
- `docs/9.3.4/evidence/step-2/step2-successor-r8e-independent-review-20260715.md`
- `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`
