---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-STEP4-UNIT-LIFECYCLE-STATIC-CONTRACT-DRIFT
severity: blocker
status: closed
closed_at: 2026-07-18
closure_evidence: docs/9.3.4/acceptance/step4-coverage-gate-acceptance.md
reproduction_status: confirmed
product_regression: false
test_strategy: unit-integration-lifecycle-shape-and-negative-regression
automation_decision: required
owner: step4-coverage-tooling
---

# Step 4 Unit lifecycle 静态契约未识别 fixture-aware EXIT wrapper

## Background

fresh all-lane run `step4-coverage-20260716-diagnostic-r8` 从调用方确认的 clean/pushed
`HEAD == origin/main`=
`3a3dd21a9aa956f0bedfffcf648ebb1cac0b756a` 启动。四个 repo demo DB exact container
在 evidence window 外停止，`13306/13308/15432/11433` 均 free。

full contract、successor overlay、authority 与 coverage contract negatives=`20/20` 先通过；
run-log lifecycle 动态 cases 跑完后，静态 shape validator 因 Unit runner 缺少旧 direct
token 而失败：

```text
scripts/verify-v934-unit.sh: missing lifecycle token 'v934_run_log_exit_trap "$?" v934_record_run_status'
```

outer 在 `bootstrap-negative` 以 exit code `1` fail closed。run-owned Git/source seal、所有
lane、aggregate、threshold 与 summary 均 absent；r8=`excluded/non-reusable`。

## Expected vs Actual

- Expected：Integration runner 可以继续使用 shared run-log direct EXIT trap；Unit runner
  因额外拥有 MySQL fixture lifecycle，必须使用 `v934_unit_exit_trap` wrapper。
- Expected：Unit wrapper 保存原退出码，先完成 lifecycle/main fixture fallback cleanup，再
  精确一次委托
  `v934_run_log_exit_trap "$exit_code" v934_record_run_status`；任何 direct trap 绕过 cleanup
  都必须 fail closed。
- Expected：静态 validator 分别验证 Unit 与 Integration 的合法 shape、顺序与唯一性，而
  不是要求两者出现同一个文本 token。
- Actual：validator 对 Unit/Integration 使用同一 token list，要求 Unit 直接包含
  `v934_run_log_exit_trap "$?" v934_record_run_status`；fixture-aware wrapper 使用
  `"$exit_code"` 委托，被误判为缺失 lifecycle。

## Root Cause

Unit MySQL 5.7 remediation 新增了两层 run-owned fixture cleanup。为保证普通失败、
INT/TERM/HUP、callback failure 与 leader-kill fallback 都先清理资源再发布最终 status，
Unit 的 EXIT trap 从 shared direct trap 演进为：

```bash
trap 'v934_unit_exit_trap "$?"' EXIT
```

wrapper 内完成 cleanup 后再调用 shared finalizer。`run_log_lifecycle_negative_test.sh` 的
静态检查仍把 Unit 和 Integration 一起循环校验旧 direct token，没有表达“不同入口、同一
shared finalizer、Unit cleanup 必须在委托之前”的新契约。这是测试契约漂移，不是产品代码
回归，也不能通过删除 Unit wrapper、跳过 bootstrap negative 或放宽 fail-closed 来修复。

## Impact and evidence boundary

- r8 在正确的 fail-closed 边界停止，没有运行任何 Maven/test/database/external lane；
- Git/source seal、toolchain receipt、JaCoCo exec、aggregate、critical counter、threshold
  observation、candidate 与 summary 全 absent；
- r8 前置 contract/overlay/authority/20 negatives 只属于该 failed run，不能拼入 successor；
- outer cleanup container/volume/network=`0/0/0`；
- 四个 demo exact container 已在 evidence window 外按相同 ID 恢复为
  `running/healthy`；
- 该 r8 时点 Step 4 仍为 `in-progress`，`can_enter_coverage_audit=no`，Step 5 保持关闭。

Immutable evidence：

- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r8-bootstrap-negative-contract-drift-fail-closed-20260717.md`。

## Fix Strategy

1. 将静态 shape validation 拆为 Unit 与 Integration 两个显式 contract，不再共享过期
   direct-trap token list。
2. Unit contract 必须按顺序验证：load shared library → 定义 Unit wrapper → lifecycle
   cleanup → main fixture cleanup → shared finalizer delegation → install traps → wrapper
   EXIT trap → open logger → close logger → completed phase → durable green status。
3. Unit wrapper 对 shared finalizer 的 delegation 必须精确一次；缺失或重复均 fail closed。
4. Unit 文件若重新出现 direct
   `trap 'v934_run_log_exit_trap "$?" v934_record_run_status' EXIT`，必须作为绕过 fixture
   cleanup 拒绝。
5. Integration contract 保持 direct shared EXIT trap，并继续验证 logger close 发生在绿色
   status 之前。
6. 保留现有 slow/nonzero/timeout/PID reuse/signal/capture/exit/group/residue 动态 cases；
   static shape 全部通过后才允许发布完整 lifecycle suite PASS。

## Implementation Closure

- Unit 与 Integration 使用两个显式 validator；共同保留 unowned process-substitution
  logger rejection；可执行 physical/logical stream 排除注释、heredoc body 与跨行 quoted
  decoy，退出 trap、cleanup/green 顺序与关键函数引用分别验证；
- Unit contract 冻结 fixture-aware wrapper executable slice，精确要求 lifecycle cleanup →
  main fixture cleanup → shared finalizer；Integration 与 Unit 都冻结 logger flush → green
  slice 及 disarm → PASS suffix；
- `trap` allowlist 同时识别 `EXIT`/numeric `0`、链式/转义/间接引用；关键 lifecycle 函数只
  允许 canonical command，拒绝尾分号重复 install/disarm、early return、function shadow 与
  `true || close`；
- Unit/Integration runner 另由 raw-byte SHA-256 seal 绑定；先 `read_bytes()` 验 exact bytes，
  再 strict UTF-8 decode。false/subshell outer context、heredoc source、eval shadow 与 CRLF
  normalization drift 均在 bootstrap fail closed；
- mutation builder 使用 `replace_exact`，每个 anchor 必须精确命中一次且 mutation 必须改变
  bytes，避免 anchor 漂移时把一个 case 误计为另一个 fail-closed 原因；
- Unit shape negatives=`13/13`、source-seal negatives=`3/3`；Integration shape negatives=
  `11/11`、source-seal negatives=`5/5`；覆盖 comment/quoted heredoc decoy、EXIT/0、重复
  install/disarm、early return、shadow、false/subshell context、heredoc source/eval 与 CRLF；
- 完整 lifecycle regression 发布唯一 PASS：原动态 `9 类 / 14 case` 全部保留，另有
  Unit/Integration shape negatives=`13+11`、source-seal negatives=`3+5`；
- modified tool SHA-256=
  `8dcc679c2762ff8908b3bc26e8dfb0553a083eb75003dd80366fd82e78d8ed9b`；top
  manifest=`60/60` / SHA-256=
  `0c4e6c18f4af0a2c35418604ce80d20828ceb4c275375b7d12461b4683553a81`；successor
  manifest=`14/14` / SHA-256=
  `acb580e92a72eb407f31f5d6f9a8139a3509f3a0bfbf58537922465f4086a112`。

## Regression Test Decision

`automation_decision=required`。该缺陷直接阻断 mandatory all-lane authority，且文本契约若
再次漂移会在 bootstrap 阶段制造伪失败或诱导删除必要 cleanup，因此必须先补自动化回归再
重跑：

1. positive：当前 fixture-aware Unit wrapper 与当前 Integration direct trap 均通过；
2. negative：删除 Unit shared-finalizer delegation；
3. negative：用 direct shared trap 替换 Unit wrapper trap，证明 cleanup bypass 被拒绝；
4. negative：把 delegation 移到 fixture cleanup 之前；
5. negative：Unit delegation 重复；
6. negative：Integration 缺失 direct shared trap 或 logger-close-before-green 顺序漂移；
7. 完整 dynamic + static lifecycle suite 必须发布唯一 PASS；
8. Step 4 bootstrap negatives 完整通过后，才允许从新的 clean/pushed HEAD 使用新 run ID
   重跑 all-lane diagnostic。

不得复用 r8 的 contract/negative 输出，不得把 focused regression 当作 Step 4 exit
evidence。

## Reproduction Evidence

- run=`step4-coverage-20260716-diagnostic-r8`；
- reported launch HEAD=`3a3dd21a9aa956f0bedfffcf648ebb1cac0b756a`；
- result=`failed / bootstrap-negative / exit 1`；
- full contract、overlay、authority 与 `20/20` contract negatives 先通过；
- lifecycle 动态 cases 到达 static shape validation，但 suite final PASS absent；
- static error 精确为 missing old Unit lifecycle token；
- Git/source seal、lane、aggregate、threshold、summary absent；
- cleanup=`0/0/0`；
- decision=`excluded-from-step4-exit / non-reusable`。

## Fix Checklist

- [x] r8 immutable failure、absence boundary、cleanup 与 demo restoration 已封存。
- [x] 建立本 BUG 并判定 `automation_decision=required`。
- [x] 实现 Unit/Integration 分类型 static shape validator。
- [x] 补 Unit shape/source-seal `13/13 + 3/3` 与 Integration `11/11 + 5/5`
      lifecycle expected-negative。
- [x] 运行完整 lifecycle dynamic/static regression 并取得唯一 PASS。
- [x] 完成两路独立实现质量复核，最终 B/H/M/L=`0/0/0/0`。
- [x] remediation 已随 commit `a0466ec04c51c436413e85836a7dee6153e18010`
      push，且 r9 启动时证明 clean `HEAD == origin/main`。
- [x] fresh r9 已通过本 BUG 对应的 bootstrap lifecycle contract 并完成全部 required
      lanes；其后在无关的 JaCoCo exec class identity scope 上 fail closed。
- [x] Cdiag `316a71f753827f8f34063b0eb0669271f696c5ee` 的 fresh r17 再次通过 lifecycle
      static/bootstrap 边界，随后在第三个真实 MySQL57 probe 的 callback ready 前因独立
      final-mysqld handoff race fail closed；r17 cleanup JSON 只证明 EXIT trap 清理，不是
      canonical lifecycle `5/5` PASS。
- [x] r17 handoff recovery pre-Cdiag formal quality=`PASS / 0/0/0/0`；只授权 replacement
      Cdiag/fresh diagnostic，BUG 仍不关闭。
- [x] fresh formal、最终质量与 coverage evidence audit 通过后关闭本 BUG。

本 BUG 的原始 stale-token drift 已被 r9 动态证明关闭；随后发现的更深层 semantic bypass
由 `BUG-934-STEP4-LIFECYCLE-SEMANTIC-VALIDATOR-BYPASS` 单独治理并关闭。后续 replacement、
formal-r4、coverage audit 与 feature acceptance 在历史 checkpoint 已通过；Step 5 曾为
`ready / not-started`，之后被 replacement chain 重新关闭。

## References

- `scripts/v934/step4/run_log_lifecycle_negative_test.sh`
- `scripts/v934/step4/run_log_lifecycle_lib.sh`
- `scripts/verify-v934-unit.sh`
- `scripts/verify-v934-integration.sh`
- `scripts/verify-v934-step4-coverage.sh`
- `target/v934-step4-coverage/runs/step4-coverage-20260716-diagnostic-r8/`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r17-unit-mysql57-final-mysqld-handoff-fail-closed-20260717.md`
- `docs/9.3.4/workitems/BUG-step4-unit-mysql57-final-mysqld-handoff-readiness-race.md`
- `docs/9.3.4/quality/step4-diagnostic-r17-recovery-implementation-quality.md`
