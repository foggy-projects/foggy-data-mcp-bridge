---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-STEP2-V933-REAL-QUERY-RUNNER-COMPAT
severity: critical
status: closed
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: scripts/v934/step3/verify-v933-real-query-compat.sh
---

# Step 2 fail-closed 默认值破坏 v9.3.3 real-query runner

## Background

9.3.4 Step 2 把根 POM 的 Surefire/Failsafe `failIfNoTests` 与
`failIfNoSpecifiedTests` 默认值改为 fail closed。Step 3 回归复核
`verify-v933-batch6-real-query.sh` 时确认：该历史 runner 使用 `-pl ... -am` 定向执行
单一 owning class，但没有显式允许上游 reactor module 的 selector miss，导致 owning
module 尚未执行前就在根聚合模块失败。

## Reproduction

```bash
scripts/verify-v933-batch6-real-query.sh v934-step3-compat-20260715
```

稳定结果：第一个 `model-lifecycle-sqlite` lane 在根模块以
`No tests to run!` 非零退出。

仅补 `-Dfailsafe.failIfNoTests=false` 后再次执行，数据库 owning lanes 均通过，但
`cache-caffeine` 在根模块以
`No tests matching pattern QueryCacheLifecycleRealQueryIT were executed!` 非零退出，确认
还缺 `-Dfailsafe.failIfNoSpecifiedTests=false`。

## Expected vs Actual

- expected：上游 reactor module 没有目标 class 时可继续；owning module 必须运行 exact
  class/testcase，且由 raw Failsafe report、freshness、BUILD SUCCESS 与 probe 共同验证。
- actual：根/上游 module 的 selector miss 提前终止 reactor，旧 runner 无法到达 owning
  module；这不是测试通过，runner 正确返回了非零。

## Root Cause

Step 2 的全局默认值本身正确，但定向 `-pl ... -am` wrapper 没有像 9.3.4 authority
runner 一样显式覆盖上游 module selector 语义。全局门与局部 wrapper 的责任边界未闭合。

## Fix

新增 v934-owned compatibility wrapper，保持 historical runner 字节不变。wrapper 通过
导出的 Maven bridge function 为旧 runner 的每次 reactor 调用统一传入：

- `-Dfailsafe.failIfNoTests=false`
- `-Dfailsafe.failIfNoSpecifiedTests=false`

这两个覆盖只允许 reactor 上游 selector miss。historical runner SHA-256 在运行前被记录，
目标 owning module 仍由原 runner 强制：

- exact FQCN 与 exact testcase count；
- fresh `failsafe-summary.xml` 和 raw XML；
- `Failures=0, Errors=0, Skipped=0`；
- exactly one `Running <FQCN>` 与 exactly one `BUILD SUCCESS`；
- v9.3.3 数据库/缓存 machine probe。

## Fix Checklist

- [x] reproduce root-module `failIfNoTests` failure
- [x] reproduce cache-lane `failIfNoSpecifiedTests` failure
- [x] keep the historical runner byte-identical and scope both relaxations to a v934 wrapper
- [x] complete the full six-lane real-query runner with S0
- [x] record final run root/hash in Step 3 evidence

Verification run：`v934-step3-compat-r5-20260715`，`11 tests / 6 reports / F0/E0/S0`；
summary SHA-256=`09000e0c35f7a3b0033d52970c52bbbdbc1dce477bb33e35dababb42a2d32883`；
historical runner SHA-256 保持
`0f560482112cd1241c54b279198d38130c59f76e100fa32fecfeb61be50ae403`。

## References

- `pom.xml`, Step 2 default Failsafe contract
- `scripts/verify-v933-batch6-real-query.sh`
- `scripts/v934/step3/verify-v933-real-query-compat.sh`
- `docs/9.3.4/workitems/BUG-step3-database-contract-gaps.md`
