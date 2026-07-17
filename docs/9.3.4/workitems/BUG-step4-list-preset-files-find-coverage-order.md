---
type: bug
bug_source: formal-gate-found
version: 9.3.4
ticket: BUG-934-STEP4-LIST-PRESET-FILES-FIND-COVERAGE-ORDER
severity: blocker
status: in-progress
reproduction_status: confirmed
product_regression: false
test_strategy: deterministic-existing-test-regression
automation_decision: required
owner: step4-coverage
---

# Step 4 ListPreset `Files.find` 覆盖顺序波动

## Background

fresh formal `step4-coverage-20260717-formal-r2` 在 clean/pushed Cfreeze commit
`1901a10138bac06a09b875c907b7aea6e2789b04` 上完成全部测试与证据 lane，随后在
`formal-coverage-gate` 以 `E_FORMAL_LOW` fail closed。该 run 的 `summary.env` 与
`coverage-gate.json` 未发布，run-status 为 `failed / exit 1`，cleanup=`0/0/0`、
sensitive scan=`passed`。

immutable failure evidence：

- `docs/9.3.4/evidence/step-4/step4-coverage-formal-r2-list-preset-branch-order-fail-closed-20260717.md`。

## Expected vs Actual

- Expected：formal 必须稳定达到 r14 reviewed exact threshold：aggregate=
  `54622/76830 line, 26106/44870 branch`。
- Actual：formal-r2 line exact 命中，branch=`26105/44870`，只少一个 branch outcome；12 个
  critical class 全部 exact 命中，below-floor=`0`，唯一 N/A 仍为
  `NamespaceScope.branch`。
- Actual：2066 个 reportable class 中只有
  `FileSystemListPresetStore` 变化：line 保持 `69/88`，branch 从 `18/26` 变为 `17/26`；
  唯一 source delta 位于 `lambda$findById$2` / line 74，从 `4/4` 变为 `3/4`。

## Root Cause

`FileSystemListPresetStore#findById` 使用：

```java
Files.find(userDir, 6, (path, attrs) ->
        attrs.isRegularFile() && path.getFileName().toString().equals(fileName))
    .findFirst();
```

目录中的 preset 文件使用随机 UUID 命名，`Files.find` 的遍历顺序未定义，随后
`findFirst()` 会短路。目标 regular file 先出现时，不会执行另一个 regular file 的
`equals(fileName)=false` outcome；非目标文件先出现时则会覆盖该 outcome。因此相同测试节点、
相同 source/class tree 和完整 exec/session 结构仍可能产生一个 branch 的差异。

逐 exec probe 对比将差异定位到 Unit/Surefire `jacoco-ut.exec`：class ID=
`d1bd017e92baa090`、probe count=`113`，r14=`74/113`，formal-r2=`73/113`，唯一缺失
probe=`106`；其余 22 个 exec 均不包含该 class。报告库存、834 份 authority XML、testcase
identity、23 exec / 48 sessions 均完整且语义一致，因而不是漏跑测试、exec 丢失、
WatchService 回归或 PostgreSQL Pivot 波动。

## Fix Strategy

1. 在既有 `ListPresetServiceTest.FileStoreTests#shouldIsolatePresetByUserAndBusinessKey`
   testcase 内，对已有多 regular-file 的用户目录执行不存在 ID 的查询。
2. 断言 `service.get("u1", "missing").isEmpty()`；不存在 ID 会强制穷尽目录并稳定执行
   filename-false outcome。
3. 不增加 `@Test`，不修改生产代码、coverage floor、critical set、exclusion 或报告结构。
4. 通过多个 fresh Maven/JVM/JaCoCo fork 验证 probe 106 每次命中且目标 bitmap 一致，再运行
   Data Viewer 全模块测试。
5. 测试源变化必须进入新的 Cdiag；machine state 恢复
   `diagnostic-ready/diagnostic-pending`，完整重走 diagnostic -> review -> Cfreeze -> formal。
6. formal-r2 保持 immutable failed evidence，不复用、不修补、不以下调 threshold 掩盖波动。

## Regression Test Decision

`automation_decision=required`：这是 exact aggregate threshold 下会造成伪随机 formal 结果的
测试证据缺陷。最小自动化回归放入既有 testcase，既保持 frozen testcase cardinality，也让
filename-false outcome 不再依赖文件系统遍历顺序。focused 绿色仅证明修复方向；最终仍由新的
all-lane diagnostic/formal authority 证明。

## Fix Checklist

- [x] formal-r2 failure、absence semantics、cleanup 与 sensitive scan 已确认。
- [x] aggregate、12 critical rows、2066 reportable class、source line 与 23 exec probe 已逐项对比。
- [x] 根因确认为 `Files.find(...).findFirst()` 与 UUID 文件顺序造成的 branch 覆盖波动。
- [x] 既有 testcase 增加不存在 ID 断言；无生产变更、无新 testcase。
- [x] focused 5/5 fresh forks 稳定命中 probe 106，目标 packed bitmap unique=`1`。
- [x] Data Viewer 模块=`104/F0E0S0`，目标类恢复 r14 exact `74/113` bitmap。
- [x] machine tuple 恢复 diagnostic state；manifest=`60/60`、contract/overlay validator PASS。
- [x] 正式 pre-Cdiag implementation quality PASS，B/H/M/L=`0/0/0/0`。
- [ ] 完成一次 Cdiag commit/push 与 clean identity。
- [ ] fresh diagnostic、candidate review、direct-child Cfreeze 与 fresh formal 全部通过。
- [ ] 最终 implementation quality、coverage audit 与 acceptance 通过。

## References

- `addons/foggy-data-viewer/src/main/java/com/foggyframework/dataviewer/service/listpreset/FileSystemListPresetStore.java`
- `addons/foggy-data-viewer/src/test/java/com/foggyframework/dataviewer/service/ListPresetServiceTest.java`
- `scripts/v934/step4/coverage-thresholds.json`
- `scripts/verify-v934-step4-coverage.sh`
- `docs/9.3.4/evidence/step-4/step4-coverage-formal-r2-list-preset-branch-order-fail-closed-20260717.md`
