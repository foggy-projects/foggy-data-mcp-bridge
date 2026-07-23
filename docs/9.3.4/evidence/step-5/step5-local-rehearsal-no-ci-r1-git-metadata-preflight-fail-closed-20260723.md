---
doc_type: execution-evidence
version: 9.3.4
step: 5
run_id: step5-local-rehearsal-20260723-no-ci-r1
mode: rehearsal
tested_commit: ea0791160764f0d9d949016733d463119fa635c6
status: failed-closed
decision: NEEDS_REPLAN
created_at: 2026-07-23
---

# Step 5 local rehearsal no-CI r1 — Git metadata preflight fail-closed

## Decision

- verdict: `failed-closed / attempt consumed / NEEDS_REPLAN`
- failure_kind: execution-environment contract mismatch；不是产品测试失败。
- failed_phase: `runtime-source-before`
- exit_code: `1`
- downstream_execution: Step 4、Maven、Docker package/image、archive 和 pointer
  publication 均未开始。
- retry: 禁止自动重试；必须先由 owner 批准新的执行环境契约。

## Authorization and Identity

- scope commit: `deb92e78d8a01aeef227f6ca8c9f9a60d9b162a2`
- activation/tested commit: `ea0791160764f0d9d949016733d463119fa635c6`
- remote branch at launch:
  `origin/codex/v934-release-authority=ea0791160764f0d9d949016733d463119fa635c6`
- PR: `#124`，head 与 tested commit 精确一致。
- GitHub Actions: `enabled=false`；未启用 CI、required check 或 branch protection。
- launch time: `2026-07-23T19:51:32+08:00`
- failure receipt time: `2026-07-23T19:51:36+08:00`

## Preflight Result

- worktree: clean、non-shallow、exact pushed HEAD。
- competing v934 runner: none；历史 r16 pane 已 dead/status=0。
- Maven/JVM controls: tmux launch 使用 `env -u` 清除全部 8 个冻结变量。
- Step 4/5/6 manifests: exact PASS。
- syntax/compile: shell `bash -n` 与相关 Python `py_compile` PASS。
- Step 5 focused static:
  - artifact self-test: PASS
  - package negative: `120/120`
  - pointer negative: `5/5`
- frozen runtime base:
  - OCI index:
    `sha256:02320dd4ce20e243dfb915c686089cf9315c763084fafbb12d5c9993aee18b57`
  - linux/amd64 manifest:
    `sha256:b658bee7bbf0277559bd07dfb2e8473c30dc90c3da0d8cfe568e61f52792ce52`
  - config:
    `sha256:af15432fe4678068270da7f69356edd1e53555f15671a6373ce44d9e65c2dfcc`
- required ports: `13306/13307/15432/11433/17017/16379/19530` all free。
- initial pointer state: candidate absent；final authority absent；runs root absent。

## Process Owner

- tmux session: `v934-step5-rehearsal-r1`
- control directory:
  `/tmp/v934-step5-rehearsal-20260723-r1.Ajm9vQ`，mode=`0700`
- observer policy: launch 后未向 pane/runner 写入控制信号。
- final pane state: dead，status=`1`。

## Failure Evidence

Runner output：

```text
[v934-release-artifact] ERROR E_DIRECTORY: runtime source Git metadata is not a real directory
[v934-release] FAILED run=step5-local-rehearsal-20260723-no-ci-r1 phase=runtime-source-before
```

Run-owned `failure.env`：

```text
run_id=step5-local-rehearsal-20260723-no-ci-r1
mode=rehearsal
phase=runtime-source-before
exit_code=1
status=failed
```

Source identity was established before the failure：

- tracked file count: `4296`
- Git HEAD: `ea0791160764f0d9d949016733d463119fa635c6`
- source SHA-256:
  `8ea272ee46e7b4bafd3bae7ae718f13344f23008fd548132cd38ff22ae546ae0`

## Root Cause

`release_artifact_tool.py::runtime_source_receipt()` calls：

```text
real_directory(repo / ".git", "runtime source Git metadata")
```

The governed execution directory was a clean independent Git worktree. Its
`.git` is a regular `gitdir:` file pointing to the main repository worktree
metadata, not a real directory. The tool therefore rejected the environment
before producing `runtime-source-before.json`.

This exposes a preflight/contract gap：

- the delivery contract required an independent clean worktree to protect the
  user's dirty workspace；
- the release artifact tool has a stricter, previously unstated full-clone
  requirement；
- the low-cost preflight did not call `scan-runtime-source`, so the mismatch
  was first observed after the formal attempt started。

## Pointer, Resource, and Evidence Safety

- candidate pointer: absent
- final authority pointer: absent
- Step 4 run root: absent
- Maven/test lanes: not started
- v934 Docker containers/volumes/networks created by this run: none
- source mutation: none observed
- production source/POM/workflow/API/SPI changes: none

Evidence hashes：

- `failure.env`:
  `1f82b8d8fd2f9de2f2aedf6817368149d39ddd33d401c447eb555c9579b8dfcd`
- empty `runtime-source-before.json`:
  `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- `source-before.json`:
  `350b6d0fcd563358c8d75d3eec82e88bff8de5994924c30d226964452a448f4b`
- `source-before.tsv`:
  `8ea272ee46e7b4bafd3bae7ae718f13344f23008fd548132cd38ff22ae546ae0`
- observer `runner.log`:
  `706aa7065109a9db2d3a12213e72485966700f63664d59f1fc5ac2a2a0bf9730`

## Replan Boundary

The minimal recommended replan is environment-only：

1. preserve the current no-CI scope and all runner/tool bytes；
2. use a new private full clone whose `.git` is a real directory；
3. add `release_artifact_tool.py scan-runtime-source --repo-root <clone>` to
   low-cost preflight before attempt activation；
4. re-confirm exact pushed HEAD, pointer initial state, Docker/base image and
   process-owner isolation；
5. authorize exactly one replacement rehearsal attempt。

Supporting linked worktrees inside `release_artifact_tool.py` would change
sealed Step 5 tooling and the Step4→Step6 hash closure. That is a larger
alternative and is not authorized by this failure record.
