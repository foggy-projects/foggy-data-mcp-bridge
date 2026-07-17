# Step 4 formal-r3 recovery implementation quality

- reviewed_at: 2026-07-17
- scope: formal-r3 fail-closed analysis, deterministic QueryModel JoinGraph double-check
  regression, diagnostic machine-state recovery and authority-document writeback
- mode: formal implementation quality gate / pre-Cdiag
- decision: pass / ready-for-one-Cdiag-commit-and-fresh-diagnostic
- B/H/M/L: `0/0/0/0`
- open mandatory fixes: `0`
- downstream authorization: one Cdiag commit/push and one fresh diagnostic only

## Review Basis

formal-r3=`step4-coverage-20260717-formal-r3` 在 clean/pushed Cfreeze
`a63c82c53ebaad1a1c22d78647fbda70b4bd6594` 上完成全部 lane，随后因 aggregate branch=
`26110/44870` 低于 reviewed exact `26111/44870` 一个 outcome 而触发 `E_FORMAL_LOW`；line=
`54624/76830` exact。required=`773+59/5707/F0E0S0`、Addon=`2/6`、exec/session=
`23/48`、12 个 critical class、class universe=`24/2098`、cleanup=`0/0/0` 与 sensitive scan
均通过。success-only summary/gate/candidate/final absent，失败证据保持 immutable。

r16 diagnostic 与 formal-r3 的 class/method/source counters 逐项比较后，唯一差异是
`QueryModelSupport#getMergedJoinGraph` line 316 inner double-check：source-line branch 从
`0 missed / 2 covered` 变为 `1 missed / 1 covered`；method branch 从 `0/4` 变为 `1/3`。
旧覆盖依赖 incidental scheduling，并非 production、report、exec、test inventory 或 class-universe
回归。

最小实现只修改既有
`RuntimeNamedDataSourceResolverBindingTest#publicationGuardSerializesTheCallbackWithAConcurrentRebind`。
第一个 caller 在 synchronized 初始化内阻塞于 mocked `TableModel#getJoinGraph` build latch；第二个
caller 启动后，测试用 `ThreadMXBean` 同时确认 `BLOCKED`、lock owner=first caller、lock identity=
同一 `QueryModelSupport`，再释放 build。随后断言两个 future 返回同一 graph、root identity 正确且
build count=`1`。原 datasource 断言未改并先完成；所有等待均为 5 秒有界，局部 `finally` 释放 latch
并取消未完成 future，外层 `finally` 执行 `shutdownNow` 和 termination 断言。

没有 `src/main`、public API、POM、runner、coverage floor、critical set、exclusion、report identity 或
testcase cardinality 变化。候选 `JoinGraphTest` 改动因冻结保护树边界被撤销，最终 successor overlay
前后均 PASS。

## Verification

| Check | Result |
|---|---|
| immutable formal-r3 | `failed / formal-coverage-gate / exit 1`；line exact；branch `-1`；success-only artifacts absent；cleanup/sensitive PASS |
| exact targeted regression | `1/F0E0S0` |
| focused JaCoCo | 5 个独立 Maven/JVM fork 全 PASS；`QueryModelSupport` class id=`d242dafe9de31249`、`34/629 probes`、packed bitmap unique=`1` |
| full owner class/module | `RuntimeNamedDataSourceResolverBindingTest=5/F0E0S0`；`foggy-runtime-api=128/F0E0S0` |
| testcase cardinality | HEAD/current `@Test=5 -> 5`；无新增或改名 testcase |
| production/API shape | production delta=`0`；无 POM/runner/floor/critical-set/exclusion 变化 |
| pending threshold | SHA-256 `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96` |
| diagnostic contract | SHA-256 `15dae282395d920ffb3d2aae4c518f0d1c8be09aaed8b08e40044f9d9bc6b0b0` |
| Step 4 manifest | SHA-256 `cc356897f6588beedf00c057c5988a176b6cef241d4d9c274103b691d254dc60`；`60/60` |
| full contract | `workflow_state=diagnostic / threshold_status=diagnostic-pending / passed`；required=`773+59/5707`、exec/session=`23/48` |
| successor overlay | `parents=3 / contracts=4 / amendments=21 / bindings=9 / required=45/446 / Addon=2/6 / passed` |
| Step 2 parent view | `724 positive + 59 structural / amendment=12 / passed` |
| contract negatives | mutation=`27/27`、source/Git identity=`22/22`、threshold/frozen replay=`12/12`；receipt SHA-256 `ed9a5945544ee7c23969f8619d1488079b1bca39ed33d1b88f3b69c72b86757b` |
| fast XML negatives | `118/118`；receipt SHA-256 `9fd41898a5799347e83c0cffcc34334186c2b47a537851160a7fffc9cc28a32e` |
| exec negatives | `17/17`；receipt SHA-256 `b80642a5fb91e084f05ecebe2f12e8ab1b7d4d229bef95a6500e87174779b2b2` |
| overlay negatives | `12/12`；receipt SHA-256 `cf98e3306fcf5650ca4aad9475beb0e986b5363d15e4f3d326568753edd92e06` |
| inventory negatives | `30/30`；receipt SHA-256 `9911f235514e8cb2294183563949dce4d5864ec939c36e5f1042abaeff3c86aa` |
| report-view negatives | `12/12` |
| lifecycle/authority negatives | run-log lifecycle PASS；authority parent positive=`2`、negative=`14` PASS |
| demo DB boundary | original four container IDs remain `running/healthy`；ports `13306/13308/15432/11433` listening |
| diff hygiene | `git diff --check` passed |

Focused exec SHA-256：

1. `a4a9d4daabbb59670bd6e49fdb5b90e4fc8c0313745b6c59a43ab0844eb2c3a8`
2. `75ede280e74122387aa677843119b9c8b5f94b855a0eb2e2db52b09f19ae34bc`
3. `ea4860cc1743c7cdfc18f87c6b0a06fe236eb8f7bcccac812b01c1cd21dd4a1c`
4. `d441b173b558224908ae26e8ff22a83f10ca15bd4cea3e5c3e35e11ae95df9af`
5. `06604255911291d5329a60d34eabd8a01169f500ba2c62ad3ec1bd601ba85e88`

packed bitmap=
`4P-_7xsAAIADAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEA`；
最后一份 focused Surefire XML SHA-256=
`58b8cdddc0f3f1f739bd694b997874984c381a95e03496adaf1798659c8085fa`。

旧 formal-r3/r16 artifact 在 machine 恢复 diagnostic state 且 HEAD/工作树已变化后，被 public
negative replay 分别以 `E_RUN_CONTEXT` / `E_MANIFEST_PROVENANCE` 拒绝；这两个预期的跨状态拒绝不计入
上述 positive negative-suite 数量，也不会被拼接为当前证据。新的 all-lane authority 只能来自
clean/pushed Cdiag 上的 fresh diagnostic。

## Findings

### Blocker / High

无。

### Medium

首轮独立审查发现一项 documentation Medium：failure evidence 与 BUG 曾把 method 的 `4` 个 branch
outcome 误写成 source line 316 自身的 `4` 个 outcome，并沿用已撤销的保护树宿主描述。现已按原始
JaCoCo XML 修正为 method=`0/4 -> 1/3`、line 316=`0/2 -> 1/1`（missed/covered），并统一为最终
Runtime API 既有 testcase、实际 first-caller build 窗口和 exact monitor 交错。独立复核转为 PASS。

### Low

无。

最终合并 B/H/M/L=`0/0/0/0`，open mandatory fixes=`0`。

## Documentation and State Review

README、roadmap、requirement、contract、module responsibility、implementation plan、progress、test plan、
acceptance evidence plan、code inventory、BUG 与 formal-r3 failure evidence 已统一为：Steps 1–3 passed；
Step 4 formal-r3 immutable fail-closed；deterministic remediation targeted/5-fork/module verified；machine=
`diagnostic-ready/diagnostic-pending`；new Cdiag pending。Step 5、coverage audit、acceptance 与 9.3.5
保持关闭。

## Decision

当前 delta 可一次提交并 push 为新的 Cdiag。提交后必须证明 clean `HEAD == origin/main` 与 source
identity，再按 exact-ID outer wrapper 暂停四个 demo DB container，运行唯一 fresh diagnostic，并在
runner 退出后恢复原 IDs。该 gate 不放行 formal、coverage audit、acceptance、Step 5 或 9.3.5；
fresh diagnostic 失败时继续 fail closed。
