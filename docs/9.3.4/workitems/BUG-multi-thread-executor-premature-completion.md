---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-MULTI-THREAD-EXECUTOR-PREMATURE-COMPLETION
severity: major
status: closed
reproduction_status: confirmed
test_strategy: unit-test
automation_decision: required
owner: foggy-core
---

# MultiThreadExecutor 提前判定完成并丢弃队列任务

## Background

9.3.4 Step 2 全量 Surefire authority 运行到 `addons/foggy-fsscript-client` 时，
`FsscriptClientProxyTest#test3` 提交 100,000 个并发任务，但最终只收集到 2,446 个结果。
2026-05-30 的测试硬化已经把结果容器改为同步集合并增加完整数量断言，因此本次失败不再被
后台线程异常或部分结果伪装成绿色。

失败运行：

```bash
scripts/verify-v934-unit.sh step2-unit-r3-20260714
```

结果：`Tests run: 10, Failures: 1, Errors: 0, Skipped: 0`，失败方法耗时仅
`0.305s`，断言为 `expected: <100000> but was: <2446>`。

## Root Cause

`MultiThreadExecutor.waitAllCompleted` 的完成判定有两个方向错误：

- 未设置 `total` 时只检查 `ThreadPoolExecutor#getActiveCount() != 0`。该值是瞬时近似值，
  已提交任务仍在 queue、worker 尚未进入 active 状态时可以为 `0`；方法会立即返回并执行
  `shutdownNow()`，尚未执行的任务被丢弃。
- 设置 `total` 时返回 `completedTaskCount == total`，但调用方在 `while` 中把 `true`
  当作“仍需等待”，条件含义正好反向；未完成时反而立即退出。

失败报告中没有业务调用异常，且“极短耗时 + 大量任务缺失”与上述提前退出路径一致，
不是 Fsscript proxy 的线程安全失败。

## Expected vs Actual

- 期望：`waitAllCompleted` 在调用前已提交的任务全部结束后才返回；`shutdown=true` 不得清空
  尚未执行的任务。
- 实际：activeCount 的瞬时空窗或反向的 total 条件会提前返回，随后
  `shutdownNow()` 丢弃 queue。

## Test Strategy

1. 原位强化现有 `test3`，使用已有 `(corePoolSize, maximumPoolSize, total)` 构造器，
   在不增加 discovery node 的前提下确定性触发反向 total 条件。
2. 修复前运行同一 focused method，保留稳定 RED。
3. 最小修复完成判定后，重复运行 focused method 与整个 class，要求零失败/错误/跳过。
4. 最终由 Step 2 Surefire authority 的 exact-set、freshness 与零 skip 契约收口。

## Code Inventory

- `foggy-core/src/main/java/com/foggyframework/core/thread/MultiThreadExecutor.java`
  - 以调用时已提交任务总数/显式 total 与 completed count 比较，禁止依赖瞬时 activeCount。
- `addons/foggy-fsscript-client/src/test/java/com/foggyframework/fsscript/client/test/support/FsscriptClientProxyTest.java`
  - 现有 `test3` 使用显式 total，保持原 test node 和并发 proxy 断言。

## Fix Checklist

- [x] 全量 Surefire authority 捕获失败。
- [x] 确认结果集合与完整数量断言已经线程安全。
- [x] 定位 activeCount 空窗与 total 条件反向根因。
- [x] focused 确定性 RED。
- [x] 最小生产修复。
- [x] focused method/class GREEN。
- [x] r8e successor 对源码与 classpath 影响完成 hash-sealed 复核。
- [x] Step 2 Surefire authority GREEN。

## Verification

现有 `test3` 保持原 discovery node，改用显式 total 与受控 start gate 后，修复前稳定为：

```text
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
expected: <10000> but was: <0>
```

生产修复将 `shutdown=true` 路径改为 graceful `shutdown + awaitTermination`，仅在中断或
要求传播任务错误时使用 `shutdownNow`；`shutdown=false` 使用调用时任务快照/显式 total
与 completed count 比较，同时恢复中断标记并使任务错误跨线程可见。

修复后 focused method 为 `1/0/0/0`，完整
`FsscriptClientProxyTest` 为 `10/0/0/0`。完整类报告 SHA-256 为
`10836166550a35146ad071898623245390b2bead1c72a1929b9f336ba74810bf`。

## References

- `addons/foggy-fsscript-client/target/surefire-reports/TEST-com.foggyframework.fsscript.client.test.support.FsscriptClientProxyTest.xml`
- `target/v934-step2-unit/runs/step2-unit-r3-20260714/run.marker`
- `docs/v3.0/workitems/BUG-mvn-test-multidb-fixture-stability.md`
- `docs/9.3.4/evidence/step-2/step2-successor-r8e-independent-review-20260715.md`
- `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`
