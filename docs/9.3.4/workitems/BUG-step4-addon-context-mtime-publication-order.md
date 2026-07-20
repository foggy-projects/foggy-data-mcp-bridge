---
doc_type: bug-remediation
version: 9.3.4
ticket: step4-addon-context-mtime-publication-order
status: ULTRA_EXECUTING
parent_feature: step4-replacement-coverage-gate-signoff
authorization: ongoing approved 9.3.4 release-authority scope
opened_at: 2026-07-20
---

# Step 4 Addon child-context publication order

## Problem

在 WSL 文件时间短暂回退时，已验证的 Step 3 parent marker 可能比随后发布的 Addon child
`run-context.json` 具有更晚的 `mtime_ns`。Step 3 的 anti-splice guard 因而正确地以
`E_CROSS_RUN_SPLICE` 拒绝该 run；不得删除、放宽或在 consumer 端绕过该 guard。

## Approved Remediation Boundary

- 仅修改已声明为 Step 4 successor amendment 的
  `scripts/verify-v934-preagg-addon-lifecycle.sh`。
- 仅在 inherited authority 已验证 canonical parent path、regular/non-link identity、SHA-256 与
  schema 后，为 child context 的临时 inode 设置不早于 parent marker 的时间下界；随后 fsync、
  原子发布、重读 child bytes，并再次核验 parent identity/SHA。
- standalone 模式不接收 parent provenance；冻结的 `scripts/v934/step3/**`、其 manifest 和
  `E_CROSS_RUN_SPLICE` consumer rule 均不得修改。
- 不改变 production API、coverage threshold、report cardinality、fixture ownership 或 release
  authority scope。

## Required Revalidation

该修复改变了正式 runner bytes，故 formal-r11 与其 feature acceptance 仅保留为历史证据，不能
覆盖新 runner。必须完成新的 clean Cdiag → fresh diagnostic → candidate/independent review →
direct-single-parent Cfreeze → fresh formal → Step 5 rehearsal 链路后，才可恢复 Step 4 downstream
authority。

## Verification Obligations

- normal parent/child publication remains valid;
- forced parent-later mtime path preserves `child_mtime >= parent_mtime` before atomic publication;
- malformed, hash-mismatched, symlinked, or publication-raced parent evidence fails closed;
- existing stale-report and downstream cross-run-splice negatives remain rejecting;
- successor, Step 4, and Step 6 hash closures validate before any diagnostic run.

## Non-Goals

- no historical runtime artifact reuse or cross-run evidence splice;
- no Step 5–7, 9.3.5, or 9.4.0 production implementation advancement until revalidation closes.
