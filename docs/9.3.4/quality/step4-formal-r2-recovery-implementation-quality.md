# Step 4 formal-r2 recovery implementation quality

- reviewed_at: 2026-07-17
- scope: formal-r2 fail-closed analysis, deterministic ListPreset filename-false regression,
  diagnostic machine-state recovery and documentation writeback
- mode: formal implementation quality gate / pre-Cdiag
- decision: pass / ready-for-one-Cdiag-commit-and-fresh-diagnostic
- B/H/M/L: `0/0/0/0`
- open mandatory fixes: `0`
- downstream authorization: one Cdiag commit/push and fresh diagnostic only

## Review Basis

formal-r2=`step4-coverage-20260717-formal-r2` 在 Cfreeze
`1901a10138bac06a09b875c907b7aea6e2789b04` 上完成全部 lane，随后因 aggregate branch
相对 r14 reviewed threshold 少 `1` 触发 `E_FORMAL_LOW`。逐 class XML、method/source line、
23 exec probe 与 report/testcase identity 对比证明，唯一变化来自
`FileSystemListPresetStore#findById` 的 filename-false outcome；`Files.find(...).findFirst()`
在 UUID 文件上的遍历顺序未定义。formal-r2 保持 immutable failed evidence，没有通过重跑或
降低 threshold 规避门禁。

实现 delta 只在既有
`ListPresetServiceTest.FileStoreTests#shouldIsolatePresetByUserAndBusinessKey` 增加一个
missing-ID empty assertion。该用户目录已有两个 regular preset files，不存在 ID 查询会穷尽
目录，确定性执行 filename-false outcome。没有生产代码、公共 API、POM、runner、coverage
floor、critical set、exclusion、report identity 或 testcase cardinality 变化。

## Verification

| Check | Result |
|---|---|
| formal-r2 failure | required=`773+59/5707/F0E0S0`、Addon=`2/6`、exec/session=`23/48`；aggregate line exact、branch `-1`；12 critical exact；cleanup=`0/0/0`；sensitive PASS |
| focused JUnit/JaCoCo | 5 个独立 Maven/JVM forks；每次 `11/F0E0S0`；probe 106=`5/5`；packed bitmap unique=`1` |
| Data Viewer module | `104/F0E0S0`；目标 class=`74/113`，恢复 r14 exact bitmap |
| production/API shape | no production、API、POM、runner、floor、critical-set or exclusion changes |
| testcase cardinality | existing `@Test` remains `11 -> 11`；no new report identity |
| pending threshold | SHA-256 `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96` |
| diagnostic contract | SHA-256 `15dae282395d920ffb3d2aae4c518f0d1c8be09aaed8b08e40044f9d9bc6b0b0` |
| Step 4 manifest | SHA-256 `cc356897f6588beedf00c057c5988a176b6cef241d4d9c274103b691d254dc60`；`60/60` |
| successor manifest | SHA-256 `3d4ed31f1ca8e3f1086ceade81be9ce94d90ee82beae1117dc708f71064a6e0f`；`14/14` |
| full contract | `workflow_state=diagnostic / threshold_status=diagnostic-pending / passed` |
| contract negatives | mutations=`27/27`、source/Git identity=`22/22`、threshold/frozen replay=`12/12`；receipt SHA-256 `cf03f7a5a26f0f5e2bfe3ae667a0835fbbf7ad6b6489831e2d793932b9b14fb4` |
| XML negatives | `118/118`；receipt SHA-256 `9fd41898a5799347e83c0cffcc34334186c2b47a537851160a7fffc9cc28a32e` |
| successor overlay | validate PASS；negatives=`12/12`；receipt SHA-256 `cf98e3306fcf5650ca4aad9475beb0e986b5363d15e4f3d326568753edd92e06` |
| exact demo DB state | four original IDs running/healthy；ports `13306/13308/15432/11433` listening outside evidence window |
| diff hygiene | `git diff --check` passed；existing CRLF normalization warning only |

## Findings

### Blocker / High / Medium

无。

### Low

首轮独立复核发现两项 documentation Low，均已在本 gate 内关闭：

1. BUG 根因片段曾误写 `Files.find(userDir, 1, ...)`，已按生产实现纠正为 depth `6`；
2. 九份顶层状态曾写 `new diagnostic pending`，已统一收紧为首个未完成门
   `new Cdiag pending`。

修正后两路独立 review 均无开放发现，最终 B/H/M/L=`0/0/0/0`。

## Documentation and State Review

README、requirement、contract、module responsibility、implementation plan、progress、test plan、
acceptance evidence plan、code inventory 与顶层 roadmap 已统一为：Steps 1–3 passed；Step 4
formal-r2 immutable fail-closed；deterministic remediation verified；machine=
`diagnostic-ready/diagnostic-pending`；new Cdiag pending。r9/formal-r1/r14/Cfreeze 段均明确为
historical/superseded；Step 5、coverage audit、acceptance 与 9.3.5 保持关闭。

## Decision

当前 delta 可一次提交并 push 为新的 Cdiag。提交后必须证明 clean
`HEAD == origin/main` 和 source identity，再停止 exact demo DB containers 并运行唯一 fresh
diagnostic；退出后立即恢复 exact IDs。该 gate 不放行 formal、coverage audit、acceptance、
Step 5 或 9.3.5；fresh diagnostic 失败时继续 fail closed。
