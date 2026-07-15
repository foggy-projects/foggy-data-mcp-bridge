---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-SUREFIRE-NESTED-IT-LEAKAGE
severity: major
status: closed
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: root-build
---

# Surefire 通过 Nested Test 名称误执行 Failsafe 所属 IT

## Background

9.3.4 Step 2 已将外部数据库和 broad integration 源统一改名为 `*IT` 并交给
Failsafe。完整 Surefire authority 的粗报告集合却出现 27 份不属于 unit ownership 的
fresh XML；它们全部来自当前 `*IT` 外层类中的 `@Nested` 类，nested 名称以
`Test` 或 `Tests` 结尾。

失败运行：

```bash
scripts/verify-v934-unit.sh step2-unit-r3-20260714
```

在 Maven 因另一个并发 BUG 停止前，已生成 `754` 份 fresh Surefire XML。与 successor
逐项比较为：expected `732`、actual `754`、missing `5`、extra `27`。5 个 missing
均属于尚未执行到的后续模块；27 个 extra 均为 lane leakage，不是改名前 stale class。

## Root Cause

Surefire 当前 include 为 `**/*Test.java`、`**/*Tests.java`、`**/*TestCase.java`，
exclude 只覆盖外层 `**/*IT.java` 等名称。编译后的 nested class 例如
`McpToolsIT$MetadataToolIntegrationTest` 会命中 include，却不命中外层 exclude；
JUnit 随后连同其 `*IT` 容器一起在 unit lane 执行。

确认泄漏的源包括：

- `AiToolsIT`：3 reports；
- `McpToolsIT`：5 reports；
- `FieldAccessPermissionIT`：7 reports；
- `PhysicalColumnPermissionIT`：7 reports；
- `SyntheticMemberPermissionIT`：5 reports。

合计 `27`。这些报告在 successor 中全部属于 Failsafe；其中 MCP 外部 MySQL 项属于
Step 3 deferred，其余属于 Step 2 integration，均不得出现在 Surefire。

## Expected vs Actual

- 期望：Surefire 只执行 successor 中的 `677` 个 unit execution owners，连同 `55`
  个 structural reports 形成严格 `732` raw reports。
- 实际：nested class 名称绕过外层 IT exclude，造成跨 runner 双执行和外部数据库用例误入
  hermetic unit lane。

## Test Strategy

1. 将本次 `27` extra 作为确定性 RED 证据。
2. Surefire 为所有 IT/E2E 外层命名补齐 `$*` nested exclude；Failsafe 仍从外层 owner
   执行并产生 nested reports。
3. successor runner-contract 校验固定 nested exclusion，负向探针必须拒绝删除或漂移。
4. 重跑完整 Surefire authority，要求 exact `677 + 55 = 732`，且 Failsafe FQCN 在
   unit report set 中为零。

## Code Inventory

- `pom.xml`
  - 为 `IT*`、`*IT`、`*ITCase`、`*E2E`、`*E2ETest` 与 legacy matrix 外层类补齐
    nested class exclusions。
- `scripts/v934/step2_successor_tool.py`
  - 将 nested exclusion 纳入 effective POM/runner contract fail-closed 校验。
- `scripts/v934/successor/step2/runner-contract.json`
  - 由 r5 successor 重生成并 hash seal。

## Fix Checklist

- [x] 完整 Surefire 运行捕获 27 个 extra reports。
- [x] 与 successor ownership 逐项比较并排除旧 class 残留根因。
- [x] 定位 nested class include/exclude 不对称。
- [x] POM 补齐 nested exclusions。
- [x] runner contract/negative probe 固定该边界。
- [x] r8e successor 复核确认。
- [x] Step 2 Surefire authority exact-set GREEN。

## References

- `pom.xml`
- `scripts/v934/successor/step2/step2-required-execution.tsv`
- `scripts/v934/successor/step2/deferred-step3.tsv`
- `target/v934-step2-unit/runs/step2-unit-r3-20260714/run.marker`
- `docs/9.3.4/evidence/step-2/step2-successor-r8e-independent-review-20260715.md`
- `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`
