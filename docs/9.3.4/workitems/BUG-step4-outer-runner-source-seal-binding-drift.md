---
type: bug
bug_source: diagnostic-found
version: 9.3.4
ticket: BUG-934-STEP4-OUTER-RUNNER-SOURCE-SEAL-BINDING-DRIFT
severity: blocker
status: closed
closed_at: 2026-07-18
closure_evidence: docs/9.3.4/acceptance/step4-coverage-gate-acceptance.md
reproduction_status: confirmed
product_regression: false
test_strategy: outer-runner-source-seal-exact-binding-regression
automation_decision: required
owner: step4-coverage-tooling
---

# Step 4 outer runner 已更新但 lifecycle 内嵌 source seal 未同步

## Background

fresh diagnostic `step4-coverage-20260717-diagnostic-r11` 从已 push 且 clean 的
`HEAD == origin/main == 141592ca9f4219d87a018774ee607b09a8e5a8a1` 启动。full coverage
contract、successor overlay、sensitive-pattern bootstrap probe、authority 与 coverage contract
negative 均已通过；`run_log_lifecycle_negative_test.sh` 随后在校验 outer runner raw-byte
identity 时以 `E_SOURCE_SEAL` fail closed。

Immutable evidence：

- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r11-outer-source-seal-fail-closed-20260717.md`。

r11 在创建 run-owned Git/source seal 前终止，没有启动 Unit、Integration、database、external、
Addon 或任何其他测试 lane。该 run 为 `failed / excluded-from-step4-exit / non-reusable`，不能
把其 bootstrap 前置绿色与后续 fresh run 拼接。

## Expected vs Actual

- Expected：Unit runner、Integration runner、outer runner、lifecycle library 的四个独立 frozen
  constants，必须各自与 top `SHA256SUMS` exact row、canonical raw bytes 形成 early exact
  binding；任一处变化都应在 full outer 分配 run root 或启动 lane 前被发现。
- Expected：修改 `scripts/verify-v934-step4-coverage.sh` 时，声明式 runtime binding 或更新
  checklist 必须覆盖所有直接和内嵌 consumer，不能只刷新 top manifest。
- Expected：binding mutation 必须稳定返回 `E_SOURCE_SEAL_BINDING`，canonical positive 必须
  证明四个 nested seals 绑定当前 canonical bytes；raw CRLF 与 executable no-op mutation 则
  分别触达 raw `E_SOURCE_SEAL` 与 executable-stream seal。
- Actual：top manifest 已刷新为当前 outer runner SHA-256
  `57f5da9a23c4973beef54a6bfd303c3dfd38fccb03a7bc2cadadbfaa3206f649`，但
  `run_log_lifecycle_negative_test.sh` 的 `OUTER_RUNNER_SHA256` 仍冻结旧值
  `02a920d91d1b8792cad47d65ce860352a8e9ecf39106f4489a714df01888dbaa`。
- Actual：现有 top manifest/validator 没有把该 nested constant 纳入 declared runtime binding，
  因而前置 contract 可以通过，直到 full outer invocation 进入 lifecycle test 才发现漂移。

## Sensitive-safe Reproduction

无需记录任何业务日志或敏感原文即可复现：

1. 对 canonical `scripts/verify-v934-step4-coverage.sh` 计算 raw SHA-256，得到
   `57f5da9a23c4973beef54a6bfd303c3dfd38fccb03a7bc2cadadbfaa3206f649`。
2. 读取 lifecycle validator 的 `OUTER_RUNNER_SHA256`，得到
   `02a920d91d1b8792cad47d65ce860352a8e9ecf39106f4489a714df01888dbaa`。
3. 执行 canonical lifecycle negative suite；在任何测试 lane 启动前观察到 stable
   `E_SOURCE_SEAL`，expected/observed hash 不相等。

r11 的 `run-status.env` 记录 `last_phase=bootstrap-negative`、`exit_code=1`；`git_head`、
`started_at`、`source_before_sha256`、`source_after_sha256` 与 `outer_marker_sha256` 均为空。

## Root Cause

r10 的 sensitive-scan 修复修改了 outer runner，并正确刷新 top `SHA256SUMS`；但 outer runner
同时被 lifecycle validator 的 raw source-seal constant 二次绑定。该 nested binding 未进入
声明式 runtime-binding inventory，也未列入 outer runner 变更 checklist，因此维护动作只更新
了顶层 manifest，没有同步 consumer 内的 frozen constant。

`E_SOURCE_SEAL` 本身工作正确：它拒绝以旧身份验证新 runner。缺陷是 binding graph 不完整，
导致本可在 focused validation 阶段发现的确定性配置漂移进入 fresh diagnostic。

## Fix Strategy

1. 更新 `OUTER_RUNNER_SHA256` 与 outer executable-stream seal；禁止关闭或绕过 raw/semantic
   任一 source seal。
2. 增加 special preflight：精确提取 Unit、Integration、outer、library 四个 nested constants，
   各自校验 singleton/64-lower-hex、top manifest canonical exact row 与实际 raw bytes。
3. outer 在创建 run root、建立 Git/source inventory 或启动 test lane 前调用该 preflight；所有
   binding drift 统一以 stable `E_SOURCE_SEAL_BINDING` fail closed。
4. 增加 canonical positive=`1` 与 negatives=`6`：outer+manifest refresh/nested stale、
   outer-runner-only drift、valid-64 nested-only wrong、missing、duplicate 与 invalid-format
   constant；避免只验证 happy path 或只验证 schema。
5. 增加 outer raw CRLF 与 executable no-op semantic negatives，分别证明 raw-byte seal 与 coded
   executable-stream seal 均真实生效。
6. 把该 binding 加入 outer runner 变更 checklist，并确保 future outer edit 必须同步 consumer
   或由自动化更新/拒绝。
7. 运行完整 lifecycle dynamic/static/source-seal suite、contract/overlay 回归与正式实现质量
   检查；B/H/M/L 清零后方可提交。
8. commit/push，证明 clean `HEAD == origin/main`，再使用全新 run ID 执行 fresh r12。

## Regression Test Decision

`automation_decision=required`。该漂移位于 Step 4 mandatory bootstrap-negative gate；只更新
常量会让下一次 outer edit 再次依赖人工记忆。自动化至少覆盖：

- Unit/Integration/outer/library 四个 canonical raw SHA 与 top manifest entries 完全一致；
- 四个 lifecycle nested seals 与 canonical raw bytes、top manifest entries 完全一致；
- canonical positive=`1` 发布 exact `count=4`；
- outer+manifest refresh/nested stale、outer-only drift、valid-64 nested-only wrong、missing、
  duplicate、invalid-format 六类 mutation 均稳定返回 `E_SOURCE_SEAL_BINDING`；
- outer raw CRLF 与 executable no-op 分别触达 raw 与 semantic source seal；
- full lifecycle、contract、successor overlay 与 authority negatives 保持通过。

## Remediation Result

当前 coded remediation identity：

- outer raw SHA-256=
  `90b4b979e55c17243644cce186767a4647ce79c85b431adcb415bddd18cc1cec`；
- outer executable-stream SHA-256=
  `065211912aab5227125ef02f40e2965fce7ff5060df5c7b91a902c4ad4f34cae`；
- lifecycle regression tool SHA-256=
  `61bf7b990bdef6e0d75c53010644bcc6d1525a67119cd36c5f82eeb911e005fc`；
- top manifest SHA-256=
  `a9b105ce2f8f640dfa09863e797697bcf9892a7b0fa68b38f83b5bbd7435afb4`。

安全复核曾发现 preflight 的 path-check/read 存在 TOCTOU Medium；当前全部输入已改为
descriptor-bound strict read：`O_NOFOLLOW` open、`fstat`、fd read，并以 post-`lstat`
复验 stable identity。该 Medium 已修，错误仍为 `E_SOURCE_SEAL_BINDING`；两路 post-fix
implementation review 与独立 docs/status review 最终 B/H/M/L=`0/0/0/0`。

Focused result：early binding=`1 positive + 6 negative`；full lifecycle suite=`PASS`；top
manifest=`60/60`、successor manifest=`14/14`、coverage contract mutations=`21/21`、overlay
negatives=`12/12`。该 remediation 时点这些结果只关闭 coded/focused gap，不是 formal exit；
后续 commit/push、fresh replacement、formal-r4 与 feature acceptance 已完成。

## Fix Checklist

- [x] 封存 r11 bootstrap-negative failure、exact hash mismatch、absence boundary 与 cleanup。
- [x] 登记本 test-governance blocker，确认不是产品 runtime regression。
- [x] 更新 raw/executable-stream seals，保持双层 source-seal fail-closed 语义不变。
- [x] 增加 canonical positive=`1` 与 exact-binding negatives=`6`。
- [x] 将 Unit/Integration/outer/library nested constants 纳入 early manifest/raw binding。
- [x] 完成完整 lifecycle suite、manifest、successor、contract/overlay focused 回归。
- [x] 完成正式实现质量复核并清零 B/H/M；两路 post-fix 与 docs/status review=`0/0/0/0`。
- [x] commit/push 并证明 clean `HEAD == origin/main`。
- [x] 使用全新 run ID 完成 fresh r12 all-lane diagnostic。
- [x] 仅在 fresh diagnostic 成功后冻结 exact-observed threshold，再执行 fresh formal、最终质量、
      coverage evidence audit 与 acceptance。

该 r11 时点 `status=in-progress`，且 r11 不可复用；后续 formal-r4、final quality、coverage
audit 与 feature acceptance 已按序通过，本 workitem 现由正式 acceptance 关闭。

## References

- `scripts/v934/step4/run_log_lifecycle_negative_test.sh`
- `scripts/verify-v934-step4-coverage.sh`
- `scripts/v934/step4/SHA256SUMS`
- `scripts/v934/step4/coverage-contract.json`
- `scripts/v934/step4/successor/overlay-contract.json`
