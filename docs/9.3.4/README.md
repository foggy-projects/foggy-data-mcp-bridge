---
doc_role: version_execution_index
doc_purpose: Index the executable 9.3.4 test and CI evidence-chain plan.
version: 9.3.4
status: in-progress
predecessor: 9.3.3 signed-off / accepted-with-risks
created_at: 2026-07-14
updated_at: 2026-07-19
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
  已按序完成。diagnostic r7 在无 ambient DB listener 的环境中
  暴露 Unit 对 `127.0.0.1:13306` 的隐式依赖并 fail closed；该 r7 时点尚无 coverage pass
  或可复用的 reviewed aggregate baseline。
- Step 4 historical formal-r4、final quality、coverage audit 与 feature acceptance 已按序关闭；
  decision=`accepted`，25/25 workitem closed。后续 release-authority delta 触发 replacement
  coverage authority；formal-r7 CALCULATE portability remediation 后的 fresh r25 虽 public-valid，
  但 follow-up audit 证明其 schema 1 把真实 known-consumer lower bound `7 reports / 12 nodes`
  低记为 historical `6/11`，所以 r25 永久是
  `pre-remediation / superseded / non-candidate`。修复后的 Cdiag `4fe86929…`、fresh-clone isolated
  r4、fresh diagnostic-r26 与 direct-child Cfreeze `7c18019e…` 已通过。fresh formal-r8 完成全部
  child/report inventory 后在 coverage reporter 直接执行 Git `100644` Python 工具时 rc126；r8 永久
  `failed/excluded/non-reusable/non-candidate`。三处 interpreter 修复、runner raw/逻辑命令流双封印、
  四工具/七调用的 semantic/Git-mode negative gate 与
  Step4→Step6 hash closure 已完成，machine=`diagnostic-ready/diagnostic-pending`。current Step 4=
  `in-progress / formal-r8 recovery / ready-for-new-Cdiag`；Step 5=`hold / closed`。
- Step 4 r5 已在 clean/pushed commit 上建立 run-owned source seal，但 database-state
  companion 选择 frozen Step 3 authority manifest 而 fail closed；该轮 Unit、Integration 和
  Addon 子结果只属于 r5 失败运行，不得拼接或复用。successor runtime-binding
  remediation 已通过正式质量闸门，两层独立库存保持：coverage execution=
  `23 exec / 48 sessions`；
  required report overlay=`773 positive + 59 structural / 5,707 testcase`，Addon companion=
  `2/6`；required 总计预期 `F0E0S0`。coverage contract mutations=`21/21`、
  source identity=`22/22`、effective POM=`4/4`、toolchain receipt=`5/5`、
  report inventory=`30/30`。Unit fixture negatives=`36/36`，其中原
  fixture/manifest schema/tamper=`20/20`、connection typed=`7/7`、atomic
  publisher=`3/3`、profile boundary=`6/6`；negative receipt 文件 schema/tamper 另为
  `4/4`，真实 fixture lifecycle=`5/5`。restricted credential receipt 的 closed Unit Maven
  observation window 由 `configure init_connect -> Maven -> 同一 root batch 先 disable 再
  SELECT` 界定，按 `connection_id` 保存有序 observed user；该窗口内所有 non-super
  connections 必须使用 run-owned `v934_unit`，callback 返回后的 provisioner `foggy`
  控制面连接在窗口外。Step 2 derived
  view/successor overlay=`12/12`/`12/12`，XML/provenance=
  `68/68`，logger lifecycle=`9 类 / 14 case`。
- r8 lifecycle closure 时点的 toolchain receipt 绑定 Step 1 raw 工具版本，并实测约束 compiler realm ASM
  `9.6`、JaCoCo realm ASM `9.7`、test classpath ASM `9.7.1` 以及 24 个
  production module 的 effective compiler。run-owned Unit fixture hardening 与 lifecycle
  remediation 的该历史静态 identity 为：top manifest=`60/60` /
  `0c4e6c18f4af0a2c35418604ce80d20828ceb4c275375b7d12461b4683553a81`，successor=
  `14/14` / `acb580e92a72eb407f31f5d6f9a8139a3509f3a0bfbf58537922465f4086a112`，
  amendment=`12 rows / 4 new + 8 changed` /
  `998ae49927721576c26327b8477010b0238843565e6afdbc70987e97544a028c`；fixture
  contract/tool/Unit runner/datasource adapter=
  `7aa1e21aef85b51a13aacc8c134a1c363c595deffbfb3acf6aafdb942519b53a` /
  `cc19390ce6c0cfb307b7632dbe4e25540b1e4d49d11ec1512739f6724646d345` /
  `45536c0a969731f6b7c87acecdb225b13a8a0fca45a9a04c9cdfb2173fc60c66` /
  `9500cd4d50930b121a36798857cd0a1cc0c8b2190b0a2fc9ad0ea464394bb256`；
  coverage diagnostic/formal=
  `c062219a6335ae41330c6d5924d6fce60941c5d168b361081fbb41df77428477` /
  `341991d6b5a15d19cdb9e0de70a8cc6ace29480227596c413c07e6bf7fdbc73d`；
  declared amendments=`18` /
  `8e21b8527f290061361ef0b8fbf084d51b2536ef479e1f70e45488f996090bfc`；
  overlay contract/tool=
  `84d09bfc333bb40d8ef830979734933717555845cebe9943f70ff7087a9a482d` /
  `1fea2816504519b7e7f1dc6839744ee943a9a4bf3feb783375e21e935da63d31`。
  datasource adapter inventory 由 scrubbed Git environment 枚举 `HEAD` tree，并以
  `GIT_NO_REPLACE_OBJECTS=1` 禁用 replace object；ambient guard 同时覆盖 underscore、
  dotted、hyphen 的 Spring/custom key，以及 `@argfile`、`VMOptionsFile`、`javaagent`、
  `agentlib`、`agentpath` 间接注入。
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
- r5 tested commit=`a35b99cb08f42817d8e75c440f18910b6961841b`，run=
  `step4-coverage-20260716-diagnostic-r5`；Unit=`681+55 / 4,941 / F0E0S0`、
  Integration=`47+4 / 320 / F0E0S0`、Addon=`2/6/F0E0S0` 后，database-state 以
  `E_AUTHORITY_MANIFEST` 拒绝 stale `foggy-dataset-model/pom.xml`。database cells、
  external、aggregate、threshold 与 summary 均未完成，r5=
  `excluded-from-step4-exit`，partial lanes 不可复用。
- r6 tested commit=`eb10d9c10a73f379db9ce4fa3d05ff340b489fd4`，source-before=
  `3,974 files` / SHA-256=
  `3a4322e8442646c58ed522c0d4fb52071b3219cc1c2f204c209299bd8acc1cff`；Unit=
  `681+55 / 4,941 / F0E0S0`、Integration=`47+4 / 320 / F0E0S0`、Addon=
  `2/6/F0E0S0` 后，database-state 以 `E_DYNAMIC_PRECONDITION` 拒绝已被长期 repo demo
  container `foggy-demo-mysql` 占用的 frozen port `13306`。database cells、external、
  aggregate、threshold、source-after 与 summary 均 absent；r6=
  `excluded-from-step4-exit`，partial lanes 不可复用。这是环境阻塞，不是产品回归。
- r7 tested commit=`528a0a541d90ef77d577e1816b392d33168cb558`，source-before=
  `3,976 files` / SHA-256=
  `b3fc04ee0d16a7a81f5e9697b10b5edeaafec0f59cd5dbec1e65625381c3fe43`；在四个 frozen
  ports 均无 listener 时，Unit 暴露 6 suites / 11 errors，证明此前绿色隐式依赖 ambient
  MySQL/schema。outer=`child-unit / exit 1`，后续 lane、aggregate、threshold、source-after
  与 summary 全 absent；r7=`excluded-from-step4-exit`。remediation 以 run-owned pinned
  MySQL 5.7 替换完整 Unit lane，保持唯一 Maven invocation 与
  `681+55 / 4,941 / F0E0S0`；已知隐藏依赖清单仅为 `6 reports / 11 testcase nodes`，不是
  其余 Unit 测试无数据库访问的证明。Step 2 identity/cardinality 继续保留结构意义，其 Unit
  正确性绿色不复用；机器例外契约见
  `scripts/v934/step4/unit-mysql57-fixture-contract.json`，迁移债务见
  [DEBT-unit-mysql57-fixture-classification-migration](workitems/DEBT-unit-mysql57-fixture-classification-migration.md)。
- Unit fixture quality r2=`step4-unit-fixture-quality-20260716-r2` 在 commit
  `a603f839a98d99b2d7beb8379f76b4d85539328c`、`3,981 files`、source SHA-256=
  `087d074f3497aff3fe305806f82b4d62ff41cdd4d3b26556e58f034138b14c2c`
  上先通过真实 lifecycle `5/5`，随后完整 Maven invocation 因全局
  `spring.datasource.*` 覆盖 `foggy-dataset-model` 的 SQLite profile 而 fail closed：
  `3,115 tests / 631 errors`。直接根因为 `org.sqlite.JDBC` 被配置为连接
  `jdbc:mysql://127.0.0.1:13306/...`；r2 excluded/non-reusable。修复已将
  `V934_UNIT_MYSQL57_URL/USERNAME/PASSWORD` 三个 placeholder 仅放入
  `foggy-dataset` test resource，callback 不再传全局 Spring datasource/test 参数，其他
  profile 保持原配置。
- fresh Unit fixture quality r3=`step4-unit-fixture-quality-20260716-r3` 已在 commit
  `50161a0a869430e353f3933d9bb00dda59d9c4b1`、`3,982 files`、source before=after
  `1db06cc18bb86c288ffa79e7094e3b9e509d866ddc23b6c93bcaaa0422b99eb2`
  上通过：唯一 Surefire invocation=`681 positive + 55 structural = 736 raw reports /
  4,941 testcase / F0E0S0`；fixture negatives=`36/36`、receipt schema/tamper=`4/4`，
  closed receipt 内 `18/18` connections 全部为 `v934_unit`；真实 lifecycle=`5/5`，
  run-owned container/volume/network cleanup=`0/0/0` 且 port free。evidence window 外已按
  exact ID `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166`
  恢复 demo MySQL 为 `running/healthy`。record=
  [step4-unit-fixture-quality-r3-pass-20260717](evidence/step-4/step4-unit-fixture-quality-r3-pass-20260717.md)。
- fresh all-lane r8=`step4-coverage-20260716-diagnostic-r8` 已从 clean/pushed
  `3a3dd21a9aa956f0bedfffcf648ebb1cac0b756a` 启动，但在建立 run-owned Git/source seal
  前因 lifecycle static contract 仍要求旧 Unit direct EXIT token，于 `bootstrap-negative`
  fail closed；所有 lane、aggregate、threshold 与 summary absent，r8=
  `excluded/non-reusable`。fixture-aware Unit wrapper 正确，缺陷是测试契约漂移；record=
  [step4-coverage-diagnostic-r8-bootstrap-negative-contract-drift-fail-closed-20260717](evidence/step-4/step4-coverage-diagnostic-r8-bootstrap-negative-contract-drift-fail-closed-20260717.md)，BUG=
  [BUG-step4-unit-lifecycle-static-contract-drift](workitems/BUG-step4-unit-lifecycle-static-contract-drift.md)。
- lifecycle remediation 已通过：动态 `9 类 / 14 case`，Unit shape/source-seal=
  `13/13 + 3/3`，Integration=`11/11 + 5/5`；runner 使用 raw-byte SHA-256 seal，拒绝
  comment/quoted heredoc、EXIT/0、early return、shadow、false/subshell、heredoc source/eval
  与 CRLF drift。两路独立质量复核 B/H/M/L=`0/0/0/0`；tool=
  `8dcc679c2762ff8908b3bc26e8dfb0553a083eb75003dd80366fd82e78d8ed9b`，top=
  `60/60` / `0c4e6c18f4af0a2c35418604ce80d20828ceb4c275375b7d12461b4683553a81`。
- fresh all-lane r9=`step4-coverage-20260717-diagnostic-r9` 已从 clean/pushed commit
  `a0466ec04c51c436413e85836a7dee6153e18010` 启动并完成 Unit、Integration、Step 3
  required、report inventory=`773+59/5,707/F0E0S0` 与 `23 exec / 48 sessions`；随后
  exec verifier 把所有测试/依赖/CGLIB 同名多 ID 当成 production drift，在
  `coverage-report` 以 `E_CLASS_ID_MISMATCH` fail closed。`exec-manifest`、aggregate、
  source-after、threshold 与 summary absent，r9=`excluded/non-reusable`。只读复算为
  `16,939` 个 JaCoCo class-ID identities、135 个同名多 ID、frozen production 冲突=0；
  record/BUG=
  [r9 exec class scope failure](evidence/step-4/step4-coverage-diagnostic-r9-exec-class-scope-fail-closed-20260717.md) /
  [BUG-step4-exec-class-id-scope-drift](workitems/BUG-step4-exec-class-id-scope-drift.md)。
- r9 remediation 将 production class-ID consistency 冻结到 24-module production class
  universe，raw/aggregate 保留所有 JaCoCo ID，aggregate 按 ID 做 exact bitmap union；focused
  exec=`17/17`、contract mutations=`21/21`、XML identity/provenance=`68/68`、
  overlay=`12/12`。同轮质量复核又确认 lifecycle
  shape validator 的 comment/dead-context 与 dynamic `trap` 拼接绕过；已登记
  [BUG-step4-lifecycle-semantic-validator-bypass](workitems/BUG-step4-lifecycle-semantic-validator-bypass.md)，
  原 lifecycle quality 结论被重新打开。coded executable-stream 修复现已通过完整 regression：
  Unit/Integration shape=`16/16 + 14/14`、semantic stream=`2/2 + 5/5`、raw seal=`2/2`、
  outer/library=`3/3 + 3/3`；三路独立正式质量最终 B/H/M/L=`0/0/0/0`。
- fresh all-lane r10=`step4-coverage-20260717-diagnostic-r10` 已从 clean/pushed commit
  `47e0c027cd205a49d40db400ba26b99e6f97d60e` 完成 required=
  `773+59/5,707/F0E0S0`、Addon=`2/6`、`23 exec / 48 sessions / 16,947 class IDs`、
  aggregate/observation/source-after/cleanup；最终因 demo identity producer 复用
  credential-shaped authorization label，在 `sensitive-scan` fail closed。sensitive/summary
  receipt absent，r10=`excluded/non-reusable`；line=`54,478/76,830`、branch=
  `25,980/44,870` 只是 failed-run partial observation，不得冻结 threshold。record/BUG=
  [r10 sensitive-scan failure](evidence/step-4/step4-coverage-diagnostic-r10-sensitive-scan-fail-closed-20260717.md) /
  [BUG-step4-sensitive-scan-authorization-context-false-positive](workitems/BUG-step4-sensitive-scan-authorization-context-false-positive.md)。
- remediation 保持五条 sensitive patterns 原样不变，仅把 producer 改成明确的 demo identity
  result wording；outer bootstrap-negative 以同一数组执行内存 `7 dangerous + 3 safe` probe，
  `rg rc>1` 双路径 fail closed，fixture 不落 run root。launcher request smoke、manifest、
  coverage contract 与 successor overlay focused validation 已通过。
- fresh all-lane r11=`step4-coverage-20260717-diagnostic-r11` 已从 clean/pushed commit
  `141592ca9f4219d87a018774ee607b09a8e5a8a1` 启动；full coverage contract、successor
  overlay、sensitive bootstrap probe、authority 与 coverage contract negatives 通过后，
  lifecycle suite 在 `bootstrap-negative` 以 stable `E_SOURCE_SEAL` 拒绝 outer actual raw
  `57f5da9a23c4973beef54a6bfd303c3dfd38fccb03a7bc2cadadbfaa3206f649` 与内嵌旧值
  `02a920d91d1b8792cad47d65ce860352a8e9ecf39106f4489a714df01888dbaa` 的漂移。
  r11 在 run-owned Git/source seal 前退出，零测试 lane、exec、aggregate、observation 与
  summary，结论=`failed / excluded / non-reusable`。
- r11 remediation 现以独立 frozen constants 建立 Unit、Integration、outer、lifecycle library
  的 early four-way raw binding；canonical positive=`1`、partial-update negatives=`6` 均使用
  stable `E_SOURCE_SEAL_BINDING`。六类 negative 为 outer+manifest 刷新但 nested stale、
  outer-only drift、valid-64 nested-only wrong、missing、duplicate 与 invalid-format constant。
  outer 另由 raw CRLF negative 与 executable no-op
  semantic negative 双层保护。focused lifecycle suite=`PASS`，top manifest=`60/60`、
  successor manifest=`14/14`、coverage contract=`21/21`、overlay=`12/12`；当前 outer raw=
  `90b4b979e55c17243644cce186767a4647ce79c85b431adcb415bddd18cc1cec`、executable stream=
  `065211912aab5227125ef02f40e2965fce7ff5060df5c7b91a902c4ad4f34cae`、lifecycle tool=
  `61bf7b990bdef6e0d75c53010644bcc6d1525a67119cd36c5f82eeb911e005fc`、top manifest=
  `a9b105ce2f8f640dfa09863e797697bcf9892a7b0fa68b38f83b5bbd7435afb4`。安全复核发现的
  preflight TOCTOU Medium 已由 descriptor-bound strict read 关闭；两路 post-fix implementation
  review 与独立 docs/status review 最终 B/H/M/L=`0/0/0/0`。
- fresh r12=`step4-coverage-20260717-diagnostic-r12` 已在 clean/pushed commit
  `05351ecab0d7fc43d12dfa307ffecf81feb41539` 完整通过 all-lane：required=
  `773+59/5,707/F0E0S0`、Addon=`2/6`、exec=`23/48/16,935`，aggregate line=
  `54,478/76,830`、branch=`25,980/44,870`，sensitive scan 与 cleanup=`0/0/0` 均通过。
  该有效 diagnostic 同时发现 9/12 critical classes 低于 floor 与唯一
  `NamespaceScope.branch` structural N/A；旧 freeze consumer 无法消费真实 enriched shape，
  r12 因而只作 immutable diagnostic，不得直接创建 Cfreeze。
- remediation 不改生产逻辑、floor、critical set 或 exclusion：八份既有测试在不增加 report/
  testcase 节点下覆盖九个 gap class；focused=`136/F0E0S0`。threshold lifecycle 现 exact
  消费真实六字段 row/enriched counter，N/A 只接受唯一 machine tuple，并以 strict JSON
  identity 拒绝 bool/int、int/float 与 `gap:false` aliases；XML=`118/118`、contract=
  `27/27`、frozen replay policy=`12/12`、overlay=`12/12`。
- fresh r13=`step4-coverage-20260717-diagnostic-r13` 已在 clean/pushed Cdiag commit
  `b76552e21479c75111f648a4aa678abe018cc3f9` 完整封存：outer=
  `diagnostic-observed / completed / exit 0`，required=`773+59/5,707/F0E0S0`、Addon=
  `2/6`、exec=`23/48`；critical below-floor=`0`，唯一 structural N/A=`1`，即
  `NamespaceScope.branch`；sensitive scan=`passed`、cleanup=`0/0/0`。threshold freeze
  candidate 已通过 public verification，SHA-256=
  `8bb47382444fd66893d250a8787416c9ce73f9590be4c66308fb7a2e3e014d00`。
- formal-r1=`step4-coverage-20260717-formal-r1` 已在 Cfreeze `86d505e` 上完成全部
  `773+59/5,707/F0E0S0` lane，但在 formal coverage gate 以 `E_FORMAL_LOW` fail closed。
  唯一漂移为 `WatchServiceFileTracer` 的 shutdown-hook 覆盖竞态：line `204/244 -> 195/244`、
  branch `98/128 -> 95/128`。该失败 run 保持 immutable，不以重跑碰运气。
- new Cdiag=`322bb346cca19998a90d6d990505ef033f3a496a` 已 commit/push 并保持 clean identity；
  fresh r14=`step4-coverage-20260717-diagnostic-r14` 完整 PASS：required=
  `773+59/5,707/F0E0S0`、Addon=`2/6`、exec=`23/48`、critical below-floor=`0`、唯一
  structural N/A=`NamespaceScope.branch`、sensitive=`passed`、cleanup=`0/0/0`。
- r14 exact candidate SHA-256=`9087774387f0bb4b177a1b5f2fe28a4102e0434afe7a5b316aa106511c9e6d55`
  经两路独立审查 PASS（合并 B/H/M/L=`0/0/0/1`）。canonical threshold 已转为
  `confirmed` SHA-256=`04544480ef73df4bfcba4ddb1d0323b8314fbb4a6934eae5eae51bb2a958486e`，
  contract/publication=`formal-ready` SHA-256=`6b5e03002ab10bb921d6cb06a4ff3472f2b0605524da6f0f9dc65452a8a21160`；
  direct-child Cfreeze=`1901a10138bac06a09b875c907b7aea6e2789b04` 已 commit/push/clean。
- fresh formal-r2 完成全部 `773+59/5,707/F0E0S0` lane 后正确 fail closed：aggregate line
  exact `54622/76830`，branch=`26105/44870`，比 r14 reviewed threshold 只少 `1`。2066 个
  reportable class 只有 `FileSystemListPresetStore` line 74 的 filename-false outcome 波动；
  12 个 critical class 全部 exact、below-floor=`0`、N/A=`1`。
- 确定性回归已放入既有 FileStore testcase：不存在 ID 查询强制穷尽已有 regular files；
  focused 5/5 fresh fork 稳定命中缺失 probe 106，Data Viewer module=`104/F0E0S0`，无生产
  变更、无新 testcase。machine state 已恢复 `diagnostic-ready/diagnostic-pending`。
- formal-r2 确定性修复的新 Cdiag=`9270d2d4e58684226aeb15eff55b027e6aa4a7eb` 已
  commit/push/clean；fresh diagnostic r15=`step4-coverage-20260717-diagnostic-r15` 在完整
  Unit replacement 的 `Bean2MapUtilsTest#testCachingMechanism` 单次纳秒倍率断言处 fail
  closed。该方法执行前静态 cache 已被同类测试预热，first/second/third 实际均为 cache hit；
  `26,839/304,859/8,517ns` 是调度噪声，不是产品回归。r15 只产生 partial
  `26 reports / 124/F1E0S0`、`2/48 sessions`，最终 sensitive scan 与所有 success-only
  artifact 均 absent，禁止拼接。
- 两个既有 Bean2Map testcase 已改为 deterministic correctness：三个同类、不同 source 实例复制精确
  断言，并移除邻接 1000-copy 的墙钟阈值；无生产/API/POM/runner/floor 变更、无 testcase
  增删。focused 10/10 fresh JVM、class=`23/F0E0S0`、module=`27/F0E0S0`；machine 仍为
  `diagnostic-ready/diagnostic-pending`。正式 pre-Cdiag quality 已 PASS，B/H/M/L=`0/0/0/0`；
  该时点待新 Cdiag、fresh diagnostic、
  review/Cfreeze/formal；`can_enter_coverage_audit=no`、`can_enter_acceptance=no`，Step 5、
  coverage audit、acceptance 与 9.3.5 仍关闭。
- superseding Cdiag=`f863c672029d5d1e5a4903df74cf6cba22a04a85` 已 commit/push；fresh
  r16=`step4-coverage-20260717-diagnostic-r16` 完整 PASS：required=
  `773+59/5,707/F0E0S0`、exec/session/identity=`23/48/16,948`，aggregate line=
  `54,624/76,830`、branch=`26,111/44,870`；12 个 critical class 全部达标，唯一 structural
  N/A 为 `NamespaceScope.branch=0/0`。
- immutable candidate SHA-256=
  `2160ef2e16fad161b91c8e3d2571a91a6a8142ae84e06d4539ec69e976563919` 已经两路独立复算；
  review SHA-256=`88b99e76e5584d3cd17bcdcffd138f1fe6655ce0b7795d3430d2f15a018c8fb3`，
  B/H/M/L=`0/0/0/1`。canonical threshold 已转为 `confirmed`，SHA-256=
  `ca6a25c66fbbe9a595adde74f1b7589bd3829b93edebfd5b11dc394ab8d088c8`；contract 已转为
  `formal-ready`，SHA-256=
  `6b5e03002ab10bb921d6cb06a4ff3472f2b0605524da6f0f9dc65452a8a21160`。
- 当前仍是 reviewed Cfreeze working tree：Cfreeze 尚未 commit/push，fresh formal 尚未运行；
  pre-Cfreeze implementation quality 已 PASS，B/H/M/L=`0/0/0/1`，只授权一次 direct-child
  Cfreeze 与 fresh formal。唯一 Low 要求 fresh formal 完整复现上述 aggregate 高水位，禁止
  下调 threshold。Step 4、coverage audit、acceptance 均未签收，`can_enter_coverage_audit=no`、
  `can_enter_acceptance=no`，Step 5 与 9.3.5 仍关闭。
- 上述状态已由 formal-r3 supersede：direct-child Cfreeze=
  `a63c82c53ebaad1a1c22d78647fbda70b4bd6594` 已 commit/push/clean，fresh
  `step4-coverage-20260717-formal-r3` 完成 `773+59/5707/F0E0S0`、`23/48`、
  cleanup=`0/0/0` 与 sensitive PASS，但在 final formal gate 因 branch=
  `26110/44870 < 26111/44870` 正确 fail closed；line exact `54624/76830`，
  success-only summary/gate 保持 absent。
- r16/formal-r3 的 2,066 个 reportable class 仅
  `QueryModelSupport#getMergedJoinGraph` 有一个 branch outcome 差异：源码 line 316
  inner DCL 由 `0 missed/2 covered` 变为 `1 missed/1 covered`。根因是旧测试依赖
  两调用者的偶然调度，不是 production regression。
- 确定性回归已放入既有
  `RuntimeNamedDataSourceResolverBindingTest#publicationGuardSerializesTheCallbackWithAConcurrentRebind`：
  受控暂停首次 build，确认第二 caller 在 exact `QueryModelSupport` monitor 上
  `BLOCKED`，再断言 single build 和 same graph；不新增/改名 `@Test`。targeted、
  protected overlay 与 5/5 fresh Maven/JVM 已 PASS；QueryModelSupport class id=
  `d242dafe9de31249`、probes=`34/629`、packed bitmap unique=`1`，Surefire=
  `1/F0E0S0`。`foggy-runtime-api` full module=`128/F0E0S0`，
  `RuntimeNamedDataSourceResolverBindingTest=5/F0E0S0`。formal-r3 recovery pre-Cdiag
  implementation quality 已 PASS，B/H/M/L=`0/0/0/0`，machine/contract/overlay/
  negative suites 通过；fresh all-lane diagnostic 尚未完成。
- formal-r3 recovery 当时已把 machine 恢复为 threshold=`diagnostic-pending` / contract=
  `diagnostic-ready`；该时点的旧 manifest 仅是历史 pre-Cdiag 快照，不再作为 current identity。
- superseding Cdiag=`316a71f753827f8f34063b0eb0669271f696c5ee` 已 commit/push/clean，并被 fresh
  diagnostic r17=`step4-coverage-20260717-diagnostic-r17` 消耗。r17 在 outer
  `child-unit / exit 1`、Unit `unit-mysql57-lifecycle-negative / exit 1` immutable fail closed；
  callback 尚未 ready，canonical lifecycle receipt、fixture/Unit XML、exec、source-after、
  aggregate、observation、summary、candidate/final 等 success-only artifact 均 absent。r17 永久
  `excluded/non-reusable`，不得 freeze，也不能证明 QueryModel DCL remediation 已通过 all-lane。
- r17 根因由 runtime RED 直接证明：stock ping 在 PID1 仍为 `docker-entrypoi` 时已 premature
  healthy。Step 4 authority Compose 现对 MySQL57/8 同时要求 PID1=`mysqld` 与原 ping；两库
  runtime GREEN 均证明首次 healthy 即 final mysqld。修复后旧字节三轮 lifecycle=`15/15`，
  后续加固字节 lifecycle=`5/5`、receipt `159fbe80595933e29f05f13c0f1d82e9b65d7f947dddec253477f0b3f3876799`
  保留为 penultimate focused evidence；最新加固字节 r2=
  `step4-unit-lifecycle-handoff-current-20260717-r2` 已 `5/5` PASS，receipt SHA-256=
  `e3bad41ad9ec634c1702ffe20f4c0ddbff3050a227956e2ba9054a11f6b606c7`。所有成功 probe 的
  `provisioner.log` absent，run-owned residue=`0/0/0`，demo exact restore=
  `runner_rc=0/restore_rc=0` 且 healthy/listening。静态 overlay=`12/12`、Unit negatives=`36/36`、coverage negatives=
  `27 + source/Git 22 + replay 12` PASS。
- 当前 machine 仍为 `diagnostic-ready/diagnostic-pending`。完整 Unit 只能由新的 clean/pushed
  replacement Cdiag 上 fresh diagnostic 证明，当前不得声称 full Unit PASS。pre-Cdiag formal
  implementation quality 已 PASS，B/H/M/L=`0/0/0/0`，record=
  `quality/step4-diagnostic-r17-recovery-implementation-quality.md`；只授权 replacement Cdiag
  commit/push/clean 与 fresh diagnostic，不授权 candidate/review/Cfreeze/formal、final quality、
  coverage audit 或 acceptance。Step 4 仍 `in-progress`，Step 5 保持关闭。
- 上述 r17 边界已由 clean/pushed replacement Cdiag=
  `5be1edaa16c5883cde2f66396ac26a1ae113430b` 的 fresh r18 supersede：
  `step4-coverage-20260717-diagnostic-r18`=`diagnostic-observed / completed / exit 0`，public
  validator=`VALID`，observation=
  `7a02bfaaec7d6d1afeac4a5cff20c708fb8ff1092185c25a31ac588b6845dd76`。Unit=
  `681+55/4,941/F0E0S0`、required=`773+59/5,707/F0E0S0`、Addon=`2/6`；Step 3 required=
  `45/446`（database=`29/370`、external=`16 reports / 76 nodes`）。coverage inventory=
  `23 exec / 48 sessions / 16,940 classes`，production universe=`24 modules / 2,098 classes`；
  aggregate line=`54,622/76,830`、branch=`26,107/44,870`，12 个 critical class 的
  below-floor=`0`，model gate=`PASS`。exec/XML negatives=`17/17 + 9/9`、cleanup=`0/0/0`、
  source before=after=`63fdcfb…`；outer exact restore 的 `runner_rc=0/restore_rc=0` 是
  evidence window 外观察，不混入 r18 canonical evidence。
- r18 是有效且 immutable 的 diagnostic PASS，但不能授权 threshold candidate：reviewed r16
  高水位 line=`54,624/76,830`、branch=`26,111/44,870`，r18 分别少 `2 line / 4 branch`，
  decision=`threshold-candidate-not-authorized`，candidate 必须 absent，禁止下调 threshold。
  exact diff 为 `BaselineRatioCalculator=-2 line/-3 branch`、`ResultShaper=-1 branch`，只来自
  PostgreSQL exec bitmap 偶发性，不是生产源码、测试节点或 class-universe 漂移。gap record=
  `evidence/step-4/step4-coverage-diagnostic-r18-governed-high-water-gap-20260717.md`。
- 当前 remediation working tree 已在既有 `PivotSqlParityIT` S12 添加 deterministic null-axis
  semantic oracle：无新/改名 `@Test`、无 production 变更；三次 fresh JVM/JaCoCo focused 均
  `1/F0E0S0`，两个目标 class 的 probe bitmap=`3/3 identical`，完整 `PivotSqlParityIT`=
  `23/F0E0S0`。successor/top hash chain 已重封，manifest=`14/14 + 60/60`，database=
  `7/29/370`、required=`45/446/F0E0S0`、overlay=`22 amendments/9 bindings` 均 PASS。
  workitem=`workitems/BUG-step4-pivot-null-axis-coverage-oracle.md`。pre-Cdiag formal quality=
  `PASS / B/H/M/L=0/0/0/0`，record=
  `quality/step4-diagnostic-r18-pivot-null-axis-implementation-quality.md`。machine 继续为
  `diagnostic-ready/diagnostic-pending`；当前只授权一次 replacement Cdiag
  commit/push/clean 与 fresh r19 diagnostic。candidate/Cfreeze/formal/final quality/coverage audit/
  acceptance 均未获授权，Step 4 仍 `in-progress`，Step 5/9.3.5 closed，
  `can_enter_coverage_audit=no`、`can_enter_acceptance=no`。
- 上述 r18 gate 已由 clean/pushed replacement Cdiag=
  `613b11a0ae6732f865f918551cd9116079771b5e` 的 fresh r19 supersede。r19=
  `diagnostic-observed / completed / exit 0`，required=`773+59/5,707/F0E0S0`、Addon=`2/6`、
  exec/session/class identity=`23/48/16,931`、production universe=`24/2,098`；source
  before=after=`a4ccff29…38f65`，cleanup=`0/0/0`，model/sensitive gates PASS，outer restore=
  `runner_rc=0/restore_rc=0`。aggregate line=`54,624/76,830`、branch=`26,111/44,870`，
  exact 达到 r16 reviewed high-water；critical=`12`、positive metrics=`23`、below-floor=`0`，
  唯一 N/A=`NamespaceScope.branch`。immutable candidate SHA-256=`6588e30b…f545b8` 已经 public
  verify 与两路 independent review PASS；canonical machine working tree 已 exact 投影为
  `confirmed/formal-ready`，pre-Cfreeze quality=`PASS / 0/0/0/0`。当前只授权 direct-child
  Cfreeze commit/push/topology/clean proof 和一次 fresh formal；formal/final quality/audit/
  acceptance 仍 pending，Step 4=`in-progress`、Step 5/9.3.5 closed。

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
- Step 4 diagnostic r5 failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r5-fail-closed-20260716.md](evidence/step-4/step4-coverage-diagnostic-r5-fail-closed-20260716.md)
- Step 4 database-state successor authority regression：
  [workitems/BUG-step4-database-state-successor-authority-manifest.md](workitems/BUG-step4-database-state-successor-authority-manifest.md)
- Step 4 diagnostic r6 environment failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r6-environment-fail-closed-20260716.md](evidence/step-4/step4-coverage-diagnostic-r6-environment-fail-closed-20260716.md)
- Step 4 r6 MySQL 5.7 port environment blocker：
  [workitems/BLOCKER-step4-r6-mysql57-port-occupation.md](workitems/BLOCKER-step4-r6-mysql57-port-occupation.md)
- Step 4 diagnostic r7 Unit hidden MySQL failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r7-unit-hidden-mysql-fail-closed-20260716.md](evidence/step-4/step4-coverage-diagnostic-r7-unit-hidden-mysql-fail-closed-20260716.md)
- Step 4 diagnostic r8 lifecycle contract drift failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r8-bootstrap-negative-contract-drift-fail-closed-20260717.md](evidence/step-4/step4-coverage-diagnostic-r8-bootstrap-negative-contract-drift-fail-closed-20260717.md)
- Step 4 diagnostic r9 exec class scope failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r9-exec-class-scope-fail-closed-20260717.md](evidence/step-4/step4-coverage-diagnostic-r9-exec-class-scope-fail-closed-20260717.md)
- Step 4 diagnostic r10 sensitive-scan failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r10-sensitive-scan-fail-closed-20260717.md](evidence/step-4/step4-coverage-diagnostic-r10-sensitive-scan-fail-closed-20260717.md)
- Step 4 sensitive-scan producer-label regression：
  [workitems/BUG-step4-sensitive-scan-authorization-context-false-positive.md](workitems/BUG-step4-sensitive-scan-authorization-context-false-positive.md)
- Step 4 diagnostic r11 outer source-seal failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r11-outer-source-seal-fail-closed-20260717.md](evidence/step-4/step4-coverage-diagnostic-r11-outer-source-seal-fail-closed-20260717.md)
- Step 4 outer source-seal binding regression：
  [workitems/BUG-step4-outer-runner-source-seal-binding-drift.md](workitems/BUG-step4-outer-runner-source-seal-binding-drift.md)
- Step 4 diagnostic r12 successful observation with threshold gaps：
  [evidence/step-4/step4-coverage-diagnostic-r12-pass-with-threshold-gaps-20260717.md](evidence/step-4/step4-coverage-diagnostic-r12-pass-with-threshold-gaps-20260717.md)
- Step 4 threshold freeze observation/applicability regression：
  [workitems/BUG-step4-threshold-freeze-observation-applicability-gap.md](workitems/BUG-step4-threshold-freeze-observation-applicability-gap.md)
- Step 4 formal-r1 WatchService shutdown-hook coverage regression：
  [workitems/BUG-step4-watchservice-shutdown-hook-coverage-race.md](workitems/BUG-step4-watchservice-shutdown-hook-coverage-race.md)
- Step 4 formal-r1 fail-closed evidence：
  [evidence/step-4/step4-coverage-formal-r1-watchservice-shutdown-race-fail-closed-20260717.md](evidence/step-4/step4-coverage-formal-r1-watchservice-shutdown-race-fail-closed-20260717.md)
- Step 4 formal-r1 recovery implementation quality：
  [quality/step4-formal-r1-recovery-implementation-quality.md](quality/step4-formal-r1-recovery-implementation-quality.md)
- Step 4 formal-r2 ListPreset branch-order regression：
  [workitems/BUG-step4-list-preset-files-find-coverage-order.md](workitems/BUG-step4-list-preset-files-find-coverage-order.md)
- Step 4 formal-r2 fail-closed evidence：
  [evidence/step-4/step4-coverage-formal-r2-list-preset-branch-order-fail-closed-20260717.md](evidence/step-4/step4-coverage-formal-r2-list-preset-branch-order-fail-closed-20260717.md)
- Step 4 formal-r2 recovery implementation quality：
  [quality/step4-formal-r2-recovery-implementation-quality.md](quality/step4-formal-r2-recovery-implementation-quality.md)
- Step 4 diagnostic r15 Bean2Map timing-oracle regression：
  [workitems/BUG-step4-bean2map-cache-timing-oracle.md](workitems/BUG-step4-bean2map-cache-timing-oracle.md)
- Step 4 diagnostic r15 fail-closed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r15-bean2map-timing-oracle-fail-closed-20260717.md](evidence/step-4/step4-coverage-diagnostic-r15-bean2map-timing-oracle-fail-closed-20260717.md)
- Step 4 diagnostic r15 recovery implementation quality：
  [quality/step4-r15-recovery-implementation-quality.md](quality/step4-r15-recovery-implementation-quality.md)
- Step 4 diagnostic r16 successful observation：
  [evidence/step-4/step4-coverage-diagnostic-r16-pass-20260717.md](evidence/step-4/step4-coverage-diagnostic-r16-pass-20260717.md)
- Step 4 diagnostic r16 threshold candidate：
  [evidence/step-4/step4-coverage-diagnostic-r16-threshold-candidate-20260717.json](evidence/step-4/step4-coverage-diagnostic-r16-threshold-candidate-20260717.json)
- Step 4 diagnostic r16 threshold review：
  [evidence/step-4/step4-coverage-diagnostic-r16-threshold-review-20260717.md](evidence/step-4/step4-coverage-diagnostic-r16-threshold-review-20260717.md)
- Step 4 r16 Cfreeze implementation quality：
  [quality/step4-r16-cfreeze-implementation-quality.md](quality/step4-r16-cfreeze-implementation-quality.md)
- Step 4 formal-r3 QueryModel inner-DCL coverage-race regression：
  [workitems/BUG-step4-query-model-join-graph-double-check-coverage-race.md](workitems/BUG-step4-query-model-join-graph-double-check-coverage-race.md)
- Step 4 formal-r3 fail-closed evidence：
  [evidence/step-4/step4-coverage-formal-r3-query-model-join-graph-double-check-race-fail-closed-20260717.md](evidence/step-4/step4-coverage-formal-r3-query-model-join-graph-double-check-race-fail-closed-20260717.md)
- Step 4 formal-r3 recovery pre-Cdiag implementation quality：
  [quality/step4-formal-r3-recovery-implementation-quality.md](quality/step4-formal-r3-recovery-implementation-quality.md)
- Step 4 diagnostic r17 final-mysqld handoff failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r17-unit-mysql57-final-mysqld-handoff-fail-closed-20260717.md](evidence/step-4/step4-coverage-diagnostic-r17-unit-mysql57-final-mysqld-handoff-fail-closed-20260717.md)
- Step 4 Unit final-mysqld handoff readiness regression：
  [workitems/BUG-step4-unit-mysql57-final-mysqld-handoff-readiness-race.md](workitems/BUG-step4-unit-mysql57-final-mysqld-handoff-readiness-race.md)
- Step 4 diagnostic r17 recovery pre-Cdiag implementation quality：
  [quality/step4-diagnostic-r17-recovery-implementation-quality.md](quality/step4-diagnostic-r17-recovery-implementation-quality.md)
- Step 4 diagnostic r18 governed high-water gap：
  [evidence/step-4/step4-coverage-diagnostic-r18-governed-high-water-gap-20260717.md](evidence/step-4/step4-coverage-diagnostic-r18-governed-high-water-gap-20260717.md)
- Step 4 Pivot null-axis deterministic coverage oracle：
  [workitems/BUG-step4-pivot-null-axis-coverage-oracle.md](workitems/BUG-step4-pivot-null-axis-coverage-oracle.md)
- Step 4 diagnostic r18 Pivot null-axis pre-Cdiag implementation quality：
  [quality/step4-diagnostic-r18-pivot-null-axis-implementation-quality.md](quality/step4-diagnostic-r18-pivot-null-axis-implementation-quality.md)
- Step 4 diagnostic r19 successful observation：
  [evidence/step-4/step4-coverage-diagnostic-r19-pass-20260717.md](evidence/step-4/step4-coverage-diagnostic-r19-pass-20260717.md)
- Step 4 diagnostic r19 threshold candidate：
  [evidence/step-4/step4-coverage-diagnostic-r19-threshold-candidate-20260717.json](evidence/step-4/step4-coverage-diagnostic-r19-threshold-candidate-20260717.json)
- Step 4 diagnostic r19 threshold review：
  [evidence/step-4/step4-coverage-diagnostic-r19-threshold-review-20260717.md](evidence/step-4/step4-coverage-diagnostic-r19-threshold-review-20260717.md)
- Step 4 r19 Cfreeze implementation quality：
  [quality/step4-r19-cfreeze-implementation-quality.md](quality/step4-r19-cfreeze-implementation-quality.md)
- Step 4 fresh formal exit：
  [evidence/step-4/step4-coverage-exit-20260717.md](evidence/step-4/step4-coverage-exit-20260717.md)
- Step 4 post-formal implementation quality：
  [quality/step4-coverage-gate-final-implementation-quality.md](quality/step4-coverage-gate-final-implementation-quality.md)
- Step 4 r14 Cfreeze implementation quality：
  [quality/step4-r14-cfreeze-implementation-quality.md](quality/step4-r14-cfreeze-implementation-quality.md)
- Step 4 diagnostic r14 successful observation：
  [evidence/step-4/step4-coverage-diagnostic-r14-pass-20260717.md](evidence/step-4/step4-coverage-diagnostic-r14-pass-20260717.md)
- Step 4 diagnostic r14 threshold candidate：
  [evidence/step-4/step4-coverage-diagnostic-r14-threshold-candidate-20260717.json](evidence/step-4/step4-coverage-diagnostic-r14-threshold-candidate-20260717.json)
- Step 4 diagnostic r14 threshold review：
  [evidence/step-4/step4-coverage-diagnostic-r14-threshold-review-20260717.md](evidence/step-4/step4-coverage-diagnostic-r14-threshold-review-20260717.md)
- Step 4 canonical evidence JSON numeric type-alias regression：
  [workitems/BUG-step4-evidence-json-numeric-type-alias-bypass.md](workitems/BUG-step4-evidence-json-numeric-type-alias-bypass.md)
- Step 4 exec class-ID scope regression：
  [workitems/BUG-step4-exec-class-id-scope-drift.md](workitems/BUG-step4-exec-class-id-scope-drift.md)
- Step 4 lifecycle semantic validator bypass regression：
  [workitems/BUG-step4-lifecycle-semantic-validator-bypass.md](workitems/BUG-step4-lifecycle-semantic-validator-bypass.md)
- Step 4 Unit lifecycle static contract drift regression：
  [workitems/BUG-step4-unit-lifecycle-static-contract-drift.md](workitems/BUG-step4-unit-lifecycle-static-contract-drift.md)
- Step 4 Unit hidden MySQL authority regression：
  [workitems/BUG-step4-unit-hidden-mysql-environment-dependency.md](workitems/BUG-step4-unit-hidden-mysql-environment-dependency.md)
- Step 4 Unit MySQL 5.7 fixture machine contract：
  [`scripts/v934/step4/unit-mysql57-fixture-contract.json`](../../scripts/v934/step4/unit-mysql57-fixture-contract.json)
- Step 4 Unit fixture classification migration debt：
  [workitems/DEBT-unit-mysql57-fixture-classification-migration.md](workitems/DEBT-unit-mysql57-fixture-classification-migration.md)
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
- Step 4 formal coverage exit：
  [evidence/step-4/step4-coverage-exit-20260717.md](evidence/step-4/step4-coverage-exit-20260717.md)
- Step 4 Pivot legacy current-source companion：
  [evidence/step-4/step4-pivot-legacy-companion-evidence-20260718.md](evidence/step-4/step4-pivot-legacy-companion-evidence-20260718.md)
- Step 4 final implementation quality：
  [quality/step4-coverage-gate-final-implementation-quality.md](quality/step4-coverage-gate-final-implementation-quality.md)
- Step 4 test evidence coverage audit：
  [coverage/step4-coverage-gate-coverage-audit.md](coverage/step4-coverage-gate-coverage-audit.md)
- Step 4 feature acceptance：
  [acceptance/step4-coverage-gate-acceptance.md](acceptance/step4-coverage-gate-acceptance.md)

## 1~7 顺序

| Step | 内容 | 当前状态 | Exit |
|---:|---|---|---|
| 1 | 契约与静态库存冻结 | passed | r8 confirmed；532 sources / 820 discovery / 829 execution / 519 predecessor；28/28 negatives |
| 2 | Surefire/Failsafe 全量分层 | passed | r8e confirmed；724 positive + 59 structural；5,205 testcase；F0/E0/S0；signal-safe status |
| 3 | 五数据库与外部集成 required matrix | passed | r4 same-commit exact `45/446/F0E0S0`；DB state `18/18`、Redis state `4/4`；Addon companion `2/6`；feature accepted |
| 4 | JaCoCo unit+IT 聚合与关键类门 | in-progress / formal-r8 recovery / ready-for-new-Cdiag | r26/Cfreeze complete；formal-r8 rc126 excluded；三处 interpreter fix + `raw 44 / stream 43 / semantic 33 / mode 4 / nonexec 4` gate + code/docs reviews PASS；next=Cdiag→r27→Cfreeze→formal-r9→post gates |
| 5 | 单一 authority runner 与 immutable evidence rehearsal | hold / execution closed | implementation preserved；entry reclosed until replacement formal + final quality + Step 4 coverage audit/feature acceptance |
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

## Superseding replacement-authority status（2026-07-19）

- fresh formal-r6 on Cfreeze `14931b68…` 在 `bootstrap-negative` fail closed；formal delta、
  frozen r22 和 contract 已通过，但 source seal/所有 test lane 尚未开始；r6 永久 failed/excluded；
- 根因是 synthetic fsmonitor v2 hook 使用换行 token，导致首读偶发掩码，不是 fresh clone 的
  real index/source 漂移；
- fixture 已改为 NUL-token，保留 exact `h` 前置条件与 validator rc=2 拒绝；focused stress=
  `100/100`、独立 stress=`1000/1000`、full negative processes=`5/5`；
- r23 在新 Cdiag `9b0bd281…` 上 full PASS/public-valid，但 branch=`26112/44870` 的唯一新增 outcome
  来自 `MapBeanInfoHelper#getBeanProperty` inner double-check 偶发调度；candidate/capsule 均 absent；
- existing-node controlled regression 已在 5 个 fresh JVM 得到 exact class probe bitmap，owning module
  `97/F0E0S0`，pre-Cdiag review=`0/0/0/0`；
- current machine=`diagnostic-ready / diagnostic-pending`。历史 r22/r23/Cfreeze 不可复用；必须
  replacement Cdiag→fresh diagnostic-r24→candidate/capsule/双审→direct-child Cfreeze→formal-r7；
- Step 5–7、coverage acceptance replacement、9.3.5 与 9.4.0 在 formal-r7 完成前保持关闭。

## Superseding diagnostic-r24 reviewed-Cfreeze status（2026-07-19）

- replacement Cdiag=`414c8b12bff31155584e639e74987bf22df13ba9` 已 clean/pushed；fresh r24=
  `completed / diagnostic-observed / exit 0 / public-valid`，required=`773+59/5707/F0E0S0`、
  Addon=`2/6`、exec/session=`23/48`、source exact、cleanup/restore PASS；
- aggregate exact line=`54624/76830`、branch=`26112/44870`、complexity=`17659/35571`；目标
  `MapBeanInfoHelper#getBeanProperty` 在受控 existing-node regression 下稳定为 branch=`4/4`、
  probe=`10/11 / _wU`；
- immutable candidate=`f13f3c35…2ee`；portable capsule=`6638 entries / symlink 0`，两次独立
  rebuild、public verify、空目录 materialize 全部 PASS；两路 independent review=`0/0/0/0`；
- canonical machine 已 exact formalize：threshold=`confirmed / ddc1b24d…c04c`、contract=
  `formal-ready / babdcd88…be1e`，Step 4/6 manifest=`61/61 + 16/16`，frozen r24 replay PASS；
- pre-Cfreeze quality=`PASS / B0 H0 M0 L0`。当前只授权以 `414c8b12…` 为唯一直接父提交的
  Cfreeze commit/push/clean 与一次 fresh-clone formal-r7；Step 5–7、post-formal audit/acceptance、
  9.3.5 与 9.4.0 继续关闭。

Current r24 records：

- [diagnostic PASS](evidence/step-4/step4-coverage-diagnostic-r24-pass-20260719.md)
- [immutable threshold candidate](evidence/step-4/step4-coverage-diagnostic-r24-threshold-candidate-20260719.json)
- [dual threshold review](evidence/step-4/step4-coverage-diagnostic-r24-threshold-review-20260719.md)
- [portable capsule manifest](evidence/step-4/step4-coverage-20260719-diagnostic-r24-portable-capsule.manifest.json)
- [pre-Cfreeze implementation quality](quality/step4-r24-cfreeze-implementation-quality.md)

## Superseding formal-r7 portability recovery（historical；2026-07-19）

- Cfreeze=`439aea5e…` 已 clean/pushed；fresh formal-r7 的 Unit 完整通过
  `681+55/4941/F0E0S0`，Integration 前两变体通过，`sqlite-broad` 在
  `CalculateMvpIT.parityCatalogCasesStayExecutable` 以 `307/F1E0S0` fail closed；
- 唯一根因是 bridge Git tree 未跟踪 parity catalog，本地 diagnostic 误读工作区父目录同名文件；
  r7 永久 `failed/excluded/non-reusable`，cleanup=`0/0/0`，四个 demo DB exact ID 恢复 healthy；
- exact catalog 已纳入 `docs/v1.5.1/`；Step 4 runner 在任何测试前校验 exact tracked
  `100644` blob + SHA-256，raw/executable lifecycle 双层 seal 与 Step4→Step6 hash cascade 已同步；
- focused `CalculateMvpIT=14/F0E0S0`，test source SHA/node count 不变；machine 已回到
  `diagnostic-ready/diagnostic-pending`；
- 该 checkpoint 当时的下一合法状态只允许 Cdiag commit/push/isolated proof→fresh diagnostic-r25→
  candidate/capsule/双审→direct-child Cfreeze→fresh formal-r8；随后已被下方 r25/r26 状态
  supersede。Step 5–7、9.3.5、9.4.0 在该 checkpoint 继续关闭。

Current recovery records：

- [formal-r7 fail-closed evidence](evidence/step-4/step4-coverage-formal-r7-calculate-catalog-portability-fail-closed-20260719.md)
- [formal-r7 failure capsule](evidence/step-4/formal-r7-failure-capsule/manifest.json)
- [portability blocker workitem](workitems/BUG-calculate-mvp-parity-catalog-fresh-clone-portability.md)
- [portability recovery implementation quality](quality/step4-formal-r7-portability-recovery-implementation-quality.md)

## Superseding diagnostic-r25 Unit MySQL 7/12 remediation（historical；2026-07-19）

- Cdiag=`5aaffbb4cd217d3d891c22eca4d3ae31d4e9d6e7`；fresh r25=
  `completed / diagnostic-observed / exit 0`，public validator=`DIAGNOSTIC VALID`，observation=
  `01487f7efd930406ffa05af9408012aa1fb215d94ba9c36c261f72c1aec7e42a`；required=
  `773+59/5707/F0E0S0`、Addon=`2/6`、exec/session=`23/48`、production universe=
  `24/2098`，source before=after、cleanup 和 exact demo DB restore 均通过；
- follow-up consumer audit 发现第 7 个真实 MySQL consumer：
  `DatasetJdbcUtilsTest#getOrCreateDataSource` 建立 JDBC connection 并执行 `SELECT 1`，却捕获
  `SQLException` 后只打印 stack trace；无 listener 时它可产生伪绿；
- r7 的 `6 reports / 11 errors` 是不可改写的历史失败观测；当前 reviewed known-consumer
  lower bound 是 `7 reports / 12 nodes`。r25 tested schema 1 contract 的 `6/11` 因而不足以授权
  candidate；r25 保持有效诊断观测，但被标记为 `pre-remediation / superseded / non-candidate`；
- remediation machine closure 已完成：测试改为 `throws SQLException` + try-with-resources +
  `SELECT 1` 精确断言；schema 2 分离 historical `6/11` 与 known lower bound `7/12`；
  fixture negatives=`42/42`、lifecycle=`5/5`、overlay=`20/20`、Step4=`61/61`、
  Step6=`16/16`，pre-Cdiag quality=`APPROVE / 0/0/0/0`。disposable MySQL 正/负结果仅是
  未封存的 local observation；new Cdiag 后仍须 isolated durable proof，随后才可 fresh r26；
- 该 checkpoint 不得生成 r25 candidate/capsule、不得 Cfreeze，也不得进入 Step 5、9.3.5 或
  9.4.0；随后已被下方 r26 reviewed-Cfreeze 状态 supersede。当时唯一合法链路是完成修复与机器
  闭包→new Cdiag/push/clean→isolated proof→fresh r26→
  candidate/capsule/双审→direct-child Cfreeze→fresh formal-r8→后置质量/audit/acceptance。

Current 7/12 remediation records：

- [r25 superseded diagnostic evidence](evidence/step-4/step4-unit-mysql57-known-consumer-7of12-remediation-20260719.md)
- [known-consumer understatement BUG](workitems/BUG-step4-unit-mysql57-known-consumer-understatement.md)
- [pre-Cdiag implementation quality](quality/step4-unit-mysql712-remediation-implementation-quality.md)

## Superseding diagnostic-r26 reviewed-Cfreeze status（2026-07-19）

- repaired Cdiag=`4fe86929de6206aa3e514c974635e90395c28b2e` 已 clean/pushed；fresh-clone isolated
  r4 保存 positive=`Maven rc 0 / 1/F0E0S0` 与 wrong-password=`Maven rc 1 / 1/F0E1S0` 双
  XML，并证明 disposable container absent、random port released；
- fresh r26=`diagnostic-observed / completed / exit 0 / public-valid`，required=
  `773+59/5707/F0E0S0`、Addon=`2/6/F0E0S0`、exec/session=`23/48`、source exact、cleanup 与
  四个 demo DB exact restore PASS；Unit fixture historical/current=`6/11 + 7/12`、negative/lifecycle=
  `42/42 + 5/5`；
- aggregate=`54624/76830 line + 26112/44870 branch`；candidate=
  `b8bd24113223e9a9c79280b248582ff34ad7a29013c2f93c4c7f4ebea682797a`，portable capsule
  deterministic rebuild/materialize 与两路 review 均 PASS，B/H/M/L=`0/0/0/0`；
- 当前只授权一个以 `4fe86929…` 为 direct parent 的 Cfreeze，随后必须 fresh formal-r8。r26
  reviewed candidate 不是 formal、coverage audit、Step 4 feature acceptance 或 9.3.4 version signoff；
- formal-r8 PASS 后按序执行 final quality→Step 4 coverage audit→Step 4 feature acceptance，三门
  PASS 后只开放 Step 5。9.3.4 仍须完成 Steps 5–7 和 version signoff；9.3.5 保持 queued，version
  signoff 后也只能先进入 Gate 0 classification-debt migration。

Current r26 records：

- [diagnostic PASS and portable evidence](evidence/step-4/step4-coverage-diagnostic-r26-pass-20260719.md)
- [immutable threshold candidate](evidence/step-4/step4-coverage-diagnostic-r26-threshold-candidate-20260719.json)
- [dual threshold review](evidence/step-4/step4-coverage-diagnostic-r26-threshold-review-20260719.md)
- [portable capsule manifest](evidence/step-4/step4-coverage-20260719-diagnostic-r26-portable-capsule.manifest.json)
- [pre-Cfreeze implementation quality](quality/step4-r26-cfreeze-implementation-quality.md)

## Superseding formal-r8 report-runner recovery status（2026-07-19）

- direct-child Cfreeze=`7c18019ed12d25c029de7e7e49caef77a79b2e67` 已 push/clean；fresh
  formal-r8 完成 Unit `681+55/4941`、Integration `47+4/320`、database `29/370`、external
  `16/76`、Step 3 required `45/446`、Addon `2/6` 与 report inventory
  `773+59/5707/F0E0S0`；
- coverage report 首个 provenance 调用直接执行 Git mode=`100644` 的 Python 工具，fresh clone
  正确返回 rc126；r8 因而永久 `failed / excluded / non-reusable / non-candidate`，exec manifest、
  aggregate、observation、gate、summary、candidate/final 均 absent；
- runner cleanup=`0/0/0`，outer restore=`rc0`，四个 demo DB 原 exact ID 均恢复
  `running/healthy`；16-entry failure capsule 与独立 sensitive scan PASS；
- 三个错误的直接调用点已统一为 `python3`；新 negative contract 先绑定 runner exact raw bytes 与
  `292` 条 logical executable command stream，再对全部四个 Git `100644` 工具和七个 interpreter
  dispatch 作 target/top-level exact binding；raw/stream/semantic mutation 分别=`44/44 / 43/43 /
  33/33`，其中 `11/11` dynamic/heredoc source mutations 包含一个 stream-unchanged inline-Python
  direct call；Git-mode mutation=`4/4`、non-executable tool smoke=`4/4`。Step 4/6
  manifests、CI workflow closure 与
  `diagnostic-ready / diagnostic-pending` machine reset PASS；
- 独立 pre-Cdiag code/docs reviews=`PASS / 0/0/0/0 / mandatory 0`；当前只授权把 failure capsule、
  BUG、修复、machine reset 与 recovery quality 提交为一个 new Cdiag；
  后续必须 fresh diagnostic-r27→candidate/capsule/双审→direct-child Cfreeze→fresh formal-r9→final
  quality→coverage audit `31/31`→feature acceptance。Step 5–7、9.3.5、9.4.0 继续关闭。

Current recovery records：

- [formal-r8 fail-closed evidence](evidence/step-4/step4-coverage-formal-r8-report-runner-permission-fail-closed-20260719.md)
- [failure capsule](evidence/step-4/formal-r8-failure-capsule/manifest.json)
- [report-runner executable-bit BUG](workitems/BUG-step4-report-runner-nonauthoritative-exec-bit-dependency.md)
- [interpreter recovery evidence](evidence/step-4/step4-coverage-report-runner-interpreter-recovery-20260719.md)
- [pre-Cdiag recovery quality](quality/step4-formal-r8-report-runner-recovery-implementation-quality.md)
