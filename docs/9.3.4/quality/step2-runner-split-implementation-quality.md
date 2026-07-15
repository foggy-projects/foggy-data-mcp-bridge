---
quality_scope: feature
quality_mode: pre-coverage-audit
version: 9.3.4
target: step2-runner-split
status: reviewed
decision: ready-with-risks
reviewed_by: codex + v934_r6_identity_review + production_diff_quality + r8e_signal_identity
reviewed_at: 2026-07-15
follow_up_required: yes
---

# Implementation Quality Gate

## Background

本记录审查 9.3.4 Step 2 的 Surefire/Failsafe 分层、35 个受控 rename、successor/
report authority、三项最小生产修复及 15 个 regression/quality workitem。范围不包含
Step 3 五库/external、Step 4 coverage、Step 5–7 release/CI authority。

首轮 formal review 在 r8d 的 `PHASE=completed` 到 trap disarm 窗口发现 signal
fail-open：shell 可实际返回 143，却留下 durable `passed/0`。r8d successor 与其
Unit/Integration 因此作废；本终审只接受重新生成、双审并实际执行的 r8e generation。

## Check Basis

- requirement/contract/plan/progress：`docs/9.3.4/{requirement,contract,implementation-plan.md,progress}`；
- workitems：`docs/9.3.4/workitems/*.md`，15/15 closed、unchecked=`0`；
- successor review：
  `docs/9.3.4/evidence/step-2/step2-successor-r8e-independent-review-20260715.md`；
- runner exit：
  `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`；
- exact git head：`9f5428d8d15d08457d2d2d57296256178c224f5d`；
- source identity：
  `12749d1fb9d37af04b8a3dd80ac49ea0fcc177309edcc0c49645a2c2c19a1a53`；
- confirmed successor manifest：
  `4259a452bf4282f85ebb8bfe092127ec3ebec95652e7c009792081f86b84b919`；
- independent read-only review：successor/runner raw XML and hash recomputation、
  normalized rename comparison、production/test Java diff review、signal injection。

## Changed Surface

- build：root Surefire/Failsafe 3.5.3 ownership、skip/zero-test defaults、model profile；
- tests：35 个 `IntegrationTest→IT` rename、namespace/capability/zero-skip fixtures；
- authority：`verify-v934-{step2-successor,unit,integration}.sh`、shared
  `authority_runner_lib.sh`、successor/report tools、sealed inventories；
- production：Mongo auto-configuration ordering、MultiThreadExecutor completion、
  PredefinedCalculatedFieldInjector defensive copy；
- docs/CI：当前 runner 调用方、evidence、workitems 与 9.3.4 roadmap status。

## Quality Checklist

| Check | Result | Evidence |
|---|---|---|
| scope and unrelated-change control | pass | 35 rename normalized review；34 mechanical，1 reviewed capability seam |
| production change minimality | pass | three workitem-bound fixes；no public API/module-boundary change |
| fail-closed authority lifecycle | pass | INT/TERM/HUP process=durable `130/143/129`；failed status；summary absent |
| concurrency / publication identity | pass | shared exclusive lock、publish CAS、cross-run splice negative |
| report cardinality and zero semantics | pass | 724 positive + 59 structural exact；5,205 testcase；F/E/S0 |
| readability and rationale | pass-with-risks | shared helper centralizes critical lifecycle；runner orchestration still duplicated |
| workitem/evidence closure | pass | 15/15 closed；r8d evidence superseded；r8e exact run roots documented |
| documentation consistency | pass | Step 2 passed、Step 3 ready、version in-progress、9.3.5 queued |

## Findings

Resolved before this decision：

1. High — completed-window signal fail-open。已由 signal mapping、non-reentrant
   finalizer、有序 disarm、schema-2 contract 和三类动态探针修复；r8e 全链重跑。
2. Medium — r8e evidence 缺 exact Git/run-root。successor、Unit、Integration 原始
   `target/.../runs/<run-id>` 与 Git head 已补齐；Step 2 archive 明确 N/A。
3. Medium — Step 2 quality/coverage record 路径与 progress gate scope 不一致。本记录
   作为 feature-scope pre-coverage gate；Step 7 仍另做 version-scope final gates。

Open non-blocking findings：

1. Low — Unit/Integration 在撤销 EXIT trap 后才向 `tee` 输出最终 PASS；若 pipe 此时
   断开，理论上可能出现 shell 非零但 durable status 已绿色。r8e 日志实际包含 PASS，
   当前 identity 不受影响；下一代 authority 应把 PASS 输出移到有序 disarm 前。
2. Low — Unit/Integration runner 仍有 baseline/marker/hash/summary 编排重复；
   successor/report tools 也偏大。shared lifecycle 已消除安全关键漂移，Step 5 single
   authority runner 应继续收敛非关键重复。
3. Low — report tool 在 `resolve()` 后检查 symlink；越界链接仍被 containment 拒绝，
   r8e run roots 无 symlink，但检查顺序后续应更直观地 fail closed。
4. Low — `MultiThreadExecutor` 等待循环保留每秒 `System.out`，可能污染应用/CI 标准
   输出；后续改为参数化 debug 日志。
5. Low — `FsscriptClientProxyTest` 的主要回归依赖 100ms releaser，尚无对
   `shutdown=false`、`stopIfHasError=true`、等待线程 interrupt 的逐分支 latch 测试。

## Risks / Follow-ups

- owner `foggy-core`：在 9.3.4 Step 4 前补 deterministic branch tests，并将等待日志
  收敛到 logger；不影响已证明的 premature-completion regression。
- owner `test-infrastructure`：Step 5 以 single release runner 复用 shared lifecycle，
  不复制一套 signal/status/lock 逻辑；同时在不复用当前 evidence 的下一代 runner
  调整最终 PASS/disarm 顺序与 symlink check 顺序。
- Step 3–7 的未实现项是版本进度，不属于本 feature quality blocker；不得被本记录
  解释为 9.3.4 version acceptance。

## Recommended Next Skills

- `foggy-test-coverage-audit`：审计 Step 2 requirement、15 个 BUG、Unit/IT、negative
  probe 和 independent-review 的映射完整性。
- Step 3 实施完成后再做其独立 implementation quality，不复用本记录预签收五库矩阵。

## Decision

`ready-with-risks`。Blocker/High/Medium open=`0/0/0`；五个 Low 已有 owner/后续阶段，
不影响进入 Step 2 coverage evidence audit。该决定只放行 Step 2 feature evidence，
9.3.4 仍为 `in-progress`，Step 3=`ready`，9.3.5=`queued`。
