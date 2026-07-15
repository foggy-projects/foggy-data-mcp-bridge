---
doc_role: implementation-quality-review-evidence
doc_purpose: Record why the confirmed r7 successor and passing r7 Unit run are diagnostic only.
version: 9.3.4
step: 2
status: failed-quality-gate
reviewer: v934_r6_identity_review
created_at: 2026-07-15
updated_at: 2026-07-15
---

# Step 2 r7 authority quality review

## Decision

`RED / supersession-required`。r7 successor 的 inventory identity 与双人独立复核仍然
有效，r7 Unit 也确实完成了全部 runtime regression；但 authority/evidence 实现存在
3 个 high、2 个 medium，不能把 r7 提升为 Step 2 exit authority。必须整改为 r8 并从头
执行 Unit 与 Integration，禁止拼接 r7 产物。

## r7 Unit diagnostic baseline

| Field | Value |
|---|---|
| run | `step2-unit-r7-20260715` |
| reports | `677 execution + 55 structural = 732 raw` |
| testcases | `4890` |
| failures / errors / skips | `0 / 0 / 0` |
| source before / after | `1eb9a2a7cf1d6090bfbdc6e1cc71e0f9d4ff904c6235e85b50070722270fd40c` |
| summary | `3db2bba707e8af40902197c7547e3bd4a6e0b7df51d0f4edf48bc0289ec39daf` |
| final manifest | `0e5cbaf776254b39c1caf56b27422e6c387ddb96e61c9490c226de505d6699b2` |
| negative probes | `fe701510de307d831c79113a35f9837266ec44fe138dce18884b85366391aeb3` (`14/14`) |
| run marker | `2b1c29c4be4f1ae61c2f33718897561ed9d97312a5807d6fd14338d2b2068217` |

该 run 没有 durable `run-status.env`，且使用 report schema 2；仅保留为真实回归通过与
r8 cardinality 兼容性的诊断证据。

## Findings

1. **HIGH — workspace concurrency**：三个 authority entrypoint 没有共享锁；报告目录、
   test bytecode 和 successor canonical publish 可并发串扰，publish 还存在 TOCTOU。
2. **HIGH — cross-run splice**：per-variant manifest 不绑定 outer run/HEAD/source，merge
   只按 variant 与报告集合校验，可以跨 run 拼成 passed。
3. **HIGH — discovery cardinality**：positive XML 仅要求 `tests > 0`，未与 hash-sealed
   discovery nodes 对比；原 negative baseline 把所有 suite 伪造成一个 testcase 仍能通过。
4. **MEDIUM — Maven zero-test default**：Surefire/Failsafe 的 `failIfNoTests` 未显式设为
   true，脱离 wrapper 的 owning lane 仍可零测试绿色。
5. **MEDIUM — Unit durable status**：Unit 没有 Integration 对称的 log/EXIT status；后置
   阶段失败时可能留下局部 passed manifest 而没有 outer-run failed 状态。

## Cardinality compatibility calculation

r7 Unit 真实 XML 与冻结 discovery 的只读复算结果：

- `671` 个静态 positive report：actual testcase nodes 全部 exact equal discovery；
- `6` 个 runtime-deferred report：actual 全部不少于
  `discovered_test_nodes + runtime_deferred_containers`；
- discovery static nodes 合计 `4811`、deferred containers `18`、actual `4890`；
- `55` 个 structural reports 全部严格为零。

因此 r8 可引入静态 exact、dynamic minimum 的 fail-closed 规则而不改变测试语义或
inventory 基数。

## Required successor

r8 至少必须封存：workspace exclusive lock、publish CAS、report schema 3 outer-run
binding、discovery cardinality、POM zero-test defaults、Unit/Integration durable status。
对应 workitems 为：

- `BUG-step2-authority-workspace-concurrency.md`；
- `BUG-step2-report-cross-run-splice.md`；
- `BUG-step2-report-discovery-cardinality.md`；
- `BUG-maven-zero-test-default.md`；
- `BUG-unit-authority-durable-status.md`。
