---
type: bug
bug_source: diagnostic-governance-found
version: 9.3.4
ticket: BUG-934-STEP4-MAP-BEAN-INFO-DOUBLE-CHECK-COVERAGE-RACE
severity: blocker
status: in-progress
reproduction_status: confirmed
product_regression: false
test_strategy: controlled-monitor-interleaving-existing-test-node
automation_decision: required
owner: step4-coverage
---

# Step 4 MapBeanInfo double-check 覆盖并发竞态

## Problem

fresh diagnostic-r23 在 clean/pushed Cdiag `9b0bd281…` 上完整 PASS，但 aggregate branch 比
r20/r21/r22 的稳定值多一个 outcome。唯一变化位于
`BeanInfoHelper.MapBeanInfoHelper#getBeanProperty` 的 inner double-check。历史 raw probe 证明该
outcome 只在少数调度中偶发命中；由于 freeze 工具把 observed branch exact 固化为 formal minimum，
r23 不可作为可复现 threshold authority。

这不是 production regression，也不是 r23 evidence 污染。缺陷是覆盖 oracle 把 formal 成败交给了
线程调度。

## Fix contract

1. 不修改 production double-check、coverage floor、critical set、denominator 或 exclusion。
2. 只扩展既有 `BeanInfoHelperTest#getClassHelper`，不得新增/改名 testcase。
3. main thread 必须先持有 helper monitor；lookup thread 只有完成 outer miss 后才会在唯一
   monitor-enter 处变成 `BLOCKED`。
4. main thread 在 monitor 内安装 property；释放后 lookup 的 inner read 必须观察到 non-null，返回
   exact installed identity。
5. 同时显式覆盖创建与 cache fast path；等待和 join 必须有界，线程必须终止。
6. focused JaCoCo 必须稳定显示方法 branch=`4/4`、complexity=`3/3`，随后完整重走
   Cdiag→diagnostic→candidate/capsule/双审→Cfreeze→formal。

## Verification and checklist

- [x] r23 public-valid、required=`773+59/5707/F0E0S0`、source seal、cleanup、sensitive 与 DB restore；
- [x] r23 candidate absent，未手工降低或绕过 threshold；
- [x] 唯一 delta 定位为 source line 245 inner false outcome；
- [x] raw exec 定位为 Unit probe index 4，其他 22 exec 对目标类不变；
- [x] existing-node regression 执行 100 次 controlled monitor interleaving；
- [x] five fresh JVM targeted=`5 x 1/F0E0S0`，class id/probe/bitmap=
  `a6629aa379049ec7 / 10/11 / _wU` exact identical，method branch=`4/4`、complexity=`3/3`；
- [x] owning module=`97/F0E0S0`；test cardinality 不变；production/POM/runner/threshold diff=`0`；
- [x] independent code review=`APPROVE / B0 H0 M0 L0`；
- [x] replacement Cdiag commit/push/clean=`414c8b12…`；
- [x] fresh diagnostic-r24 and exact counter/probe confirmation；
- [x] new candidate/capsule、dual threshold review 与 canonical machine formalization；
- [ ] direct-child Cfreeze commit/push/clean 与 fresh formal-r7；
- [ ] replacement quality/coverage/acceptance 后关闭本 BUG。

## References

- `foggy-core/src/main/java/com/foggyframework/core/utils/beanhelper/BeanInfoHelper.java`
- `foggy-core/src/test/java/com/foggyframework/core/utils/beanhelper/BeanInfoHelperTest.java`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r23-incidental-map-double-check-high-water-20260719.md`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r24-pass-20260719.md`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r24-threshold-review-20260719.md`
- `scripts/v934/step4/coverage_xml_tool.py`
