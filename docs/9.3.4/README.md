---
doc_role: version_execution_index
doc_purpose: Index the executable 9.3.4 test and CI evidence-chain plan.
version: 9.3.4
status: in-progress
predecessor: 9.3.3 signed-off / accepted-with-risks
created_at: 2026-07-14
updated_at: 2026-07-16
---

# 9.3.4 测试与 CI 证据链

## 文档作用

- doc_type: requirement+contract+plan+progress+acceptance-evidence-index
- intended_for: project-root-session / build owner / CI owner / reviewer
- purpose: 把 Failsafe 分层、五数据库矩阵、聚合覆盖率、required CI 与
  immutable release evidence 拆成严格 1~7 的可执行版本计划。

## Entry Decision

- 9.3.3 已 `signed-off / accepted-with-risks`；replacement authority
  `20260714T084351Z-3271604`=`3824 tests / 519 reports / F0/E0/S3`，满足本版本
  predecessor。
- 9.3.4-A 最小门继续由
  `docs/9.3.3/preconditions/9.3.4-A-minimum-test-gate.md` 提供历史 authority；
  本目录只引用，不复制一份可漂移状态。
- Step 1 已以 `step1-candidate-r8-20260714` 正式确认；两路独立复核均为 PASS，
  blocker=0。最终 inventory validator、confirmed-summary validator 与 16/16 hash
  manifest 复验通过。
- Step 2 已由 signal-safe r8e successor、Surefire Unit 与 Failsafe Integration
  三条独立权威链通过：`724 positive / 59 structural / 5,205 testcase / F0/E0/S0`；
  `46` 个 external execution 保持 exact deferred-to-Step-3。
- r8d successor 及其 Unit/Integration 因 `completed` 窗口 signal fail-open 被明确
  作废，禁止用于 coverage/acceptance；r8e 的 INT/TERM/HUP 动态探针分别以
  `130/143/129` fail closed。
- Step 3 已由同一 committed HEAD 的正式父运行
  `step3-required-20260716-final-r4` 通过：database `29/370` + required external
  `16/76` = exact `45/446/F0E0S0`，gap/overlap/extra=`0/0/0`；DB state=`18/18`、
  Redis state=`4/4`、Addon companion=`2/6`。quality→coverage→feature acceptance
  已按序完成；Step 4 仍为 `in-progress`，diagnostic r4 已 fail closed，尚无 coverage
  pass 或 aggregate baseline/review。
- Step 4 r4 后 source-policy remediation 已静态通过，两层独立库存保持：coverage execution=
  `23 exec / 48 sessions`；
  required report overlay=`773 positive + 59 structural / 5,707 testcase`，Addon companion=
  `2/6`；required 总计预期 `F0E0S0`。coverage contract mutations=`20/20`、
  source identity=`22/22`、effective POM=`4/4`、toolchain receipt=`5/5`、
  report inventory=`27/27`；Step 2 derived view/successor overlay=`12/12`/`12/12`，
  XML/provenance=`63/63`，logger lifecycle=`9 类 / 14 case`。
- toolchain receipt 绑定 Step 1 raw 工具版本，并实测约束 compiler realm ASM
  `9.6`、JaCoCo realm ASM `9.7`、test classpath ASM `9.7.1` 以及 24 个
  production module 的 effective compiler。report amendment 现为 exact
  `11 rows = 4 new + 7 changed`，SHA-256=
  `937666fc1926ec1c4764ebb50d4b4d4bdd1f1013f0d63cc77d9a1856fae153d2`；successor
  declared amendments=`17`，SHA-256=
  `1e4f15c9e403d454fe07404e45b1226eae94f70faa154433a8db39531a305b47`。本地 Step 4
  `SHA256SUMS` 已通过 exact `54/54`，manifest SHA-256=
  `ebda814b1278f92cf1ba7dc202170e4a77cb7e1f4485e6cb1375d152592a76d0`；
  successor manifest=`12/12`，SHA-256=
  `751018ac7c2357cface77dd125c5edc757ad488a500a3c8d9eece0354767381a`；coverage
  contract diagnostic/formal SHA-256=
  `5f4b49fd161b4f381a4f8c2238583eb56f27b577973ff93ce0659d84cca75f1d` /
  `58c3479666d0b786ea0ad8327b72b05c9e006dfdb516eacce9098ea83ef4c405`；coverage tool /
  contract-negative / XML tool SHA-256=
  `07a36a2be8edc0afc0ab1031b052c2208a4e32769c4cdb475a397f81e6121ac9` /
  `732d799619461a4b49c8e9bfbb0a3487b107c36110b9e55cd91a405352d0ddb0` /
  `b837314ac4166eeeab94124b53e4f776dcdf8095a3b3915e14e45b81d910d439`；overlay contract /
  overlay tool / outer SHA-256=
  `2d4fe0024caac33199e2ccf87289dd9a262302d3faabad6b038adadb2b2974cb` /
  `a16aadf9c4d540cda8b95d1fc1ded94cf420aa0cfe5a1653b8f90d4cb72e0f51` /
  `254c7603554787ca38d880ac607f7dd4a21ae89064674490858245f0824951c9`。
  新增回归证明 tracked FIFO 会在 worktree-aware Git 读取前 fail-fast，且 before/after raw
  stat identity 比较会拒绝内容可被 Git clean 等价化、但运行中发生的并发重写。
  source seal 清除 ambient/global Git clean 配置并显式复算 raw 与 CRLF-input 两个
  candidate，使真实 CRLF worktree 在 HEAD/index clean-equivalent 时通过；HEAD-fixed
  attributes 若声明 external clean filter，则在任何 worktree-aware Git hash/driver hook
  执行前 fail closed，negative 证明 hook 未执行。
- 静态复核还发现并修复 root coverage `argLine` 早解析 fail-open：legacy
  profile 现在实际生成/读取 exec，低于既有门时正确失败；生产代码不变，
  canonical `@{argLine}` 已由 versioned contract guard 与 `8/8` negatives 自动保护。详见
  [BUG-step4-legacy-coverage-argline-fail-open](workitems/BUG-step4-legacy-coverage-argline-fail-open.md)。
- clean/pushed HEAD `bc100b0f63bd3ff62d1105611dae41741790aedd` 的
  `step4-coverage-20260716-diagnostic-r1` 在 `child-unit` 以
  `3115 tests / 1 failure / 0 errors / 0 skipped` fail closed。根因是腐化 daily 表但
  查询实际命中 monthly，同时暴露 raw-vs-raw 与 nullable/empty 伪绿。修复后的 focused
  整类=`9/F0E0S0`，四类组合=`57/F0E0S0`；源码 SHA-256=
  `affb6e415c1770eae7c9ab6f3505ac67acfe03eac1461bd2a48addf3ee790623`。详见
  [BUG-step4-preagg-validation-wrong-table](workitems/BUG-step4-preagg-validation-wrong-table.md)。
- clean/pushed HEAD `0101a44a07784bf6b484d490c7fb508727fbab70` 的
  `step4-coverage-20260716-diagnostic-r2` 已完整通过 Unit
  `681 execution + 55 structural / 4,941 testcase / F0E0S0`；Integration 执行
  caffeine=`2/F0E0S0`、hermetic=`3/F0E0S0`、sqlite-broad=`307/F1E0S0`，合计
  `312/F1E0S0`。唯一失败为 `PreAggregationL2CacheIT` 未显式声明 snapshot-only，outer
  在 `child-integration` fail closed，summary absent，后续 lane/aggregate/threshold 均未执行。
  failed diagnostic 见
  [step4-coverage-diagnostic-r2-fail-closed-20260716.md](evidence/step-4/step4-coverage-diagnostic-r2-fail-closed-20260716.md)。
- L2 fixture 已显式关闭 hybrid 并收紧 exact name/table/raw negative，最终源码 SHA-256=
  `bb5d6884401579447382587861e197feac2884ab701065d7826fe7a676e0c313`；focused=
  `1/F0E0S0`、与 `PreAggregationIT` 组合=`30/F0E0S0`。主动 hybrid 扫描还发现 Pivot
  legacy fallback 两次稳定 RED；仅 legacy 分支关闭 hybrid 后，legacy/V934 SQLite
  focused 各 `1/F0E0S0`，源码 SHA-256=
  `5c6dcd3b4afba4d93a93c1af47cc4484a1dfc9976da92669bfbcc4529ede6155`。生产
  Matcher、hybrid 默认、threshold/exclusion 均未修改。
- clean/pushed HEAD `e16693297239f2a861f3b93b3de60c1bb783bda0` 的 r3 完整通过 Unit
  `681 execution + 55 structural / 4,941 testcase / F0E0S0`，随后因 Unit runner 未
  close/wait 异步日志 `tee`，outer 在 `child-unit` 报 live process-group residue 并
  fail closed。r3 summary/observation absent，后续所有 lane/gate 均未执行；见
  [r3 failed evidence](evidence/step-4/step4-coverage-diagnostic-r3-fail-closed-20260716.md)
  与 [logger race BUG](workitems/BUG-step4-child-run-log-tee-residue-race.md)。
- r4 的 reported launch head 为 `ceea084ca25a9d679ba128e3f6bd50a63322c112`；outer 在
  `source-before` 以 exit code `2` fail closed，run-owned Git/source seal、全部测试 lane、
  aggregate 与 summary 均 absent，decision=`excluded-from-step4-exit`。根因是
  `core.fileMode=false` checkout 被错误要求 worktree executable bit 与 Git mode 完全一致；
  详见 [r4 failed evidence](evidence/step-4/step4-coverage-diagnostic-r4-fail-closed-20260716.md)
  与 [source inventory BUG](workitems/BUG-step4-source-inventory-filemode-false.md)。
- 当前状态为 `in-progress / r4 historical fail-closed / source-policy remediation statically
  passed / final review passed B/H/M/L=0/0/0/2 / amend/push + fresh r5 pending`。两项 Low
  均 accepted：`/usr/bin/echo` 平台前提漂移会 fail closed；同 UID 视为 build authority，
  未来更强隔离改用 readonly snapshot/独立 checkout。`coverage-thresholds.json` 仍为
  `diagnostic-pending`；r1–r4 与 focused/static 结果都不是 Step 4 exit evidence。Step 5 仍关闭，
  `can_enter_coverage_audit=no`。

## 执行资料

- requirement: [requirement/P0-test-ci-evidence-chain.md](requirement/P0-test-ci-evidence-chain.md)
- test/evidence contract: [contract/test-lane-evidence-contract.md](contract/test-lane-evidence-contract.md)
- module responsibility: [module-responsibility.md](module-responsibility.md)
- code inventory: [code-inventory.md](code-inventory.md)
- implementation plan: [implementation-plan.md](implementation-plan.md)
- project execution prompt: [execution-prompt.md](execution-prompt.md)
- progress: [progress/test-ci-evidence-chain-progress.md](progress/test-ci-evidence-chain-progress.md)
- test plan: [test/test-ci-evidence-chain-test-plan.md](test/test-ci-evidence-chain-test-plan.md)
- acceptance evidence plan: [acceptance-evidence-plan.md](acceptance-evidence-plan.md)
- Step 4 diagnostic-ready quality gate:
  [quality/step4-diagnostic-ready-implementation-quality.md](quality/step4-diagnostic-ready-implementation-quality.md)
- Step 4 diagnostic r1 PreAgg validation regression：
  [workitems/BUG-step4-preagg-validation-wrong-table.md](workitems/BUG-step4-preagg-validation-wrong-table.md)
- Step 4 diagnostic r2 failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r2-fail-closed-20260716.md](evidence/step-4/step4-coverage-diagnostic-r2-fail-closed-20260716.md)
- Step 4 diagnostic r2 PreAgg L2 fixture regression：
  [workitems/BUG-step4-preagg-l2-hybrid-fixture-drift.md](workitems/BUG-step4-preagg-l2-hybrid-fixture-drift.md)
- Step 4 proactive Pivot legacy fixture regression：
  [workitems/BUG-step4-pivot-legacy-hybrid-fixture-drift.md](workitems/BUG-step4-pivot-legacy-hybrid-fixture-drift.md)
- Step 4 diagnostic r3 failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r3-fail-closed-20260716.md](evidence/step-4/step4-coverage-diagnostic-r3-fail-closed-20260716.md)
- Step 4 child logger lifecycle regression：
  [workitems/BUG-step4-child-run-log-tee-residue-race.md](workitems/BUG-step4-child-run-log-tee-residue-race.md)
- Step 4 Git environment isolation regression：
  [workitems/BUG-step4-git-environment-override-bypass.md](workitems/BUG-step4-git-environment-override-bypass.md)
- Step 4 source/context/raw-exec provenance regression：
  [workitems/BUG-step4-source-context-crosslink-gap.md](workitems/BUG-step4-source-context-crosslink-gap.md)
- Step 4 diagnostic r4 failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r4-fail-closed-20260716.md](evidence/step-4/step4-coverage-diagnostic-r4-fail-closed-20260716.md)
- Step 4 source inventory file-mode regression：
  [workitems/BUG-step4-source-inventory-filemode-false.md](workitems/BUG-step4-source-inventory-filemode-false.md)
- Step 1 confirmed evidence:
  [evidence/step-1/inventory-contract-freeze-20260714.md](evidence/step-1/inventory-contract-freeze-20260714.md)
- Step 2 structural amendment evidence：
  [evidence/step-2/step2-structural-container-contract-amendment-20260714.md](evidence/step-2/step2-structural-container-contract-amendment-20260714.md)
- Step 2 successor independent review：
  [evidence/step-2/step2-successor-r8e-independent-review-20260715.md](evidence/step-2/step2-successor-r8e-independent-review-20260715.md)
- Step 2 runner exit：
  [evidence/step-2/step2-runner-split-exit-r8e-20260715.md](evidence/step-2/step2-runner-split-exit-r8e-20260715.md)
- Step 2 implementation quality：
  [quality/step2-runner-split-implementation-quality.md](quality/step2-runner-split-implementation-quality.md)
- Step 2 coverage audit：
  [coverage/step2-runner-split-coverage-audit.md](coverage/step2-runner-split-coverage-audit.md)
- Step 3 five-DB diagnostic foundation（not authority）：
  [evidence/step-3/step3-five-db-foundation-20260715.md](evidence/step-3/step3-five-db-foundation-20260715.md)
- Step 3 database matrix runner/collector candidate（run-local / not authority）：
  [evidence/step-3/step3-database-matrix-runner-candidate-20260715.md](evidence/step-3/step3-database-matrix-runner-candidate-20260715.md)
- Step 3 committed Redis external subset candidate（run-local / not full authority）：
  [evidence/step-3/step3-external-redis-runner-candidate-20260715.md](evidence/step-3/step3-external-redis-runner-candidate-20260715.md)
- Step 3 committed Mongo/DataViewer external subset candidate（run-local / not full authority）：
  [evidence/step-3/step3-external-mongo-runner-candidate-20260715.md](evidence/step-3/step3-external-mongo-runner-candidate-20260715.md)
- Step 3 committed MySQL57 external subset candidate（run-local / not full authority）：
  [evidence/step-3/step3-external-mysql-runner-candidate-20260715.md](evidence/step-3/step3-external-mysql-runner-candidate-20260715.md)
- Step 3 committed Vector external subset candidate（run-local / not full authority）：
  [evidence/step-3/step3-external-vector-runner-candidate-20260715.md](evidence/step-3/step3-external-vector-runner-candidate-20260715.md)
- Step 3 shared external matrix candidate（single outer / complete external subset）：
  [evidence/step-3/step3-shared-external-matrix-candidate-20260716.md](evidence/step-3/step3-shared-external-matrix-candidate-20260716.md)
- Step 3 formal required-matrix exit：
  [evidence/step-3/step3-required-matrix-exit-20260716.md](evidence/step-3/step3-required-matrix-exit-20260716.md)
- Step 4 MultiThread prerequisite companion evidence：
  [evidence/step-3/step3-multithread-prerequisite-evidence-20260716.md](evidence/step-3/step3-multithread-prerequisite-evidence-20260716.md)
- Step 3 implementation quality：
  [quality/step3-required-matrix-implementation-quality.md](quality/step3-required-matrix-implementation-quality.md)
- Step 3 test evidence coverage audit：
  [coverage/step3-required-matrix-coverage-audit.md](coverage/step3-required-matrix-coverage-audit.md)
- Step 3 feature acceptance：
  [acceptance/step3-required-matrix-acceptance.md](acceptance/step3-required-matrix-acceptance.md)

## 1~7 顺序

| Step | 内容 | 当前状态 | Exit |
|---:|---|---|---|
| 1 | 契约与静态库存冻结 | passed | r8 confirmed；532 sources / 820 discovery / 829 execution / 519 predecessor；28/28 negatives |
| 2 | Surefire/Failsafe 全量分层 | passed | r8e confirmed；724 positive + 59 structural；5,205 testcase；F0/E0/S0；signal-safe status |
| 3 | 五数据库与外部集成 required matrix | passed | r4 same-commit exact `45/446/F0E0S0`；DB state `18/18`、Redis state `4/4`；Addon companion `2/6`；feature accepted |
| 4 | JaCoCo unit+IT 聚合与关键类门 | in-progress / r4 historical fail-closed / source-policy remediation statically passed | 静态契约 exact 23/48 + 773/59/5707；contract/source/XML/overlay=`20/22/63/12`；top 54/54；final review B/H/M/L=`0/0/0/2`、two Low accepted；amend/push + fresh r5 pending；`can_enter_coverage_audit=no` |
| 5 | 单一 authority runner 与 immutable evidence rehearsal | pending | dirty-safe candidate 可独立复算，但不更新 final authority pointer |
| 6 | PR/main/release CI 接线 | pending | exact five-cell artifacts、stable aggregator、JAR/镜像同一制品 |
| 7 | clean-commit 权威回放与后置门 | pending | self-check→quality→coverage→version acceptance signed-off |

任一步未满足 exit，不进入下一步；expected-negative、diagnostic 和 superseded
run 不得拼接为绿色 authority。

9.3.1–9.3.3 historical run/FQCN/count 保持封存；重命名后的 9.3.4 通过 reviewed
predecessor migration manifest 和 successor current-source lanes 证明等价回归，
不要求不可变的 v933 Batch 7 runner 在新命名上原样通过。

## 范围边界

- 本版本允许创建 build-only coverage report module；它不含生产类、不进入
  Launcher，不是 9.4.0 的生产模块拆分。
- 不实施 9.3.5 的 typed execution phase、统一 QueryFacade、公共 DTO/大类拆解。
- 不实施 9.4.0 的 `model-api/core/jdbc/starter/web`、BackendProvider、SPI v2
  或 Addon TCK。
- 测试治理发现生产 BUG 时先建 9.3.4 workitem，做最小 RED→fix→GREEN；若需
  公共 API/模块边界变更则停止并转交 9.3.5/9.4.0。
