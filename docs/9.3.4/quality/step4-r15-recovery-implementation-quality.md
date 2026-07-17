# Step 4 diagnostic-r15 recovery implementation quality

- reviewed_at: 2026-07-17
- scope: r15 Unit fail-closed evidence、Bean2Map deterministic correctness remediation、
  current-state documentation and diagnostic machine boundary
- mode: formal implementation quality gate / pre-Cdiag
- decision: pass / ready-for-one-Cdiag-commit-and-fresh-diagnostic
- B/H/M/L: `0/0/0/0`
- open mandatory fixes: `0`
- downstream authorization: one new Cdiag commit/push and one fresh diagnostic only

## Review Basis

diagnostic-r15=`step4-coverage-20260717-diagnostic-r15` 在 clean/pushed Cdiag
`9270d2d4e58684226aeb15eff55b027e6aa4a7eb` 上进入完整 Unit replacement，随后由
`Bean2MapUtilsTest#testCachingMechanism` 的单次纳秒倍率断言触发 fail closed。r15 仅产生
partial `26 reports / 124/F1E0S0` 和 `2/48 sessions`；source-after、最终 sensitive scan、
report/exec inventory、aggregate、observation、candidate、summary/gate 均 absent。run-owned
cleanup=`0/0/0`，该失败证据保持 immutable、禁止跨 run 拼接。

原测试在方法执行前已使用相同 SourceBean/TargetBean class，static cache 已预热；三个样本实际
都是 cache hit。first/second/third=`26,839/304,859/8,517ns` 不能证明缓存正确性或退化，
属于 JVM/JIT/GC/OS/JaCoCo 调度敏感的 test oracle，不是 product regression。

实现 delta 只修改两个既有 testcase。`testCachingMechanism` 使用三个同类、不同
`SourceBean` 实例与不同 name/age，分别复制到三个 target 并硬编码精确断言，能够拒绝 cache
错误绑定首个 source 实例；`testPerformanceWithManyObjects` 保留 1000-copy 与首末 correctness，
删除固定 1000ms 门。无 production、public API、POM、runner、machine、coverage floor、
critical set、exclusion 或 testcase-cardinality 变化。

## Verification

| Check | Result |
|---|---|
| immutable r15 failure | `failed/child-unit/exit 1`；partial=`124/F1E0S0`、`2/48 sessions`；final sensitive/success-only artifacts absent；cleanup=`0/0/0` |
| focused JUnit/JaCoCo | 10 个独立 Maven/JVM forks，均 `1/F0E0S0`；目标 class/probe bitmap 10/10 exact identical |
| full class | `23/F0E0S0`；exec SHA-256=`df6914b25731199bd2673e09e5c95ebd311422b021059792539da41145249cff` |
| full module | `27/F0E0S0`；exec SHA-256=`20a9050f1abf9b7711a7a13cf830ce9160c69853ccc5e40bafa8a986ecbb37d2` |
| testcase cardinality | `Bean2MapUtilsTest @Test=23 -> 23`；module remains 27 nodes |
| production/API shape | no `src/main`、API、POM、runner、machine、floor、critical-set or exclusion changes |
| top manifest | `60/60`；SHA-256=`cc356897f6588beedf00c057c5988a176b6cef241d4d9c274103b691d254dc60` |
| successor manifest | `14/14`；SHA-256=`3d4ed31f1ca8e3f1086ceade81be9ce94d90ee82beae1117dc708f71064a6e0f` |
| full contract | `workflow_state=diagnostic / threshold_status=diagnostic-pending / passed`；required=`773+59/5707`、exec/session=`23/48` |
| successor overlay | `parents=3 / contracts=4 / amendments=21 / bindings=9 / required=45/446 / addon=2/6 / passed` |
| current machine | threshold=`diagnostic-pending` SHA-256=`0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96`；contract=`diagnostic-ready` SHA-256=`15dae282395d920ffb3d2aae4c518f0d1c8be09aaed8b08e40044f9d9bc6b0b0` |
| r15 preflight boundary | contract/source/threshold/XML/overlay=`27/27 + 22/22 + 12/12 + 118/118 + 12/12`；toolchain=`5/5`；class universe=`24/2098` |
| diff hygiene | `git diff --check` passed；test source working EOL normalized to indexed LF |

## Findings and Closure

### Initial code-review Medium

首版 deterministic oracle 在三次调用之间修改同一个 source 对象；若错误 cache 保存该对象引用
并继续读取其当前值，测试仍可能全绿。修复改为三个同类、不同实例；新 10-fork/class/module
证据全部重新生成，旧修复哈希不复用。独立复验确认该 Medium 已关闭。

### Initial evidence/documentation Medium and Low

独立审查发现并关闭：

1. formal-r2 recovery pending 段曾与已完成 Cdiag/r15 冲突，现均标 historical/superseded；
2. Current Progress/Inventory 曾沿用旧 contract/XML 数量，现更新为 r15 preflight exact，并明确
   历史 fixture/report 结果不得拼接；
3. 四个 demo DB 的恢复曾被归因给 r15 wrapper，现明确为 evidence-window 外编排观察，不属于
   r15 runner-owned evidence；
4. remediation class XML 是 canonical target 的可覆盖文件，现只标 contemporaneous、
   non-retained，不作为 r15 authority。

三路独立最终复验均为 PASS，分别覆盖代码/测试语义、immutable evidence/hash 与跨文档状态；
合并最终 B/H/M/L=`0/0/0/0`。

## Documentation and State Review

README、roadmap、requirement、contract、module responsibility、implementation plan、progress、
test plan、acceptance evidence plan、code inventory、BUG 与 failure evidence 已统一为：Steps 1–3
passed；Step 4=`r15 Unit fail-closed / deterministic timing-oracle remediation verified / new Cdiag
pending`；machine=`diagnostic-ready/diagnostic-pending`；final sensitive scan absent；partial evidence
不可拼接；Step 5、coverage audit、acceptance 与 9.3.5 保持关闭。

## Decision

当前 delta 可一次提交并 push 为新的 Cdiag。提交后必须证明 clean
`HEAD == origin/main` 与 parent topology，再停止 exact demo DB containers，只在 evidence window
运行唯一 fresh diagnostic，并在退出后按 exact IDs 恢复。该 gate 不放行 formal、coverage
audit、acceptance、Step 5 或 9.3.5；fresh diagnostic 失败时继续 fail closed。
