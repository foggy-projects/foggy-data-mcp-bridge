# Step 4 formal-r1 recovery implementation quality

- reviewed_at: 2026-07-17
- scope: formal-r1 fail-closed analysis, deterministic WatchService lifecycle regression,
  diagnostic machine-state recovery and documentation writeback
- mode: formal implementation quality gate / pre-Cdiag
- decision: pass / ready-for-one-Cdiag-commit-and-fresh-diagnostic
- B/H/M/L: `0/0/0/0`
- open mandatory fixes: `0`
- downstream authorization: fresh diagnostic only

## Review Basis

formal-r1=`step4-coverage-20260717-formal-r1` 在 Cfreeze `86d505e` 上完成全部 lane，随后
因 `WatchServiceFileTracer` 相对 r13 少 `9 line / 3 branch` 触发 `E_FORMAL_LOW`。逐 class
XML、method/source line 与 23 exec probe 对比证明该变化只来自 tracer/JaCoCo JVM shutdown
hooks 的无序竞态；formal-r1 保持 immutable failed evidence，没有通过重跑规避门禁。

实现 delta 只修改既有 `WatchServiceFileTracerTest#testWatchServiceAvailable`：反射创建
isolated tracer，以 `try/finally` 显式 shutdown，断言 isolated 从 available 变为 unavailable，
并断言全局 singleton 仍 available。没有生产代码、公共 API、coverage floor、critical set、
exclusion、report identity 或 testcase cardinality 变化。

## Verification

| Check | Result |
|---|---|
| focused JUnit | 5 个独立 Maven/JVM forks；每次 `11/F0E0S0` |
| JaCoCo target class | 5/5=`177/245 probes`；formal-r1 缺失 7 probes 全命中；packed bitmap unique=`1` |
| isolation | isolated shutdown 后 unavailable；singleton 前后 available；无残留 Maven/Watch thread |
| shutdown idempotence | isolated 显式 shutdown 后 JVM hook 重入无异常 |
| pending threshold | b765 exact blob SHA `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96` |
| diagnostic contract | b765 exact blob SHA `15dae282395d920ffb3d2aae4c518f0d1c8be09aaed8b08e40044f9d9bc6b0b0` |
| Step 4 manifest | b765 exact blob SHA `cc356897f6588beedf00c057c5988a176b6cef241d4d9c274103b691d254dc60`；`60/60` |
| full contract | `workflow_state=diagnostic / threshold_status=diagnostic-pending / passed` |
| contract negatives | mutations=`27/27`、source/Git identity=`22/22`、threshold/frozen replay=`12/12` |
| successor overlay / Step 2 view | passed / parent=`724+59`、amendment=`12` |
| diff hygiene | `git diff --check` passed |

## Findings

### Blocker

无。

### High

无。

### Medium

无。测试不会关闭 singleton；isolated worker 由显式 shutdown 等待退出。constructor 留下的
单个 JVM hook 只会幂等重入已完成的 shutdown，不再决定首次 coverage probes。

### Low

无开放项。focused raw exec 位于 ignored `target/`，tracked failure evidence 已记录复现命令、
5 份 exec SHA、共同 class ID/packed bitmap SHA 与 Surefire XML SHA；最终 authority 仍必须由
fresh diagnostic/formal 产生，focused receipt 不冒充 release evidence。

## Documentation and State Review

README、requirement、implementation plan、progress、test plan、acceptance evidence plan、
code inventory 与顶层 roadmap 已统一为：Steps 1–3 passed，Step 4 formal-r1 fail closed，
deterministic remediation verified，machine tuple restored to
`diagnostic-ready/diagnostic-pending`，new diagnostic pending。历史 r9/r12/r13/Cfreeze sections
已标为 snapshot/historical，formal-r1 之后的 current boundary 明确优先。

## Decision

当前 delta 可一次提交并 push 为新的 Cdiag。提交后必须证明 clean
`HEAD == origin/main` 和 source-hash，再停止 exact demo DB containers并运行唯一 fresh
diagnostic。该 gate 不放行 formal、coverage audit、acceptance、Step 5 或 9.3.5；新 diagnostic
失败时继续 fail closed。
