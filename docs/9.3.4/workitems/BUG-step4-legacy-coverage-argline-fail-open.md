---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-STEP4-LEGACY-COVERAGE-ARGLINE-FAIL-OPEN
severity: major
status: closed
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: root-build
---

# Step 4 legacy coverage argLine 早解析导致缺数据伪绿

## Background

9.3.4 Step 4 在复核 legacy `foggy-dataset-model` coverage profile 时发现，根
Surefire/Failsafe 将公共 JVM 参数写为 `${argLine} @{jacoco.*.argLine}`。
`${argLine}` 在 JaCoCo `prepare-agent` 设置该 property 之前已被早期解析，使
legacy profile 的 agent 参数没有进入实际 test JVM。

该问题不改变测试本身的 pass/fail，但会让 coverage report/check 在没有
execution data 时被插件 skip，最终 Maven 仍返回 `BUILD SUCCESS`，属于覆盖率
evidence 链的 fail-open。

## Reproduction

在修复前，对 `foggy-dataset-model` 的 legacy `coverage` profile 运行 focused
test：

1. JaCoCo `prepare-agent` 日志声明已设置 `argLine`；
2. test 运行后未生成 `jacoco.exec`；
3. `report` 与 `check` 因 missing execution data 而 skip；
4. Maven 最终返回 `BUILD SUCCESS`。

该组合可稳定复现“日志看似已装 agent，实际无 exec，且 coverage gate 绿色”的
伪绿链路。

## Root Cause

Surefire/Failsafe 需要在 test JVM 启动前使用 late property evaluation 取得
JaCoCo `prepare-agent` 后写入的 `argLine`。根 POM 中 `${argLine}` 使用早期
interpolation；即使 `prepare-agent` 稍后正确更新 property，已解析的 runner
参数也不会自动带入 agent。

## Expected vs Actual

- Expected：开启 legacy coverage profile 时，`prepare-agent` 产生的 `argLine` 必须
  进入 test JVM，生成非空 exec；`report/check` 实际读取该 exec，低于既有
  门槛必须非零退出。
- Actual：`prepare-agent` 只有设置日志，test JVM 没有 agent，exec 缺失；
  report/check 跳过 missing data 后整体 `BUILD SUCCESS`。

## Impact Scope

- 影响根 POM 继承的 Surefire/Failsafe `argLine` 组装，直接暴露于
  `foggy-dataset-model` legacy coverage profile。
- 若不修复，可把“没有 coverage data”冒充为“coverage check 通过”，阻断
  Step 4 证据链的 fail-closed 要求。
- 修复仅改变 build/test JVM 参数的 late evaluation，不修改生产代码、覆盖率
  阈值或 exclusion。

## Test Strategy

`automation_decision=required`。该 BUG 可稳定复现、会造成绿色证据失真，且
修复点集中于 build contract。versioned `coverage_tool.py` 必须精确强制
`@{argLine} @{jacoco.*.argLine}`，并由 Step 4 manifest、`validate-contract` 与
baseline negatives 防止回退；下列三态 focused 动态证据证明该 canonical 形式
在实际 Maven JVM 中生效：

1. legacy `coverage` profile：focused test 必须生成 exec，report/check 必须
   实际读取；当 focused coverage 低于既有门槛时必须 `rc=1`；
2. 普通无 coverage profile：测试必须 `rc=0`，且不得意外生成 exec；
3. `v934-coverage` profile：测试必须 `rc=0`、生成非空 exec，且 session
   identity 必须与调用方指定值精确相等。

本 BUG 不以“某个 focused test 本身绿色”作为关闭条件；必须同时证明
exec 存在性、report/check 数据消费、低门失败和普通 profile 不泄漏。

## Code Inventory

- `pom.xml`：Surefire/Failsafe 共享 `argLine` 的 late evaluation 修复。
- `foggy-dataset-model/pom.xml`：legacy `coverage` 的 `prepare-agent`、report 与
  check 验证面；阈值不改。
- `scripts/v934/step4/`：Step 4 自动化回归与 exec/session provenance 验证面。

## Fix Checklist

- [x] 确认 legacy profile 的 `prepare-agent` 日志、exec 缺失、report/check skip
  与 `BUILD SUCCESS` 伪绿链路。
- [x] 根 Surefire/Failsafe 从 `${argLine} @{jacoco.*.argLine}` 改为
  `@{argLine} @{jacoco.*.argLine}`。
- [x] 保持生产代码、legacy coverage 门和 v934 threshold/exclusion 不变。
- [x] 完成 legacy、普通与 v934 三态 focused 验证。
- [x] `coverage_tool.py` 精确强制 canonical late-evaluation 形式，并由
  manifest + `validate-contract` + `8/8` baseline negatives 自动防回退。
- [ ] 在 fresh all-lane diagnostic 中复验实际 exec/session/report 链路。

## Verification

修复后 focused 结果：

- legacy `foggy-dataset-model` coverage + `PreAggregationEdgeCaseTest`：生成
  `jacoco.exec=336699 bytes`，report/check 实际读取；因 bundle LINE/BRANCH=
  `0.17/0.11`、关键类 LINE/BRANCH=`0.71/0.55` 低于既有门，正确
  `rc=1`；
- 普通 `MapBuilderTest`：`rc=0`，未生成 exec；
- 新 `v934-coverage` profile：`rc=0`，生成 `exec=30713 bytes`，session=
  `static-audit-foggy-core`。

上述结果证明 build 链已从“missing data 跳过且绿色”恢复为“有数据必读取、
低于门必失败”。canonical 形式已由 versioned contract guard 自动保护，
三态 focused 动态证据已完成，因此本 BUG 状态为 `closed`。fresh all-lane
diagnostic 仍是 Step 4 阶段待办，但不是重开本 BUG 的条件。

## References

- `docs/9.3.4/README.md`
- `docs/9.3.4/code-inventory.md`
- `docs/9.3.4/progress/test-ci-evidence-chain-progress.md`
- `docs/9.3.4/test/test-ci-evidence-chain-test-plan.md`
