---
type: bug
bug_source: quality-review
version: 9.3.4
ticket: BUG-934-MAVEN-ZERO-TEST-DEFAULT
severity: major
status: closed
reproduction_status: confirmed-by-plugin-contract
test_strategy: pom-contract-and-integration-test
automation_decision: required
owner: root-build
---

# 普通 Maven owning lane 的 failIfNoTests 默认仍为 false

## Problem

Surefire/Failsafe 3.5.3 的 `failIfNoTests` 默认值为 `false`。r7 只固定了
`failIfNoSpecifiedTests`，authority wrapper 虽可由 exact report verifier 兜底，脱离
wrapper 的普通 owning Maven lane 仍可能在零测试时绿色退出。

## Expected

- 根 POM 对 Surefire/Failsafe 都显式 default `failIfNoTests=true`。
- Integration 的 `-pl ... -am` 仅对选定 reactor 显式覆盖 Failsafe 的
  `failIfNoTests/NoSpecifiedTests=false`，owner exact-set 继续由 verifier fail-closed。
- 默认值、property expression 与受控 override scope 进入 runner contract 和负向探针。

## Fix Checklist

- [x] 根 POM 两个 runner 默认 zero-test fail-closed。
- [x] selected-reactor helper override 受控且成对出现。
- [x] POM/runner contract 负向探针通过。
- [x] r8e Unit/Integration authority 通过。

## Evidence

- `docs/9.3.4/evidence/step-2/step2-successor-r8e-independent-review-20260715.md`
- `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`
