---
evidence_scope: feature
evidence_kind: successor-independent-review
version: 9.3.4
target: step2-runner-split-r8e
status: passed
decision: confirm-approved
reviewed_at: 2026-07-15
reviewers: v934_r8e_identity_review+v934_snapshot_skip
blockers: 0
---

# Step 2 successor r8e 独立双审

## Decision

`step2-candidate-r8e-20260715` 两路独立复核均为 PASS，blocker=`0`，允许执行
原子 confirmation。本结论只确认 r8e successor candidate，不复用已被 signal
fail-open 发现作废的 r8d Unit/Integration 运行。

## Candidate Identity

- git head：`9f5428d8d15d08457d2d2d57296256178c224f5d`；
- raw run root：
  `target/v934-step2-successor/runs/step2-candidate-r8e-20260715/`；
- published canonical：`scripts/v934/successor/step2/`；
- Step 2 不生成 evidence archive；archive/root digest 属于 Step 5/7 authority contract，
  本记录以 candidate/canonical `SHA256SUMS`、freeze、summary、status 与 log 哈希封存；
- source before/after/独立复算：
  `12749d1fb9d37af04b8a3dd80ac49ea0fcc177309edcc0c49645a2c2c19a1a53`；
- candidate freeze：
  `7c89e16fbcc6ebf04b702a94d493ce584461a88b19a6e4a0f79383d0fee52020`；
- candidate manifest：
  `39403fb028904da3ac445787d8107cf5a25bfa9c4fd828f1a282a5d97730a231`；
- candidate summary：
  `bbf5cbf9252dfd3bf9e8a09da190405aa9bc4fd4b83ab3dbafb39406f3fac728`；
- outer status/log：
  `5ad28ad9a55c47ced4145cac51b194669ba78f5205e045ba5b9d493f29a08b21` /
  `68222ac18ea48acd439233b6335ec6d86c842036a666ff1ad70daa72d42e423e`；
- outer result：`completed / exit=0 / passed / publish-validated / rollback=not-required`。

## Independent Recalculation

两路 reviewer 均执行官方 `validate` 与 `validate-summary`，并绕过 summary 独立读取
TSV/JSON、manifest 与 raw hashes：

- workspace sources=`532`，discovery rows=`820`，classpath entries=`2395`；
- positive executions=`770`，其中 Step 2=`724`（Surefire `677` + Failsafe `47`），
  Step 3 deferred=`46`；
- structural reports=`59`（Surefire `55` + Failsafe `4`）；
- predecessor refs=`519`（execution `480` + structural `39`）；
- successor expected-negative=`33/33 passed`；manifest exact entries=`13/13`；
- exact r7 archive freeze/manifest/summary 分别为
  `3fea72715f651755897cee4464fd6075d5ea2187672e9d0fe66c97bf6d02a5d6` /
  `d0dfc94e1aa9bb8018d6ac6b5ae4b5c73ae25f6f6f81778a64e3f7787e2a3ca2` /
  `69a94475ca4c9d7162e9e6b217f637026cc790d3de4b654d9d12eb4c0dbe4f61`。

## Signal Fail-closed Review

`authority_lock_contract.schema_version=2`。独立 reviewer 在 `PHASE=completed`、已有
临时绿色 status 与 summary 的同构进程中分别注入信号：

| Signal | Process exit | Durable exit | Durable status | Summary |
|---|---:|---:|---|---|
| INT | 130 | 130 | failed | absent |
| TERM | 143 | 143 | failed | absent |
| HUP | 129 | 129 | failed | absent |

`authority-signal-contract-drift` 被精确拒绝为 `E_AUTHORITY_LOCK`。成功 disarm 顺序为
先 ignore `INT/TERM/HUP`、再移除 `EXIT`，finalizer 内屏蔽信号重入。

## Excluded Evidence

- `step2-candidate-r8d-20260715` 因 completed-window signal fail-open 被正式质量闸门
  作废；其 Unit/Integration 结果不得用于本轮 coverage/acceptance。
- r8e 与 r8d 的九个测试身份库存逐字节一致，但 control-plane freeze、runner contract、
  negative ledger 和 source seal 均为新身份。
- 把 r8d summary 拼接到 r8e canonical directory 时，官方 `validate-summary` 非零拒绝。
