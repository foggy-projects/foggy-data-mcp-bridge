---
doc_role: companion-test-evidence
doc_purpose: Bind the deterministic MultiThreadExecutor prerequisite result used by the Step 4 entry decision.
version: 9.3.4
step: 3
status: passed
evidence_id: step3-multithread-prerequisite-20260716-r1
tested_commit: ce3d70c391c7b8bd8046fe66dde0ad568d66601e
recorded_at: 2026-07-16
---

# Step 4 MultiThread Prerequisite Evidence

## Execution Identity

- command: `mvn -pl foggy-core -Dtest=MultiThreadExecutorTest test -q`
- working directory: repository root
- HEAD at invocation: `ce3d70c391c7b8bd8046fe66dde0ad568d66601e`
- worktree at invocation: clean
- process exit: `0`
- completed/mtime: `2026-07-16 04:44:35.971703998 +0800`
- raw Surefire XML:
  `foggy-core/target/surefire-reports/TEST-com.foggyframework.core.thread.MultiThreadExecutorTest.xml`
- raw XML size: `16377` bytes
- raw XML SHA-256:
  `5d3ba9ff2778886160262b499999753f98a0b9251f5a76f6b9f86c45d723291a`

## Source Identity

- production source:
  `foggy-core/src/main/java/com/foggyframework/core/thread/MultiThreadExecutor.java`
- production SHA-256:
  `500c03c5ec2f3c077c557970286ddf320f5080c666443ca2f08f64181be7bff1`
- test source:
  `foggy-core/src/test/java/com/foggyframework/core/thread/MultiThreadExecutorTest.java`
- test SHA-256:
  `d857e64773bc511820c735d342a9f7ea5109478da2f7dc2318eebdcdc938c607`

两个 source SHA 同时由
`scripts/v934/step3/step3-required-contract.json` 的 Step 4 prerequisite bindings
冻结；parent contract validation 在本次记录前后均通过。

## Result

Surefire testsuite metrics：`tests=3 / failures=0 / errors=0 / skipped=0`。

| Testcase | Validation branch | Result |
|---|---|---|
| `waitWithoutShutdownUsesTaskSnapshotAndKeepsExecutorReusable` | `shutdown=false` task snapshot and executor reuse | passed |
| `stopIfHasErrorPropagatesFailureAndStopsRemainingTasks` | `stopIfHasError=true` failure propagation and remaining-task stop | passed |
| `waiterInterruptRestoresInterruptFlagAndStopsShutdownExecutor` | waiter interrupt restoration and shutdown exit | passed |

Production waiting output is emitted through the debug logger rather than `System.out`。

## Evidence Boundary

- 本 companion result 不属于 Step 3 deferred required inventory，不计入 45/446。
- raw XML 位于 ignored Maven target，可能被后续测试覆盖；本记录持久化其 exact
  command、HEAD、source/test SHA、mtime、size、metrics、testcase 与 content SHA，
  使当次 artifact identity 可审计和按同一 commit 复现。
- 这不是 immutable release archive，也没有 JaCoCo exec。Step 4 仍必须带 agent 重跑
  canonical lanes；Step 5 才负责 portable archive contract。

## Decision

三个 Step 2 遗留的 deterministic concurrency branches 与 logger 补强均有 same-HEAD
绿色证据。该 prerequisite=`passed`，允许 Step 4 entry 在 Step 3 feature gates 完成后
标为 `ready / not-started`。
